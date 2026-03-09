#!/usr/bin/env bash
# ============================================================================
# SudarshanChakra — Unified Setup, Build, Test & Deploy Script
# ============================================================================
#
# Modular CLI for the SudarshanChakra monorepo. Each component can be built,
# tested, and deployed independently, or all at once.
#
# Run with --help for full usage information.
#
# Components: backend (Java/Spring Boot), dashboard (React/Vite),
#   edge (Python/YOLO), android (Kotlin/Compose), firmware (ESP32/Arduino),
#   cloud (PostgreSQL/RabbitMQ), AlertManagement (Python/Raspberry Pi)
# ============================================================================

set -uo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_NAME="$(basename "$0")"
ORIG_ARGS=("$@")

VERBOSE=0
NO_COLOR=0

# Pre-scan for flags that affect output formatting
for _arg in "$@"; do
  case "${_arg}" in
    --no-color) NO_COLOR=1 ;;
    -v|--verbose) VERBOSE=1 ;;
  esac
done
unset _arg

# ============================================================================
# COLORS
# ============================================================================

if [[ -t 1 ]] && [[ "${NO_COLOR}" -eq 0 ]]; then
  readonly RED=$'\033[0;31m'
  readonly GREEN=$'\033[0;32m'
  readonly YELLOW=$'\033[1;33m'
  readonly BLUE=$'\033[0;34m'
  readonly CYAN=$'\033[0;36m'
  readonly BOLD=$'\033[1m'
  readonly DIM=$'\033[2m'
  readonly RESET=$'\033[0m'
else
  readonly RED=''
  readonly GREEN=''
  readonly YELLOW=''
  readonly BLUE=''
  readonly CYAN=''
  readonly BOLD=''
  readonly DIM=''
  readonly RESET=''
fi

# ============================================================================
# LOGGING
# ============================================================================

_ts() { date '+%H:%M:%S'; }

log_info()    { printf "${BLUE}[%s]${RESET} %s\n" "$(_ts)" "$1"; }
log_success() { printf "${GREEN}[%s] OK${RESET} %s\n" "$(_ts)" "$1"; }
log_warn()    { printf "${YELLOW}[%s] WARN${RESET} %s\n" "$(_ts)" "$1" >&2; }
log_error()   { printf "${RED}[%s] FAIL${RESET} %s\n" "$(_ts)" "$1" >&2; }
log_debug()   { [[ "${VERBOSE}" -eq 1 ]] && printf "${DIM}[%s] DBG  %s${RESET}\n" "$(_ts)" "$1"; return 0; }

die() { log_error "$1"; exit 1; }

banner() {
  local title="$1"
  printf "\n${BOLD}"
  printf '%.0s=' {1..60}
  printf "\n  %s\n" "${title}"
  printf '%.0s=' {1..60}
  printf "${RESET}\n\n"
}

format_duration() {
  local secs=$1
  if [[ ${secs} -ge 60 ]]; then
    printf '%dm %ds' $((secs / 60)) $((secs % 60))
  else
    printf '%ds' "${secs}"
  fi
}

# ============================================================================
# CONFIGURATION
# ============================================================================

DOCKER_BIN="${DOCKER_BIN:-docker}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
VENV_DIR="${ROOT_DIR}/.venv"
PYTHON_EXEC="${PYTHON_BIN}"
PIP_CMD=""
LOG_DIR="${ROOT_DIR}/logs"

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
SKIP_DASHBOARD="${SKIP_DASHBOARD:-0}"

# Service names and ports (boot order: dependencies first, gateway last)
BACKEND_SERVICES=(auth-service device-service alert-service siren-service api-gateway)
declare -A SERVICE_PORT=(
  [auth-service]=8083
  [device-service]=8082
  [alert-service]=8081
  [siren-service]=8084
  [api-gateway]=8080
)

# ============================================================================
# RESULT TRACKING
# ============================================================================

declare -a RESULT_NAMES=()
declare -a RESULT_STATUSES=()
declare -a RESULT_DURATIONS=()
TOTAL_FAILURES=0

# Run a command function with timing, banner, and result tracking.
# Usage: track_run "LABEL" function_name [args...]
track_run() {
  local label="$1"; shift
  local start_time end_time duration rc=0

  banner "${label}"
  start_time=$(date +%s)

  "$@" || rc=$?

  end_time=$(date +%s)
  duration=$((end_time - start_time))

  RESULT_NAMES+=("${label}")
  RESULT_DURATIONS+=("${duration}")

  if [[ ${rc} -eq 0 ]]; then
    RESULT_STATUSES+=("PASS")
    log_success "${label} completed in $(format_duration ${duration})"
  else
    RESULT_STATUSES+=("FAIL")
    log_error "${label} failed after $(format_duration ${duration})"
    TOTAL_FAILURES=$((TOTAL_FAILURES + 1))
  fi

  return ${rc}
}

track_skip() {
  local label="$1"
  local reason="${2:-skipped}"
  RESULT_NAMES+=("${label}")
  RESULT_STATUSES+=("SKIP")
  RESULT_DURATIONS+=("0")
  log_warn "${label}: ${reason}"
}

