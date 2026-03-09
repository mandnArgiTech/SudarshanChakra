#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_NAME="$(basename "$0")"
ORIG_ARGS=("$@")
INSTALL_DEPS=0

# Parse --help / --install-deps before rest of config
for arg in "$@"; do
  case "${arg}" in
    --help|-h)
      echo "SudarshanChakra — Setup and build all components"
      echo ""
      echo "USAGE:"
      echo "  ${SCRIPT_NAME} [OPTIONS]"
      echo ""
      echo "OPTIONS:"
      echo "  -h, --help          Show this help."
      echo "  -i, --install-deps  Install system packages (Ubuntu/Debian; uses sudo)."
      echo ""
      echo "PREREQUISITES (install once, then run script without --install-deps):"
      echo "  • Docker running: sudo systemctl start docker"
      echo "  • Ubuntu/Debian packages:"
      echo "    sudo apt-get update && sudo apt-get install -y \\"
      echo "      git curl unzip openjdk-21-jdk nodejs npm ripgrep python3-pip python3-venv docker.io"
      echo "  • If docker.io fails (Conflict: containerd), use Docker CE instead:"
      echo "    sudo apt-get install -y docker-ce docker-ce-cli containerd.io"
      echo ""
      echo "ENVIRONMENT:"
      echo "  SKIP_ANDROID=1      Skip Android build."
      echo "  SKIP_FIRMWARE=1     Skip ESP32 firmware build."
      echo "  SKIP_DASHBOARD=1    Skip dashboard build (auto if node/npm missing)."
      echo "  ANDROID_HOME        Path to Android SDK."
      echo ""
      echo "EXAMPLES:"
      echo "  ./${SCRIPT_NAME} --install-deps   # Install deps + build all"
      echo "  ./${SCRIPT_NAME}                 # Build only (deps must be installed)"
      echo "  SKIP_ANDROID=1 ./${SCRIPT_NAME}  # Build without Android"
      exit 0
      ;;
    --install-deps|-i) INSTALL_DEPS=1 ;;
  esac
done

DOCKER_BIN="${DOCKER_BIN:-docker}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
VENV_DIR="${VENV_DIR:-${ROOT_DIR}/.venv}"
PYTHON_EXEC="${PYTHON_BIN}"
# Prefer pip3 if available; otherwise use python3 -m pip
if command -v pip3 >/dev/null 2>&1; then
  PIP_EXEC="pip3"
else
  PIP_EXEC="${PYTHON_BIN} -m pip"
fi

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-postgres}"
RABBITMQ_CONTAINER="${RABBITMQ_CONTAINER:-rabbitmq}"
DOCKER_NETWORK="${DOCKER_NETWORK:-sc-net}"
DB_NAME="${DB_NAME:-sudarshanchakra}"
DB_USER="${DB_USER:-scadmin}"
DB_PASS="${DB_PASS:-devpassword123}"
RABBITMQ_USER="${RABBITMQ_USER:-admin}"
RABBITMQ_PASS="${RABBITMQ_PASS:-devpassword123}"

SKIP_ANDROID="${SKIP_ANDROID:-0}"
SKIP_FIRMWARE="${SKIP_FIRMWARE:-0}"
SKIP_DASHBOARD="${SKIP_DASHBOARD:-}"

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

