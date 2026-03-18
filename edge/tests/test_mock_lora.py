"""Tests for mock_lora.py."""

import json
import tempfile
import time

import pytest

from mock_lora import MockLoRaReceiver


@pytest.fixture
def tags_file(tmp_path):
    p = tmp_path / "authorized_tags.json"
    p.write_text(json.dumps({"tags": ["TAG-W001", "TAG-W002"]}))
    return str(p)


def test_mock_lora_worker_present(tags_file):
    r = MockLoRaReceiver(authorized_tags_path=tags_file, worker_present=True, beacon_interval=0.5)
    r.start()
    time.sleep(1.2)
    assert r.is_worker_nearby() is True
    r.stop()
    r.join(timeout=2.0)


def test_mock_lora_worker_absent(tags_file):
    r = MockLoRaReceiver(authorized_tags_path=tags_file, worker_present=False, beacon_interval=0.5)
    r.start()
    time.sleep(1.2)
    assert r.is_worker_nearby() is False
    r.stop()
    r.join(timeout=2.0)


def test_mock_lora_toggle_worker(tags_file):
    r = MockLoRaReceiver(authorized_tags_path=tags_file, worker_present=True, beacon_interval=0.5)
    r.start()
    time.sleep(1.0)
    assert r.is_worker_nearby() is True
    r.set_worker_present(False)
    time.sleep(16.0)
    assert r.is_worker_nearby() is False
    r.stop()
    r.join(timeout=2.0)


def test_mock_lora_get_nearby_workers(tags_file):
    r = MockLoRaReceiver(authorized_tags_path=tags_file, worker_present=True, beacon_interval=0.5)
    r.start()
    time.sleep(1.2)
    workers = r.get_nearby_workers()
    assert len(workers) >= 1
    assert workers[0]["tag_id"] in ("TAG-W001", "TAG-W002")
    assert workers[0]["type"] == "WORKER_PING"
    assert workers[0].get("mock") is True
    r.stop()
    r.join(timeout=2.0)


def test_mock_lora_simulate_fall(tags_file):
    r = MockLoRaReceiver(authorized_tags_path=tags_file, worker_present=False, beacon_interval=1.0)
    called = []

    def cb(tag_id, pkt):
        called.append((tag_id, pkt))

    r.fall_callbacks.append(cb)
    r.simulate_fall("TAG-C001")
    assert len(called) == 1
    assert called[0][0] == "TAG-C001"
    assert called[0][1].get("TYPE") == "FALL"
    assert called[0][1].get("PRIORITY") == "CRITICAL"


def test_mock_lora_fall_callback_multiple(tags_file):
    r = MockLoRaReceiver(authorized_tags_path=tags_file, worker_present=False, beacon_interval=1.0)
    calls = []
    r.fall_callbacks.append(lambda t, p: calls.append(1))
    r.fall_callbacks.append(lambda t, p: calls.append(2))
    r.simulate_fall("TAG-X")
    assert calls == [1, 2]


def test_mock_lora_get_all_tags(tags_file):
    r = MockLoRaReceiver(authorized_tags_path=tags_file, worker_present=True, beacon_interval=0.5)
    r.start()
    time.sleep(1.0)
    r.simulate_fall("TAG-C001")
    tags = r.get_all_tags(max_age_seconds=120.0)
    tag_ids = {t["tag_id"] for t in tags}
    assert "TAG-W001" in tag_ids or "TAG-W002" in tag_ids
    assert "TAG-C001" in tag_ids
    r.stop()
    r.join(timeout=2.0)


def test_mock_lora_authorized_tags_from_config(tmp_path):
    p = tmp_path / "tags.json"
    p.write_text(json.dumps({"tags": ["TAG-X1", "TAG-X2"]}))
    r = MockLoRaReceiver(authorized_tags_path=str(p), worker_present=False, beacon_interval=1.0)
    assert r.authorized_tags == {"TAG-X1", "TAG-X2"}
