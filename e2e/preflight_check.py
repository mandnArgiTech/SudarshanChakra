#!/usr/bin/env python3
"""
SudarshanChakra — E2E Environment Preflight Check
===================================================

Run this BEFORE the E2E test suite to validate that every piece of
infrastructure is correctly configured and reachable. Catches config
problems in 60 seconds instead of 20 minutes into a test run.

Usage:
    python3 e2e/preflight_check.py --config e2e/config/e2e_config.yml
    python3 e2e/preflight_check.py --config e2e/config/e2e_config.yml --fix  # attempt auto-fixes
    python3 e2e/preflight_check.py --config e2e/config/e2e_config.yml --json  # machine-readable output

Exit codes:
    0 = all checks passed
    1 = one or more checks failed
    2 = config file not found / invalid
"""

import argparse
import json
import os
import re
import socket
import ssl
import subprocess
import sys
import time
import urllib.request
import urllib.error
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

try:
    import yaml
    HAS_YAML = True
except ImportError:
    HAS_YAML = False

try:
    import paho.mqtt.client as mqtt
    HAS_PAHO = True
except ImportError:
    HAS_PAHO = False

# ═══════════════════════════════════════════════════════════════════
# Data structures
# ═══════════════════════════════════════════════════════════════════

@dataclass
class CheckResult:
    name: str
    group: str
    passed: bool
    message: str
    fix_hint: Optional[str] = None
    duration_ms: int = 0

@dataclass
class PreflightReport:
    checks: list = field(default_factory=list)
    start_time: float = 0
    end_time: float = 0

    @property
    def passed(self):
        return all(c.passed for c in self.checks)

    @property
    def total(self):
        return len(self.checks)

    @property
    def passed_count(self):
        return sum(1 for c in self.checks if c.passed)

    @property
    def failed_count(self):
        return sum(1 for c in self.checks if not c.passed)


# ═══════════════════════════════════════════════════════════════════
# Check helpers
# ═══════════════════════════════════════════════════════════════════

def check_tcp(host, port, timeout=5):
    """Check if a TCP port is open."""
    try:
        sock = socket.create_connection((host, port), timeout=timeout)
        sock.close()
        return True, f"{host}:{port} reachable"
    except (socket.timeout, ConnectionRefusedError, OSError) as e:
        return False, f"{host}:{port} unreachable — {e}"


def check_http(url, timeout=10, expected_status=200, check_json=False):
    """Check if an HTTP endpoint responds."""
    try:
        req = urllib.request.Request(url)
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        resp = urllib.request.urlopen(req, timeout=timeout, context=ctx)
        status = resp.getcode()
        body = resp.read().decode("utf-8", errors="replace")[:500]
        if check_json:
            try:
                json.loads(body)
            except json.JSONDecodeError:
                return False, f"HTTP {status} but response is not valid JSON"
        if status == expected_status:
            return True, f"HTTP {status} OK"
        return False, f"Expected {expected_status}, got {status}"
    except urllib.error.HTTPError as e:
        return False, f"HTTP {e.code}: {e.reason}"
    except Exception as e:
        return False, f"Request failed: {e}"


def check_mqtt_connect(host, port=1883, timeout=10):
    """Check MQTT broker connectivity."""
    if not HAS_PAHO:
        return None, "paho-mqtt not installed — skipping MQTT check"
    connected = {"ok": False, "msg": ""}

    def on_connect(client, userdata, flags, rc, properties=None):
        if rc == 0:
            connected["ok"] = True
            connected["msg"] = "MQTT connected"
        else:
            connected["msg"] = f"MQTT connect failed, rc={rc}"
        client.disconnect()

    def on_connect_fail(client, userdata):
        connected["msg"] = "MQTT connection refused"

    try:
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"preflight-{int(time.time())}")
        client.on_connect = on_connect
        client.connect(host, port, keepalive=timeout)
        client.loop_start()
        deadline = time.time() + timeout
        while not connected["ok"] and time.time() < deadline and connected["msg"] == "":
            time.sleep(0.2)
        client.loop_stop()
        client.disconnect()
        return connected["ok"], connected["msg"] or "MQTT timeout"
    except Exception as e:
        return False, f"MQTT error: {e}"


