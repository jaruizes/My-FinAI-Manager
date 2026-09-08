#!/usr/bin/env bash
# Canonical entry point: stop the complete local platform (EN001 FR-011).
# Safe to run when the platform is already stopped (AC-013).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/infrastructure/compose.yaml"

# --volumes removes the ephemeral PostgreSQL data so the next start is clean;
# Flyway re-seeds the platform version on every boot.
docker compose -f "$COMPOSE_FILE" down --remove-orphans --volumes

echo "✔ Platform stopped."
