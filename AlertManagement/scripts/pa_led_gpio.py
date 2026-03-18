#!/usr/bin/env python3
"""Optional GPIO LED status (Raspberry Pi). Requires RPi.GPIO."""
import os
import sys

if os.getenv("PA_LED_GPIO", "").lower() not in ("1", "true"):
    sys.exit(0)

try:
    import RPi.GPIO as GPIO
except ImportError:
    print("pip install RPi.GPIO", file=sys.stderr)
    sys.exit(1)

PIN = int(os.getenv("PA_LED_PIN", "17"))
GPIO.setmode(GPIO.BCM)
GPIO.setup(PIN, GPIO.OUT)
GPIO.output(PIN, GPIO.HIGH)
