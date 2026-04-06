#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# sc — SudarshanChakra Operations CLI
# ═══════════════════════════════════════════════════════════════════════════
#
# Single entry point for setup, build, test, deploy, upgrade, and operate.
#
# Usage:
#   ./sc.sh <command> [target] [options]
#
# Commands:
#   setup       First-time install of all dependencies
#   build       Build one or all components
#   test        Run tests for one or all components
#   deploy      Deploy to local dev or Docker/VPS
#   upgrade     Rebuild + redeploy a single component (zero-downtime for Docker)
#   restart     Restart a running service without rebuild
#   stop        Stop one or all services
#   status      Health check all services
#   logs        Tail logs for a service
#   db          Database operations (migrate, backup, restore, shell)
#   flash       Flash firmware to ESP devices
#   apk         Build Android APK and optionally push to device
#
# Examples:
#   ./sc.sh setup                  # First-time: install all deps
#   ./sc.sh build all              # Build everything
#   ./sc.sh deploy local           # Full local dev stack
#   ./sc.sh deploy docker          # Production Docker stack
#   ./sc.sh status                 # Health check all services
#   ./sc.sh upgrade backend auth-service  # Rebuild + redeploy auth-service only
#   ./sc.sh upgrade edge           # Push new edge code, restart pipeline
#   ./sc.sh upgrade dashboard      # Rebuild dashboard, restart nginx
#   ./sc.sh db migrate             # Run pending Flyway migrations
#   ./sc.sh db backup              # pg_dump to timestamped file
#   ./sc.sh db restore latest      # Restore from latest backup
#   ./sc.sh apk build              # Build debug APK
#   ./sc.sh apk push               # Build + install on connected device
#   ./sc.sh flash water            # Flash ESP8266 water sensor
#   ./sc.sh restart alert-service  # Restart just alert-service
#   ./sc.sh logs auth-service      # Tail auth-service logs
#   ./sc.sh logs edge              # Tail edge AI logs
# ═══════════════════════════════════════════════════════════════════════════

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLOUD="$ROOT/cloud"
LOG_DIR="$ROOT/logs"
BACKUP_DIR="$ROOT/backups"

# Colors
if [[ -t 1 ]]; then
  R=$'\033[31m' G=$'\033[32m' Y=$'\033[33m' B=$'\033[34m'
  C=$'\033[36m' W=$'\033[1m' D=$'\033[2m' X=$'\033[0m'
else
  R='' G='' Y='' B='' C='' W='' D='' X=''
fi

info()  { printf "${B}[%s]${X} %s\n" "$(date +%H:%M:%S)" "$1"; }
ok()    { printf "${G}[%s] ✓${X} %s\n" "$(date +%H:%M:%S)" "$1"; }
warn()  { printf "${Y}[%s] !${X} %s\n" "$(date +%H:%M:%S)" "$1" >&2; }
fail()  { printf "${R}[%s] ✗${X} %s\n" "$(date +%H:%M:%S)" "$1" >&2; }
die()   { fail "$1"; exit 1; }

# Docker compose helper
dc() {
  local env_file="$CLOUD/.env"
  if [[ -f "$env_file" ]]; then
    (cd "$CLOUD" && docker compose --env-file .env -f docker-compose.vps.yml "$@")
  else
    (cd "$CLOUD" && docker compose -f docker-compose.vps.yml "$@")
  fi
}

# ───────────────────────────────────────────────────────────────────────
# SETUP
# ───────────────────────────────────────────────────────────────────────

cmd_setup() {
  info "Running first-time setup via setup_and_build_all.sh..."
  bash "$ROOT/setup_and_build_all.sh" install-deps
}

# ───────────────────────────────────────────────────────────────────────
# BUILD
# ───────────────────────────────────────────────────────────────────────

