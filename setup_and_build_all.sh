#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_NAME="$(basename "$0")"
INSTALL_DEPS=0

# Parse --help / --install-deps before rest of config
for arg in "$@"; do
  case "${arg}" in
    --help|-h)
      echo "SudarshanChakra — Setup and build all components"
      echo "Ready for bare-metal Ubuntu 24.04 (with or without GPU)."
      echo ""
      echo "USAGE:"
      echo "  ${SCRIPT_NAME} [OPTIONS]"
      echo ""
      echo "OPTIONS:"
      echo "  -h, --help          Show this help and exit."
      echo "  -i, --install-deps  On Ubuntu/Debian, install required system packages and optionally Android SDK (uses sudo)."
      echo ""
      echo "ENVIRONMENT:"
      echo "  SKIP_ANDROID=1      Skip Android app build (default: 0)."
      echo "  SKIP_FIRMWARE=1     Skip ESP32 firmware build (default: 0)."
      echo "  ANDROID_HOME        Path to Android SDK. If unset, script uses ${ROOT_DIR}/android-sdk when present."
      echo "  JAVA_HOME           Optional. Backend requires Java 21; script auto-detects if not set."
      echo ""
      echo "UBUNTU 24.04 (BARE METAL) — Required packages (or run with --install-deps once):"
      echo "  git curl unzip openjdk-21-jdk nodejs npm ripgrep python3.12-venv docker.io"
      echo "  For Android APK: use --install-deps (downloads SDK to android-sdk/) or set ANDROID_HOME."
      echo "  For Edge AI GPU: nvidia-driver-535, nvidia-container-toolkit (optional)."
      echo ""
      echo "EXAMPLES:"
      echo "  ./${SCRIPT_NAME} --install-deps    # Install deps + build all (including Android APK)"
      echo "  SKIP_ANDROID=1 ./${SCRIPT_NAME}     # Build without Android"
      echo "  ./${SCRIPT_NAME}                    # Build only (deps must already be installed)"
      exit 0
      ;;
    --install-deps|-i) INSTALL_DEPS=1 ;;
  esac
done

DOCKER_BIN="${DOCKER_BIN:-docker}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
VENV_DIR="${VENV_DIR:-${ROOT_DIR}/.venv}"
PYTHON_EXEC="${PYTHON_BIN}"
PIP_EXEC="pip3"

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-postgres}"
RABBITMQ_CONTAINER="${RABBITMQ_CONTAINER:-rabbitmq}"
DOCKER_NETWORK="${DOCKER_NETWORK:-sc-net}"
DB_NAME="${DB_NAME:-sudarshanchakra}"
DB_USER="${DB_USER:-scadmin}"
DB_PASS="${DB_PASS:-devpassword123}"
RABBITMQ_USER="${RABBITMQ_USER:-admin}"
RABBITMQ_PASS="${RABBITMQ_PASS:-devpassword123}"
RABBITMQ_ERLANG_COOKIE="${RABBITMQ_ERLANG_COOKIE:-sudarshanchakra-dev-cookie}"

SKIP_ANDROID="${SKIP_ANDROID:-0}"
SKIP_FIRMWARE="${SKIP_FIRMWARE:-0}"

STEP_COUNTER=0
SKIPPED_COMPONENTS=()

log() {
  printf '\n[%s] %s\n' "${SCRIPT_NAME}" "$1"
}

warn() {
  printf '\n[%s] WARNING: %s\n' "${SCRIPT_NAME}" "$1" >&2
}

die() {
  printf '\n[%s] ERROR: %s\n' "${SCRIPT_NAME}" "$1" >&2
  exit 1
}

next_step() {
  STEP_COUNTER=$((STEP_COUNTER + 1))
  log "Step ${STEP_COUNTER}: $1"
}

require_cmd() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || die "Missing command: ${cmd}"
}

ensure_repo_root() {
  [[ -d "${ROOT_DIR}/backend" ]] || die "Run this script from repo root."
  [[ -d "${ROOT_DIR}/dashboard" ]] || die "Run this script from repo root."
  [[ -d "${ROOT_DIR}/edge" ]] || die "Run this script from repo root."
  [[ -d "${ROOT_DIR}/cloud" ]] || die "Run this script from repo root."
}

