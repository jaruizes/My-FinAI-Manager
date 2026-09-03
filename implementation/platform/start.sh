#!/usr/bin/env bash
#
# Canonical local entry point to START the My-FinAI-Manager platform (EN002).
#
# The COMPLETE platform runs as containers through Docker Compose:
#
#   Docker Compose
#     ├── postgres   (PostgreSQL 16)
#     ├── backend    (core-service, Spring Boot)  -> http://localhost:8080
#     └── frontend   (Angular build served by nginx, /api proxied to backend) -> http://localhost:4200
#
# There is NO host Spring Boot process and NO host Angular dev server. Docker is the only runtime
# dependency for running the platform. (Local Java/Node tooling is still used for building/testing.)
#
# Usage:
#   ./start.sh              start using existing images (builds an image only if it is missing)
#   BUILD=1 ./start.sh      rebuild all platform images first
#   ./start.sh --build      same as BUILD=1
#
# This script is the stable interface. Its internals may change; the contract (start.sh / stop.sh /
# e2e.sh) must not. Browser E2E is run via ./e2e.sh, never from here.

set -euo pipefail

PLATFORM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${PLATFORM_DIR}/infrastructure/local/compose.yaml"
ENV_FILE="${PLATFORM_DIR}/infrastructure/local/.env"
ENV_EXAMPLE="${PLATFORM_DIR}/infrastructure/local/.env.example"

BACKEND_CTX="${PLATFORM_DIR}/backend/core-service"
FRONTEND_CTX="${PLATFORM_DIR}/frontend/web"

BACKEND_IMAGE="finai/core-service:local"
FRONTEND_IMAGE="finai/web:local"

BACKEND_PORT=8080
FRONTEND_PORT=4200
POSTGRES_PORT=5432

READY_TIMEOUT=180   # seconds to wait for all services healthy

# The local platform is built and run for the HOST architecture. A stray DOCKER_DEFAULT_PLATFORM
# (e.g. linux/amd64 on an Apple-silicon machine) makes Compose demand an image variant that was
# never built and forces slow emulation. Clear it for this script.
unset DOCKER_DEFAULT_PLATFORM

die()  { echo "ERROR: $*" >&2; exit 1; }
info() { echo "[start] $*"; }

BUILD="${BUILD:-0}"
[[ "${1:-}" == "--build" ]] && BUILD=1

# --- preconditions ---------------------------------------------------------

command -v docker >/dev/null 2>&1 || die "docker is not installed or not on PATH."
docker info >/dev/null 2>&1 || die "the Docker daemon is not running. Start Docker (or 'colima start') and retry."

if docker compose version >/dev/null 2>&1; then
  DC=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  DC=(docker-compose)
else
  die "Docker Compose is not available (need 'docker compose' or 'docker-compose')."
fi

[[ -f "${ENV_FILE}" ]] || die "missing ${ENV_FILE}. Create it with: cp \"${ENV_EXAMPLE}\" \"${ENV_FILE}\""

port_in_use() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
  else
    nc -z localhost "${port}" >/dev/null 2>&1
  fi
}

