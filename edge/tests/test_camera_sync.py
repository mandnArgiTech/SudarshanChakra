"""camera_sync mapping (no HTTP)."""

from __future__ import annotations

from camera_sync import build_cameras_document, map_api_camera_to_edge


def test_map_rtsp_camera():
    row = map_api_camera_to_edge(
        {
            "id": "c1",
            "name": "Gate",
            "rtspUrl": "rtsp://u:p@10.0.0.1/stream2",
            "fpsTarget": 2.5,
            "enabled": True,
            "sourceType": "rtsp",
        }
    )
    assert row["id"] == "c1"
    assert row["rtsp_url"].startswith("rtsp://")
    assert row["source_type"] == "rtsp"
    assert row["fps"] == 2.5


def test_map_file_source():
    row = map_api_camera_to_edge(
        {
            "id": "c2",
            "name": "Bench",
            "rtspUrl": "",
            "sourceUrl": "/data/clips/test.mp4",
            "fpsTarget": 2.0,
            "enabled": True,
            "sourceType": "file",
        }
    )
    assert row["source_type"] == "file"
    assert row["source_url"] == "/data/clips/test.mp4"


def test_build_document_skips_empty_id():
    doc = build_cameras_document([{"id": "", "name": "x", "rtspUrl": "rtsp://a"}])
    assert doc["cameras"] == []
