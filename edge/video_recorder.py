#!/usr/bin/env python3
"""
video_recorder.py — Continuous RTSP Recording via ffmpeg
=========================================================
Records each camera's RTSP stream continuously using ffmpeg subprocess
with ``-c:v copy`` (zero CPU — copies H.264 directly).
Splits into configurable-length MP4 segments.

Directory layout::

    /data/video/cam-01/2026-03-22/08/cam-01_08-00.mp4
                                      cam-01_08-10.mp4
"""

import json
import logging
import os
import signal
import subprocess
import threading
import time
from dataclasses import dataclass
from typing import Dict, Optional

log = logging.getLogger("video_recorder")

STORAGE_CONFIG_PATH = os.getenv(
    "VIDEO_STORAGE_CONFIG",
    os.path.join(os.getenv("CONFIG_DIR", "/app/config"), "storage.json"),
)


@dataclass
class RecordingConfig:
    camera_id: str
    rtsp_url: str
    output_dir: str
    segment_seconds: int = 600


class CameraRecorder:
    """Manages a single ffmpeg recording subprocess for one camera."""

    def __init__(self, config: RecordingConfig):
        self.config = config
        self._proc: Optional[subprocess.Popen] = None
        self._thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()
        self._restart_count = 0

    def start(self):
        self._stop_event.clear()
        self._thread = threading.Thread(
            target=self._run_loop, daemon=True,
            name=f"rec-{self.config.camera_id}",
        )
        self._thread.start()
        log.info("[%s] Recorder started → %s",
                 self.config.camera_id, self.config.output_dir)

    def stop(self):
        self._stop_event.set()
        if self._proc and self._proc.poll() is None:
            try:
                self._proc.send_signal(signal.SIGINT)
                self._proc.wait(timeout=10)
            except Exception:
                self._proc.kill()
        log.info("[%s] Recorder stopped", self.config.camera_id)

    def is_alive(self) -> bool:
        return bool(self._thread and self._thread.is_alive())

    def _run_loop(self):
        backoff = 5
        while not self._stop_event.is_set():
            self._ensure_output_dir()
            try:
                self._spawn_ffmpeg()
                backoff = 5
            except Exception as e:
                log.error("[%s] ffmpeg error: %s", self.config.camera_id, e)

            if self._stop_event.is_set():
                break
            self._restart_count += 1
            log.warning("[%s] ffmpeg exited, restarting in %ds (restart #%d)",
                        self.config.camera_id, backoff, self._restart_count)
            self._stop_event.wait(timeout=backoff)
            backoff = min(backoff * 2, 60)

    def _ensure_output_dir(self):
        os.makedirs(self.config.output_dir, exist_ok=True)

    def _spawn_ffmpeg(self):
        seg = self.config.segment_seconds
        out_pattern = os.path.join(
            self.config.output_dir,
            "%Y-%m-%d", "%H",
            f"{self.config.camera_id}_%H-%M-%S.mp4",
        )
        cmd = [
            "ffmpeg", "-hide_banner", "-loglevel", "warning",
            "-rtsp_transport", "tcp",
            "-i", self.config.rtsp_url,
            "-c:v", "copy", "-c:a", "copy",
            "-f", "segment",
            "-segment_time", str(seg),
            "-segment_format", "mp4",
            "-strftime", "1",
            "-reset_timestamps", "1",
            out_pattern,
        ]
        log.info("[%s] ffmpeg cmd: %s", self.config.camera_id,
                 " ".join(c if " " not in c else f'"{c}"' for c in cmd))

        date_dir = os.path.join(
            self.config.output_dir,
            time.strftime("%Y-%m-%d"),
            time.strftime("%H"),
        )
        os.makedirs(date_dir, exist_ok=True)

        self._proc = subprocess.Popen(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
        while not self._stop_event.is_set():
            retcode = self._proc.poll()
            if retcode is not None:
                stderr = self._proc.stderr.read().decode(errors="replace") if self._proc.stderr else ""
                if stderr.strip():
                    log.warning("[%s] ffmpeg stderr: %s",
                                self.config.camera_id, stderr[-500:])
                break
            self._stop_event.wait(timeout=5)

            date_dir = os.path.join(
                self.config.output_dir,
                time.strftime("%Y-%m-%d"),
                time.strftime("%H"),
            )
            os.makedirs(date_dir, exist_ok=True)


class VideoRecorder:
    """
    Top-level recorder that manages one CameraRecorder per camera.

    Reads camera list from the caller (farm_edge_node) and storage paths
    from ``storage.json``.
    """

    def __init__(self, cameras: list, storage_config: Optional[dict] = None):
        self._cameras = cameras
        self._storage = storage_config or self._load_storage_config()
        self._recorders: Dict[str, CameraRecorder] = {}
        self._segment_seconds = int(self._storage.get("segment_seconds", 600))

    @staticmethod
    def _load_storage_config() -> dict:
        try:
            with open(STORAGE_CONFIG_PATH) as f:
                return json.load(f)
        except FileNotFoundError:
            log.warning("storage.json not found at %s, using defaults",
                        STORAGE_CONFIG_PATH)
            return {"ssd_pools": [{"path": "/data/video", "cameras": []}]}

    def _resolve_output_dir(self, camera_id: str) -> str:
        for pool in self._storage.get("ssd_pools", []):
            if camera_id in pool.get("cameras", []):
                return os.path.join(pool["path"], camera_id)
        base = self._storage.get("ssd_pools", [{}])[0].get("path", "/data/video")
        return os.path.join(base, camera_id)

    def _recording_url(self, cam) -> str:
        """Prefer stream1 (full quality) for recording if available."""
        if hasattr(cam, "stream1_url") and cam.stream1_url:
            return cam.stream1_url
        url = cam.rtsp_url if hasattr(cam, "rtsp_url") else str(cam)
        return url.replace("/stream2", "/stream1")

    def start(self):
        for cam in self._cameras:
            cam_id = cam.id if hasattr(cam, "id") else str(cam)
            if not getattr(cam, "enabled", True):
                continue
            rec_url = self._recording_url(cam)
            out_dir = self._resolve_output_dir(cam_id)
            config = RecordingConfig(
                camera_id=cam_id,
                rtsp_url=rec_url,
                output_dir=out_dir,
                segment_seconds=self._segment_seconds,
            )
            recorder = CameraRecorder(config)
            recorder.start()
            self._recorders[cam_id] = recorder
        log.info("VideoRecorder started for %d cameras", len(self._recorders))

    def stop(self):
        for cam_id, rec in self._recorders.items():
            rec.stop()
        log.info("VideoRecorder stopped all recorders")

    def get_base_path(self, camera_id: str) -> str:
        return self._resolve_output_dir(camera_id)

    def get_status(self) -> dict:
        return {
            "cameras": {
                cam_id: {
                    "alive": rec.is_alive(),
                    "restarts": rec._restart_count,
                    "output_dir": rec.config.output_dir,
                }
                for cam_id, rec in self._recorders.items()
            },
            "segment_seconds": self._segment_seconds,
        }
