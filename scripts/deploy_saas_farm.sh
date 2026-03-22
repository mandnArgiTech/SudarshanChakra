#!/usr/bin/env bash
# Provision a new tenant farm via API (through api-gateway or auth-service).
#
# Required: SUPER_ADMIN_TOKEN — JWT with role super_admin (and farms:manage if enforced downstream).
#
# Optional env:
#   API_BASE   — default http://localhost:8080/api/v1 (gateway) or set http://localhost:8083/api/v1 for auth direct
#   OWNER_NAME CONTACT_PHONE CONTACT_EMAIL
#
# Example:
#   export SUPER_ADMIN_TOKEN="eyJ..."
#   ./scripts/deploy_saas_farm.sh --name "AquaFarm" --slug aquafarm --plan full
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080/api/v1}"
TOKEN="${SUPER_ADMIN_TOKEN:-}"
NAME=""
SLUG=""
PLAN="full"
MODULES_JSON='["alerts","cameras","sirens","water","pumps","zones","devices","workers","analytics"]'

usage() {
  echo "Usage: $0 --name <farm name> --slug <slug> [--plan <subscription_plan>]" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name) NAME="$2"; shift 2 ;;
    --slug) SLUG="$2"; shift 2 ;;
    --plan) PLAN="$2"; shift 2 ;;
    --modules-json) MODULES_JSON="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "Unknown option: $1" >&2; usage ;;
  esac
done

if [[ -z "$TOKEN" ]]; then
  echo "Set SUPER_ADMIN_TOKEN to a JWT for role super_admin" >&2
  exit 1
fi
if [[ -z "$NAME" || -z "$SLUG" ]]; then
  usage
fi

OWNER_NAME="${OWNER_NAME:-}"
CONTACT_PHONE="${CONTACT_PHONE:-}"
CONTACT_EMAIL="${CONTACT_EMAIL:-}"

# Build JSON body (jq optional)
if command -v jq >/dev/null 2>&1; then
  BODY=$(jq -n \
    --arg name "$NAME" \
    --arg slug "$SLUG" \
    --arg plan "$PLAN" \
    --argjson modules "$MODULES_JSON" \
    --arg owner "$OWNER_NAME" \
    --arg phone "$CONTACT_PHONE" \
    --arg email "$CONTACT_EMAIL" \
    '{name:$name, slug:$slug, subscriptionPlan:$plan, modulesEnabled:$modules}
      + (if $owner != "" then {ownerName:$owner} else {} end)
      + (if $phone != "" then {contactPhone:$phone} else {} end)
      + (if $email != "" then {contactEmail:$email} else {} end)')
else
  BODY=$(printf '%s' "{\"name\":\"${NAME//\"/\\\"}\",\"slug\":\"${SLUG//\"/\\\"}\",\"subscriptionPlan\":\"${PLAN//\"/\\\"}\",\"modulesEnabled\":${MODULES_JSON}}")
fi

echo "POST ${API_BASE}/farms"
curl -sS -X POST "${API_BASE}/farms" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${BODY}" | tee /dev/stderr
echo
