"""Tests for zone_engine.py — virtual fence polygon engine."""

import json
import sys
import os
import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from zone_engine import ZoneEngine


class TestZoneEngineLoading:
    """Tests for zone config loading and error handling."""

    def test_load_valid_config(self, zones_config):
        engine = ZoneEngine(zones_config)
        assert len(engine.zones) == 2
        assert len(engine.polygons) == 4

    def test_load_missing_file(self, tmp_path):
        engine = ZoneEngine(str(tmp_path / "nonexistent.json"))
        assert len(engine.zones) == 0
        assert len(engine.polygons) == 0

    def test_load_invalid_json(self, tmp_path):
        bad_file = tmp_path / "bad.json"
        bad_file.write_text("{invalid json")
        engine = ZoneEngine(str(bad_file))
        assert len(engine.zones) == 0

    def test_reload_updates_zones(self, tmp_path):
        config_path = tmp_path / "zones.json"
        config_path.write_text(json.dumps({
            "cameras": [{"camera_id": "cam-01", "zones": []}]
        }))
        engine = ZoneEngine(str(config_path))
        assert len(engine.polygons) == 0

        config_path.write_text(json.dumps({
            "cameras": [{
                "camera_id": "cam-01",
                "zones": [{
                    "id": "z1", "name": "Zone 1", "type": "intrusion",
                    "priority": "high", "target_classes": ["person"],
                    "polygon": [[0, 0], [100, 0], [100, 100], [0, 100]],
                }]
            }]
        }))
        engine.reload()
        assert len(engine.polygons) == 1

    def test_skip_disabled_zones(self, tmp_path):
        config_path = tmp_path / "zones.json"
        config_path.write_text(json.dumps({
            "cameras": [{
                "camera_id": "cam-01",
                "zones": [{
                    "id": "z1", "name": "Disabled Zone", "type": "intrusion",
                    "priority": "high", "target_classes": ["person"],
                    "polygon": [[0, 0], [100, 0], [100, 100]], "enabled": False,
                }]
            }]
        }))
        engine = ZoneEngine(str(config_path))
        assert len(engine.polygons) == 0

    def test_skip_polygon_with_fewer_than_3_points(self, tmp_path):
        config_path = tmp_path / "zones.json"
        config_path.write_text(json.dumps({
            "cameras": [{
                "camera_id": "cam-01",
                "zones": [{
                    "id": "z1", "name": "Bad Zone", "type": "intrusion",
                    "priority": "high", "target_classes": ["person"],
                    "polygon": [[0, 0], [100, 0]],
                }]
            }]
        }))
        engine = ZoneEngine(str(config_path))
        assert len(engine.polygons) == 0


class TestIntrusionDetection:
    """Tests for intrusion/zero_tolerance/hazard zone checks."""

    def test_person_inside_intrusion_zone(self, zones_config, make_detection):
        engine = ZoneEngine(zones_config)
        det = make_detection(bbox=[150, 100, 250, 450])
        result = engine.check_detection(det)
        assert result is not None
        assert result["zone_type"] in ("intrusion", "zero_tolerance")

    def test_person_outside_all_zones(self, zones_config, make_detection):
        engine = ZoneEngine(zones_config)
        det = make_detection(camera_id="cam-99", bbox=[100, 100, 200, 400])
        result = engine.check_detection(det)
        assert result is None

    def test_wrong_class_ignored(self, zones_config, make_detection):
        engine = ZoneEngine(zones_config)
        det = make_detection(cls="dog", bbox=[150, 100, 250, 450])
        result = engine.check_detection(det)
        assert result is None

    def test_person_inside_pond_zone(self, zones_config, make_detection):
        engine = ZoneEngine(zones_config)
        det = make_detection(bbox=[150, 100, 250, 250],
                             bottom_center=[200, 250])
        result = engine.check_detection(det)
        assert result is not None
        assert result["zone_type"] == "zero_tolerance"
        assert result["priority"] == "critical"


class TestLivestockContainment:
    """Tests for livestock containment zone (alert when OUTSIDE)."""

    def test_cow_inside_pen_no_alert(self, zones_config, make_detection):
        engine = ZoneEngine(zones_config)
        det = make_detection(camera_id="cam-02", cls="cow",
                             bbox=[100, 100, 200, 300],
                             bottom_center=[150, 300])
        result = engine.check_detection(det)
        assert result is None or result["zone_type"] != "livestock_containment"

    def test_cow_outside_pen_triggers_alert(self, zones_config, make_detection):
        engine = ZoneEngine(zones_config)
        det = make_detection(camera_id="cam-02", cls="cow",
                             bbox=[450, 10, 600, 40],
                             bottom_center=[525, 40])
        result = engine.check_detection(det)
        assert result is not None
        assert result["zone_type"] == "livestock_containment"
        assert result["priority"] == "warning"


class TestMultiZonePriority:
    """Tests for G8: highest priority zone returned when overlapping."""

    def test_overlapping_zones_returns_highest_priority(self, zones_config, make_detection):
        engine = ZoneEngine(zones_config)
        det = make_detection(bbox=[150, 100, 250, 250],
                             bottom_center=[200, 250])
        result = engine.check_detection(det)
        assert result is not None
        assert result["priority"] == "critical"
        assert result["zone_type"] == "zero_tolerance"


class TestGetters:
    """Tests for utility methods."""

    def test_get_zones_for_camera(self, zones_config):
        engine = ZoneEngine(zones_config)
        zones = engine.get_zones_for_camera("cam-01")
        assert len(zones) == 2

    def test_get_zones_for_unknown_camera(self, zones_config):
        engine = ZoneEngine(zones_config)
        zones = engine.get_zones_for_camera("cam-99")
        assert len(zones) == 0

    def test_get_all_zones(self, zones_config):
        engine = ZoneEngine(zones_config)
        all_zones = engine.get_all_zones()
        assert "cam-01" in all_zones
        assert "cam-02" in all_zones
