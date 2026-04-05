#!/usr/bin/env bash
# Full E2E orchestrator: preflight → pytest → Playwright (optional) → Maestro (optional)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

CONFIG="${E2E_CONFIG:-e2e/config/e2e_config.yml}"
SKIP_PREFLIGHT=0
SKIP_PLAYWRIGHT=0
SKIP_MAESTRO=0
SKIP_PYTEST=0

usage() {
  echo "Usage: $0 [--config PATH] [--skip-preflight] [--skip-pytest] [--skip-playwright] [--skip-maestro]"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --config)
      CONFIG="$2"
      shift 2
      ;;
    --skip-preflight) SKIP_PREFLIGHT=1; shift ;;
    --skip-pytest) SKIP_PYTEST=1; shift ;;
    --skip-playwright) SKIP_PLAYWRIGHT=1; shift ;;
    --skip-maestro) SKIP_MAESTRO=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1"; usage; exit 1 ;;
  esac
done

if [[ "$CONFIG" = /* ]]; then
  export E2E_CONFIG="$CONFIG"
else
  export E2E_CONFIG="$REPO_ROOT/$CONFIG"
fi
export PYTHONPATH="$REPO_ROOT"

# Surface URLs for Playwright / tools when config exists
if [[ -f "$E2E_CONFIG" ]]; then
  eval "$(python3 - "$E2E_CONFIG" <<'PY'
import sys, shlex
try:
    import yaml
except ImportError:
    sys.exit(0)
path = sys.argv[1]
with open(path) as f:
    cfg = yaml.safe_load(f) or {}
be = cfg.get("backend") or {}
ed = cfg.get("edge") or {}
auth = cfg.get("auth") or {}
if be.get("api_base"):
    print(f"export E2E_API={shlex.quote(str(be['api_base']))}")
if be.get("dashboard_url"):
    print(f"export E2E_DASHBOARD_URL={shlex.quote(str(be['dashboard_url']))}")
if ed.get("flask_url"):
    print(f"export E2E_FLASK_URL={shlex.quote(str(ed['flask_url']))}")
if auth.get("admin_user"):
    print(f"export E2E_ADMIN_USER={shlex.quote(str(auth['admin_user']))}")
if auth.get("admin_pass"):
    print(f"export E2E_ADMIN_PASS={shlex.quote(str(auth['admin_pass']))}")
PY
)"
fi

if [[ $SKIP_PREFLIGHT -eq 0 ]]; then
  echo "==> Preflight: python3 e2e/preflight_check.py --config $E2E_CONFIG"
  python3 e2e/preflight_check.py --config "$E2E_CONFIG" || exit 1
fi

if [[ $SKIP_PYTEST -eq 0 ]]; then
  echo "==> pytest e2e/tests"
  python3 -m pytest e2e/tests -v --tb=short || exit 1
fi

if [[ $SKIP_PLAYWRIGHT -eq 0 ]] && [[ "${E2E_SKIP_PLAYWRIGHT:-0}" != "1" ]] && [[ -f e2e/playwright/package.json ]]; then
  echo "==> Playwright"
  (cd e2e/playwright && npm ci --no-audit --fund=false 2>/dev/null || npm install --no-audit --fund=false)
  (cd e2e/playwright && npx playwright install --with-deps chromium 2>/dev/null || npx playwright install chromium)
  (cd e2e/playwright && npx playwright test) || exit 1
fi

if [[ $SKIP_MAESTRO -eq 0 ]] && [[ "${E2E_RUN_MAESTRO:-}" =~ ^(1|true|yes)$ ]]; then
  command -v maestro >/dev/null 2>&1 || { echo "==> Maestro skipped (maestro not installed)"; }
  if command -v maestro >/dev/null 2>&1 && [[ -d e2e/maestro/flows ]]; then
    echo "==> Maestro flows (E2E_RUN_MAESTRO=1)"
    for f in e2e/maestro/flows/*.yaml; do
      [[ -e "$f" ]] || continue
      [[ "$(basename "$f")" == "server_config.yaml" ]] && continue
      maestro test "$f" || exit 1
    done
  fi
fi

echo "==> run_full_e2e.sh finished OK"
