#!/usr/bin/env python3
"""
file_grabber.py — Video File Frame Grabber
============================================
Reads frames from local MP4/AVI files for offline analysis or
training data review. Same interface as ``CameraGrabber``.
"""

import cv2
import logging
import threading
import time
from queue import Queue

log = logging.getLogger("file_grabber")


class FileGrabber(threading.Thread):
    """
    Reads frames from a video file at the configured FPS.

    Supports ``loop=True`` to replay continuously (useful for testing).
    """

    def __init__(self, camera_id: str, file_path: str, queue: Queue,
                 fps: float = 5.0, loop: bool = False):
        super().__init__(daemon=True, name=f"file-{camera_id}")
        self.camera_id = camera_id
        self.file_path = file_path
        self.queue = queue
        self.fps = fps
        self.loop = loop
        self._stop_event = threading.Event()
        self.frame_count = 0
        self.connected = False
        self.last_frame_time = 0.0

    def run(self):
        from pipeline import FramePacket

        interval = 1.0 / self.fps

        while not self._stop_event.is_set():
            cap = cv2.VideoCapture(self.file_path)
            if not cap.isOpened():
                log.error("[%s] Cannot open file: %s", self.camera_id, self.file_path)
                self.connected = False
                break

            self.connected = True
            log.info("[%s] File source: %s at %.1f FPS (loop=%s)",
                     self.camera_id, self.file_path, self.fps, self.loop)

            while not self._stop_event.is_set():
                ret, frame = cap.read()
                if not ret:
                    if self.loop:
                        cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                        continue
                    else:
                        break

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

                time.sleep(interval)

            cap.release()

            if not self.loop:
                break

        self.connected = False
        log.info("[%s] File grabber finished (%d frames)", self.camera_id, self.frame_count)

    def stop(self):
        self._stop_event.set()
