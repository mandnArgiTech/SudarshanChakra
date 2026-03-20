#!/usr/bin/env python3
"""
PA system self-test: disk, MQTT ping, optional speaker check, publish results.

Environment:
  PA_SELF_TEST=true          — required to run destructive/audio tests
  PA_MQTT_BROKER, PA_MQTT_PORT, PA_MQTT_USER, PA_MQTT_PASS
  PA_SELF_TEST_TOPIC         — default pa/health/self_test
  PA_SELF_TEST_SKIP_AUDIO=1  — skip espeak / aplay / speaker tone
  PA_SELF_TEST_SKIP_TONE=1   — skip 440Hz WAV aplay test only
"""
from __future__ import annotations

import json
import logging
import math
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import wave

log = logging.getLogger("pa_self_test")
logging.basicConfig(level=logging.INFO, format="%(message)s")

BROKER = os.getenv("PA_MQTT_BROKER", "192.168.1.100")
PORT = int(os.getenv("PA_MQTT_PORT", "1883"))
USER = os.getenv("PA_MQTT_USER", "")
PASS = os.getenv("PA_MQTT_PASS", "")
TOPIC = os.getenv("PA_SELF_TEST_TOPIC", "pa/health/self_test")
SKIP_AUDIO = os.getenv("PA_SELF_TEST_SKIP_AUDIO", "").lower() in ("1", "true")
SKIP_TONE = os.getenv("PA_SELF_TEST_SKIP_TONE", "").lower() in ("1", "true")


def _disk_free_gb(path: str = "/") -> float:
    try:
        u = shutil.disk_usage(path)
        return round(u.free / (1024**3), 2)
    except OSError:
        return -1.0


def _mqtt_ping() -> tuple[bool, str]:
    try:
        import paho.mqtt.client as mqtt
    except ImportError:
        return False, "paho-mqtt not installed"

    ok = [False]
    err = [""]

    def on_connect(c, _u, _f, rc):
        if rc == 0:
            ok[0] = True
        else:
            err[0] = f"rc={rc}"

    c = mqtt.Client(client_id="pa-selftest", protocol=mqtt.MQTTv311)
    if USER:
        c.username_pw_set(USER, PASS)
    c.on_connect = on_connect
    try:
        c.connect(BROKER, PORT, keepalive=10)
        c.loop_start()
        time.sleep(1.5)
        c.loop_stop()
        c.disconnect()
    except OSError as e:
        return False, str(e)
    return ok[0], err[0] or "connected"


def _audio_devices() -> str:
    try:
        p = subprocess.run(
            ["aplay", "-l"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        return (p.stdout or p.stderr or "?")[:500]
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return "aplay not available"


def _sine_tone_aplay() -> tuple[bool, str]:
    """Play short 440Hz sine via ALSA aplay (stdlib WAV generation)."""
    if SKIP_AUDIO or SKIP_TONE:
        return True, "skipped"
    path = None
    try:
        fd, path = tempfile.mkstemp(suffix=".wav")
        os.close(fd)
        framerate = 16000
        duration = 0.45
        n = int(framerate * duration)
        with wave.open(path, "w") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(framerate)
            for i in range(n):
                sample = int(32767 * 0.15 * math.sin(2 * math.pi * 440 * i / framerate))
                w.writeframes(struct.pack("<h", sample))
        p = subprocess.run(
            ["aplay", "-q", path],
            timeout=15,
            capture_output=True,
        )
        return p.returncode == 0, (p.stderr or b"").decode()[:200]
    except FileNotFoundError:
        return False, "aplay missing"
    except subprocess.TimeoutExpired:
        return False, "aplay timeout"
    except Exception as e:
        return False, str(e)[:200]
    finally:
        if path:
            try:
                os.unlink(path)
            except OSError:
                pass


def _espeak_test() -> tuple[bool, str]:
    try:
        p = subprocess.run(
            ["espeak-ng", "PA self test OK"],
            timeout=15,
            capture_output=True,
        )
        return p.returncode == 0, (p.stderr or b"").decode()[:200]
    except FileNotFoundError:
        return False, "espeak-ng missing"
    except subprocess.TimeoutExpired:
        return False, "timeout"


def _publish(result: dict) -> None:
    try:
        import paho.mqtt.client as mqtt
    except ImportError:
        return
    c = mqtt.Client(client_id="pa-selftest-publish", protocol=mqtt.MQTTv311)
    if USER:
        c.username_pw_set(USER, PASS)
    try:
        c.connect(BROKER, PORT, keepalive=10)
        c.loop_start()
        c.publish(TOPIC, json.dumps(result), qos=1)
        time.sleep(0.3)
        c.loop_stop()
        c.disconnect()
    except OSError as e:
        log.warning("Could not publish self_test: %s", e)


def main() -> int:
    force = os.getenv("PA_SELF_TEST", "false").lower() == "true"
    if not force and "--force" not in sys.argv:
        print("Set PA_SELF_TEST=true or pass --force", file=sys.stderr)
        return 0

    results: dict = {
        "ok": True,
        "ts": time.time(),
        "checks": {},
    }

    free = _disk_free_gb("/tmp")
    results["checks"]["disk_free_gb_tmp"] = free
    if free >= 0 and free < 0.05:
        results["ok"] = False
        results["checks"]["disk_warning"] = "Low free space on /tmp"

    mq_ok, mq_detail = _mqtt_ping()
    results["checks"]["mqtt"] = {"ok": mq_ok, "detail": mq_detail}
    if not mq_ok:
        results["ok"] = False

    results["checks"]["audio_devices"] = _audio_devices()

    tone_ok, tone_err = _sine_tone_aplay()
    results["checks"]["speaker_tone_aplay"] = {"ok": tone_ok, "detail": tone_err}
    if not SKIP_AUDIO and not SKIP_TONE and not tone_ok:
        results["ok"] = False

    if not SKIP_AUDIO:
        sp_ok, sp_err = _espeak_test()
        results["checks"]["espeak"] = {"ok": sp_ok, "detail": sp_err}
        if not sp_ok:
            results["ok"] = False
    else:
        results["checks"]["espeak"] = {"ok": None, "skipped": True}

    log.info(json.dumps(results, indent=2))
    _publish(results)
    return 0 if results["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
