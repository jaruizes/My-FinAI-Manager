#!/usr/bin/env bash
#
# EN001 — Canonical local entry point to STOP the My-FinAI-Manager platform.
#
# Stops the backend and frontend background processes (if running) and tears down local
# infrastructure. Safe to run when nothing is running (no-op, exit 0).

set -euo pipefail

PLATFORM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="${PLATFORM_DIR}/.run"
COMPOSE_FILE="${PLATFORM_DIR}/infrastructure/local/compose.yaml"
ENV_FILE="${PLATFORM_DIR}/infrastructure/local/.env"

info() { echo "[stop] $*"; }

stop_pid() {
  local name="$1" pidfile="${RUN_DIR}/$1.pid"
  [[ -f "${pidfile}" ]] || { info "${name}: not running."; return 0; }
  local pid
  pid="$(cat "${pidfile}")"
  if kill -0 "${pid}" 2>/dev/null; then
    info "stopping ${name} (PID ${pid}) ..."
    # kill the whole process group so child JVM / node processes go too
    kill -TERM "-${pid}" 2>/dev/null || kill -TERM "${pid}" 2>/dev/null || true
    for _ in $(seq 1 20); do kill -0 "${pid}" 2>/dev/null || break; sleep 0.5; done
    kill -9 "${pid}" 2>/dev/null || true
  else
    info "${name}: stale PID file, cleaning up."
  fi
  rm -f "${pidfile}"
}

stop_pid backend
stop_pid frontend

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  if docker compose version >/dev/null 2>&1; then DC=(docker compose); else DC=(docker-compose); fi
  info "stopping local infrastructure ..."
  ENV_ARGS=()
  [[ -f "${ENV_FILE}" ]] && ENV_ARGS=(--env-file "${ENV_FILE}")
  "${DC[@]}" "${ENV_ARGS[@]}" -f "${COMPOSE_FILE}" down
else
  info "Docker not available; skipping infrastructure teardown."
fi

rmdir "${RUN_DIR}" 2>/dev/null || true
info "done."
