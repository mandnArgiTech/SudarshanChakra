"""
Suite 9: Water Motor/Pump Control — API tier (Playwright UI steps in e2e/playwright/water.spec.ts).
See docs/E2E_REAL_HARDWARE_TEST_PLAN.md
"""
from __future__ import annotations

import pytest

from e2e.helpers.api_client import api_request


@pytest.mark.skip(reason="T9.1: Playwright — navigate to pump control page (see water.spec.ts)")
def test_t9_1_playwright_pump_page():
    pass


def test_t9_2_start_pump_via_api(require_stack, api_base: str, admin_token: str):
    st, motors = api_request(api_base, "GET", "/api/v1/water/motors", token=admin_token)
    if st != 200 or not isinstance(motors, list) or len(motors) == 0:
        pytest.skip("no water motors in environment — seed data required")
    mid = motors[0].get("id") if isinstance(motors[0], dict) else getattr(motors[0], "id", None)
    if not mid:
        pytest.skip("could not resolve motor id")
    st2, body = api_request(
        api_base,
        "POST",
        f"/api/v1/water/motors/{mid}/command",
        {"command": "pump_on"},
        token=admin_token,
    )
    assert st2 == 200, body


def test_t9_3_stop_pump_via_api(require_stack, api_base: str, admin_token: str):
    st, motors = api_request(api_base, "GET", "/api/v1/water/motors", token=admin_token)
    if st != 200 or not isinstance(motors, list) or len(motors) == 0:
        pytest.skip("no water motors in environment")
    mid = motors[0].get("id") if isinstance(motors[0], dict) else None
    if not mid:
        pytest.skip("could not resolve motor id")
    st2, body = api_request(
        api_base,
        "POST",
        f"/api/v1/water/motors/{mid}/command",
        {"command": "pump_off"},
        token=admin_token,
    )
    assert st2 == 200, body


@pytest.mark.skip(reason="T9.4: motor_run_log DB audit — no stable public API in test plan v1")
def test_t9_4_motor_run_log_entry():
    pass