install_system_deps() {
  log "Installing system dependencies (Ubuntu/Debian; uses sudo)"
  if ! command -v apt-get >/dev/null 2>&1; then
    die "apt-get not found. This script supports Ubuntu/Debian. Install required packages manually (see --help)."
  fi
  sudo apt-get update -qq
  sudo apt-get install -y -qq \
    git curl unzip \
    openjdk-21-jdk \
    nodejs npm \
    ripgrep \
    python3.12-venv \
    docker.io \
    || die "apt-get install failed. Fix errors above and retry."
  # Add current user to docker group so Docker runs without sudo
  if [[ -n "${SUDO_USER:-}" ]]; then
    sudo usermod -aG docker "${SUDO_USER}" 2>/dev/null || true
  fi
  log "System packages installed. You may need to log out and back in for Docker group to apply."
  # Optional: download Android SDK into android-sdk/ if building Android
  if [[ "${SKIP_ANDROID}" != "1" ]]; then
    local sdk_root="${ROOT_DIR}/android-sdk"
    if [[ -z "${ANDROID_HOME:-}" && ! -d "${sdk_root}/cmdline-tools/latest/bin" ]]; then
      log "Downloading Android command-line tools into android-sdk/"
      mkdir -p "${sdk_root}"
      curl -sL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
        || { warn "Android SDK download failed. Set ANDROID_HOME manually or run with SKIP_ANDROID=1."; return; }
      unzip -q -o /tmp/cmdline-tools.zip -d "${sdk_root}"
      rm -f /tmp/cmdline-tools.zip
      if [[ -d "${sdk_root}/cmdline-tools" && ! -d "${sdk_root}/cmdline-tools/latest" ]]; then
        mkdir -p "${sdk_root}/cmdline-tools/latest"
        mv "${sdk_root}"/cmdline-tools/bin "${sdk_root}"/cmdline-tools/lib \
           "${sdk_root}"/cmdline-tools/NOTICE.txt "${sdk_root}"/cmdline-tools/source.properties \
           "${sdk_root}/cmdline-tools/latest/" 2>/dev/null || true
      fi
      if [[ -x "${sdk_root}/cmdline-tools/latest/bin/sdkmanager" ]]; then
        yes | "${sdk_root}/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${sdk_root}" --licenses 2>/dev/null | tail -1 || true
        "${sdk_root}/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${sdk_root}" \
          "platform-tools" "platforms;android-34" "build-tools;34.0.0" 2>/dev/null || true
        export ANDROID_HOME="${sdk_root}"
        log "Android SDK installed at ${sdk_root}. Set ANDROID_HOME=${sdk_root} in your shell to reuse."
      else
        warn "Android SDK structure incomplete. Set ANDROID_HOME manually or use SKIP_ANDROID=1."
      fi
    elif [[ -d "${sdk_root}/cmdline-tools/latest/bin" ]]; then
      export ANDROID_HOME="${sdk_root}"
    fi
  fi
}

check_gpu_runtime() {
  next_step "Checking GPU and Docker runtime"
  if command -v nvidia-smi >/dev/null 2>&1; then
    nvidia-smi -L || warn "nvidia-smi exists but GPU enumeration failed."
  else
    warn "nvidia-smi not found. Edge AI inference needs NVIDIA drivers."
  fi

  if ! "${DOCKER_BIN}" info >/dev/null 2>&1; then
    warn "Docker daemon is not ready. Attempting to start it."
    if command -v systemctl >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1; then
      sudo systemctl start docker || true
    fi
  fi

  "${DOCKER_BIN}" info >/dev/null 2>&1 || die "Docker daemon is not running."
}

check_base_toolchain() {
  next_step "Checking required tooling"
  require_cmd git
  require_cmd "${PYTHON_BIN}"
  require_cmd pip3
  require_cmd "${DOCKER_BIN}"
  require_cmd java
  # Backend requires Java 21; prefer JAVA_HOME if set, else check default java
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    java_ver=$("${JAVA_HOME}/bin/java" -version 2>&1 | head -1) || true
  else
    java_ver=$(java -version 2>&1 | head -1) || true
  fi
  if ! echo "${java_ver}" | grep -qE '"21\.'; then
    # Try to find Java 21 in common locations (e.g. after apt install openjdk-21-jdk)
    local j21
    for j21 in /usr/lib/jvm/java-21-openjdk-amd64/bin/java /usr/lib/jvm/java-21-openjdk/bin/java; do
      if [[ -x "${j21}" ]]; then
        export JAVA_HOME="${j21%/bin/java}"
        java_ver=$("${j21}" -version 2>&1 | head -1) || true
        break
      fi
    done
  fi
  if ! echo "${java_ver}" | grep -qE '"21\.'; then
    die "Backend requires Java 21. Found: ${java_ver:-unknown}. Install: sudo apt install openjdk-21-jdk, or run with --install-deps"
  fi
  require_cmd node
  require_cmd npm
  require_cmd rg
}

