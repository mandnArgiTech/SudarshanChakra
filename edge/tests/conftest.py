"""Shared pytest fixtures for edge tests."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any, Dict

import pytest

_ROOT = Path(__file__).resolve().parent.parent
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

# Shared zones.json shape: 2 cameras, 4 enabled polygons (see test_zone_engine expectations).
ZONES_FIXTURE: Dict[str, Any] = {
    "cameras": [
        {
            "camera_id": "cam-01",
            "zones": [
                {
                    "id": "z_main",
                    "name": "Main perimeter",
                    "type": "intrusion",
                    "priority": "high",
                    "target_classes": ["person", "child"],
                    "polygon": [[0, 0], [700, 0], [700, 500], [0, 500]],
                },
                {
                    "id": "z_pond",
                    "name": "Pond zero tolerance",
                    "type": "zero_tolerance",
                    "priority": "critical",
                    "target_classes": ["person", "child"],
                    "polygon": [[100, 100], [300, 100], [300, 300], [100, 300]],
                },
            ],
        },
        {
            "camera_id": "cam-02",
            "zones": [
                {
                    "id": "z_pen",
                    "name": "Cow pen",
                    "type": "livestock_containment",
                    "priority": "warning",
                    "target_classes": ["cow"],
                    "polygon": [[50, 50], [350, 50], [350, 350], [50, 350]],
                },
                {
                    "id": "z_extra",
                    "name": "Aux",
                    "type": "intrusion",
                    "priority": "high",
                    "target_classes": ["person"],
                    "polygon": [[0, 0], [40, 0], [40, 40], [0, 40]],
                },
            ],
        },
    ],
}


@pytest.fixture(autouse=True)
def _edge_writable_media_dirs(monkeypatch, tmp_path):
    """Avoid AlertDecisionEngine mkdir /data/video/... during unit tests."""
    video_root = tmp_path / "video"
    video_root.mkdir()
    snaps = tmp_path / "snapshots"
    snaps.mkdir()
    clips = video_root / "clips"
    clips.mkdir()
    monkeypatch.setenv("VIDEO_BASE_PATH", str(video_root))
    monkeypatch.setenv("SNAPSHOT_DIR", str(snaps))


@pytest.fixture
def zones_config(tmp_path: Path) -> str:
    p = tmp_path / "zones.json"
    p.write_text(json.dumps(ZONES_FIXTURE), encoding="utf-8")
    return str(p)


@pytest.fixture
def make_detection():
    """Factory for detection dicts used by zone_engine and alert_engine."""

    def _make(
        camera_id: str = "cam-01",
        cls: str = "person",
        confidence: float = 0.95,
        bbox: list | None = None,
        bottom_center: list | None = None,
        **_: Any,
    ) -> dict:
        bb = bbox if bbox is not None else [150, 100, 250, 450]
        if bottom_center is None:
            bc = [(bb[0] + bb[2]) / 2.0, float(bb[3])]
        else:
            bc = [float(bottom_center[0]), float(bottom_center[1])]
        return {
            "camera_id": camera_id,
            "class": cls,
            "confidence": confidence,
            "bbox": bb,
            "bottom_center": bc,
        }

    return _make