print_summary() {
  printf "\n${BOLD}"
  printf '%.0s─' {1..60}
  printf "\n  %-28s %-10s %s\n" "COMPONENT" "STATUS" "DURATION"
  printf '%.0s─' {1..60}
  printf "${RESET}\n"

  local i
  for i in "${!RESULT_NAMES[@]}"; do
    local name="${RESULT_NAMES[${i}]}"
    local status="${RESULT_STATUSES[${i}]}"
    local dur="${RESULT_DURATIONS[${i}]}"
    local color="${RESET}" dur_str="--"

    case "${status}" in
      PASS) color="${GREEN}" ;;
      FAIL) color="${RED}" ;;
      SKIP) color="${YELLOW}" ;;
    esac

    [[ "${status}" != "SKIP" ]] && dur_str=$(format_duration "${dur}")

    printf "  %-28s ${color}%-10s${RESET} %s\n" "${name}" "${status}" "${dur_str}"
  done

  printf "${BOLD}"
  printf '%.0s─' {1..60}
  printf "${RESET}\n"

  if [[ ${TOTAL_FAILURES} -gt 0 ]]; then
    log_error "${TOTAL_FAILURES} command(s) failed."
  elif [[ ${#RESULT_NAMES[@]} -gt 0 ]]; then
    log_success "All commands completed successfully."
  fi
}

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

require_cmd() {
  command -v "$1" >/dev/null 2>&1 \
    || { log_error "Required command not found: $1"; return 1; }
}

ensure_repo_root() {
  for dir in backend dashboard edge cloud; do
    [[ -d "${ROOT_DIR}/${dir}" ]] \
      || die "'${dir}/' not found. Run this script from the SudarshanChakra repo root."
  done
}

# Docker: check binary, activate group if needed, start daemon if stopped
ensure_docker() {
  require_cmd "${DOCKER_BIN}" || return 1

  if ! "${DOCKER_BIN}" info >/dev/null 2>&1; then
    # Re-exec under sg docker if group membership exists but session hasn't picked it up
    if id -nG 2>/dev/null | grep -qw docker \
       && sg docker -c "${DOCKER_BIN} info" >/dev/null 2>&1; then
      log_info "Docker requires group activation. Restarting under 'sg docker'..."
      local quoted_args=""
      local a
      for a in "${ORIG_ARGS[@]}"; do
        quoted_args+=" '${a//\'/\'\\\'\'}'"
      done
      exec sg docker -c "\"$0\"${quoted_args}"
    fi

    log_info "Docker daemon not running. Attempting to start..."
    if command -v systemctl >/dev/null 2>&1; then
      sudo systemctl start docker 2>/dev/null || true
    fi
  fi

  "${DOCKER_BIN}" info >/dev/null 2>&1 || {
    log_error "Docker is not running or permission denied."
    log_error "Fix: sudo usermod -aG docker \$USER && newgrp docker"
    return 1
  }
  log_debug "Docker OK: $(${DOCKER_BIN} --version)"
}

ensure_java21() {
  require_cmd java || return 1

  local java_ver=""
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    java_ver=$("${JAVA_HOME}/bin/java" -version 2>&1 | head -1) || true
  else
    java_ver=$(java -version 2>&1 | head -1) || true
  fi

  # Auto-detect Java 21 in common system paths
  if ! echo "${java_ver}" | grep -qE '"21\.'; then
    local j21
    for j21 in /usr/lib/jvm/java-21-openjdk-amd64/bin/java \
                /usr/lib/jvm/java-21-openjdk/bin/java; do
      if [[ -x "${j21}" ]]; then
        export JAVA_HOME="${j21%/bin/java}"
        java_ver=$("${j21}" -version 2>&1 | head -1) || true
        break
      fi
    done
  fi

  echo "${java_ver}" | grep -qE '"21\.' || {
    log_error "Java 21 required. Found: ${java_ver:-none}"
    log_error "Install: sudo apt install openjdk-21-jdk"
    return 1
  }
  log_debug "Java: ${java_ver}"
}

ensure_node18() {
  require_cmd node || return 1
  require_cmd npm  || return 1

  local node_major
  node_major=$(node --version 2>/dev/null | sed 's/v\([0-9]*\).*/\1/')
  [[ "${node_major}" =~ ^[0-9]+$ ]] || node_major="0"
  [[ "${node_major}" -ge 18 ]] || {
    log_error "Node.js 18+ required. Found: v${node_major}"
    log_error "Install via https://nodejs.org/ or run: install-deps"
    return 1
  }
  log_debug "Node.js $(node --version), npm $(npm --version 2>/dev/null)"
}

# Bootstrap pip if not available, set PIP_CMD
ensure_pip() {
  [[ -n "${PIP_CMD}" ]] && return 0

  if command -v pip3 >/dev/null 2>&1; then
    PIP_CMD="pip3"
  elif "${PYTHON_BIN}" -m pip --version >/dev/null 2>&1; then
    PIP_CMD="${PYTHON_BIN} -m pip"
  else
    log_info "pip not found — bootstrapping with get-pip.py..."
    local get_pip="${ROOT_DIR}/.get-pip.py"
    if [[ ! -f "${get_pip}" ]]; then
      if command -v curl >/dev/null 2>&1; then
        curl -sSL -o "${get_pip}" "https://bootstrap.pypa.io/get-pip.py"
      elif command -v wget >/dev/null 2>&1; then
        wget -q -O "${get_pip}" "https://bootstrap.pypa.io/get-pip.py"
      else
        log_error "Neither curl nor wget found. Install python3-pip."; return 1
      fi
    fi
    PIP_BREAK_SYSTEM_PACKAGES=1 "${PYTHON_BIN}" "${get_pip}" --user \
      || { log_error "get-pip.py bootstrap failed."; return 1; }
    export PATH="${HOME}/.local/bin:${PATH}"

    if "${PYTHON_BIN}" -m pip --version >/dev/null 2>&1; then
      PIP_CMD="${PYTHON_BIN} -m pip"
    else
      log_error "pip bootstrap completed but still not functional."; return 1
    fi
  fi
}

run_pip() {
  # PIP_BREAK_SYSTEM_PACKAGES: on Ubuntu 24.04+ (PEP 668), pip refuses to
  # install into system Python without this. Older pip versions ignore it.
  if [[ "${PIP_CMD}" == *" -m pip"* ]]; then
    PIP_BREAK_SYSTEM_PACKAGES=1 "${PYTHON_BIN}" -m pip "$@"
  else
    PIP_BREAK_SYSTEM_PACKAGES=1 ${PIP_CMD} "$@"
  fi
}

# Create .venv and point PYTHON_EXEC / PIP_CMD into it
setup_python_venv() {
  ensure_pip || return 1

  if [[ ! -d "${VENV_DIR}" ]]; then
    log_info "Creating Python virtual environment at ${VENV_DIR}..."
    "${PYTHON_BIN}" -m venv "${VENV_DIR}" 2>/dev/null || {
      log_warn "venv creation failed — using system Python."
      return 0
    }
  fi

  if [[ -x "${VENV_DIR}/bin/python" && -x "${VENV_DIR}/bin/pip" ]]; then
    PYTHON_EXEC="${VENV_DIR}/bin/python"
    PIP_CMD="${VENV_DIR}/bin/pip"
  fi

  run_pip install --upgrade pip wheel -q || log_warn "pip upgrade failed."
}

check_port_free() {
  local port=$1
  if command -v ss >/dev/null 2>&1; then
    ! ss -tlnp 2>/dev/null | grep -q ":${port} "
  elif command -v lsof >/dev/null 2>&1; then
    ! lsof -i ":${port}" -sTCP:LISTEN >/dev/null 2>&1
  else
    ! (echo > "/dev/tcp/localhost/${port}") 2>/dev/null
  fi
}

wait_for_port() {
  local port=$1 timeout=${2:-60} elapsed=0
  while [[ ${elapsed} -lt ${timeout} ]]; do
    # Try bash /dev/tcp first, fall back to nc or ss for portability
    if (echo > "/dev/tcp/localhost/${port}") 2>/dev/null; then
      return 0
    elif command -v nc >/dev/null 2>&1 && nc -z localhost "${port}" 2>/dev/null; then
      return 0
    elif command -v ss >/dev/null 2>&1 && ss -tlnp 2>/dev/null | grep -q ":${port} "; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  return 1
}

wait_for_http() {
  local url=$1 timeout=${2:-90} elapsed=0
  while [[ ${elapsed} -lt ${timeout} ]]; do
    if curl -sf "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
    elapsed=$((elapsed + 3))
  done
  return 1
}

ensure_android_home() {
  if [[ -z "${ANDROID_HOME:-}" ]]; then
    if [[ -d "${ROOT_DIR}/android-sdk/cmdline-tools/latest/bin" ]]; then
      export ANDROID_HOME="${ROOT_DIR}/android-sdk"
    elif [[ -d "/opt/android-sdk" ]]; then
      export ANDROID_HOME="/opt/android-sdk"
    fi
  fi

  [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]] || {
    log_error "ANDROID_HOME not set or doesn't exist."
    log_error "Set ANDROID_HOME, run install-deps, or use SKIP_ANDROID=1."
    return 1
  }
  log_debug "ANDROID_HOME=${ANDROID_HOME}"
}

# Resolve docker compose command (v2 plugin vs standalone v1)
resolve_compose_cmd() {
  if "${DOCKER_BIN}" compose version >/dev/null 2>&1; then
    echo "${DOCKER_BIN} compose"
  elif command -v docker-compose >/dev/null 2>&1; then
    echo "docker-compose"
  else
    log_error "Neither 'docker compose' (v2) nor 'docker-compose' (v1) found."
    return 1
  fi
}

# ============================================================================
# INSTALL DEPENDENCIES
# ============================================================================

cmd_install_deps() {
  log_info "Installing system dependencies (Ubuntu/Debian — uses sudo)"

  command -v apt-get >/dev/null 2>&1 \
    || { log_error "apt-get not found. This script supports Ubuntu/Debian only."; return 1; }

  # Verify sudo access early
  if ! sudo -n true 2>/dev/null; then
    log_info "sudo access required. You may be prompted for your password."
    sudo true || { log_error "Cannot obtain sudo. Run with a user that has sudo privileges."; return 1; }
  fi

  # Detect Ubuntu version for version-specific handling
  local ubuntu_ver=""
  if [[ -f /etc/os-release ]]; then
    ubuntu_ver=$(. /etc/os-release && echo "${VERSION_ID}")
  fi
  log_info "Detected OS: Ubuntu ${ubuntu_ver:-unknown}"

  # APT options: wait up to 120s for dpkg lock (another apt may be running)
  local APT="sudo apt-get -o DPkg::Lock::Timeout=120"

  # ── Phase 1: Repair any broken dpkg/apt state ──
  log_info "[1/8] Repairing package manager state..."
  sudo dpkg --configure -a 2>/dev/null || true
  ${APT} -f install -y -qq 2>/dev/null || true

  # ── Phase 2: Update package index (with retry) ──
  log_info "[2/8] Updating package index..."
  local attempt
  for attempt in 1 2 3; do
    if ${APT} update -qq 2>&1; then
      break
    fi
    log_warn "apt-get update failed (attempt ${attempt}/3). Retrying in 5s..."
    sleep 5
  done

  # ── Phase 3: Core tools (these almost never fail) ──
  log_info "[3/8] Installing core tools (git, curl, unzip, ripgrep)..."
  ${APT} install -y -qq git curl unzip 2>&1 \
    || { log_error "Failed to install git/curl/unzip. Check apt output above."; return 1; }
  ${APT} install -y -qq ripgrep 2>/dev/null \
    || log_warn "ripgrep not available — non-essential, continuing."

  # ── Phase 4: Python + venv + pip ──
  log_info "[4/8] Installing Python, venv, and pip..."
  local py_ver=""
  if command -v python3 >/dev/null 2>&1; then
    py_ver=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")' 2>/dev/null || echo "")
  fi

  # Install python3 if somehow missing
  ${APT} install -y -qq python3 2>&1 || true

  if [[ -z "${py_ver}" ]]; then
    py_ver=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")' 2>/dev/null || echo "3")
  fi

  # Try version-specific venv package first, then generic fallback
  local venv_installed=0
  for venv_pkg in "python${py_ver}-venv" "python3-venv"; do
    if ${APT} install -y -qq "${venv_pkg}" 2>/dev/null; then
      venv_installed=1
      log_debug "Installed ${venv_pkg}"
      break
    fi
  done
  [[ ${venv_installed} -eq 1 ]] || log_warn "python3-venv install failed — venv creation may not work."

  # pip: try package install, then bootstrap fallback
  if ! python3 -m pip --version >/dev/null 2>&1; then
    ${APT} install -y -qq python3-pip 2>/dev/null || true
  fi
  if ! python3 -m pip --version >/dev/null 2>&1; then
    log_info "pip not available via apt — bootstrapping with get-pip.py..."
    local get_pip="/tmp/get-pip.py"
    curl -sSL -o "${get_pip}" "https://bootstrap.pypa.io/get-pip.py" 2>/dev/null \
      && PIP_BREAK_SYSTEM_PACKAGES=1 python3 "${get_pip}" --user 2>/dev/null \
      && export PATH="${HOME}/.local/bin:${PATH}" \
      || log_warn "pip bootstrap failed — build-edge may not work."
    rm -f "${get_pip}"
  fi

  # ── Phase 5: Java 21 ──
  log_info "[5/8] Installing Java 21..."
  if java -version 2>&1 | grep -qE '"21\.'; then
    log_info "Java 21 already installed."
  else
    # On 22.04, openjdk-21-jdk may need universe/updates repos
    if [[ "${ubuntu_ver}" == "22.04" ]]; then
      ${APT} install -y -qq software-properties-common 2>/dev/null || true
      sudo add-apt-repository -y universe 2>/dev/null || true
      ${APT} update -qq 2>/dev/null || true
    fi
    if ! ${APT} install -y -qq openjdk-21-jdk 2>&1; then
      # On some minimal 22.04 installs, try backport PPA as last resort
      log_warn "openjdk-21-jdk not in repos — trying openjdk-r PPA..."
      sudo add-apt-repository -y ppa:openjdk-r/ppa 2>/dev/null || true
      ${APT} update -qq 2>/dev/null || true
      ${APT} install -y -qq openjdk-21-jdk 2>&1 \
        || { log_error "Java 21 install failed. Install manually: sudo apt install openjdk-21-jdk"; return 1; }
    fi
    # Ensure Java 21 is the default if multiple JDKs are installed
    local java21_bin
    java21_bin=$(find /usr/lib/jvm -path '*/java-21-openjdk*/bin/java' -type f 2>/dev/null | head -1)
    if [[ -n "${java21_bin}" ]]; then
      sudo update-alternatives --set java "${java21_bin}" 2>/dev/null || true
      local javac21_bin="${java21_bin%/*}/javac"
      if [[ -x "${javac21_bin}" ]]; then
        sudo update-alternatives --set javac "${javac21_bin}" 2>/dev/null || true
      fi
    fi
  fi

  # ── Phase 6: Docker ──
  log_info "[6/8] Installing Docker..."
  if command -v docker >/dev/null 2>&1 && docker --version >/dev/null 2>&1; then
    log_info "Docker already installed: $(docker --version 2>/dev/null)"
  else
    # Determine which Docker packages are available / already partially installed
    local has_docker_ce has_docker_io
    has_docker_ce=$(dpkg -l docker-ce 2>/dev/null | grep -c '^ii' || echo 0)
    has_docker_io=$(dpkg -l docker.io 2>/dev/null | grep -c '^ii' || echo 0)

    if [[ "${has_docker_ce}" -gt 0 ]]; then
      # Docker CE partially installed — complete the install
      log_info "Completing Docker CE installation..."
      ${APT} install -y -qq docker-ce docker-ce-cli containerd.io 2>&1 \
        || log_warn "Docker CE completion failed."
    elif [[ "${has_docker_io}" -gt 0 ]]; then
      # docker.io partially installed — complete it
      log_info "Completing docker.io installation..."
      ${APT} install -y -qq docker.io 2>&1 || log_warn "docker.io completion failed."
    else
      # Fresh install: try docker.io first (no repo setup needed)
      # Remove potential conflicts first
      ${APT} remove -y containerd 2>/dev/null || true
      if ! ${APT} install -y -qq docker.io 2>&1; then
        log_warn "docker.io failed — trying Docker CE..."
        # Docker CE needs containerd.io which can conflict with Ubuntu's containerd
        ${APT} remove -y containerd runc 2>/dev/null || true
        ${APT} install -y -qq docker-ce docker-ce-cli containerd.io 2>&1 \
          || log_warn "Docker install failed. Install manually: https://docs.docker.com/engine/install/"
      fi
    fi
  fi

  # Add user to docker group (both sudo user and current user)
  if getent group docker >/dev/null 2>&1; then
    local target_user="${SUDO_USER:-${USER}}"
    if [[ -n "${target_user}" ]] && ! id -nG "${target_user}" 2>/dev/null | grep -qw docker; then
      sudo usermod -aG docker "${target_user}" 2>/dev/null || true
      log_info "Added '${target_user}' to docker group. Log out and back in to activate."
    fi
  fi

  # Start Docker daemon if not running
  if command -v docker >/dev/null 2>&1 && ! docker info >/dev/null 2>&1; then
    log_info "Starting Docker daemon..."
    if command -v systemctl >/dev/null 2>&1; then
      sudo systemctl enable docker 2>/dev/null || true
      sudo systemctl start docker 2>/dev/null || true
    fi
  fi

  # Ensure docker compose plugin is available
  if command -v docker >/dev/null 2>&1; then
    if ! docker compose version >/dev/null 2>&1; then
      log_info "Installing docker-compose-plugin..."
      ${APT} install -y -qq docker-compose-plugin 2>/dev/null \
        || log_warn "docker-compose-plugin not available — 'docker compose' may not work."
    fi
  fi

  # ── Phase 7: Node.js 22 LTS ──
  log_info "[7/8] Installing Node.js 22 LTS..."
  local node_major="0"
  if command -v node >/dev/null 2>&1; then
    node_major=$(node --version 2>/dev/null | sed 's/v\([0-9]*\).*/\1/' || echo "0")
    # Ensure numeric
    [[ "${node_major}" =~ ^[0-9]+$ ]] || node_major="0"
  fi

  if [[ "${node_major}" -ge 18 ]]; then
    log_info "Node.js $(node --version) already installed (>= 18)."
  else
    # Remove old Ubuntu Node.js packages that conflict with NodeSource
    # libnode72 (22.04), libnode108/libnode109 (24.04), and related dev packages
    log_info "Removing old Node.js packages to avoid conflicts..."
    local old_node_pkgs
    old_node_pkgs=$(dpkg -l 'libnode*' 'nodejs-doc' 2>/dev/null | awk '/^ii/{print $2}' | tr '\n' ' ')
    if [[ -n "${old_node_pkgs}" ]]; then
      ${APT} remove -y ${old_node_pkgs} 2>/dev/null || true
    fi
    sudo dpkg --configure -a 2>/dev/null || true

    log_info "Setting up NodeSource repository..."
    local nodesource_ok=0
    for attempt in 1 2 3; do
      if curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - 2>&1; then
        nodesource_ok=1
        break
      fi
      log_warn "NodeSource setup attempt ${attempt}/3 failed. Retrying in 5s..."
      sleep 5
    done

    if [[ ${nodesource_ok} -eq 1 ]]; then
      # Force overwrite in case old files from system nodejs remain
      ${APT} install -y -qq --allow-downgrades nodejs 2>&1 \
        || sudo dpkg --force-overwrite -i /var/cache/apt/archives/nodejs_*.deb 2>/dev/null \
        || log_warn "Node.js install failed after NodeSource setup."
      # Update npm
      if command -v npm >/dev/null 2>&1; then
        sudo npm install -g npm@latest 2>/dev/null || true
      fi
    else
      log_warn "NodeSource unavailable. Install Node.js 18+ manually: https://nodejs.org/"
    fi
  fi

  # ── Phase 8: arduino-cli + Android SDK ──
  log_info "[8/8] Installing optional tools (arduino-cli, Android SDK)..."

  # arduino-cli
  if command -v arduino-cli >/dev/null 2>&1; then
    log_info "arduino-cli already installed."
  else
    log_info "Downloading arduino-cli..."
    local acli_url="https://downloads.arduino.cc/arduino-cli/arduino-cli_latest_Linux_64bit.tar.gz"
    local acli_tmp="/tmp/arduino-cli.tar.gz"
    for attempt in 1 2 3; do
      if curl -fsSL -o "${acli_tmp}" "${acli_url}" 2>/dev/null; then
        if sudo tar -xzf "${acli_tmp}" -C /usr/local/bin arduino-cli 2>/dev/null; then
          log_success "arduino-cli installed."
          break
        fi
      fi
      log_warn "arduino-cli download attempt ${attempt}/3 failed."
      sleep 3
    done
    rm -f "${acli_tmp}"
    command -v arduino-cli >/dev/null 2>&1 \
      || log_warn "arduino-cli not installed — firmware builds will be skipped."
  fi

  # Android SDK
  if [[ "${SKIP_ANDROID}" != "1" && -z "${ANDROID_HOME:-}" ]]; then
    local sdk_root="${ROOT_DIR}/android-sdk"
    if [[ -d "${sdk_root}/cmdline-tools/latest/bin" ]]; then
      log_info "Android SDK already present at ${sdk_root}."
      export ANDROID_HOME="${sdk_root}"
    else
      log_info "Downloading Android command-line tools..."
      mkdir -p "${sdk_root}"
      local sdk_zip="/tmp/cmdline-tools.zip"
      if curl -sL -o "${sdk_zip}" \
           "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" 2>/dev/null; then
        unzip -q -o "${sdk_zip}" -d "${sdk_root}" 2>/dev/null
        rm -f "${sdk_zip}"
        # Reorganize into cmdline-tools/latest/ structure
        if [[ -d "${sdk_root}/cmdline-tools" && ! -d "${sdk_root}/cmdline-tools/latest" ]]; then
          mkdir -p "${sdk_root}/cmdline-tools/latest"
          mv "${sdk_root}"/cmdline-tools/bin "${sdk_root}"/cmdline-tools/lib \
             "${sdk_root}"/cmdline-tools/NOTICE.txt "${sdk_root}"/cmdline-tools/source.properties \
             "${sdk_root}/cmdline-tools/latest/" 2>/dev/null || true
        fi
        if [[ -x "${sdk_root}/cmdline-tools/latest/bin/sdkmanager" ]]; then
          yes | "${sdk_root}/cmdline-tools/latest/bin/sdkmanager" \
            --sdk_root="${sdk_root}" --licenses >/dev/null 2>&1 || true
          "${sdk_root}/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${sdk_root}" \
            "platform-tools" "platforms;android-34" "build-tools;34.0.0" 2>/dev/null || true
          export ANDROID_HOME="${sdk_root}"
          log_success "Android SDK installed at ${sdk_root}"
        else
          log_warn "Android SDK structure incomplete. Set ANDROID_HOME manually or use SKIP_ANDROID=1."
        fi
      else
        log_warn "Android SDK download failed. Use SKIP_ANDROID=1 or set ANDROID_HOME."
      fi
    fi
  fi

  # ── Final verification ──
  printf "\n${BOLD}  Dependency check:${RESET}\n"
  local dep_ok=0 dep_total=0
  for dep_cmd in git curl python3 java docker node npm; do
    dep_total=$((dep_total + 1))
    if command -v "${dep_cmd}" >/dev/null 2>&1; then
      local ver=""
      case "${dep_cmd}" in
        java)   ver=$(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/') ;;
        python3) ver=$(python3 --version 2>&1 | sed 's/Python //') ;;
        node)   ver=$(node --version 2>/dev/null) ;;
        npm)    ver=$(npm --version 2>/dev/null) ;;
        docker) ver=$(docker --version 2>/dev/null | sed 's/Docker version \([^,]*\).*/\1/') ;;
        *)      ver="ok" ;;
      esac
      printf "    ${GREEN}%-14s %s${RESET}\n" "${dep_cmd}" "${ver}"
      dep_ok=$((dep_ok + 1))
    else
      printf "    ${RED}%-14s MISSING${RESET}\n" "${dep_cmd}"
    fi
  done
  for dep_cmd in arduino-cli; do
    dep_total=$((dep_total + 1))
    if command -v "${dep_cmd}" >/dev/null 2>&1; then
      printf "    ${GREEN}%-14s installed${RESET}\n" "${dep_cmd}"
      dep_ok=$((dep_ok + 1))
    else
      printf "    ${YELLOW}%-14s not found (optional)${RESET}\n" "${dep_cmd}"
      dep_ok=$((dep_ok + 1))
    fi
  done
  printf "\n"

  log_success "Dependency installation complete (${dep_ok}/${dep_total} verified)."
  if ! docker info >/dev/null 2>&1; then
    log_info "Docker group change requires logout/login. Run: newgrp docker"
  fi
}

