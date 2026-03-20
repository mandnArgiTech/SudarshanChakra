#!/usr/bin/env python3
"""
Scheduled PA playback — MQTT play_bgm (with playlist rotation), announces, sunrise/sunset hooks.

Environment:
  PA_MQTT_BROKER, PA_MQTT_PORT, PA_MQTT_USER, PA_MQTT_PASS
  PA_SCHEDULE_TOPIC       — command topic (default pa/command)
  PA_SCHEDULE_BGM_TIME    — daily BGM HH:MM (default 06:00)
  PA_SCHEDULE_BGM_URL     — single BGM URL (if PA_SCHEDULE_BGM_URLS unset)
  PA_SCHEDULE_BGM_URLS    — comma or newline separated URLs; rotates each scheduled play
  PA_SCHEDULE_PLAYLIST_STATE — file storing next index (default /tmp/pa_bgm_playlist_idx)
  PA_SCHEDULE_ANNOUNCE_TIME / PA_SCHEDULE_ANNOUNCE_TEXT — optional daily announce
  PA_SCHEDULE_CHECK_SEC   — main loop sleep (default 30)

  Sunrise / sunset (optional, requires `astral` + PA_FARM_LAT, PA_FARM_LON):
  PA_FARM_TZ              — IANA zone (default UTC)
  PA_SUNRISE_TOPIC        — default pa/schedule/sunrise
  PA_SUNSET_TOPIC         — default pa/schedule/sunset
  Publishes JSON {"event":"sunrise"|"sunset","iso":"..."} once per local day when time passes event.

  Subscribes to pa/schedule/reload (log only; restart to apply env).
"""
from __future__ import annotations

import json
import logging
import os
import sys
import time
from datetime import date, datetime, timedelta

try:
    import schedule
except ImportError:
    print("pip install schedule paho-mqtt", file=sys.stderr)
    sys.exit(1)

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("pip install paho-mqtt", file=sys.stderr)
    sys.exit(1)