cmd_build() {
  local target="${1:-all}"
  case "$target" in
    all)        bash "$ROOT/setup_and_build_all.sh" build-all ;;
    backend)    bash "$ROOT/setup_and_build_all.sh" build-backend ;;
    dashboard)  bash "$ROOT/setup_and_build_all.sh" build-dashboard ;;
    edge)       bash "$ROOT/setup_and_build_all.sh" build-edge ;;
    android)    bash "$ROOT/setup_and_build_all.sh" build-android ;;
    simulator)  bash "$ROOT/setup_and_build_all.sh" build-simulator ;;
    firmware)   bash "$ROOT/setup_and_build_all.sh" build-firmware ;;
    cloud)      bash "$ROOT/setup_and_build_all.sh" build-cloud ;;
    *) die "Unknown build target: $target. Use: all|backend|dashboard|edge|android|simulator|firmware|cloud" ;;
  esac
}

# ───────────────────────────────────────────────────────────────────────
# TEST
# ───────────────────────────────────────────────────────────────────────

cmd_test() {
  local target="${1:-all}"
  case "$target" in
    all)        bash "$ROOT/setup_and_build_all.sh" test-all ;;
    backend)    bash "$ROOT/setup_and_build_all.sh" test-backend ;;
    dashboard)  bash "$ROOT/setup_and_build_all.sh" test-dashboard ;;
    edge)       bash "$ROOT/setup_and_build_all.sh" test-edge ;;
    android)    bash "$ROOT/setup_and_build_all.sh" test-android ;;
    e2e)
      info "Running E2E preflight + test suite..."
      python3 "$ROOT/e2e/preflight_check.py" --config "$ROOT/e2e/config/e2e_config.yml"
      bash "$ROOT/e2e/run_full_e2e.sh"
      ;;
    *) die "Unknown test target: $target. Use: all|backend|dashboard|edge|android|e2e" ;;
  esac
}

# ───────────────────────────────────────────────────────────────────────
# DEPLOY
# ───────────────────────────────────────────────────────────────────────

cmd_deploy() {
  local target="${1:-local}"
  case "$target" in
    local)      bash "$ROOT/setup_and_build_all.sh" deploy-all ;;
    docker)     bash "$ROOT/setup_and_build_all.sh" deploy-docker ;;
    docker-stop) bash "$ROOT/setup_and_build_all.sh" deploy-docker-stop ;;
    infra)      bash "$ROOT/setup_and_build_all.sh" deploy-infra ;;
    *) die "Unknown deploy target: $target. Use: local|docker|docker-stop|infra" ;;
  esac
}

# ───────────────────────────────────────────────────────────────────────
# UPGRADE — rebuild + redeploy a single component without touching others
# ───────────────────────────────────────────────────────────────────────

