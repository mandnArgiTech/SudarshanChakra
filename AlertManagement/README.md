# PA System — Complete Implementation Guide

## Raspberry Pi Zero 2 W | PCM5102A I2S DAC | Ahuja SSA-250DP 250W PA

---

## System Architecture

```
┌─────────────────────────────────────┐
│  Edge AI Server (RTX 3050)          │
│  ┌───────────┐  ┌────────────────┐  │
│  │ Camera AI │  │ Nginx (MP3s)   │  │
│  │ Intrusion │  │ /audio/*.mp3   │  │
│  │ Detection │  └────────┬───────┘  │
│  └─────┬─────┘           │          │
│        │           ┌─────┴──────┐   │
│        └──────────▶│ Mosquitto  │   │
│     MQTT publish   │ MQTT Broker│   │
│                    └─────┬──────┘   │
└──────────────────────────┼──────────┘
                           │ WiFi (MQTT QoS 1)
┌──────────────────────────┼──────────┐
│  Raspberry Pi Zero 2 W   │          │
│  ┌───────────────────────┴───────┐  │
│  │  pa_controller.py             │  │
│  │  State Machine:               │  │
│  │    IDLE ↔ PLAYING_BGM         │  │
│  │    ANY  → PLAYING_SIREN       │  │
│  └───────────────┬───────────────┘  │
│                  │ I2S (GPIO)       │
│  ┌───────────────┴───────────────┐  │
│  │  PCM5102A DAC                 │  │
│  └───────────────┬───────────────┘  │
└──────────────────┼──────────────────┘
                   │ 3.5mm / RCA Line Out
┌──────────────────┼──────────────────┐
│  Ahuja SSA-250DP │ 250W Amplifier   │
│  (24V DC from LiFePO4 battery)      │
│                  │ 100V Line        │
│  ┌───────┐ ┌───────┐ ┌───────┐     │
│  │SUH-25 │ │SUH-25 │ │SUH-25 │     │
│  │Horn 1 │ │Horn 2 │ │Horn N │     │
│  └───────┘ └───────┘ └───────┘     │
└─────────────────────────────────────┘
```

---

## 1. I2S Audio & OS Configuration

### 1.1 PCM5102A Wiring (Pi GPIO Header → DAC Board)

| PCM5102A Pin | Connect To         | Pi Header Pin |
|:-------------|:-------------------|:--------------|
| BCK          | GPIO 18            | Pin 12        |
| LRCK (WS)   | GPIO 19            | Pin 35        |
| DIN          | GPIO 21            | Pin 40        |
| VIN          | 3.3V               | Pin 1         |
| GND          | GND                | Pin 6         |
| FMT          | GND (I2S format)   | Pin 9         |
| XSMT         | 3.3V (un-mute)    | Pin 17        |
| SCK          | GND (internal clk) | Pin 14        |

### 1.2 Boot Configuration (`/boot/firmware/config.txt`)

Open the file and make these changes:

```bash
sudo nano /boot/firmware/config.txt
```

**Comment out** the default audio line:
```
#dtparam=audio=on
```

**Add** at the end of the file:
```
dtoverlay=hifiberry-dac
gpu_mem=16
dtoverlay=disable-bt
```

### 1.3 ALSA Configuration

Create `/etc/asound.conf`:

```
pcm.!default {
    type softvol
    slave.pcm "i2s_dac"
    control {
        name "SoftMaster"
        card 0
    }
    min_dB -51.0
    max_dB 0.0
}

pcm.i2s_dac {
    type hw
    card 0
    device 0
    format S32_LE
    rate 44100
    channels 2
}

ctl.!default {
    type hw
    card 0
}
```

### 1.4 Verify Audio After Reboot

```bash
sudo reboot
# After reboot:
aplay -l                          # Should show "snd_rpi_hifiberry_dac"
speaker-test -t wav -c 2 -D default  # Should hear audio through DAC
```

---

## 2. Read-Only Filesystem Configuration

This is critical for preventing SD card corruption when the LiFePO4 battery runs out or power is cut unexpectedly.

### 2.1 Step-by-Step Manual Commands

Run these in order. The `setup.sh` script automates all of this, but here are the individual commands for understanding:

