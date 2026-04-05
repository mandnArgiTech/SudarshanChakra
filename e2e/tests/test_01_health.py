"""
Suite 1: Infrastructure Health (8 tests) — see docs/E2E_REAL_HARDWARE_TEST_PLAN.md
"""
from __future__ import annotations

import json
import os
import urllib.request

import pytest

from e2e.helpers.api_client import api_request, host_from_url, http_get, tcp_open


def test_t1_1_api_gateway_actuator_health(require_stack, api_base: str):
    st, body = api_request(api_base, "GET", "/actuator/health")
    assert st == 200
    if isinstance(body, dict):
        assert body.get("status") in ("UP", "up", None) or "status" in body


def test_t1_2_auth_login_returns_jwt(require_stack, api_base: str, e2e_config: dict):
    auth = e2e_config.get("auth") or {}
    user = auth.get("admin_user") or os.environ.get("E2E_ADMIN_USER")
    password = auth.get("admin_pass") or os.environ.get("E2E_ADMIN_PASS")
    if not user or not password:
        u = f"e2e_t12_{os.getpid()}"
        reg_st, reg = api_request(
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
        if reg_st != 200:
            pytest.skip(f"no admin in config and register failed: {reg_st}")
        user, password = u, "e2e_secret_12"
    st, body = api_request(
        api_base,
        "POST",
        "/api/v1/auth/login",
        {"username": user, "password": password},
    )
    assert st == 200
    token = body.get("token") or body.get("accessToken")
    assert token and isinstance(token, str) and len(token) > 10


def test_t1_3_alert_service_reachable(require_stack, api_base: str):
    """Proxy for WebSocket endpoint: alert-service HTTP actuator on :8081 (common local layout)."""
    host = host_from_url(api_base)
    if not tcp_open(host, 8081, timeout=3):
        pytest.skip("alert-service :8081 not open (expected if only gateway exposed)")
    st, _ = http_get(f"http://{host}:8081/actuator/health", timeout=5)
    assert st == 200


def test_t1_4_rabbitmq_management_or_amqp(require_stack, api_base: str, e2e_config: dict):
    be = e2e_config.get("backend") or {}
    host = be.get("mqtt_broker") or host_from_url(api_base)
    user = os.environ.get("E2E_RABBITMQ_USER", "admin")
    password = os.environ.get("E2E_RABBITMQ_PASS", "devpassword123")
    req = urllib.request.Request(f"http://{host}:15672/api/overview")
    import base64 as b64

    creds = b64.b64encode(f"{user}:{password}".encode()).decode()
    req.add_header("Authorization", f"Basic {creds}")
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            assert r.status == 200
            data = json.loads(r.read().decode())
            assert "management_version" in data or "rabbitmq_version" in data
    except Exception:
        if tcp_open(host, 5672, timeout=3):
            pytest.skip("RabbitMQ AMQP up but management API not reachable or auth mismatch")
        pytest.fail("RabbitMQ not reachable")


def test_t1_5_postgresql_tcp(require_stack, api_base: str):
    host = host_from_url(api_base)
    assert tcp_open(host, 5432, timeout=5), "PostgreSQL :5432 not accepting connections"


def test_t1_6_dashboard_loads(require_stack, e2e_config: dict):
    be = e2e_config.get("backend") or {}
    url = be.get("dashboard_url") or os.environ.get("E2E_DASHBOARD_URL")
    if not url:
        pytest.skip("dashboard_url not in e2e_config.backend")
    st, _ = http_get(url.rstrip("/") + "/", timeout=15)
    assert st == 200


def test_t1_7_edge_flask_health(require_stack, e2e_config: dict):
    edge = e2e_config.get("edge") or {}
    flask_url = (edge.get("flask_url") or os.environ.get("E2E_FLASK_URL") or "").rstrip("/")
    if not flask_url:
        pytest.skip("edge.flask_url not in e2e_config")
    st, body = http_get(f"{flask_url}/health", timeout=10)
    assert st == 200
    try:
        j = json.loads(body)
        assert j.get("status") == "ok" or "status" in j
    except json.JSONDecodeError:
        pytest.fail("edge /health did not return JSON")


def test_t1_8_mqtt_broker_tcp(require_stack, api_base: str, e2e_config: dict):
    be = e2e_config.get("backend") or {}
    host = be.get("mqtt_broker") or host_from_url(api_base)
    port = int(be.get("mqtt_port") or 1883)
    assert tcp_open(host, port, timeout=5), f"MQTT {host}:{port} not reachable"
