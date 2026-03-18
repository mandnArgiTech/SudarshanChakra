#!/usr/bin/env python3
"""Minimal PA status dashboard (Flask). Set PA_DASHBOARD_PORT=8085."""
import os
import time
from flask import Flask, jsonify

app = Flask(__name__)


@app.route("/")
def home():
    return (
        "<html><body><h1>PA Dashboard</h1><p>MQTT topics: pa/command, pa/status, pa/health</p>"
        f"<pre>uptime_s={time.time()}</pre></body></html>"
    )


@app.route("/api/health")
def health():
    return jsonify({"ok": True, "service": "pa_dashboard"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PA_DASHBOARD_PORT", "8085")))
