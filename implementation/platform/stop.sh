#!/usr/bin/env bash
#
# Canonical local entry point to STOP the My-FinAI-Manager platform (EN002).
#
# Stops the complete containerized platform through Docker Compose. Safe to run when nothing is
# running (no-op, exit 0). The PostgreSQL data volume is KEPT so local developer data survives a
# stop/start cycle — remove it deliberately with:  docker compose -f infrastructure/local/compose.yaml down -v
#
# After EN002 there are no backend/frontend host processes and no PID files.

set -euo pipefail

PLATFORM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${PLATFORM_DIR}/infrastructure/local/compose.yaml"
ENV_FILE="${PLATFORM_DIR}/infrastructure/local/.env"

info() { echo "[stop] $*"; }

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  info "Docker is not available; nothing to stop."
  exit 0
fi

if docker compose version >/dev/null 2>&1; then
  DC=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  DC=(docker-compose)
else
  info "Docker Compose not available; nothing to stop."
  exit 0
fi

ENV_ARGS=()
[[ -f "${ENV_FILE}" ]] && ENV_ARGS=(--env-file "${ENV_FILE}")

info "stopping the platform (docker compose down) ..."
# `down` with no service names tears down every service in compose.yaml — this already includes
# the EN006/ADR-004 observability stack (otel-collector, jaeger, prometheus, grafana); no separate
# teardown step is needed for them.
"${DC[@]}" "${ENV_ARGS[@]}" -f "${COMPOSE_FILE}" down --remove-orphans

# Legacy cleanup: earlier versions ran host processes and wrote PID files here.
rmdir "${PLATFORM_DIR}/.run" 2>/dev/null || true

info "done. PostgreSQL data volume kept (use 'down -v' to remove it)."
