#!/usr/bin/env python3
"""
Optional LAN bridge: subscribe to local MQTT water readings, republish to VPS broker.
Run when ESP8266 cannot reach VPS directly.
"""
import json
import os
import sys

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("pip install paho-mqtt", file=sys.stderr)
    sys.exit(1)

LOCAL = os.getenv("WATER_BRIDGE_LOCAL", "192.168.1.1")
VPS = os.getenv("WATER_BRIDGE_VPS", "10.8.0.1")
TOPIC_IN = os.getenv("WATER_BRIDGE_TOPIC_IN", "farm/local/water")
TOPIC_OUT = os.getenv("WATER_BRIDGE_TOPIC_OUT", "farm/water/water.level")


def main():
    local = mqtt.Client()
    remote = mqtt.Client()

    def on_local_msg(_, __, msg):
        try:
            payload = msg.payload.decode()
            json.loads(payload)
            remote.publish(TOPIC_OUT, payload, qos=1)
        except Exception as e:
            print("bridge error", e)

    local.on_message = on_local_msg
    local.connect(LOCAL, 1883, 60)
    local.subscribe(TOPIC_IN)
    remote.connect(VPS, 1883, 60)
    remote.loop_start()
    local.loop_forever()


if __name__ == "__main__":
    main()
