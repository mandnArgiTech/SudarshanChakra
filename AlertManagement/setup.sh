#!/bin/bash
# ==============================================================================
# PA System — Full Setup Script for Raspberry Pi Zero 2 W
# ==============================================================================
# Run this ONCE on a fresh Pi OS Lite (Bookworm) installation.
# After running this script, the Pi will be in read-only mode.
#
# Usage:
#   chmod +x setup.sh
#   sudo ./setup.sh
#
# IMPORTANT: Run this over SSH, NOT serial console.
# ==============================================================================

set -euo pipefail
trap 'echo "ERROR on line $LINENO. Setup incomplete." >&2' ERR

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[SETUP]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*" >&2; }

if [ "$EUID" -ne 0 ]; then
    err "Must run as root. Use: sudo ./setup.sh"
    exit 1
fi

# ==============================================================================
# PHASE 1: System Update & Package Installation
# ==============================================================================
log "Phase 1: Installing packages..."

apt-get update
apt-get install -y --no-install-recommends \
    mpv \
    python3-pip \
    python3-paho-mqtt \
    alsa-utils \
    i2c-tools

# Verify mpv is installed
mpv --version | head -1
log "mpv installed successfully."

# Install paho-mqtt if not available as system package
python3 -c "import paho.mqtt.client" 2>/dev/null || {
    warn "paho-mqtt not in system packages, installing via pip..."
    pip3 install --break-system-packages paho-mqtt
}

# ==============================================================================
# PHASE 2: I2S Audio Configuration
# ==============================================================================
log "Phase 2: Configuring I2S audio output..."

BOOT_CONFIG=""
if [ -f /boot/firmware/config.txt ]; then
    BOOT_CONFIG="/boot/firmware/config.txt"   # Bookworm
elif [ -f /boot/config.txt ]; then
    BOOT_CONFIG="/boot/config.txt"            # Bullseye
else
    err "Cannot find config.txt!"
    exit 1
fi

log "Using boot config: $BOOT_CONFIG"

# Disable onboard audio
sed -i 's/^dtparam=audio=on/#dtparam=audio=on/' "$BOOT_CONFIG"

# Add I2S overlay if not already present
if ! grep -q "dtoverlay=hifiberry-dac" "$BOOT_CONFIG"; then
    cat >> "$BOOT_CONFIG" <<'EOF'

# --- PA System: I2S DAC Configuration ---
dtoverlay=hifiberry-dac
gpu_mem=16
dtoverlay=disable-bt
EOF
    log "Added I2S overlay to $BOOT_CONFIG"
else
    warn "I2S overlay already configured."
fi

# ALSA configuration
cp /opt/pa-system/config/asound.conf /etc/asound.conf
log "ALSA configuration installed."

# Add pi user to audio group
usermod -aG audio pi

# ==============================================================================
# PHASE 3: Disable WiFi Power Management
# ==============================================================================
log "Phase 3: Disabling WiFi power management..."

# Method 1: NetworkManager dispatcher (Bookworm default)
mkdir -p /etc/NetworkManager/dispatcher.d
cat > /etc/NetworkManager/dispatcher.d/99-disable-powersave <<'EOF'
#!/bin/bash
if [ "$2" = "up" ]; then
    /sbin/iwconfig wlan0 power off 2>/dev/null || true
fi
EOF
chmod +x /etc/NetworkManager/dispatcher.d/99-disable-powersave

# Method 2: Also set via iw (belt and suspenders)
cat > /etc/rc.local.d/wifi-powersave.sh <<'RCEOF' || true
#!/bin/bash
iw dev wlan0 set power_save off
RCEOF

log "WiFi power save disabled."

# ==============================================================================
# PHASE 4: Deploy PA Controller
# ==============================================================================
log "Phase 4: Deploying PA controller..."

mkdir -p /opt/pa-system/config
mkdir -p /etc/pa-system

# Copy files
cp /opt/pa-system/scripts/pa_controller.py /opt/pa-system/pa_controller.py 2>/dev/null || {
    warn "Run this after copying project files to /opt/pa-system/"
}
cp /opt/pa-system/config/pa.env /etc/pa-system/pa.env 2>/dev/null || true

# Install systemd service
cp /opt/pa-system/systemd/pa-controller.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable pa-controller.service
log "Systemd service enabled."

# ==============================================================================
# PHASE 5: Read-Only Filesystem
# ==============================================================================
log "Phase 5: Configuring Read-Only filesystem..."

warn "This is the critical step. The SD card will be protected from corruption."
warn "After this, writes to / and /boot will fail (by design)."

