#!/usr/bin/env bash
# Orchestrate test layers: edge, backend, dashboard, optional e2e
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

echo "=== Edge (pytest) ==="
(cd edge && python3 -m pytest tests/ -v --tb=short -q) || exit 1

echo "=== Backend (Gradle test) ==="
(cd backend && ./gradlew test --no-daemon -q) || exit 1

echo "=== Dashboard (vitest) ==="
if [[ -d dashboard/node_modules ]]; then
  (cd dashboard && npm run test -- --run) || exit 1
else
  echo "Skip dashboard (npm install in dashboard/)"
fi

if [[ "${RUN_E2E:-}" == "1" ]] && [[ -f e2e/run_e2e.sh ]]; then
  echo "=== E2E ==="
  ./e2e/run_e2e.sh || exit 1
fi

echo "=== All requested tests passed ==="