DC_ENV=("${DC[@]}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

# A port in use is only a problem if it is NOT our own compose project (re-running start.sh is fine).
own_project_running() { "${DC_ENV[@]}" ps --status running 2>/dev/null | grep -q .; }
for pair in "PostgreSQL:${POSTGRES_PORT}" "backend:${BACKEND_PORT}" "frontend:${FRONTEND_PORT}"; do
  name="${pair%%:*}"; port="${pair##*:}"
  if port_in_use "${port}" && ! own_project_running; then
    die "port ${port} (${name}) is already in use by another process. Free it (or stop that process) and retry."
  fi
done

# --- build (only if requested or missing) --------------------------------

build_image() {
  local image="$1" context="$2" label="$3"
  info "building ${label} image (${image}) ... (this can take a few minutes on first run)"
  docker build -t "${image}" "${context}" >/dev/null 2>&1 \
    || die "failed to build the ${label} image. Run 'docker build ${context}' to see the error."
}

image_missing() { ! docker image inspect "$1" >/dev/null 2>&1; }

if [[ "${BUILD}" == "1" ]]; then
  build_image "${BACKEND_IMAGE}"  "${BACKEND_CTX}"  "backend"
  build_image "${FRONTEND_IMAGE}" "${FRONTEND_CTX}" "frontend"
else
  image_missing "${BACKEND_IMAGE}"  && build_image "${BACKEND_IMAGE}"  "${BACKEND_CTX}"  "backend"
  image_missing "${FRONTEND_IMAGE}" && build_image "${FRONTEND_IMAGE}" "${FRONTEND_CTX}" "frontend"
fi

# Make sure the base image(s) we do NOT build are the HOST-architecture variant. A wrong-arch
# variant (e.g. cached from a run with DOCKER_DEFAULT_PLATFORM=linux/amd64) runs under slow
# emulation and Compose prints a platform-mismatch warning. `docker image inspect` reports an
# empty Architecture for such an emulated image, so treat empty-or-mismatched as "needs a native
# pull". Best-effort — a network failure is not fatal.
pull_native() {
  local image="$1" host cached
  host="$(docker version --format '{{.Server.Arch}}' 2>/dev/null || true)"
  [[ -n "${host}" ]] || return 0
  cached="$(docker image inspect "${image}" --format '{{.Architecture}}' 2>/dev/null || true)"
  [[ "${cached}" == "${host}" ]] && return 0
  info "pulling ${image} for linux/${host}${cached:+ (cached variant is linux/${cached})} ..."
  docker rmi -f "${image}" >/dev/null 2>&1 || true
  docker pull -q --platform "linux/${host}" "${image}" >/dev/null 2>&1 || true
}
PG_IMAGE="$(grep -m1 -oE 'postgres:[0-9A-Za-z._-]+' "${COMPOSE_FILE}" 2>/dev/null || echo postgres:16-alpine)"
pull_native "${PG_IMAGE}"

# --- start --------------------------------------------------------------

info "starting the platform (postgres + backend + frontend) ..."
"${DC_ENV[@]}" up -d --no-build postgres backend frontend

# --- wait for health ---------------------------------------------------

info "waiting for all services to become healthy (timeout ${READY_TIMEOUT}s) ..."
deadline=$(( $(date +%s) + READY_TIMEOUT ))
while :; do
  status="$("${DC_ENV[@]}" ps --format '{{.Service}}={{.Health}}' 2>/dev/null | sort | tr '\n' ' ')"
  if echo "${status}" | grep -q 'postgres=healthy' \
     && echo "${status}" | grep -q 'backend=healthy' \
     && echo "${status}" | grep -q 'frontend=healthy'; then
    break
  fi
  if echo "${status}" | grep -q 'unhealthy'; then
    unhealthy="$(echo "${status}" | tr ' ' '\n' | grep 'unhealthy' | cut -d= -f1 | tr '\n' ' ')"
    echo
    "${DC_ENV[@]}" logs --tail 40 ${unhealthy} || true
    die "service(s) reported unhealthy: ${unhealthy}. Logs above. Inspect with: ${DC[*]} -f ${COMPOSE_FILE} logs ${unhealthy}"
  fi
  if (( $(date +%s) >= deadline )); then
    echo
    "${DC_ENV[@]}" ps || true
    die "platform did not become healthy within ${READY_TIMEOUT}s. Current: ${status:-<none>}. Inspect with: ${DC[*]} -f ${COMPOSE_FILE} logs"
  fi
  sleep 3
done

# --- summary -----------------------------------------------------------

cat <<EOF

[start] Platform is up (all containers healthy).
        Frontend       : http://localhost:${FRONTEND_PORT}
        Backend        : http://localhost:${BACKEND_PORT}
        Backend health : http://localhost:${BACKEND_PORT}/actuator/health
        PostgreSQL     : localhost:${POSTGRES_PORT}

        Logs : ${DC[*]} -f ${COMPOSE_FILE} logs -f
        Stop : ${PLATFORM_DIR}/stop.sh
        E2E  : ${PLATFORM_DIR}/e2e.sh
EOF