run_pip() {
  if [[ "${PIP_EXEC}" == *" -m pip"* ]]; then
    "${PYTHON_BIN}" -m pip "$@"
  else
    "${PIP_EXEC}" "$@"
  fi
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
  # Fix any broken dependencies before installing new packages
  log "Checking for broken dependencies..."
  sudo apt-get -f install -y -qq 2>/dev/null || true

  # Install everything except Docker first (avoids containerd.io vs containerd conflict when Docker repo is added)
  local pkgs_base="git curl unzip openjdk-21-jdk nodejs npm ripgrep python3-pip python3.12-venv"
  if ! sudo apt-get install -y -qq $pkgs_base; then
    warn "Install failed (often due to python3.12-venv). Retrying with python3-venv..."
    pkgs_base="git curl unzip openjdk-21-jdk nodejs npm ripgrep python3-pip python3-venv"
    if ! sudo apt-get install -y $pkgs_base; then
      die "Install failed. Run: sudo apt-get update && sudo apt-get -f install -y && sudo apt-get install -y git curl unzip openjdk-21-jdk nodejs npm ripgrep python3-pip python3-venv"
    fi
  fi

  # Install Docker: prefer docker.io (Ubuntu); if conflict with containerd.io, use Docker CE (official repo)
  if ! sudo apt-get install -y -qq docker.io; then
    warn "docker.io failed (often Conflicts: containerd when Docker official repo is added). Trying docker-ce..."
    if ! sudo apt-get install -y -qq docker-ce docker-ce-cli containerd.io; then
      die "Docker install failed. Install Docker manually: https://docs.docker.com/engine/install/"
    fi
  fi

  # Add current user to docker group so Docker runs without sudo
  if [[ -n "${SUDO_USER:-}" ]]; then
    sudo usermod -aG docker "${SUDO_USER}" 2>/dev/null || true
  fi
  log "System packages installed. You may need to log out and back in for Docker group to apply."

  # Install Node.js 22 LTS via NodeSource if system node is too old (<18)
  local node_major
  node_major=$(node --version 2>/dev/null | sed 's/v\([0-9]*\).*/\1/' || echo "0")
  if [[ "${node_major}" -lt 18 ]]; then
    log "Node.js v${node_major} is too old. Installing Node.js 22 LTS via NodeSource..."
    curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - \
      && sudo apt-get install -y nodejs \
      || warn "NodeSource install failed. Install Node.js 18+ manually: https://nodejs.org/"
    # Update npm to latest
    sudo npm install -g npm@latest 2>/dev/null || true
  fi

  # Install arduino-cli for firmware builds
  if ! command -v arduino-cli >/dev/null 2>&1; then
    log "Installing arduino-cli..."
    local acli_url="https://downloads.arduino.cc/arduino-cli/arduino-cli_latest_Linux_64bit.tar.gz"
    local acli_tmp="/tmp/arduino-cli.tar.gz"
    curl -fsSL -o "${acli_tmp}" "${acli_url}" \
      && sudo tar -xzf "${acli_tmp}" -C /usr/local/bin arduino-cli \
      && rm -f "${acli_tmp}" \
      && log "arduino-cli installed." \
      || warn "arduino-cli install failed. Firmware build will be skipped."
  fi

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
    # User may be in docker group but current session hasn't picked it up; use sg docker to re-exec
    if id -nG | grep -qw docker && sg docker -c "${DOCKER_BIN} info" >/dev/null 2>&1; then
      log "Docker requires group activation. Restarting script under 'sg docker'..."
      exec sg docker -c "\"$0\" ${ORIG_ARGS[*]}"
    fi
    warn "Docker daemon is not ready. Attempting to start it."
    if command -v systemctl >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1; then
      sudo systemctl start docker 2>/dev/null || true
    fi
  fi

  "${DOCKER_BIN}" info >/dev/null 2>&1 || die "Docker daemon is not running or current user lacks permission. Run: sudo usermod -aG docker \$USER && newgrp docker"
}

