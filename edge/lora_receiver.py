#!/usr/bin/env python3
"""
lora_receiver.py — ESP32 LoRa Tag Receiver
============================================
Listens on USB-Serial for LoRa beacon packets from ESP32 worker
tags and child safety tags. Provides sensor fusion data to the
alert decision engine.

Packet format from ESP32 (see esp32_lora_tag.ino):
  TAG:<id>,TYPE:<type>,SEQ:<n>,ACCEL:<g>,BAT:<v>

Types:
  WORKER_PING  — Periodic worker beacon (suppresses intrusion alarms)
  CHILD_PING   — Periodic child safety beacon
  FALL         — CRITICAL: child fall detected (immediate alert)

The LoRa receiver module on the Edge Node is typically a second ESP32
connected via USB, running a simple LoRa-to-Serial bridge firmware
that forwards received LoRa packets as lines to the USB serial port.
"""

import json
import logging
import threading
import time
from typing import List

try:
    import serial
except ImportError:
    serial = None

log = logging.getLogger("lora_receiver")


class LoRaReceiver(threading.Thread):
    """
    Receives ESP32 LoRa beacon packets via USB-Serial.

    Thread-safe: is_worker_nearby() can be called from the inference
    thread while run() processes serial data in its own thread.
    """

    def __init__(self, port: str = "/dev/ttyUSB0", baud: int = 115200,
                 authorized_tags_path: str = "/app/config/authorized_tags.json"):
        super().__init__(daemon=True, name="lora-receiver")
        self.port = port
        self.baud = baud
        self.last_seen: dict = {}       # tag_id → {"timestamp", "rssi", "type", "accel", "battery"}
        self.authorized_tags: set = set()
        self.fall_callbacks: list = []   # Callbacks for FALL events
        self._lock = threading.Lock()
        self._stop_event = threading.Event()
        self._load_authorized(authorized_tags_path)

    def _load_authorized(self, path: str):
        """Load authorized worker tag IDs from config."""
        try:
            with open(path) as f:
                data = json.load(f)
            self.authorized_tags = set(data.get("tags", []))
            log.info("Loaded %d authorized worker tags", len(self.authorized_tags))
        except FileNotFoundError:
            log.warning("Authorized tags file not found at %s", path)
            self.authorized_tags = set()
        except Exception as e:
            log.error("Error loading authorized tags: %s", e)
            self.authorized_tags = set()

    def run(self):
        """Main serial read loop — reconnects on failure."""
        if serial is None:
            log.error("pyserial not installed. LoRa receiver disabled.")
            return

        while not self._stop_event.is_set():
            try:
                ser = serial.Serial(self.port, self.baud, timeout=1)
                log.info("LoRa receiver connected on %s at %d baud", self.port, self.baud)

                while not self._stop_event.is_set():
                    line = ser.readline().decode("utf-8", errors="ignore").strip()
                    if line and not line.startswith("#"):  # Skip bridge stats/comments
                        self._parse_packet(line)

            except serial.SerialException as e:
                log.warning("LoRa serial error on %s: %s. Retrying in 5s...", self.port, e)
                time.sleep(5)
            except Exception as e:
                log.error("LoRa receiver error: %s. Retrying in 5s...", e)
                time.sleep(5)

    def _parse_packet(self, line: str):
        """
        Parse a LoRa beacon packet.

        Format: TAG:<id>,TYPE:<type>,SEQ:<n>[,ACCEL:<g>][,BAT:<v>][,PRIORITY:<p>]
        """
        try:
            parts = {}
            for segment in line.split(","):
                if ":" in segment:
                    key, value = segment.split(":", 1)
                    parts[key.strip()] = value.strip()

            tag_id = parts.get("TAG", "")
            pkt_type = parts.get("TYPE", "UNKNOWN")
            accel = float(parts.get("ACCEL", "0"))
            battery = float(parts.get("BAT", "0"))

            if not tag_id:
                return

            with self._lock:
                self.last_seen[tag_id] = {
                    "timestamp": time.time(),
                    "type": pkt_type,
                    "accel": accel,
                    "battery": battery,
                    "rssi": -1,
                }

            # Handle FALL detection — CRITICAL priority
            if pkt_type == "FALL":
                log.critical("FALL DETECTED from tag %s! Triggering critical alert.", tag_id)
                for callback in self.fall_callbacks:
                    try:
                        callback(tag_id, parts)
                    except Exception as e:
                        log.error("Fall callback error: %s", e)

            log.debug("LoRa packet: TAG=%s TYPE=%s ACCEL=%.2f BAT=%.2f",
                      tag_id, pkt_type, accel, battery)

        except Exception as e:
            log.debug("Failed to parse LoRa packet '%s': %s", line[:100], e)

    def is_worker_nearby(self, max_age_seconds: float = 15.0) -> bool:
        """
        Check if any authorized worker tag was seen recently.

        Used by alert decision engine to suppress intrusion alarms
        when known workers are on-site.
        """
        now = time.time()
        with self._lock:
            for tag_id, info in self.last_seen.items():
                if tag_id in self.authorized_tags:
                    if (now - info["timestamp"]) < max_age_seconds:
                        return True
        return False

    def get_nearby_workers(self, max_age_seconds: float = 15.0) -> List[dict]:
        """Return list of recently seen authorized worker tags."""
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
                        })
        return workers

    def get_all_tags(self, max_age_seconds: float = 60.0) -> List[dict]:
        """Return all recently seen tags (workers + children + unknown)."""
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
                        "accel": info["accel"],
                        "battery": info["battery"],
                    })
        return tags

    def stop(self):
        self._stop_event.set()