```bash
# --- Disable swap ---
sudo dphys-swapfile swapoff
sudo systemctl disable dphys-swapfile
sudo apt-get remove -y --purge dphys-swapfile

# --- Journal to volatile (RAM) ---
sudo mkdir -p /etc/systemd/journald.conf.d
sudo tee /etc/systemd/journald.conf.d/volatile.conf <<EOF
[Journal]
Storage=volatile
RuntimeMaxUse=8M
EOF

# --- Add tmpfs mounts to /etc/fstab ---
sudo tee -a /etc/fstab <<EOF
tmpfs /tmp                    tmpfs nosuid,nodev,size=32M   0 0
tmpfs /var/tmp                tmpfs nosuid,nodev,size=16M   0 0
tmpfs /var/log                tmpfs nosuid,nodev,size=16M   0 0
tmpfs /var/lib/dhcpcd         tmpfs nosuid,nodev,size=4M    0 0
tmpfs /var/lib/NetworkManager tmpfs nosuid,nodev,size=8M    0 0
tmpfs /var/spool              tmpfs nosuid,nodev,size=4M    0 0
EOF

# --- Set rootfs to read-only ---
# Edit /etc/fstab: change 'defaults' to 'defaults,ro' for / and /boot
sudo sed -i 's|\(.*ext4.*\)defaults\(.*\)|\1defaults,ro\2|' /etc/fstab
sudo sed -i 's|\(.*/boot.*\)defaults\(.*\)|\1defaults,ro\2|' /etc/fstab

# --- Add 'ro' to kernel cmdline ---
sudo sed -i 's/$/ ro/' /boot/firmware/cmdline.txt
sudo sed -i 's/fsck.repair=yes/fsck.repair=no/' /boot/firmware/cmdline.txt

# --- Disable fake-hwclock (needs write access) ---
sudo systemctl disable fake-hwclock.service

# --- Add convenience aliases to ~/.bashrc ---
cat >> ~/.bashrc <<'BASH'
alias ro='sudo mount -o remount,ro / ; sudo mount -o remount,ro /boot ; echo "READ-ONLY"'
alias rw='sudo mount -o remount,rw / ; sudo mount -o remount,rw /boot ; echo "READ-WRITE"'
alias pa-logs='journalctl -u pa-controller -f'
alias pa-restart='sudo systemctl restart pa-controller'
BASH
```

### 2.2 Maintenance Workflow

When you need to make changes to the Pi:

```bash
ssh pi@<pi-ip>
rw                    # Remount as read-write
# ... make your changes ...
ro                    # ALWAYS remount as read-only when done
```

---

## 3. Python Architecture — State Machine

### 3.1 State Diagram

```
                    ┌──────────────────────────┐
                    │                          │
        ┌───────── │          IDLE             │ ◀─────────┐
        │          │                          │           │
        │          └──────────────────────────┘           │
        │                     │                            │
        │              play_bgm                            │
        │                     │                          stop
        │                     ▼                            │
        │          ┌──────────────────────────┐           │
        │          │                          │           │
   play_siren      │      PLAYING_BGM         │ ──────────┘
        │          │                          │   (finish/stop)
        │          └──────────────────────────┘
        │                     │
        │              play_siren (INTERRUPT!)
        │                     │
        │                     ▼
        │          ┌──────────────────────────┐
        └────────▶ │                          │
                   │     PLAYING_SIREN        │ ──▶ IDLE (or resume BGM)
                   │   [HIGHEST PRIORITY]     │
                   └──────────────────────────┘
```

### 3.2 Priority Interrupt Sequence

When a siren arrives during Suprabatham playback:

1. MQTT message arrives: `{"command": "play_siren", "url": "..."}`
2. Controller saves the current BGM URL
3. `os.killpg(SIGKILL)` instantly terminates the mpv process group
4. New mpv instance spawns with siren at 100% volume, looped N times
5. Monitor thread waits for siren playback to complete
6. If `RESUME_AFTER_SIREN=true`, BGM restarts from the beginning
7. Otherwise, system returns to IDLE

The kill uses `SIGKILL` (not `SIGTERM`) for minimum latency — this is a security siren, not a polite request.

### 3.3 Key Design Decisions

**Why mpv over python-vlc?**
The Pi Zero 2 W has only 512 MB RAM. mpv's subprocess model uses ~12 MB RSS vs python-vlc's ~38 MB. mpv also handles HTTP streaming natively with configurable cache, and killing a subprocess is cleaner than managing VLC's internal state.

**Why subprocess over python bindings?**
Process isolation. If mpv hangs or crashes, `SIGKILL` to the process group guarantees cleanup. No leaked file descriptors, no zombie threads, no shared memory corruption.

**Why `os.killpg` instead of `process.kill()`?**
mpv may spawn child processes (demuxers, decoders). `killpg` kills the entire process group, preventing orphaned children.

---

## 4. Deployment Steps

### 4.1 Copy Files to Pi

From your development machine:

```bash
# Copy the project to Pi
scp -r pa-system/ pi@<pi-ip>:/home/pi/pa-system/

# SSH into Pi
ssh pi@<pi-ip>

# Move to system location
sudo mkdir -p /opt/pa-system/{config,scripts,systemd}
sudo cp /home/pi/pa-system/scripts/pa_controller.py /opt/pa-system/
sudo cp /home/pi/pa-system/config/pa.env /etc/pa-system/pa.env
sudo cp /home/pi/pa-system/config/asound.conf /etc/asound.conf
sudo cp /home/pi/pa-system/systemd/pa-controller.service /etc/systemd/system/
```

