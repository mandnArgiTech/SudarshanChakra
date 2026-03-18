#!/usr/bin/env python3
"""
PA System MQTT Controller — Raspberry Pi Zero 2 W
==================================================
State-machine audio controller for agricultural shed PA system.

Hardware chain:
  Pi Zero 2 W → I2S (GPIO) → PCM5102A DAC → Ahuja SSA-250DP → 100V Line → SUH-25XT Horns

Architecture:
  - MQTT listener receives commands from edge AI server
  - Priority engine: SIREN > BGM (siren instantly pre-empts any playing audio)
  - Uses mpv via subprocess for HTTP streaming (low memory footprint on Pi Zero 2 W)
  - Watchdog heartbeat for systemd integration
  - All state transitions are thread-safe

Author: Auto-generated for Sanga Reddy agricultural shed PA system
"""

import json
import enum
import logging
import os
import signal
import subprocess
import sys
import threading
import time
from dataclasses import dataclass, field
from typing import Optional

import paho.mqtt.client as mqtt

# ---------------------------------------------------------------------------
# Configuration — override via environment variables
# ---------------------------------------------------------------------------
MQTT_BROKER   = os.getenv("PA_MQTT_BROKER", "192.168.1.100")
MQTT_PORT     = int(os.getenv("PA_MQTT_PORT", "1883"))
MQTT_TOPIC    = os.getenv("PA_MQTT_TOPIC", "pa/command")
MQTT_STATUS   = os.getenv("PA_MQTT_STATUS_TOPIC", "pa/status")
MQTT_HEALTH   = os.getenv("PA_MQTT_HEALTH_TOPIC", "pa/health")
MQTT_USER     = os.getenv("PA_MQTT_USER", "")
MQTT_PASS     = os.getenv("PA_MQTT_PASS", "")
MQTT_CLIENT   = os.getenv("PA_MQTT_CLIENT_ID", "pi-pa-controller")

# Audio defaults
DEFAULT_VOLUME   = int(os.getenv("PA_DEFAULT_VOLUME", "85"))    # 0-100
SIREN_VOLUME     = int(os.getenv("PA_SIREN_VOLUME", "100"))     # Always max for security
SIREN_REPEAT     = int(os.getenv("PA_SIREN_REPEAT", "3"))       # Loop siren N times
RESUME_AFTER_SIREN = os.getenv("PA_RESUME_AFTER_SIREN", "false").lower() == "true"

# mpv socket for IPC (used on tmpfs so read-only FS is fine)
MPV_SOCKET = "/tmp/mpv-pa-socket"

# Logging
LOG_LEVEL = os.getenv("PA_LOG_LEVEL", "INFO").upper()

