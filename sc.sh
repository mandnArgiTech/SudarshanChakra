#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# sc — SudarshanChakra Operations CLI
# ═══════════════════════════════════════════════════════════════════════════
# Just run: ./sc.sh — Everything is interactive. No options to remember.
#
# 3 deployment zones:
#   CLOUD VPS  — Docker containers (backend, frontend, infra)
#   FARM LAN   — Edge AI, PA system, water sensor, cameras, ESP32
#   MOBILE     — Android app (normal + kiosk)
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLOUD="$ROOT/cloud"; LOG_DIR="$ROOT/logs"; BACKUP_DIR="$ROOT/backups"

# ── Config (override in .env or environment) ──
VPS_HOST="${VPS_HOST:-}"                         # e.g. root@vivasvan-tech.in
EDGE_HOST="${EDGE_HOST:-}"                       # e.g. devi@192.168.1.50
PA_HOST="${PA_HOST:-}"                           # e.g. pi@192.168.1.200
ESP8266_IP="${ESP8266_IP:-192.168.1.150}"
DB_USER="${DB_USER:-scadmin}"
DB_NAME="${DB_NAME:-sudarshanchakra}"

# Load .env if exists
[[ -f "$ROOT/.env" ]] && set -a && source "$ROOT/.env" 2>/dev/null && set +a || true

if [[ -t 1 ]]; then
  R=$'\033[31m' G=$'\033[32m' Y=$'\033[33m' B=$'\033[34m'
  C=$'\033[36m' W=$'\033[1m' D=$'\033[2m' X=$'\033[0m' INV=$'\033[7m'
else R='' G='' Y='' B='' C='' W='' D='' X='' INV=''; fi
info(){ printf "${B}[%s]${X} %s\n" "$(date +%H:%M:%S)" "$1"; }
ok(){ printf "${G}[%s] ✓${X} %s\n" "$(date +%H:%M:%S)" "$1"; }
warn(){ printf "${Y}[%s] !${X} %s\n" "$(date +%H:%M:%S)" "$1" >&2; }
die(){ printf "${R}[%s] ✗${X} %s\n" "$(date +%H:%M:%S)" "$1" >&2; exit 1; }

# Docker compose on VPS (local or remote)
dc(){
  if [[ -n "$VPS_HOST" ]]; then
    ssh "$VPS_HOST" "cd ~/SudarshanChakra/cloud && docker compose -f docker-compose.vps.yml $*"
  else
    local e="$CLOUD/.env"
    if [[ -f "$e" ]]; then (cd "$CLOUD" && docker compose --env-file .env -f docker-compose.vps.yml "$@")
    else (cd "$CLOUD" && docker compose -f docker-compose.vps.yml "$@"); fi
  fi
}
confirm(){ printf "\n  ${Y}%s${X} [y/N] " "${1:-Continue?}"; local a; read -r a; [[ "$a" == "y" || "$a" == "Y" ]]; }
pause(){ printf "\n  ${D}Press any key...${X}"; read -rsn1; }