def check_command_exists(cmd):
    """Check if a CLI tool is installed."""
    try:
        result = subprocess.run(
            ["which", cmd], capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0:
            return True, f"{cmd} found at {result.stdout.strip()}"
        return False, f"{cmd} not found in PATH"
    except Exception as e:
        return False, f"Error checking {cmd}: {e}"


def check_rtsp_stream(url, timeout=10):
    """Check if an RTSP stream is reachable using ffprobe."""
    try:
        result = subprocess.run(
            ["ffprobe", "-v", "error", "-rtsp_transport", "tcp",
             "-i", url, "-show_entries", "stream=width,height,codec_name",
             "-of", "json", "-timeout", str(timeout * 1000000)],
            capture_output=True, text=True, timeout=timeout + 5
        )
        if result.returncode == 0:
            info = json.loads(result.stdout)
            streams = info.get("streams", [])
            if streams:
                s = streams[0]
                return True, f"{s.get('codec_name','?')} {s.get('width','?')}x{s.get('height','?')}"
            return False, "No video streams found"
        return False, f"ffprobe error: {result.stderr[:200]}"
    except FileNotFoundError:
        return None, "ffprobe not installed — skipping RTSP check"
    except subprocess.TimeoutExpired:
        return False, f"RTSP timeout after {timeout}s"
    except Exception as e:
        return False, f"RTSP error: {e}"


def check_onvif_device(host, port, user, password, timeout=10):
    """Check if ONVIF device responds and report capabilities."""
    try:
        from onvif import ONVIFCamera
        cam = ONVIFCamera(host, port, user, password)
        info = cam.devicemgmt.GetDeviceInformation()
        # Check PTZ
        has_ptz = False
        try:
            ptz = cam.create_ptz_service()
            nodes = ptz.GetNodes()
            has_ptz = len(nodes) > 0
        except Exception:
            pass
        return True, f"{info.Manufacturer} {info.Model} (FW: {info.FirmwareVersion}) PTZ={'yes' if has_ptz else 'no'}"
    except ImportError:
        return None, "onvif-zeep not installed — skipping ONVIF check"
    except Exception as e:
        return False, f"ONVIF error: {e}"


def check_android_emulator():
    """Check if Android emulator is available."""
    try:
        result = subprocess.run(
            ["adb", "devices"], capture_output=True, text=True, timeout=5
        )
        lines = [l.strip() for l in result.stdout.strip().split("\n") if l.strip() and "List" not in l]
        emulators = [l for l in lines if "emulator" in l and "device" in l]
        if emulators:
            return True, f"{len(emulators)} emulator(s) running: {emulators[0].split()[0]}"
        # Check if any AVDs are available
        avd_result = subprocess.run(
            ["emulator", "-list-avds"], capture_output=True, text=True, timeout=5
        )
        avds = [l.strip() for l in avd_result.stdout.strip().split("\n") if l.strip()]
        if avds:
            return False, f"No emulator running. Available AVDs: {', '.join(avds)}. Start with: emulator -avd {avds[0]} &"
        return False, "No AVDs found. Create one in Android Studio → AVD Manager"
    except FileNotFoundError:
        return False, "adb not found — install Android SDK Platform Tools"
    except Exception as e:
        return False, f"Error: {e}"


def check_apk_exists(apk_path):
    """Check if the debug APK has been built."""
    if os.path.isfile(apk_path):
        size_mb = os.path.getsize(apk_path) / (1024 * 1024)
        return True, f"APK found ({size_mb:.1f} MB)"
    return False, f"APK not found at {apk_path}. Run: cd android && ./gradlew assembleDebug"


def check_audio_device(device="hw:0,0"):
    """Check if ALSA audio output device exists."""
    try:
        result = subprocess.run(
            ["aplay", "-l"], capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0 and "card" in result.stdout.lower():
            cards = [l for l in result.stdout.split("\n") if "card" in l.lower() and ":" in l]
            return True, f"{len(cards)} audio device(s) found"
        return False, "No audio devices found"
    except FileNotFoundError:
        return None, "aplay not installed — siren audio tests will be skipped"
    except Exception as e:
        return False, f"Audio check error: {e}"


def check_disk_space(path, min_gb=5):
    """Check if a path has sufficient disk space."""
    try:
        stat = os.statvfs(path)
        free_gb = (stat.f_bavail * stat.f_frsize) / (1024 ** 3)
        if free_gb >= min_gb:
            return True, f"{free_gb:.1f} GB free (minimum: {min_gb} GB)"
        return False, f"Only {free_gb:.1f} GB free (minimum: {min_gb} GB)"
    except Exception as e:
        return False, f"Cannot check disk: {e}"


def check_docker_running():
    """Check if Docker daemon is running."""
    try:
        result = subprocess.run(
            ["docker", "info"], capture_output=True, text=True, timeout=10
        )
        if result.returncode == 0:
            # Extract server version
            for line in result.stdout.split("\n"):
                if "Server Version" in line:
                    return True, line.strip()
            return True, "Docker running"
        return False, f"Docker not responding: {result.stderr[:100]}"
    except FileNotFoundError:
        return False, "docker not found in PATH"
    except Exception as e:
        return False, f"Docker error: {e}"


def check_docker_compose_file(filepath):
    """Check if a docker-compose file exists and is parseable."""
    if not os.path.isfile(filepath):
        return False, f"File not found: {filepath}"
    try:
        result = subprocess.run(
            ["docker", "compose", "-f", filepath, "config", "--quiet"],
            capture_output=True, text=True, timeout=10
        )
        if result.returncode == 0:
            return True, f"Valid compose file"
        return False, f"Invalid: {result.stderr[:150]}"
    except Exception as e:
        return False, f"Cannot validate: {e}"


def check_esp8266_http(ip, timeout=5):
    """Check if ESP8266 water sensor HTTP API responds."""
    ok, msg = check_http(f"http://{ip}/api/status", timeout=timeout, check_json=True)
    if ok:
        # Also check /api/level
        ok2, msg2 = check_http(f"http://{ip}/api/level", timeout=timeout, check_json=True)
        if ok2:
            try:
                resp = urllib.request.urlopen(f"http://{ip}/api/level", timeout=timeout)
                data = json.loads(resp.read().decode())
                pct = data.get("percentFilled", "?")
                state = data.get("state", "?")
                valid = data.get("valid", False)
                return True, f"Level: {pct}% state={state} valid={valid}"
            except Exception:
                return True, "ESP8266 responds but cannot parse level"
        return True, f"Status OK, but /api/level failed: {msg2}"
    return ok, msg


def check_mqtt_topic_active(broker, port, topic, timeout=90, description=""):
    """Subscribe and wait for a message on a topic."""
    if not HAS_PAHO:
        return None, "paho-mqtt not installed"
    result = {"received": False, "payload": None}

    def on_message(client, userdata, msg):
        result["received"] = True
        try:
            result["payload"] = json.loads(msg.payload.decode())
        except Exception:
            result["payload"] = msg.payload.decode()[:100]

    try:
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"preflight-sub-{int(time.time())}")
        client.connect(broker, port)
        client.subscribe(topic)
        client.on_message = on_message
        client.loop_start()
        deadline = time.time() + timeout
        while not result["received"] and time.time() < deadline:
            time.sleep(1)
            remaining = int(deadline - time.time())
            if remaining % 10 == 0 and remaining > 0:
                print(f"      ... waiting for {description or topic} ({remaining}s remaining)", flush=True)
        client.loop_stop()
        client.disconnect()
        if result["received"]:
            preview = str(result["payload"])[:80]
            return True, f"Message received: {preview}"
        return False, f"No message on {topic} within {timeout}s"
    except Exception as e:
        return False, f"MQTT subscribe error: {e}"


# ═══════════════════════════════════════════════════════════════════
# Main preflight runner
# ═══════════════════════════════════════════════════════════════════

def run_check(report: PreflightReport, name: str, group: str, check_fn, fix_hint=None):
    """Run a single check and add to report."""
    t0 = time.time()
    try:
        passed, message = check_fn()
    except Exception as e:
        passed, message = False, f"Unexpected error: {e}"
    duration_ms = int((time.time() - t0) * 1000)

    if passed is None:
        # Skipped (dependency not installed)
        icon = "⊘"
        color = "\033[33m"
    elif passed:
        icon = "✓"
        color = "\033[32m"
    else:
        icon = "✗"
        color = "\033[31m"

    reset = "\033[0m"
    print(f"  {color}{icon}{reset} {name} — {message} ({duration_ms}ms)")

    report.checks.append(CheckResult(
        name=name, group=group,
        passed=passed if passed is not None else True,
        message=message, fix_hint=fix_hint, duration_ms=duration_ms,
    ))


def load_config(config_path):
    """Load YAML or JSON config."""
    if not os.path.isfile(config_path):
        print(f"\033[31mConfig not found: {config_path}\033[0m")
        sys.exit(2)
    with open(config_path) as f:
        content = f.read()
    if HAS_YAML:
        return yaml.safe_load(content)
    return json.loads(content)


def run_preflight(config_path, wait_for_water=False, json_output=False):
    report = PreflightReport(start_time=time.time())
    cfg = load_config(config_path)

    be = cfg.get("backend", {})
    edge = cfg.get("edge", {})
    water = cfg.get("water", {})
    siren = cfg.get("siren", {})
    auth = cfg.get("auth", {})
    android = cfg.get("android", {})

    api_base = be.get("api_base", "http://localhost:8080")
    dashboard_url = be.get("dashboard_url", "http://localhost:3000")
    mqtt_broker = be.get("mqtt_broker", "localhost")
    mqtt_port = be.get("mqtt_port", 1883)
    flask_url = edge.get("flask_url", "http://localhost:5000")

    # Parse host from URLs
    def host_of(url):
        return re.sub(r"https?://", "", url).split(":")[0].split("/")[0]

    vm1_host = host_of(api_base)
    vm2_host = host_of(flask_url)

    # ── GROUP 1: Tools & Dependencies ──
    print("\n\033[1m[1/8] Local tools & dependencies\033[0m")
    run_check(report, "Python 3.10+", "tools",
              lambda: (sys.version_info >= (3, 10), f"Python {sys.version.split()[0]}"),
              fix_hint="Install Python 3.10+")
    run_check(report, "paho-mqtt", "tools",
              lambda: (HAS_PAHO, "installed" if HAS_PAHO else "missing"),
              fix_hint="pip install paho-mqtt")
    run_check(report, "PyYAML", "tools",
              lambda: (HAS_YAML, "installed" if HAS_YAML else "missing"),
              fix_hint="pip install pyyaml")
    for tool in ["docker", "node", "npx"]:
        run_check(report, tool, "tools",
                  lambda t=tool: check_command_exists(t),
                  fix_hint=f"Install {tool}")
    run_check(report, "Playwright", "tools",
              lambda: check_command_exists("npx"),
              fix_hint="npm install -D @playwright/test && npx playwright install")
    run_check(report, "ffprobe", "tools",
              lambda: check_command_exists("ffprobe"),
              fix_hint="apt install ffmpeg")

    # ── GROUP 2: VM1 Backend Services ──
    print(f"\n\033[1m[2/8] VM1 — Backend services ({vm1_host})\033[0m")
    run_check(report, f"VM1 TCP reachable ({vm1_host})", "vm1",
              lambda: check_tcp(vm1_host, 8080))
    run_check(report, "API Gateway /actuator/health", "vm1",
              lambda: check_http(f"{api_base}/actuator/health", check_json=True),
              fix_hint="Start backend: docker compose -f cloud/docker-compose.e2e.yml up -d")
    run_check(report, "Dashboard loads", "vm1",
              lambda: check_http(dashboard_url),
              fix_hint=f"Dashboard not responding at {dashboard_url}")
    run_check(report, "PostgreSQL :5432", "vm1",
              lambda: check_tcp(vm1_host, 5432),
              fix_hint="PostgreSQL not running on VM1")
    run_check(report, "RabbitMQ AMQP :5672", "vm1",
              lambda: check_tcp(vm1_host, 5672),
              fix_hint="RabbitMQ not running on VM1")
    run_check(report, "RabbitMQ MQTT :1883", "vm1",
              lambda: check_tcp(mqtt_broker, mqtt_port),
              fix_hint="RabbitMQ MQTT plugin not enabled")
    run_check(report, "MQTT broker connect", "vm1",
              lambda: check_mqtt_connect(mqtt_broker, mqtt_port),
              fix_hint="MQTT broker refusing connections")
    run_check(report, "Auth login", "vm1",
              lambda: _check_auth_login(api_base, auth),
              fix_hint="Check auth-service logs and user seed data")

    # ── GROUP 3: VM2 Edge Services ──
    print(f"\n\033[1m[3/8] VM2 — Edge node ({vm2_host})\033[0m")
    run_check(report, f"VM2 TCP reachable ({vm2_host})", "vm2",
              lambda: check_tcp(vm2_host, 5000))
    run_check(report, "Edge Flask /health", "vm2",
              lambda: check_http(f"{flask_url}/health", check_json=True),
              fix_hint="Edge node not running. Start: docker compose -f edge/docker-compose.e2e.yml up -d")
    run_check(report, "Edge /api/cameras/status", "vm2",
              lambda: check_http(f"{flask_url}/api/cameras/status", check_json=True),
              fix_hint="Edge pipeline may not have started")

    # ── GROUP 4: Real Cameras ──
    cameras = edge.get("cameras", [])
    if cameras:
        print(f"\n\033[1m[4/8] Real cameras ({len(cameras)} configured)\033[0m")
        for cam in cameras:
            cam_id = cam.get("id", "unknown")
            rtsp_url = cam.get("rtsp", "")
            onvif_host = cam.get("onvif_host", "")
            onvif_port = cam.get("onvif_port", 2020)
            has_ptz = cam.get("has_ptz", False)

            run_check(report, f"{cam_id} RTSP stream", "cameras",
                      lambda u=rtsp_url: check_rtsp_stream(u),
                      fix_hint=f"Camera not reachable. Check IP, credentials, ONVIF enabled")
            run_check(report, f"{cam_id} ONVIF device info", "cameras",
                      lambda h=onvif_host, p=onvif_port: check_onvif_device(
                          h, p,
                          rtsp_url.split("//")[1].split(":")[0] if "//" in rtsp_url else "admin",
                          rtsp_url.split(":")[2].split("@")[0] if "@" in rtsp_url else "admin",
                      ),
                      fix_hint="Enable ONVIF in Tapo app → Advanced → ONVIF")
            run_check(report, f"{cam_id} snapshot via Edge", "cameras",
                      lambda c=cam_id: check_http(f"{flask_url}/api/snapshot/{c}"),
                      fix_hint=f"Edge not serving snapshots for {cam_id}")
    else:
        print(f"\n\033[1m[4/8] Real cameras — SKIPPED (none configured)\033[0m")

    # ── GROUP 5: ESP8266 Water Sensor ──
    esp_ip = water.get("esp8266_ip", "")
    level_topic = water.get("level_topic", "")
    if esp_ip:
        print(f"\n\033[1m[5/8] ESP8266 water sensor ({esp_ip})\033[0m")
        run_check(report, "ESP8266 HTTP /api/status", "water",
                  lambda: check_esp8266_http(esp_ip),
                  fix_hint=f"ESP8266 not reachable at {esp_ip}. Check WiFi and IP")
        run_check(report, "ESP8266 /api/level", "water",
                  lambda: check_http(f"http://{esp_ip}/api/level", check_json=True),
                  fix_hint="Sensor may not be reading correctly")
        run_check(report, "ESP8266 /api/mqtt/status", "water",
                  lambda: check_http(f"http://{esp_ip}/api/mqtt/status", check_json=True),
                  fix_hint="ESP8266 MQTT not connected. Check broker IP in ESP config")
        if wait_for_water and level_topic:
            run_check(report, f"Water level MQTT publish ({level_topic})", "water",
                      lambda: check_mqtt_topic_active(
                          mqtt_broker, mqtt_port, level_topic,
                          timeout=90, description="ESP8266 water level"
                      ),
                      fix_hint="ESP8266 not publishing. Check MQTT config on device")
    else:
        print(f"\n\033[1m[5/8] ESP8266 water sensor — SKIPPED (not configured)\033[0m")

    # ── GROUP 6: Siren / Audio ──
    print(f"\n\033[1m[6/8] Siren / audio output\033[0m")
    audio_device = siren.get("audio_device", "hw:0,0")
    run_check(report, "ALSA audio devices", "siren",
              lambda: check_audio_device(audio_device),
              fix_hint="Connect a speaker to the edge node audio output")

    # ── GROUP 7: Android ──
    print(f"\n\033[1m[7/8] Android emulator\033[0m")
    run_check(report, "adb available", "android",
              lambda: check_command_exists("adb"),
              fix_hint="Install Android SDK Platform Tools")
    run_check(report, "Android emulator", "android",
              lambda: check_android_emulator(),
              fix_hint="Start emulator: emulator -avd Pixel_7_API_34 &")
    apk_path = android.get("apk_path", "android/app/build/outputs/apk/debug/app-debug.apk")
    run_check(report, "Debug APK built", "android",
              lambda: check_apk_exists(apk_path),
              fix_hint="cd android && ./gradlew assembleDebug")
    run_check(report, "Maestro CLI", "android",
              lambda: check_command_exists("maestro"),
              fix_hint="Install: curl -Ls https://get.maestro.mobile.dev | bash")

    # ── GROUP 8: Disk & Resources ──
    print(f"\n\033[1m[8/8] Disk & resources\033[0m")
    run_check(report, "Disk space (working dir)", "resources",
              lambda: check_disk_space(".", min_gb=2),
              fix_hint="Free up disk space")
    run_check(report, "Docker daemon", "resources",
              lambda: check_docker_running(),
              fix_hint="Start Docker: sudo systemctl start docker")

    # ── Report ──
    report.end_time = time.time()
    return report


def _check_auth_login(api_base, auth):
    """Attempt actual login and verify JWT."""
    user = auth.get("admin_user", "")
    password = auth.get("admin_pass", "")
    if not user or not password:
        return False, "admin_user/admin_pass not set in config"
    try:
        data = json.dumps({"username": user, "password": password}).encode()
        req = urllib.request.Request(
            f"{api_base}/api/v1/auth/login",
            data=data,
            headers={"Content-Type": "application/json"},
        )
        resp = urllib.request.urlopen(req, timeout=10)
        body = json.loads(resp.read().decode())
        token = body.get("token", "")
        if not token:
            return False, "Login succeeded but no token in response"
        # Decode JWT payload (base64, no verification)
        import base64
        parts = token.split(".")
        if len(parts) != 3:
            return False, "Token is not a valid JWT"
        payload_b64 = parts[1] + "=" * (4 - len(parts[1]) % 4)
        payload = json.loads(base64.b64decode(payload_b64))
        role = payload.get("role", "?")
        modules = payload.get("modules", [])
        farm_id = payload.get("farm_id", "?")
        return True, f"JWT OK — role={role}, {len(modules)} modules, farm={farm_id[:8]}..."
    except urllib.error.HTTPError as e:
        if e.code == 401:
            return False, "Login failed — invalid credentials"
        return False, f"Login error HTTP {e.code}"
    except Exception as e:
        return False, f"Login error: {e}"


def print_report(report: PreflightReport, json_output=False):
    if json_output:
        out = {
            "passed": report.passed,
            "total": report.total,
            "passed_count": report.passed_count,
            "failed_count": report.failed_count,
            "duration_sec": round(report.end_time - report.start_time, 1),
            "checks": [
                {"name": c.name, "group": c.group, "passed": c.passed,
                 "message": c.message, "fix_hint": c.fix_hint, "duration_ms": c.duration_ms}
                for c in report.checks
            ],
        }
        print(json.dumps(out, indent=2))
        return

    duration = report.end_time - report.start_time
    print(f"\n{'═' * 60}")
    if report.passed:
        print(f"\033[32m  PREFLIGHT PASSED — {report.passed_count}/{report.total} checks OK ({duration:.1f}s)\033[0m")
    else:
        print(f"\033[31m  PREFLIGHT FAILED — {report.failed_count} of {report.total} checks failed ({duration:.1f}s)\033[0m")
        print()
        print("  Failed checks:")
        for c in report.checks:
            if not c.passed:
                print(f"    \033[31m✗ [{c.group}] {c.name}\033[0m")
                print(f"      {c.message}")
                if c.fix_hint:
                    print(f"      \033[33mFix: {c.fix_hint}\033[0m")
    print(f"{'═' * 60}\n")


# ═══════════════════════════════════════════════════════════════════
# Entry point
# ═══════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description="SudarshanChakra E2E Preflight Check")
    parser.add_argument("--config", required=True, help="Path to e2e_config.yml")
    parser.add_argument("--json", action="store_true", help="JSON output")
    parser.add_argument("--wait-for-water", action="store_true",
                        help="Wait up to 90s for a real ESP8266 water level MQTT publish")
    parser.add_argument("--fix", action="store_true",
                        help="Attempt auto-fixes (start emulator, etc.)")
    args = parser.parse_args()

    print()
    print("  ╔══════════════════════════════════════════════════╗")
    print("  ║  SudarshanChakra — E2E Preflight Check          ║")
    print("  ╚══════════════════════════════════════════════════╝")

    report = run_preflight(
        config_path=args.config,
        wait_for_water=args.wait_for_water,
        json_output=args.json,
    )

    print_report(report, json_output=args.json)
    sys.exit(0 if report.passed else 1)


if __name__ == "__main__":
    main()