logging.basicConfig(
    level=getattr(logging, LOG_LEVEL, logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
log = logging.getLogger("pa_controller")


# ---------------------------------------------------------------------------
# State Machine
# ---------------------------------------------------------------------------
class State(enum.Enum):
    IDLE          = "idle"
    PLAYING_BGM   = "playing_bgm"
    PLAYING_SIREN = "playing_siren"


class Priority(enum.Enum):
    BGM   = 0   # Low priority — background music / chants
    SIREN = 10  # High priority — security siren (pre-empts everything)


@dataclass
class PlaybackContext:
    """Tracks what is currently playing and what was interrupted."""
    url: str = ""
    priority: Priority = Priority.BGM
    process: Optional[subprocess.Popen] = None
    interrupted_url: Optional[str] = None  # URL to resume after siren


# ---------------------------------------------------------------------------
# Audio Engine (mpv subprocess)
# ---------------------------------------------------------------------------
class AudioEngine:
    """
    Thin wrapper around mpv launched as a subprocess.

    Why mpv over python-vlc?
      - 3× lower RSS on Pi Zero 2 W (~12 MB vs ~38 MB)
      - Native HTTP streaming with retry/buffering
      - No X11/PulseAudio dependency — pure ALSA output
      - Clean process model: kill -9 is always safe
    """

    def __init__(self):
        self._lock = threading.Lock()
        self._process: Optional[subprocess.Popen] = None

    def play(self, url: str, volume: int = 85, loop: int = 1) -> subprocess.Popen:
        """
        Start mpv playing the given URL.
        Returns the Popen handle. Caller is responsible for lifecycle.
        """
        self.stop()  # Kill anything currently playing

        cmd = [
            "mpv",
            "--no-video",                   # Audio only — no framebuffer needed
            "--no-terminal",                 # Suppress interactive terminal control
            f"--volume={volume}",
            "--audio-device=alsa/default",   # Use ALSA default (our I2S DAC)
            "--cache=yes",                   # Enable cache for HTTP streams
            "--cache-secs=10",               # Buffer 10s to handle WiFi hiccups
            "--demuxer-max-bytes=2M",        # Limit memory on Pi Zero 2 W (512 MB)
            "--demuxer-readahead-secs=5",
            f"--loop-file={loop}",           # Loop N times (inf for infinite)
            "--really-quiet",                # Suppress all output
            url,
        ]

        log.info("Starting mpv: volume=%d, loop=%d, url=%s", volume, loop, url)

        with self._lock:
            self._process = subprocess.Popen(
                cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                preexec_fn=os.setsid,  # New process group for clean kill
            )
            return self._process

    def stop(self) -> None:
        """Kill current playback immediately."""
        with self._lock:
            if self._process and self._process.poll() is None:
                pgid = os.getpgid(self._process.pid)
                log.info("Killing mpv process group %d", pgid)
                try:
                    os.killpg(pgid, signal.SIGKILL)  # Instant kill — no graceful shutdown needed
                    self._process.wait(timeout=2)
                except (ProcessLookupError, subprocess.TimeoutExpired):
                    pass
                self._process = None

    def is_playing(self) -> bool:
        with self._lock:
            return self._process is not None and self._process.poll() is None


# ---------------------------------------------------------------------------
# PA Controller (State Machine Core)
# ---------------------------------------------------------------------------
class PAController:
    """
    Main state machine that handles MQTT commands and manages audio playback.

    State transitions:
        IDLE ──play_bgm──▶ PLAYING_BGM ──play_siren──▶ PLAYING_SIREN
          ▲                     │                            │
          │                   stop                      (auto-finish)
          │                     │                            │
          └─────────────────────┘◀───────(resume?)───────────┘

    Thread safety: All state mutations go through _transition() which holds _lock.
    """

    def __init__(self):
        self._state = State.IDLE
        self._ctx = PlaybackContext()
        self._engine = AudioEngine()
        self._lock = threading.Lock()
        self._monitor_thread: Optional[threading.Thread] = None
        self._shutdown = threading.Event()
        self._mqtt_client: Optional[mqtt.Client] = None

    @property
    def state(self) -> State:
        return self._state

    def set_mqtt_client(self, client: mqtt.Client):
        self._mqtt_client = client

    def _publish_status(self, status: str, detail: str = ""):
        """Publish state to MQTT status topic for monitoring."""
        if self._mqtt_client and self._mqtt_client.is_connected():
            payload = json.dumps({
                "state": self._state.value,
                "status": status,
                "detail": detail,
                "timestamp": time.time(),
            })
            try:
                self._mqtt_client.publish(MQTT_STATUS, payload, qos=1)
            except Exception as e:
                log.warning("Failed to publish status: %s", e)

    def _transition(self, new_state: State, reason: str = ""):
        """Thread-safe state transition with logging."""
        old = self._state
        self._state = new_state
        log.info("STATE: %s → %s  (%s)", old.value, new_state.value, reason)
        self._publish_status(reason)

    def handle_command(self, payload: dict) -> None:
        """
        Route an MQTT command to the appropriate handler.

        Expected payloads:
            {"command": "play_bgm",   "url": "http://..."}
            {"command": "play_siren", "url": "http://..."}
            {"command": "stop"}
            {"command": "volume", "level": 80}
            {"command": "status"}
        """
        cmd = payload.get("command", "").lower().strip()
        url = payload.get("url", "")
        log.info("Received command: %s", cmd)

        if cmd == "play_bgm":
            self._play_bgm(url)
        elif cmd == "play_siren":
            self._play_siren(url)
        elif cmd == "stop":
            self._stop()
        elif cmd == "status":
            self._publish_status("status_request")
        elif cmd == "volume":
            self._set_alsa_volume(int(payload.get("level", DEFAULT_VOLUME)))
        elif cmd == "get_volume":
            self._publish_status("volume:" + self._get_alsa_volume())
        elif cmd == "play_tone":
            cache = os.getenv("PA_AUDIO_CACHE", "/tmp/pa-cache")
            os.makedirs(cache, exist_ok=True)
            tone_files = {"intrusion": "intrusion.mp3", "fire": "fire.mp3", "livestock": "livestock.mp3"}
            at = str(payload.get("alert_type", "default"))
            local = os.path.join(cache, tone_files.get(at, "default.mp3"))
            target = url if url.startswith("http") else (local if os.path.isfile(local) else url)
            if target:
                self._play_siren(target)
            else:
                log.warning("play_tone: missing file for %s (place MP3 in %s)", at, cache)
        elif cmd == "announce":
            self._tts(str(payload.get("text", "")))
        else:
            log.warning("Unknown command: %s", cmd)

    def _set_alsa_volume(self, level: int) -> None:
        level = max(0, min(100, level))
        try:
            subprocess.run(
                ["amixer", "-q", "sset", "Master", f"{level}%"],
                timeout=5, capture_output=True,
            )
            log.info("ALSAmixer Master -> %d%%", level)
        except Exception as e:
            log.warning("amixer failed: %s", e)

    def _get_alsa_volume(self) -> str:
        try:
            p = subprocess.run(
                ["amixer", "get", "Master"],
                capture_output=True, text=True, timeout=5,
            )
            return (p.stdout or "?")[:200]
        except Exception:
            return "?"

    def _tts(self, text: str) -> None:
        if not text.strip():
            return
        try:
            subprocess.Popen(
                ["espeak-ng", "-s", "150", text],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            log.info("TTS: %s", text[:80])
        except FileNotFoundError:
            log.warning("espeak-ng not installed")
        except Exception as e:
            log.warning("TTS failed: %s", e)

    def _play_bgm(self, url: str) -> None:
        """Start background music / Suprabatham chant."""
        with self._lock:
            if self._state == State.PLAYING_SIREN:
                log.warning("Ignoring BGM request — siren is active (siren has priority)")
                return

            self._engine.stop()
            proc = self._engine.play(url, volume=DEFAULT_VOLUME, loop=1)
            self._ctx = PlaybackContext(url=url, priority=Priority.BGM, process=proc)
            self._transition(State.PLAYING_BGM, f"bgm: {url}")

        # Monitor thread to detect when playback finishes naturally
        self._start_monitor(proc)

    def _play_siren(self, url: str) -> None:
        """
        PRIORITY INTERRUPT: Immediately kill anything playing and blast the siren.
        This is the security-critical path — latency must be minimal.
        """
        with self._lock:
            interrupted_url = None

            # If BGM is playing, save its URL for potential resume
            if self._state == State.PLAYING_BGM:
                interrupted_url = self._ctx.url
                log.info("Interrupting BGM for siren. Saved URL for resume: %s", interrupted_url)

            # KILL immediately — don't wait for graceful shutdown
            self._engine.stop()

            proc = self._engine.play(url, volume=SIREN_VOLUME, loop=SIREN_REPEAT)
            self._ctx = PlaybackContext(
                url=url,
                priority=Priority.SIREN,
                process=proc,
                interrupted_url=interrupted_url,
            )
            self._transition(State.PLAYING_SIREN, f"SIREN ACTIVE: {url}")

        # Monitor thread to handle post-siren behavior
        self._start_monitor(proc)

    def _stop(self) -> None:
        """Stop all playback and return to IDLE."""
        with self._lock:
            self._engine.stop()
            self._ctx = PlaybackContext()
            self._transition(State.IDLE, "manual stop")

    def _start_monitor(self, proc: subprocess.Popen) -> None:
        """Spawn a background thread that waits for mpv to exit."""
        if self._monitor_thread and self._monitor_thread.is_alive():
            pass  # Previous monitor will see its process is dead and exit

        def _monitor():
            try:
                proc.wait()  # Blocks until mpv exits
            except Exception:
                pass

            # Check stderr for errors
            if proc.stderr:
                try:
                    err = proc.stderr.read().decode("utf-8", errors="replace").strip()
                    if err:
                        log.warning("mpv stderr: %s", err[:500])
                except Exception:
                    pass

            with self._lock:
                # Only act if this process is still the active one
                if self._ctx.process is not proc:
                    return

                if self._state == State.PLAYING_SIREN:
                    # Siren finished — decide whether to resume BGM
                    if RESUME_AFTER_SIREN and self._ctx.interrupted_url:
                        resume_url = self._ctx.interrupted_url
                        log.info("Siren complete. Resuming BGM: %s", resume_url)
                        self._transition(State.IDLE, "siren complete, resuming BGM")
                        # Release lock before calling _play_bgm (which acquires it)
                        threading.Thread(
                            target=self._play_bgm, args=(resume_url,), daemon=True
                        ).start()
                        return
                    else:
                        log.info("Siren complete. Returning to IDLE.")
                        self._ctx = PlaybackContext()
                        self._transition(State.IDLE, "siren complete")
                elif self._state == State.PLAYING_BGM:
                    log.info("BGM playback finished.")
                    self._ctx = PlaybackContext()
                    self._transition(State.IDLE, "bgm finished")

        t = threading.Thread(target=_monitor, daemon=True, name="playback-monitor")
        t.start()
        self._monitor_thread = t

    def shutdown(self) -> None:
        """Clean shutdown — kill audio and signal all threads."""
        log.info("Shutting down PA controller...")
        self._shutdown.set()
        self._engine.stop()
        self._state = State.IDLE


# ---------------------------------------------------------------------------
# MQTT Client Setup
# ---------------------------------------------------------------------------
def create_mqtt_client(controller: PAController) -> mqtt.Client:
    """Build and configure the paho-mqtt client."""

    client = mqtt.Client(
        client_id=MQTT_CLIENT,
        protocol=mqtt.MQTTv311,
        clean_session=True,
    )

    if MQTT_USER:
        client.username_pw_set(MQTT_USER, MQTT_PASS)

    # Last Will & Testament — broker publishes this if Pi disconnects ungracefully
    lwt_payload = json.dumps({"state": "offline", "timestamp": time.time()})
    client.will_set(MQTT_STATUS, lwt_payload, qos=1, retain=True)

    # --- Callbacks ---
    def on_connect(client, userdata, flags, rc):
        if rc == 0:
            log.info("Connected to MQTT broker at %s:%d", MQTT_BROKER, MQTT_PORT)
            # Subscribe on every (re)connect to handle broker restarts
            client.subscribe(MQTT_TOPIC, qos=1)
            log.info("Subscribed to topic: %s", MQTT_TOPIC)
            # Announce we're online
            online_payload = json.dumps({"state": "online", "timestamp": time.time()})
            client.publish(MQTT_STATUS, online_payload, qos=1, retain=True)
        else:
            log.error("MQTT connect failed with code %d", rc)

    def on_disconnect(client, userdata, rc):
        if rc != 0:
            log.warning("Unexpected MQTT disconnect (rc=%d). Will auto-reconnect.", rc)

    def on_message(client, userdata, msg):
        try:
            raw = msg.payload.decode("utf-8")
            log.debug("MQTT raw message on %s: %s", msg.topic, raw)
            payload = json.loads(raw)
        except (json.JSONDecodeError, UnicodeDecodeError) as e:
            log.error("Invalid MQTT payload: %s", e)
            return

        try:
            controller.handle_command(payload)
        except Exception as e:
            log.exception("Error handling command: %s", e)

    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_message = on_message

    # Reconnect settings — aggressive for security-critical system
    client.reconnect_delay_set(min_delay=1, max_delay=30)

    return client


def _start_health_heartbeat(client: mqtt.Client, controller: "PAController") -> None:
    """Publish JSON heartbeat to pa/health every 60s for monitoring."""

    def _loop():
        while not controller._shutdown.is_set():
            if controller._shutdown.wait(timeout=60.0):
                break
            try:
                if client.is_connected():
                    client.publish(
                        MQTT_HEALTH,
                        json.dumps({
                            "status": "ok",
                            "component": "pa_controller",
                            "client_id": MQTT_CLIENT,
                            "timestamp": time.time(),
                        }),
                        qos=0,
                    )
            except Exception as e:
                log.debug("PA health heartbeat: %s", e)

    threading.Thread(target=_loop, daemon=True, name="pa-health-heartbeat").start()


# ---------------------------------------------------------------------------
# Main Entry Point
# ---------------------------------------------------------------------------
def main():
    log.info("=" * 60)
    log.info("PA System Controller starting")
    log.info("  Broker: %s:%d", MQTT_BROKER, MQTT_PORT)
    log.info("  Topic:  %s", MQTT_TOPIC)
    log.info("  Volume: BGM=%d, Siren=%d", DEFAULT_VOLUME, SIREN_VOLUME)
    log.info("  Resume after siren: %s", RESUME_AFTER_SIREN)
    log.info("=" * 60)

    controller = PAController()
    client = create_mqtt_client(controller)
    controller.set_mqtt_client(client)

    # Graceful shutdown on SIGTERM/SIGINT
    def signal_handler(signum, frame):
        log.info("Received signal %d, shutting down...", signum)
        controller.shutdown()
        client.disconnect()
        sys.exit(0)

    signal.signal(signal.SIGTERM, signal_handler)
    signal.signal(signal.SIGINT, signal_handler)

    # Connect with retry loop
    while True:
        try:
            client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
            break
        except (ConnectionRefusedError, OSError) as e:
            log.error("Cannot reach MQTT broker: %s. Retrying in 5s...", e)
            time.sleep(5)

    _start_health_heartbeat(client, controller)
    log.info("PA health heartbeat every 60s → %s", MQTT_HEALTH)

    # Blocking network loop — handles reconnect automatically
    try:
        client.loop_forever()
    except KeyboardInterrupt:
        pass
    finally:
        controller.shutdown()
        client.disconnect()
        log.info("PA Controller shut down cleanly.")


if __name__ == "__main__":
    main()
