#!/usr/bin/env bash
# Canonical entry point: containerized Playwright browser E2E (EN001 FR-012).
# Ensures the platform is healthy, runs Chromium E2E in a container against the
# containerized frontend, and propagates Playwright's exit code. Diagnostics are
# written to implementation/platform/e2e/{playwright-report,test-results}/ (bind
# mounted, git-ignored) and retained on failure.
set -euo pipefail

unset DOCKER_DEFAULT_PLATFORM

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/infrastructure/compose.yaml"
E2E_OVERLAY="$SCRIPT_DIR/infrastructure/compose.e2e.yaml"

# 1. Bring the platform up and wait for readiness (bounded health checks).
"$SCRIPT_DIR/start.sh"

# 2. Run the containerized Playwright suite; do not abort on its non-zero exit.
echo
echo "▶ Running containerized Playwright (Chromium) against http://frontend:8080 …"
set +e
docker compose -f "$COMPOSE_FILE" -f "$E2E_OVERLAY" run --rm playwright
exit_code=$?
set -e

if [[ $exit_code -eq 0 ]]; then
  echo "✔ E2E passed."
else
  echo "✖ E2E failed (exit $exit_code). Diagnostics:" >&2
  echo "   $SCRIPT_DIR/e2e/playwright-report/" >&2
  echo "   $SCRIPT_DIR/e2e/test-results/" >&2
fi

exit $exit_code