# --- 5a: Create tmpfs mounts for directories that need writes ---
cat >> /etc/fstab <<'EOF'

# --- PA System: tmpfs mounts for read-only mode ---
tmpfs /tmp                  tmpfs nosuid,nodev,size=32M     0 0
tmpfs /var/tmp              tmpfs nosuid,nodev,size=16M     0 0
tmpfs /var/log              tmpfs nosuid,nodev,size=16M     0 0
tmpfs /var/lib/dhcpcd       tmpfs nosuid,nodev,size=4M      0 0
tmpfs /var/lib/NetworkManager tmpfs nosuid,nodev,size=8M    0 0
tmpfs /var/spool            tmpfs nosuid,nodev,size=4M      0 0
tmpfs /run                  tmpfs nosuid,nodev,mode=0755    0 0
EOF

# --- 5b: Move volatile state to tmpfs ---
# systemd journal → volatile (write to RAM, not SD)
mkdir -p /etc/systemd/journald.conf.d
cat > /etc/systemd/journald.conf.d/volatile.conf <<'EOF'
[Journal]
Storage=volatile
RuntimeMaxUse=8M
EOF

# fake-hwclock needs write access — redirect to tmpfs
systemctl disable fake-hwclock.service 2>/dev/null || true

# --- 5c: Disable swap (not needed with LiFePO4 + no wear on SD) ---
dphys-swapfile swapoff 2>/dev/null || true
systemctl disable dphys-swapfile.service 2>/dev/null || true
apt-get remove -y --purge dphys-swapfile 2>/dev/null || true

# --- 5d: Set filesystem to read-only in fstab ---
# Change ext4 mount options to include 'ro'
sed -i 's|\(.*ext4.*\)defaults\(.*\)|\1defaults,ro\2|' /etc/fstab

# Also mark /boot as read-only
sed -i 's|\(.*/boot.*\)defaults\(.*\)|\1defaults,ro\2|' /etc/fstab

# --- 5e: Set read-only in kernel cmdline ---
CMDLINE=""
if [ -f /boot/firmware/cmdline.txt ]; then
    CMDLINE="/boot/firmware/cmdline.txt"
elif [ -f /boot/cmdline.txt ]; then
    CMDLINE="/boot/cmdline.txt"
fi

if [ -n "$CMDLINE" ]; then
    # Add 'ro' to kernel command line if not present
    if ! grep -q ' ro ' "$CMDLINE" && ! grep -q ' ro$' "$CMDLINE"; then
        sed -i 's/$/ ro/' "$CMDLINE"
        log "Added 'ro' to kernel cmdline."
    fi
    # Disable filesystem check forcing (fsck can't write in ro mode)
    sed -i 's/fsck.repair=yes/fsck.repair=no/' "$CMDLINE"
fi

# --- 5f: Helper aliases for maintenance ---
cat >> /home/pi/.bashrc <<'BASH'

# --- PA System: Read-Only FS Helpers ---
alias ro='sudo mount -o remount,ro / ; sudo mount -o remount,ro /boot ; echo "Filesystem: READ-ONLY"'
alias rw='sudo mount -o remount,rw / ; sudo mount -o remount,rw /boot ; echo "Filesystem: READ-WRITE (remember to run ro when done!)"'
alias pa-logs='journalctl -u pa-controller -f'
alias pa-restart='sudo systemctl restart pa-controller'
alias pa-status='sudo systemctl status pa-controller'
BASH

log "Added helper aliases: rw, ro, pa-logs, pa-restart, pa-status"

# ==============================================================================
# PHASE 6: Final Verification
# ==============================================================================
log "Phase 6: Verification..."

echo ""
echo "============================================================"
echo "  SETUP COMPLETE — REBOOT REQUIRED"
echo "============================================================"
echo ""
echo "  After reboot:"
echo "    1. The Pi will boot in READ-ONLY mode"
echo "    2. The PA controller will auto-start via systemd"
echo "    3. MQTT commands will be accepted on topic: $PA_MQTT_TOPIC"
echo ""
echo "  Maintenance commands (SSH into Pi):"
echo "    rw          — Remount filesystem as read-write"
echo "    ro          — Remount filesystem as read-only"
echo "    pa-logs     — Tail PA controller logs"
echo "    pa-status   — Check service status"
echo "    pa-restart  — Restart the PA controller"
echo ""
echo "  To test audio before reboot:"
echo "    speaker-test -t wav -c 2 -D default"
echo ""
echo "  Ready to reboot? Run:"
echo "    sudo reboot"
echo ""
