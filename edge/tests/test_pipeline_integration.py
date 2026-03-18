"""
Integration tests: detection → zone_engine → filter → LoRa fusion → MQTT alert chain.
"""

import json
import os
import tempfile
import time
from unittest.mock import MagicMock

import numpy as np
import pytest

from zone_engine import ZoneEngine
from alert_engine import AlertDecisionEngine
from mock_lora import MockLoRaReceiver


def _zones_path(tmp_path):
    cfg = {
        "cameras": [
            {
                "camera_id": "cam-01",
                "zones": [
                    {
                        "id": "z-perim",
                        "name": "Perimeter",
                        "type": "intrusion",
                        "priority": "high",
                        "target_classes": ["person"],
                        "polygon": [[0, 0], [640, 0], [640, 480], [0, 480]],
                        "enabled": True,
                    },
                    {
                        "id": "z-pond",
                        "name": "Pond",
                        "type": "zero_tolerance",
                        "priority": "critical",
                        "target_classes": ["person", "child"],
                        "polygon": [[100, 100], [500, 100], [500, 400], [100, 400]],
                        "enabled": True,
                    },
                ],
            },
            {
                "camera_id": "cam-02",
                "zones": [
                    {
                        "id": "z-pen",
                        "name": "Pen",
                        "type": "livestock_containment",
                        "priority": "warning",
                        "target_classes": ["cow"],
                        "polygon": [[200, 200], [400, 200], [400, 400], [200, 400]],
                        "enabled": True,
                    },
                ],
            },
            {
                "camera_id": "cam-empty",
                "zones": [],
            },
        ]
    }
    p = tmp_path / "zones.json"
    p.write_text(json.dumps(cfg))
    return str(p)


class _Mqtt:
    def __init__(self):
        self.published = []

    def publish(self, topic, payload, qos=0):
        self.published.append((topic, payload.decode() if isinstance(payload, bytes) else payload))
        return type("R", (), {"rc": 0})()


def _det(cam, cls, conf, bc, bbox=None):
    if bbox is None:
        bx, by = bc
        bbox = [bc[0] - 40, bc[1] - 100, bc[0] + 40, bc[1]]
    return {
        "camera_id": cam,
        "class": cls,
        "confidence": conf,
        "bbox": bbox,
        "bottom_center": list(bc),
        "timestamp": time.time(),
        "frame_number": 1,
    }


@pytest.fixture
def engine_factory(tmp_path, monkeypatch):
    monkeypatch.setenv("SNAPSHOT_DIR", str(tmp_path / "snap"))
    monkeypatch.setenv("ALERT_DEDUP_SECONDS", "2")

    def _make(worker_present=False):
        zp = _zones_path(tmp_path)
        ze = ZoneEngine(zp)
        tags = tmp_path / "tags.json"
        tags.write_text(json.dumps({"tags": ["TAG-W001"]}))
        lora = MockLoRaReceiver(
            authorized_tags_path=str(tags),
            worker_present=worker_present,
            beacon_interval=0.3,
        )
        lora.start()
        time.sleep(0.8)
        mqtt = _Mqtt()
        eng = AlertDecisionEngine(ze, lora, mqtt, "node-test")
        return eng, mqtt, lora

    yield _make
    # caller must stop lora


def test_chain_person_intrusion_alert(engine_factory):
    eng, mqtt, lora = engine_factory(worker_present=False)
    try:
        eng.process_detection(_det("cam-01", "person", 0.9, (320, 240)))
        assert eng.stats["alerts_published"] == 1
        assert eng.stats["zone_violations"] == 1
        topics = [t for t, _ in mqtt.published]
        assert any("farm/alerts" in t for t in topics)
    finally:
        lora.stop()
        lora.join(timeout=2.0)


def test_chain_no_zone_no_alert(engine_factory):
    eng, mqtt, lora = engine_factory(worker_present=False)
    try:
        eng.process_detection(_det("cam-empty", "person", 0.9, (100, 100)))
        assert eng.stats["alerts_published"] == 0
        assert eng.stats["zone_violations"] == 0
    finally:
        lora.stop()
        lora.join(timeout=2.0)


