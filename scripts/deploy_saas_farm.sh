#!/usr/bin/env bash
# Provision a new tenant farm via auth-service API (super_admin JWT required).
# Example:
#   export SUPER_ADMIN_TOKEN="eyJ..."
#   ./scripts/deploy_saas_farm.sh --name "AquaFarm" --slug aquafarm --plan water_only
set -euo pipefail
API="${API_BASE:-http://localhost:8080/api/v1}"
TOKEN="${SUPER_ADMIN_TOKEN:-}"
if [[ -z "$TOKEN" ]]; then
  echo "Set SUPER_ADMIN_TOKEN to a JWT for role super_admin" >&2
  exit 1
fi
echo "POST $API/farms — implement body from FarmRequest (name, slug, subscription_plan, modules_enabled, optional initial admin)."
echo "This script is a stub; use curl or the dashboard once logged in as super_admin."
