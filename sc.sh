#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# sc — SudarshanChakra Operations CLI
# ═══════════════════════════════════════════════════════════════════════════
# Just run: ./sc.sh — Everything is interactive. No options to remember.
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLOUD="$ROOT/cloud"; LOG_DIR="$ROOT/logs"; BACKUP_DIR="$ROOT/backups"
if [[ -t 1 ]]; then
  R=$'\033[31m' G=$'\033[32m' Y=$'\033[33m' B=$'\033[34m'
  C=$'\033[36m' W=$'\033[1m' D=$'\033[2m' X=$'\033[0m' INV=$'\033[7m'
else R='' G='' Y='' B='' C='' W='' D='' X='' INV=''; fi
info(){ printf "${B}[%s]${X} %s\n" "$(date +%H:%M:%S)" "$1"; }
ok(){ printf "${G}[%s] ✓${X} %s\n" "$(date +%H:%M:%S)" "$1"; }
warn(){ printf "${Y}[%s] !${X} %s\n" "$(date +%H:%M:%S)" "$1" >&2; }
die(){ printf "${R}[%s] ✗${X} %s\n" "$(date +%H:%M:%S)" "$1" >&2; exit 1; }
dc(){ local e="$CLOUD/.env"; if [[ -f "$e" ]]; then (cd "$CLOUD" && docker compose --env-file .env -f docker-compose.vps.yml "$@"); else (cd "$CLOUD" && docker compose -f docker-compose.vps.yml "$@"); fi; }
confirm(){ printf "\n  ${Y}%s${X} [y/N] " "${1:-Continue?}"; local a; read -r a; [[ "$a" == "y" || "$a" == "Y" ]]; }
pause(){ printf "\n  ${D}Press any key...${X}"; read -rsn1; }

# ── Interactive menu (pure bash, no deps) ──
MENU_RESULT=""
menu_select(){
  local title="$1"; shift; local opts=("$@") sel=0 n=${#opts[@]} k
  tput civis 2>/dev/null||true; trap 'tput cnorm 2>/dev/null;tput sgr0 2>/dev/null' RETURN
  while true; do
    tput clear 2>/dev/null||printf "\033[2J\033[H"
    printf "\n  ${W}${C}SudarshanChakra${X}  ${D}Smart Farm Security${X}\n\n  ${W}%s${X}\n\n" "$title"
    local i; for i in "${!opts[@]}"; do
      if [[ $i -eq $sel ]]; then printf "  ${INV}${G} > %-54s ${X}\n" "${opts[$i]}"
      else printf "    ${D}%-54s${X}\n" "${opts[$i]}"; fi
    done
    printf "\n  ${D}↑↓ navigate · Enter select · q quit${X}\n"
    IFS= read -rsn1 k
    case "$k" in
      $'\x1b') read -rsn2 k; case "$k" in '[A') ((sel>0))&&((sel--));; '[B') ((sel<n-1))&&((sel++));; esac;;
      '') MENU_RESULT=$sel; return 0;; 'q'|'Q') MENU_RESULT=-1; return 1;;
      'k') ((sel>0))&&((sel--));; 'j') ((sel<n-1))&&((sel++));;
    esac
  done
}

