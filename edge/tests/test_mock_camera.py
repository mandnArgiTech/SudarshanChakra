"""Tests for mock_camera.py — synthetic frames and MockInferencePipeline."""

import json
import os
import tempfile
import threading
import time

from dataclasses import dataclass

import numpy as np
import pytest
import cv2

from mock_camera import MockCameraGrabber, MockInferencePipeline


@dataclass
class _Cam:
    id: str
    name: str
    rtsp_url: str = ""
    fps: float = 2.0
    enabled: bool = True


def test_mock_camera_generates_frame():
    g = MockCameraGrabber("cam-01", "Test", fps=10.0)
    frame = g.grab_frame()
    assert frame is not None
    assert frame.shape == (480, 640, 3)
    assert frame.dtype == np.uint8


def test_mock_camera_cycles_scenarios():
    g = MockCameraGrabber("cam-01", "Test", fps=100.0)
    classes_seen = []
    for _ in range(10):
        g.grab_frame()
        sc = g.get_current_scenario()
        classes_seen.append(sc["class"] if sc else None)
    assert classes_seen[0] == "person"
    assert classes_seen[3] == "snake"
    # Index 7 and 8 in first 10 grabs: frame_count 1-10 maps to scenarios 1-10 mod 10
    # MOCK_SCENARIOS index 7 is dog, 8 is None, 9 is None
    assert classes_seen[7] == "dog"
    assert classes_seen[8] is None


def test_mock_camera_frame_count_increments():
    g = MockCameraGrabber("cam-01", "Test", fps=10.0)
    for _ in range(5):
        g.grab_frame()
    assert g.frame_count == 5


def test_mock_camera_video_file_mode(tmp_path):
    path = str(tmp_path / "tiny.mp4")
    w, h = 640, 480
    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    out = cv2.VideoWriter(path, fourcc, 5.0, (w, h))
    for i in range(3):
        frame = np.full((h, w, 3), (i * 50, 0, 0), dtype=np.uint8)
        out.write(frame)
    out.release()

    g = MockCameraGrabber("cam-01", "Vid", fps=1.0, video_path=path)
    f1 = g.grab_frame()
    f2 = g.grab_frame()
    f3 = g.grab_frame()
    f4 = g.grab_frame()
    assert f1 is not None and f4 is not None
    assert np.array_equal(f1, f4)


def test_mock_pipeline_creates_detections():
    cams = [
        _Cam(id="c1", name="A", rtsp_url="rtsp://x", fps=1.0),
        _Cam(id="c2", name="B", rtsp_url="rtsp://y", fps=1.0),
    ]
    pipe = MockInferencePipeline(cams, detection_interval=0.3)
    received = []

    def cb(d):
        received.append(d)

    pipe.results_callbacks.append(cb)
    t = threading.Thread(target=pipe.start, daemon=True)
    t.start()
    time.sleep(1.2)
    pipe.stop()
    t.join(timeout=2.0)
    assert len(received) >= 1
    d = received[0]
    assert "camera_id" in d and "class" in d and "confidence" in d
    assert "bbox" in d and "bottom_center" in d and "timestamp" in d
    assert d.get("mock") is True


def test_mock_pipeline_detection_interval():
    cams = [_Cam(id="c1", name="A", rtsp_url="rtsp://x", fps=1.0)]
    pipe = MockInferencePipeline(cams, detection_interval=2.0)
    received = []
    pipe.results_callbacks.append(received.append)
    t = threading.Thread(target=pipe.start, daemon=True)
    t.start()
    time.sleep(7.0)
    pipe.stop()
    t.join(timeout=2.0)
    assert 2 <= len(received) <= 5


def test_mock_pipeline_get_stats():
    cams = [
        _Cam(id="c1", name="A", rtsp_url="rtsp://x", fps=1.0),
        _Cam(id="c2", name="B", rtsp_url="rtsp://y", fps=1.0),
    ]
    pipe = MockInferencePipeline(cams, detection_interval=0.5)
    stats = pipe.get_stats()
    assert stats["mode"] == "MOCK"
    assert stats["cameras_total"] == 2
    assert stats["cameras_connected"] == 2
    assert "scenario_index" in stats
