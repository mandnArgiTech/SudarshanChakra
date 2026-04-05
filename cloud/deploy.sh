#!/usr/bin/env bash
# ==============================================================================
# SudarshanChakra — VPS deployment script
# ==============================================================================
# Builds all images (backend + dashboard), then runs docker-compose.vps.yml.
# Run from repo root. Requires: Docker, docker compose. Images are built inside
# Docker (no host Java/Node required for build).
#
# Compose profiles (G-18): services require an active profile (default full).
# Use SC_COMPOSE_PROFILE=security or --profile monitoring etc. For farm simulator
# also pass --profile dev (or set SC_COMPOSE_EXTRA_PROFILES=dev).
#
# Usage:
#   ./cloud/deploy.sh                    # build and deploy (--profile full)
#   ./cloud/deploy.sh --profile security # subset without mdm
#   ./cloud/deploy.sh --no-build        # deploy only (existing images)
#   ./cloud/deploy.sh --build-only      # build only, do not start stack
# ==============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLOUD_DIR="${ROOT_DIR}/cloud"
BACKEND_DIR="${ROOT_DIR}/backend"
DASHBOARD_DIR="${ROOT_DIR}/dashboard"
COMPOSE_FILE="${CLOUD_DIR}/docker-compose.vps.yml"

die() { echo "[ERROR] $*" >&2; exit 1; }

NO_BUILD=false
BUILD_ONLY=false
COMPOSE_PROFILE="${SC_COMPOSE_PROFILE:-full}"
EXTRA_PROFILES=()
if [[ -n "${SC_COMPOSE_EXTRA_PROFILES:-}" ]]; then
  # space-separated e.g. SC_COMPOSE_EXTRA_PROFILES=dev
  read -r -a EXTRA_PROFILES <<< "${SC_COMPOSE_EXTRA_PROFILES}"
fi

RAW_ARGS=("$@")
i=0
while [[ $i -lt ${#RAW_ARGS[@]} ]]; do
  case "${RAW_ARGS[$i]}" in
    --no-build)   NO_BUILD=true ;;
    --build-only) BUILD_ONLY=true ;;
    --profile)
      ((i += 1)) || true
      COMPOSE_PROFILE="${RAW_ARGS[$i]:-}"
      if [[ -z "$COMPOSE_PROFILE" ]]; then
        echo "[ERROR] --profile requires a value" >&2
        exit 1
      fi
      ;;
    --profile=*)
      COMPOSE_PROFILE="${RAW_ARGS[$i]#--profile=}"
      ;;
    -h|--help)
      echo "Usage: $0 [--profile <full|security|monitoring|water_only>] [--no-build] [--build-only]"
      echo "  --profile NAME   Compose profile (default: full, or SC_COMPOSE_PROFILE)"
      echo "  Extra profiles: set SC_COMPOSE_EXTRA_PROFILES=dev for simulator"
      echo "  --no-build       Use existing images; do not build"
      echo "  --build-only     Build images only; do not start stack"
      exit 0
      ;;
    *)
      die "Unknown option: ${RAW_ARGS[$i]}"
      ;;
  esac
  ((i += 1)) || true
done

# -----------------------------------------------------------------------------
# 1. Ensure cloud/.env exists
# -----------------------------------------------------------------------------
if [[ ! -f "${CLOUD_DIR}/.env" ]]; then
  if [[ -f "${CLOUD_DIR}/.env.example" ]]; then
    cp "${CLOUD_DIR}/.env.example" "${CLOUD_DIR}/.env"
    echo "[INFO] Created ${CLOUD_DIR}/.env from .env.example — please set DB_PASS, RABBITMQ_PASS, JWT_SECRET"
  else
    die "Missing ${CLOUD_DIR}/.env and no .env.example found"
  fi
fi

# Load env for RabbitMQ init and compose
set -a
# shellcheck source=/dev/null
source "${CLOUD_DIR}/.env"
set +a

export DB_PASS="${DB_PASS:?Set DB_PASS in cloud/.env}"
export RABBITMQ_PASS="${RABBITMQ_PASS:?Set RABBITMQ_PASS in cloud/.env}"
export JWT_SECRET="${JWT_SECRET:?Set JWT_SECRET in cloud/.env}"

# -----------------------------------------------------------------------------
# 2. Build images (unless --no-build)
# -----------------------------------------------------------------------------
build_backend() {
  local service="$1"
  echo "[BUILD] Backend: ${service}"
  docker build -f "${BACKEND_DIR}/${service}/Dockerfile" -t "sudarshanchakra/${service}:latest" "${BACKEND_DIR}"
}

build_all() {
  if [[ "$NO_BUILD" == true && "$BUILD_ONLY" != true ]]; then
    echo "[SKIP] Build (--no-build)"
    return
  fi

  echo "[BUILD] Backend images (context: backend/)"
  build_backend alert-service
  build_backend device-service
  build_backend auth-service
  build_backend siren-service
  build_backend mdm-service
  build_backend api-gateway

  echo "[BUILD] Dashboard"
  docker build -t sudarshanchakra/dashboard:latest "${DASHBOARD_DIR}"
}

build_all

if [[ "$BUILD_ONLY" == true ]]; then
  echo "[DONE] Build only — not starting stack"
  exit 0
fi

# -----------------------------------------------------------------------------
# 3. Start stack
# -----------------------------------------------------------------------------
COMPOSE_BASE="$(basename "${COMPOSE_FILE}")"
PROFILE_ARGS=(--profile "$COMPOSE_PROFILE")
for p in "${EXTRA_PROFILES[@]}"; do
  [[ -n "$p" ]] && PROFILE_ARGS+=(--profile "$p")
done

echo "[DEPLOY] Starting stack with ${COMPOSE_FILE} ${PROFILE_ARGS[*]}"
cd "${CLOUD_DIR}"
docker compose -f "${COMPOSE_BASE}" "${PROFILE_ARGS[@]}" up -d

# -----------------------------------------------------------------------------
# 4. Wait for RabbitMQ and run topology init
# -----------------------------------------------------------------------------
echo "[WAIT] RabbitMQ..."
for i in {1..30}; do
  if docker compose -f "${COMPOSE_BASE}" "${PROFILE_ARGS[@]}" exec -T rabbitmq rabbitmq-diagnostics check_running &>/dev/null; then
    break
  fi
  if [[ $i -eq 30 ]]; then
    die "RabbitMQ did not become ready in time"
  fi
  sleep 2
done

echo "[INIT] RabbitMQ topology"
docker run --rm --network cloud_default \
  -e RABBITMQ_HOST=rabbitmq \
  -e RABBITMQ_USER="${RABBITMQ_USER:-admin}" \
  -e RABBITMQ_PASS="${RABBITMQ_PASS}" \
  -v "${CLOUD_DIR}/scripts/rabbitmq_init.py:/script.py:ro" \
  python:3.12-slim bash -c "pip install -q pika && python /script.py" || true

echo "[DONE] Stack is up (profile: ${COMPOSE_PROFILE}). Dashboard: http://localhost:9080 (or http://<vps-ip>:9080). API: http://localhost:9080/api/v1/"
echo "       HTTPS (with LE certs + default nginx-vps.conf): https://<your-domain>/ (port 443)"
echo "       Health: curl -s http://localhost:9080/health  |  TLS: curl -sS https://<your-domain>/health"
echo "       No certs yet? Use nginx-vps-http.conf and drop 443 + /etc/letsencrypt from compose — see docs/DEPLOYMENT_GUIDE.md §1.4"
echo "       See docs/DEPLOY_AFTER_BUILD.md and docs/USER_GUIDE.md for how to use."