# ============================================================================
# BUILD COMMANDS
# ============================================================================

# -- Infrastructure (PostgreSQL + RabbitMQ + topology) -----------------------

_start_infra() {
  ensure_docker || return 1

  log_info "Creating Docker network '${DOCKER_NETWORK}'..."
  "${DOCKER_BIN}" network create "${DOCKER_NETWORK}" >/dev/null 2>&1 || true

  # ── PostgreSQL ──
  log_info "Starting PostgreSQL..."
  if "${DOCKER_BIN}" ps --format '{{.Names}}' | grep -qx "${POSTGRES_CONTAINER}"; then
    log_info "PostgreSQL container already running."
  elif "${DOCKER_BIN}" ps -a --format '{{.Names}}' | grep -qx "${POSTGRES_CONTAINER}"; then
    "${DOCKER_BIN}" start "${POSTGRES_CONTAINER}" \
      || { log_error "Failed to start existing PostgreSQL container."; return 1; }
  else
    "${DOCKER_BIN}" run -d --name "${POSTGRES_CONTAINER}" \
      --network "${DOCKER_NETWORK}" \
      -e "POSTGRES_DB=${DB_NAME}" \
      -e "POSTGRES_USER=${DB_USER}" \
      -e "POSTGRES_PASSWORD=${DB_PASS}" \
      -p 127.0.0.1:5432:5432 \
      -v "${ROOT_DIR}/cloud/db/init.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro" \
      postgres:16-alpine \
      || { log_error "Failed to create PostgreSQL container."; return 1; }
  fi

  # ── RabbitMQ (always recreate to avoid .erlang.cookie permission issues) ──
  log_info "Starting RabbitMQ (fresh container)..."
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
    || { log_error "Failed to create RabbitMQ container."; return 1; }

  # ── Wait for PostgreSQL ──
  log_info "Waiting for PostgreSQL readiness..."
  local ready=0
  for _ in $(seq 1 60); do
    if "${DOCKER_BIN}" exec "${POSTGRES_CONTAINER}" \
         pg_isready -U "${DB_USER}" -d "${DB_NAME}" >/dev/null 2>&1; then
      ready=1; break
    fi
    sleep 2
  done
  [[ ${ready} -eq 1 ]] || { log_error "PostgreSQL did not become ready in 2 minutes."; return 1; }
  log_success "PostgreSQL is ready (port 5432)."

  # ── Wait for RabbitMQ (can take 60-90s with management + MQTT plugins) ──
  log_info "Waiting for RabbitMQ (may take 1-2 minutes)..."
  ready=0
  for _ in $(seq 1 105); do
    if "${DOCKER_BIN}" exec "${RABBITMQ_CONTAINER}" \
         rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
      ready=1; break
    fi
    sleep 2
  done
  if [[ ${ready} -eq 0 ]]; then
    log_warn "RabbitMQ container logs (last 30 lines):"
    "${DOCKER_BIN}" logs "${RABBITMQ_CONTAINER}" 2>&1 | tail -30
    log_error "RabbitMQ did not become ready in 3.5 minutes."
    return 1
  fi
  log_success "RabbitMQ is ready (port 5672, management 15672)."

  # ── Enable MQTT plugin ──
  log_info "Enabling RabbitMQ MQTT plugin..."
  "${DOCKER_BIN}" exec "${RABBITMQ_CONTAINER}" \
    rabbitmq-plugins enable rabbitmq_mqtt rabbitmq_web_mqtt 2>/dev/null || true

  # ── Initialize topology ──
  _init_topology
}

