#!/usr/bin/env python3
"""
mock_camera.py — Synthetic Camera Feed for Dev Mode
=====================================================
Replaces real RTSP cameras when MOCK_CAMERAS=true.

Two modes:
  1. VIDEO_FILE: Reads frames from a video file in /app/mock_videos/
  2. GENERATED:  Creates synthetic frames with colored rectangles
                 simulating people, cows, snakes at known positions.

The mock camera also injects FAKE DETECTIONS at configurable intervals
so the entire pipeline (zone check → LoRa fusion → alert → MQTT)
can be tested end-to-end without a real YOLO model.
"""

import cv2
import numpy as np
import os
import time
import logging
import random
from dataclasses import dataclass
from typing import List, Optional
from queue import Queue

log = logging.getLogger("mock_camera")

# Detection scenarios that cycle through automatically
MOCK_SCENARIOS = [
    {
        "class": "person",
        "confidence": 0.87,
        "bbox": [200, 100, 300, 400],  # Inside typical perimeter zone
        "description": "Person walking through perimeter",
    },
    {
        "class": "person",
        "confidence": 0.92,
        "bbox": [250, 150, 350, 380],  # Inside pond zone (cam-03)
        "description": "Person near pond (zero-tolerance trigger)",
    },
    {
        "class": "child",
        "confidence": 0.78,
        "bbox": [280, 200, 340, 320],  # Small bbox = child
        "description": "Child detected near pond",
    },
    {
        "class": "snake",
        "confidence": 0.72,
        "bbox": [100, 350, 400, 390],  # Wide, low = snake shape
        "description": "Snake on ground",
    },
    {
        "class": "cow",
        "confidence": 0.91,
        "bbox": [50, 50, 250, 300],  # Outside typical containment polygon
        "description": "Cow outside containment area",
    },
    {
        "class": "fire",
        "confidence": 0.83,
        "bbox": [300, 100, 450, 250],
        "description": "Fire detected in storage area",
    },
    {
        "class": "scorpion",
        "confidence": 0.65,
        "bbox": [350, 400, 380, 430],  # Very small bbox
        "description": "Scorpion at ground level",
    },
    {
        "class": "dog",
        "confidence": 0.88,
        "bbox": [150, 200, 300, 380],  # Should be suppressed (info class)
        "description": "Farm dog (should NOT trigger alert)",
    },
    None,  # Empty frame — no detection
    None,  # Empty frame
]


