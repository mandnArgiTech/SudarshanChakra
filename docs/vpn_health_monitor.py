#!/usr/bin/env python3
"""
vpn_health_monitor.py — VPN Tunnel Health Check
=================================================
Runs on VPS as a cron/systemd timer. Pings Edge Node VPN IPs
and publishes online/offline events to RabbitMQ.

Usage: Runs inside docker-compose.cloud.yml vpn-health-monitor service.
"""

import subprocess
import json
import time
import os

try:
    import pika
except ImportError:
    print("ERROR: pika not installed. Run: pip install pika")
    exit(1)

NODES = {
    "edge-node-a": os.getenv("NODE_A_VPN_IP", "10.8.0.10"),
    "edge-node-b": os.getenv("NODE_B_VPN_IP", "10.8.0.11"),
}

FAIL_THRESHOLD = 3
RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "admin")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "changeme")

# Persistent state file
STATE_FILE = "/tmp/vpn_health_state.json"


def load_state() -> dict:
    try:
        with open(STATE_FILE) as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {n: {"fail_count": 0, "was_offline": False} for n in NODES}


def save_state(state: dict):
    with open(STATE_FILE, "w") as f:
        json.dump(state, f)


def ping(ip: str) -> bool:
    result = subprocess.run(
        ["ping", "-c", "1", "-W", "2", ip],
        capture_output=True,
    )
    return result.returncode == 0


def publish_event(node_id: str, event_type: str):
    try:
        credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
        connection = pika.BlockingConnection(
            pika.ConnectionParameters(
                host=RABBITMQ_HOST,
                credentials=credentials,
                connection_attempts=3,
                retry_delay=2,
            )
        )
        channel = connection.channel()
        channel.exchange_declare(
            exchange="farm.events",
            exchange_type="topic",
            durable=True,
        )

        payload = json.dumps({
            "node_id": node_id,
            "event": event_type,
            "vpn_ip": NODES[node_id],
            "timestamp": time.time(),
        })

        channel.basic_publish(
            exchange="farm.events",
            routing_key=f"node.{event_type}",
            body=payload,
            properties=pika.BasicProperties(
                delivery_mode=2,  # Persistent
                content_type="application/json",
            ),
        )

        print(f"[EVENT] {node_id}: {event_type}")
        connection.close()

    except Exception as e:
        print(f"[ERROR] Failed to publish event: {e}")


def main():
    state = load_state()

    for node_id, ip in NODES.items():
        if node_id not in state:
            state[node_id] = {"fail_count": 0, "was_offline": False}

        reachable = ping(ip)

        if reachable:
            if state[node_id]["was_offline"]:
                publish_event(node_id, "online")
                print(f"[OK] {node_id} ({ip}): Back ONLINE")
            else:
                print(f"[OK] {node_id} ({ip}): Reachable")

            state[node_id]["fail_count"] = 0
            state[node_id]["was_offline"] = False
        else:
            state[node_id]["fail_count"] += 1
            print(f"[WARN] {node_id} ({ip}): Unreachable "
                  f"(fail {state[node_id]['fail_count']}/{FAIL_THRESHOLD})")

            if state[node_id]["fail_count"] >= FAIL_THRESHOLD:
                if not state[node_id]["was_offline"]:
                    publish_event(node_id, "offline")
                    state[node_id]["was_offline"] = True

    save_state(state)


if __name__ == "__main__":
    main()