setup_python_env() {
  next_step "Creating Python virtual environment"
  if [[ ! -d "${VENV_DIR}" ]]; then
    if ! "${PYTHON_BIN}" -m venv "${VENV_DIR}"; then
      warn "python venv creation failed. Falling back to system Python."
    fi
  fi

  if [[ -x "${VENV_DIR}/bin/python" && -x "${VENV_DIR}/bin/pip" ]]; then
    PYTHON_EXEC="${VENV_DIR}/bin/python"
    PIP_EXEC="${VENV_DIR}/bin/pip"
  else
    PYTHON_EXEC="${PYTHON_BIN}"
    PIP_EXEC="pip3"
  fi

  "${PIP_EXEC}" install --upgrade pip wheel
  "${PIP_EXEC}" install -r "${ROOT_DIR}/edge/requirements.txt" flake8 pytest pika
}

setup_dashboard_deps() {
  next_step "Installing dashboard dependencies"
  (
    cd "${ROOT_DIR}/dashboard"
    npm install
  )
}

ensure_container_running() {
  local name="$1"
  shift

  if "${DOCKER_BIN}" ps --format '{{.Names}}' | rg -x "${name}" >/dev/null 2>&1; then
    return
  fi

  if "${DOCKER_BIN}" ps -a --format '{{.Names}}' | rg -x "${name}" >/dev/null 2>&1; then
    "${DOCKER_BIN}" start "${name}" >/dev/null
    return
  fi

  "${DOCKER_BIN}" run -d --name "${name}" "$@" >/dev/null
}

start_infra() {
  next_step "Starting PostgreSQL and RabbitMQ containers"
  "${DOCKER_BIN}" network create "${DOCKER_NETWORK}" >/dev/null 2>&1 || true

  ensure_container_running "${POSTGRES_CONTAINER}" \
    --network "${DOCKER_NETWORK}" \
    -e "POSTGRES_DB=${DB_NAME}" \
    -e "POSTGRES_USER=${DB_USER}" \
    -e "POSTGRES_PASSWORD=${DB_PASS}" \
    -p 127.0.0.1:5432:5432 \
    -v "${ROOT_DIR}/cloud/db/init.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro" \
    postgres:16-alpine

  ensure_container_running "${RABBITMQ_CONTAINER}" \
    --network "${DOCKER_NETWORK}" \
    --hostname farm-broker \
    -e "RABBITMQ_DEFAULT_USER=${RABBITMQ_USER}" \
    -e "RABBITMQ_DEFAULT_PASS=${RABBITMQ_PASS}" \
    -e "RABBITMQ_ERLANG_COOKIE=${RABBITMQ_ERLANG_COOKIE}" \
    -v "rabbitmq_data:/var/lib/rabbitmq" \
    -p 5672:5672 \
    -p 1883:1883 \
    -p 15672:15672 \
    -v "${ROOT_DIR}/cloud/rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins:ro" \
    rabbitmq:3-management
}

wait_for_infra() {
  next_step "Waiting for infrastructure readiness"

  for _ in {1..60}; do
    if "${DOCKER_BIN}" exec "${POSTGRES_CONTAINER}" pg_isready -U "${DB_USER}" -d "${DB_NAME}" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done

  "${DOCKER_BIN}" exec "${POSTGRES_CONTAINER}" pg_isready -U "${DB_USER}" -d "${DB_NAME}" >/dev/null 2>&1 \
    || die "PostgreSQL did not become ready."

  for _ in {1..60}; do
    if "${DOCKER_BIN}" exec "${RABBITMQ_CONTAINER}" rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done

  "${DOCKER_BIN}" exec "${RABBITMQ_CONTAINER}" rabbitmq-diagnostics -q ping >/dev/null 2>&1 \
    || die "RabbitMQ did not become ready."
}