def test_chain_dog_filtered(engine_factory):
    eng, mqtt, lora = engine_factory(worker_present=False)
    try:
        eng.process_detection(_det("cam-01", "dog", 0.9, (320, 240)))
        assert eng.stats["filtered_out"] >= 1
        assert eng.stats["alerts_published"] == 0
    finally:
        lora.stop()
        lora.join(timeout=2.0)


def test_chain_low_confidence_filtered(engine_factory):
    eng, mqtt, lora = engine_factory(worker_present=False)
    try:
        eng.process_detection(_det("cam-01", "person", 0.05, (320, 240)))
        assert eng.stats["filtered_out"] >= 1
        assert eng.stats["alerts_published"] == 0
    finally:
        lora.stop()
        lora.join(timeout=2.0)


def test_chain_cow_livestock_containment(engine_factory):
    eng, mqtt, lora = engine_factory(worker_present=False)
    try:
        eng.process_detection(_det("cam-02", "cow", 0.9, (50, 50)))
        assert eng.stats["zone_violations"] == 1
        assert eng.stats["alerts_published"] == 1
    finally:
        lora.stop()
        lora.join(timeout=2.0)


def test_chain_worker_suppresses_intrusion(engine_factory):
    eng, mqtt, lora = engine_factory(worker_present=True)
    try:
        eng.process_detection(_det("cam-01", "person", 0.9, (30, 400)))
        assert eng.stats["worker_suppressed"] == 1
        assert eng.stats["alerts_published"] == 0
        sup = [p for t, p in mqtt.published if "worker_identified" in t]
        assert len(sup) == 1
    finally:
        lora.stop()
        lora.join(timeout=2.0)


def test_chain_zero_tolerance_not_suppressed_by_worker(engine_factory):
    eng, mqtt, lora = engine_factory(worker_present=True)
    try:
        eng.process_detection(_det("cam-01", "child", 0.85, (250, 300)))
        assert eng.stats["alerts_published"] == 1
        assert eng.stats["worker_suppressed"] == 0
    finally:
        lora.stop()
        lora.join(timeout=2.0)


def test_chain_deduplication(engine_factory):
    eng, mqtt, lora = engine_factory(worker_present=False)
    try:
        d = _det("cam-01", "person", 0.9, (30, 400))
        eng.process_detection(d)
        eng.process_detection(d)
        assert eng.stats["deduplicated"] == 1
        assert eng.stats["alerts_published"] == 1
    finally:
        lora.stop()
        lora.join(timeout=2.0)


def test_chain_snake_hazard(monkeypatch, tmp_path):
    monkeypatch.setenv("SNAPSHOT_DIR", str(tmp_path / "s2"))
    monkeypatch.setenv("ALERT_DEDUP_SECONDS", "60")
    zp = tmp_path / "z2.json"
    zp.write_text(json.dumps({
        "cameras": [{
            "camera_id": "cam-sn",
            "zones": [{
                "id": "hz",
                "name": "H",
                "type": "hazard",
                "priority": "high",
                "target_classes": ["snake"],
                "polygon": [[0, 200], [640, 200], [640, 480], [0, 480]],
                "enabled": True,
            }],
        }],
    }))
    ze = ZoneEngine(str(zp))
    tags = tmp_path / "t2.json"
    tags.write_text(json.dumps({"tags": ["T"]}))
    lora = MockLoRaReceiver(str(tags), worker_present=False, beacon_interval=1.0)
    mqtt = _Mqtt()
    eng = AlertDecisionEngine(ze, lora, mqtt, "n")
    eng.process_detection(_det("cam-sn", "snake", 0.8, (225, 370), bbox=[50, 350, 400, 390]))
    assert eng.stats["alerts_published"] == 1