# Shared helper: install pika if needed, then create RabbitMQ exchanges/queues/bindings
_init_topology() {
  log_info "Initializing RabbitMQ topology..."

  if ! "${PYTHON_EXEC}" -c "import pika" 2>/dev/null; then
    log_info "Installing pika for topology initialization..."
    ensure_pip || return 1
    run_pip install pika -q || { log_error "Failed to install pika."; return 1; }
  fi

  local attempt
  for attempt in $(seq 1 10); do
    if (cd "${ROOT_DIR}" && RABBITMQ_PASS="${RABBITMQ_PASS}" \
         "${PYTHON_EXEC}" "${ROOT_DIR}/cloud/scripts/rabbitmq_init.py" 2>&1); then
      log_success "RabbitMQ topology initialized."
      return 0
    fi
    log_warn "Topology init attempt ${attempt}/10 failed. Retrying in 3s..."
    sleep 3
  done

  log_error "RabbitMQ topology initialization failed after 10 attempts."
  return 1
}

cmd_build_cloud() {
  # Validate cloud scripts before starting infra
  log_info "Validating cloud scripts and configs..."

  # Syntax-check all Python scripts in cloud/scripts/
  local py_file
  for py_file in "${ROOT_DIR}"/cloud/scripts/*.py; do
    [[ -f "${py_file}" ]] || continue
    "${PYTHON_BIN}" -m py_compile "${py_file}" \
      || { log_error "Syntax error in ${py_file}"; return 1; }
  done
  log_success "Cloud Python scripts validated."

  # Validate nginx configs (if nginx/docker is available)
  local nginx_ok=1
  if command -v nginx >/dev/null 2>&1; then
    for conf in "${ROOT_DIR}"/cloud/nginx/*.conf; do
      [[ -f "${conf}" ]] || continue
      nginx -t -c "${conf}" 2>/dev/null \
        || { log_warn "nginx config issue: ${conf##*/} (may need Docker context)"; nginx_ok=0; }
    done
  elif command -v "${DOCKER_BIN}" >/dev/null 2>&1 && "${DOCKER_BIN}" info >/dev/null 2>&1; then
    for conf in "${ROOT_DIR}"/cloud/nginx/*.conf; do
      [[ -f "${conf}" ]] || continue
      "${DOCKER_BIN}" run --rm -v "${conf}:/etc/nginx/nginx.conf:ro" \
        nginx:alpine nginx -t 2>&1 \
        || { log_warn "nginx config issue: ${conf##*/}"; nginx_ok=0; }
    done
  else
    log_debug "Neither nginx nor docker available — skipping nginx config validation."
  fi
  [[ ${nginx_ok} -eq 1 ]] && log_success "Nginx configs validated." || true

  _start_infra
}

