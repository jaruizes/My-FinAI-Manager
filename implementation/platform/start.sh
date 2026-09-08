#!/usr/bin/env bash
# Canonical entry point: build and start the complete local platform (EN001 FR-010).
# Brings up every application and observability container and waits — using the
# containers' own health checks, not fixed sleeps — until the platform is ready.
set -euo pipefail

# The local platform runs on the host's native architecture. Don't inherit a
# cross-platform override (some setups export DOCKER_DEFAULT_PLATFORM=linux/amd64
# for unrelated work) — emulated images would be slow and may not be cached.
unset DOCKER_DEFAULT_PLATFORM

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/infrastructure/compose.yaml"
TIMEOUT="${PLATFORM_START_TIMEOUT:-360}"

# Base compose args, plus any extra override file (used by tests / customisation).
COMPOSE=(docker compose -f "$COMPOSE_FILE")
[ -n "${PLATFORM_COMPOSE_OVERRIDE:-}" ] && COMPOSE+=(-f "$PLATFORM_COMPOSE_OVERRIDE")

# Build the application images with the classic builder. We do not use
# `docker compose build`, which requires a newer buildx than is always present.
# Set PLATFORM_SKIP_BUILD=1 to reuse existing images.
if [ "${PLATFORM_SKIP_BUILD:-0}" != "1" ]; then
  echo "▶ Building application images…"
  docker build -t myfinaimanager/core-service:local "$SCRIPT_DIR/backend/core-service"
  docker build -t myfinaimanager/frontend:local "$SCRIPT_DIR/frontend/web"
fi

echo "▶ Starting the My-FinAI-Manager platform…"
if "${COMPOSE[@]}" up -d --no-build --wait --wait-timeout "$TIMEOUT"; then
  cat <<'EOF'

✔ Platform is ready.

  Frontend (Home)        http://localhost:8080
  Business API           http://localhost:8081/api/v1/hello
  OpenAPI document       http://localhost:8081/v3/api-docs
  Jaeger (traces)        http://localhost:16686
  Prometheus (metrics)   http://localhost:9090
  Grafana (dashboards)   http://localhost:3000   (anonymous viewing enabled; admin / admin)

Run the browser E2E:  implementation/platform/e2e.sh
Stop the platform:    implementation/platform/stop.sh
EOF
  exit 0
fi

echo >&2
echo "✖ The platform did not become ready within ${TIMEOUT}s. It is NOT ready." >&2
echo >&2
"${COMPOSE[@]}" ps >&2 || true
for svc in $("${COMPOSE[@]}" config --services); do
  cid="$("${COMPOSE[@]}" ps -q "$svc" 2>/dev/null || true)"
  [ -z "$cid" ] && { echo "   $svc: not created" >&2; continue; }
  state="$(docker inspect -f '{{.State.Status}}{{if .State.Health}} ({{.State.Health.Status}}){{end}}' "$cid" 2>/dev/null || echo unknown)"
  case "$state" in
    "running (healthy)"|running) ;;
    *)
      echo "   --- $svc: $state — last 40 log lines ---" >&2
      "${COMPOSE[@]}" logs --tail 40 "$svc" >&2 || true
      ;;
  esac
done
exit 1
