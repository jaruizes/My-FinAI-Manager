#!/usr/bin/env bash
#
# Canonical entry point for browser-based E2E testing (EN002).
#
# Runs the Playwright smoke suite (Chromium, in a container) against a FULLY ISOLATED, throwaway
# copy of the containerized platform:
#
#   docker compose -p finai-e2e -f compose.yaml -f compose.e2e.yaml --profile e2e
#     ├── postgres   (disposable volume, port 15432)
#     ├── backend    (port 18080)
#     ├── frontend   (port 14200)
#     └── e2e        (Playwright -> http://frontend)
#
# It builds the images, starts the stack, waits for health, runs Playwright, propagates the exit
# code, and ALWAYS tears the environment down (including its disposable volume). The developer's
# normal `./start.sh` platform and its data volume are never touched.
#
# Usage:
#   ./e2e.sh                       run the whole suite
#   ./e2e.sh -g "shell loads"      pass args straight through to `playwright test`
#
# Exit code == Playwright's exit code (0 = all passed, non-zero = failure). (VC-011)

set -euo pipefail

PLATFORM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="${PLATFORM_DIR}/infrastructure/local"
COMPOSE_FILE="${INFRA_DIR}/compose.yaml"
COMPOSE_E2E_FILE="${INFRA_DIR}/compose.e2e.yaml"
ENV_FILE="${INFRA_DIR}/.env"
ENV_EXAMPLE="${INFRA_DIR}/.env.example"

E2E_CTX="${PLATFORM_DIR}/e2e"
BACKEND_CTX="${PLATFORM_DIR}/backend/core-service"
FRONTEND_CTX="${PLATFORM_DIR}/frontend/web"
FINNHUB_STUB_CTX="${PLATFORM_DIR}/e2e/finnhub-stub"
OPENAI_STUB_CTX="${PLATFORM_DIR}/e2e/openai-stub"
E2E_IMAGE="finai/e2e:local"
BACKEND_IMAGE="finai/core-service:local"
FRONTEND_IMAGE="finai/web:local"
FINNHUB_STUB_IMAGE="finai/finnhub-stub:local"
OPENAI_STUB_IMAGE="finai/openai-stub:local"

PROJECT="finai-e2e"
READY_TIMEOUT="${E2E_READY_TIMEOUT:-180}"
RESULTS_DIR="${E2E_CTX}/test-results"

# Build and run for the host architecture regardless of a stray DOCKER_DEFAULT_PLATFORM.
unset DOCKER_DEFAULT_PLATFORM

die()  { echo "ERROR: $*" >&2; exit 1; }
info() { echo "[e2e] $*"; }

command -v docker >/dev/null 2>&1 || die "docker is not installed or not on PATH."
docker info >/dev/null 2>&1 || die "the Docker daemon is not running. Start Docker (or 'colima start') and retry."
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required ('docker compose')."
[[ -f "${ENV_FILE}" ]] || die "missing ${ENV_FILE}. Create it with: cp \"${ENV_EXAMPLE}\" \"${ENV_FILE}\""

DC=(docker compose -p "${PROJECT}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" -f "${COMPOSE_E2E_FILE}")

teardown() {
  info "tearing down the isolated E2E environment (down -v) ..."
  "${DC[@]}" --profile e2e down -v --remove-orphans >/dev/null 2>&1 || true
}
trap teardown EXIT

# Start from a guaranteed-clean isolated project (in case a previous run crashed before teardown).
"${DC[@]}" --profile e2e down -v --remove-orphans >/dev/null 2>&1 || true

# Fresh diagnostics directory each run — no contamination from earlier runs (VC-010, OD-8).
rm -rf "${RESULTS_DIR}"
mkdir -p "${RESULTS_DIR}"

# --- build the images this run uses -------------------------------------

build_image() {
  local image="$1" context="$2" label="$3"
  info "building ${label} image (${image}) ..."
  docker build -t "${image}" "${context}" >/dev/null 2>&1 \
    || die "failed to build the ${label} image. Run 'docker build ${context}' to see the error."
}
build_image "${BACKEND_IMAGE}"      "${BACKEND_CTX}"      "backend"
build_image "${FRONTEND_IMAGE}"     "${FRONTEND_CTX}"     "frontend"
build_image "${FINNHUB_STUB_IMAGE}" "${FINNHUB_STUB_CTX}" "finnhub-stub (FD004 E2E)"
build_image "${OPENAI_STUB_IMAGE}"  "${OPENAI_STUB_CTX}"  "openai-stub (FD005 E2E)"
build_image "${E2E_IMAGE}"          "${E2E_CTX}"          "e2e (Playwright)"

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

# --- start the isolated platform (not the e2e service yet) --------------

info "starting the isolated platform (project '${PROJECT}') ..."
"${DC[@]}" up -d --no-build postgres finnhub-stub openai-stub backend frontend

info "waiting for postgres + finnhub-stub + openai-stub + backend + frontend to become healthy (timeout ${READY_TIMEOUT}s) ..."
deadline=$(( $(date +%s) + READY_TIMEOUT ))
while :; do
  status="$("${DC[@]}" ps --format '{{.Service}}={{.Health}}' 2>/dev/null | sort | tr '\n' ' ')"
  if echo "${status}" | grep -q 'postgres=healthy' \
     && echo "${status}" | grep -q 'finnhub-stub=healthy' \
     && echo "${status}" | grep -q 'openai-stub=healthy' \
     && echo "${status}" | grep -q 'backend=healthy' \
     && echo "${status}" | grep -q 'frontend=healthy'; then
    break
  fi
  if echo "${status}" | grep -q 'unhealthy' || (( $(date +%s) >= deadline )); then
    echo; "${DC[@]}" ps || true
    "${DC[@]}" logs --tail 40 postgres finnhub-stub openai-stub backend frontend || true
    die "isolated platform did not become healthy (status: ${status:-<none>})."
  fi
  sleep 3
done
info "platform healthy — running Playwright."

# --- run Playwright ---------------------------------------------------

set +e
"${DC[@]}" run --rm e2e "$@"
EXIT=$?
set -e

if [[ ${EXIT} -ne 0 ]]; then
  info "Playwright failed (exit ${EXIT}); capturing container logs for diagnosis ..."
  mkdir -p "${RESULTS_DIR}/containers"
  for svc in postgres finnhub-stub openai-stub backend frontend; do
    "${DC[@]}" logs --no-color "${svc}" > "${RESULTS_DIR}/containers/${svc}.log" 2>&1 || true
  done
  info "container logs: ${RESULTS_DIR}/containers/  |  Playwright artifacts: ${RESULTS_DIR}/"
else
  info "Playwright passed. Report: ${RESULTS_DIR}/html/index.html"
fi

exit ${EXIT}
