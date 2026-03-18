"""Tests for mock_siren.py."""

import json
from unittest.mock import MagicMock

import pytest

from mock_siren import MockSirenHandler


@pytest.fixture
def mock_mqtt():
    m = MagicMock()
    m.publish = MagicMock()
    return m


def test_mock_siren_trigger(mock_mqtt):
    h = MockSirenHandler(mock_mqtt, "edge-node-a")
    msg = MagicMock()
    msg.topic = "farm/siren/trigger"
    msg.payload = json.dumps({"siren_url": "http://x/s.mp3", "triggered_by": "test"}).encode()
    h.handle_command(None, None, msg)
    assert h.siren_active is True
    mock_mqtt.publish.assert_called()
    call_kw = mock_mqtt.publish.call_args
    assert call_kw[0][0] == "farm/siren/ack"
    body = json.loads(call_kw[0][1])
    assert body["status"] == "siren_activated"


def test_mock_siren_stop(mock_mqtt):
    h = MockSirenHandler(mock_mqtt, "edge-node-a")
    h.siren_active = True
    msg = MagicMock()
    msg.topic = "farm/siren/stop"
    msg.payload = b"{}"
    h.handle_command(None, None, msg)
    assert h.siren_active is False
    body = json.loads(mock_mqtt.publish.call_args[0][1])
    assert body["status"] == "siren_stopped"


def test_mock_siren_activation_count(mock_mqtt):
    h = MockSirenHandler(mock_mqtt, "n")
    for _ in range(3):
        msg = MagicMock()
        msg.topic = "farm/siren/trigger"
        msg.payload = b"{}"
        h.handle_command(None, None, msg)
    assert h.activation_count == 3


def test_mock_siren_get_status(mock_mqtt):
    h = MockSirenHandler(mock_mqtt, "n")
    msg = MagicMock()
    msg.topic = "farm/siren/trigger"
    msg.payload = b"{}"
    h.handle_command(None, None, msg)
    st = h.get_status()
    assert st["active"] is True
    assert st["mock"] is True
    assert st["activation_count"] == 1


def test_mock_siren_invalid_payload(mock_mqtt):
    h = MockSirenHandler(mock_mqtt, "n")
    msg = MagicMock()
    msg.topic = "farm/siren/trigger"
    msg.payload = b"not-json{{{"
    h.handle_command(None, None, msg)
    assert h.siren_active is False