# ═══ Interactive menu (pure bash, zero deps) ═══
MENU_RESULT=""
menu_select(){
  local title="$1"; shift; local opts=("$@") sel=0 n=${#opts[@]} k
  tput civis 2>/dev/null||true; trap 'tput cnorm 2>/dev/null;tput sgr0 2>/dev/null' RETURN
  while true; do
    tput clear 2>/dev/null||printf "\033[2J\033[H"
    printf "\n  ${W}${C}SudarshanChakra${X}  ${D}Smart Farm Security${X}\n\n  ${W}%s${X}\n\n" "$title"
    local i; for i in "${!opts[@]}"; do
      if [[ $i -eq $sel ]]; then printf "  ${INV}${G} > %-58s ${X}\n" "${opts[$i]}"
      else printf "    ${D}%-58s${X}\n" "${opts[$i]}"; fi
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

# ═══════════════════════════════════════════════════════════════════════════
# STATUS — all 3 zones
# ═══════════════════════════════════════════════════════════════════════════
_check_port(){ (echo>/dev/tcp/$1/$2) 2>/dev/null; }
_check_http(){ curl -sf "$1" -o /dev/null --max-time 3 2>/dev/null; }
_check_ssh(){ ssh -o ConnectTimeout=3 -o BatchMode=yes "$1" "echo ok" 2>/dev/null|grep -q ok; }
_up(){ printf "  ${G}●${X} %-28s %-18s ${G}%s${X}\n" "$1" "$2" "${3:-UP}"; }
_down(){ printf "  ${R}○${X} %-28s %-18s ${R}%s${X}\n" "$1" "$2" "${3:-DOWN}"; }
_warn_s(){ printf "  ${Y}○${X} %-28s %-18s ${Y}%s${X}\n" "$1" "$2" "$3"; }

show_status(){
  printf "\n"
  printf "  ${W}╔══════════════════════════════════════════════════════════╗${X}\n"
  printf "  ${W}║       SudarshanChakra — System Status                   ║${X}\n"
  printf "  ${W}╚══════════════════════════════════════════════════════════╝${X}\n"

  # ── Zone 1: Cloud VPS ──
  printf "\n  ${W}${C}ZONE 1: CLOUD VPS${X}"
  [[ -n "$VPS_HOST" ]] && printf " ${D}($VPS_HOST)${X}" || printf " ${D}(localhost)${X}"
  printf "\n\n"

  printf "  ${D}Infrastructure (Docker)${X}\n"
  for s_p in "postgresql:5432" "rabbitmq-amqp:5672" "rabbitmq-mqtt:1883" "nginx-proxy:443"; do
    local s="${s_p%%:*}" p="${s_p##*:}"
    if [[ -n "$VPS_HOST" ]]; then
      ssh -o ConnectTimeout=3 "$VPS_HOST" "(echo>/dev/tcp/127.0.0.1/$p) 2>/dev/null" 2>/dev/null && _up "$s" ":$p" || _down "$s" ":$p"
    else _check_port 127.0.0.1 "$p" && _up "$s" ":$p" || _down "$s" ":$p"; fi
  done

  printf "\n  ${D}Backend Microservices (Docker)${X}\n"
  for s_p in "auth-service:8083" "alert-service:8081" "device-service:8082" "siren-service:8084" "mdm-service:8085" "api-gateway:8080"; do
    local s="${s_p%%:*}" p="${s_p##*:}"
    local base="http://127.0.0.1"
    [[ -n "$VPS_HOST" ]] && base="http://$VPS_HOST"
    _check_http "$base:$p/actuator/health" && _up "$s" ":$p" || _down "$s" ":$p"
  done

  printf "\n  ${D}Frontend (Docker)${X}\n"
  for s_p in "dashboard:3000" "simulator:3001"; do
    local s="${s_p%%:*}" p="${s_p##*:}"
    local base="http://127.0.0.1"
    [[ -n "$VPS_HOST" ]] && base="http://$VPS_HOST"
    _check_http "$base:$p" && _up "$s" ":$p" || _down "$s" ":$p"
  done

  # ── Zone 2: Farm LAN ──
  printf "\n  ${W}${C}ZONE 2: FARM LAN${X} ${D}(Sanga Reddy)${X}\n\n"

  printf "  ${D}Edge AI Node${X}\n"
  if [[ -n "$EDGE_HOST" ]]; then
    if _check_ssh "$EDGE_HOST"; then
      _up "edge-ai" "$EDGE_HOST:5000" "REACHABLE"
      # Check if edge Flask is responding
      ssh -o ConnectTimeout=3 "$EDGE_HOST" "curl -sf http://localhost:5000/health -o /dev/null" 2>/dev/null \
        && _up "  edge-flask" ":5000" "RUNNING" || _warn_s "  edge-flask" ":5000" "SSH OK, Flask DOWN"
    else _down "edge-ai" "$EDGE_HOST" "UNREACHABLE"; fi
  else _warn_s "edge-ai" "EDGE_HOST not set" "SKIPPED"; fi

  printf "\n  ${D}PA System (Raspberry Pi)${X}\n"
  if [[ -n "$PA_HOST" ]]; then
    _check_ssh "$PA_HOST" && _up "pa-system" "$PA_HOST" "REACHABLE" || _down "pa-system" "$PA_HOST" "UNREACHABLE"
  else _warn_s "pa-system" "PA_HOST not set" "SKIPPED"; fi

  printf "\n  ${D}Water Sensor (ESP8266)${X}\n"
  _check_http "http://$ESP8266_IP/api/status" && _up "water-sensor" "$ESP8266_IP" "ONLINE" || _down "water-sensor" "$ESP8266_IP" "OFFLINE"

  printf "\n  ${D}Cameras (Tapo RTSP/ONVIF)${X}\n"
  for cam_ip in ${CAMERA_IPS:-192.168.1.201 192.168.1.202}; do
    _check_port "$cam_ip" 554 && _up "camera" "$cam_ip:554" "RTSP OK" || _down "camera" "$cam_ip:554" "OFFLINE"
  done

  printf "\n  ${D}VPN Tunnel (Farm ↔ VPS)${X}\n"
  if ip addr show tun0 >/dev/null 2>&1; then
    local vpn_ip; vpn_ip=$(ip -4 addr show tun0 2>/dev/null|grep -oP 'inet \K[\d.]+')
    _up "openvpn" "tun0 ($vpn_ip)" "CONNECTED"
  else _down "openvpn" "tun0" "NOT CONNECTED"; fi

  printf "\n  ${D}ESP32 Serial (USB)${X}\n"
  local serial; serial=$(ls /dev/ttyUSB* /dev/ttyACM* 2>/dev/null|head -1)
  [[ -n "$serial" ]] && _up "esp32-serial" "$serial" "DETECTED" || _warn_s "esp32-serial" "/dev/ttyUSB*" "NONE"

  # ── Zone 3: Mobile ──
  printf "\n  ${W}${C}ZONE 3: MOBILE${X} ${D}(4G/5G)${X}\n\n"

  printf "  ${D}Android Device${X}\n"
  if command -v adb>/dev/null 2>&1 && adb devices 2>/dev/null|grep -q "device$"; then
    local dev; dev=$(adb devices 2>/dev/null|grep "device$"|head -1|awk '{print $1}')
    _up "android" "adb ($dev)" "CONNECTED"
  else _warn_s "android" "adb" "NO DEVICE"; fi
}

# ═══════════════════════════════════════════════════════════════════════════
# BUILD
# ═══════════════════════════════════════════════════════════════════════════
do_build(){
  menu_select "Build what?" \
    "ALL — everything" \
    "──── ZONE 1: Cloud VPS (Docker) ─────────────────────" \
    "auth-service" \
    "alert-service" \
    "device-service" \
    "siren-service" \
    "mdm-service" \
    "api-gateway" \
    "All 6 backend services" \
    "dashboard (React)" \
    "simulator" \
    "──── ZONE 2: Farm LAN ───────────────────────────────" \
    "edge-ai (Python)" \
    "PA system (AlertManagement)" \
    "esp32-lora-bridge (firmware)" \
    "worker-beacon (firmware)" \
    "esp32-lora-tag (firmware)" \
    "──── ZONE 3: Mobile ─────────────────────────────────" \
    "Android APK (debug)" \
    "Android APK (release)" \
  || return 0

  case $MENU_RESULT in
    0) bash "$ROOT/setup_and_build_all.sh" build-all;;
    1) return 0;;
    2) (cd "$ROOT/backend" && ./gradlew :auth-service:build -x test --no-daemon) && ok "auth-service built";;
    3) (cd "$ROOT/backend" && ./gradlew :alert-service:build -x test --no-daemon) && ok "alert-service built";;
    4) (cd "$ROOT/backend" && ./gradlew :device-service:build -x test --no-daemon) && ok "device-service built";;
    5) (cd "$ROOT/backend" && ./gradlew :siren-service:build -x test --no-daemon) && ok "siren-service built";;
    6) (cd "$ROOT/backend" && ./gradlew :mdm-service:build -x test --no-daemon) && ok "mdm-service built";;
    7) (cd "$ROOT/backend" && ./gradlew :api-gateway:build -x test --no-daemon) && ok "api-gateway built";;
    8) bash "$ROOT/setup_and_build_all.sh" build-backend;;
    9) bash "$ROOT/setup_and_build_all.sh" build-dashboard;;
    10) bash "$ROOT/setup_and_build_all.sh" build-simulator;;
    11) return 0;;
    12) bash "$ROOT/setup_and_build_all.sh" build-edge;;
    13) bash "$ROOT/setup_and_build_all.sh" build-alertmgmt;;
    14) arduino-cli compile --fqbn esp32:esp32:esp32 "$ROOT/firmware/esp32_lora_bridge_receiver" && ok "Bridge compiled";;
    15) arduino-cli compile --fqbn esp32:esp32:esp32 "$ROOT/firmware/worker_beacon" && ok "Beacon compiled";;
    16) arduino-cli compile --fqbn esp32:esp32:esp32 "$ROOT/firmware/esp32_lora_tag" && ok "Tag compiled";;
    17) return 0;;
    18) (cd "$ROOT/android" && ./gradlew assembleDebug --no-daemon) && ok "Debug APK built";;
    19) (cd "$ROOT/android" && ./gradlew assembleRelease --no-daemon) && ok "Release APK built";;
  esac
}