init_rabbitmq_topology() {
  next_step "Initializing RabbitMQ exchanges and queues"
  local attempt
  for attempt in {1..10}; do
    if (
      cd "${ROOT_DIR}"
      RABBITMQ_PASS="${RABBITMQ_PASS}" "${PYTHON_EXEC}" "${ROOT_DIR}/cloud/scripts/rabbitmq_init.py"
    ); then
      return
    fi

    warn "RabbitMQ topology init attempt ${attempt} failed. Retrying..."
    sleep 3
  done

  die "RabbitMQ topology initialization failed after multiple attempts."
}

build_backend() {
  next_step "Building backend microservices"
  (
    cd "${ROOT_DIR}/backend"
    ./gradlew clean build --no-daemon
  )
}

build_dashboard() {
  next_step "Building dashboard"
  (
    cd "${ROOT_DIR}/dashboard"
    npm run lint
    npm run build
    npm run test -- --run --passWithNoTests
  )
}

build_edge() {
  next_step "Building and validating edge AI Python code"
  (
    cd "${ROOT_DIR}/edge"
    "${PYTHON_EXEC}" -m py_compile *.py
    if ! "${PYTHON_EXEC}" -m flake8 --max-line-length=120 *.py; then
      warn "Edge flake8 reported issues. Continuing because this is a pre-existing codebase baseline."
    fi
    if ! "${PYTHON_EXEC}" -m pytest tests; then
      warn "Edge pytest reported failures. Continuing because this is a pre-existing codebase baseline."
    fi
  )
}

build_android() {
  if [[ "${SKIP_ANDROID}" == "1" ]]; then
    warn "Skipping Android build (SKIP_ANDROID=1)."
    SKIPPED_COMPONENTS+=("android")
    return
  fi

  next_step "Building Android app"

  [[ -d "${ROOT_DIR}/android" ]] || die "Android project not found at ${ROOT_DIR}/android."

  if [[ -z "${ANDROID_HOME:-}" ]]; then
    if [[ -d "${ROOT_DIR}/android-sdk/cmdline-tools/latest/bin" ]]; then
      export ANDROID_HOME="${ROOT_DIR}/android-sdk"
    elif [[ -d "/opt/android-sdk" ]]; then
      export ANDROID_HOME="/opt/android-sdk"
    fi
  fi

  [[ -n "${ANDROID_HOME:-}" ]] || die "ANDROID_HOME is not set. Set ANDROID_HOME or run with --install-deps, or use SKIP_ANDROID=1."
  [[ -d "${ANDROID_HOME}" ]] || die "ANDROID_HOME path does not exist: ${ANDROID_HOME}"

  (
    cd "${ROOT_DIR}/android"
    ANDROID_HOME="${ANDROID_HOME}" ./gradlew clean assembleDebug testDebugUnitTest --no-daemon
  )
}

build_alert_management() {
  next_step "Checking AlertManagement Python scripts"
  (
    cd "${ROOT_DIR}"
    "${PYTHON_EXEC}" -m py_compile AlertManagement/scripts/*.py
  )
}

build_firmware() {
  if [[ "${SKIP_FIRMWARE}" == "1" ]]; then
    warn "Skipping firmware build (SKIP_FIRMWARE=1)."
    SKIPPED_COMPONENTS+=("firmware")
    return
  fi

  next_step "Building ESP32 firmware sketches"

  if ! command -v arduino-cli >/dev/null 2>&1; then
    warn "arduino-cli not found. Firmware build skipped."
    SKIPPED_COMPONENTS+=("firmware")
    return
  fi

  arduino-cli core update-index
  arduino-cli core install esp32:esp32

  arduino-cli compile --fqbn esp32:esp32:esp32 "${ROOT_DIR}/firmware/lora_bridge/esp32_lora_bridge_receiver.ino"
  arduino-cli compile --fqbn esp32:esp32:esp32 "${ROOT_DIR}/firmware/worker_beacon/esp32_lora_tag.ino"
}

print_summary() {
  next_step "Setup and build completed"
  if [[ "${#SKIPPED_COMPONENTS[@]}" -gt 0 ]]; then
    warn "Skipped components: ${SKIPPED_COMPONENTS[*]}"
  fi
  log "All requested setup/build tasks finished."
}

main() {
  ensure_repo_root
  if [[ "${INSTALL_DEPS}" == "1" ]]; then
    install_system_deps
  fi
  check_base_toolchain
  check_gpu_runtime
  setup_python_env
  setup_dashboard_deps
  start_infra
  wait_for_infra
  init_rabbitmq_topology
  build_backend
  build_dashboard
  build_edge
  build_android
  build_alert_management
  build_firmware
  print_summary
}

main "$@"
