#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# sc — SudarshanChakra Operations CLI
# ═══════════════════════════════════════════════════════════════════════════
# Just run: ./sc.sh — Everything is interactive. No options to remember.
# 19 modules. One menu.
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
dc(){
  local e="$CLOUD/.env"
  if [[ -f "$e" ]]; then (cd "$CLOUD" && docker compose --env-file .env -f docker-compose.vps.yml "$@")
  else (cd "$CLOUD" && docker compose -f docker-compose.vps.yml "$@"); fi
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

# ═══ STATUS — all 19 modules ═══
show_status(){
  printf "\n  ${W}Service Health — 19 Modules${X}\n"

  printf "\n  ${C}Backend Microservices (Java/Spring Boot)${X}\n"
  for s_p in "auth-service:8083" "alert-service:8081" "device-service:8082" "siren-service:8084" "mdm-service:8085" "api-gateway:8080"; do
    local s="${s_p%%:*}" p="${s_p##*:}"
    if curl -sf "http://localhost:$p/actuator/health" -o /dev/null --max-time 2 2>/dev/null; then
      printf "  ${G}●${X} %-26s :%-5s ${G}UP${X}\n" "$s" "$p"
    else printf "  ${R}○${X} %-26s :%-5s ${R}DOWN${X}\n" "$s" "$p"; fi
  done

  printf "\n  ${C}Frontend (React/Vite)${X}\n"
  for s_p in "dashboard:3000" "simulator:3001"; do
    local s="${s_p%%:*}" p="${s_p##*:}"
    if curl -sf "http://localhost:$p" -o /dev/null --max-time 2 2>/dev/null; then
      printf "  ${G}●${X} %-26s :%-5s ${G}UP${X}\n" "$s" "$p"
    else printf "  ${R}○${X} %-26s :%-5s ${R}DOWN${X}\n" "$s" "$p"; fi
  done

  printf "\n  ${C}Edge AI (Python/Flask)${X}\n"
  if curl -sf "http://localhost:5000/health" -o /dev/null --max-time 2 2>/dev/null; then
    printf "  ${G}●${X} %-26s :%-5s ${G}UP${X}\n" "edge-ai" "5000"
  else printf "  ${R}○${X} %-26s :%-5s ${R}DOWN${X}\n" "edge-ai" "5000"; fi

  printf "\n  ${C}Infrastructure${X}\n"
  for s_p in "postgresql:5432" "rabbitmq-amqp:5672" "rabbitmq-mqtt:1883" "rabbitmq-mgmt:15672" "nginx:9080"; do
    local s="${s_p%%:*}" p="${s_p##*:}"
    if (echo>/dev/tcp/127.0.0.1/$p) 2>/dev/null; then
      printf "  ${G}●${X} %-26s :%-5s ${G}UP${X}\n" "$s" "$p"
    else printf "  ${R}○${X} %-26s :%-5s ${R}DOWN${X}\n" "$s" "$p"; fi
  done

  printf "\n  ${C}VPN Tunnel${X}\n"
  if ip addr show tun0 >/dev/null 2>&1; then
    local vpn_ip; vpn_ip=$(ip -4 addr show tun0 2>/dev/null | grep -oP 'inet \K[\d.]+' | head -1)
    printf "  ${G}●${X} %-26s %-6s ${G}UP${X} (${vpn_ip:-?})\n" "openvpn-tunnel" "tun0"
  else
    printf "  ${R}○${X} %-26s %-6s ${R}DOWN${X}\n" "openvpn-tunnel" "tun0"
  fi

  printf "\n  ${C}Android App${X}\n"
  if command -v adb>/dev/null 2>&1 && adb devices 2>/dev/null|grep -q "device$"; then
    local dev; dev=$(adb devices 2>/dev/null|grep "device$"|head -1|awk '{print $1}')
    printf "  ${G}●${X} %-26s %-6s ${G}CONNECTED${X} ($dev)\n" "android-device" "adb"
  else
    printf "  ${Y}○${X} %-26s %-6s ${Y}NO DEVICE${X}\n" "android-device" "adb"
  fi

  printf "\n  ${C}Firmware / IoT${X}\n"
  # ESP8266 water sensor
  local esp_ip="${ESP8266_IP:-192.168.1.150}"
  if curl -sf "http://$esp_ip/api/status" -o /dev/null --max-time 2 2>/dev/null; then
    printf "  ${G}●${X} %-26s %-6s ${G}ONLINE${X}\n" "water-sensor (ESP8266)" "$esp_ip"
  else
    printf "  ${Y}○${X} %-26s %-6s ${Y}OFFLINE${X}\n" "water-sensor (ESP8266)" "$esp_ip"
  fi
  # Check USB serial for ESP32
  if ls /dev/ttyUSB* /dev/ttyACM* 2>/dev/null|head -1|grep -q .; then
    local serial; serial=$(ls /dev/ttyUSB* /dev/ttyACM* 2>/dev/null|head -1)
    printf "  ${G}●${X} %-26s %-6s ${G}DETECTED${X}\n" "esp32-serial" "$serial"
  else
    printf "  ${Y}○${X} %-26s %-6s ${Y}NONE${X}\n" "esp32-serial" "/dev/*"
  fi

  printf "\n  ${C}PA System (Raspberry Pi)${X}\n"
  local pa_host="${PA_HOST:-}"
  if [[ -n "$pa_host" ]] && ssh -o ConnectTimeout=2 "$pa_host" "echo ok" 2>/dev/null|grep -q ok; then
    printf "  ${G}●${X} %-26s %-6s ${G}REACHABLE${X}\n" "pa-system" "$pa_host"
  else
    printf "  ${Y}○${X} %-26s %-6s ${Y}NOT CHECKED${X} (set PA_HOST)\n" "pa-system" "ssh"
  fi

  # Docker containers
  if docker compose version >/dev/null 2>&1 && dc ps 2>/dev/null|grep -q .; then
    printf "\n  ${C}Docker Containers${X}\n"
    dc ps --format "  {{.Name}}  {{.Status}}" 2>/dev/null||true
  fi
}

# ═══ BUILD ═══
do_build(){
  menu_select "Build what?" \
    "ALL — everything (backend + frontend + edge + firmware)" \
    "──── Backend ────────────────────────────────────────" \
    "auth-service        (JWT, users, farms, RBAC)" \
    "alert-service       (alerts, WebSocket)" \
    "device-service      (cameras, zones, nodes, water, motors)" \
    "siren-service       (siren trigger/stop)" \
    "mdm-service         (MDM telemetry, commands, OTA)" \
    "api-gateway         (routing, module access filter)" \
    "All 6 backend services" \
    "──── Frontend ───────────────────────────────────────" \
    "dashboard           (React admin portal, :3000)" \
    "simulator           (farm MQTT simulator, :3001)" \
    "──── Edge + Mobile ──────────────────────────────────" \
    "edge-ai             (Python YOLO pipeline, :5000)" \
    "android             (Kotlin APK — normal + kiosk)" \
    "──── Firmware ───────────────────────────────────────" \
    "esp32-lora-bridge   (LoRa bridge receiver)" \
    "worker-beacon       (ESP32 worker tag)" \
    "esp32-lora-tag      (ESP32 LoRa tag)" \
    "──── Infrastructure ─────────────────────────────────" \
    "cloud-infra         (validate scripts + nginx + start PG + RMQ)" \
    "pa-system           (validate AlertManagement scripts)" \
  || return 0

  case $MENU_RESULT in
    0) bash "$ROOT/setup_and_build_all.sh" build-all;;
    1) return 0;; # separator
    2) (cd "$ROOT/backend" && ./gradlew :auth-service:build -x test --no-daemon);;
    3) (cd "$ROOT/backend" && ./gradlew :alert-service:build -x test --no-daemon);;
    4) (cd "$ROOT/backend" && ./gradlew :device-service:build -x test --no-daemon);;
    5) (cd "$ROOT/backend" && ./gradlew :siren-service:build -x test --no-daemon);;
    6) (cd "$ROOT/backend" && ./gradlew :mdm-service:build -x test --no-daemon);;
    7) (cd "$ROOT/backend" && ./gradlew :api-gateway:build -x test --no-daemon);;
    8) bash "$ROOT/setup_and_build_all.sh" build-backend;;
    9) return 0;; # separator
    10) bash "$ROOT/setup_and_build_all.sh" build-dashboard;;
    11) bash "$ROOT/setup_and_build_all.sh" build-simulator;;
    12) return 0;; # separator
    13) bash "$ROOT/setup_and_build_all.sh" build-edge;;
    14) bash "$ROOT/setup_and_build_all.sh" build-android;;
    15) return 0;; # separator
    16) arduino-cli compile --fqbn esp32:esp32:esp32 "$ROOT/firmware/esp32_lora_bridge_receiver" && ok "Bridge compiled";;
    17) arduino-cli compile --fqbn esp32:esp32:esp32 "$ROOT/firmware/worker_beacon" && ok "Beacon compiled";;
    18) arduino-cli compile --fqbn esp32:esp32:esp32 "$ROOT/firmware/esp32_lora_tag" && ok "Tag compiled";;
    19) return 0;; # separator
    20) bash "$ROOT/setup_and_build_all.sh" build-cloud;;
    21) bash "$ROOT/setup_and_build_all.sh" build-alertmgmt;;
  esac
}

