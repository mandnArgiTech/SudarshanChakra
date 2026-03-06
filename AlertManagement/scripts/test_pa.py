#!/usr/bin/env python3
"""
PA System — Test Client (run on edge AI server or any machine)
===============================================================
Sends MQTT commands to the Pi PA controller for testing.

Usage:
    python3 test_pa.py bgm                  # Play Suprabatham
    python3 test_pa.py siren                # Trigger security siren
    python3 test_pa.py stop                 # Stop all playback
    python3 test_pa.py interrupt            # Play BGM then siren after 5s (interrupt test)
    python3 test_pa.py status               # Request status
"""

import json
import sys
import time
import paho.mqtt.client as mqtt

# --- Configuration (match your setup) ---
BROKER = "192.168.1.100"        # Your edge AI server IP
PORT   = 1883
TOPIC  = "pa/command"
STATUS_TOPIC = "pa/status"

# URLs served by Nginx on the edge AI server
BGM_URL   = "http://192.168.1.100/audio/suprabatham.mp3"
SIREN_URL = "http://192.168.1.100/audio/siren.mp3"


def send_command(cmd: dict):
    """Connect, send one command, disconnect."""
    client = mqtt.Client(client_id="pa-test-client")
    client.connect(BROKER, PORT)
    payload = json.dumps(cmd)
    info = client.publish(TOPIC, payload, qos=1)
    info.wait_for_publish()
    print(f"Sent: {payload}")
    client.disconnect()


def monitor_status(duration=30):
    """Listen to status topic for N seconds."""
    def on_message(client, userdata, msg):
        data = json.loads(msg.payload.decode())
        print(f"[STATUS] {json.dumps(data, indent=2)}")

    client = mqtt.Client(client_id="pa-status-monitor")
    client.on_message = on_message
    client.connect(BROKER, PORT)
    client.subscribe(STATUS_TOPIC)
    print(f"Monitoring {STATUS_TOPIC} for {duration}s...")
    client.loop_start()
    time.sleep(duration)
    client.loop_stop()
    client.disconnect()


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    action = sys.argv[1].lower()

    if action == "bgm":
        send_command({"command": "play_bgm", "url": BGM_URL})

    elif action == "siren":
        send_command({"command": "play_siren", "url": SIREN_URL})

    elif action == "stop":
        send_command({"command": "stop"})

    elif action == "status":
        send_command({"command": "status"})
        monitor_status(10)

    elif action == "interrupt":
        print("=== INTERRUPT TEST ===")
        print("Step 1: Starting BGM...")
        send_command({"command": "play_bgm", "url": BGM_URL})
        print("Step 2: Waiting 5 seconds...")
        time.sleep(5)
        print("Step 3: Triggering SIREN (should instantly kill BGM)...")
        send_command({"command": "play_siren", "url": SIREN_URL})
        print("Step 4: Monitoring status...")
        monitor_status(20)

    else:
        print(f"Unknown action: {action}")
        print(__doc__)
        sys.exit(1)


if __name__ == "__main__":
    main()