log = logging.getLogger("pa_scheduler")
logging.basicConfig(
    level=os.getenv("PA_LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    force=True,
)

BROKER = os.getenv("PA_MQTT_BROKER", "192.168.1.100")
PORT = int(os.getenv("PA_MQTT_PORT", "1883"))
USER = os.getenv("PA_MQTT_USER", "")
PASS = os.getenv("PA_MQTT_PASS", "")
TOPIC = os.getenv("PA_SCHEDULE_TOPIC", "pa/command")
CLIENT_ID = os.getenv("PA_SCHEDULE_CLIENT_ID", "pa-scheduler")
BGM_TIME = os.getenv("PA_SCHEDULE_BGM_TIME", "06:00")
ANN_TIME = os.getenv("PA_SCHEDULE_ANNOUNCE_TIME", "").strip()
ANN_TEXT = os.getenv("PA_SCHEDULE_ANNOUNCE_TEXT", "Farm PA schedule check.").strip()
CHECK = int(os.getenv("PA_SCHEDULE_CHECK_SEC", "30"))
STATE_FILE = os.getenv("PA_SCHEDULE_PLAYLIST_STATE", "/tmp/pa_bgm_playlist_idx")
SUNRISE_TOPIC = os.getenv("PA_SUNRISE_TOPIC", "pa/schedule/sunrise")
SUNSET_TOPIC = os.getenv("PA_SUNSET_TOPIC", "pa/schedule/sunset")

_client: mqtt.Client | None = None
_playlist: list[str] = []
_sun_warned = False
_last_sun_date: date | None = None
_sunrise_fired = False
_sunset_fired = False


def _load_playlist() -> list[str]:
    raw = os.getenv("PA_SCHEDULE_BGM_URLS", "").strip()
    urls: list[str] = []
    if raw:
        for part in raw.replace("\n", ",").split(","):
            u = part.strip()
            if u:
                urls.append(u)
    if not urls:
        single = os.getenv("PA_SCHEDULE_BGM_URL", "").strip()
        if single:
            urls = [single]
    return urls


def _read_playlist_index() -> int:
    try:
        with open(STATE_FILE, encoding="utf-8") as f:
            return int(f.read().strip())
    except Exception:
        return 0


def _write_playlist_index(i: int) -> None:
    try:
        d = os.path.dirname(STATE_FILE)
        if d:
            os.makedirs(d, exist_ok=True)
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            f.write(str(i))
    except OSError as e:
        log.warning("Could not persist playlist index: %s", e)


def next_bgm_url() -> str:
    global _playlist
    if not _playlist:
        return ""
    idx = _read_playlist_index() % len(_playlist)
    url = _playlist[idx]
    _write_playlist_index(idx + 1)
    log.info("BGM playlist [%s/%s]: %s", idx + 1, len(_playlist), url[:80] + ("…" if len(url) > 80 else ""))
    return url


def _make_client() -> mqtt.Client:
    c = mqtt.Client(client_id=CLIENT_ID, protocol=mqtt.MQTTv311)
    if USER:
        c.username_pw_set(USER, PASS)

    def on_connect(client, _u, _f, rc):
        if rc == 0:
            log.info("Scheduler connected to MQTT %s:%s", BROKER, PORT)
            client.subscribe("pa/schedule/reload", qos=0)
        else:
            log.error("MQTT connect failed rc=%s", rc)

    def on_message(_c, _u, msg):
        if msg.topic == "pa/schedule/reload":
            log.info("Schedule reload requested (restart service to apply env changes)")

    c.on_connect = on_connect
    c.on_message = on_message
    return c


def _publish_play_bgm():
    url = next_bgm_url()
    if not url:
        log.warning("No BGM URL(s) configured — skip BGM job")
        return
    assert _client is not None
    payload = json.dumps({"command": "play_bgm", "url": url})
    _client.publish(TOPIC, payload, qos=1)
    log.info("Published scheduled play_bgm")


def _publish_announce():
    assert _client is not None
    payload = json.dumps({"command": "announce", "text": ANN_TEXT})
    _client.publish(TOPIC, payload, qos=1)
    log.info("Published scheduled announce")


def _tick_sun_hooks():
    """Fire MQTT hooks once per local day near sunrise/sunset (requires astral)."""
    global _sun_warned, _last_sun_date, _sunrise_fired, _sunset_fired
    lat_s = os.getenv("PA_FARM_LAT", "").strip()
    lon_s = os.getenv("PA_FARM_LON", "").strip()
    if not lat_s or not lon_s:
        return

    try:
        from astral import LocationInfo
        from astral.sun import sun
        from zoneinfo import ZoneInfo
    except ImportError:
        if not _sun_warned:
            log.warning("Sunrise/sunset hooks need `pip install astral` — skipping")
            _sun_warned = True
        return

    assert _client is not None
    tzname = os.getenv("PA_FARM_TZ", "UTC")
    try:
        tz = ZoneInfo(tzname)
    except Exception:
        tz = ZoneInfo("UTC")
        if not _sun_warned:
            log.warning("Invalid PA_FARM_TZ=%r — using UTC", tzname)
            _sun_warned = True

    today = datetime.now(tz).date()
    if _last_sun_date != today:
        _last_sun_date = today
        _sunrise_fired = False
        _sunset_fired = False

    try:
        loc = LocationInfo("farm", "", tzname, float(lat_s), float(lon_s))
        s = sun(loc.observer, date=today, tzinfo=tz)
        sunrise = s["sunrise"]
        sunset = s["sunset"]
    except Exception as e:
        if not _sun_warned:
            log.warning("Sun calculation failed: %s", e)
            _sun_warned = True
        return

    now = datetime.now(tz)
    window = timedelta(seconds=int(os.getenv("PA_SUN_HOOK_WINDOW_SEC", "120")))

    if not _sunrise_fired and sunrise <= now < sunrise + window:
        _sunrise_fired = True
        body = json.dumps({"event": "sunrise", "iso": now.isoformat()})
        _client.publish(SUNRISE_TOPIC, body, qos=1)
        log.info("Published %s", SUNRISE_TOPIC)

    if not _sunset_fired and sunset <= now < sunset + window:
        _sunset_fired = True
        body = json.dumps({"event": "sunset", "iso": now.isoformat()})
        _client.publish(SUNSET_TOPIC, body, qos=1)
        log.info("Published %s", SUNSET_TOPIC)


def main():
    global _client, _playlist
    _playlist = _load_playlist()
    _client = _make_client()
    while True:
        try:
            _client.connect(BROKER, PORT, keepalive=60)
            break
        except OSError as e:
            log.warning("MQTT unreachable %s — retry in 30s: %s", BROKER, e)
            time.sleep(30)
    _client.loop_start()

    schedule.clear()
    schedule.every().day.at(BGM_TIME).do(_publish_play_bgm)
    if ANN_TIME:
        schedule.every().day.at(ANN_TIME).do(_publish_announce)

    log.info(
        "Jobs: BGM at %s playlist=%s item(s)",
        BGM_TIME,
        len(_playlist) or "(configure PA_SCHEDULE_BGM_URL or PA_SCHEDULE_BGM_URLS)",
    )
    if ANN_TIME:
        log.info("Announce at %s", ANN_TIME)
    if os.getenv("PA_FARM_LAT") and os.getenv("PA_FARM_LON"):
        log.info("Sun hooks enabled (astral) → %s / %s", SUNRISE_TOPIC, SUNSET_TOPIC)

    while True:
        schedule.run_pending()
        _tick_sun_hooks()
        time.sleep(CHECK)


if __name__ == "__main__":
    main()