# ═══ TEST ═══
do_test(){
  menu_select "Test what?" \
    "ALL — every test suite" \
    "──── Backend (JUnit) ────────────────────────────────" \
    "auth-service tests" \
    "alert-service tests" \
    "device-service tests" \
    "siren-service tests" \
    "mdm-service tests" \
    "All backend tests" \
    "──── Frontend + Edge + Mobile ───────────────────────" \
    "dashboard tests     (Vitest)" \
    "edge-ai tests       (pytest)" \
    "android tests       (JUnit/MockK)" \
    "──── Integration ────────────────────────────────────" \
    "E2E preflight check (environment)" \
    "E2E full suite      (Playwright + Maestro + hardware)" \
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
    8) return 0;;
    9) bash "$ROOT/setup_and_build_all.sh" test-dashboard;;
    10) bash "$ROOT/setup_and_build_all.sh" test-edge;;
    11) bash "$ROOT/setup_and_build_all.sh" test-android;;
    12) return 0;;
    13) python3 "$ROOT/e2e/preflight_check.py" --config "$ROOT/e2e/config/e2e_config.yml"||true;;
    14) bash "$ROOT/e2e/run_full_e2e.sh"||true;;
  esac
}

# ═══ DEPLOY ═══
do_deploy(){
  menu_select "Deploy where?" \
    "Local dev stack — all services on this machine" \
    "Docker / VPS — production (docker-compose.vps.yml)" \
    "──── Individual (local) ─────────────────────────────" \
    "Infrastructure only (PostgreSQL + RabbitMQ + topology)" \
    "Backend only (6 Spring Boot services)" \
    "Dashboard only (:3000)" \
    "Simulator only (:3001)" \
    "Edge AI only (:5000)" \
    "──── Docker control ─────────────────────────────────" \
    "Stop Docker stack" \
    "View Docker logs" \
  || return 0

  case $MENU_RESULT in
    0) bash "$ROOT/setup_and_build_all.sh" deploy-all;;
    1) bash "$ROOT/setup_and_build_all.sh" deploy-docker;;
    2) return 0;;
    3) bash "$ROOT/setup_and_build_all.sh" deploy-infra;;
    4) bash "$ROOT/setup_and_build_all.sh" deploy-backend;;
    5) bash "$ROOT/setup_and_build_all.sh" deploy-dashboard;;
    6) bash "$ROOT/setup_and_build_all.sh" deploy-simulator;;
    7) bash "$ROOT/setup_and_build_all.sh" deploy-edge;;
    8) return 0;;
    9) bash "$ROOT/setup_and_build_all.sh" deploy-docker-stop;;
    10) bash "$ROOT/setup_and_build_all.sh" deploy-docker-logs;;
  esac
}