# ── Status ──
show_status(){
  printf "\n  ${W}Service Health${X}\n\n"
  for s_p in "auth-service:8083" "alert-service:8081" "device-service:8082" "siren-service:8084" "mdm-service:8085" "api-gateway:8080"; do
    local s="${s_p%%:*}" p="${s_p##*:}"
    if curl -sf "http://localhost:$p/actuator/health" -o /dev/null --max-time 2 2>/dev/null; then
      printf "  ${G}●${X} %-24s :%-5s ${G}UP${X}\n" "$s" "$p"
    else printf "  ${R}○${X} %-24s :%-5s ${R}DOWN${X}\n" "$s" "$p"; fi
  done
  for s_p in "dashboard:3000" "simulator:3001" "edge-ai:5000"; do
    local s="${s_p%%:*}" p="${s_p##*:}"
    if curl -sf "http://localhost:$p" -o /dev/null --max-time 2 2>/dev/null; then
      printf "  ${G}●${X} %-24s :%-5s ${G}UP${X}\n" "$s" "$p"
    else printf "  ${R}○${X} %-24s :%-5s ${R}DOWN${X}\n" "$s" "$p"; fi
  done
  for s_p in "postgresql:5432" "rabbitmq:5672" "rabbitmq-mqtt:1883"; do
    local s="${s_p%%:*}" p="${s_p##*:}"
    if (echo>/dev/tcp/127.0.0.1/$p) 2>/dev/null; then
      printf "  ${G}●${X} %-24s :%-5s ${G}UP${X}\n" "$s" "$p"
    else printf "  ${R}○${X} %-24s :%-5s ${R}DOWN${X}\n" "$s" "$p"; fi
  done
  if docker compose version >/dev/null 2>&1 && dc ps 2>/dev/null|grep -q Up; then
    printf "\n  ${W}Docker:${X}\n"; dc ps --format "  {{.Name}}  {{.Status}}" 2>/dev/null||true
  fi
}

# ── Build ──
do_build(){
  menu_select "Build what?" "All components" "Backend (Java/Spring)" "Dashboard (React)" "Edge AI (Python)" "Android APK" "Simulator" "Firmware (ESP32)" "Cloud infra validate" || return 0
  local cmds=("build-all" "build-backend" "build-dashboard" "build-edge" "build-android" "build-simulator" "build-firmware" "build-cloud")
  bash "$ROOT/setup_and_build_all.sh" "${cmds[$MENU_RESULT]}"
}

# ── Test ──
do_test(){
  menu_select "Test what?" "All tests" "Backend (JUnit)" "Dashboard (Vitest)" "Edge AI (pytest)" "Android (unit)" "E2E (full hardware)" || return 0
  case $MENU_RESULT in
    0) bash "$ROOT/setup_and_build_all.sh" test-all;;
    1) bash "$ROOT/setup_and_build_all.sh" test-backend;;
    2) bash "$ROOT/setup_and_build_all.sh" test-dashboard;;
    3) bash "$ROOT/setup_and_build_all.sh" test-edge;;
    4) bash "$ROOT/setup_and_build_all.sh" test-android;;
    5) python3 "$ROOT/e2e/preflight_check.py" --config "$ROOT/e2e/config/e2e_config.yml"||true; bash "$ROOT/e2e/run_full_e2e.sh"||true;;
  esac
}

# ── Deploy ──
do_deploy(){
  menu_select "Deploy where?" "Local dev stack (all services)" "Docker / VPS production" "Infrastructure only (Postgres + RabbitMQ)" "Stop Docker stack" || return 0
  case $MENU_RESULT in
    0) bash "$ROOT/setup_and_build_all.sh" deploy-all;; 1) bash "$ROOT/setup_and_build_all.sh" deploy-docker;;
    2) bash "$ROOT/setup_and_build_all.sh" deploy-infra;; 3) bash "$ROOT/setup_and_build_all.sh" deploy-docker-stop;;
  esac
}

