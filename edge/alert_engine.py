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
import subprocess
import threading
import time
import uuid
from collections import deque
import cv2
import numpy as np
import paho.mqtt.client as mqtt

try:
    from detection_filters import filter_detection
except ImportError:
    filter_detection = None

log = logging.getLogger("alert_engine")


class AlertDecisionEngine:
    """
    Central brain: detection + zone + LoRa → alert decision.

    Thread safety: process_detection() is called from the inference
    thread. All mutable state (_recent_alerts) is protected via lock.

    State transitions per detection:
      detection → filter → zone_check → dedup_check → lora_fusion → publish/suppress
    """

    def __init__(self, zone_engine, lora_receiver, mqtt_client: mqtt.Client,
                 node_id: str):
        self.zone_engine = zone_engine
        self.lora = lora_receiver
        self.mqtt = mqtt_client
        self.node_id = node_id

        self._recent_alerts: dict = {}
        self._dedup_lock = threading.Lock()
        self.DEDUP_WINDOW = int(os.getenv("ALERT_DEDUP_SECONDS", "30"))

        self.snapshot_dir = os.getenv("SNAPSHOT_DIR", "/tmp/snapshots")
        os.makedirs(self.snapshot_dir, exist_ok=True)

        self.vpn_ip = os.getenv("VPN_IP", self._default_vpn_ip())

        self.stats = {
            "total_detections": 0,
            "zone_violations": 0,
            "alerts_published": 0,
            "worker_suppressed": 0,
            "deduplicated": 0,
            "filtered_out": 0,
            "throttled": 0,
        }
        self._alert_history = deque(maxlen=100)
        self._camera_counts = {}
        self._throttle_window = float(os.getenv("ALERT_THROTTLE_WINDOW_SEC", "60"))
        self._throttle_max = int(os.getenv("ALERT_THROTTLE_MAX_PER_CAMERA", "10"))
        self._cleanup_stop = threading.Event()
        t = threading.Thread(
            target=self._snapshot_cleanup_loop,
            daemon=True,
            name="snapshot-cleanup",
        )
        t.start()

    def process_detection(self, detection: dict, frame: np.ndarray = None):
        """
        Main decision pipeline for a single detection.

        Called by InferencePipeline for every detected object in every frame.
        Must be fast — target <1ms per call.

        Args:
            detection: Detection dict from YOLO inference.
            frame: Original camera frame (optional). When provided, enables
                   fire/smoke color validation and snapshot saving.
        """
        correlation_id = str(uuid.uuid4())[:8]
        detection["correlation_id"] = correlation_id
        self.stats["total_detections"] += 1

        if filter_detection is not None:
            detection = filter_detection(detection, frame=frame)
            if detection is None:
                self.stats["filtered_out"] += 1
                return

        violation = self.zone_engine.check_detection(detection)
        if not violation:
            return

        self.stats["zone_violations"] += 1

        if violation["zone_type"] == "zero_tolerance" and detection["class"] == "person":
            if detection.get("metadata", {}).get("possible_child"):
                violation["priority"] = "critical"
                detection["class"] = "possible_child"
                log.info("[%s] Child heuristic escalated person to critical in %s",
                         correlation_id, violation["zone_name"])

        dedup_key = f"{violation['zone_id']}:{detection['class']}"
        now = time.time()

        with self._dedup_lock:
            if dedup_key in self._recent_alerts:
                if now - self._recent_alerts[dedup_key] < self.DEDUP_WINDOW:
                    self.stats["deduplicated"] += 1
                    return

        if violation["zone_type"] not in ("zero_tolerance",):
            if detection["class"] == "person" and self.lora.is_worker_nearby():
                self.stats["worker_suppressed"] += 1
                self._publish_suppression(detection, violation)
                return

        cam_id = detection["camera_id"]
        now = time.time()
        with self._dedup_lock:
            lst = self._camera_counts.setdefault(cam_id, [])
            lst[:] = [t for t in lst if now - t < self._throttle_window]
            if len(lst) >= self._throttle_max:
                self.stats["throttled"] += 1
                return
            lst.append(now)

        alert_id = str(uuid.uuid4())
        snapshot_url = ""
        if frame is None and detection.get("_frame") is not None:
            frame = detection.pop("_frame", None)
        if frame is not None:
            snapshot_url = self._save_snapshot(alert_id, frame, detection)

        alert = {
            "alert_id": alert_id,
            "correlation_id": correlation_id,
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
            "snapshot_url": snapshot_url,
            "worker_suppressed": False,
            "timestamp": now,
            "metadata": {
                "lora_workers_nearby": self.lora.get_nearby_workers(),
                "frame_number": detection.get("frame_number", 0),
            },
        }

        topic = f"farm/alerts/{violation['priority']}"
        try:
            result = self.mqtt.publish(topic, json.dumps(alert), qos=1)
            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                self.stats["alerts_published"] += 1
                self._alert_history.append({
                    "alert_id": alert_id,
                    "camera_id": detection["camera_id"],
                    "class": detection["class"],
                    "zone": violation["zone_name"],
                    "priority": violation["priority"],
                    "timestamp": now,
                    "snapshot_url": snapshot_url,
                })
                log.info("[%s] ALERT [%s] %s: %s in %s (%.0f%% conf)",
                         correlation_id,
                         violation["priority"].upper(),
                         detection["class"],
                         violation["zone_name"],
                         detection["camera_id"],
                         detection["confidence"] * 100)
                if os.getenv("LOCAL_ALERT_SOUND", "").lower() in ("1", "true", "yes"):
                    try:
                        wav = os.getenv("LOCAL_ALERT_WAV", "/usr/share/sounds/alsa/Front_Center.wav")
                        subprocess.Popen(
                            ["aplay", "-q", wav],
                            stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL,
                        )
                    except Exception:
                        pass
            else:
                log.error("[%s] MQTT publish failed (rc=%d) for alert %s",
                          correlation_id, result.rc, alert_id)
        except Exception as e:
            log.error("[%s] Failed to publish alert: %s", correlation_id, e)

        with self._dedup_lock:
            self._recent_alerts[dedup_key] = now
            self._cleanup_dedup()

    def _save_snapshot(self, alert_id: str, frame: np.ndarray,
                       detection: dict) -> str:
        """Save detection frame as JPEG snapshot and return its URL."""
        try:
            x1, y1, x2, y2 = [int(v) for v in detection["bbox"]]
            annotated = frame.copy()
            cv2.rectangle(annotated, (x1, y1), (x2, y2), (0, 0, 255), 2)
            label = f"{detection['class']} {detection['confidence']:.0%}"
            cv2.putText(annotated, label, (x1, y1 - 10),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)

            path = os.path.join(self.snapshot_dir, f"{alert_id}.jpg")
            cv2.imwrite(path, annotated, [cv2.IMWRITE_JPEG_QUALITY, 85])
            log.debug("Snapshot saved: %s", path)
            return f"http://{self.vpn_ip}:5000/snapshots/{alert_id}.jpg"
        except Exception as e:
            log.warning("Failed to save snapshot: %s", e)
            return ""

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

    def _default_vpn_ip(self) -> str:
        """Get this node's VPN IP from well-known mappings."""
        node_ips = {
            "edge-node-a": "10.8.0.10",
            "edge-node-b": "10.8.0.11",
        }
        return node_ips.get(self.node_id, "10.8.0.10")

    def get_stats(self) -> dict:
        """Return engine statistics for monitoring/heartbeat."""
        return dict(self.stats)

    def get_alert_history(self) -> list:
        """Last N alerts (newest last)."""
        return list(self._alert_history)

    def _snapshot_cleanup_loop(self):
        """Delete snapshot JPEGs older than 24h periodically."""
        while not self._cleanup_stop.is_set():
            if self._cleanup_stop.wait(timeout=3600):
                break
            try:
                cutoff = time.time() - 86400
                for name in os.listdir(self.snapshot_dir):
                    if not name.endswith(".jpg"):
                        continue
                    path = os.path.join(self.snapshot_dir, name)
                    try:
                        if os.path.isfile(path) and os.path.getmtime(path) < cutoff:
                            os.remove(path)
                    except OSError:
                        pass
            except Exception as e:
                log.debug("Snapshot cleanup: %s", e)