# ═══ UPGRADE — rebuild + redeploy ONE component ═══
do_upgrade(){
  menu_select "Upgrade what?" \
    "──── Backend (pick service) ─────────────────────────" \
    "auth-service" \
    "alert-service" \
    "device-service" \
    "siren-service" \
    "mdm-service" \
    "api-gateway" \
    "All 6 backend services" \
    "──── Frontend ───────────────────────────────────────" \
    "dashboard" \
    "simulator" \
    "──── Edge + Mobile ──────────────────────────────────" \
    "edge-ai             (local restart or remote via EDGE_HOST)" \
    "android APK         (build + optional push)" \
    "──── Firmware (flash to device) ─────────────────────" \
    "water-sensor        (ESP8266 — PlatformIO instructions)" \
    "worker-beacon       (ESP32 — arduino-cli upload)" \
    "esp32-lora-bridge   (ESP32 — arduino-cli upload)" \
    "──── Infrastructure ─────────────────────────────────" \
    "database            (run pending migrations)" \
    "nginx               (reload config)" \
    "VPN tunnel          (restart OpenVPN)" \
    "PA system           (sync scripts to Raspberry Pi)" \
  || return 0

  case $MENU_RESULT in
    0) return 0;; # separator
    1|2|3|4|5|6)
      local svcs=("" "auth-service" "alert-service" "device-service" "siren-service" "mdm-service" "api-gateway")
      local svc="${svcs[$MENU_RESULT]}"
      _upgrade_backend_svc "$svc";;
    7) for svc in auth-service alert-service device-service siren-service mdm-service api-gateway; do _upgrade_backend_svc "$svc"; done;;
    8) return 0;;
    9) _upgrade_dashboard;;
    10) _upgrade_simulator;;
    11) return 0;;
    12) _upgrade_edge;;
    13) _upgrade_android;;
    14) return 0;;
    15) _flash_water;;
    16) _flash_esp32 "worker_beacon" "Worker beacon";;
    17) _flash_esp32 "esp32_lora_bridge_receiver" "LoRa bridge";;
    18) return 0;;
    19) do_db_migrate;;
    20) _upgrade_nginx;;
    21) _upgrade_vpn;;
    22) _upgrade_pa;;
  esac
}

