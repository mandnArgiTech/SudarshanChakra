#!/usr/bin/env bash
# Provision a tenant farm via auth API and optionally start the matching Docker Compose profile (G-18).
#
# Requires: SUPER_ADMIN_TOKEN — JWT with ROLE_SUPER_ADMIN for POST /api/v1/farms.
#
# Env:
#   API_BASE              — default http://localhost:9080/api/v1 (nginx on VPS)
#   SUPER_ADMIN_TOKEN     — Bearer token (or pass via stdin is NOT supported; use env)
#   INITIAL_ADMIN_PASSWORD — optional; else generated unless --admin-password is set
#
# Example:
#   export SUPER_ADMIN_TOKEN="eyJ..."
#   ./scripts/deploy_saas_farm.sh \
#     --plan water_only \
#     --farm-name "Test Farm" \
#     --slug test-farm \
#     --admin-user testadmin \
#     --admin-email test@example.com
#
# Options:
#   --no-compose          Do not run docker compose (API only)
#   --admin-password STR  Initial farm admin password (min length enforced by API)
#   --name                Alias for --farm-name
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CLOUD_DIR="${REPO_ROOT}/cloud"

API_BASE="${API_BASE:-http://localhost:9080/api/v1}"
TOKEN="${SUPER_ADMIN_TOKEN:-}"
PLAN="full"
FARM_NAME=""
SLUG=""
ADMIN_USER=""
ADMIN_EMAIL=""
ADMIN_PASSWORD="${INITIAL_ADMIN_PASSWORD:-}"
NO_COMPOSE=false
GENERATED_PASSWORD=false

usage() {
  echo "Usage: $0 --plan <full|security|monitoring|water_only> --farm-name <name> --slug <slug> \\" >&2
  echo "         --admin-user <user> --admin-email <email> [--admin-password <pwd>] [--no-compose]" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --plan) PLAN="$2"; shift 2 ;;
    --farm-name|--name) FARM_NAME="$2"; shift 2 ;;
    --slug) SLUG="$2"; shift 2 ;;
    --admin-user) ADMIN_USER="$2"; shift 2 ;;
    --admin-email) ADMIN_EMAIL="$2"; shift 2 ;;
    --admin-password) ADMIN_PASSWORD="$2"; shift 2 ;;
    --no-compose) NO_COMPOSE=true; shift ;;
    -h|--help) usage ;;
    *) echo "Unknown option: $1" >&2; usage ;;
  esac
done

die() { echo "[ERROR] $*" >&2; exit 1; }

[[ -n "$TOKEN" ]] || die "Set SUPER_ADMIN_TOKEN to a JWT for super_admin"
[[ -n "$FARM_NAME" && -n "$SLUG" && -n "$ADMIN_USER" && -n "$ADMIN_EMAIL" ]] || usage

case "$PLAN" in
  full|security|monitoring|water_only) ;;
  *) die "Invalid --plan (use full, security, monitoring, water_only)" ;;
esac

if [[ -z "$ADMIN_PASSWORD" ]]; then
  if command -v openssl >/dev/null 2>&1; then
    ADMIN_PASSWORD="$(openssl rand -base64 18)"
    GENERATED_PASSWORD=true
  else
    die "Set --admin-password or INITIAL_ADMIN_PASSWORD, or install openssl to auto-generate"
  fi
fi

# modulesEnabled per plan (align with ModuleConstants / gateway)
case "$PLAN" in
  full)
    MODULES_JSON='["alerts","cameras","sirens","water","pumps","zones","devices","workers","analytics","mdm"]'
    ;;
  security)
    MODULES_JSON='["alerts","cameras","sirens","water","pumps","zones","devices","workers","analytics"]'
    ;;
  monitoring)
    MODULES_JSON='["alerts","cameras","water","pumps","zones","devices","workers","analytics"]'
    ;;
  water_only)
    MODULES_JSON='["alerts","water","pumps","zones","devices"]'
    ;;
esac

TMP_BODY="$(mktemp)"
TMP_CODE="$(mktemp)"
trap 'rm -f "$TMP_BODY" "$TMP_CODE"' EXIT

if command -v jq >/dev/null 2>&1; then
  BODY=$(jq -n \
    --arg name "$FARM_NAME" \
    --arg slug "$SLUG" \
    --arg plan "$PLAN" \
    --argjson modules "$MODULES_JSON" \
    --arg u "$ADMIN_USER" \
    --arg p "$ADMIN_PASSWORD" \
    --arg e "$ADMIN_EMAIL" \
    '{
      name: $name,
      slug: $slug,
      subscriptionPlan: $plan,
      modulesEnabled: $modules,
      initialAdminUsername: $u,
      initialAdminPassword: $p,
      initialAdminEmail: $e
    }')
else
  # Minimal fallback without jq (no escaping beyond basic — avoid quotes in input)
  BODY=$(printf '%s' "{\"name\":\"${FARM_NAME//\"/\\\"}\",\"slug\":\"${SLUG//\"/\\\"}\",\"subscriptionPlan\":\"${PLAN}\",\"modulesEnabled\":${MODULES_JSON},\"initialAdminUsername\":\"${ADMIN_USER//\"/\\\"}\",\"initialAdminPassword\":\"${ADMIN_PASSWORD//\"/\\\"}\",\"initialAdminEmail\":\"${ADMIN_EMAIL//\"/\\\"}\"}")
fi

echo "[INFO] POST ${API_BASE}/farms (plan=${PLAN})"
HTTP_CODE=$(curl -sS -o "$TMP_BODY" -w "%{http_code}" -X POST "${API_BASE}/farms" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${BODY}")

cat "$TMP_BODY" >&2
echo >&2

[[ "$HTTP_CODE" -ge 200 && "$HTTP_CODE" -lt 300 ]] || die "Farm create failed (HTTP ${HTTP_CODE})"

if [[ "$GENERATED_PASSWORD" == true ]]; then
  echo "[INFO] Generated initial admin password for ${ADMIN_USER} (save this): ${ADMIN_PASSWORD}" >&2
fi

if [[ "$NO_COMPOSE" == true ]]; then
  echo "[DONE] Farm provisioned; skipped docker compose (--no-compose)"
  exit 0
fi

[[ -f "${CLOUD_DIR}/docker-compose.vps.yml" ]] || die "Missing ${CLOUD_DIR}/docker-compose.vps.yml"

echo "[INFO] docker compose -f docker-compose.vps.yml --profile ${PLAN} up -d"
(
  cd "$CLOUD_DIR" || exit 1
  docker compose -f docker-compose.vps.yml --profile "$PLAN" up -d
)

echo "[DONE] Farm provisioned and stack started with profile ${PLAN}"
