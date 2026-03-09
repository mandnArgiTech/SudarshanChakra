#!/usr/bin/env python3
"""
mock_siren.py — Simulated PA Siren System for Dev Mode
========================================================
Replaces the real Ahuja SSA-250DP PA system when MOCK_SIREN=true.

Instead of publishing to the Pi Zero's pa/command MQTT topic, this
module logs siren activations and publishes acknowledgments so the
entire command chain (Android → VPS → Edge → Siren → Ack) works.
"""

import json
import logging
import time

log = logging.getLogger("mock_siren")


class MockSirenHandler:
    """
    Handles siren commands from cloud/Android app in dev mode.
    Logs the commands and sends acknowledgments back via MQTT.
    """

    def __init__(self, mqtt_client, node_id: str):
        self.mqtt = mqtt_client
        self.node_id = node_id
        self.siren_active = False
        self.activation_count = 0
        self.last_trigger_time = 0.0

        log.info("Mock siren handler initialized for node %s", node_id)

    def handle_command(self, client, userdata, msg):
        """MQTT on_message handler for siren commands."""
        topic = msg.topic
        try:
            payload = json.loads(msg.payload.decode())
        except (json.JSONDecodeError, UnicodeDecodeError):
            log.error("Invalid siren command payload on %s", topic)
            return

        if "trigger" in topic:
            self._trigger(payload)
        elif "stop" in topic:
            self._stop(payload)

    def _trigger(self, payload: dict):
        self.siren_active = True
        self.activation_count += 1
        self.last_trigger_time = time.time()

        log.warning("=" * 60)
        log.warning("  MOCK SIREN TRIGGERED!")
        log.warning("  Node: %s", self.node_id)
        log.warning("  URL: %s", payload.get("siren_url", "default"))
        log.warning("  Triggered by: %s", payload.get("triggered_by", "unknown"))
        log.warning("  Activation #%d", self.activation_count)
        log.warning("  (In production, Ahuja SSA-250DP would sound now)")
        log.warning("=" * 60)

        # Send acknowledgment back to cloud
        self.mqtt.publish("farm/siren/ack", json.dumps({
            "node_id": self.node_id,
            "status": "siren_activated",
            "mock": True,
            "activation_count": self.activation_count,
            "timestamp": time.time(),
        }), qos=1)

    def _stop(self, payload: dict):
        self.siren_active = False

        log.warning("=" * 60)
        log.warning("  MOCK SIREN STOPPED")
        log.warning("  Node: %s", self.node_id)
        log.warning("=" * 60)

        self.mqtt.publish("farm/siren/ack", json.dumps({
            "node_id": self.node_id,
            "status": "siren_stopped",
            "mock": True,
            "timestamp": time.time(),
        }), qos=1)

    def get_status(self) -> dict:
        return {
            "active": self.siren_active,
            "mock": True,
            "activation_count": self.activation_count,
            "last_trigger": self.last_trigger_time,
        }
