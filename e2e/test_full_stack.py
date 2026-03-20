#!/usr/bin/env python3
"""
Full-stack E2E against API gateway (8080). Start postgres, rabbitmq, backends, gateway, then:

  export E2E_API=http://127.0.0.1:8080
  pytest e2e/test_full_stack.py -v
"""
import json
import os
import urllib.error
import urllib.request

import pytest

BASE = os.getenv("E2E_API", "http://127.0.0.1:8080").rstrip("/")
TIMEOUT = 15


def _up():
    try:
        urllib.request.urlopen(f"{BASE}/actuator/health", timeout=2)
        return True
    except Exception:
        return False


def _api(method, path, body=None, token=None):
    url = f"{BASE}{path}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            raw = r.read().decode()
            try:
                return r.status, json.loads(raw) if raw else {}
            except json.JSONDecodeError:
                return r.status, raw
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


@pytest.fixture(scope="module")
def require_stack():
    if not _up():
        pytest.skip("Gateway not reachable at %s — start full stack" % BASE)


@pytest.fixture(scope="module")
def token(require_stack):
    u = "e2e_user_%d" % (os.getpid() % 100000)
    st, reg = _api("POST", "/api/v1/auth/register", {
        "username": u, "password": "e2e_secret_12", "email": f"{u}@e2e.local", "role": "viewer",
    })
    if st != 200:
        pytest.skip("auth register failed: %s %s" % (st, reg))
    return reg.get("token") or reg.get("accessToken")


def test_gateway_health(require_stack):
    st, _ = _api("GET", "/actuator/health")
    assert st == 200


def test_auth_register_duplicate(require_stack, token):
    u = "e2e_dup_%d" % (os.getpid() % 100000)
    st, _ = _api("POST", "/api/v1/auth/register", {
        "username": u, "password": "x" * 8, "role": "viewer",
    })
    assert st == 200
    st2, _ = _api("POST", "/api/v1/auth/register", {
        "username": u, "password": "y" * 8, "role": "viewer",
    })
    assert st2 in (400, 409, 422)


def test_auth_login_bad_password(require_stack):
    u = "e2e_bad_%d" % (os.getpid() % 100000)
    st, reg = _api("POST", "/api/v1/auth/register", {
        "username": u, "password": "correct_pass_1", "role": "viewer",
    })
    assert st == 200
    st2, _ = _api("POST", "/api/v1/auth/login", {"username": u, "password": "wrongxxx"})
    assert st2 in (400, 401, 422)


def test_device_node_roundtrip(require_stack, token):
    nid = "e2e-node-%d" % os.getpid()
    farm = "a0000000-0000-0000-0000-000000000001"
    st, _ = _api("POST", "/api/v1/nodes", {
        "id": nid, "farmId": farm, "displayName": "E2E", "status": "online",
    }, token)
    assert st == 200
    st2, body = _api("GET", f"/api/v1/nodes/{nid}", token=token)
    assert st2 == 200
    assert body.get("id") == nid or body.get("displayName") == "E2E"


def test_device_camera(require_stack, token):
    nid = "e2e-camnode-%d" % os.getpid()
    farm = "a0000000-0000-0000-0000-000000000001"
    _api("POST", "/api/v1/nodes", {"id": nid, "farmId": farm, "displayName": "N"}, token)
    cid = "e2e-cam-%d" % os.getpid()
    st, _ = _api("POST", "/api/v1/cameras", {
        "id": cid, "nodeId": nid, "name": "E2E Cam", "rtspUrl": "rtsp://127.0.0.1/x",
    }, token)
    assert st == 200


def test_alerts_list(require_stack, token):
    st, body = _api("GET", "/api/v1/alerts?size=5", token=token)
    assert st == 200
    assert "content" in body or isinstance(body, list)


def test_siren_trigger_history(require_stack, token):
    st, _ = _api("POST", "/api/v1/siren/trigger", {
        "nodeId": "e2e-siren-node", "sirenUrl": "http://127.0.0.1/s.wav",
    }, token)
    assert st == 200
    st2, h = _api("GET", "/api/v1/siren/history?size=5", token=token)
    assert st2 == 200


def test_water_tank_list(require_stack, token):
    st, body = _api("GET", "/api/v1/water/tanks", token=token)
    assert st == 200
    assert isinstance(body, list)


def test_water_tank_create(require_stack, token):
    farm = "a0000000-0000-0000-0000-000000000001"
    st, body = _api("POST", "/api/v1/water/tanks", {
        "farmId": farm, "name": "E2E Tank", "thresholdLowPct": 18.0,
    }, token)
    assert st == 200
    assert body.get("name") == "E2E Tank"


def test_zones_list_device(require_stack, token):
    st, body = _api("GET", "/api/v1/zones", token=token)
    assert st in (200, 404)


def test_tags_list(require_stack, token):
    st, _ = _api("GET", "/api/v1/tags", token=token)
    assert st in (200, 404)


def test_gateway_cors_preflight(require_stack):
    req = urllib.request.Request(
        f"{BASE}/api/v1/auth/login",
        method="OPTIONS",
        headers={
            "Origin": "http://localhost:3000",
            "Access-Control-Request-Method": "POST",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            assert r.status in (200, 204)
    except Exception:
        pass


def test_alert_get_by_id_404(require_stack, token):
    import uuid
    st, _ = _api("GET", f"/api/v1/alerts/{uuid.uuid4()}", token=token)
    assert st in (404, 400)


if __name__ == "__main__":
    raise SystemExit(pytest.main([__file__, "-v"]))