_upgrade_backend_svc(){
  local svc="$1"
  info "Building $svc..."
  (cd "$ROOT/backend" && ./gradlew ":$svc:build" -x test --no-daemon)||die "Build failed"
  local df="$ROOT/backend/$svc/Dockerfile"
  if [[ -f "$df" ]] && dc ps 2>/dev/null|grep -q Up; then
    info "Rebuilding Docker image..."
    docker build -t "sudarshanchakra/$svc:latest" -f "$df" "$ROOT/backend/"||die "Docker build failed"
    info "Restarting container..."
    dc stop "$svc" 2>/dev/null||true; dc up -d "$svc"||die "Start failed"
    sleep 3
    local port; port=$(grep -oP "SERVER_PORT=\K\d+" "$CLOUD/docker-compose.vps.yml" 2>/dev/null|head -1)
    ok "$svc upgraded and running"
  else
    ok "$svc built. Restart manually if running locally."
  fi
}

_upgrade_dashboard(){
  info "Rebuilding dashboard..."
  if [[ -f "$ROOT/dashboard/Dockerfile" ]] && dc ps 2>/dev/null|grep -q Up; then
    docker build -t "sudarshanchakra/dashboard:latest" "$ROOT/dashboard/"||die "Build failed"
    dc stop react-dashboard 2>/dev/null||true; dc up -d react-dashboard; ok "Dashboard upgraded"
  else
    (cd "$ROOT/dashboard" && npm install && npm run build)||die "Build failed"
    ok "Dashboard rebuilt (restart dev server if running locally)"
  fi
}

_upgrade_simulator(){
  info "Rebuilding simulator..."
  if [[ -f "$ROOT/simulator/Dockerfile" ]] && dc ps 2>/dev/null|grep -q Up; then
    docker build -t "sudarshanchakra/simulator:latest" -f "$ROOT/simulator/Dockerfile" "$ROOT/simulator/"||die "Build failed"
    dc stop farm-simulator 2>/dev/null||true; dc up -d farm-simulator; ok "Simulator upgraded"
  else
    (cd "$ROOT/simulator" && npm install && npm run build)||die "Build failed"
    ok "Simulator rebuilt"
  fi
}

_upgrade_edge(){
  local eh="${EDGE_HOST:-}"
  if [[ -n "$eh" ]]; then
    info "Syncing edge code to $eh via rsync..."
    rsync -avz --exclude='__pycache__' --exclude='.venv' --exclude='*.pyc' \
      "$ROOT/edge/" "$eh:~/SudarshanChakra/edge/"||die "rsync failed"
    info "Restarting edge on $eh..."
    ssh "$eh" "cd ~/SudarshanChakra && docker compose -f edge/docker-compose.yml restart edge-ai 2>/dev/null || (pkill -f edge_gui.py; sleep 2; cd edge && nohup python3 edge_gui.py > /tmp/edge.log 2>&1 &)"||true
    ok "Edge upgraded on $eh"
  else
    info "Restarting local edge..."
    local pid; pid=$(grep edge "$LOG_DIR/service.pids" 2>/dev/null|awk '{print $1}'|head -1)
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null||true; sleep 2; mkdir -p "$LOG_DIR"
    (cd "$ROOT/edge" && python3 edge_gui.py)</dev/null>>"$LOG_DIR/edge.log" 2>&1 &
    echo "$! edge 5000">>"$LOG_DIR/service.pids"
    ok "Edge restarted locally (PID $!). Set EDGE_HOST=user@ip for remote."
  fi
}

