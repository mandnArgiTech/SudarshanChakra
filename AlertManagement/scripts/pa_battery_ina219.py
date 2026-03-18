#!/usr/bin/env python3
"""Optional INA219 battery monitor — pip install adafruit-circuitpython-ina219."""
import os
import sys

if os.getenv("PA_BATTERY_MONITOR", "").lower() not in ("1", "true"):
    sys.exit(0)
try:
    import board
    from adafruit_ina219 import INA219
except ImportError:
    print("Install adafruit-circuitpython-ina219 on Raspberry Pi", file=sys.stderr)
    sys.exit(1)

i2c = board.I2C()
ina = INA219(i2c)
print(f"Bus V: {ina.bus_voltage}V  Shunt mV: {ina.shunt_voltage}mV  Current mA: {ina.current}mA")