# ── Upgrade ──
do_upgrade(){
  menu_select "Upgrade what?" "Backend — pick one service" "Backend — all 6 services" "Dashboard" "Edge AI (local or remote)" "Android APK" "Database (run migrations)" || return 0
  case $MENU_RESULT in
    0)
      menu_select "Which service?" "auth-service" "alert-service" "device-service" "siren-service" "mdm-service" "api-gateway" || return 0
      local svcs=("auth-service" "alert-service" "device-service" "siren-service" "mdm-service" "api-gateway")
      local svc="${svcs[$MENU_RESULT]}"
      info "Building backend..."; (cd "$ROOT/backend" && ./gradlew clean build -x test --no-daemon)||die "Build failed"
      local df="$ROOT/backend/$svc/Dockerfile"
      if [[ -f "$df" ]] && dc ps 2>/dev/null|grep -q Up; then
        docker build -t "sudarshanchakra/$svc:latest" -f "$df" "$ROOT/backend/"||die "Docker build failed"
        dc stop "$svc" 2>/dev/null||true; dc up -d "$svc"||die "Start failed"; ok "$svc upgraded"
      else ok "Built. Restart manually if local."; fi;;
    1)
      info "Building all backend..."; (cd "$ROOT/backend" && ./gradlew clean build -x test --no-daemon)||die "Build failed"
      for svc in auth-service alert-service device-service siren-service mdm-service api-gateway; do
        local df="$ROOT/backend/$svc/Dockerfile"
        if [[ -f "$df" ]] && dc ps 2>/dev/null|grep -q Up; then
          docker build -t "sudarshanchakra/$svc:latest" -f "$df" "$ROOT/backend/"
          dc stop "$svc" 2>/dev/null||true; dc up -d "$svc"; ok "$svc upgraded"
        fi
      done;;
    2)
      info "Upgrading dashboard..."
      if [[ -f "$ROOT/dashboard/Dockerfile" ]] && dc ps 2>/dev/null|grep -q Up; then
        docker build -t "sudarshanchakra/dashboard:latest" "$ROOT/dashboard/"
        dc stop react-dashboard 2>/dev/null||true; dc up -d react-dashboard; ok "Dashboard upgraded"
      else (cd "$ROOT/dashboard" && npm install && npm run build)||die "Build failed"; ok "Dashboard rebuilt"; fi;;
    3)
      local eh="${EDGE_HOST:-}"
      if [[ -n "$eh" ]]; then
        info "Syncing to $eh..."; rsync -avz --exclude='__pycache__' --exclude='.venv' "$ROOT/edge/" "$eh:~/SudarshanChakra/edge/"
        ssh "$eh" "cd ~/SudarshanChakra && docker compose -f edge/docker-compose.yml restart edge-ai"||true; ok "Edge upgraded on $eh"
      else
        info "Restarting local edge..."
        local pid; pid=$(grep edge "$LOG_DIR/service.pids" 2>/dev/null|awk '{print $1}'|head -1)
        [[ -n "$pid" ]] && kill "$pid" 2>/dev/null||true; sleep 2; mkdir -p "$LOG_DIR"
        (cd "$ROOT/edge" && python3 edge_gui.py)</dev/null>>"$LOG_DIR/edge.log" 2>&1 &
        echo "$! edge 5000">>"$LOG_DIR/service.pids"; ok "Edge restarted. Set EDGE_HOST for remote."
      fi;;
    4)
      info "Building APK..."; (cd "$ROOT/android" && ./gradlew assembleDebug --no-daemon)||die "Failed"
      ok "APK: android/app/build/outputs/apk/debug/app-debug.apk"
      if command -v adb>/dev/null 2>&1; then confirm "Push to device?"&&{ adb install -r "$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"&&ok "Installed"||warn "adb failed"; }; fi;;
    5) do_db_migrate;;
  esac
}

# ── Stop ──
do_stop(){
  menu_select "Stop what?" "Everything" "Docker stack only" "Local services only" || return 0
  case $MENU_RESULT in
    0) dc down 2>/dev/null||true
       [[ -f "$LOG_DIR/service.pids" ]] && { while IFS=' ' read -r p s _; do kill "$p" 2>/dev/null&&info "Stopped $s"||true; done<"$LOG_DIR/service.pids"; >"$LOG_DIR/service.pids"; }
       ok "Everything stopped";;
    1) dc down 2>/dev/null&&ok "Docker stopped";;
    2) [[ -f "$LOG_DIR/service.pids" ]]&&{ while IFS=' ' read -r p s _; do kill "$p" 2>/dev/null&&info "Stopped $s"||true; done<"$LOG_DIR/service.pids"; >"$LOG_DIR/service.pids"; ok "Local stopped"; }||warn "No PID file";;
  esac
}

