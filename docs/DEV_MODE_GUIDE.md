# Dev Mode — Run SudarshanChakra Without Hardware

## What It Solves

The production edge system needs an NVIDIA RTX 3060 GPU, 8 RTSP cameras, ESP32 LoRa hardware, and the Ahuja PA siren. Dev mode replaces all of these with software mocks so you can develop, test, and demonstrate the entire system on any laptop.

## Quick Start

```bash
cd edge
docker compose -f docker-compose.dev.yml up
```

That's it. This starts:
- A local Mosquitto MQTT broker (port 1883)
- The edge AI container in CPU mode with mock everything

Open `http://localhost:5000` to see the Flask polygon editor with synthetic camera frames.

## What Gets Mocked

| Real Hardware | Mock Replacement | Behavior |
|:---|:---|:---|
| RTX 3060 + TensorRT | CPU PyTorch (no YOLO inference) | Cycles through 10 predefined detection scenarios every 5 seconds |
| 8 RTSP cameras | `mock_camera.py` | Generates synthetic 640×480 frames with colored bounding boxes |
| ESP32 LoRa tags | `mock_lora.py` | Simulates worker tag beacons (toggleable), fall events (triggerable) |
| Ahuja PA siren | `mock_siren.py` | Logs siren commands to console, sends MQTT acks |
| VPS RabbitMQ | Local Mosquitto | Runs on same Docker network, port 1883 |

## Mock Detection Scenarios

The mock pipeline cycles through these detections automatically:

| # | Class | Confidence | What It Tests |
|:--|:------|:-----------|:-------------|
| 1 | person | 87% | Perimeter intrusion → LoRa fusion → suppressed (worker nearby) |
| 2 | person | 92% | Person in pond zone → zero-tolerance → CRITICAL alert (never suppressed) |
| 3 | child | 78% | Child near pond → CRITICAL alert |
| 4 | snake | 72% | Snake on ground → hazard alert |
| 5 | cow | 91% | Cow outside containment → livestock warning |
| 6 | fire | 83% | Fire in storage → critical alert |
| 7 | scorpion | 65% | Scorpion at ground level → hazard alert |
| 8 | dog | 88% | Farm dog → suppression class → NO alert |
| 9-10 | (empty) | — | Empty frames → no detection |

## Simulation Commands (via MQTT)

While the dev stack is running, you can trigger events manually:

```bash
# Simulate a child falling into the pond (CRITICAL alert)
mosquitto_pub -t dev/simulate/fall -m '{"tag_id": "TAG-C001"}'

# Disable worker tag (next person detection becomes intruder alert)
mosquitto_pub -t dev/simulate/worker_toggle -m '{"present": false}'

# Re-enable worker tag (person detections get suppressed again)
mosquitto_pub -t dev/simulate/worker_toggle -m '{"present": true}'

# Trigger siren (tests the Android→VPS→Edge→Siren chain)
mosquitto_pub -t farm/siren/trigger -m '{"command":"trigger","triggered_by":"manual"}'

# Stop siren
mosquitto_pub -t farm/siren/stop -m '{"command":"stop"}'
```

## End-to-End Test Suite

```bash
# Run all 5 tests
pip install paho-mqtt
python test_e2e.py

# Run a single test
python test_e2e.py --test fall_detect

# Point to a remote broker
python test_e2e.py --broker 192.168.1.100
```

Tests and what they validate:

| Test | What It Validates |
|:-----|:-----------------|
| `alert_flow` | Mock detection → zone engine → alert published to `farm/alerts/#` |
| `worker_suppress` | Toggle worker OFF → person detection becomes intruder alert |
| `fall_detect` | Simulated ESP32 fall → CRITICAL alert on `farm/alerts/critical` |
| `siren_trigger` | Siren command → mock siren activates → ack on `farm/siren/ack` |
| `siren_stop` | Siren stop → mock siren deactivates → ack |

## Environment Variables

| Variable | Default | Description |
|:---------|:--------|:-----------|
| `DEV_MODE` | `false` | Master switch — enables all mocks |
| `MOCK_CAMERAS` | `false` | Replace RTSP cameras with synthetic frames |
| `MOCK_LORA` | `false` | Replace LoRa serial with simulated tags |
| `MOCK_SIREN` | `false` | Replace PA siren with log output |
| `MOCK_DETECTION_INTERVAL` | `5` | Seconds between mock detections |
| `YOLO_DEVICE` | `cpu` | Force CPU inference (auto in dev mode) |

## Testing with the Cloud Backend

The dev edge connects to any MQTT broker. To test with the full VPS stack:

```bash
# Start VPS stack
cd cloud && ./deploy.sh

# Start edge in dev mode pointing to VPS broker
cd edge
VPN_BROKER_IP=vivasvan-tech.in \
MQTT_PORT=1883 \
docker compose -f docker-compose.dev.yml up

# Mock alerts will flow through RabbitMQ → alert-service → PostgreSQL → React dashboard
```

## File Reference

| File | Lines | Purpose |
|:-----|:------|:--------|
| `Dockerfile.dev` | 27 | CPU-only container (python:3.11-slim) |
| `requirements-dev.txt` | 17 | CPU PyTorch + ultralytics |
| `docker-compose.dev.yml` | 60 | Dev stack with Mosquitto broker |
| `mock_camera.py` | 220 | Synthetic frames + mock detection scenarios |
| `mock_lora.py` | 170 | Simulated worker/child tags + fall events |
| `mock_siren.py` | 95 | Siren command logging + MQTT acks |
| `test_e2e.py` | 265 | 5 end-to-end tests for full flow validation |
| `dev/mosquitto.conf` | 4 | Local MQTT broker config |