def test_chain_scorpion_temporal(monkeypatch, tmp_path):
    monkeypatch.setenv("SNAPSHOT_DIR", str(tmp_path / "s3"))
    monkeypatch.setenv("ALERT_DEDUP_SECONDS", "60")
    zp = tmp_path / "z3.json"
    zp.write_text(json.dumps({
        "cameras": [{
            "camera_id": "cam-sc",
            "zones": [{
                "id": "hz",
                "name": "H",
                "type": "hazard",
                "priority": "high",
                "target_classes": ["scorpion"],
                "polygon": [[0, 0], [640, 0], [640, 480], [0, 480]],
                "enabled": True,
            }],
        }],
    }))
    ze = ZoneEngine(str(zp))
    tags = tmp_path / "t3.json"
    tags.write_text(json.dumps({"tags": ["T"]}))
    lora = MockLoRaReceiver(str(tags), worker_present=False, beacon_interval=1.0)
    mqtt = _Mqtt()
    eng = AlertDecisionEngine(ze, lora, mqtt, "n")
    eng.process_detection(_det("cam-sc", "scorpion", 0.8, (200, 350), bbox=[170, 320, 230, 360]))
    assert eng.stats["alerts_published"] == 0
    eng.process_detection(_det("cam-sc", "scorpion", 0.8, (210, 355), bbox=[180, 325, 240, 365]))
    assert eng.stats["alerts_published"] == 1


def test_chain_fire_temporal(monkeypatch, tmp_path):
    monkeypatch.setenv("SNAPSHOT_DIR", str(tmp_path / "s4"))
    zp = tmp_path / "z4.json"
    zp.write_text(json.dumps({
        "cameras": [{
            "camera_id": "cam-f",
            "zones": [{
                "id": "hz",
                "name": "H",
                "type": "hazard",
                "priority": "high",
                "target_classes": ["fire"],
                "polygon": [[0, 0], [640, 0], [640, 480], [0, 480]],
                "enabled": True,
            }],
        }],
    }))
    ze = ZoneEngine(str(zp))
    tags = tmp_path / "t4.json"
    tags.write_text(json.dumps({"tags": ["T"]}))
    lora = MockLoRaReceiver(str(tags), worker_present=False, beacon_interval=1.0)
    mqtt = _Mqtt()
    eng = AlertDecisionEngine(ze, lora, mqtt, "n")
    for i in range(3):
        eng.process_detection(_det("cam-f", "fire", 0.9, (300 + i, 250 + i)))
    assert eng.stats["alerts_published"] == 1


def test_stats_total_detections(engine_factory):
    eng, mqtt, lora = engine_factory(worker_present=False)
    try:
        eng.process_detection(_det("cam-empty", "person", 0.9, (1, 1)))
        assert eng.stats["total_detections"] == 1
    finally:
        lora.stop()
        lora.join(timeout=2.0)


def test_fall_event_publishes_critical(monkeypatch, tmp_path):
    monkeypatch.setenv("SNAPSHOT_DIR", str(tmp_path / "sf"))
    ze = ZoneEngine(_zones_path(tmp_path))
    tags = tmp_path / "tf.json"
    tags.write_text(json.dumps({"tags": ["T"]}))
    lora = MockLoRaReceiver(str(tags), worker_present=False, beacon_interval=1.0)
    mqtt = _Mqtt()
    eng = AlertDecisionEngine(ze, lora, mqtt, "n")
    eng.process_fall_event("TAG-C001", {"TYPE": "FALL"})
    assert eng.stats["alerts_published"] == 1
    crit = [p for t, p in mqtt.published if t == "farm/alerts/critical"]
    assert len(crit) == 1
    body = json.loads(crit[0])
    assert body["detection_class"] == "fall_detected"


def test_snapshot_in_alert_when_frame_provided(engine_factory):
    eng, mqtt, lora = engine_factory(worker_present=False)
    try:
        frame = np.zeros((480, 640, 3), dtype=np.uint8)
        eng.process_detection(
            _det("cam-01", "person", 0.9, (30, 400)),
            frame=frame,
        )
        assert eng.stats["alerts_published"] == 1
        payload = json.loads(mqtt.published[0][1])
        assert "snapshot_url" in payload
        assert ".jpg" in payload["snapshot_url"] or payload["snapshot_url"] == ""
    finally:
        lora.stop()
        lora.join(timeout=2.0)