# -- Backend (Java / Spring Boot / Gradle) -----------------------------------

cmd_build_backend() {
  ensure_java21 || return 1

  log_info "Building 5 Spring Boot microservices (compile + package, skip tests)..."
  log_info "Services: ${BACKEND_SERVICES[*]}"

  (cd "${ROOT_DIR}/backend" && ./gradlew clean build -x test --no-daemon) \
    || { log_error "Backend Gradle build failed."; return 1; }

  log_success "Backend build artifacts ready."
}

# -- Dashboard (React / Vite / TypeScript) -----------------------------------

cmd_build_dashboard() {
  ensure_node18 || return 1

  log_info "Installing npm dependencies..."
  (cd "${ROOT_DIR}/dashboard" && npm install) \
    || { log_error "npm install failed."; return 1; }

  log_info "Running ESLint..."
  (cd "${ROOT_DIR}/dashboard" && npm run lint) \
    || { log_warn "ESLint reported issues (non-fatal)."; }

  log_info "Building dashboard (tsc + vite build)..."
  (cd "${ROOT_DIR}/dashboard" && npm run build) \
    || { log_error "Dashboard build failed."; return 1; }

  log_success "Dashboard built to dashboard/dist/."
}

# -- Edge AI (Python / YOLO / Flask) -----------------------------------------

cmd_build_edge() {
  require_cmd "${PYTHON_BIN}" || return 1

  log_info "Setting up Python environment for Edge AI..."
  setup_python_venv || return 1

  log_info "Installing edge dependencies..."
  run_pip install -r "${ROOT_DIR}/edge/requirements.txt" \
    flake8 pytest pika opencv-python-headless -q \
    || { log_error "pip install for edge failed."; return 1; }

  log_info "Syntax-checking edge Python files..."
  (cd "${ROOT_DIR}/edge" && "${PYTHON_EXEC}" -m py_compile *.py) \
    || { log_error "Edge Python syntax check failed."; return 1; }

  log_info "Running flake8 lint..."
  (cd "${ROOT_DIR}/edge" && "${PYTHON_EXEC}" -m flake8 --max-line-length=120 *.py) \
    || log_warn "flake8 reported issues (non-fatal)."

  log_success "Edge AI code validated."
}

# -- Android (Kotlin / Compose / Gradle) -------------------------------------

cmd_build_android() {
  ensure_java21      || return 1
  ensure_android_home || return 1

  log_info "Building Android app (assembleDebug)..."
  [[ -d "${ROOT_DIR}/android" ]] \
    || { log_error "android/ directory not found."; return 1; }

  (cd "${ROOT_DIR}/android" && \
   ANDROID_HOME="${ANDROID_HOME}" ./gradlew clean assembleDebug --no-daemon) \
    || { log_error "Android build failed."; return 1; }

  log_success "Android APK built: android/app/build/outputs/apk/debug/app-debug.apk"
}

# -- Firmware (ESP32 / Arduino) ----------------------------------------------

cmd_build_firmware() {
  require_cmd arduino-cli || {
    log_error "arduino-cli not found. Run: install-deps"
    return 1
  }

  log_info "Updating ESP32 board index and installing dependencies..."
  arduino-cli core update-index || { log_error "ESP32 board index update failed."; return 1; }
  arduino-cli core install esp32:esp32 || { log_error "ESP32 core install failed."; return 1; }
  arduino-cli lib install "LoRa"       || { log_error "LoRa library install failed."; return 1; }

  log_info "Compiling esp32_lora_bridge_receiver..."
  arduino-cli compile --fqbn esp32:esp32:esp32 \
    "${ROOT_DIR}/firmware/esp32_lora_bridge_receiver" \
    || { log_error "Bridge receiver firmware build failed."; return 1; }

  log_info "Compiling esp32_lora_tag..."
  arduino-cli compile --fqbn esp32:esp32:esp32 \
    "${ROOT_DIR}/firmware/esp32_lora_tag" \
    || { log_error "LoRa tag firmware build failed."; return 1; }

  log_success "Both firmware sketches compiled successfully."
}

# -- AlertManagement (Python scripts for Raspberry Pi PA system) -------------

