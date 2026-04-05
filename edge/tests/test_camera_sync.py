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


def test_run_camera_sync_skips_write_when_unchanged(monkeypatch, tmp_path):
    import camera_sync as cs

    monkeypatch.setenv("DEVICE_SERVICE_URL", "http://example/api/v1")
    monkeypatch.setenv("CAMERA_SYNC_TOKEN", "tok")
    monkeypatch.setenv("EDGE_NODE_ID", "n1")
    monkeypatch.setenv("CONFIG_DIR", str(tmp_path))

    api_cam = {
        "id": "c1",
        "name": "Gate",
        "rtspUrl": "rtsp://u:p@10/stream",
        "fpsTarget": 2.0,
        "enabled": True,
        "sourceType": "rtsp",
    }

    def fake_fetch(base, node, token):
        assert base == "http://example/api/v1"
        assert node == "n1"
        assert token == "tok"
        return [api_cam]

    monkeypatch.setattr(cs, "fetch_cameras", fake_fetch)

    assert cs.run_camera_sync() is True
    path = tmp_path / "cameras.json"
    mtime1 = path.stat().st_mtime_ns
    assert cs.run_camera_sync() is False
    assert path.stat().st_mtime_ns == mtime1