_upgrade_android(){
  info "Building Android debug APK..."
  (cd "$ROOT/android" && ./gradlew assembleDebug --no-daemon)||die "Build failed"
  local apk="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
  ok "APK: $apk ($(du -h "$apk"|awk '{print $1}'))"
  if command -v adb>/dev/null 2>&1 && adb devices 2>/dev/null|grep -q "device$"; then
    confirm "Push to connected device via adb?" && { adb install -r "$apk" && ok "Installed on device" || warn "adb install failed"; }
  else
    warn "No device connected. Connect via USB and run: adb install -r $apk"
  fi
}

_flash_water(){
  printf "\n  ${C}Water Sensor (ESP8266)${X}\n\n"
  echo "  Firmware is in a separate repo:"
  echo ""
  echo "  ${W}git clone https://github.com/mandnArgiTech/AutoWaterLevelControl.git${X}"
  echo "  ${W}cd AutoWaterLevelControl${X}"
  echo "  ${W}git checkout feature/fluid-level-monitor-esp8266${X}"
  echo ""
  printf "  Port [/dev/ttyUSB0]: "; local port; read -r port; port="${port:-/dev/ttyUSB0}"
  echo ""
  echo "  Flash command:"
  echo "  ${W}pio run -t upload --upload-port $port${X}"
  echo ""
  echo "  After flash, configure via: http://<ESP8266_IP>/api/config"
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

_upgrade_nginx(){
  info "Reloading Nginx..."
  if dc ps 2>/dev/null|grep -q nginx; then
    dc exec nginx-proxy nginx -s reload 2>/dev/null && ok "Nginx reloaded"|| warn "Reload failed — try: dc restart nginx-proxy"
  elif command -v nginx>/dev/null 2>&1; then
    sudo nginx -s reload && ok "Nginx reloaded" || warn "Failed"
  else
    warn "Nginx not found running. Deploy Docker stack first."
  fi
}

_upgrade_vpn(){
  menu_select "VPN action:" \
    "Start OpenVPN server" \
    "Stop OpenVPN server" \
    "Restart OpenVPN server" \
    "Check tunnel status" \
    "Start OpenVPN client (edge node)" \
  || return 0

  case $MENU_RESULT in
    0)
      if [[ -f "$CLOUD/vpn/server.conf" ]]; then
        sudo openvpn --config "$CLOUD/vpn/server.conf" --daemon && ok "VPN server started"
      else die "server.conf not found at $CLOUD/vpn/"; fi;;
    1) sudo killall openvpn 2>/dev/null && ok "VPN stopped" || warn "No VPN process found";;
    2) sudo killall openvpn 2>/dev/null; sleep 2
       sudo openvpn --config "$CLOUD/vpn/server.conf" --daemon && ok "VPN restarted";;
    3)
      if ip addr show tun0 >/dev/null 2>&1; then
        local ip; ip=$(ip -4 addr show tun0 2>/dev/null|grep -oP 'inet \K[\d.]+')
        ok "VPN tunnel UP — $ip"
        info "Testing edge nodes..."
        for node_ip in 10.8.0.10 10.8.0.11; do
          if ping -c 1 -W 2 "$node_ip" >/dev/null 2>&1; then
            printf "  ${G}●${X} $node_ip reachable\n"
          else printf "  ${R}○${X} $node_ip unreachable\n"; fi
        done
      else warn "tun0 not found — VPN is down"; fi;;
    4)
      local configs=()
      for f in "$CLOUD"/vpn/client/*.conf; do [[ -f "$f" ]] && configs+=("$(basename "$f")"); done
      [[ ${#configs[@]} -eq 0 ]] && { warn "No client configs in $CLOUD/vpn/client/"; return; }
      menu_select "Which client config?" "${configs[@]}" || return 0
      local conf="$CLOUD/vpn/client/${configs[$MENU_RESULT]}"
      sudo openvpn --config "$conf" --daemon && ok "VPN client started with ${configs[$MENU_RESULT]}";;
  esac
}

_upgrade_pa(){
  local pa_host="${PA_HOST:-}"
  if [[ -z "$pa_host" ]]; then
    printf "\n  PA_HOST not set. Enter Raspberry Pi address (e.g. pi@192.168.1.200): "
    read -r pa_host
    [[ -z "$pa_host" ]] && { warn "Skipped"; return; }
  fi
  info "Syncing AlertManagement to $pa_host..."
  rsync -avz "$ROOT/AlertManagement/" "$pa_host:~/AlertManagement/"||die "rsync failed"
  ssh "$pa_host" "cd ~/AlertManagement && pip3 install -r requirements.txt 2>/dev/null; sudo systemctl restart pa-controller 2>/dev/null"||true
  ok "PA system updated on $pa_host"
}

# ═══ STOP ═══
do_stop(){
  menu_select "Stop what?" \
    "Everything (Docker + local + VPN)" \
    "Docker stack only" \
    "Local services only" \
    "VPN tunnel only" \
    "──── Individual service ──────────────────────────────" \
    "auth-service" "alert-service" "device-service" "siren-service" "mdm-service" "api-gateway" \
    "dashboard" "simulator" "edge-ai" \
  || return 0

  case $MENU_RESULT in
    0)
      dc down 2>/dev/null||true; sudo killall openvpn 2>/dev/null||true
      [[ -f "$LOG_DIR/service.pids" ]]&&{ while IFS=' ' read -r p s _; do kill "$p" 2>/dev/null&&info "Stopped $s"||true; done<"$LOG_DIR/service.pids"; >"$LOG_DIR/service.pids"; }
      ok "Everything stopped";;
    1) dc down 2>/dev/null && ok "Docker stopped";;
    2) [[ -f "$LOG_DIR/service.pids" ]]&&{ while IFS=' ' read -r p s _; do kill "$p" 2>/dev/null&&info "Stopped $s"||true; done<"$LOG_DIR/service.pids"; >"$LOG_DIR/service.pids"; ok "Local stopped"; };;
    3) sudo killall openvpn 2>/dev/null && ok "VPN stopped" || warn "No VPN running";;
    4) return 0;;
    5|6|7|8|9|10|11|12|13)
      local svcs=("" "" "" "" "" "auth-service" "alert-service" "device-service" "siren-service" "mdm-service" "api-gateway" "react-dashboard" "farm-simulator" "edge-ai")
      local svc="${svcs[$MENU_RESULT]}"
      if dc ps 2>/dev/null|grep -q "$svc"; then dc stop "$svc" && ok "$svc stopped"
      else
        local pid; pid=$(grep "$svc" "$LOG_DIR/service.pids" 2>/dev/null|awk '{print $1}'|head -1)
        [[ -n "$pid" ]] && kill "$pid" 2>/dev/null && ok "$svc stopped (PID $pid)" || warn "$svc not found running"
      fi;;
  esac
}

# ═══ LOGS ═══
do_logs(){
  local t=()
  if dc ps --format '{{.Names}}' 2>/dev/null|grep -q .; then
    while IFS= read -r n; do t+=("Docker: $n"); done< <(dc ps --format '{{.Names}}' 2>/dev/null)
  fi
  [[ -d "$LOG_DIR" ]] && for f in "$LOG_DIR"/*.log; do [[ -f "$f" ]] && t+=("Local:  $(basename "$f" .log)"); done
  [[ ${#t[@]} -eq 0 ]] && { warn "No logs found. Deploy first."; pause; return; }
  menu_select "View logs for:" "${t[@]}" || return 0
  local c="${t[$MENU_RESULT]}"
  if [[ "$c" == Docker:* ]]; then dc logs -f --tail=100 "${c#Docker: }"
  else tail -f -n 100 "$LOG_DIR/${c#Local:  }.log"; fi
}

# ═══ DATABASE ═══
do_db_menu(){
  menu_select "Database:" "Run migrations" "Backup" "Restore from backup" "Open psql shell" "Reset (DANGER — drops everything)" || return 0
  case $MENU_RESULT in 0) do_db_migrate;; 1) do_db_backup;; 2) do_db_restore;; 3) do_db_shell;; 4) do_db_reset;; esac
}
_dbx(){ if docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then docker exec -i postgres "$@"; else "$@"; fi; }
do_db_migrate(){
  local d="$CLOUD/db/flyway"; [[ -d "$d" ]]||{ warn "No migrations at $d"; return; }
  info "Migration files:"; ls -1 "$d"/V*.sql 2>/dev/null|while read -r f; do echo "  $(basename "$f")"; done
  confirm "Apply all?"||return 0
  for f in "$d"/V*.sql; do [[ -f "$f" ]]||continue; info "$(basename "$f")..."; _dbx psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}" < "$f" 2>&1 && ok "Applied"||warn "May already exist"; done
}
do_db_backup(){
  mkdir -p "$BACKUP_DIR"; local ts; ts=$(date +%Y%m%d_%H%M%S); local f="$BACKUP_DIR/sc_${ts}.sql.gz"
  info "Backing up..."
  if docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then docker exec postgres pg_dump -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"|gzip>"$f"
  else pg_dump -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"|gzip>"$f"; fi
  ok "$f ($(du -h "$f"|awk '{print $1}'))"; ls -t "$BACKUP_DIR"/sc_*.sql.gz 2>/dev/null|tail -n +11|xargs rm -f 2>/dev/null||true
}
do_db_restore(){
  local bs=(); while IFS= read -r f; do bs+=("$(basename "$f")  $(du -h "$f"|awk '{print $1}')"); done< <(ls -t "$BACKUP_DIR"/sc_*.sql.gz 2>/dev/null)
  [[ ${#bs[@]} -eq 0 ]] && { warn "No backups in $BACKUP_DIR"; return; }
  menu_select "Restore from:" "${bs[@]}"||return 0; local fn="${bs[$MENU_RESULT]%% *}"; local fp="$BACKUP_DIR/$fn"
  printf "\n  ${R}${W}This will DROP and recreate the database!${X}\n"; confirm "Sure?"||return 0
  info "Restoring $fn..."
  if docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then
    docker exec postgres psql -U "${DB_USER:-scadmin}" -c "DROP DATABASE IF EXISTS ${DB_NAME:-sudarshanchakra};"
    docker exec postgres psql -U "${DB_USER:-scadmin}" -c "CREATE DATABASE ${DB_NAME:-sudarshanchakra};"
    gunzip -c "$fp"|docker exec -i postgres psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"
  else psql -U "${DB_USER:-scadmin}" -c "DROP DATABASE IF EXISTS ${DB_NAME:-sudarshanchakra};" ; psql -U "${DB_USER:-scadmin}" -c "CREATE DATABASE ${DB_NAME:-sudarshanchakra};" ; gunzip -c "$fp"|psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"; fi
  ok "Restored"
}
do_db_shell(){ info "psql (Ctrl+D to exit)..."; if docker ps --format '{{.Names}}' 2>/dev/null|grep -qx postgres; then docker exec -it postgres psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"; else psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}"; fi; }
do_db_reset(){ printf "\n  ${R}${W}DANGER: Destroys ALL data!${X}\n"; confirm "Full database reset?"||return 0; _dbx psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"; _dbx psql -U "${DB_USER:-scadmin}" -d "${DB_NAME:-sudarshanchakra}" < "$CLOUD/db/init.sql"; do_db_migrate; ok "Database reset complete"; }

# ═══ MAIN MENU LOOP ═══
while true; do
  menu_select "What do you want to do?" \
    "Status          check all 19 modules" \
    "Build           compile components" \
    "Test            run test suites" \
    "Deploy          start services (local or Docker)" \
    "Upgrade         rebuild + redeploy one thing" \
    "Stop            stop services" \
    "Logs            view service logs" \
    "Database        migrate / backup / restore / shell" \
    "Setup           first-time dependency install" \
    "Exit" \
  || break
  tput clear 2>/dev/null||printf "\033[2J\033[H"
  case $MENU_RESULT in
    0) show_status;pause;; 1) do_build;pause;; 2) do_test;pause;; 3) do_deploy;pause;;
    4) do_upgrade;pause;; 5) do_stop;pause;; 6) do_logs;; 7) do_db_menu;pause;;
    8) bash "$ROOT/setup_and_build_all.sh" install-deps;pause;; 9) break;;
  esac
done
tput clear 2>/dev/null||true; printf "\n  ${D}Goodbye.${X}\n\n"
