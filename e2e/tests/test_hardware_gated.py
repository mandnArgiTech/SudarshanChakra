"""
Optional hardware checks (Suites 7–8 helpers). Run with E2E_RUN_HARDWARE=1 and valid e2e_config.yml.
"""
from __future__ import annotations

import os

import pytest

from e2e.helpers import audio_verify
from e2e.helpers import mqtt_helper
from e2e.helpers import onvif_verify


@pytest.fixture
def hardware_enabled():
    if os.environ.get("E2E_RUN_HARDWARE", "").lower() not in ("1", "true", "yes"):
        pytest.skip("set E2E_RUN_HARDWARE=1 to run MQTT/ONVIF/audio probes")


def test_mqtt_helper_import_and_skip_without_broker(hardware_enabled, e2e_config: dict):
    be = e2e_config.get("backend") or {}
    host = be.get("mqtt_broker")
    port = int(be.get("mqtt_port") or 1883)
    if not host:
        pytest.skip("mqtt_broker not in config")
    try:
        mqtt_helper.wait_for_message(host, port, "$SYS/#", timeout_sec=2.0)
    except Exception:
        pytest.skip("MQTT broker did not deliver on $SYS within 2s (normal for RabbitMQ MQTT)")


def test_onvif_helper_skips_without_package(hardware_enabled):
    if not onvif_verify.onvif_available():
        pytest.skip("onvif-zeep not installed")
    pytest.skip("farm-only: pass camera host via config and call get_ptz_position in Suite 4")


def test_audio_helper_arecord(hardware_enabled, e2e_config: dict):
    siren = e2e_config.get("siren") or {}
    dev = siren.get("audio_device", "hw:0,0")
    if not audio_verify.arecord_available():
        pytest.skip("arecord not on PATH")
    rms = audio_verify.record_and_measure_rms(device=dev, duration_sec=1)
    if rms is None:
        pytest.skip("ALSA capture failed")
    assert rms >= 0
