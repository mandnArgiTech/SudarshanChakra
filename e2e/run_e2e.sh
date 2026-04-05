#!/usr/bin/env bash
# Legacy: bring up optional docker compose, then delegate to the full orchestrator.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
docker compose -f docker-compose.e2e.yml up -d --wait 2>/dev/null || true
echo "E2E: use ./e2e/run_full_e2e.sh --config e2e/config/e2e_config.yml (see docs/garuda/stories/G-14_E2E_SUITE.md)"
exec "$SCRIPT_DIR/run_full_e2e.sh" "$@"
