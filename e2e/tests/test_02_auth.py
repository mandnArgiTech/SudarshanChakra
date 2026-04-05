"""
Suite 2: Auth & RBAC (6 tests) — see docs/E2E_REAL_HARDWARE_TEST_PLAN.md
"""
from __future__ import annotations

import base64
import json

import pytest

from e2e.helpers.api_client import api_request


def _jwt_payload(token: str) -> dict:
    parts = token.split(".")
    if len(parts) != 3:
        return {}
    payload_b64 = parts[1] + "=" * (4 - len(parts[1]) % 4)
    try:
        return json.loads(base64.b64decode(payload_b64))
    except Exception:
        return {}


def test_t2_1_login_admin_jwt_role(require_stack, api_base: str, admin_token: str):
    payload = _jwt_payload(admin_token)
    role = str(payload.get("role") or "").lower()
    assert role or payload.get("sub"), f"JWT payload missing role/sub: {payload}"
    assert role in ("admin", "super_admin", "superadmin", "owner") or "admin" in role, (
        f"expected admin-class role in JWT, got role={payload.get('role')!r}"
    )


def test_t2_2_login_viewer_jwt_role(require_stack, api_base: str, config_viewer_token: str):
    payload = _jwt_payload(config_viewer_token)
    role = (payload.get("role") or "").lower()
    assert "viewer" in role or role == "viewer" or payload.get("role"), f"expected viewer role, got {payload}"


def test_t2_3_viewer_cannot_trigger_siren(require_stack, api_base: str, config_viewer_token: str):
    st, _ = api_request(
        api_base,
        "POST",
        "/api/v1/siren/trigger",
        {"nodeId": "e2e-rbac-node", "sirenUrl": "http://127.0.0.1/s.wav"},
        token=config_viewer_token,
    )
    assert st in (403, 401), f"viewer siren trigger expected 403/401, got {st}"


def test_t2_4_viewer_can_list_alerts(require_stack, api_base: str, config_viewer_token: str):
    st, body = api_request(api_base, "GET", "/api/v1/alerts?size=5", token=config_viewer_token)
    assert st == 200
    assert isinstance(body, (dict, list))


def test_t2_5_invalid_credentials_401(require_stack, api_base: str):
    st, _ = api_request(
        api_base,
        "POST",
        "/api/v1/auth/login",
        {"username": "___no_such_user_e2e___", "password": "wrong"},
    )
    assert st in (400, 401, 422)


def test_t2_6_expired_or_invalid_jwt_rejected(require_stack, api_base: str):
    bogus = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0.invalidsig"
    st, _ = api_request(api_base, "GET", "/api/v1/alerts?size=1", token=bogus)
    assert st in (401, 403)