# ═══════════════════════════════════════════════════════════════════════════
# TEST
# ═══════════════════════════════════════════════════════════════════════════
do_test(){
  menu_select "Test what?" \
    "ALL tests" \
    "──── ZONE 1: Cloud VPS ──────────────────────────────" \
    "auth-service (JUnit)" \
    "alert-service (JUnit)" \
    "device-service (JUnit)" \
    "siren-service (JUnit)" \
    "mdm-service (JUnit)" \
    "All backend tests" \
    "dashboard (Vitest)" \
    "──── ZONE 2: Farm LAN ───────────────────────────────" \
    "edge-ai (pytest)" \
    "──── ZONE 3: Mobile ─────────────────────────────────" \
    "Android (unit tests)" \
    "──── Integration ────────────────────────────────────" \
    "E2E preflight (environment check)" \
    "E2E full suite (Playwright + Maestro)" \
  || return 0

  case $MENU_RESULT in
    0) bash "$ROOT/setup_and_build_all.sh" test-all;;
    1) return 0;;
    2) (cd "$ROOT/backend" && ./gradlew :auth-service:test --no-daemon);;
    3) (cd "$ROOT/backend" && ./gradlew :alert-service:test --no-daemon);;
    4) (cd "$ROOT/backend" && ./gradlew :device-service:test --no-daemon);;
    5) (cd "$ROOT/backend" && ./gradlew :siren-service:test --no-daemon);;
    6) (cd "$ROOT/backend" && ./gradlew :mdm-service:test --no-daemon);;
    7) bash "$ROOT/setup_and_build_all.sh" test-backend;;
    8) bash "$ROOT/setup_and_build_all.sh" test-dashboard;;
    9) return 0;;
    10) bash "$ROOT/setup_and_build_all.sh" test-edge;;
    11) return 0;;
    12) bash "$ROOT/setup_and_build_all.sh" test-android;;
    13) return 0;;
    14) python3 "$ROOT/e2e/preflight_check.py" --config "$ROOT/e2e/config/e2e_config.yml"||true;;
    15) bash "$ROOT/e2e/run_full_e2e.sh"||true;;
  esac
}

