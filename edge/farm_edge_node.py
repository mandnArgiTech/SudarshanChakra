#!/usr/bin/env python3
"""
farm_edge_node.py — Main Entrypoint for Edge AI Container
==========================================================
This is the CMD entrypoint specified in the Dockerfile.
Orchestrates: YOLO inference pipeline, Flask GUI, LoRa receiver,
alert decision engine, and siren command listener.

Hardware: i5-10400 / RTX 3060 12GB / 32GB RAM
Cameras:  TP-Link VIGI C540-W (Pan/Tilt), Tapo C210/Outdoor
"""

import json
import logging
import os
import signal
import sys
import threading
import time

import paho.mqtt.client as mqtt

# ── Dev Mode Detection ──
DEV_MODE = os.getenv("DEV_MODE", "false").lower() == "true"
MOCK_CAMERAS = os.getenv("MOCK_CAMERAS", "false").lower() == "true"
MOCK_LORA = os.getenv("MOCK_LORA", "false").lower() == "true"
MOCK_SIREN = os.getenv("MOCK_SIREN", "false").lower() == "true"

if DEV_MODE or MOCK_CAMERAS:
    from mock_camera import MockInferencePipeline
if DEV_MODE or MOCK_LORA:
    from mock_lora import MockLoRaReceiver
if DEV_MODE or MOCK_SIREN:
    from mock_siren import MockSirenHandler

from pipeline import CameraConfig  # noqa: E402
if not (DEV_MODE or MOCK_CAMERAS):
    from pipeline import InferencePipeline, load_model  # noqa: E402

from zone_engine import ZoneEngine  # noqa: E402
if not (DEV_MODE or MOCK_LORA):
    from lora_receiver import LoRaReceiver  # noqa: E402
from alert_engine import AlertDecisionEngine  # noqa: E402
from edge_gui import create_app  # noqa: E402

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
NODE_ID = os.getenv("NODE_ID", "edge-node-a")
VPN_BROKER_IP = os.getenv("VPN_BROKER_IP", "10.8.0.1")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_USER = os.getenv("MQTT_USER", "edge-publisher")
MQTT_PASS = os.getenv("MQTT_PASS", "")
CONFIG_DIR = os.getenv("CONFIG_DIR", "/app/config")
MODEL_DIR = os.getenv("MODEL_DIR", "/app/models")
FLASK_PORT = int(os.getenv("FLASK_PORT", "5000"))
LORA_PORT = os.getenv("LORA_PORT", "/dev/ttyUSB0")
LORA_ENABLED = os.getenv("LORA_ENABLED", "true").lower() == "true"

LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
logging.basicConfig(
    level=getattr(logging, LOG_LEVEL, logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
log = logging.getLogger("farm_edge_node")


def _mqtt_admin_reload(zone_engine, mqtt_client):
    """Reload zones.json + suppression_rules.json (topic farm/admin/reload_config)."""
    try:
        from detection_filters import reload_suppression_rules
        zone_engine.reload()
        reload_suppression_rules()
        mqtt_client.publish(
            f"farm/nodes/{NODE_ID}/config_reloaded",
            json.dumps({"node_id": NODE_ID, "ok": True}),
            qos=1,
        )
        log.info("Admin reload: zones + suppression_rules applied")
    except Exception as e:
        log.error("reload_config failed: %s", e)


def _mqtt_model_update(mqtt_client, msg, pipeline_holder):
    """Hot-swap model weights (topic farm/admin/model_update, JSON {\"path\": \"/models/x.pt\"})."""
    try:
        body = json.loads(msg.payload.decode() or "{}")
    except Exception:
        body = {}
    path = (body.get("path") or "").strip() or os.getenv("PT_PATH", "")
    pl = pipeline_holder.get("pipeline")
    ok = bool(path and pl and getattr(pl, "swap_model", lambda _: False)(path))
    mqtt_client.publish(
        f"farm/nodes/{NODE_ID}/model_update_ack",
        json.dumps({"node_id": NODE_ID, "ok": ok, "path": path}),
        qos=1,
    )


def load_camera_configs() -> list:
    """
    Load camera configurations from JSON.

    TP-Link RTSP URL formats:
      VIGI C540-W:  rtsp://<user>:<pass>@<ip>:554/stream1  (main)
                    rtsp://<user>:<pass>@<ip>:554/stream2  (sub)
      Tapo C210:    rtsp://<user>:<pass>@<ip>:554/stream1  (main, H.264)
                    rtsp://<user>:<pass>@<ip>:554/stream2  (sub, lower res)
      Tapo Outdoor: rtsp://<user>:<pass>@<ip>:554/stream1

    NOTE: Tapo cameras require enabling RTSP in the Tapo app under
    Camera Settings → Advanced → Device Account (set username/password).
    VIGI cameras use the NVR or standalone RTSP with admin credentials.

    For frame-skipping at 2-3 FPS, always use stream2 (sub-stream, 640x480)
    to reduce bandwidth and decoding load on the RTX 3060.
    """
    config_path = os.path.join(CONFIG_DIR, "cameras.json")
    try:
        with open(config_path) as f:
            data = json.load(f)
        cameras = []
        for cam in data.get("cameras", []):
            cameras.append(CameraConfig(
                id=cam["id"],
                name=cam["name"],
                rtsp_url=cam["rtsp_url"],
                fps=cam.get("fps", 2.0),
                enabled=cam.get("enabled", True),
            ))
        log.info("Loaded %d camera configs from %s", len(cameras), config_path)
        return cameras
    except FileNotFoundError:
        log.warning("No cameras.json found, using defaults")
        return [
            # Example configs — update IPs and credentials for your network
            CameraConfig(
                id="cam-01", name="Front Gate (VIGI C540-W)",
                rtsp_url="rtsp://admin:password@192.168.1.201:554/stream2",
                fps=2.5,
            ),
            CameraConfig(
                id="cam-02", name="Storage Shed (Tapo C210)",
                rtsp_url="rtsp://admin:password@192.168.1.202:554/stream2",
                fps=2.0,
            ),
            CameraConfig(
                id="cam-03", name="Pond Area (VIGI C540-W)",
                rtsp_url="rtsp://admin:password@192.168.1.203:554/stream2",
                fps=3.0,  # Higher FPS for safety-critical zone
            ),
            CameraConfig(
                id="cam-04", name="East Perimeter (Tapo Outdoor)",
                rtsp_url="rtsp://admin:password@192.168.1.204:554/stream2",
                fps=2.0,
            ),
        ]


def create_mqtt_client() -> mqtt.Client:
    """Create MQTT client for publishing alerts to VPS broker over VPN."""
    client = mqtt.Client(client_id=f"{NODE_ID}-publisher", protocol=mqtt.MQTTv311)

    if MQTT_USER:
        client.username_pw_set(MQTT_USER, MQTT_PASS)

    # Last Will — broker publishes if edge node disconnects
    client.will_set(
        f"farm/nodes/{NODE_ID}/status",
        json.dumps({"node_id": NODE_ID, "status": "offline", "timestamp": time.time()}),
        qos=1, retain=True,
    )

    def on_connect(client, userdata, flags, rc):
        if rc == 0:
            log.info("Connected to VPS MQTT broker at %s:%d via VPN", VPN_BROKER_IP, MQTT_PORT)
            # Announce online
            client.publish(
                f"farm/nodes/{NODE_ID}/status",
                json.dumps({"node_id": NODE_ID, "status": "online", "timestamp": time.time()}),
                qos=1, retain=True,
            )
            # Subscribe to siren commands and admin commands
            client.subscribe("farm/siren/trigger", qos=1)
            client.subscribe("farm/siren/stop", qos=1)
            client.subscribe("farm/admin/update", qos=1)
            client.subscribe("farm/admin/reload_config", qos=1)
            client.subscribe("farm/admin/model_update", qos=1)
            log.info("Subscribed to siren and admin topics (incl. reload_config, model_update)")
        else:
            log.error("MQTT connection failed with rc=%d", rc)

    def on_disconnect(client, userdata, rc):
        if rc != 0:
            log.warning("Unexpected MQTT disconnect (rc=%d). Auto-reconnecting...", rc)

    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.reconnect_delay_set(min_delay=1, max_delay=30)

    return client


def setup_siren_listener(mqtt_client, zone_engine, pipeline_holder):
    """
    Subscribe to siren commands from cloud/Android app.
    Forwards to local PA system (AlertManagement controller on Pi).
    """
    # Local MQTT client for PA controller (if Pi is on same network)
    pa_client = None
    PA_BROKER = os.getenv("PA_MQTT_BROKER", "")

    if PA_BROKER:
        pa_client = mqtt.Client(client_id=f"{NODE_ID}-pa-bridge")
        try:
            pa_client.connect(PA_BROKER, 1883)
            pa_client.loop_start()
            log.info("Connected to local PA controller at %s", PA_BROKER)
        except Exception as e:
            log.warning("Cannot connect to PA controller: %s", e)
            pa_client = None

    def on_siren_message(client, userdata, msg):
        topic = msg.topic
        try:
            payload = json.loads(msg.payload.decode())
        except (json.JSONDecodeError, UnicodeDecodeError):
            log.error("Invalid siren command payload")
            return

        log.info("SIREN COMMAND received: %s on %s", payload, topic)

        if "trigger" in topic:
            # Forward to PA controller
            if pa_client:
                pa_client.publish("pa/command", json.dumps({
                    "command": "play_siren",
                    "url": payload.get("siren_url", "http://localhost/audio/siren.mp3"),
                }), qos=1)

            # Acknowledge back to cloud
            client.publish("farm/siren/ack", json.dumps({
                "node_id": NODE_ID,
                "status": "siren_activated",
                "timestamp": time.time(),
            }), qos=1)

        elif "stop" in topic:
            if pa_client:
                pa_client.publish("pa/command", json.dumps({"command": "stop"}), qos=1)

            client.publish("farm/siren/ack", json.dumps({
                "node_id": NODE_ID,
                "status": "siren_stopped",
                "timestamp": time.time(),
            }), qos=1)

    def combined(client, userdata, msg):
        if msg.topic == "farm/admin/reload_config":
            _mqtt_admin_reload(zone_engine, client)
        elif msg.topic == "farm/admin/model_update":
            _mqtt_model_update(client, msg, pipeline_holder)
        else:
            on_siren_message(client, userdata, msg)

    mqtt_client.on_message = combined


def start_heartbeat(mqtt_client, pipeline=None, alert_engine=None):
    """Publish periodic heartbeat with GPU/system stats and pipeline metrics."""
    def heartbeat_loop():
        while True:
            try:
                gpu_info = {}
                try:
                    import subprocess
                    result = subprocess.run(
                        ["nvidia-smi", "--query-gpu=utilization.gpu,memory.used,temperature.gpu",
                         "--format=csv,noheader,nounits"],
                        capture_output=True, text=True, timeout=5,
                    )
                    if result.returncode == 0:
                        parts = result.stdout.strip().split(", ")
                        gpu_info = {
                            "gpu_util": float(parts[0]),
                            "gpu_mem_mb": float(parts[1]),
                            "gpu_temp_c": float(parts[2]),
                        }
                except Exception:
                    pass

                pipeline_stats = pipeline.get_stats() if pipeline else {}
                engine_stats = alert_engine.get_stats() if alert_engine else {}

                mqtt_client.publish(
                    f"farm/nodes/{NODE_ID}/heartbeat",
                    json.dumps({
                        "node_id": NODE_ID,
                        "timestamp": time.time(),
                        "status": "online",
                        **gpu_info,
                        "pipeline": pipeline_stats,
                        "engine": engine_stats,
                    }),
                    qos=0,
                )
            except Exception as e:
                log.warning("Heartbeat error: %s", e)

            time.sleep(30)

    t = threading.Thread(target=heartbeat_loop, daemon=True, name="heartbeat")
    t.start()


def main():
    log.info("=" * 70)
    log.info("  SudarshanChakra Edge Node: %s", NODE_ID)
    if DEV_MODE:
        log.info("  *** DEV MODE — No GPU, Mock Cameras, Mock LoRa, Mock Siren ***")
    log.info("  VPN Broker: %s:%d", VPN_BROKER_IP, MQTT_PORT)
    log.info("  Config Dir: %s", CONFIG_DIR)
    log.info("  Model Dir:  %s", MODEL_DIR)
    log.info("  LoRa:       %s", "Mock" if MOCK_LORA else ("Enabled" if LORA_ENABLED else "Disabled"))
    log.info("=" * 70)

    # ── Step 1: Load YOLO model (skip in dev mode) ──
    model = None
    if not (DEV_MODE or MOCK_CAMERAS):
        log.info("Loading YOLO model (first run will build TensorRT engine)...")
        model = load_model()
        log.info("Model loaded successfully.")
    else:
        log.info("DEV MODE: Skipping YOLO model load — using mock detections")

    # ── Step 2: Load camera configs ──
    cameras = load_camera_configs()

    # ── Step 3: Initialize zone engine ──
    zone_engine = ZoneEngine(os.path.join(CONFIG_DIR, "zones.json"))
    log.info("Zone engine loaded with %d zone definitions.", len(zone_engine.polygons))

    pipeline_holder = {}
    try:
        from config_watcher import start_zone_reload_watcher
        start_zone_reload_watcher(
            zone_engine,
            [
                os.path.join(CONFIG_DIR, "zones.json"),
                os.path.join(CONFIG_DIR, "suppression_rules.json"),
            ],
        )
        log.info("File watcher: zones.json + suppression_rules.json")
    except Exception as ex:
        log.debug("config watcher skipped: %s", ex)

    # ── Step 4: Initialize LoRa receiver (mock in dev mode) ──
    if MOCK_LORA or DEV_MODE:
        lora = MockLoRaReceiver(
            authorized_tags_path=os.path.join(CONFIG_DIR, "authorized_tags.json"),
            worker_present=True,  # Set False to test intruder alerts
        )
        lora.start()
        log.info("MOCK LoRa receiver started (worker_present=True)")
    elif LORA_ENABLED:
        lora = LoRaReceiver(port=LORA_PORT)
        lora.start()
        log.info("LoRa receiver started on %s", LORA_PORT)
    else:
        lora = None

    # ── Step 5: Connect to MQTT broker ──
    mqtt_client = create_mqtt_client()

    # Siren handler (mock in dev mode)
    if MOCK_SIREN or DEV_MODE:
        mock_siren = MockSirenHandler(mqtt_client, NODE_ID)

        def dev_route(client, userdata, msg):
            if msg.topic == "farm/admin/reload_config":
                _mqtt_admin_reload(zone_engine, client)
            elif msg.topic == "farm/admin/model_update":
                _mqtt_model_update(client, msg, pipeline_holder)
            else:
                mock_siren.handle_command(client, userdata, msg)

        mqtt_client.on_message = dev_route
        log.info("MOCK siren + admin MQTT handlers installed")
    else:
        setup_siren_listener(mqtt_client, zone_engine, pipeline_holder)

    # Dev mode: subscribe to simulation commands
    if DEV_MODE:
        def on_dev_command(client, userdata, msg):
            """Handle dev simulation commands."""
            topic = msg.topic
            try:
                payload = json.loads(msg.payload.decode())
            except Exception:
                payload = {}

            if "simulate/fall" in topic and lora and hasattr(lora, 'simulate_fall'):
                tag_id = payload.get("tag_id", "TAG-C001")
                lora.simulate_fall(tag_id)
                log.info("DEV: Simulated fall event for %s", tag_id)

            elif "simulate/worker_toggle" in topic and lora and hasattr(lora, 'set_worker_present'):
                present = payload.get("present", True)
                lora.set_worker_present(present)
                log.info("DEV: Worker presence set to %s", present)

        # Will be subscribed after connect

    while True:
        try:
            mqtt_client.connect(VPN_BROKER_IP, MQTT_PORT, keepalive=60)
            break
        except (ConnectionRefusedError, OSError) as e:
            log.error("Cannot reach broker at %s: %s. Retrying in 5s...", VPN_BROKER_IP, e)
            time.sleep(5)

    mqtt_client.loop_start()

    if not (DEV_MODE or MOCK_CAMERAS):
        from pipeline import set_mqtt_camera_events
        set_mqtt_camera_events(mqtt_client)

    # Subscribe to dev simulation topics
    if DEV_MODE:
        mqtt_client.subscribe("dev/simulate/#", qos=1)
        mqtt_client.message_callback_add("dev/simulate/#", on_dev_command)
        log.info("DEV: Listening for simulation commands on dev/simulate/#")
        log.info("DEV: Trigger fall:   mosquitto_pub -t dev/simulate/fall -m '{\"tag_id\":\"TAG-C001\"}'")
        log.info("DEV: Toggle worker:  mosquitto_pub -t dev/simulate/worker_toggle -m '{\"present\":false}'")

    # ── Step 6 & 7: Initialize alert decision engine ──
    class DummyLoRa:
        def is_worker_nearby(self, max_age_seconds=15.0): return False
        def get_nearby_workers(self, max_age_seconds=15.0): return []

    alert_engine = AlertDecisionEngine(
        zone_engine=zone_engine,
        lora_receiver=lora if lora else DummyLoRa(),
        mqtt_client=mqtt_client,
        node_id=NODE_ID,
    )

    # ── Step 7b: Wire fall detector to alert engine ──
    if lora and hasattr(lora, 'fall_callbacks'):
        lora.fall_callbacks.append(alert_engine.process_fall_event)
        log.info("Fall detector callback wired: LoRa → AlertEngine")

    # ── Step 8: Build pipeline (before Flask so /health and /api/status see it) ──
    pt_path = os.getenv("PT_PATH", os.path.join(MODEL_DIR, "yolov8n_farm.pt"))
    if DEV_MODE or MOCK_CAMERAS:
        detection_interval = float(os.getenv("MOCK_DETECTION_INTERVAL", "5"))
        log.info("MOCK inference pipeline (detection every %.0fs)...", detection_interval)
        pipeline = MockInferencePipeline(cameras, detection_interval=detection_interval)
    else:
        log.info("Inference pipeline with %d cameras...", len(cameras))
        pipeline = InferencePipeline(model, cameras)

    pipeline_holder["pipeline"] = pipeline

    flask_app = create_app(
        zone_engine, cameras, CONFIG_DIR,
        mqtt_client=mqtt_client, pipeline=pipeline,
        model_pt_path=pt_path, node_id=NODE_ID,
        alert_engine=alert_engine,
    )
    flask_thread = threading.Thread(
        target=lambda: flask_app.run(host="0.0.0.0", port=FLASK_PORT, debug=False),
        daemon=True, name="flask-gui",
    )
    flask_thread.start()
    log.info("Flask Edge GUI started on port %d", FLASK_PORT)

    pipeline.results_callbacks.append(alert_engine.process_detection)
    start_heartbeat(mqtt_client, pipeline=pipeline, alert_engine=alert_engine)

    # Graceful shutdown
    def shutdown(signum, frame):
        log.info("Shutting down edge node...")
        mqtt_client.loop_stop()
        mqtt_client.disconnect()
        sys.exit(0)

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)

    try:
        pipeline.start()  # Blocking — runs inference loop
    except KeyboardInterrupt:
        shutdown(None, None)


if __name__ == "__main__":
    main()
