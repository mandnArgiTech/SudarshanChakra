#!/usr/bin/env python3
"""
detection_filters.py — Post-Processing Validation Filters
==========================================================
Multi-layer false positive reduction for every detection class.
Called by alert_engine.py before zone checks.

Filters:
  Layer 1: Geometric validation (shape, size, position)
  Layer 2: Color validation (fire/smoke HSV histogram)
  Layer 3: Temporal confirmation (fire/smoke/scorpion)
  Layer 4: Night-time threshold adjustment
"""

import cv2
import logging
import time
import numpy as np
from collections import defaultdict
from datetime import datetime

log = logging.getLogger("detection_filters")


# ─── Configuration ────────────────────────────────────────────────────────

CLASS_THRESHOLDS = {
    "person":   0.40,
    "child":    0.40,
    "cow":      0.40,
    "snake":    0.50,
    "scorpion": 0.35,
    "fire":     0.45,
    "smoke":    0.40,
    "dog":      0.45,
    "vehicle":  0.45,
    "bird":     0.50,
}

NIGHT_ADJUSTMENTS = {
    "person": -0.05, "child": -0.05, "cow": -0.05,
    "snake": -0.10, "scorpion": -0.10, "fire": 0.0,
    "smoke": -0.15, "dog": -0.05, "vehicle": -0.05, "bird": -0.10,
}

SUPPRESS_CLASSES = {"dog", "vehicle", "bird"}  # These never generate alerts


# ─── Temporal Confirmer ───────────────────────────────────────────────────

class TemporalConfirmer:
    """Requires N detections within T seconds to confirm."""

    def __init__(self):
        self._history = defaultdict(list)
        self._configs = {
            "fire":     {"count": 3, "window": 5.0},
            "smoke":    {"count": 3, "window": 5.0},
            "scorpion": {"count": 2, "window": 3.0},
        }

    def needs_confirmation(self, class_name: str) -> bool:
        return class_name in self._configs

    def check(self, camera_id: str, class_name: str) -> bool:
        if class_name not in self._configs:
            return True  # No confirmation needed

        cfg = self._configs[class_name]
        key = f"{camera_id}:{class_name}"
        now = time.time()

        self._history[key].append(now)
        cutoff = now - cfg["window"]
        self._history[key] = [t for t in self._history[key] if t > cutoff]

        confirmed = len(self._history[key]) >= cfg["count"]
        if confirmed:
            night = "(night)" if is_nighttime() else ""
            log.info("Temporal confirmation: %s on %s CONFIRMED (%d/%d in %.1fs) %s",
                     class_name, camera_id, len(self._history[key]),
                     cfg["count"], cfg["window"], night)
        return confirmed


# ─── Night Detection ──────────────────────────────────────────────────────

def is_nighttime() -> bool:
    hour = datetime.now().hour
    return hour >= 18 or hour < 6  # Sanga Reddy: ~18:30 sunset, ~05:30 sunrise


def get_threshold(class_name: str) -> float:
    base = CLASS_THRESHOLDS.get(class_name, 0.40)
    if is_nighttime():
        adj = NIGHT_ADJUSTMENTS.get(class_name, 0)
        return max(0.15, base + adj)
    return base


# ─── Geometric Filters ────────────────────────────────────────────────────

def validate_snake(det: dict, frame_h: int = 480) -> bool:
    x1, y1, x2, y2 = det["bbox"]
    w, h = x2 - x1, y2 - y1

    aspect = max(w, h) / max(min(w, h), 1)
    if aspect < 1.3:
        return False  # Too square for a snake

    area_ratio = (w * h) / (640 * frame_h)
    if area_ratio > 0.40 or area_ratio < 0.001:
        return False  # Impossibly large or too small

    if y2 < frame_h * 0.3:
        return False  # Snakes don't fly — reject high detections

    det.setdefault("metadata", {})["aspect_ratio"] = round(aspect, 2)
    return True


def validate_scorpion(det: dict, frame_h: int = 480) -> bool:
    x1, y1, x2, y2 = det["bbox"]
    w, h = x2 - x1, y2 - y1

    area_ratio = (w * h) / (640 * frame_h)
    if area_ratio > 0.10:
        return False  # Scorpions are small — reject large boxes

    if y2 < frame_h * 0.5:
        return False  # Must be in lower half (ground level)

    return True


def validate_person(det: dict, frame_h: int = 480) -> bool:
    x1, y1, x2, y2 = det["bbox"]
    h = y2 - y1

    height_ratio = h / frame_h
    if height_ratio < 0.05 or height_ratio > 0.95:
        return False  # Too small (noise) or too large (fills frame)

    return True


