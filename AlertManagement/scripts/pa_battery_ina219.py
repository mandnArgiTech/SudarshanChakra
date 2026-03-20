#!/usr/bin/env python3
"""
INA219 battery / bus monitor loop — publishes to pa/health/battery.

Environment:
  PA_BATTERY_MONITOR=true|1
  PA_BATTERY_INTERVAL_SEC=30
  PA_BATTERY_LOW_V=11.0  — warn below this bus voltage (12V lead acid rough)
  PA_MQTT_BROKER, PA_MQTT_PORT, PA_MQTT_USER, PA_MQTT_PASS
"""
from __future__ import annotations

import json
import logging
import os
import sys
import time

log = logging.getLogger("pa_battery")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

TOPIC = os.getenv("PA_BATTERY_HEALTH_TOPIC", "pa/health/battery")
WARN_TOPIC = os.getenv("PA_BATTERY_WARN_TOPIC", "pa/health/battery_warn")
INTERVAL = float(os.getenv("PA_BATTERY_INTERVAL_SEC", "30"))
LOW_V = float(os.getenv("PA_BATTERY_LOW_V", "11.0"))
BROKER = os.getenv("PA_MQTT_BROKER", "192.168.1.100")
PORT = int(os.getenv("PA_MQTT_PORT", "1883"))
USER = os.getenv("PA_MQTT_USER", "")
PASS = os.getenv("PA_MQTT_PASS", "")


def main() -> None:
    if os.getenv("PA_BATTERY_MONITOR", "").lower() not in ("1", "true"):
        log.info("PA_BATTERY_MONITOR not enabled — exit")
        return

    try:
        import board
        from adafruit_ina219 import INA219
    except ImportError:
        log.error("Install adafruit-circuitpython-ina219 on Raspberry Pi")
        sys.exit(1)

    try:
        import paho.mqtt.client as mqtt
    except ImportError:
        log.error("pip install paho-mqtt")
        sys.exit(1)

    i2c = board.I2C()
    ina = INA219(i2c)

    client = mqtt.Client(client_id="pa-battery", protocol=mqtt.MQTTv311)
    if USER:
        client.username_pw_set(USER, PASS)

    while True:
        try:
            client.connect(BROKER, PORT, keepalive=60)
            break
        except OSError as e:
            log.warning("MQTT %s — retry in 30s: %s", BROKER, e)
            time.sleep(30)

    client.loop_start()

    try:
        while True:
            try:
                bus_v = float(ina.bus_voltage)
                current_ma = float(ina.current)
                payload = {
                    "ts": time.time(),
                    "bus_voltage_v": round(bus_v, 3),
                    "current_ma": round(current_ma, 2),
                    "shunt_mv": round(float(ina.shunt_voltage) * 1000, 3),
                }
                client.publish(TOPIC, json.dumps(payload), qos=0)
                log.debug("Published %s", payload)

                if bus_v > 0 and bus_v < LOW_V:
                    client.publish(
                        WARN_TOPIC,
                        json.dumps({"ts": time.time(), "bus_voltage_v": bus_v, "threshold": LOW_V}),
                        qos=1,
                    )
                    log.warning("Low battery bus voltage: %.2fV < %.2fV", bus_v, LOW_V)
            except Exception as e:
                log.error("INA219 read failed: %s", e)
            time.sleep(INTERVAL)
    finally:
        client.loop_stop()
        try:
            client.disconnect()
        except Exception:
            pass


if __name__ == "__main__":
    main()