# ═══════════════════════════════════════════════════════════════════════════
# DEPLOY
# ═══════════════════════════════════════════════════════════════════════════
do_deploy(){
  menu_select "Deploy to:" \
    "ZONE 1: Cloud VPS — full Docker stack" \
    "ZONE 1: Cloud VPS — specific profile" \
    "ZONE 2: Edge AI — deploy to farm node" \
    "ZONE 2: PA System — deploy to Raspberry Pi" \
    "ZONE 2: VPN — connect farm to VPS" \
    "──── Docker control ─────────────────────────────────" \
    "Stop Docker stack" \
    "View Docker container status" \
    "──── Dev machine (local testing only) ───────────────" \
    "Local dev stack (all services on this machine)" \
  || return 0

  case $MENU_RESULT in
    0) # Full VPS deploy
      info "Building images + deploying to VPS..."
      bash "$ROOT/cloud/deploy.sh";;
    1) # Profile select
      menu_select "Docker Compose profile:" "full (all services)" "security (no MDM)" "monitoring (no siren)" "water_only (water + alerts only)" || return 0
      local profiles=("full" "security" "monitoring" "water_only")
      local prof="${profiles[$MENU_RESULT]}"
      info "Deploying with profile: $prof"
      dc --profile "$prof" up -d && ok "Deployed ($prof)";;
    2) # Edge deploy
      if [[ -z "$EDGE_HOST" ]]; then printf "\n  EDGE_HOST not set. Enter (e.g. devi@192.168.1.50): "; read -r EDGE_HOST; fi
      [[ -z "$EDGE_HOST" ]] && { warn "Skipped"; return; }
      info "Deploying edge to $EDGE_HOST..."
      rsync -avz --exclude='__pycache__' --exclude='.venv' --exclude='*.pyc' \
        "$ROOT/edge/" "$EDGE_HOST:~/SudarshanChakra/edge/"||die "rsync failed"
      ssh "$EDGE_HOST" "cd ~/SudarshanChakra/edge && docker compose up -d 2>/dev/null || (pip3 install -r requirements.txt && nohup python3 edge_gui.py > /tmp/edge.log 2>&1 &)"
      ok "Edge deployed to $EDGE_HOST";;
    3) # PA deploy
      if [[ -z "$PA_HOST" ]]; then printf "\n  PA_HOST not set. Enter (e.g. pi@192.168.1.200): "; read -r PA_HOST; fi
      [[ -z "$PA_HOST" ]] && { warn "Skipped"; return; }
      info "Deploying PA system to $PA_HOST..."
      rsync -avz "$ROOT/AlertManagement/" "$PA_HOST:~/AlertManagement/"||die "rsync failed"
      ssh "$PA_HOST" "cd ~/AlertManagement && pip3 install -r requirements.txt 2>/dev/null; sudo systemctl restart pa-controller 2>/dev/null"||true
      ok "PA system deployed to $PA_HOST";;
    4) # VPN
      do_vpn;;
    5) return 0;;
    6) dc down && ok "Docker stopped";;
    7) dc ps;;
    8) return 0;;
    9) bash "$ROOT/setup_and_build_all.sh" deploy-all;;
  esac
}

