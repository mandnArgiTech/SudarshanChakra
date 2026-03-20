#!/usr/bin/env python3
"""
pipeline.py — Multi-Camera RTSP Frame-Skipping Inference Pipeline
==================================================================
Grabs frames from RTSP cameras at 2-3 FPS per camera, queues them,
and runs batched YOLOv8n inference on the RTX 3060.

Target: 6-8 cameras on a single RTX 3060 12GB at <15ms/frame.
"""

import cv2
import os
import threading
import time
import logging
from queue import Queue
from dataclasses import dataclass
from typing import List, Callable
import numpy as np

from ultralytics import YOLO

log = logging.getLogger("pipeline")

_mqtt_camera_events = None


def set_mqtt_camera_events(client):
    """Optional paho client for camera online/offline events (farm.events)."""
    global _mqtt_camera_events
    _mqtt_camera_events = client


def _publish_camera_event(camera_id: str, event: str):
    if _mqtt_camera_events is None:
        return
    try:
        import json as _json
        _mqtt_camera_events.publish(
            "farm/events/camera_status",
            _json.dumps({"camera_id": camera_id, "event": event, "ts": time.time()}),
            qos=0,
        )
    except Exception:
        pass


# ── Model Paths ──
ENGINE_PATH = os.getenv("ENGINE_PATH", "/app/models/yolov8n_farm.engine")
PT_PATH = os.getenv("PT_PATH", "/app/models/yolov8n_farm.pt")
CONFIDENCE_THRESHOLD = float(os.getenv("CONFIDENCE_THRESHOLD", "0.40"))
INPUT_SIZE = int(os.getenv("INPUT_SIZE", "640"))


def load_model() -> YOLO:
    """
    Load the YOLO model, building TensorRT engine on first run.
    The .engine file is cached on a mounted volume so subsequent
    container restarts skip the 2-3 minute export step.
    """
    if os.path.exists(ENGINE_PATH):
        log.info("Loading cached TensorRT engine: %s", ENGINE_PATH)
        return YOLO(ENGINE_PATH)

    if not os.path.exists(PT_PATH):
        log.warning("Custom model not found at %s, downloading YOLOv8n base...", PT_PATH)
        model = YOLO("yolov8n.pt")
    else:
        log.info("Loading PyTorch model: %s", PT_PATH)
        model = YOLO(PT_PATH)

    log.info("Exporting to TensorRT FP16 (this takes 2-3 minutes on first run)...")
    model.export(format="engine", half=True, device=0, workspace=4)
    log.info("TensorRT export complete. Engine cached at: %s", ENGINE_PATH)

    return YOLO(ENGINE_PATH)


@dataclass
class CameraConfig:
    """Configuration for a single RTSP camera."""
    id: str
    name: str
    rtsp_url: str
    fps: float = 2.0
    enabled: bool = True


@dataclass
class FramePacket:
    """A single frame captured from a camera, ready for inference."""
    camera_id: str
    frame: np.ndarray
    timestamp: float
    frame_number: int


class CameraGrabber(threading.Thread):
    """
    Grabs frames from a single RTSP camera at the configured FPS.

    Design decisions:
    - Runs in its own thread (GIL is released during cv2.read())
    - Non-blocking queue put — drops oldest frame if queue is full
    - Exponential backoff on disconnect (1s → 30s max)
    - cv2.CAP_PROP_BUFFERSIZE=1 to always get latest frame
    """

    def __init__(self, config: CameraConfig, queue: Queue):
        super().__init__(daemon=True, name=f"cam-{config.id}")
        self.config = config
        self.queue = queue
        self._stop_event = threading.Event()
        self.frame_count = 0
        self.connected = False
        self.last_frame_time = 0.0

    def run(self):
        interval = 1.0 / self.config.fps
        backoff = 1.0

        while not self._stop_event.is_set():
            cap = cv2.VideoCapture(self.config.rtsp_url, cv2.CAP_FFMPEG)
            cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)

            if not cap.isOpened():
                if self.connected:
                    _publish_camera_event(self.config.id, "offline")
                self.connected = False
                log.warning("[%s] Cannot open RTSP stream. Retrying in %.0fs...",
                            self.config.id, backoff)
                time.sleep(min(backoff, 30))
                backoff *= 2
                continue

            was_connected = self.connected
            self.connected = True
            backoff = 1.0
            log.info("[%s] Connected to %s at %.1f FPS",
                     self.config.id, self.config.name, self.config.fps)
            if not was_connected:
                _publish_camera_event(self.config.id, "online")

            while not self._stop_event.is_set():
                ret, frame = cap.read()
                if not ret:
                    log.warning("[%s] Frame read failed. Reconnecting...", self.config.id)
                    self.connected = False
                    _publish_camera_event(self.config.id, "offline")
                    break

                self.frame_count += 1
                self.last_frame_time = time.time()

                packet = FramePacket(
                    camera_id=self.config.id,
                    frame=frame,
                    timestamp=self.last_frame_time,
                    frame_number=self.frame_count,
                )

                # Non-blocking: drop oldest if queue is full
                if self.queue.full():
                    try:
                        self.queue.get_nowait()
                    except Exception:
                        pass
                self.queue.put(packet)

                # Provide frame to Edge GUI snapshot cache
                try:
                    from edge_gui import update_snapshot
                    update_snapshot(self.config.id, frame)
                except ImportError:
                    pass

                time.sleep(interval)

            cap.release()

    def stop(self):
        self._stop_event.set()


