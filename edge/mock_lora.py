#!/usr/bin/env python3
"""
mock_lora.py — Simulated ESP32 LoRa Tag System for Dev Mode
=============================================================
Replaces the real LoRa USB-Serial receiver when MOCK_LORA=true.

Simulates:
  - Worker tag beacons (suppresses intrusion alarms)
  - Child safety tag beacons
  - Fall detection events (triggerable via MQTT command)

In dev mode, you can trigger a simulated fall event by publishing:
  mosquitto_pub -t "dev/simulate/fall" -m '{"tag_id": "TAG-C001"}'
"""

import json
import logging
import os
import threading
import time
from typing import List

log = logging.getLogger("mock_lora")


class MockLoRaReceiver(threading.Thread):
    """
    Simulates the LoRa receiver for dev/test environments.

    Periodically "sees" authorized worker tags to test suppression logic.
    Can be triggered to simulate a child fall event via MQTT.
    """

    def __init__(self, authorized_tags_path: str = "/app/config/authorized_tags.json",
                 worker_present: bool = True,
                 beacon_interval: float = 5.0):
        """
        Args:
            authorized_tags_path: Path to authorized_tags.json
            worker_present: If True, simulates a worker tag being nearby
                           (tests alarm suppression). If False, no worker
                           tag is seen (tests intruder detection).
            beacon_interval: Seconds between simulated beacons
        """
        super().__init__(daemon=True, name="mock-lora")
        self.worker_present = worker_present
        self.beacon_interval = beacon_interval
        self.last_seen = {}
        self.authorized_tags = set()
        self.fall_callbacks = []
        self._stop_event = threading.Event()
        self._lock = threading.Lock()
        self._load_authorized(authorized_tags_path)

        log.info("Mock LoRa receiver initialized. Worker present: %s", worker_present)

    def _load_authorized(self, path: str):
        try:
            with open(path) as f:
                data = json.load(f)
            self.authorized_tags = set(data.get("tags", []))
            log.info("Loaded %d authorized tags for mock", len(self.authorized_tags))
        except (FileNotFoundError, json.JSONDecodeError):
            self.authorized_tags = {"TAG-W001", "TAG-W002"}
            log.warning("Using default mock tags: TAG-W001, TAG-W002")

    def run(self):
        """Simulate periodic worker tag beacons."""
        while not self._stop_event.is_set():
            if self.worker_present and self.authorized_tags:
                # Simulate a worker tag beacon
                tag_id = list(self.authorized_tags)[0]  # Use first authorized tag
                with self._lock:
                    self.last_seen[tag_id] = {
                        "timestamp": time.time(),
                        "type": "WORKER_PING",
                        "accel": 0.98,
                        "battery": 3.72,
                        "rssi": -45,
                    }
                log.debug("Mock beacon: %s (worker nearby)", tag_id)

            time.sleep(self.beacon_interval)

    def simulate_fall(self, tag_id: str = "TAG-C001"):
        """
        Simulate a child fall detection event.
        Can be called programmatically or triggered via MQTT.
        """
        log.warning("MOCK FALL EVENT simulated for tag %s", tag_id)

        with self._lock:
            self.last_seen[tag_id] = {
                "timestamp": time.time(),
                "type": "FALL",
                "accel": 0.1,  # Free-fall acceleration
                "battery": 3.65,
                "rssi": -38,
            }

        # Fire fall callbacks (same as real LoRa receiver)
        packet_data = {
            "TAG": tag_id,
            "TYPE": "FALL",
            "PRIORITY": "CRITICAL",
            "ACCEL": "0.10",
        }
        for callback in self.fall_callbacks:
            try:
                callback(tag_id, packet_data)
            except Exception as e:
                log.error("Fall callback error: %s", e)

    def set_worker_present(self, present: bool):
        """Toggle worker presence for testing."""
        self.worker_present = present
        if not present:
            with self._lock:
                # Clear worker tags from last_seen
                self.last_seen = {k: v for k, v in self.last_seen.items()
                                  if k not in self.authorized_tags}
        log.info("Mock worker presence set to: %s", present)

    def is_worker_nearby(self, max_age_seconds: float = 15.0) -> bool:
        now = time.time()
        with self._lock:
            for tag_id, info in self.last_seen.items():
                if tag_id in self.authorized_tags:
                    if (now - info["timestamp"]) < max_age_seconds:
                        return True
        return False

    def get_nearby_workers(self, max_age_seconds: float = 15.0) -> List[dict]:
        now = time.time()
        workers = []
        with self._lock:
            for tag_id, info in self.last_seen.items():
                if tag_id in self.authorized_tags:
                    age = now - info["timestamp"]
                    if age < max_age_seconds:
                        workers.append({
                            "tag_id": tag_id,
                            "type": info["type"],
                            "age_seconds": round(age, 1),
                            "battery": info["battery"],
                            "mock": True,
                        })
        return workers

    def get_all_tags(self, max_age_seconds: float = 60.0) -> List[dict]:
        now = time.time()
        tags = []
        with self._lock:
            for tag_id, info in self.last_seen.items():
                age = now - info["timestamp"]
                if age < max_age_seconds:
                    tags.append({
                        "tag_id": tag_id,
                        "type": info["type"],
                        "authorized": tag_id in self.authorized_tags,
                        "age_seconds": round(age, 1),
                        "battery": info["battery"],
                        "mock": True,
                    })
        return tags

    def stop(self):
        self._stop_event.set()
