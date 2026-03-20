#!/usr/bin/env bash
# ============================================================================
# Register Camera in Backend Device Service
# ============================================================================
# Usage:
#   ./scripts/register_camera.sh <camera-id> <name> <rtsp-url> [node-id]
#
# Example:
#   ./scripts/register_camera.sh cam-01 "Test Camera" \
#     "rtsp://user:pass@192.168.1.100:554/stream2" edge-node-local
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

API_BASE="${API_BASE:-http://localhost:9080/api/v1}"
AUTH_USER="${AUTH_USER:-admin}"
AUTH_PASS="${AUTH_PASS:-admin}"

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 <camera-id> <name> <rtsp-url> [node-id] [model]"
  echo ""
  echo "Example:"
  echo "  $0 cam-tapo-01 'Tapo C110' 'rtsp://admin:pass@192.168.68.56:554/stream2' edge-node-local 'TP-Link Tapo C110'"
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
echo ""

# Get auth token
echo "Authenticating..."
AUTH_RESPONSE=$(curl -s -X POST "${API_BASE}/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${AUTH_USER}\",\"password\":\"${AUTH_PASS}\"}")

if ! echo "${AUTH_RESPONSE}" | grep -q "token"; then
  echo "ERROR: Authentication failed. Response:"
  echo "${AUTH_RESPONSE}"
  exit 1
fi

TOKEN=$(echo "${AUTH_RESPONSE}" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [[ -z "${TOKEN}" ]]; then
  echo "ERROR: Could not extract token from response"
  exit 1
fi

echo "✓ Authenticated"
echo ""

# Register camera
echo "Registering camera..."
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
  "status": "online"
}
EOF
)

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE}/cameras" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${CAMERA_JSON}")

HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
BODY=$(echo "${RESPONSE}" | sed '$d')

if [[ "${HTTP_CODE}" == "200" ]] || [[ "${HTTP_CODE}" == "201" ]]; then
  echo "✓ Camera registered successfully"
  echo "${BODY}" | python3 -m json.tool 2>/dev/null || echo "${BODY}"
else
  echo "ERROR: Registration failed (HTTP ${HTTP_CODE})"
  echo "${BODY}"
  exit 1
fi
