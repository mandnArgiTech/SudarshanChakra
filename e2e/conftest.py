"""Shared fixtures: load e2e_config.yml (or env fallbacks) for pytest under e2e/tests/."""
from __future__ import annotations

import os
import uuid

import pytest

from e2e.helpers.api_client import (
    api_base_from_config,
    api_request,
    gateway_reachable,
    load_e2e_config,
)


@pytest.fixture(scope="session")
def e2e_config() -> dict:
    return load_e2e_config()


@pytest.fixture(scope="session")
def api_base(e2e_config) -> str:
    return api_base_from_config(e2e_config)


@pytest.fixture(scope="session")
def require_stack(api_base: str):
    if not gateway_reachable(api_base):
        pytest.skip(f"Gateway not reachable at {api_base} — start stack or set E2E_CONFIG / E2E_API")
    yield


@pytest.fixture(scope="module")
def viewer_token(require_stack, api_base: str) -> str:
    u = f"e2e_view_{os.getpid() % 100000}_{uuid.uuid4().hex[:6]}"
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
        pytest.skip(f"auth register failed: {st} {reg}")
    tok = reg.get("token") or reg.get("accessToken")
    if not tok:
        pytest.skip("no token in register response")
    return tok


@pytest.fixture(scope="module")
def admin_token(require_stack, api_base: str, e2e_config: dict) -> str:
    auth = e2e_config.get("auth") or {}
    user = auth.get("admin_user") or os.environ.get("E2E_ADMIN_USER")
    password = auth.get("admin_pass") or os.environ.get("E2E_ADMIN_PASS")
    if not user or not password:
        pytest.skip("admin credentials not in e2e_config.auth (admin_user/admin_pass)")
    st, body = api_request(
        api_base,
        "POST",
        "/api/v1/auth/login",
        {"username": user, "password": password},
    )
    if st != 200:
        pytest.skip(f"admin login failed: {st} {body}")
    tok = body.get("token") or body.get("accessToken")
    if not tok:
        pytest.skip("no token in login response")
    return tok


@pytest.fixture(scope="module")
def config_viewer_token(require_stack, api_base: str, e2e_config: dict) -> str:
    auth = e2e_config.get("auth") or {}
    user = auth.get("viewer_user") or os.environ.get("E2E_VIEWER_USER")
    password = auth.get("viewer_pass") or os.environ.get("E2E_VIEWER_PASS")
    if not user or not password:
        pytest.skip("viewer credentials not in e2e_config.auth (viewer_user/viewer_pass)")
    st, body = api_request(
        api_base,
        "POST",
        "/api/v1/auth/login",
        {"username": user, "password": password},
    )
    if st != 200:
        pytest.skip(f"viewer login failed: {st} {body}")
    tok = body.get("token") or body.get("accessToken")
    if not tok:
        pytest.skip("no token in login response")
    return tok