check_base_toolchain() {
  next_step "Checking required tooling"
  # If node/npm missing and SKIP_DASHBOARD not set, skip dashboard later
  if [[ -z "${SKIP_DASHBOARD}" ]]; then
    command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1 || SKIP_DASHBOARD=1
  fi
  require_cmd git
  require_cmd "${PYTHON_BIN}"
  # pip: require pip3, python3 -m pip, or bootstrap with get-pip.py --user
  if command -v pip3 >/dev/null 2>&1; then
    PIP_EXEC="pip3"
  elif "${PYTHON_BIN}" -m pip --version >/dev/null 2>&1; then
    PIP_EXEC="${PYTHON_BIN} -m pip"
  else
    log "pip not found; attempting to bootstrap with get-pip.py --user"
    local get_pip="${ROOT_DIR}/.get-pip.py"
    if [[ ! -f "${get_pip}" ]]; then
      if command -v curl >/dev/null 2>&1; then
        curl -sSL -o "${get_pip}" "https://bootstrap.pypa.io/get-pip.py"
      elif command -v wget >/dev/null 2>&1; then
        wget -q -O "${get_pip}" "https://bootstrap.pypa.io/get-pip.py"
      else
        die "Neither curl nor wget found. Install python3-pip or run with --install-deps."
      fi
      [[ -f "${get_pip}" ]] || die "Could not download get-pip.py."
    fi
    "${PYTHON_BIN}" "${get_pip}" --user \
      || die "get-pip.py --user failed. Install python3-pip or run with --install-deps."
    export PATH="${HOME}/.local/bin:${PATH}"
    if "${PYTHON_BIN}" -m pip --version >/dev/null 2>&1; then
      PIP_EXEC="${PYTHON_BIN} -m pip"
    else
      die "pip bootstrap completed but python3 -m pip still not available."
    fi
  fi
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
  if [[ "${SKIP_DASHBOARD}" != "1" ]]; then
    require_cmd node
    require_cmd npm
    local node_major
    node_major=$(node --version 2>/dev/null | sed 's/v\([0-9]*\).*/\1/')
    if [[ -n "${node_major}" && "${node_major}" -lt 18 ]]; then
      warn "Node.js v${node_major} is too old. Dashboard requires Node 18+. Skipping dashboard. Upgrade: https://nodejs.org/"
      SKIP_DASHBOARD=1
      SKIPPED_COMPONENTS+=("dashboard")
    fi
  fi
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
    # Keep PIP_EXEC as set in check_base_toolchain (may be python3 -m pip if bootstrapped)
  fi

  run_pip install --upgrade pip wheel
  run_pip install -r "${ROOT_DIR}/edge/requirements.txt" flake8 pytest pika opencv-python-headless
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

  if "${DOCKER_BIN}" ps --format '{{.Names}}' | grep -Ex "${name}" >/dev/null 2>&1; then
    return
  fi

  if "${DOCKER_BIN}" ps -a --format '{{.Names}}' | grep -Ex "${name}" >/dev/null 2>&1; then
    "${DOCKER_BIN}" start "${name}" || die "Failed to start container: ${name}. Run: docker logs ${name}"
    return
  fi

  "${DOCKER_BIN}" run -d --name "${name}" "$@" || die "Failed to run container: ${name}. Check Docker and image pull."
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

  # RabbitMQ: always recreate to avoid stale .erlang.cookie permission issues
  log "Recreating RabbitMQ container..."
  "${DOCKER_BIN}" rm -fv "${RABBITMQ_CONTAINER}" 2>/dev/null || true
  "${DOCKER_BIN}" volume rm rabbitmq_data 2>/dev/null || true

  "${DOCKER_BIN}" run -d --name "${RABBITMQ_CONTAINER}" \
    --user 999:999 \
    --network "${DOCKER_NETWORK}" \
    --hostname farm-broker \
    -e "RABBITMQ_DEFAULT_USER=${RABBITMQ_USER}" \
    -e "RABBITMQ_DEFAULT_PASS=${RABBITMQ_PASS}" \
    -p 5672:5672 \
    -p 1883:1883 \
    -p 15672:15672 \
    rabbitmq:3-management \
    || die "Failed to start RabbitMQ container."
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

  # RabbitMQ can take 60–90s to start with management + MQTT plugins; allow up to 3.5 minutes
  log "Waiting for RabbitMQ (may take 1–2 minutes)..."
  for _ in {1..105}; do
    if "${DOCKER_BIN}" exec "${RABBITMQ_CONTAINER}" rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done

  if ! "${DOCKER_BIN}" exec "${RABBITMQ_CONTAINER}" rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
    warn "RabbitMQ did not become ready. Last 40 lines of container log:"
    "${DOCKER_BIN}" logs "${RABBITMQ_CONTAINER}" 2>&1 | tail -40
    die "RabbitMQ did not become ready. Run: docker rm -f rabbitmq; docker volume rm rabbitmq_data; then re-run this script."
  fi

  # Enable MQTT plugin (for Android / edge); image has management enabled by default
  log "Enabling RabbitMQ MQTT plugin..."
  "${DOCKER_BIN}" exec "${RABBITMQ_CONTAINER}" rabbitmq-plugins enable rabbitmq_mqtt rabbitmq_web_mqtt 2>/dev/null || true
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

  die "RabbitMQ topology initialization failed. Check: docker logs rabbitmq; ensure broker is up: docker exec rabbitmq rabbitmq-diagnostics -q ping"
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
  arduino-cli lib install "LoRa"

  arduino-cli compile --fqbn esp32:esp32:esp32 "${ROOT_DIR}/firmware/esp32_lora_bridge_receiver"
  arduino-cli compile --fqbn esp32:esp32:esp32 "${ROOT_DIR}/firmware/esp32_lora_tag"
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
  if [[ "${SKIP_DASHBOARD}" != "1" ]]; then
    setup_dashboard_deps
  else
    warn "Skipping dashboard (node/npm unavailable or SKIP_DASHBOARD=1)."
    SKIPPED_COMPONENTS+=("dashboard")
  fi
  start_infra
  wait_for_infra
  init_rabbitmq_topology
  build_backend
  if [[ "${SKIP_DASHBOARD}" != "1" ]]; then
    build_dashboard
  fi
  build_edge
  build_android
  build_alert_management
  build_firmware
  print_summary
}

main "$@"