cmd_upgrade() {
  local target="${1:?Usage: sc.sh upgrade <target> [service]}"
  local service="${2:-}"

  case "$target" in

    backend)
      # Upgrade a specific backend service or all backend services
      if [[ -z "$service" ]]; then
        info "Upgrading ALL backend services..."
        local services=(auth-service alert-service device-service siren-service mdm-service api-gateway)
      else
        local services=("$service")
      fi

      info "Building backend JARs..."
      (cd "$ROOT/backend" && ./gradlew clean build -x test --no-daemon) \
        || die "Backend build failed"

      for svc in "${services[@]}"; do
        info "Upgrading $svc..."
        local dockerfile="$ROOT/backend/$svc/Dockerfile"
        if [[ -f "$dockerfile" ]]; then
          docker build -t "sudarshanchakra/$svc:latest" \
            -f "$dockerfile" "$ROOT/backend/" \
            || die "Docker build failed for $svc"

          # Rolling restart: stop old, start new
          dc stop "$svc" 2>/dev/null || true
          dc up -d "$svc" \
            || die "Failed to start $svc"

          ok "$svc upgraded and running"
        else
          warn "No Dockerfile for $svc — skipping Docker build"
        fi
      done

      info "Waiting for services to stabilize..."
      sleep 5
      cmd_status_backend
      ;;

    dashboard)
      info "Rebuilding dashboard..."
      if [[ -f "$ROOT/dashboard/Dockerfile" ]]; then
        docker build -t "sudarshanchakra/dashboard:latest" "$ROOT/dashboard/" \
          || die "Dashboard Docker build failed"
        dc stop react-dashboard 2>/dev/null || true
        dc up -d react-dashboard \
          || die "Failed to restart dashboard"
        ok "Dashboard upgraded"
      else
        # Local mode: rebuild + restart Vite
        (cd "$ROOT/dashboard" && npm install && npm run build) \
          || die "Dashboard build failed"
        ok "Dashboard rebuilt. Restart the dev server manually if running locally."
      fi
      ;;

    edge)
      local edge_host="${EDGE_HOST:-}"
      if [[ -z "$edge_host" ]]; then
        # Local edge: restart the process
        info "Upgrading local edge..."
        local edge_pid
        edge_pid=$(grep "edge" "$LOG_DIR/service.pids" 2>/dev/null | awk '{print $1}' | head -1)
        if [[ -n "$edge_pid" ]] && kill -0 "$edge_pid" 2>/dev/null; then
          kill "$edge_pid" 2>/dev/null || true
          sleep 2
        fi
        (cd "$ROOT/edge" && python3 edge_gui.py) \
          </dev/null >> "$LOG_DIR/edge.log" 2>&1 &
        echo "$! edge 5000" >> "$LOG_DIR/service.pids"
        ok "Edge restarted locally (PID $!)"
      else
        # Remote edge: rsync + restart via SSH
        info "Upgrading remote edge at $edge_host..."
        rsync -avz --exclude='__pycache__' --exclude='.venv' --exclude='*.pyc' \
          "$ROOT/edge/" "$edge_host:~/SudarshanChakra/edge/" \
          || die "rsync to $edge_host failed"
        ssh "$edge_host" "cd ~/SudarshanChakra && docker compose -f edge/docker-compose.yml restart edge-ai" \
          || die "Edge restart on $edge_host failed"
        ok "Edge upgraded on $edge_host"
      fi
      ;;

    android|apk)
      cmd_apk build
      ;;

    water)
      info "Upgrading water module..."
      warn "ESP8266 firmware must be flashed separately: ./sc.sh flash water"
      # Restart water-related backend if in Docker
      if dc ps device-service 2>/dev/null | grep -q "Up"; then
        dc restart device-service
        ok "device-service restarted (handles water endpoints)"
      fi
      ;;

    db|database)
      cmd_db migrate
      ;;

    *)
      die "Unknown upgrade target: $target. Use: backend [service]|dashboard|edge|android|water|db"
      ;;
  esac
}

# ───────────────────────────────────────────────────────────────────────
# RESTART — restart without rebuild
# ───────────────────────────────────────────────────────────────────────

cmd_restart() {
  local target="${1:?Usage: sc.sh restart <service>}"

  # Check if Docker stack is running
  if dc ps 2>/dev/null | grep -q "Up"; then
    info "Restarting $target in Docker..."
    dc restart "$target" || die "Failed to restart $target"
    ok "$target restarted"
  else
    # Local mode: find PID and restart
    local pid
    pid=$(grep "$target" "$LOG_DIR/service.pids" 2>/dev/null | awk '{print $1}' | head -1)
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      info "Stopping $target (PID $pid)..."
      kill "$pid" 2>/dev/null || true
      sleep 2
      info "Process stopped. Re-run: ./sc.sh deploy local to start all services."
    else
      warn "$target not found in running services. Use: ./sc.sh deploy local"
    fi
  fi
}

# ───────────────────────────────────────────────────────────────────────
# STOP
# ───────────────────────────────────────────────────────────────────────

cmd_stop() {
  local target="${1:-all}"

  if [[ "$target" == "all" ]]; then
    # Stop Docker if running
    if dc ps 2>/dev/null | grep -q "Up"; then
      info "Stopping Docker stack..."
      dc down
      ok "Docker stack stopped"
    fi

    # Stop local services
    if [[ -f "$LOG_DIR/service.pids" ]]; then
      info "Stopping local services..."
      while IFS=' ' read -r pid svc port; do
        if kill -0 "$pid" 2>/dev/null; then
          kill "$pid" 2>/dev/null && info "Stopped $svc (PID $pid, port $port)"
        fi
      done < "$LOG_DIR/service.pids"
      > "$LOG_DIR/service.pids"
      ok "All local services stopped"
    fi
  else
    cmd_restart "$target"  # restart handles stop logic
  fi
}

# ───────────────────────────────────────────────────────────────────────
# STATUS — health check all services
# ───────────────────────────────────────────────────────────────────────

