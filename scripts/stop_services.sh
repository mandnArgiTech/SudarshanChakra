#!/bin/bash
# Stop backend services and dashboard (and any other entries in logs/service.pids, e.g. simulator)
# Usage: ./scripts/stop_services.sh [backend|dashboard|all]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${ROOT_DIR}/logs"
PID_FILE="${LOG_DIR}/service.pids"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

stop_by_pid_file() {
    if [[ ! -f "${PID_FILE}" ]]; then
        log_warn "PID file not found: ${PID_FILE}"
        return 1
    fi

    local count=0
    while IFS= read -r line; do
        [[ -z "${line}" ]] && continue
        
        local pid=$(echo "${line}" | awk '{print $1}')
        local svc=$(echo "${line}" | awk '{print $2}')
        local port=$(echo "${line}" | awk '{print $3}')
        
        # Validate PID is numeric
        if ! [[ "${pid}" =~ ^[0-9]+$ ]]; then
            continue
        fi
        
        # Check if process still exists
        if kill -0 "${pid}" 2>/dev/null; then
            log_info "Stopping ${svc} (PID ${pid}, port ${port})..."
            kill "${pid}" 2>/dev/null || true
            count=$((count + 1))
        else
            log_warn "PID ${pid} (${svc}) not running"
        fi
    done < "${PID_FILE}"
    
    if [[ ${count} -gt 0 ]]; then
        log_info "Stopped ${count} service(s). Waiting 2 seconds..."
        sleep 2
        # Force kill any remaining
        while IFS= read -r line; do
            [[ -z "${line}" ]] && continue
            local pid=$(echo "${line}" | awk '{print $1}')
            if [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null; then
                log_warn "Force killing ${pid}..."
                kill -9 "${pid}" 2>/dev/null || true
            fi
        done < "${PID_FILE}"
    fi
    
    # Clear PID file
    : > "${PID_FILE}"
    return 0
}

stop_by_port() {
    local port=$1
    local name=$2
    
    # Find process using the port
    local pid=$(lsof -ti:${port} 2>/dev/null || true)
    
    if [[ -n "${pid}" ]]; then
        log_info "Stopping ${name} on port ${port} (PID ${pid})..."
        kill "${pid}" 2>/dev/null || true
        sleep 1
        # Force kill if still running
        if kill -0 "${pid}" 2>/dev/null; then
            log_warn "Force killing ${pid}..."
            kill -9 "${pid}" 2>/dev/null || true
        fi
        return 0
    else
        log_warn "${name} not running on port ${port}"
        return 1
    fi
}

stop_backend() {
    log_info "Stopping backend services..."
    
    # Try PID file first
    if stop_by_pid_file; then
        # Also kill any remaining Gradle bootRun processes
        pkill -f "gradle.*bootRun" 2>/dev/null && log_info "Killed remaining Gradle bootRun processes" || true
        return 0
    fi
    
    # Fallback: kill by port
    local stopped=0
    for port in 8080 8081 8082 8083 8084; do
        stop_by_port "${port}" "backend-${port}" && stopped=$((stopped + 1)) || true
    done
    
    # Also kill any Gradle processes
    pkill -f "gradle.*bootRun" 2>/dev/null && log_info "Killed Gradle bootRun processes" || true
    
    if [[ ${stopped} -eq 0 ]]; then
        log_warn "No backend services found running"
    fi
}

stop_dashboard() {
    log_info "Stopping dashboard..."
    
    # Try PID file first
    if [[ -f "${PID_FILE}" ]]; then
        while IFS= read -r line; do
            local svc=$(echo "${line}" | awk '{print $2}')
            if [[ "${svc}" == "dashboard" ]]; then
                local pid=$(echo "${line}" | awk '{print $1}')
                if [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null; then
                    log_info "Stopping dashboard (PID ${pid})..."
                    kill "${pid}" 2>/dev/null || true
                    sleep 1
                    kill -9 "${pid}" 2>/dev/null || true
                    return 0
                fi
            fi
        done < "${PID_FILE}"
    fi
    
    # Fallback: kill by port 3000
    stop_by_port 3000 "dashboard"
    
    # Also kill any node/vite processes
    pkill -f "vite.*3000" 2>/dev/null && log_info "Killed Vite processes" || true
    pkill -f "node.*dashboard" 2>/dev/null && log_info "Killed Node dashboard processes" || true
}

stop_all() {
    log_info "Stopping all services (backend + dashboard)..."
    stop_backend
    stop_dashboard
    log_info "Done!"
}

# Main
case "${1:-all}" in
    backend)
        stop_backend
        ;;
    dashboard)
        stop_dashboard
        ;;
    all|"")
        stop_all
        ;;
    *)
        echo "Usage: $0 [backend|dashboard|all]"
        echo "  backend   - Stop only backend services (ports 8080-8084)"
        echo "  dashboard - Stop only dashboard (port 3000)"
        echo "  all       - Stop everything (default)"
        exit 1
        ;;
esac