### 4.2 Run the Setup Script (or do it manually per Section 1-2)

```bash
sudo chmod +x /home/pi/pa-system/setup.sh
sudo /home/pi/pa-system/setup.sh
sudo reboot
```

### 4.3 Verify After Reboot

```bash
# Check filesystem is read-only
mount | grep ' / '          # Should show 'ro'

# Check service is running
systemctl status pa-controller

# Check logs
journalctl -u pa-controller -f

# Check audio device
aplay -l
```

---

## 5. Edge AI Server Configuration

### 5.1 Nginx (Audio File Server)

```bash
# On the edge AI server:
sudo mkdir -p /var/www/pa-audio
sudo cp suprabatham.mp3 siren.mp3 /var/www/pa-audio/
sudo cp nginx-pa-audio.conf /etc/nginx/sites-available/pa-audio
sudo ln -s /etc/nginx/sites-available/pa-audio /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# Test:
curl -I http://localhost/audio/suprabatham.mp3
```

### 5.2 Mosquitto MQTT Broker

```bash
sudo apt install mosquitto mosquitto-clients

# Test publish from server:
mosquitto_pub -h localhost -t pa/command \
  -m '{"command":"play_bgm","url":"http://192.168.1.100/audio/suprabatham.mp3"}'
```

### 5.3 Integration with Camera AI

From your intrusion detection Python code:

```python
import json
import paho.mqtt.publish as publish

def trigger_siren():
    """Called when camera AI detects an intruder."""
    publish.single(
        topic="pa/command",
        payload=json.dumps({
            "command": "play_siren",
            "url": "http://192.168.1.100/audio/siren.mp3"
        }),
        hostname="192.168.1.100",
        port=1883,
        qos=1,
    )
```

### 5.4 Cron Job for Morning Chants

```bash
# On the edge AI server, add to crontab:
crontab -e

# Play Suprabatham every morning at 5:30 AM
30 5 * * * mosquitto_pub -h localhost -t pa/command -m '{"command":"play_bgm","url":"http://192.168.1.100/audio/suprabatham.mp3"}'
```

---

## 6. Testing

### 6.1 Test Script

Use the included `test_pa.py` from any machine on the network:

```bash
python3 test_pa.py bgm          # Start Suprabatham
python3 test_pa.py siren        # Trigger siren
python3 test_pa.py stop         # Stop everything
python3 test_pa.py interrupt    # BGM → 5s → Siren (interrupt test)
python3 test_pa.py status       # Request status report
```

### 6.2 Manual MQTT Test

```bash
# From any machine with mosquitto-clients:
# Play BGM
mosquitto_pub -h 192.168.1.100 -t pa/command \
  -m '{"command":"play_bgm","url":"http://192.168.1.100/audio/suprabatham.mp3"}'

# Trigger siren (interrupts BGM)
mosquitto_pub -h 192.168.1.100 -t pa/command \
  -m '{"command":"play_siren","url":"http://192.168.1.100/audio/siren.mp3"}'

# Stop
mosquitto_pub -h 192.168.1.100 -t pa/command -m '{"command":"stop"}'

# Monitor status
mosquitto_sub -h 192.168.1.100 -t pa/status -v
```

---

## 7. MQTT Command Reference

| Command | Payload | Priority | Behavior |
|:--------|:--------|:---------|:---------|
| `play_bgm` | `{"command":"play_bgm","url":"http://..."}` | Low | Plays once. Ignored if siren active. |
| `play_siren` | `{"command":"play_siren","url":"http://..."}` | High | Kills anything playing. Loops N times. |
| `stop` | `{"command":"stop"}` | — | Stops all playback, returns to IDLE. |
| `status` | `{"command":"status"}` | — | Publishes state to `pa/status`. |

---

## 8. Troubleshooting

**No audio output:**
1. `aplay -l` — confirm `snd_rpi_hifiberry_dac` shows up
2. Check PCM5102A wiring, especially XSMT (must be HIGH to un-mute)
3. `speaker-test -t wav -c 2` — tests raw ALSA path
4. Check `amixer` — ensure SoftMaster isn't at 0

**MQTT not connecting:**
1. `mosquitto_sub -h <broker-ip> -t '#'` from another machine
2. Check firewall: `sudo ufw status` on the server
3. Verify Pi WiFi: `iwconfig wlan0`, `ping <broker-ip>`

**Service won't start:**
1. `journalctl -u pa-controller -e` — check recent errors
2. `python3 /opt/pa-system/pa_controller.py` — run manually to see errors
3. Verify mpv: `which mpv`, `mpv --version`

**SD card went read-write after reboot:**
1. Check `/etc/fstab` for `ro` flag
2. Check `/boot/firmware/cmdline.txt` for `ro`
3. Run `mount | grep ' / '` to verify