# ═══════════════════════════════════════════════════════════════════════════
# UPGRADE — rebuild + redeploy ONE thing
# ═══════════════════════════════════════════════════════════════════════════
do_upgrade(){
  menu_select "Upgrade what?" \
    "──── ZONE 1: Cloud VPS (Docker) ─────────────────────" \
    "auth-service" \
    "alert-service" \
    "device-service" \
    "siren-service" \
    "mdm-service" \
    "api-gateway" \
    "All 6 backend services" \
    "dashboard" \
    "simulator" \
    "nginx (reload config)" \
    "Database (run migrations)" \
    "──── ZONE 2: Farm LAN ───────────────────────────────" \
    "edge-ai (rsync + restart)" \
    "PA system (rsync to Pi)" \
    "VPN tunnel (restart)" \
    "──── ZONE 2: Flash Firmware ─────────────────────────" \
    "water-sensor ESP8266 (PlatformIO)" \
    "worker-beacon ESP32 (arduino-cli)" \
    "esp32-lora-bridge (arduino-cli)" \
    "──── ZONE 3: Mobile ─────────────────────────────────" \
    "Android APK (build + push to device)" \
  || return 0

  case $MENU_RESULT in
    0) return 0;;
    1|2|3|4|5|6)
      local svcs=("" "auth-service" "alert-service" "device-service" "siren-service" "mdm-service" "api-gateway")
      _upgrade_cloud_svc "${svcs[$MENU_RESULT]}";;
    7) for svc in auth-service alert-service device-service siren-service mdm-service api-gateway; do _upgrade_cloud_svc "$svc"; done;;
    8) _upgrade_cloud_frontend "dashboard" "react-dashboard";;
    9) _upgrade_cloud_frontend "simulator" "farm-simulator";;
    10) _upgrade_nginx;;
    11) do_db_migrate;;
    12) return 0;;
    13) _upgrade_edge;;
    14) _upgrade_pa;;
    15) do_vpn;;
    16) return 0;;
    17) _flash_water;;
    18) _flash_esp32 "worker_beacon" "Worker beacon";;
    19) _flash_esp32 "esp32_lora_bridge_receiver" "LoRa bridge";;
    20) return 0;;
    21) _upgrade_android;;
  esac
}

_upgrade_cloud_svc(){
  local svc="$1"
  info "Building $svc JAR..."
  (cd "$ROOT/backend" && ./gradlew ":$svc:build" -x test --no-daemon)||die "Build failed"
  local df="$ROOT/backend/$svc/Dockerfile"
  [[ -f "$df" ]]||die "No Dockerfile for $svc"
  info "Building Docker image..."
  docker build -t "sudarshanchakra/$svc:latest" -f "$df" "$ROOT/backend/"||die "Docker build failed"
  if [[ -n "$VPS_HOST" ]]; then
    info "Pushing image to $VPS_HOST..."
    docker save "sudarshanchakra/$svc:latest"|ssh "$VPS_HOST" "docker load"
    ssh "$VPS_HOST" "cd ~/SudarshanChakra/cloud && docker compose -f docker-compose.vps.yml stop $svc && docker compose -f docker-compose.vps.yml up -d $svc"
  else
    dc stop "$svc" 2>/dev/null||true; dc up -d "$svc"||die "Start failed"
  fi
  ok "$svc upgraded"
}

_upgrade_cloud_frontend(){
  local dir="$1" container="$2"
  info "Building $dir..."
  [[ -f "$ROOT/$dir/Dockerfile" ]]||die "No Dockerfile"
  docker build -t "sudarshanchakra/$dir:latest" "$ROOT/$dir/"||die "Build failed"
  if [[ -n "$VPS_HOST" ]]; then
    info "Pushing to $VPS_HOST..."
    docker save "sudarshanchakra/$dir:latest"|ssh "$VPS_HOST" "docker load"
    ssh "$VPS_HOST" "cd ~/SudarshanChakra/cloud && docker compose -f docker-compose.vps.yml stop $container && docker compose -f docker-compose.vps.yml up -d $container"
  else
    dc stop "$container" 2>/dev/null||true; dc up -d "$container"
  fi
  ok "$dir upgraded"
}

_upgrade_nginx(){
  info "Reloading Nginx config..."
  if [[ -n "$VPS_HOST" ]]; then
    ssh "$VPS_HOST" "docker exec nginx-proxy nginx -s reload" && ok "Nginx reloaded on VPS"
  else
    dc exec nginx-proxy nginx -s reload 2>/dev/null && ok "Nginx reloaded" || warn "Failed"
  fi
}

_upgrade_edge(){
  if [[ -z "$EDGE_HOST" ]]; then printf "\n  EDGE_HOST (e.g. devi@192.168.1.50): "; read -r EDGE_HOST; fi
  [[ -z "$EDGE_HOST" ]] && { warn "Skipped"; return; }
  info "Syncing edge code to $EDGE_HOST..."
  rsync -avz --exclude='__pycache__' --exclude='.venv' --exclude='*.pyc' \
    "$ROOT/edge/" "$EDGE_HOST:~/SudarshanChakra/edge/"||die "rsync failed"
  info "Restarting edge..."
  ssh "$EDGE_HOST" "cd ~/SudarshanChakra/edge && docker compose restart edge-ai 2>/dev/null || (pkill -f edge_gui.py 2>/dev/null; sleep 2; nohup python3 edge_gui.py > /tmp/edge.log 2>&1 &)"
  ok "Edge upgraded on $EDGE_HOST"
}

