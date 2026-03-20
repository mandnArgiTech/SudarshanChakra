#!/usr/bin/env bash
# ============================================================================
# Quick Local Deployment: Backend + Edge on Same Machine
# ============================================================================
# This script automates deployment of backend services and edge AI
# for testing with a single camera.
#
# Usage:
#   ./scripts/deploy_local.sh [--skip-infra] [--skip-backend] [--skip-edge]
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

SKIP_INFRA=0
SKIP_BACKEND=0
SKIP_EDGE=0

for arg in "$@"; do
  case "${arg}" in
    --skip-infra) SKIP_INFRA=1 ;;
    --skip-backend) SKIP_BACKEND=1 ;;
    --skip-edge) SKIP_EDGE=1 ;;
    *) echo "Unknown option: ${arg}"; exit 1 ;;
  esac
done

cd "${ROOT_DIR}"

echo "=========================================="
echo "SudarshanChakra Local Deployment"
echo "=========================================="
echo ""

# Step 1: Infrastructure
if [[ "${SKIP_INFRA}" == "0" ]]; then
  echo "[1/3] Starting infrastructure (PostgreSQL + RabbitMQ)..."
  ./setup_and_build_all.sh deploy-infra || {
    echo "ERROR: Infrastructure deployment failed"
    exit 1
  }
  echo "✓ Infrastructure ready"
  echo ""
else
  echo "[1/3] Skipping infrastructure (--skip-infra)"
  echo ""
fi

# Step 2: Backend Services
if [[ "${SKIP_BACKEND}" == "0" ]]; then
  echo "[2/3] Starting backend services..."
  
  # Build first
  echo "  Building backend..."
  (cd backend && ./gradlew build -x test --no-daemon) || {
    echo "ERROR: Backend build failed"
    exit 1
  }
  
  # Start services
  echo "  Starting services (logs in logs/)..."
  ./setup_and_build_all.sh deploy-backend || {
    echo "ERROR: Backend deployment failed"
    exit 1
  }
  
  echo "✓ Backend services running"
  echo "  - API Gateway: http://localhost:8080"
  echo "  - (via nginx): http://localhost:9080"
  echo ""
  
  # Wait for services to be ready
  echo "  Waiting for services to be ready..."
  sleep 5
  
  # Register camera if backend is accessible
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "  Registering camera in backend..."
    if [[ -f "${SCRIPT_DIR}/register_camera.sh" ]]; then
      "${SCRIPT_DIR}/register_camera.sh" \
        "cam-tapo-c110-01" \
        "Test Camera (Tapo C110)" \
        "rtsp://administrator:interOP@123@192.168.68.56:554/stream2" \
        "edge-node-local" \
        "TP-Link Tapo C110" || echo "  WARN: Camera registration failed (may already exist)"
    fi
  else
    echo "  WARN: Backend not ready yet — register camera manually later"
  fi
  echo ""
else
  echo "[2/3] Skipping backend (--skip-backend)"
  echo ""
fi

# Step 3: Edge AI
if [[ "${SKIP_EDGE}" == "0" ]]; then
  echo "[3/3] Starting edge AI..."
  
  cd edge
  
  # Check Python venv
  if [[ ! -d ".venv" ]]; then
    echo "  Creating Python virtual environment..."
    python3 -m venv .venv
  fi
  
  echo "  Activating venv and installing dependencies..."
  source .venv/bin/activate
  pip install -q -r requirements.txt
  
  # Set environment
  export NODE_ID=edge-node-local
  export VPN_BROKER_IP=localhost
  export MQTT_PORT=1883
  export MQTT_USER=admin
  export MQTT_PASS=devpassword123
  export CONFIG_DIR="${ROOT_DIR}/edge/config"
  export MODEL_DIR="${ROOT_DIR}/edge/models"
  export FLASK_PORT=5000
  export LORA_ENABLED=false  # Disable LoRa for local testing
  
  echo "  Starting edge node..."
  echo "  - Flask GUI: http://localhost:5000"
  echo "  - MQTT Broker: localhost:1883"
  echo "  - Camera: 192.168.68.56 (Tapo C110)"
  echo ""
  echo "Press Ctrl+C to stop edge node"
  echo ""
  
  python3 farm_edge_node.py
else
  echo "[3/3] Skipping edge (--skip-edge)"
  echo ""
fi

echo "=========================================="
echo "Deployment complete!"
echo "=========================================="