cmd_build_alertmgmt() {
  require_cmd "${PYTHON_BIN}" || return 1

  log_info "Syntax-checking AlertManagement scripts..."
  (cd "${ROOT_DIR}" && "${PYTHON_EXEC}" -m py_compile AlertManagement/scripts/*.py) \
    || { log_error "AlertManagement syntax check failed."; return 1; }

  log_success "AlertManagement scripts validated."
}

# -- Build All (orchestrator) ------------------------------------------------

cmd_build_all() {
  local fail=0

  track_run "BUILD CLOUD"      cmd_build_cloud      || fail=1
  track_run "BUILD BACKEND"    cmd_build_backend    || fail=1

  if [[ "${SKIP_DASHBOARD}" == "1" ]]; then
    track_skip "BUILD DASHBOARD" "SKIP_DASHBOARD=1"
  elif ! command -v node >/dev/null 2>&1; then
    track_skip "BUILD DASHBOARD" "node not found"
  else
    track_run "BUILD DASHBOARD" cmd_build_dashboard || fail=1
  fi

  track_run "BUILD EDGE"       cmd_build_edge       || fail=1

  if [[ "${SKIP_ANDROID}" == "1" ]]; then
    track_skip "BUILD ANDROID" "SKIP_ANDROID=1"
  else
    track_run "BUILD ANDROID"  cmd_build_android    || fail=1
  fi

  track_run "BUILD ALERTMGMT"  cmd_build_alertmgmt  || fail=1

  if [[ "${SKIP_FIRMWARE}" == "1" ]]; then
    track_skip "BUILD FIRMWARE" "SKIP_FIRMWARE=1"
  elif ! command -v arduino-cli >/dev/null 2>&1; then
    track_skip "BUILD FIRMWARE" "arduino-cli not found"
  else
    track_run "BUILD FIRMWARE" cmd_build_firmware   || fail=1
  fi

  return ${fail}
}

# ============================================================================
# TEST COMMANDS
# ============================================================================

cmd_test_backend() {
  ensure_java21 || return 1

  log_info "Running backend unit tests (JUnit 5, H2 in-memory DB)..."
  (cd "${ROOT_DIR}/backend" && ./gradlew test --no-daemon) \
    || { log_error "Backend tests failed."; return 1; }

  log_success "Backend tests passed."
}

cmd_test_dashboard() {
  ensure_node18 || return 1

  log_info "Running dashboard tests (Vitest)..."
  (cd "${ROOT_DIR}/dashboard" && npm run test -- --run --passWithNoTests) \
    || { log_error "Dashboard tests failed."; return 1; }

  log_success "Dashboard tests passed."
}

cmd_test_edge() {
  require_cmd "${PYTHON_BIN}" || return 1

  # Ensure test dependencies are available
  if ! "${PYTHON_EXEC}" -c "import pytest" 2>/dev/null; then
    log_info "Installing pytest..."
    ensure_pip || return 1
    run_pip install pytest -q || return 1
  fi

  log_info "Running edge tests (pytest)..."
  (cd "${ROOT_DIR}/edge" && "${PYTHON_EXEC}" -m pytest tests/ -v) \
    || { log_error "Edge tests failed."; return 1; }

  log_success "Edge tests passed."
}

cmd_test_android() {
  ensure_java21      || return 1
  ensure_android_home || return 1

  log_info "Running Android unit tests..."
  (cd "${ROOT_DIR}/android" && \
   ANDROID_HOME="${ANDROID_HOME}" ./gradlew testDebugUnitTest --no-daemon) \
    || { log_error "Android tests failed."; return 1; }

  log_success "Android tests passed."
}

cmd_test_all() {
  local fail=0

  track_run "TEST BACKEND" cmd_test_backend || fail=1

  if [[ "${SKIP_DASHBOARD}" == "1" ]]; then
    track_skip "TEST DASHBOARD" "SKIP_DASHBOARD=1"
  elif ! command -v node >/dev/null 2>&1; then
    track_skip "TEST DASHBOARD" "node not found"
  else
    track_run "TEST DASHBOARD" cmd_test_dashboard || fail=1
  fi

  track_run "TEST EDGE" cmd_test_edge || fail=1

  if [[ "${SKIP_ANDROID}" == "1" ]]; then
    track_skip "TEST ANDROID" "SKIP_ANDROID=1"
  else
    track_run "TEST ANDROID" cmd_test_android || fail=1
  fi

  return ${fail}
}

# ============================================================================
# DEPLOY COMMANDS — LOCAL (Non-Docker services)
# ============================================================================

cmd_deploy_infra() {
  _start_infra
}

cmd_deploy_backend() {
  ensure_java21 || return 1

  mkdir -p "${LOG_DIR}"
  : > "${LOG_DIR}/service.pids"

  # Pre-build all services first to avoid Gradle lock contention when
  # multiple bootRun processes compile simultaneously.
  log_info "Pre-building backend services..."
  (cd "${ROOT_DIR}/backend" && ./gradlew build -x test --no-daemon) \
    || { log_error "Backend pre-build failed."; return 1; }

  log_info "Starting 5 backend services sequentially..."
  log_info "Logs directory: ${LOG_DIR}/"

  local svc port pid all_ok=1
  for svc in "${BACKEND_SERVICES[@]}"; do
    port="${SERVICE_PORT[${svc}]}"

    if ! check_port_free "${port}"; then
      log_warn "Port ${port} already in use — ${svc} may already be running. Skipping."
      continue
    fi

    log_info "Starting ${svc} on port ${port}..."
    (cd "${ROOT_DIR}/backend" && \
     ./gradlew ":${svc}:bootRun" --no-daemon) \
       >> "${LOG_DIR}/${svc}.log" 2>&1 &
    pid=$!
    echo "${pid} ${svc} ${port}" >> "${LOG_DIR}/service.pids"

    # Wait for this service before starting the next to avoid resource contention
    if wait_for_port "${port}" 120; then
      log_success "${svc} is up on port ${port} (PID ${pid})."
    else
      log_error "${svc} did not start on port ${port}. Check ${LOG_DIR}/${svc}.log"
      all_ok=0
    fi
  done

  # Print connection summary
  printf "\n${BOLD}  Backend services:${RESET}\n"
  for svc in "${BACKEND_SERVICES[@]}"; do
    port="${SERVICE_PORT[${svc}]}"
    printf "    %-20s http://localhost:%s\n" "${svc}" "${port}"
  done
  printf "\n"

  if [[ -f "${LOG_DIR}/service.pids" ]]; then
    log_info "To stop all services: kill \$(awk '{print \$1}' ${LOG_DIR}/service.pids)"
  fi

  [[ ${all_ok} -eq 1 ]] || return 1
}

cmd_deploy_dashboard() {
  ensure_node18 || return 1

  if ! check_port_free 3000; then
    log_warn "Port 3000 already in use — dashboard may already be running."
    return 0
  fi

  # Ensure dependencies are installed
  if [[ ! -d "${ROOT_DIR}/dashboard/node_modules" ]]; then
    log_info "Installing dashboard dependencies..."
    (cd "${ROOT_DIR}/dashboard" && npm install) || return 1
  fi

  mkdir -p "${LOG_DIR}"

  log_info "Starting Vite dev server on port 3000..."
  (cd "${ROOT_DIR}/dashboard" && npm run dev) \
    >> "${LOG_DIR}/dashboard.log" 2>&1 &
  local pid=$!
  echo "${pid} dashboard 3000" >> "${LOG_DIR}/service.pids"

  if wait_for_port 3000 30; then
    log_success "Dashboard running at http://localhost:3000 (PID ${pid})"
  else
    log_error "Dashboard failed to start. Check ${LOG_DIR}/dashboard.log"
    return 1
  fi
}

cmd_deploy_edge() {
  require_cmd "${PYTHON_BIN}" || return 1

  if ! check_port_free 5000; then
    log_warn "Port 5000 already in use — edge server may already be running."
    return 0
  fi

  mkdir -p "${LOG_DIR}"

  log_info "Starting edge Flask server on port 5000..."
  (cd "${ROOT_DIR}/edge" && "${PYTHON_EXEC}" edge_gui.py) \
    >> "${LOG_DIR}/edge.log" 2>&1 &
  local pid=$!
  echo "${pid} edge 5000" >> "${LOG_DIR}/service.pids"

  if wait_for_port 5000 20; then
    log_success "Edge server running at http://localhost:5000 (PID ${pid})"
  else
    log_warn "Edge server may not have started. Check ${LOG_DIR}/edge.log"
    log_warn "Edge requires GPU + cameras for full operation; dev mode may work without."
  fi
}

cmd_deploy_all() {
  local fail=0

  track_run "DEPLOY INFRA"     cmd_deploy_infra     || fail=1
  track_run "DEPLOY BACKEND"   cmd_deploy_backend   || fail=1

  if [[ "${SKIP_DASHBOARD}" == "1" ]] || ! command -v node >/dev/null 2>&1; then
    track_skip "DEPLOY DASHBOARD" "node not available or SKIP_DASHBOARD=1"
  else
    track_run "DEPLOY DASHBOARD" cmd_deploy_dashboard || fail=1
  fi

  track_run "DEPLOY EDGE" cmd_deploy_edge || fail=1

  # Print final connection info
  printf "\n${BOLD}  Local dev stack:${RESET}\n"
  printf "    %-20s %s\n" "PostgreSQL"   "localhost:5432"
  printf "    %-20s %s\n" "RabbitMQ"     "localhost:5672 (mgmt: http://localhost:15672)"
  printf "    %-20s %s\n" "API Gateway"  "http://localhost:8080"
  printf "    %-20s %s\n" "Dashboard"    "http://localhost:3000"
  printf "    %-20s %s\n" "Edge GUI"     "http://localhost:5000"
  printf "\n"

  [[ -f "${LOG_DIR}/service.pids" ]] && \
    log_info "Stop all: kill \$(awk '{print \$1}' ${LOG_DIR}/service.pids)"

  return ${fail}
}

# ============================================================================
# DEPLOY COMMANDS — DOCKER (Production / VPS)
# ============================================================================

cmd_deploy_docker() {
  ensure_docker || return 1

  local compose_cmd compose_file="${ROOT_DIR}/cloud/docker-compose.vps.yml"
  compose_cmd=$(resolve_compose_cmd) || return 1

  [[ -f "${compose_file}" ]] \
    || { log_error "Compose file not found: ${compose_file}"; return 1; }

  # Build backend JARs (Dockerfiles are multi-stage but we pre-build for speed)
  log_info "Building backend JARs..."
  ensure_java21 || return 1
  (cd "${ROOT_DIR}/backend" && ./gradlew clean build -x test --no-daemon) \
    || { log_error "Backend JAR build failed."; return 1; }

  # Build Docker images for each backend service
  log_info "Building Docker images for backend services..."
  local svc
  for svc in "${BACKEND_SERVICES[@]}"; do
    local dockerfile="${ROOT_DIR}/backend/${svc}/Dockerfile"
    if [[ -f "${dockerfile}" ]]; then
      log_info "  Building image: sudarshanchakra/${svc}:latest"
      "${DOCKER_BIN}" build -t "sudarshanchakra/${svc}:latest" \
        -f "${dockerfile}" "${ROOT_DIR}/backend/" \
        || { log_error "Docker build failed for ${svc}"; return 1; }
    else
      log_warn "No Dockerfile for ${svc} — skipping image build."
    fi
  done

  # Build dashboard image
  if [[ -f "${ROOT_DIR}/dashboard/Dockerfile" ]]; then
    log_info "  Building image: sudarshanchakra/dashboard:latest"
    "${DOCKER_BIN}" build -t "sudarshanchakra/dashboard:latest" \
      "${ROOT_DIR}/dashboard/" \
      || { log_error "Docker build failed for dashboard"; return 1; }
  fi

  # Start everything via Compose
  log_info "Starting Docker Compose stack..."
  ${compose_cmd} -f "${compose_file}" up -d \
    || { log_error "docker compose up failed."; return 1; }

  # Wait for infrastructure readiness
  log_info "Waiting for PostgreSQL..."
  wait_for_port 5432 60 || { log_error "PostgreSQL not ready."; return 1; }
  log_success "PostgreSQL is ready."

  log_info "Waiting for RabbitMQ..."
  wait_for_port 5672 120 || { log_error "RabbitMQ not ready."; return 1; }
  log_success "RabbitMQ is ready."

  # Initialize RabbitMQ topology (shared helper handles pika install + retries)
  _init_topology || log_warn "Topology init failed — services may start without queues."

  # Wait for nginx / API gateway
  log_info "Waiting for services to come online..."
  wait_for_port 9080 60 && log_success "Nginx proxy ready on port 9080." \
    || log_warn "Nginx not responding on port 9080 yet."

  # Print access info
  printf "\n${BOLD}  Docker deployment running:${RESET}\n"
  printf "    %-20s %s\n" "Web UI"         "http://localhost:9080"
  printf "    %-20s %s\n" "API"            "http://localhost:9080/api/"
  printf "    %-20s %s\n" "RabbitMQ Mgmt"  "http://localhost:15672"
  printf "    %-20s %s\n" "PostgreSQL"     "localhost:5432"
  printf "\n"

  log_info "Logs: ${compose_cmd} -f ${compose_file} logs -f"
  log_info "Stop: ${compose_cmd} -f ${compose_file} down"
  log_success "Docker deployment complete."
}

cmd_deploy_docker_stop() {
  ensure_docker || return 1

  local compose_cmd compose_file="${ROOT_DIR}/cloud/docker-compose.vps.yml"
  compose_cmd=$(resolve_compose_cmd) || return 1

  log_info "Stopping Docker Compose stack..."
  ${compose_cmd} -f "${compose_file}" down \
    || { log_error "docker compose down failed."; return 1; }

  log_success "Docker Compose stack stopped."
}

cmd_deploy_docker_logs() {
  ensure_docker || return 1

  local compose_cmd compose_file="${ROOT_DIR}/cloud/docker-compose.vps.yml"
  compose_cmd=$(resolve_compose_cmd) || return 1

  log_info "Tailing Docker Compose logs (Ctrl+C to stop)..."
  ${compose_cmd} -f "${compose_file}" logs -f --tail=100
}

# ============================================================================
# INTERACTIVE MENU (whiptail / dialog TUI)
# ============================================================================

cmd_menu() {
  # Fix TERM if dumb/empty (Cursor IDE, some SSH sessions)
  if [[ "${TERM:-dumb}" == "dumb" || -z "${TERM:-}" ]]; then
    for _t in xterm-256color xterm ansi vt100; do
      if TERM="${_t}" tput clear >/dev/null 2>&1; then
        export TERM="${_t}"
        break
      fi
    done
  fi

  local tui=""
  if command -v whiptail >/dev/null 2>&1; then
    tui="whiptail"
  elif command -v dialog >/dev/null 2>&1; then
    tui="dialog"
  else
    die "Interactive menu requires 'whiptail' or 'dialog'. Install: sudo apt install whiptail"
  fi

  local term_h term_w
  term_h=$(tput lines 2>/dev/null || echo 30)
  term_w=$(tput cols  2>/dev/null || echo 80)
  local dlg_h=$((term_h - 4))
  local dlg_w=$((term_w - 6))
  [[ ${dlg_h} -gt 40 ]] && dlg_h=40
  [[ ${dlg_w} -gt 90 ]] && dlg_w=90
  [[ ${dlg_h} -lt 20 ]] && dlg_h=20
  [[ ${dlg_w} -lt 60 ]] && dlg_w=60
  local list_h=$((dlg_h - 9))

  local choices
  choices=$("${tui}" \
    --title " SudarshanChakra — Setup, Build, Test & Deploy " \
    --checklist \
    "Arrow keys to navigate, Space to toggle, Enter to confirm.\nSelected commands run in the order shown below." \
    ${dlg_h} ${dlg_w} ${list_h} \
    "install-deps"      "[SETUP]  Install system packages (sudo)"           OFF \
    "build-cloud"       "[BUILD]  Validate scripts/nginx + start infra"    OFF \
    "build-backend"     "[BUILD]  5 Spring Boot microservices (Gradle)"     OFF \
    "build-dashboard"   "[BUILD]  Lint + build React dashboard (Vite)"      OFF \
    "build-edge"        "[BUILD]  Validate Edge AI Python code (flake8)"    OFF \
    "build-android"     "[BUILD]  Android app (Gradle assembleDebug)"       OFF \
    "build-firmware"    "[BUILD]  ESP32 firmware (arduino-cli)"             OFF \
    "build-alertmgmt"   "[BUILD]  Validate AlertManagement scripts"         OFF \
    "build-all"         "[BUILD]  >> ALL builds in dependency order <<"     OFF \
    "test-backend"      "[TEST]   Backend unit tests (JUnit 5, H2)"        OFF \
    "test-dashboard"    "[TEST]   Dashboard tests (Vitest)"                 OFF \
    "test-edge"         "[TEST]   Edge AI tests (pytest)"                   OFF \
    "test-android"      "[TEST]   Android unit tests (Gradle)"              OFF \
    "test-all"          "[TEST]   >> ALL test suites <<"                    OFF \
    "deploy-infra"      "[LOCAL]  Start PostgreSQL + RabbitMQ containers"   OFF \
    "deploy-backend"    "[LOCAL]  Start 5 backend services (bootRun)"       OFF \
    "deploy-dashboard"  "[LOCAL]  Start Vite dev server (port 3000)"        OFF \
    "deploy-edge"       "[LOCAL]  Start edge Flask server (port 5000)"      OFF \
    "deploy-all"        "[LOCAL]  >> Full local dev stack <<"               OFF \
    "deploy-docker"     "[DOCKER] Build images + docker-compose up"         OFF \
    "deploy-docker-stop" "[DOCKER] Stop Docker Compose stack"               OFF \
    "deploy-docker-logs" "[DOCKER] Tail Docker Compose logs"                OFF \
    3>&1 1>&2 2>&3) || true

  local cmds=()
  local item
  for item in ${choices}; do
    item="${item%\"}"
    item="${item#\"}"
    [[ -n "${item}" ]] && cmds+=("${item}")
  done

  if [[ ${#cmds[@]} -eq 0 ]]; then
    log_info "No commands selected. Exiting."
    exit 0
  fi

  log_info "Running: ${cmds[*]}"
  exec "$0" "${cmds[@]}"
}

# ============================================================================
# HELP TEXT
# ============================================================================

show_help() {
  cat <<EOF
${BOLD}SudarshanChakra — Unified Setup, Build, Test & Deploy${RESET}
Enterprise Smart Farm Hazard Detection & Security System (IoT + Edge AI + Cloud)

${BOLD}USAGE:${RESET}
  ./${SCRIPT_NAME} [commands...] [options]

  When no command is given, ${CYAN}build-all${RESET} is assumed.
  Multiple commands can be chained: ${DIM}./${SCRIPT_NAME} build-backend test-backend${RESET}

${BOLD}PREREQUISITES:${RESET}
  Ubuntu 22.04 / 24.04 (or Debian-based). Run ${CYAN}install-deps${RESET} to auto-install, or manually:
    Java 21          sudo apt install openjdk-21-jdk
    Node.js 22+      curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
    Python 3.10+     (pre-installed on Ubuntu 22.04/24.04)
    Docker           sudo apt install docker.io && sudo usermod -aG docker \$USER
    arduino-cli      curl -fsSL https://downloads.arduino.cc/arduino-cli/... | sudo tar -xzf -
    Android SDK      Set ANDROID_HOME or use install-deps to download

${BOLD}COMPONENT STACK:${RESET}
  backend        Java 21, Spring Boot 3.2, Gradle 8.7 (5 microservices)
  dashboard      React 18, Vite 5, TypeScript, Tailwind CSS 3
  edge           Python 3.10+, YOLO, Flask, OpenCV
  android        Kotlin 1.9, Jetpack Compose, Gradle 8.5
  firmware       C++ / Arduino, ESP32, LoRa SX1276
  cloud          Docker Compose, PostgreSQL 16, RabbitMQ 3
  alertmgmt      Python (Raspberry Pi PA system)

${BOLD}SERVICE PORTS:${RESET}
  PostgreSQL       5432        RabbitMQ AMQP    5672
  RabbitMQ Mgmt    15672       RabbitMQ MQTT    1883
  API Gateway      8080        Alert Service    8081
  Device Service   8082        Auth Service     8083
  Siren Service    8084        Dashboard (dev)  3000
  Edge GUI         5000        Nginx (Docker)   9080

${BOLD}SETUP:${RESET}
  ${CYAN}install-deps${RESET}            Install all system packages (Ubuntu/Debian; uses sudo)

${BOLD}BUILD:${RESET}
  ${CYAN}build-backend${RESET}           Gradle build (compile + package, skip tests)
  ${CYAN}build-dashboard${RESET}         npm install + ESLint + tsc + Vite build -> dist/
  ${CYAN}build-edge${RESET}              Python venv + pip deps + py_compile + flake8
  ${CYAN}build-android${RESET}           Gradle assembleDebug -> app-debug.apk
  ${CYAN}build-firmware${RESET}          arduino-cli compile (bridge receiver + LoRa tag)
  ${CYAN}build-alertmgmt${RESET}         py_compile AlertManagement/scripts/*.py
  ${CYAN}build-cloud${RESET}             Validate cloud scripts/nginx + start PostgreSQL/RabbitMQ
  ${CYAN}build-all${RESET}               Run all build commands in dependency order

${BOLD}TEST:${RESET}
  ${CYAN}test-backend${RESET}            Gradle test (JUnit 5, H2 in-memory DB)
  ${CYAN}test-dashboard${RESET}          Vitest (--run --passWithNoTests)
  ${CYAN}test-edge${RESET}               pytest edge/tests/ -v
  ${CYAN}test-android${RESET}            Gradle testDebugUnitTest
  ${CYAN}test-all${RESET}                Run all test commands

${BOLD}DEPLOY (Local / Non-Docker):${RESET}
  ${CYAN}deploy-infra${RESET}            Start PostgreSQL + RabbitMQ containers + topology
  ${CYAN}deploy-backend${RESET}          Pre-build, then bootRun 5 services (background, PID tracked)
  ${CYAN}deploy-dashboard${RESET}        Vite dev server on :3000 (background)
  ${CYAN}deploy-edge${RESET}             Flask server on :5000 (background)
  ${CYAN}deploy-all${RESET}              Start full local dev stack (infra + backend + dashboard + edge)

${BOLD}DEPLOY (Docker / Production):${RESET}
  ${CYAN}deploy-docker${RESET}           Build Docker images + docker-compose.vps.yml up
  ${CYAN}deploy-docker-stop${RESET}      docker compose down
  ${CYAN}deploy-docker-logs${RESET}      docker compose logs -f --tail=100

${BOLD}OPTIONS:${RESET}
  ${CYAN}-h, --help${RESET}              Show this help
  ${CYAN}-m, --menu${RESET}              Interactive TUI checklist (whiptail/dialog)
  ${CYAN}-v, --verbose${RESET}           Show extra debug output
  ${CYAN}--no-color${RESET}              Disable colored output

${BOLD}ENVIRONMENT VARIABLES:${RESET}
  SKIP_ANDROID=1          Skip Android in build-all / test-all
  SKIP_FIRMWARE=1         Skip firmware in build-all
  SKIP_DASHBOARD=1        Skip dashboard in build-all / test-all / deploy-all
  ANDROID_HOME            Path to Android SDK (default: ./android-sdk or /opt/android-sdk)
  DB_PASS                 PostgreSQL password     (default: devpassword123)
  RABBITMQ_PASS           RabbitMQ password       (default: devpassword123)
  DOCKER_BIN              Docker binary           (default: docker)
  PYTHON_BIN              Python binary           (default: python3)

${BOLD}EXAMPLES:${RESET}
  ${DIM}# First-time setup: install everything and build${RESET}
  ./${SCRIPT_NAME} install-deps build-all

  ${DIM}# Build everything (default when no command given)${RESET}
  ./${SCRIPT_NAME}

  ${DIM}# Work on a single component${RESET}
  ./${SCRIPT_NAME} build-backend test-backend
  ./${SCRIPT_NAME} build-dashboard test-dashboard

  ${DIM}# Start local development environment${RESET}
  ./${SCRIPT_NAME} deploy-all

  ${DIM}# Start only infra + backend (no frontend/edge)${RESET}
  ./${SCRIPT_NAME} deploy-infra deploy-backend

  ${DIM}# Production Docker deployment${RESET}
  ./${SCRIPT_NAME} deploy-docker

  ${DIM}# Full CI/CD pipeline: build, test, deploy${RESET}
  ./${SCRIPT_NAME} build-all test-all deploy-docker

  ${DIM}# Skip optional components${RESET}
  SKIP_ANDROID=1 SKIP_FIRMWARE=1 ./${SCRIPT_NAME} build-all test-all

EOF
}

# ============================================================================
# MAIN — CLI PARSER & DISPATCHER
# ============================================================================

main() {
  ensure_repo_root

  local commands=()

  # Parse arguments into commands and options
  for arg in "$@"; do
    case "${arg}" in
      -h|--help)
        show_help
        exit 0
        ;;
      -m|--menu)
        cmd_menu
        ;;
      -v|--verbose|--no-color)
        # Already parsed in pre-scan
        ;;
      install-deps|build-backend|build-dashboard|build-edge|build-android|\
      build-firmware|build-alertmgmt|build-cloud|build-all|\
      test-backend|test-dashboard|test-edge|test-android|test-all|\
      deploy-infra|deploy-backend|deploy-dashboard|deploy-edge|deploy-all|\
      deploy-docker|deploy-docker-stop|deploy-docker-logs)
        commands+=("${arg}")
        ;;
      *)
        log_error "Unknown command: ${arg}"
        log_info "Run './${SCRIPT_NAME} --help' for usage."
        exit 1
        ;;
    esac
  done

  # Default to build-all when no commands given
  if [[ ${#commands[@]} -eq 0 ]]; then
    commands=("build-all")
  fi

  local overall_start
  overall_start=$(date +%s)

  # Dispatch each command
  for cmd in "${commands[@]}"; do
    case "${cmd}" in
      install-deps)      track_run "INSTALL DEPS"      cmd_install_deps      ;;
      build-cloud)       track_run "BUILD CLOUD"       cmd_build_cloud       ;;
      build-backend)     track_run "BUILD BACKEND"     cmd_build_backend     ;;
      build-dashboard)   track_run "BUILD DASHBOARD"   cmd_build_dashboard   ;;
      build-edge)        track_run "BUILD EDGE"        cmd_build_edge        ;;
      build-android)     track_run "BUILD ANDROID"     cmd_build_android     ;;
      build-firmware)    track_run "BUILD FIRMWARE"     cmd_build_firmware    ;;
      build-alertmgmt)   track_run "BUILD ALERTMGMT"   cmd_build_alertmgmt   ;;
      build-all)         cmd_build_all                                       ;;
      test-backend)      track_run "TEST BACKEND"      cmd_test_backend      ;;
      test-dashboard)    track_run "TEST DASHBOARD"    cmd_test_dashboard    ;;
      test-edge)         track_run "TEST EDGE"         cmd_test_edge         ;;
      test-android)      track_run "TEST ANDROID"      cmd_test_android      ;;
      test-all)          cmd_test_all                                        ;;
      deploy-infra)      track_run "DEPLOY INFRA"      cmd_deploy_infra      ;;
      deploy-backend)    track_run "DEPLOY BACKEND"    cmd_deploy_backend    ;;
      deploy-dashboard)  track_run "DEPLOY DASHBOARD"  cmd_deploy_dashboard  ;;
      deploy-edge)       track_run "DEPLOY EDGE"       cmd_deploy_edge       ;;
      deploy-all)        cmd_deploy_all                                      ;;
      deploy-docker)     track_run "DEPLOY DOCKER"     cmd_deploy_docker     ;;
      deploy-docker-stop)  track_run "STOP DOCKER"     cmd_deploy_docker_stop  ;;
      deploy-docker-logs)  cmd_deploy_docker_logs                            ;;
    esac
  done

  local overall_end
  overall_end=$(date +%s)

  # Print summary table if more than one result was tracked
  if [[ ${#RESULT_NAMES[@]} -gt 0 ]]; then
    print_summary
    printf "\n  ${DIM}Total time: $(format_duration $((overall_end - overall_start)))${RESET}\n\n"
  fi

  [[ ${TOTAL_FAILURES} -eq 0 ]] || exit 1
}

main "$@"
