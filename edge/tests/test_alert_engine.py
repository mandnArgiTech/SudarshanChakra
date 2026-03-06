"""Tests for alert_engine.py — central alert decision engine."""

import json
import sys
import os
import time
from unittest.mock import MagicMock, patch
import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from alert_engine import AlertDecisionEngine
from zone_engine import ZoneEngine


class MockLoRa:
    """Mock LoRa receiver for testing."""
    def __init__(self, worker_nearby=False):
        self._worker_nearby = worker_nearby

    def is_worker_nearby(self, max_age_seconds=15.0):
        return self._worker_nearby

    def get_nearby_workers(self, max_age_seconds=15.0):
        if self._worker_nearby:
            return [{"tag_id": "TAG-W001", "type": "WORKER_PING",
                     "age_seconds": 2.0, "battery": 3.7}]
        return []


@pytest.fixture
def engine(zones_config):
    """Create an AlertDecisionEngine with mock MQTT and LoRa."""
    zone_engine = ZoneEngine(zones_config)
    mqtt_client = MagicMock()
    mqtt_client.publish.return_value = MagicMock(rc=0)
    lora = MockLoRa(worker_nearby=False)
    eng = AlertDecisionEngine(
        zone_engine=zone_engine,
        lora_receiver=lora,
        mqtt_client=mqtt_client,
        node_id="edge-node-test",
    )
    return eng


@pytest.fixture
def engine_with_worker(zones_config):
    """Engine where a worker tag is detected nearby."""
    zone_engine = ZoneEngine(zones_config)
    mqtt_client = MagicMock()
    mqtt_client.publish.return_value = MagicMock(rc=0)
    lora = MockLoRa(worker_nearby=True)
    return AlertDecisionEngine(
        zone_engine=zone_engine, lora_receiver=lora,
        mqtt_client=mqtt_client, node_id="edge-node-test",
    )


class TestAlertPublishing:
    """Tests for alert generation and MQTT publishing."""

    def test_person_in_zone_publishes_alert(self, engine, make_detection):
        det = make_detection(bbox=[150, 100, 250, 450], bottom_center=[200, 450])
        engine.process_detection(det)
        assert engine.mqtt.publish.called
        args = engine.mqtt.publish.call_args
        assert "farm/alerts/" in args[0][0]
        payload = json.loads(args[0][1])
        assert payload["detection_class"] == "person"
        assert payload["node_id"] == "edge-node-test"
        assert "correlation_id" in payload

    def test_detection_outside_zone_no_alert(self, engine, make_detection):
        det = make_detection(camera_id="cam-99",
                             bbox=[100, 100, 200, 400])
        engine.process_detection(det)
        assert not engine.mqtt.publish.called

    def test_suppression_class_no_alert(self, engine, make_detection):
        det = make_detection(cls="dog", confidence=0.99,
                             bbox=[150, 100, 250, 450])
        engine.process_detection(det)
        assert not engine.mqtt.publish.called


class TestDeduplication:
    """Tests for alert deduplication window."""

    def test_duplicate_suppressed(self, engine, make_detection):
        det1 = make_detection(bbox=[150, 100, 250, 450],
                              bottom_center=[200, 450])
        det2 = make_detection(bbox=[150, 100, 250, 450],
                              bottom_center=[200, 450])
        engine.process_detection(det1)
        engine.process_detection(det2)
        assert engine.mqtt.publish.call_count == 1
        assert engine.stats["deduplicated"] == 1

    def test_different_classes_not_deduplicated(self, engine, make_detection):
        det1 = make_detection(cls="person", bbox=[150, 100, 250, 250],
                              bottom_center=[200, 250])
        det2 = make_detection(cls="child", bbox=[150, 100, 250, 250],
                              bottom_center=[200, 250])
        engine.process_detection(det1)
        engine.process_detection(det2)
        assert engine.mqtt.publish.call_count == 2


class TestWorkerSuppression:
    """Tests for LoRa sensor fusion / worker suppression."""

    def test_worker_nearby_suppresses_intrusion(self, engine_with_worker,
                                                 make_detection):
        det = make_detection(bbox=[500, 100, 600, 450],
                             bottom_center=[550, 450])
        engine_with_worker.process_detection(det)
        calls = engine_with_worker.mqtt.publish.call_args_list
        alert_calls = [c for c in calls if "farm/alerts/" in c[0][0]]
        assert len(alert_calls) == 0
        assert engine_with_worker.stats["worker_suppressed"] == 1

    def test_zero_tolerance_never_suppressed(self, engine_with_worker,
                                              make_detection):
        det = make_detection(bbox=[150, 100, 250, 250],
                             bottom_center=[200, 250])
        engine_with_worker.process_detection(det)
        calls = engine_with_worker.mqtt.publish.call_args_list
        alert_calls = [c for c in calls if "farm/alerts/" in c[0][0]]
        assert len(alert_calls) == 1


class TestFallEvent:
    """Tests for ESP32 fall detection processing."""

    def test_fall_event_publishes_critical(self, engine):
        engine.process_fall_event("TAG-C001", {"TYPE": "FALL"})
        assert engine.mqtt.publish.called
        args = engine.mqtt.publish.call_args
        assert args[0][0] == "farm/alerts/critical"
        payload = json.loads(args[0][1])
        assert payload["detection_class"] == "fall_detected"
        assert payload["priority"] == "critical"


class TestStats:
    """Tests for monitoring statistics."""

    def test_stats_increment(self, engine, make_detection):
        det = make_detection(bbox=[150, 100, 250, 450],
                             bottom_center=[200, 450])
        engine.process_detection(det)
        stats = engine.get_stats()
        assert stats["total_detections"] >= 1
        assert stats["alerts_published"] >= 1

    def test_initial_stats_zero(self, engine):
        stats = engine.get_stats()
        assert stats["total_detections"] == 0
        assert stats["alerts_published"] == 0
