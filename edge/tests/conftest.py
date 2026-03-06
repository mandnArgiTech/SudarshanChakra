"""Shared test fixtures for edge AI pipeline tests."""

import json
import os
import tempfile
import pytest


@pytest.fixture
def zones_config(tmp_path):
    """Create a temporary zones.json with test zones."""
    config = {
        "cameras": [
            {
                "camera_id": "cam-01",
                "zones": [
                    {
                        "id": "zone-perimeter",
                        "name": "Farm Perimeter",
                        "type": "intrusion",
                        "priority": "high",
                        "target_classes": ["person"],
                        "polygon": [[0, 0], [640, 0], [640, 480], [0, 480]],
                        "enabled": True,
                    },
                    {
                        "id": "zone-pond",
                        "name": "Pond Danger Zone",
                        "type": "zero_tolerance",
                        "priority": "critical",
                        "target_classes": ["person", "child"],
                        "polygon": [[100, 100], [300, 100], [300, 300], [100, 300]],
                        "enabled": True,
                    },
                ],
            },
            {
                "camera_id": "cam-02",
                "zones": [
                    {
                        "id": "zone-pen",
                        "name": "Cattle Pen",
                        "type": "livestock_containment",
                        "priority": "warning",
                        "target_classes": ["cow"],
                        "polygon": [[50, 50], [400, 50], [400, 400], [50, 400]],
                        "enabled": True,
                    },
                    {
                        "id": "zone-hazard",
                        "name": "Snake Zone",
                        "type": "hazard",
                        "priority": "high",
                        "target_classes": ["snake", "scorpion"],
                        "polygon": [[0, 200], [640, 200], [640, 480], [0, 480]],
                        "enabled": True,
                    },
                ],
            },
        ]
    }
    config_path = tmp_path / "zones.json"
    config_path.write_text(json.dumps(config))
    return str(config_path)


@pytest.fixture
def make_detection():
    """Factory for creating detection dicts."""
    def _make(camera_id="cam-01", cls="person", confidence=0.85,
              bbox=None, bottom_center=None):
        if bbox is None:
            bbox = [100.0, 100.0, 200.0, 400.0]
        if bottom_center is None:
            bottom_center = [(bbox[0] + bbox[2]) / 2, bbox[3]]
        return {
            "camera_id": camera_id,
            "class": cls,
            "confidence": confidence,
            "bbox": bbox,
            "bottom_center": bottom_center,
            "timestamp": 1000000.0,
            "frame_number": 1,
        }
    return _make