class InferencePipeline:
    """
    Consumes FramePackets from all cameras and runs YOLO inference.

    Detection results are passed to registered callbacks (zone engine,
    alert engine, etc.) for downstream processing.

    Resource budget on RTX 3060 12GB:
    - YOLOv8n TensorRT FP16: ~200 MB VRAM
    - 8 cameras × 2.5 FPS = 20 inferences/sec
    - At ~6ms/inference (TensorRT FP16), GPU utilization ≈ 12%
    - Leaves ~11 GB VRAM and 88% GPU for headroom
    """

    def __init__(self, model: YOLO, cameras: List[CameraConfig]):
        self.model = model
        self._model_lock = threading.Lock()
        self.cameras = [c for c in cameras if c.enabled]
        self.frame_queue: Queue = Queue(maxsize=len(self.cameras) * 2)
        self.grabbers: List[CameraGrabber] = []
        self.results_callbacks: List[Callable] = []
        self._running = False
        self.model_path = PT_PATH
        try:
            if getattr(model, "ckpt_path", None):
                self.model_path = model.ckpt_path
        except Exception:
            pass

    def swap_model(self, path: str) -> bool:
        """Hot-swap weights (MQTT farm/admin/model_update). Path: .pt or .engine."""
        path = os.path.expanduser(path.strip())
        if not os.path.isfile(path):
            log.error("Model file not found: %s", path)
            return False
        try:
            new_model = YOLO(path)
            with self._model_lock:
                self.model = new_model
            self.model_path = path
            log.info("Inference model swapped to %s", path)
            return True
        except Exception as e:
            log.error("Model swap failed: %s", e)
            return False

    def start(self):
        """Start all camera grabbers and run inference loop (blocking)."""
        self._running = True

        # Start camera threads
        for cam in self.cameras:
            grabber = CameraGrabber(cam, self.frame_queue)
            grabber.start()
            self.grabbers.append(grabber)
            log.info("Started grabber for %s (%s) at %.1f FPS",
                     cam.id, cam.name, cam.fps)

        log.info("Inference pipeline running with %d cameras. Waiting for frames...",
                 len(self.grabbers))

        # Main inference loop
        while self._running:
            try:
                packet = self.frame_queue.get(timeout=5)
            except Exception:
                continue

            try:
                with self._model_lock:
                    results = self.model.predict(
                        packet.frame,
                        imgsz=INPUT_SIZE,
                        conf=CONFIDENCE_THRESHOLD,
                        verbose=False,
                    )

                if results and len(results) > 0:
                    self._process_results(packet, results[0])

            except Exception as e:
                log.error("Inference error on %s: %s", packet.camera_id, e)

    def _process_results(self, packet: FramePacket, result):
        """Extract detections and dispatch to callbacks with the original frame."""
        if result.boxes is None or len(result.boxes) == 0:
            return

        for box in result.boxes:
            cls_id = int(box.cls[0])
            cls_name = result.names[cls_id]
            conf = float(box.conf[0])
            x1, y1, x2, y2 = box.xyxy[0].tolist()

            detection = {
                "camera_id": packet.camera_id,
                "class": cls_name,
                "confidence": conf,
                "bbox": [x1, y1, x2, y2],
                "bottom_center": [(x1 + x2) / 2, y2],
                "timestamp": packet.timestamp,
                "frame_number": packet.frame_number,
                "_frame": packet.frame,
            }

            for callback in self.results_callbacks:
                try:
                    callback(detection, frame=packet.frame)
                except TypeError:
                    try:
                        callback(detection)
                    except Exception as e:
                        log.error("Detection callback error: %s", e)
                except Exception as e:
                    log.error("Detection callback error: %s", e)

    def stop(self):
        """Stop all grabbers and inference loop."""
        self._running = False
        for g in self.grabbers:
            g.stop()

    def get_stats(self) -> dict:
        """Return pipeline statistics for monitoring."""
        return {
            "model_path": getattr(self, "model_path", PT_PATH),
            "cameras_total": len(self.cameras),
            "cameras_connected": sum(1 for g in self.grabbers if g.connected),
            "queue_size": self.frame_queue.qsize(),
            "total_frames": sum(g.frame_count for g in self.grabbers),
        }
