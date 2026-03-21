#!/usr/bin/env bash
# ============================================================================
# Register Camera in Backend Device Service
# ============================================================================
# PREREQUISITE: device-service OR api-gateway must already be running.
#   This script does NOT start Java. "Connection refused" = start backend first.
#
# Usage:
#   ./scripts/register_camera.sh <camera-id> <name> <rtsp-url> [node-id] [model]
#
# Environment:
#   API_BASE   — if unset, probes :8080 → :9080 → :8082 on 127.0.0.1
#   SKIP_AUTH  — 1 = no JWT (default; device-service allows local POST)
#   AUTH_USER / AUTH_PASS — when SKIP_AUTH=0
#
# Example:
#   ./scripts/register_camera.sh cam-tapo-01 "Tapo C110" \
#     "rtsp://admin:pass@192.168.68.56:554/stream2" edge-node-a "TP-Link Tapo C110"
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"

AUTH_USER="${AUTH_USER:-admin}"
AUTH_PASS="${AUTH_PASS:-admin}"
SKIP_AUTH="${SKIP_AUTH:-1}"

print_backend_not_running_help() {
  local base="$1"
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  BACKEND NOT RUNNING — connection refused"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo ""
  echo "  This script only talks to the HTTP API. It does NOT start Spring Boot."
  echo "  Nothing is accepting connections at:"
  echo "    ${base}"
  echo ""
  echo "  Start the service first, wait for 'Started ...Application', then re-run."
  echo ""
  echo "  Camera API lives in device-service (port 8082), usually via gateway (8080)."
  echo ""
  echo "  --- Option A: only device-service (needs PostgreSQL) ---"
  echo "    cd ${BACKEND_DIR}"
  echo "    ./gradlew :device-service:bootRun"
  echo ""
  echo "  --- Option B: full local backend (from repo root) ---"
  echo "    ./setup_and_build_all.sh deploy-backend"
  echo ""
  echo "  --- Verify something is listening ---"
  echo "    ss -tlnp | grep -E ':8080|:8082' || true"
  echo "    curl -sS -o /dev/null -w 'HTTP %{http_code}\\n' --connect-timeout 2 ${base}/nodes"
  echo ""
}

# Returns 0 if we get any HTTP status (including 500) — i.e. TCP + HTTP responded.
# Returns 1 only when curl cannot connect (connection refused, timeout).
api_reachable() {
  local base="$1" code
  code=$(curl -sS -o /dev/null -w "%{http_code}" --connect-timeout 3 "${base}/nodes" 2>/dev/null) || true
  [[ -n "${code}" && "${code}" != "000" ]]
}

discover_api_base() {
  local b code
  for b in \
    "http://127.0.0.1:8080/api/v1" \
    "http://127.0.0.1:9080/api/v1" \
    "http://127.0.0.1:8082/api/v1"; do
    code=$(curl -sS -o /dev/null -w "%{http_code}" --connect-timeout 2 "${b}/nodes" 2>/dev/null) || code=000
    if [[ -n "${code}" && "${code}" != "000" ]]; then
      echo "${b}"
      return 0
    fi
  done
  return 1
}

if [[ -z "${API_BASE:-}" ]]; then
  echo "Detecting API (GET /nodes on 127.0.0.1:8080 → :9080 → :8082)..."
  if ! API_BASE="$(discover_api_base)"; then
    echo "ERROR: Nothing answered on 8080, 9080, or 8082."
    print_backend_not_running_help "http://127.0.0.1:8082/api/v1"
    exit 1
  fi
  echo "  Using ${API_BASE}"
  echo ""
fi

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 <camera-id> <name> <rtsp-url> [node-id] [model]"
  echo ""
  echo "Prerequisite: backend running (see messages above if connection fails)."
  echo "Node ID must exist in edge_nodes (e.g. edge-node-a from init.sql)."
  exit 1
fi

CAMERA_ID="$1"
CAMERA_NAME="$2"
RTSP_URL="$3"
NODE_ID="${4:-edge-node-local}"
MODEL="${5:-Unknown}"