_upgrade_pa(){
  if [[ -z "$PA_HOST" ]]; then printf "\n  PA_HOST (e.g. pi@192.168.1.200): "; read -r PA_HOST; fi
  [[ -z "$PA_HOST" ]] && { warn "Skipped"; return; }
  info "Syncing PA scripts to $PA_HOST..."
  rsync -avz "$ROOT/AlertManagement/" "$PA_HOST:~/AlertManagement/"||die "rsync failed"
  ssh "$PA_HOST" "cd ~/AlertManagement && pip3 install -r requirements.txt 2>/dev/null; sudo systemctl restart pa-controller 2>/dev/null"||true
  ok "PA system updated on $PA_HOST"
}

_upgrade_android(){
  info "Building Android debug APK..."
  (cd "$ROOT/android" && ./gradlew assembleDebug --no-daemon)||die "Build failed"
  local apk="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
  ok "APK: $apk ($(du -h "$apk"|awk '{print $1}'))"
  if command -v adb>/dev/null 2>&1 && adb devices 2>/dev/null|grep -q "device$"; then
    confirm "Push to connected device?" && { adb install -r "$apk" && ok "Installed" || warn "adb failed"; }
  else warn "No device connected. Use: adb install -r $apk"; fi
}

_flash_water(){
  printf "\n  ${C}Water Sensor (ESP8266) — separate repo${X}\n\n"
  echo "  git clone https://github.com/mandnArgiTech/AutoWaterLevelControl.git"
  echo "  cd AutoWaterLevelControl"
  echo "  git checkout feature/fluid-level-monitor-esp8266"
  printf "\n  Port [/dev/ttyUSB0]: "; local port; read -r port; port="${port:-/dev/ttyUSB0}"
  echo ""; echo "  Flash: pio run -t upload --upload-port $port"
  echo "  Config: http://<ESP_IP>/api/config"
}

_flash_esp32(){
  local sketch="$1" label="$2"
  printf "\n  Port [/dev/ttyUSB0]: "; local port; read -r port; port="${port:-/dev/ttyUSB0}"
  info "Compiling $label..."
  arduino-cli compile --fqbn esp32:esp32:esp32 "$ROOT/firmware/$sketch"||die "Compile failed"
  info "Uploading to $port..."
  arduino-cli upload --fqbn esp32:esp32:esp32 -p "$port" "$ROOT/firmware/$sketch"||die "Upload failed"
  ok "$label flashed to $port"
}

