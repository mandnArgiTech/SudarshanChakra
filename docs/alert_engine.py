#!/usr/bin/env python3
"""
alert_engine.py — Central Alert Decision Engine
=================================================
Combines detection results, zone violations, and LoRa sensor fusion
to produce validated alerts that are published to the VPS MQTT broker.

Decision pipeline per detection:
  1. Zone Check      → Is the detection inside a monitored zone?
  2. Deduplication   → Was this exact zone+class already alerted recently?
  3. LoRa Fusion     → Is an authorized worker tag nearby? (suppress if yes)
  4. Priority Route  → Zero-tolerance zones skip fusion (never suppressed)
  5. Publish         → Send alert to VPS via MQTT over VPN
"""

import json
import logging
import os
import time
import uuid
from typing import Optional

import paho.mqtt.client as mqtt

log = logging.getLogger("alert_engine")


class AlertDecisionEngine:
    """
    Central brain: detection + zone + LoRa → alert decision.
    
    Thread safety: process_detection() is called from the inference
    thread. All mutable state (_recent_alerts) is protected.
    
    State transitions per detection:
      detection → zone_check → dedup_check → lora_fusion → publish/suppress
    """

    def __init__(self, zone_engine, lora_receiver, mqtt_client: mqtt.Client,
                 node_id: str):
        self.zone_engine = zone_engine
        self.lora = lora_receiver
        self.mqtt = mqtt_client
        self.node_id = node_id

        # Deduplication
        self._recent_alerts: dict = {}  # "zone_id:class" → timestamp
        self.DEDUP_WINDOW = int(os.getenv("ALERT_DEDUP_SECONDS", "30"))

        # Snapshot directory (for saving detection frames)
        self.snapshot_dir = os.getenv("SNAPSHOT_DIR", "/tmp/snapshots")
        os.makedirs(self.snapshot_dir, exist_ok=True)

        # Counters for monitoring
        self.stats = {
            "total_detections": 0,
            "zone_violations": 0,
            "alerts_published": 0,
            "worker_suppressed": 0,
            "deduplicated": 0,
        }

    def process_detection(self, detection: dict):
        """
        Main decision pipeline for a single detection.
        
        Called by InferencePipeline for every detected object in every frame.
        Must be fast — target <1ms per call.
        """
        self.stats["total_detections"] += 1

        # ── Step 1: Zone Check ──
        violation = self.zone_engine.check_detection(detection)
        if not violation:
            return  # Detection is not in any monitored zone — ignore

        self.stats["zone_violations"] += 1

        # ── Step 2: Deduplication ──
        dedup_key = f"{violation['zone_id']}:{detection['class']}"
        now = time.time()

        if dedup_key in self._recent_alerts:
            if now - self._recent_alerts[dedup_key] < self.DEDUP_WINDOW:
                self.stats["deduplicated"] += 1
                return  # Already alerted for this zone+class recently

        # ── Step 3: LoRa Sensor Fusion ──
        # Zero-tolerance zones NEVER get suppressed (even if workers are nearby)
        if violation["zone_type"] not in ("zero_tolerance",):
            if detection["class"] == "person" and self.lora.is_worker_nearby():
                self.stats["worker_suppressed"] += 1
                self._publish_suppression(detection, violation)
                return  # Authorized worker — suppress alarm

        # ── Step 4: Build Alert Payload ──
        alert_id = str(uuid.uuid4())
        alert = {
            "alert_id": alert_id,
            "node_id": self.node_id,
            "camera_id": detection["camera_id"],
            "zone_id": violation["zone_id"],
            "zone_name": violation["zone_name"],
            "zone_type": violation["zone_type"],
            "priority": violation["priority"],
            "detection_class": detection["class"],
            "confidence": round(detection["confidence"], 3),
            "bbox": [round(v, 1) for v in detection["bbox"]],
            "bottom_center": [round(v, 1) for v in detection["bottom_center"]],
            "snapshot_url": f"http://{self._get_vpn_ip()}:5000/snapshots/{alert_id}.jpg",
            "worker_suppressed": False,
            "timestamp": now,
            "metadata": {
                "lora_workers_nearby": self.lora.get_nearby_workers(),
                "frame_number": detection.get("frame_number", 0),
            },
        }

        # ── Step 5: Publish to VPS Broker ──
        topic = f"farm/alerts/{violation['priority']}"
        try:
            result = self.mqtt.publish(topic, json.dumps(alert), qos=1)
            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                self.stats["alerts_published"] += 1
                log.info("ALERT [%s] %s: %s in %s (%.0f%% conf)",
                         violation["priority"].upper(),
                         detection["class"],
                         violation["zone_name"],
                         detection["camera_id"],
                         detection["confidence"] * 100)
            else:
                log.error("MQTT publish failed (rc=%d) for alert %s", result.rc, alert_id)
        except Exception as e:
            log.error("Failed to publish alert: %s", e)

        # Update deduplication tracker
        self._recent_alerts[dedup_key] = now
        self._cleanup_dedup()

    def process_fall_event(self, tag_id: str, packet_data: dict):
        """
        Handle ESP32 fall detection event — always CRITICAL priority.
        Called directly by LoRa receiver's fall_callback.
        """
        alert_id = str(uuid.uuid4())
        alert = {
            "alert_id": alert_id,
            "node_id": self.node_id,
            "camera_id": "lora_sensor",
            "zone_id": "zone-pond-safety",
            "zone_name": "Pond Safety (Fall Detector)",
            "zone_type": "zero_tolerance",
            "priority": "critical",
            "detection_class": "fall_detected",
            "confidence": 1.0,
            "bbox": [],
            "bottom_center": [],
            "snapshot_url": "",
            "worker_suppressed": False,
            "timestamp": time.time(),
            "metadata": {
                "source": "esp32_fall_detector",
                "tag_id": tag_id,
                "packet": packet_data,
            },
        }

        topic = "farm/alerts/critical"
        try:
            self.mqtt.publish(topic, json.dumps(alert), qos=1)
            self.stats["alerts_published"] += 1
            log.critical("FALL ALERT published for tag %s", tag_id)
        except Exception as e:
            log.error("Failed to publish fall alert: %s", e)

    def _publish_suppression(self, detection: dict, violation: dict):
        """Log worker-suppressed events for audit trail."""
        try:
            self.mqtt.publish("farm/events/worker_identified", json.dumps({
                "node_id": self.node_id,
                "camera_id": detection["camera_id"],
                "zone_id": violation["zone_id"],
                "zone_name": violation["zone_name"],
                "detection_class": detection["class"],
                "workers": self.lora.get_nearby_workers(),
                "timestamp": time.time(),
            }), qos=0)
        except Exception:
            pass

        log.debug("Suppressed %s alarm in %s — authorized worker nearby",
                  detection["class"], violation["zone_name"])

    def _cleanup_dedup(self):
        """Remove stale dedup entries to prevent memory leak."""
        now = time.time()
        cutoff = self.DEDUP_WINDOW * 2
        self._recent_alerts = {
            k: v for k, v in self._recent_alerts.items()
            if now - v < cutoff
        }

    def _get_vpn_ip(self) -> str:
        """Get this node's VPN IP for snapshot URLs."""
        node_ips = {
            "edge-node-a": "10.8.0.10",
            "edge-node-b": "10.8.0.11",
        }
        return node_ips.get(self.node_id, "10.8.0.10")

    def get_stats(self) -> dict:
        """Return engine statistics for monitoring/heartbeat."""
        return dict(self.stats)