def validate_fire(det: dict, frame: np.ndarray = None) -> bool:
    """Validate fire with color histogram if frame is available."""
    if frame is None:
        return True  # Can't validate without frame

    x1, y1, x2, y2 = [int(v) for v in det["bbox"]]
    fh, fw = frame.shape[:2]
    x1, y1 = max(0, x1), max(0, y1)
    x2, y2 = min(fw, x2), min(fh, y2)

    roi = frame[y1:y2, x1:x2]
    if roi.size == 0:
        return False

    hsv = cv2.cvtColor(roi, cv2.COLOR_BGR2HSV)

    # Fire HSV ranges
    mask1 = cv2.inRange(hsv, np.array([0, 100, 200]), np.array([15, 255, 255]))
    mask2 = cv2.inRange(hsv, np.array([15, 100, 200]), np.array([35, 255, 255]))
    mask3 = cv2.inRange(hsv, np.array([170, 100, 200]), np.array([180, 255, 255]))

    fire_mask = mask1 | mask2 | mask3
    total = roi.shape[0] * roi.shape[1]
    fire_ratio = cv2.countNonZero(fire_mask) / total

    det.setdefault("metadata", {})["fire_pixel_ratio"] = round(fire_ratio, 3)
    return fire_ratio > 0.30


def validate_smoke(det: dict, frame: np.ndarray = None) -> bool:
    """Validate smoke with color + texture analysis."""
    if frame is None:
        return True

    x1, y1, x2, y2 = [int(v) for v in det["bbox"]]
    fh, fw = frame.shape[:2]
    x1, y1 = max(0, x1), max(0, y1)
    x2, y2 = min(fw, x2), min(fh, y2)

    roi = frame[y1:y2, x1:x2]
    if roi.size == 0:
        return False

    hsv = cv2.cvtColor(roi, cv2.COLOR_BGR2HSV)
    smoke_mask = cv2.inRange(hsv, np.array([0, 0, 80]), np.array([180, 80, 220]))
    total = roi.shape[0] * roi.shape[1]
    smoke_ratio = cv2.countNonZero(smoke_mask) / total

    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    texture_var = float(np.var(gray))

    valid = smoke_ratio > 0.40 and 100 < texture_var < 3000

    det.setdefault("metadata", {})["smoke_pixel_ratio"] = round(smoke_ratio, 3)
    det.setdefault("metadata", {})["texture_variance"] = round(texture_var, 1)
    return valid


def check_child_heuristic(det: dict, frame_h: int = 480) -> bool:
    """Check if a 'person' detection might actually be a child."""
    x1, y1, x2, y2 = det["bbox"]
    bbox_h = y2 - y1
    height_ratio = bbox_h / frame_h

    if height_ratio < 0.30:
        det.setdefault("metadata", {})["possible_child"] = True
        det.setdefault("metadata", {})["height_ratio"] = round(height_ratio, 3)
        return True
    return False


# ─── Master Filter ────────────────────────────────────────────────────────

GEOMETRIC_VALIDATORS = {
    "snake": validate_snake,
    "scorpion": validate_scorpion,
    "person": validate_person,
    "child": validate_person,
}

temporal_confirmer = TemporalConfirmer()


def filter_detection(det: dict, frame: np.ndarray = None,
                     frame_h: int = 480) -> dict | None:
    """
    Master filter pipeline. Returns the detection if it passes all
    filters, or None if it should be rejected.

    Called by alert_engine.py for every YOLO detection.
    """
    cls = det["class"]

    # Skip suppression classes (they never generate alerts)
    if cls in SUPPRESS_CLASSES:
        return None

    # Layer 0: Confidence threshold (with night adjustment)
    threshold = get_threshold(cls)
    if det["confidence"] < threshold:
        return None

    # Layer 1: Geometric validation
    validator = GEOMETRIC_VALIDATORS.get(cls)
    if validator and not validator(det, frame_h):
        return None

    # Layer 2: Color validation (fire/smoke only)
    if cls == "fire" and frame is not None:
        if not validate_fire(det, frame):
            return None
    elif cls == "smoke" and frame is not None:
        if not validate_smoke(det, frame):
            return None

    # Layer 3: Temporal confirmation
    if temporal_confirmer.needs_confirmation(cls):
        if not temporal_confirmer.check(det["camera_id"], cls):
            return None  # Not yet confirmed — need more frames

    # Layer 4: Child heuristic (for person detections in zero_tolerance zones)
    if cls == "person":
        check_child_heuristic(det, frame_h)

    return det
