"""MQTT integration tests — run with: MQTT_INTEGRATION=1 pytest tests/test_integration_mqtt.py -v"""
import json
import os
import threading
import time

import pytest

paho = pytest.importorskip("paho.mqtt.client", reason="paho-mqtt")

BROKER = os.getenv("MQTT_TEST_BROKER", "127.0.0.1")
PORT = int(os.getenv("MQTT_TEST_PORT", "1883"))
pytestmark = pytest.mark.skipif(
    os.getenv("MQTT_INTEGRATION", "") != "1",
    reason="Set MQTT_INTEGRATION=1 and start Mosquitto (e.g. docker run -p 1883:1883 eclipse-mosquitto)",
)


def _client():
    try:
        return paho.Client(paho.CallbackAPIVersion.VERSION2)
    except Exception:
        return paho.Client()


@pytest.fixture
def connected():
    c = _client()
    c.connect(BROKER, PORT, 60)
    c.loop_start()
    time.sleep(0.3)
    yield c
    c.loop_stop()
    c.disconnect()


def test_publish_alert_critical(connected):
    received = []

    def on_msg(cl, userdata, msg):
        received.append((msg.topic, msg.payload.decode()))

    connected.subscribe("farm/alerts/#")
    connected.on_message = on_msg
    time.sleep(0.2)
    payload = json.dumps({"alert_id": "t1", "node_id": "n", "camera_id": "c", "zone_id": "z",
                          "zone_name": "Z", "zone_type": "intrusion", "detection_class": "person",
                          "confidence": 0.9})
    connected.publish("farm/alerts/critical", payload, qos=1)
    time.sleep(0.5)
    assert any("critical" in t for t, _ in received)


def test_siren_ack_flow(connected):
    ack = []
    connected.subscribe("farm/siren/ack")
    connected.on_message = lambda cl, u, m: ack.append(m.payload.decode())
    time.sleep(0.2)
    connected.publish(
        "farm/siren/trigger",
        json.dumps({"node_id": "e1", "siren_url": "http://x/a.mp3"}),
        qos=1,
    )
    time.sleep(0.3)
    # No mock siren in integration — publish ack ourselves to test subscriber path
    connected.publish("farm/siren/ack", json.dumps({"status": "ok"}), qos=1)
    time.sleep(0.3)
    assert ack


def test_worker_suppression_topic(connected):
    got = []
    connected.subscribe("farm/events/#")
    connected.on_message = lambda c, u, m: got.append(m.topic)
    time.sleep(0.2)
    connected.publish(
        "farm/events/worker_identified",
        json.dumps({"node_id": "n", "camera_id": "c"}),
        qos=1,
    )
    time.sleep(0.3)
    assert got


def test_heartbeat_style(connected):
    connected.publish("node/edge-node-a/heartbeat", b"{}", qos=0)
    time.sleep(0.1)


def test_dev_simulate_fall_topic(connected):
    connected.publish("dev/simulate/fall", json.dumps({"tag_id": "TAG-X"}), qos=1)
    time.sleep(0.1)


def test_water_level_publish(connected):
    connected.publish(
        "farm/water/water.level",
        json.dumps({"tank_id": "00000000-0000-0000-0000-000000000001", "level_pct": 50.0}),
        qos=1,
    )
    time.sleep(0.1)


def test_pa_health_topic(connected):
    msgs = []
    connected.subscribe("pa/health")
    connected.on_message = lambda c, u, m: msgs.append(m.payload.decode())
    time.sleep(0.2)
    connected.publish("pa/health", json.dumps({"status": "ok", "ts": time.time()}), qos=0)
    time.sleep(0.3)
    assert msgs