echo "Registering camera: ${CAMERA_ID}"
echo "  Name: ${CAMERA_NAME}"
echo "  RTSP: ${RTSP_URL}"
echo "  Node: ${NODE_ID}"
echo "  Model: ${MODEL}"
echo "  API:  ${API_BASE}"
echo ""

if ! api_reachable "${API_BASE}"; then
  print_backend_not_running_help "${API_BASE}"
  exit 1
fi
echo "✓ API reachable (${API_BASE}/nodes)"
echo ""

CAMERA_JSON=$(cat <<EOF
{
  "id": "${CAMERA_ID}",
  "nodeId": "${NODE_ID}",
  "name": "${CAMERA_NAME}",
  "rtspUrl": "${RTSP_URL}",
  "model": "${MODEL}",
  "fpsTarget": 2.0,
  "resolution": "640x480",
  "enabled": true,
  "status": "active"
}
EOF
)
# DB cameras.status CHECK: active | offline | error | unknown (not "online" — that is for edge_nodes)

post_camera() {
  if [[ -n "${1:-}" ]]; then
    curl -sS -w "\n%{http_code}" -X POST "${API_BASE}/cameras" \
      -H "Authorization: Bearer $1" \
      -H "Content-Type: application/json" \
      -d "${CAMERA_JSON}" || printf '\ncurl_failed\n'
  else
    curl -sS -w "\n%{http_code}" -X POST "${API_BASE}/cameras" \
      -H "Content-Type: application/json" \
      -d "${CAMERA_JSON}" || printf '\ncurl_failed\n'
  fi
}

TOKEN=""
if [[ "${SKIP_AUTH}" != "1" ]]; then
  echo "Authenticating (${API_BASE}/auth/login)..."
  set +e
  AUTH_RESPONSE=$(curl -sS -X POST "${API_BASE}/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${AUTH_USER}\",\"password\":\"${AUTH_PASS}\"}" 2>&1)
  CURL_EC=$?
  set -e
  if [[ ${CURL_EC} -ne 0 ]]; then
    echo "ERROR: Login request failed (curl exit ${CURL_EC})."
    echo "  ${AUTH_RESPONSE}"
    exit 1
  fi
  if ! echo "${AUTH_RESPONSE}" | grep -q '"token"'; then
    echo "ERROR: Authentication failed. Response:"
    echo "${AUTH_RESPONSE}"
    exit 1
  fi
  TOKEN=$(echo "${AUTH_RESPONSE}" | grep -o '"token":"[^"]*' | head -1 | cut -d'"' -f4)
  if [[ -z "${TOKEN}" ]]; then
    echo "ERROR: Could not extract token from response"
    exit 1
  fi
  echo "✓ Authenticated"
  echo ""
else
  echo "SKIP_AUTH=1 — posting without JWT."
  echo ""
fi

echo "POST ${API_BASE}/cameras …"
set +e
if [[ -n "${TOKEN}" ]]; then
  RESPONSE=$(post_camera "${TOKEN}")
else
  RESPONSE=$(post_camera "")
fi
set -e

if echo "${RESPONSE}" | tail -n1 | grep -q "curl_failed"; then
  print_backend_not_running_help "${API_BASE}"
  exit 1
fi

HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
BODY=$(echo "${RESPONSE}" | sed '$d')

if [[ "${HTTP_CODE}" == "200" ]] || [[ "${HTTP_CODE}" == "201" ]]; then
  echo "✓ Camera registered successfully (HTTP ${HTTP_CODE})"
  echo "${BODY}" | python3 -m json.tool 2>/dev/null || echo "${BODY}"
  exit 0
fi

if [[ "${SKIP_AUTH}" == "1" ]] && [[ "${HTTP_CODE}" == "401" || "${HTTP_CODE}" == "403" ]]; then
  echo "Got HTTP ${HTTP_CODE} without auth — try SKIP_AUTH=0 with valid AUTH_USER/AUTH_PASS."
fi

echo "ERROR: Registration failed (HTTP ${HTTP_CODE})"
echo "${BODY}"
exit 1
