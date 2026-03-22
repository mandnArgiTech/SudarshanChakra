#!/usr/bin/env python3
"""
http_grabber.py — HTTP JPEG/MJPEG Stream Grabber
===================================================
Reads frames from HTTP JPEG snapshots or MJPEG streams.
Same interface as ``CameraGrabber``.
"""

import cv2
import logging
import numpy as np
import threading
import time
import urllib.request
from queue import Queue

log = logging.getLogger("http_grabber")


class HttpGrabber(threading.Thread):
    """
    Grabs frames from an HTTP endpoint:
    - Single JPEG: polls at configured FPS
    - MJPEG stream: reads multipart boundary
    """

    def __init__(self, camera_id: str, url: str, queue: Queue,
                 fps: float = 2.0):
        super().__init__(daemon=True, name=f"http-{camera_id}")
        self.camera_id = camera_id
        self.url = url
        self.queue = queue
        self.fps = fps
        self._stop_event = threading.Event()
        self.frame_count = 0
        self.connected = False
        self.last_frame_time = 0.0

    def run(self):
        from pipeline import FramePacket

        interval = 1.0 / self.fps
        backoff = 1.0

        while not self._stop_event.is_set():
            try:
                frame = self._fetch_jpeg()
                if frame is None:
                    raise ValueError("Empty frame")

                self.connected = True
                backoff = 1.0
                self.frame_count += 1
                self.last_frame_time = time.time()

                packet = FramePacket(
                    camera_id=self.camera_id,
                    frame=frame,
                    timestamp=self.last_frame_time,
                    frame_number=self.frame_count,
                )
                if self.queue.full():
                    try:
                        self.queue.get_nowait()
                    except Exception:
                        pass
                self.queue.put(packet)

                try:
                    from edge_gui import update_snapshot
                    update_snapshot(self.camera_id, frame)
                except ImportError:
                    pass

            except Exception as e:
                self.connected = False
                log.warning("[%s] HTTP grab failed: %s. Retry in %.0fs...",
                            self.camera_id, e, backoff)
                self._stop_event.wait(timeout=min(backoff, 30))
                backoff *= 2
                continue

            self._stop_event.wait(timeout=interval)

    def _fetch_jpeg(self):
        """Fetch a single JPEG from the URL and decode it."""
        req = urllib.request.Request(self.url)
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = resp.read()
        arr = np.frombuffer(data, dtype=np.uint8)
        return cv2.imdecode(arr, cv2.IMREAD_COLOR)

    def stop(self):
        self._stop_event.set()