cmd_status_backend() {
  declare -A ports=(
    [auth-service]=8083 [alert-service]=8081 [device-service]=8082
    [siren-service]=8084 [mdm-service]=8085 [api-gateway]=8080
  )
  for svc in auth-service alert-service device-service siren-service mdm-service api-gateway; do
    local port=${ports[$svc]}
    local url="http://localhost:$port/actuator/health"
    if curl -sf "$url" -o /dev/null --max-time 3 2>/dev/null; then
      printf "  ${G}●${X} %-22s port %-5s ${G}UP${X}\n" "$svc" "$port"
    else
      printf "  ${R}○${X} %-22s port %-5s ${R}DOWN${X}\n" "$svc" "$port"
    fi
  done
}

cmd_status() {
  printf "\n${W}SudarshanChakra — Service Status${X}\n\n"

  # Backend services
  printf "${W}Backend Services:${X}\n"
  cmd_status_backend

  # Dashboard
  printf "\n${W}Frontend:${X}\n"
  for svc_port in "dashboard:3000" "simulator:3001"; do
    local svc="${svc_port%%:*}" port="${svc_port##*:}"
    if curl -sf "http://localhost:$port" -o /dev/null --max-time 3 2>/dev/null; then
      printf "  ${G}●${X} %-22s port %-5s ${G}UP${X}\n" "$svc" "$port"
    else
      printf "  ${R}○${X} %-22s port %-5s ${R}DOWN${X}\n" "$svc" "$port"
    fi
  done

  # Edge
  printf "\n${W}Edge AI:${X}\n"
  if curl -sf "http://localhost:5000/health" -o /dev/null --max-time 3 2>/dev/null; then
    printf "  ${G}●${X} %-22s port %-5s ${G}UP${X}\n" "edge-ai" "5000"
  else
    printf "  ${R}○${X} %-22s port %-5s ${R}DOWN${X}\n" "edge-ai" "5000"
  fi

  # Infrastructure
  printf "\n${W}Infrastructure:${X}\n"
  for svc_port in "postgresql:5432" "rabbitmq-amqp:5672" "rabbitmq-mqtt:1883" "rabbitmq-mgmt:15672"; do
    local svc="${svc_port%%:*}" port="${svc_port##*:}"
    if (echo > /dev/tcp/127.0.0.1/$port) 2>/dev/null; then
      printf "  ${G}●${X} %-22s port %-5s ${G}UP${X}\n" "$svc" "$port"
    else
      printf "  ${R}○${X} %-22s port %-5s ${R}DOWN${X}\n" "$svc" "$port"
    fi
  done

  # Docker status if running
  if docker compose version >/dev/null 2>&1; then
    printf "\n${W}Docker Containers:${X}\n"
    dc ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || true
  fi

  printf "\n"
}

# ───────────────────────────────────────────────────────────────────────
# LOGS
# ───────────────────────────────────────────────────────────────────────

