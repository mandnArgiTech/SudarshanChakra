"""MQTT subscribe/publish helpers for E2E (Suites 6–8, siren ack)."""
from __future__ import annotations

import json
import threading
import time
from typing import Any, Callable, Dict, List, Optional

try:
    import paho.mqtt.client as mqtt

    HAS_PAHO = True
except ImportError:
    HAS_PAHO = False
    mqtt = None  # type: ignore


def require_paho():
    if not HAS_PAHO:
        raise RuntimeError("paho-mqtt not installed (pip install paho-mqtt)")


def wait_for_message(
    broker: str,
    port: int,
    topic: str,
    timeout_sec: float = 30.0,
    match: Optional[Callable[[dict], bool]] = None,
) -> Optional[dict]:
    """
    Subscribe to ``topic``, return first JSON payload as dict within timeout.
    """
    require_paho()
    result: Dict[str, Any] = {}
    event = threading.Event()

    def on_message(_client, _userdata, msg):
        try:
            payload = json.loads(msg.payload.decode("utf-8"))
        except Exception:
            payload = {"_raw": msg.payload.decode("utf-8", errors="replace")}
        if match is None or match(payload):
            result["payload"] = payload
            event.set()

    client = _make_client()
    client.on_message = on_message
    client.connect(broker, port, keepalive=30)
    client.subscribe(topic)
    client.loop_start()
    try:
        event.wait(timeout=timeout_sec)
    finally:
        client.loop_stop()
        client.disconnect()
    return result.get("payload")


def _make_client():
    """paho-mqtt 1.x / 2.x — use legacy client_id constructor for broad compatibility."""
    return mqtt.Client(client_id="e2e_sc")


def publish_json(broker: str, port: int, topic: str, payload: dict, qos: int = 1) -> None:
    require_paho()
    client = _make_client()
    client.connect(broker, port, keepalive=15)
    client.publish(topic, json.dumps(payload), qos=qos)
    client.loop_start()
    time.sleep(0.3)
    client.loop_stop()
    client.disconnect()


def collect_messages(
    broker: str,
    port: int,
    topic: str,
    duration_sec: float = 5.0,
    max_messages: int = 50,
) -> List[dict]:
    """Collect JSON messages on topic for a fixed window (debug helper)."""
    require_paho()
    out: List[dict] = []

    def on_message(_c, _u, msg):
        if len(out) >= max_messages:
            return
        try:
            out.append(json.loads(msg.payload.decode("utf-8")))
        except Exception:
            out.append({"_raw": msg.payload.decode("utf-8", errors="replace")})

    client = _make_client()
    client.on_message = on_message
    client.connect(broker, port, keepalive=30)
    client.subscribe(topic)
    client.loop_start()
    time.sleep(duration_sec)
    client.loop_stop()
    client.disconnect()
    return out