# ── Logs ──
do_logs(){
  local t=()
  if dc ps --format '{{.Names}}' 2>/dev/null|grep -q .; then while IFS= read -r n; do t+=("Docker: $n"); done< <(dc ps --format '{{.Names}}' 2>/dev/null); fi
  [[ -d "$LOG_DIR" ]] && for f in "$LOG_DIR"/*.log; do [[ -f "$f" ]] && t+=("Local: $(basename "$f" .log)"); done
  [[ ${#t[@]} -eq 0 ]] && { warn "No logs. Deploy first."; pause; return; }
  menu_select "View logs for:" "${t[@]}" || return 0
  local c="${t[$MENU_RESULT]}"
  if [[ "$c" == Docker:* ]]; then dc logs -f --tail=100 "${c#Docker: }"; else tail -f -n 100 "$LOG_DIR/${c#Local: }.log"; fi
}

# ── Database ──
do_db_menu(){
  menu_select "Database:" "Run migrations" "Backup" "Restore from backup" "Open psql shell" "Reset (DANGER)" || return 0
  case $MENU_RESULT in 0) do_db_migrate;; 1) do_db_backup;; 2) do_db_restore;; 3) do_db_shell;; 4) do_db_reset;; esac
}
_dbx(){ if docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then docker exec -i postgres "$@"; else "$@"; fi; }
do_db_migrate(){
  local d="$CLOUD/db/flyway"; [[ -d "$d" ]]||{ warn "No migrations"; return; }
  info "Migrations:"; ls -1 "$d"/V*.sql 2>/dev/null|while read -r f; do echo "  $(basename "$f")"; done
  confirm "Apply?"||return 0
  for f in "$d"/V*.sql; do [[ -f "$f" ]]||continue; info "$(basename "$f")..."; _dbx psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}" < "$f" 2>&1&&ok "Applied"||warn "May exist"; done
}
do_db_backup(){
  mkdir -p "$BACKUP_DIR"; local ts; ts=$(date +%Y%m%d_%H%M%S); local f="$BACKUP_DIR/sc_${ts}.sql.gz"
  info "Backing up..."; if docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then docker exec postgres pg_dump -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"|gzip>"$f"; else pg_dump -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"|gzip>"$f"; fi
  ok "$f ($(du -h "$f"|awk '{print $1}'))"; ls -t "$BACKUP_DIR"/sc_*.sql.gz 2>/dev/null|tail -n +11|xargs rm -f 2>/dev/null||true
}
do_db_restore(){
  local bs=(); while IFS= read -r f; do bs+=("$(basename "$f")  $(du -h "$f"|awk '{print $1}')"); done< <(ls -t "$BACKUP_DIR"/sc_*.sql.gz 2>/dev/null)
  [[ ${#bs[@]} -eq 0 ]]&&{ warn "No backups"; return; }
  menu_select "Restore from:" "${bs[@]}"||return 0; local fn="${bs[$MENU_RESULT]%% *}"; local fp="$BACKUP_DIR/$fn"
  printf "\n  ${R}${W}This will DROP the database!${X}\n"; confirm "Sure?"||return 0
  info "Restoring $fn..."
  if docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then
    docker exec postgres psql -U "${DB_USER:-scadmin}" -c "DROP DATABASE IF EXISTS ${DB_NAME:-sudarshanchakra};"
    docker exec postgres psql -U "${DB_USER:-scadmin}" -c "CREATE DATABASE ${DB_NAME:-sudarshanchakra};"
    gunzip -c "$fp"|docker exec -i postgres psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"
  else psql -U "${DB_USER:-scadmin}" -c "DROP DATABASE IF EXISTS ${DB_NAME:-sudarshanchakra};"; psql -U "${DB_USER:-scadmin}" -c "CREATE DATABASE ${DB_NAME:-sudarshanchakra};"; gunzip -c "$fp"|psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"; fi
  ok "Restored"
}
do_db_shell(){ info "psql (Ctrl+D to exit)..."; if docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then docker exec -it postgres psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"; else psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"; fi; }
do_db_reset(){ printf "\n  ${R}${W}DANGER: Destroys ALL data!${X}\n"; confirm "Full reset?"||return 0; info "Resetting..."; _dbx psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"; _dbx psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}" < "$CLOUD/db/init.sql"; do_db_migrate; ok "Reset complete"; }

# ── APK ──
do_apk(){
  menu_select "Android:" "Build debug APK" "Build + push to device" "Build release APK" || return 0
  case $MENU_RESULT in
    0) (cd "$ROOT/android" && ./gradlew assembleDebug --no-daemon)||die "Failed"; ok "APK ready";;
    1) (cd "$ROOT/android" && ./gradlew assembleDebug --no-daemon)||die "Failed"; adb install -r "$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"&&ok "Installed"||die "adb failed";;
    2) (cd "$ROOT/android" && ./gradlew assembleRelease --no-daemon)||die "Failed"; ok "Release APK ready";;
  esac
}

# ── Flash ──
do_flash(){
  menu_select "Flash firmware:" "Water sensor (ESP8266)" "Worker beacon (ESP32)" "LoRa bridge (ESP32)" || return 0
  printf "\n  Port [/dev/ttyUSB0]: "; local port; read -r port; port="${port:-/dev/ttyUSB0}"
  case $MENU_RESULT in
    0) printf "\n  ${Y}Water sensor is in a separate repo:${X}\n  git clone https://github.com/mandnArgiTech/AutoWaterLevelControl.git\n  git checkout feature/fluid-level-monitor-esp8266\n  pio run -t upload --upload-port $port\n";;
    1) arduino-cli compile --fqbn esp32:esp32:esp32 "$ROOT/firmware/worker_beacon"||die "Compile failed"; arduino-cli upload --fqbn esp32:esp32:esp32 -p "$port" "$ROOT/firmware/worker_beacon"||die "Upload failed"; ok "Flashed";;
    2) arduino-cli compile --fqbn esp32:esp32:esp32 "$ROOT/firmware/esp32_lora_bridge_receiver"||die "Compile failed"; arduino-cli upload --fqbn esp32:esp32:esp32 -p "$port" "$ROOT/firmware/esp32_lora_bridge_receiver"||die "Upload failed"; ok "Flashed";;
  esac
}

# ═══ MAIN MENU LOOP ═══
while true; do
  menu_select "What do you want to do?" \
    "Status        — check all services" \
    "Build         — compile components" \
    "Test          — run test suites" \
    "Deploy        — start services" \
    "Upgrade       — rebuild + redeploy one thing" \
    "Stop          — stop services" \
    "Logs          — view service logs" \
    "Database      — migrate / backup / restore" \
    "Android APK   — build or push" \
    "Flash         — ESP firmware" \
    "Setup         — first-time install" \
    "Exit" \
  || break
  tput clear 2>/dev/null||printf "\033[2J\033[H"
  case $MENU_RESULT in
    0) show_status;pause;; 1) do_build;pause;; 2) do_test;pause;; 3) do_deploy;pause;;
    4) do_upgrade;pause;; 5) do_stop;pause;; 6) do_logs;; 7) do_db_menu;pause;;
    8) do_apk;pause;; 9) do_flash;pause;; 10) bash "$ROOT/setup_and_build_all.sh" install-deps;pause;; 11) break;;
  esac
done
tput clear 2>/dev/null||true; printf "\n  ${D}Goodbye.${X}\n\n"