cmd_logs() {
  local target="${1:?Usage: sc.sh logs <service>}"
  local lines="${2:-100}"

  # Try Docker first
  if dc ps 2>/dev/null | grep -q "$target"; then
    info "Tailing Docker logs for $target..."
    dc logs -f --tail="$lines" "$target"
  # Try local log files
  elif [[ -f "$LOG_DIR/$target.log" ]]; then
    info "Tailing $LOG_DIR/$target.log..."
    tail -f -n "$lines" "$LOG_DIR/$target.log"
  else
    # List available log files
    warn "No logs found for '$target'. Available:"
    ls -1 "$LOG_DIR"/*.log 2>/dev/null | sed 's/.*\//  /' | sed 's/\.log$//'
    echo ""
    info "Docker services:"
    dc ps --format "  {{.Name}}" 2>/dev/null || echo "  (Docker not running)"
  fi
}

# ───────────────────────────────────────────────────────────────────────
# DATABASE
# ───────────────────────────────────────────────────────────────────────

_db_container() {
  # Find PostgreSQL: Docker container or localhost
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "postgres"; then
    echo "docker"
  else
    echo "local"
  fi
}

_db_exec() {
  local mode=$(_db_container)
  if [[ "$mode" == "docker" ]]; then
    docker exec -i postgres "$@"
  else
    "$@"
  fi
}

cmd_db() {
  local action="${1:?Usage: sc.sh db <migrate|backup|restore|shell|reset>}"
  shift

  local DB_NAME="${DB_NAME:-sudarshanchakra}"
  local DB_USER="${DB_USER:-scadmin}"

  case "$action" in

    migrate)
      info "Running Flyway migrations..."
      local migration_dir="$CLOUD/db/flyway"
      if [[ ! -d "$migration_dir" ]]; then
        die "Migration directory not found: $migration_dir"
      fi

      # List pending migrations
      info "Pending migration files:"
      ls -1 "$migration_dir"/V*.sql 2>/dev/null | while read -r f; do
        echo "  $(basename "$f")"
      done

      # Apply via psql (simpler than requiring Flyway CLI)
      for sql_file in "$migration_dir"/V*.sql; do
        [[ -f "$sql_file" ]] || continue
        local fname
        fname=$(basename "$sql_file")

        # Check if already applied (flyway_schema_history or just try)
        info "Applying $fname..."
        if _db_exec psql -U "$DB_USER" -d "$DB_NAME" -f - < "$sql_file" 2>&1; then
          ok "$fname applied"
        else
          warn "$fname may already be applied or has errors — check output above"
        fi
      done
      ok "Migration complete"
      ;;

    backup)
      mkdir -p "$BACKUP_DIR"
      local timestamp
      timestamp=$(date +%Y%m%d_%H%M%S)
      local backup_file="$BACKUP_DIR/sudarshanchakra_${timestamp}.sql.gz"

      info "Backing up database to $backup_file..."
      if [[ "$(_db_container)" == "docker" ]]; then
        docker exec postgres pg_dump -U "$DB_USER" -d "$DB_NAME" | gzip > "$backup_file"
      else
        pg_dump -U "$DB_USER" -d "$DB_NAME" | gzip > "$backup_file"
      fi

      local size
      size=$(du -h "$backup_file" | awk '{print $1}')
      ok "Backup complete: $backup_file ($size)"

      # Keep last 10 backups
      ls -t "$BACKUP_DIR"/sudarshanchakra_*.sql.gz 2>/dev/null | tail -n +11 | xargs rm -f 2>/dev/null
      info "Kept last 10 backups"
      ;;

    restore)
      local target="${1:-latest}"
      local restore_file

      if [[ "$target" == "latest" ]]; then
        restore_file=$(ls -t "$BACKUP_DIR"/sudarshanchakra_*.sql.gz 2>/dev/null | head -1)
        [[ -n "$restore_file" ]] || die "No backups found in $BACKUP_DIR"
      elif [[ -f "$target" ]]; then
        restore_file="$target"
      elif [[ -f "$BACKUP_DIR/$target" ]]; then
        restore_file="$BACKUP_DIR/$target"
      else
        die "Backup not found: $target"
      fi

      warn "This will DROP and recreate the database. Are you sure? (y/N)"
      read -r confirm
      [[ "$confirm" == "y" || "$confirm" == "Y" ]] || die "Aborted"

      info "Restoring from $restore_file..."
      if [[ "$(_db_container)" == "docker" ]]; then
        docker exec postgres psql -U "$DB_USER" -c "DROP DATABASE IF EXISTS $DB_NAME;"
        docker exec postgres psql -U "$DB_USER" -c "CREATE DATABASE $DB_NAME;"
        gunzip -c "$restore_file" | docker exec -i postgres psql -U "$DB_USER" -d "$DB_NAME"
      else
        psql -U "$DB_USER" -c "DROP DATABASE IF EXISTS $DB_NAME;"
        psql -U "$DB_USER" -c "CREATE DATABASE $DB_NAME;"
        gunzip -c "$restore_file" | psql -U "$DB_USER" -d "$DB_NAME"
      fi
      ok "Database restored from $restore_file"
      ;;

    shell)
      info "Opening psql shell..."
      if [[ "$(_db_container)" == "docker" ]]; then
        docker exec -it postgres psql -U "$DB_USER" -d "$DB_NAME"
      else
        psql -U "$DB_USER" -d "$DB_NAME"
      fi
      ;;

    reset)
      warn "This will DROP all tables and re-run init.sql + all migrations. Are you sure? (y/N)"
      read -r confirm
      [[ "$confirm" == "y" || "$confirm" == "Y" ]] || die "Aborted"

      info "Resetting database..."
      _db_exec psql -U "$DB_USER" -d "$DB_NAME" -c \
        "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

      info "Running init.sql..."
      _db_exec psql -U "$DB_USER" -d "$DB_NAME" -f - < "$CLOUD/db/init.sql"

      info "Running migrations..."
      cmd_db migrate
      ok "Database reset complete"
      ;;

    *)
      die "Unknown db action: $action. Use: migrate|backup|restore|shell|reset"
      ;;
  esac
}

# ───────────────────────────────────────────────────────────────────────
# APK — Android build + push
# ───────────────────────────────────────────────────────────────────────

cmd_apk() {
  local action="${1:-build}"

  case "$action" in
    build)
      info "Building Android debug APK..."
      (cd "$ROOT/android" && ./gradlew assembleDebug --no-daemon) \
        || die "Android build failed"
      local apk="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
      [[ -f "$apk" ]] || die "APK not found at $apk"
      local size
      size=$(du -h "$apk" | awk '{print $1}')
      ok "APK built: $apk ($size)"
      ;;

    push|install)
      cmd_apk build
      local apk="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
      info "Installing APK on connected device..."
      adb install -r "$apk" || die "adb install failed. Is a device connected?"
      ok "APK installed on device"
      ;;

    release)
      info "Building release APK..."
      (cd "$ROOT/android" && ./gradlew assembleRelease --no-daemon) \
        || die "Release build failed"
      ok "Release APK built at android/app/build/outputs/apk/release/"
      ;;

    *)
      die "Unknown apk action: $action. Use: build|push|release"
      ;;
  esac
}

# ───────────────────────────────────────────────────────────────────────
# FLASH — firmware to ESP devices
# ───────────────────────────────────────────────────────────────────────

cmd_flash() {
  local target="${1:?Usage: sc.sh flash <water|beacon|bridge>}"
  local port="${2:-/dev/ttyUSB0}"

  case "$target" in
    water|esp8266)
      info "Flashing water sensor (ESP8266)..."
      if [[ -d "$ROOT/../AutoWaterLevelControl" ]]; then
        warn "Water sensor firmware is in a separate repo: AutoWaterLevelControl"
        info "Use PlatformIO: cd ../AutoWaterLevelControl && pio run -t upload --upload-port $port"
      else
        warn "AutoWaterLevelControl repo not found."
        info "Clone: git clone https://github.com/mandnArgiTech/AutoWaterLevelControl.git"
        info "Branch: feature/fluid-level-monitor-esp8266"
        info "Flash: cd AutoWaterLevelControl && pio run -t upload --upload-port $port"
      fi
      ;;

    beacon|worker)
      info "Flashing worker beacon (ESP32)..."
      arduino-cli compile --fqbn esp32:esp32:esp32 "$ROOT/firmware/worker_beacon" \
        || die "Compile failed"
      arduino-cli upload --fqbn esp32:esp32:esp32 -p "$port" "$ROOT/firmware/worker_beacon" \
        || die "Upload failed"
      ok "Worker beacon flashed to $port"
      ;;

    bridge|lora)
      info "Flashing LoRa bridge receiver (ESP32)..."
      arduino-cli compile --fqbn esp32:esp32:esp32 "$ROOT/firmware/esp32_lora_bridge_receiver" \
        || die "Compile failed"
      arduino-cli upload --fqbn esp32:esp32:esp32 -p "$port" "$ROOT/firmware/esp32_lora_bridge_receiver" \
        || die "Upload failed"
      ok "LoRa bridge flashed to $port"
      ;;

    *)
      die "Unknown flash target: $target. Use: water|beacon|bridge"
      ;;
  esac
}

# ───────────────────────────────────────────────────────────────────────
# HELP
# ───────────────────────────────────────────────────────────────────────

cmd_help() {
  cat <<EOF
${W}sc — SudarshanChakra Operations CLI${X}

${W}USAGE:${X}
  ./sc.sh <command> [target] [options]

${W}FIRST TIME:${X}
  ./sc.sh setup                    Install all dependencies (Java, Node, Docker, Python)

${W}BUILD:${X}
  ./sc.sh build all                Build everything
  ./sc.sh build backend            Build backend only (Gradle)
  ./sc.sh build dashboard          Build dashboard only (Vite)
  ./sc.sh build edge               Build edge AI (Python lint + compile)
  ./sc.sh build android            Build Android APK (Gradle)
  ./sc.sh build firmware           Build ESP32 firmware (arduino-cli)

${W}TEST:${X}
  ./sc.sh test all                 Run all test suites
  ./sc.sh test backend             Backend unit tests (JUnit)
  ./sc.sh test dashboard           Dashboard tests (Vitest)
  ./sc.sh test edge                Edge AI tests (pytest)
  ./sc.sh test android             Android unit tests
  ./sc.sh test e2e                 Full E2E suite (preflight + Playwright + Maestro)

${W}DEPLOY:${X}
  ./sc.sh deploy local             Full local dev stack (Postgres + RabbitMQ + services)
  ./sc.sh deploy docker            Production Docker stack (docker-compose.vps.yml)
  ./sc.sh deploy docker-stop       Stop Docker stack

${W}UPGRADE (rebuild + redeploy one component):${X}
  ./sc.sh upgrade backend                Rebuild + restart all backend services
  ./sc.sh upgrade backend auth-service   Rebuild + restart just auth-service
  ./sc.sh upgrade dashboard              Rebuild + restart dashboard
  ./sc.sh upgrade edge                   Restart edge AI (local or remote via EDGE_HOST)
  ./sc.sh upgrade android                Rebuild APK
  ./sc.sh upgrade water                  Restart device-service (water endpoints)
  ./sc.sh upgrade db                     Run pending database migrations

${W}OPERATIONS:${X}
  ./sc.sh status                   Health check all services
  ./sc.sh restart <service>        Restart a single service
  ./sc.sh stop [all|service]       Stop services
  ./sc.sh logs <service>           Tail service logs

${W}DATABASE:${X}
  ./sc.sh db migrate               Run pending Flyway SQL migrations
  ./sc.sh db backup                Backup database (gzipped, timestamped)
  ./sc.sh db restore latest        Restore from latest backup
  ./sc.sh db restore <file>        Restore from specific backup file
  ./sc.sh db shell                 Open psql interactive shell
  ./sc.sh db reset                 Drop all tables + re-run init.sql + migrations

${W}ANDROID:${X}
  ./sc.sh apk build                Build debug APK
  ./sc.sh apk push                 Build + install on connected device (adb)
  ./sc.sh apk release              Build release APK

${W}FIRMWARE:${X}
  ./sc.sh flash water              Flash ESP8266 water sensor (PlatformIO)
  ./sc.sh flash beacon [port]      Flash ESP32 worker beacon
  ./sc.sh flash bridge [port]      Flash ESP32 LoRa bridge receiver

${W}ENVIRONMENT:${X}
  EDGE_HOST=user@192.168.1.50      Remote edge node for 'upgrade edge' (rsync + SSH)
  DB_NAME=sudarshanchakra          Database name (default)
  DB_USER=scadmin                  Database user (default)

EOF
}

# ───────────────────────────────────────────────────────────────────────
# MAIN
# ───────────────────────────────────────────────────────────────────────

main() {
  local cmd="${1:-help}"
  shift 2>/dev/null || true

  case "$cmd" in
    setup)     cmd_setup "$@" ;;
    build)     cmd_build "$@" ;;
    test)      cmd_test "$@" ;;
    deploy)    cmd_deploy "$@" ;;
    upgrade)   cmd_upgrade "$@" ;;
    restart)   cmd_restart "$@" ;;
    stop)      cmd_stop "$@" ;;
    status)    cmd_status "$@" ;;
    logs)      cmd_logs "$@" ;;
    db)        cmd_db "$@" ;;
    apk)       cmd_apk "$@" ;;
    flash)     cmd_flash "$@" ;;
    help|-h|--help) cmd_help ;;
    *) die "Unknown command: $cmd. Run: ./sc.sh help" ;;
  esac
}

main "$@"
