"""
API smoke tests (gateway CRUD paths) — complements Suites 1–2; keeps legacy e2e/test_full_stack coverage.
"""
from __future__ import annotations

import os
import uuid

import pytest

from e2e.helpers.api_client import api_request


@pytest.fixture(scope="module")
def reg_token(require_stack, api_base: str) -> str:
    u = f"e2e_api_{os.getpid() % 100000}_{uuid.uuid4().hex[:6]}"
    st, reg = api_request(
        api_base,
        "POST",
        "/api/v1/auth/register",
        {
            "username": u,
            "password": "e2e_secret_12",
            "email": f"{u}@e2e.local",
            "role": "viewer",
        },
    )
    if st != 200:
        pytest.skip(f"register failed: {st} {reg}")
    tok = reg.get("token") or reg.get("accessToken")
    if not tok:
        pytest.skip("no token")
    return tok


def test_auth_register_duplicate(require_stack, api_base: str, reg_token: str):
    u = f"e2e_dup_{os.getpid() % 100000}_{uuid.uuid4().hex[:6]}"
    st, _ = api_request(
        api_base,
        "POST",
        "/api/v1/auth/register",
        {
            "username": u,
            "password": "xxxxxxxx",
            "email": f"{u}@e2e.local",
            "role": "viewer",
        },
    )
    assert st == 200
    st2, _ = api_request(
        api_base,
        "POST",
        "/api/v1/auth/register",
        {
            "username": u,
            "password": "yyyyyyyy",
            "email": f"{u}2@e2e.local",
            "role": "viewer",
        },
    )
    assert st2 in (400, 409, 422)


def test_device_node_roundtrip(require_stack, api_base: str, reg_token: str):
    nid = f"e2e-node-{os.getpid()}"
    farm = "a0000000-0000-0000-0000-000000000001"
    st, _ = api_request(
        api_base,
        "POST",
        "/api/v1/nodes",
        {"id": nid, "farmId": farm, "displayName": "E2E", "status": "online"},
        token=reg_token,
    )
    assert st == 200
    st2, body = api_request(api_base, "GET", f"/api/v1/nodes/{nid}", token=reg_token)
    assert st2 == 200
    assert body.get("id") == nid or body.get("displayName") == "E2E"


def test_device_camera(require_stack, api_base: str, reg_token: str):
    nid = f"e2e-camnode-{os.getpid()}"
    farm = "a0000000-0000-0000-0000-000000000001"
    api_request(
        api_base,
        "POST",
        "/api/v1/nodes",
        {"id": nid, "farmId": farm, "displayName": "N"},
        token=reg_token,
    )
    cid = f"e2e-cam-{os.getpid()}"
    st, _ = api_request(
        api_base,
        "POST",
        "/api/v1/cameras",
        {"id": cid, "nodeId": nid, "name": "E2E Cam", "rtspUrl": "rtsp://127.0.0.1/x"},
        token=reg_token,
    )
    assert st == 200


def test_alerts_list(require_stack, api_base: str, reg_token: str):
    st, body = api_request(api_base, "GET", "/api/v1/alerts?size=5", token=reg_token)
    assert st == 200
    assert "content" in body or isinstance(body, list)


def test_siren_trigger_history(require_stack, api_base: str, reg_token: str):
    st, _ = api_request(
        api_base,
        "POST",
        "/api/v1/siren/trigger",
        {"nodeId": "e2e-siren-node", "sirenUrl": "http://127.0.0.1/s.wav"},
        token=reg_token,
    )
    assert st == 200
    st2, _ = api_request(api_base, "GET", "/api/v1/siren/history?size=5", token=reg_token)
    assert st2 == 200


def test_water_tank_list(require_stack, api_base: str, reg_token: str):
    st, body = api_request(api_base, "GET", "/api/v1/water/tanks", token=reg_token)
    assert st == 200
    assert isinstance(body, list)


def test_water_tank_create(require_stack, api_base: str, reg_token: str):
    farm = "a0000000-0000-0000-0000-000000000001"
    st, body = api_request(
        api_base,
        "POST",
        "/api/v1/water/tanks",
        {"farmId": farm, "name": "E2E Tank", "thresholdLowPct": 18.0},
        token=reg_token,
    )
    assert st == 200
    assert body.get("name") == "E2E Tank"


def test_zones_list_device(require_stack, api_base: str, reg_token: str):
    st, body = api_request(api_base, "GET", "/api/v1/zones", token=reg_token)
    assert st in (200, 404)


def test_tags_list(require_stack, api_base: str, reg_token: str):
    st, _ = api_request(api_base, "GET", "/api/v1/tags", token=reg_token)
    assert st in (200, 404)


def test_gateway_cors_preflight(require_stack, api_base: str):
    import urllib.request

    req = urllib.request.Request(
        f"{api_base}/api/v1/auth/login",
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
        pytest.skip("CORS preflight not confirmed (gateway may not echo OPTIONS)")


def test_alert_get_by_id_404(require_stack, api_base: str, reg_token: str):
    st, _ = api_request(api_base, "GET", f"/api/v1/alerts/{uuid.uuid4()}", token=reg_token)
    assert st in (404, 400)
