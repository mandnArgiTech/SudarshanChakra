#!/usr/bin/env python3
"""
zone_engine.py — Virtual Fence Polygon Engine
===============================================
Manages virtual fence zones and performs point-in-polygon checks
using the Shapely geometry library.

Zone types:
  - intrusion:              Alert when target class ENTERS polygon
  - zero_tolerance:         Same as intrusion but NEVER suppressed by worker tags
  - livestock_containment:  Alert when target class is OUTSIDE polygon
  - hazard:                 Same as intrusion (for snake/scorpion/fire zones)
"""

import json
import logging
import threading
from typing import Dict, List, Optional

from shapely.geometry import Point, Polygon

log = logging.getLogger("zone_engine")


class ZoneEngine:
    """
    Manages virtual fence zones and performs point-in-polygon checks.
    
    Thread-safe: reload() can be called from Flask GUI thread while
    check_detection() is called from inference thread.
    """

    def __init__(self, config_path: str = "/app/config/zones.json"):
        self.config_path = config_path
        self.zones: Dict[str, List[dict]] = {}      # camera_id → [zone_defs]
        self.polygons: Dict[str, Polygon] = {}       # "cam:zone_id" → Polygon
        self._lock = threading.RLock()
        self.reload()

    def reload(self):
        """
        Reload zone definitions from JSON config.
        Called on startup and whenever zones are added/removed via GUI.
        """
        with self._lock:
            self.zones.clear()
            self.polygons.clear()

            try:
                with open(self.config_path) as f:
                    data = json.load(f)
            except FileNotFoundError:
                log.warning("Zones config not found at %s. No zones active.",
                            self.config_path)
                return
            except json.JSONDecodeError as e:
                log.error("Invalid zones.json: %s", e)
                return

            # Handle both single-camera and multi-camera formats
            cameras = data.get("cameras", [data] if "camera_id" in data else [])

            for cam in cameras:
                cam_id = cam.get("camera_id", "unknown")
                zone_list = cam.get("zones", [])
                self.zones[cam_id] = zone_list

                for zone in zone_list:
                    if not zone.get("enabled", True):
                        continue
                    poly_coords = zone.get("polygon", [])
                    if len(poly_coords) < 3:
                        log.warning("Zone %s has <3 points, skipping.", zone.get("id"))
                        continue

                    key = f"{cam_id}:{zone['id']}"
                    try:
                        self.polygons[key] = Polygon(poly_coords)
                    except Exception as e:
                        log.error("Invalid polygon for zone %s: %s", zone.get("id"), e)

            log.info("Zone engine loaded: %d cameras, %d active zones",
                     len(self.zones), len(self.polygons))

    def check_detection(self, detection: dict) -> Optional[dict]:
        """
        Check if a detection's bottom-center point falls inside any zone.
        
        For 'intrusion', 'zero_tolerance', 'hazard': triggers when INSIDE polygon.
        For 'livestock_containment': triggers when OUTSIDE polygon.
        
        Args:
            detection: dict with keys: camera_id, class, bottom_center, confidence, bbox
            
        Returns:
            Zone violation dict if triggered, None otherwise.
        """
        with self._lock:
            cam_id = detection["camera_id"]
            cls = detection["class"]
            bx, by = detection["bottom_center"]
            point = Point(bx, by)

            zones = self.zones.get(cam_id, [])

            # First pass: check intrusion/zero_tolerance/hazard zones
            for zone in zones:
                if not zone.get("enabled", True):
                    continue
                if cls not in zone.get("target_classes", []):
                    continue
                if zone["type"] == "livestock_containment":
                    continue  # Handle separately below

                key = f"{cam_id}:{zone['id']}"
                polygon = self.polygons.get(key)

                if polygon and polygon.contains(point):
                    return {
                        "zone_id": zone["id"],
                        "zone_name": zone["name"],
                        "zone_type": zone["type"],
                        "priority": zone["priority"],
                        "detection": detection,
                    }

            # Second pass: livestock containment (triggers when OUTSIDE)
            for zone in zones:
                if not zone.get("enabled", True):
                    continue
                if zone["type"] != "livestock_containment":
                    continue
                if cls not in zone.get("target_classes", []):
                    continue

                key = f"{cam_id}:{zone['id']}"
                polygon = self.polygons.get(key)

                if polygon and not polygon.contains(point):
                    return {
                        "zone_id": zone["id"],
                        "zone_name": zone["name"],
                        "zone_type": zone["type"],
                        "priority": zone["priority"],
                        "detection": detection,
                    }

        return None

    def get_zones_for_camera(self, camera_id: str) -> List[dict]:
        """Return all zone definitions for a specific camera."""
        with self._lock:
            return list(self.zones.get(camera_id, []))

    def get_all_zones(self) -> Dict[str, List[dict]]:
        """Return all zones grouped by camera ID."""
        with self._lock:
            return dict(self.zones)
