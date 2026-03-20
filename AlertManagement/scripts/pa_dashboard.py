#!/usr/bin/env python3
"""
PA status dashboard (Flask) — HTML status + JSON APIs for operators.

Environment:
  PA_DASHBOARD_PORT=8085
  PA_MQTT_BROKER, PA_MQTT_PORT, PA_MQTT_USER, PA_MQTT_PASS
  PA_MQTT_STATUS_TOPIC=pa/status  — optional one-shot subscribe sample
"""
from __future__ import annotations

import json
import os
import shutil
import threading
import time
from collections import deque

from flask import Flask, Response, jsonify, request, stream_with_context

app = Flask(__name__)

_start = time.time()
_history: deque = deque(maxlen=100)
_history_lock = threading.Lock()


def _disk_free_gb(path: str = "/") -> float:
    try:
        u = shutil.disk_usage(path)
        return round(u.free / (1024**3), 2)
    except OSError:
        return -1.0


def _mqtt_sample_status() -> dict | None:
    try:
        import paho.mqtt.client as mqtt
    except ImportError:
        return None

    broker = os.getenv("PA_MQTT_BROKER", "192.168.1.100")
    port = int(os.getenv("PA_MQTT_PORT", "1883"))
    user = os.getenv("PA_MQTT_USER", "")
    pwd = os.getenv("PA_MQTT_PASS", "")
    topic = os.getenv("PA_MQTT_STATUS_TOPIC", "pa/status")
    payload = [None]

    def on_message(_c, _u, msg):
        try:
            payload[0] = json.loads(msg.payload.decode())
        except Exception:
            payload[0] = {"raw": msg.payload.decode(errors="replace")[:500]}

    c = mqtt.Client(client_id="pa-dash-sample", protocol=mqtt.MQTTv311)
    if user:
        c.username_pw_set(user, pwd)
    c.on_message = on_message
    try:
        c.connect(broker, port, keepalive=10)
        c.subscribe(topic, qos=0)
        c.loop_start()
        time.sleep(1.0)
        c.loop_stop()
        c.disconnect()
    except OSError:
        return None
    return payload[0]


@app.route("/")
def home():
    uptime = int(time.time() - _start)
    free = _disk_free_gb()
    return f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>PA Dashboard</title>
<style>
body{{font-family:system-ui;background:#0f172a;color:#e2e8f0;padding:24px;max-width:720px;margin:0 auto;}}
h1{{color:#f59e0b;}} a{{color:#38bdf8;}} pre{{background:#1e293b;padding:12px;border-radius:8px;overflow:auto;}}
</style></head><body>
<h1>SudarshanChakra PA</h1>
<p>Topics: <code>pa/command</code>, <code>pa/status</code>, <code>pa/health</code></p>
<p>Uptime: <strong>{uptime}s</strong> · Disk free (/): <strong>{free} GB</strong></p>
<p><a href="/api/health">/api/health</a> · <a href="/api/status">/api/status</a> · <a href="/api/history">/api/history</a></p>
<p>SSE (live): <code>GET /api/events</code> — JSON snapshots every {os.getenv("PA_SSE_INTERVAL", "3")}s (use EventSource in browser or curl -N).</p>
</body></html>"""


@app.route("/api/health")
def health():
    return jsonify(
        {
            "ok": True,
            "service": "pa_dashboard",
            "uptime_s": round(time.time() - _start, 1),
            "disk_free_gb": _disk_free_gb(),
        }
    )


@app.route("/api/status")
def status():
    sample = _mqtt_sample_status()
    return jsonify(
        {
            "uptime_s": round(time.time() - _start, 1),
            "disk_free_gb": _disk_free_gb(),
            "mqtt_status_sample": sample,
        }
    )


@app.route("/api/history")
def history():
    with _history_lock:
        return jsonify(list(_history))


def _sse_snapshot() -> dict:
    with _history_lock:
        hist_len = len(_history)
    return {
        "uptime_s": round(time.time() - _start, 1),
        "disk_free_gb": _disk_free_gb(),
        "history_len": hist_len,
    }


@app.route("/api/events")
def sse_events():
    """Server-Sent Events stream for lightweight live status (no WebSocket)."""

    interval = float(os.getenv("PA_SSE_INTERVAL", "3"))

    def generate():
        while True:
            payload = json.dumps(_sse_snapshot())
            yield f"data: {payload}\n\n"
            time.sleep(interval)

    return Response(
        stream_with_context(generate()),
        mimetype="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.route("/api/volume", methods=["POST"])
def volume():
    """Forward ALSA-style volume hint to log; real control is via MQTT pa/command."""
    data = request.get_json(silent=True) or {}
    level = int(data.get("level", 80))
    entry = {"ts": time.time(), "event": "volume_request", "level": level}
    with _history_lock:
        _history.append(entry)
    return jsonify({"ok": True, "level": level, "hint": "Publish MQTT pa/command volume"})


@app.route("/api/event", methods=["POST"])
def ingest_event():
    """Optional: pa_controller can POST milestones here if extended."""
    data = request.get_json(silent=True) or {}
    with _history_lock:
        _history.append({"ts": time.time(), **data})
    return jsonify({"ok": True})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PA_DASHBOARD_PORT", "8085")), threaded=True)
