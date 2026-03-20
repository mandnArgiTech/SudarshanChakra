#!/usr/bin/env python3
"""
GPIO status LED (Raspberry Pi): idle=green solid, siren=red solid, error=blink.

Environment:
  PA_LED_GPIO=true|1
  PA_LED_PIN_GREEN=17
  PA_LED_PIN_RED=27   — optional second LED; if unset, single LED uses patterns
  PA_LED_MODE=single  — single bi-color via one pin: use RED pin only, blink = error

For simplicity with one LED: PA_LED_PIN=17 — HIGH=on (activity), blink on error via thread.
Subscribe to pa/status JSON and set LED from state field.

  PA_LED_MQTT_STATUS=pa/status
"""
from __future__ import annotations

import json
import logging
import os
import sys
import threading
import time

log = logging.getLogger("pa_led")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

MODE_IDLE = "idle"
MODE_SIREN = "siren"
MODE_ERROR = "error"

_state_lock = threading.Lock()
_mode = MODE_IDLE
_blink_stop = threading.Event()
_blink_thread: threading.Thread | None = None


def _setup_gpio():
    import RPi.GPIO as GPIO  # type: ignore

    GPIO.setmode(GPIO.BCM)
    return GPIO


def _led_loop(gpio, pin: int):
    while not _blink_stop.is_set():
        gpio.output(pin, gpio.HIGH)
        time.sleep(0.4)
        if _blink_stop.wait(0):
            break
        gpio.output(pin, gpio.LOW)
        time.sleep(0.4)


def set_mode(gpio, pin_green: int, pin_red: int | None, mode: str) -> None:
    global _blink_thread, _mode
    with _state_lock:
        _blink_stop.set()
        if _blink_thread and _blink_thread.is_alive():
            _blink_thread.join(timeout=2)
        _blink_stop.clear()
        _mode = mode

        try:
            if pin_red is None:
                if mode == MODE_ERROR:
                    _blink_thread = threading.Thread(
                        target=_led_loop, args=(gpio, pin_green), daemon=True
                    )
                    _blink_thread.start()
                elif mode == MODE_SIREN:
                    gpio.output(pin_green, gpio.HIGH)
                else:
                    gpio.output(pin_green, gpio.LOW)
            else:
                gpio.output(pin_green, gpio.LOW)
                gpio.output(pin_red, gpio.LOW)
                if mode == MODE_SIREN:
                    gpio.output(pin_red, gpio.HIGH)
                elif mode == MODE_ERROR:
                    _blink_thread = threading.Thread(
                        target=_led_loop, args=(gpio, pin_red), daemon=True
                    )
                    _blink_thread.start()
                else:
                    gpio.output(pin_green, gpio.HIGH)
        except Exception as e:
            log.warning("GPIO output: %s", e)


def _map_status_payload(payload: dict) -> str:
    st = str(payload.get("state", "")).lower()
    detail = str(payload.get("detail", "")).lower()
    if "siren" in st or "playing_siren" in st:
        return MODE_SIREN
    if "error" in detail or "fail" in detail:
        return MODE_ERROR
    return MODE_IDLE


def main() -> None:
    if os.getenv("PA_LED_GPIO", "").lower() not in ("1", "true"):
        log.info("PA_LED_GPIO not enabled")
        return

    try:
        gpio = _setup_gpio()
    except Exception as e:
        log.error("GPIO init failed: %s", e)
        sys.exit(1)

    pin_g = int(os.getenv("PA_LED_PIN_GREEN", os.getenv("PA_LED_PIN", "17")))
    pin_r_env = os.getenv("PA_LED_PIN_RED", "").strip()
    pin_r = int(pin_r_env) if pin_r_env else None

    gpio.setup(pin_g, gpio.OUT)
    if pin_r is not None:
        gpio.setup(pin_r, gpio.OUT)

    try:
        import paho.mqtt.client as mqtt
    except ImportError:
        log.error("pip install paho-mqtt")
        sys.exit(1)

    broker = os.getenv("PA_MQTT_BROKER", "192.168.1.100")
    port = int(os.getenv("PA_MQTT_PORT", "1883"))
    user = os.getenv("PA_MQTT_USER", "")
    pwd = os.getenv("PA_MQTT_PASS", "")
    topic = os.getenv("PA_LED_MQTT_STATUS", "pa/status")

    def on_message(_c, _u, msg):
        try:
            data = json.loads(msg.payload.decode())
        except Exception:
            return
        set_mode(gpio, pin_g, pin_r, _map_status_payload(data))

    client = mqtt.Client(client_id="pa-led", protocol=mqtt.MQTTv311)
    if user:
        client.username_pw_set(user, pwd)
    client.on_message = on_message

    def on_connect(c, _u, _f, rc):
        if rc == 0:
            c.subscribe(topic, qos=0)
            log.info("LED GPIO listening on %s", topic)
            set_mode(gpio, pin_g, pin_r, MODE_IDLE)

    client.on_connect = on_connect

    try:
        client.connect(broker, port, keepalive=60)
        client.loop_forever()
    except KeyboardInterrupt:
        pass
    finally:
        _blink_stop.set()
        try:
            gpio.cleanup()
        except Exception:
            pass


if __name__ == "__main__":
    main()