# ═══════════════════════════════════════════════════════════════════════════
# VPN
# ═══════════════════════════════════════════════════════════════════════════
do_vpn(){
  menu_select "VPN Tunnel (Farm ↔ VPS):" \
    "Start VPN server (on VPS)" \
    "Stop VPN server" \
    "Restart VPN server" \
    "Check tunnel status + ping edge nodes" \
    "Start VPN client (this machine → VPS)" \
  || return 0

  case $MENU_RESULT in
    0) sudo openvpn --config "$CLOUD/vpn/server.conf" --daemon && ok "VPN server started"||die "Failed";;
    1) sudo killall openvpn 2>/dev/null && ok "VPN stopped"||warn "No VPN running";;
    2) sudo killall openvpn 2>/dev/null; sleep 2; sudo openvpn --config "$CLOUD/vpn/server.conf" --daemon && ok "VPN restarted";;
    3)
      if ip addr show tun0>/dev/null 2>&1; then
        local ip; ip=$(ip -4 addr show tun0 2>/dev/null|grep -oP 'inet \K[\d.]+')
        ok "VPN UP — local IP: $ip"
        for node in 10.8.0.1 10.8.0.10 10.8.0.11; do
          ping -c 1 -W 2 "$node">/dev/null 2>&1 && _up "  $node" "" "REACHABLE" || _down "  $node" "" "UNREACHABLE"
        done
      else _down "VPN" "tun0" "NOT CONNECTED"; fi;;
    4)
      local configs=()
      for f in "$CLOUD"/vpn/client/*.conf; do [[ -f "$f" ]] && configs+=("$(basename "$f")"); done
      [[ ${#configs[@]} -eq 0 ]] && { warn "No client configs"; return; }
      menu_select "Client config:" "${configs[@]}"||return 0
      sudo openvpn --config "$CLOUD/vpn/client/${configs[$MENU_RESULT]}" --daemon && ok "VPN client started";;
  esac
}

# ═══════════════════════════════════════════════════════════════════════════
# STOP
# ═══════════════════════════════════════════════════════════════════════════
do_stop(){
  menu_select "Stop what?" \
    "ZONE 1: Cloud VPS Docker stack" \
    "ZONE 2: Edge AI (remote)" \
    "ZONE 2: VPN tunnel" \
    "Everything (all zones)" \
    "──── Individual Docker container ────────────────────" \
    "auth-service" "alert-service" "device-service" \
    "siren-service" "mdm-service" "api-gateway" \
    "dashboard" "simulator" "nginx" \
  || return 0

  case $MENU_RESULT in
    0) dc down && ok "VPS Docker stopped";;
    1)
      if [[ -n "$EDGE_HOST" ]]; then
        ssh "$EDGE_HOST" "cd ~/SudarshanChakra/edge && docker compose down 2>/dev/null; pkill -f edge_gui.py 2>/dev/null"||true
        ok "Edge stopped on $EDGE_HOST"
      else warn "EDGE_HOST not set"; fi;;
    2) sudo killall openvpn 2>/dev/null && ok "VPN stopped"||warn "No VPN";;
    3) dc down 2>/dev/null||true; sudo killall openvpn 2>/dev/null||true
       [[ -n "$EDGE_HOST" ]] && ssh "$EDGE_HOST" "pkill -f edge_gui.py 2>/dev/null"||true
       ok "Everything stopped";;
    4) return 0;;
    5|6|7|8|9|10|11|12|13)
      local svcs=("" "" "" "" "" "auth-service" "alert-service" "device-service" "siren-service" "mdm-service" "api-gateway" "react-dashboard" "farm-simulator" "nginx-proxy")
      dc stop "${svcs[$MENU_RESULT]}" && ok "${svcs[$MENU_RESULT]} stopped";;
  esac
}

# ═══════════════════════════════════════════════════════════════════════════
# LOGS
# ═══════════════════════════════════════════════════════════════════════════
do_logs(){
  local t=()
  # Docker containers
  local containers; containers=$(dc ps --format '{{.Names}}' 2>/dev/null||true)
  if [[ -n "$containers" ]]; then while IFS= read -r n; do [[ -n "$n" ]] && t+=("VPS Docker: $n"); done <<< "$containers"; fi
  # Local logs
  [[ -d "$LOG_DIR" ]] && for f in "$LOG_DIR"/*.log; do [[ -f "$f" ]] && t+=("Local file: $(basename "$f" .log)"); done
  # Edge remote
  [[ -n "$EDGE_HOST" ]] && t+=("Edge remote: $EDGE_HOST (SSH)")
  [[ ${#t[@]} -eq 0 ]] && { warn "No logs found. Deploy first."; pause; return; }
  menu_select "View logs for:" "${t[@]}"||return 0
  local c="${t[$MENU_RESULT]}"
  if [[ "$c" == "VPS Docker:"* ]]; then dc logs -f --tail=100 "${c#VPS Docker: }"
  elif [[ "$c" == "Edge remote:"* ]]; then ssh "$EDGE_HOST" "tail -f /tmp/edge.log 2>/dev/null || docker logs -f edge-ai --tail=100 2>/dev/null"
  else tail -f -n 100 "$LOG_DIR/${c#Local file: }.log"; fi
}

# ═══════════════════════════════════════════════════════════════════════════
# DATABASE (runs on VPS PostgreSQL Docker container)
# ═══════════════════════════════════════════════════════════════════════════
do_db_menu(){
  menu_select "Database (VPS PostgreSQL):" "Run migrations" "Backup" "Restore from backup" "Open psql shell" "Reset (DANGER)" || return 0
  case $MENU_RESULT in 0) do_db_migrate;; 1) do_db_backup;; 2) do_db_restore;; 3) do_db_shell;; 4) do_db_reset;; esac
}
_psql(){
  if [[ -n "$VPS_HOST" ]]; then ssh "$VPS_HOST" "docker exec -i postgres psql -U $DB_USER -d $DB_NAME" "$@"
  elif docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then docker exec -i postgres psql -U "$DB_USER" -d "$DB_NAME" "$@"
  else psql -U "$DB_USER" -d "$DB_NAME" "$@"; fi
}
do_db_migrate(){
  local d="$CLOUD/db/flyway"; [[ -d "$d" ]]||{ warn "No migrations"; return; }
  info "Migration files:"; ls -1 "$d"/V*.sql 2>/dev/null|while read -r f; do echo "  $(basename "$f")"; done
  confirm "Apply all?"||return 0
  for f in "$d"/V*.sql; do [[ -f "$f" ]]||continue; info "$(basename "$f")..."
    if [[ -n "$VPS_HOST" ]]; then
      ssh "$VPS_HOST" "docker exec -i postgres psql -U $DB_USER -d $DB_NAME" < "$f" 2>&1 && ok "Applied"||warn "May exist"
    elif docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then
      docker exec -i postgres psql -U "$DB_USER" -d "$DB_NAME" < "$f" 2>&1 && ok "Applied"||warn "May exist"
    else psql -U "$DB_USER" -d "$DB_NAME" < "$f" 2>&1 && ok "Applied"||warn "May exist"; fi
  done
}
do_db_backup(){
  mkdir -p "$BACKUP_DIR"; local ts; ts=$(date +%Y%m%d_%H%M%S); local f="$BACKUP_DIR/sc_${ts}.sql.gz"
  info "Backing up..."
  if [[ -n "$VPS_HOST" ]]; then ssh "$VPS_HOST" "docker exec postgres pg_dump -U $DB_USER -d $DB_NAME"|gzip>"$f"
  elif docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then docker exec postgres pg_dump -U "$DB_USER" -d "$DB_NAME"|gzip>"$f"
  else pg_dump -U "$DB_USER" -d "$DB_NAME"|gzip>"$f"; fi
  ok "$f ($(du -h "$f"|awk '{print $1}'))"; ls -t "$BACKUP_DIR"/sc_*.sql.gz 2>/dev/null|tail -n +11|xargs rm -f 2>/dev/null||true
}
do_db_restore(){
  local bs=(); while IFS= read -r f; do bs+=("$(basename "$f")  $(du -h "$f"|awk '{print $1}')"); done< <(ls -t "$BACKUP_DIR"/sc_*.sql.gz 2>/dev/null)
  [[ ${#bs[@]} -eq 0 ]]&&{ warn "No backups"; return; }
  menu_select "Restore from:" "${bs[@]}"||return 0; local fn="${bs[$MENU_RESULT]%% *}"; local fp="$BACKUP_DIR/$fn"
  printf "\n  ${R}${W}This will DROP and recreate the database!${X}\n"; confirm "Sure?"||return 0
  info "Restoring $fn..."
  if [[ -n "$VPS_HOST" ]]; then
    ssh "$VPS_HOST" "docker exec postgres psql -U $DB_USER -c 'DROP DATABASE IF EXISTS $DB_NAME;'"
    ssh "$VPS_HOST" "docker exec postgres psql -U $DB_USER -c 'CREATE DATABASE $DB_NAME;'"
    gunzip -c "$fp"|ssh "$VPS_HOST" "docker exec -i postgres psql -U $DB_USER -d $DB_NAME"
  elif docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then
    docker exec postgres psql -U "$DB_USER" -c "DROP DATABASE IF EXISTS $DB_NAME;"
    docker exec postgres psql -U "$DB_USER" -c "CREATE DATABASE $DB_NAME;"
    gunzip -c "$fp"|docker exec -i postgres psql -U "$DB_USER" -d "$DB_NAME"
  fi; ok "Restored"
}
do_db_shell(){
  info "Opening psql (Ctrl+D to exit)..."
  if [[ -n "$VPS_HOST" ]]; then ssh -t "$VPS_HOST" "docker exec -it postgres psql -U $DB_USER -d $DB_NAME"
  elif docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then docker exec -it postgres psql -U "$DB_USER" -d "$DB_NAME"
  else psql -U "$DB_USER" -d "$DB_NAME"; fi
}
do_db_reset(){
  printf "\n  ${R}${W}DANGER: Destroys ALL data!${X}\n"; confirm "Full database reset?"||return 0
  info "Resetting..."
  if [[ -n "$VPS_HOST" ]]; then
    ssh "$VPS_HOST" "docker exec -i postgres psql -U $DB_USER -d $DB_NAME -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'"
    ssh "$VPS_HOST" "docker exec -i postgres psql -U $DB_USER -d $DB_NAME" < "$CLOUD/db/init.sql"
  elif docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then
    docker exec -i postgres psql -U "$DB_USER" -d "$DB_NAME" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
    docker exec -i postgres psql -U "$DB_USER" -d "$DB_NAME" < "$CLOUD/db/init.sql"
  fi; do_db_migrate; ok "Database reset complete"
}

# ═══════════════════════════════════════════════════════════════════════════
# MAIN MENU
# ═══════════════════════════════════════════════════════════════════════════
while true; do
  menu_select "What do you want to do?" \
    "Status      — all 3 zones (VPS + Farm + Mobile)" \
    "Build       — compile components" \
    "Test        — run test suites" \
    "Deploy      — start services" \
    "Upgrade     — rebuild + redeploy one thing" \
    "Stop        — stop services" \
    "Logs        — view service logs" \
    "Database    — migrate / backup / restore" \
    "VPN         — farm ↔ VPS tunnel" \
    "Setup       — first-time dependency install" \
    "Exit" \
  || break
  tput clear 2>/dev/null||printf "\033[2J\033[H"
  case $MENU_RESULT in
    0) show_status;pause;; 1) do_build;pause;; 2) do_test;pause;; 3) do_deploy;pause;;
    4) do_upgrade;pause;; 5) do_stop;pause;; 6) do_logs;; 7) do_db_menu;pause;;
    8) do_vpn;pause;; 9) bash "$ROOT/setup_and_build_all.sh" install-deps;pause;; 10) break;;
  esac
done
tput clear 2>/dev/null||true; printf "\n  ${D}Goodbye.${X}\n\n"