class MockCameraGrabber:
    """
    Replaces CameraGrabber from pipeline.py in dev mode.
    Generates synthetic frames at configured FPS.
    """

    def __init__(self, camera_id: str, camera_name: str, fps: float = 1.0,
                 video_path: Optional[str] = None):
        self.camera_id = camera_id
        self.camera_name = camera_name
        self.fps = fps
        self.video_path = video_path
        self.frame_count = 0
        self.connected = True
        self.last_frame_time = 0.0
        self._cap = None

        if video_path and os.path.exists(video_path):
            self._cap = cv2.VideoCapture(video_path)
            log.info("[%s] Mock camera using video file: %s", camera_id, video_path)
        else:
            log.info("[%s] Mock camera generating synthetic frames at %.1f FPS",
                     camera_id, fps)

    def grab_frame(self) -> Optional[np.ndarray]:
        """Get next frame (from video or generated)."""
        if self._cap and self._cap.isOpened():
            ret, frame = self._cap.read()
            if not ret:
                self._cap.set(cv2.CAP_PROP_POS_FRAMES, 0)  # Loop video
                ret, frame = self._cap.read()
            if ret:
                self.frame_count += 1
                self.last_frame_time = time.time()
                return frame

        # Generate synthetic frame
        return self._generate_frame()

    def _generate_frame(self) -> np.ndarray:
        """Create a synthetic 640x480 frame with camera info overlay."""
        frame = np.zeros((480, 640, 3), dtype=np.uint8)

        # Dark background with slight noise
        frame[:] = (20, 25, 30)
        noise = np.random.randint(0, 10, frame.shape, dtype=np.uint8)
        frame = cv2.add(frame, noise)

        # Camera info
        cv2.putText(frame, f"DEV MODE: {self.camera_id}",
                    (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 200, 255), 2)
        cv2.putText(frame, f"{self.camera_name}",
                    (10, 60), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (150, 150, 150), 1)
        cv2.putText(frame, f"Frame: {self.frame_count}",
                    (10, 470), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (100, 100, 100), 1)
        cv2.putText(frame, time.strftime("%H:%M:%S"),
                    (540, 470), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (100, 100, 100), 1)

        # Draw a mock object based on current scenario
        scenario_idx = self.frame_count % len(MOCK_SCENARIOS)
        scenario = MOCK_SCENARIOS[scenario_idx]
        if scenario:
            x1, y1, x2, y2 = scenario["bbox"]
            color = {
                "person": (0, 255, 0), "child": (0, 255, 255),
                "cow": (255, 200, 0), "snake": (0, 0, 255),
                "scorpion": (128, 0, 255), "fire": (0, 0, 255),
                "dog": (200, 200, 200), "vehicle": (255, 255, 0),
            }.get(scenario["class"], (255, 255, 255))

            cv2.rectangle(frame, (x1, y1), (x2, y2), color, 2)
            cv2.putText(frame, f"{scenario['class']} {scenario['confidence']:.0%}",
                        (x1, y1 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)

        self.frame_count += 1
        self.last_frame_time = time.time()
        return frame

    def get_current_scenario(self) -> Optional[dict]:
        """Return the mock detection for the current frame."""
        if self.frame_count == 0:
            return None
        scenario_idx = (self.frame_count - 1) % len(MOCK_SCENARIOS)
        return MOCK_SCENARIOS[scenario_idx]


class MockInferencePipeline:
    """
    Replaces InferencePipeline in dev mode.
    Instead of running YOLO, it cycles through predefined detection
    scenarios at a configurable interval.
    """

    def __init__(self, cameras: list, detection_interval: float = 5.0):
        """
        Args:
            cameras: List of CameraConfig objects
            detection_interval: Seconds between mock detections
        """
        self.cameras = cameras
        self.detection_interval = detection_interval
        self.mock_cameras = {}
        self.results_callbacks = []
        self._running = False
        self._scenario_index = 0

        for cam in cameras:
            video_path = os.path.join("/app/mock_videos", f"{cam.id}.mp4")
            self.mock_cameras[cam.id] = MockCameraGrabber(
                camera_id=cam.id,
                camera_name=cam.name,
                fps=cam.fps,
                video_path=video_path if os.path.exists(video_path) else None,
            )

        log.info("Mock inference pipeline initialized with %d cameras, "
                 "detection every %.1fs", len(cameras), detection_interval)

    def start(self):
        """Run the mock detection loop (blocking, like the real pipeline)."""
        self._running = True

        try:
            from edge_gui import update_snapshot
        except ImportError:
            update_snapshot = None

        log.info("Mock pipeline running. Will cycle through %d detection scenarios.",
                 len(MOCK_SCENARIOS))

        while self._running:
            # Pick a camera and generate a detection
            cam_ids = list(self.mock_cameras.keys())
            cam_id = cam_ids[self._scenario_index % len(cam_ids)]
            mock_cam = self.mock_cameras[cam_id]

            # Grab frame (for GUI snapshot)
            frame = mock_cam.grab_frame()
            if update_snapshot:
                update_snapshot(cam_id, frame)

            # Get mock detection
            scenario = MOCK_SCENARIOS[self._scenario_index % len(MOCK_SCENARIOS)]
            self._scenario_index += 1

            if scenario:
                x1, y1, x2, y2 = scenario["bbox"]
                detection = {
                    "camera_id": cam_id,
                    "class": scenario["class"],
                    "confidence": scenario["confidence"],
                    "bbox": scenario["bbox"],
                    "bottom_center": [(x1 + x2) / 2, y2],
                    "timestamp": time.time(),
                    "frame_number": mock_cam.frame_count,
                    "mock": True,
                }

                log.info("MOCK DETECTION [%s] %s: %s (%.0f%%) — %s",
                         cam_id, scenario["class"],
                         scenario["bbox"], scenario["confidence"] * 100,
                         scenario["description"])

                for callback in self.results_callbacks:
                    try:
                        callback(detection)
                    except Exception as e:
                        log.error("Detection callback error: %s", e)

            time.sleep(self.detection_interval)

    def stop(self):
        self._running = False
        for cam in self.mock_cameras.values():
            if cam._cap and cam._cap.isOpened():
                cam._cap.release()

    def get_stats(self) -> dict:
        return {
            "mode": "MOCK",
            "cameras_total": len(self.cameras),
            "cameras_connected": len(self.mock_cameras),
            "scenario_index": self._scenario_index,
            "total_frames": sum(c.frame_count for c in self.mock_cameras.values()),
        }
