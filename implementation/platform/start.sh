#!/usr/bin/env bash
#
# EN001 — Canonical local entry point to START the My-FinAI-Manager platform.
#
# Starts, in order:
#   1. local infrastructure (PostgreSQL) via Docker Compose
#   2. the backend (core-service, Spring Boot)   -> http://localhost:8080
#   3. the frontend (web, Angular dev server)    -> http://localhost:4200
#
# Backend/frontend run as background processes; their PIDs are written to .run/ so stop.sh can
# terminate them. Re-running start.sh detects processes that are already up and does not duplicate
# them.
#
# This script is the stable interface. Its internals may change; the contract (start.sh / stop.sh)
# must not.

set -euo pipefail

PLATFORM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="${PLATFORM_DIR}/.run"
COMPOSE_FILE="${PLATFORM_DIR}/infrastructure/local/compose.yaml"
ENV_FILE="${PLATFORM_DIR}/infrastructure/local/.env"
ENV_EXAMPLE="${PLATFORM_DIR}/infrastructure/local/.env.example"
BACKEND_DIR="${PLATFORM_DIR}/backend/core-service"
FRONTEND_DIR="${PLATFORM_DIR}/frontend/web"

BACKEND_PORT=8080
FRONTEND_PORT=4200
POSTGRES_PORT=5432

die() { echo "ERROR: $*" >&2; exit 1; }
info() { echo "[start] $*"; }

# --- preconditions -----------------------------------------------------------

command -v docker >/dev/null 2>&1 || die "docker is not installed or not on PATH."
docker info >/dev/null 2>&1 || die "the Docker daemon is not running. Start Docker and retry."

if docker compose version >/dev/null 2>&1; then
  DC=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  DC=(docker-compose)
else
  die "Docker Compose is not available (need 'docker compose' or 'docker-compose')."
fi

command -v mvn >/dev/null 2>&1 || die "Maven (mvn) is not installed or not on PATH."
command -v npm >/dev/null 2>&1 || die "npm is not installed or not on PATH."

if [[ ! -f "${ENV_FILE}" ]]; then
  die "missing ${ENV_FILE}. Create it with: cp \"${ENV_EXAMPLE}\" \"${ENV_FILE}\""
fi

port_in_use() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
  else
    nc -z localhost "${port}" >/dev/null 2>&1
  fi
}

mkdir -p "${RUN_DIR}"

# --- 1. infrastructure -------------------------------------------------------

info "starting local infrastructure (PostgreSQL) ..."
"${DC[@]}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d

info "waiting for PostgreSQL to become healthy ..."
for _ in $(seq 1 40); do
  status="$("${DC[@]}" -f "${COMPOSE_FILE}" ps --format '{{.Health}}' postgres 2>/dev/null || true)"
  [[ "${status}" == "healthy" ]] && break
  sleep 2
done
[[ "${status:-}" == "healthy" ]] || die "PostgreSQL did not become healthy. Check: ${DC[*]} -f ${COMPOSE_FILE} logs postgres"
info "PostgreSQL is healthy on port ${POSTGRES_PORT}."

# --- 2. backend -------------------------------------------------------------

if [[ -f "${RUN_DIR}/backend.pid" ]] && kill -0 "$(cat "${RUN_DIR}/backend.pid")" 2>/dev/null; then
  info "backend already running (PID $(cat "${RUN_DIR}/backend.pid"))."
elif port_in_use "${BACKEND_PORT}"; then
  die "port ${BACKEND_PORT} is already in use; cannot start the backend. Free it or run stop.sh."
else
  info "starting backend (core-service) ..."
  ( cd "${BACKEND_DIR}" && set -a && . "${ENV_FILE}" && set +a \
      && exec mvn -q -DskipTests spring-boot:run ) \
      > "${RUN_DIR}/backend.log" 2>&1 &
  echo $! > "${RUN_DIR}/backend.pid"
  info "backend starting (PID $(cat "${RUN_DIR}/backend.pid")); logs: ${RUN_DIR}/backend.log"
fi

# --- 3. frontend ----------------------------------------------------------

if [[ -f "${RUN_DIR}/frontend.pid" ]] && kill -0 "$(cat "${RUN_DIR}/frontend.pid")" 2>/dev/null; then
  info "frontend already running (PID $(cat "${RUN_DIR}/frontend.pid"))."
elif port_in_use "${FRONTEND_PORT}"; then
  die "port ${FRONTEND_PORT} is already in use; cannot start the frontend. Free it or run stop.sh."
else
  if [[ ! -d "${FRONTEND_DIR}/node_modules" ]]; then
    info "installing frontend dependencies (first run) ..."
    ( cd "${FRONTEND_DIR}" && npm install --no-audit --no-fund )
  fi
  info "starting frontend (web) ..."
  ( cd "${FRONTEND_DIR}" && exec npm start ) > "${RUN_DIR}/frontend.log" 2>&1 &
  echo $! > "${RUN_DIR}/frontend.pid"
  info "frontend starting (PID $(cat "${RUN_DIR}/frontend.pid")); logs: ${RUN_DIR}/frontend.log"
fi

# --- summary --------------------------------------------------------------

cat <<EOF

[start] Platform is starting.
        Backend        : http://localhost:${BACKEND_PORT}
        Backend health : http://localhost:${BACKEND_PORT}/actuator/health
        Frontend       : http://localhost:${FRONTEND_PORT}
        PostgreSQL     : localhost:${POSTGRES_PORT}

        Backend/frontend take a few seconds to become ready. Tail logs with:
          tail -f ${RUN_DIR}/backend.log
          tail -f ${RUN_DIR}/frontend.log

        Stop everything with: ${PLATFORM_DIR}/stop.sh
EOF
