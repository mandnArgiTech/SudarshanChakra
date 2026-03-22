"""Flask edge_gui routes: recordings listing and alert clips."""

from __future__ import annotations

import json
from pathlib import Path
from types import SimpleNamespace

import pytest

from edge_gui import create_app
from zone_engine import ZoneEngine


@pytest.fixture()
def zones_path(tmp_path: Path) -> Path:
    p = tmp_path / "zones.json"
    p.write_text(json.dumps({"cameras": []}), encoding="utf-8")
    return p


@pytest.fixture()
def app(zones_path: Path, monkeypatch, tmp_path: Path):
    monkeypatch.setenv("VIDEO_BASE_PATH", str(tmp_path / "video"))
    ze = ZoneEngine(str(zones_path))
    cams = [SimpleNamespace(id="cam-01", name="Test")]

    class FakeRecorder:
        def __init__(self, root: Path):
            self._root = root

        def get_base_path(self, camera_id: str) -> str:
            return str(self._root / camera_id)

    rec_root = tmp_path / "rec"
    cam_base = rec_root / "cam-01" / "2026-03-01" / "14"
    cam_base.mkdir(parents=True)
    (cam_base / "seg01.mp4").write_bytes(b"fake")

    flask_app = create_app(
        ze,
        cams,
        str(tmp_path / "cfg"),
        video_recorder=FakeRecorder(rec_root),
    )
    flask_app.config["TESTING"] = True
    return flask_app


def test_list_recordings(app):
    client = app.test_client()
    rv = client.get("/api/recordings/cam-01")
    assert rv.status_code == 200
    data = rv.get_json()
    assert data["camera_id"] == "cam-01"
    assert len(data["dates"]) >= 1
    assert data["dates"][0]["date"] == "2026-03-01"


def test_list_recordings_date_filter(app):
    client = app.test_client()
    rv = client.get("/api/recordings/cam-01?date=2099-12-31")
    assert rv.status_code == 200
    assert rv.get_json()["dates"] == []


def test_clip_404(app, tmp_path: Path, monkeypatch):
    monkeypatch.setenv("VIDEO_BASE_PATH", str(tmp_path / "video"))
    clips = tmp_path / "video" / "clips"
    clips.mkdir(parents=True, exist_ok=True)
    client = app.test_client()
    rv = client.get("/api/clips/missing-id.mp4")
    assert rv.status_code == 404


def test_clip_serves_file(app, tmp_path: Path, monkeypatch):
    vbase = tmp_path / "video"
    clips = vbase / "clips"
    clips.mkdir(parents=True, exist_ok=True)
    alert_id = "550e8400-e29b-41d4-a716-446655440000"
    fp = clips / f"{alert_id}.mp4"
    fp.write_bytes(b"%ftypmp42")
    monkeypatch.setenv("VIDEO_BASE_PATH", str(vbase))

    client = app.test_client()
    rv = client.get(f"/api/clips/{alert_id}.mp4")
    assert rv.status_code == 200
    assert rv.data.startswith(b"%ftyp")
