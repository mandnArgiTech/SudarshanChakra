#!/usr/bin/env python3
"""Full-stack E2E placeholders — expand when all services run in CI."""
import os
import sys
import urllib.request

import pytest

BASE = os.getenv("E2E_API", "http://127.0.0.1:8080")


def _reachable(url, timeout=2):
    try:
        urllib.request.urlopen(url, timeout=timeout)
        return True
    except Exception:
        return False


@pytest.mark.skipif(not _reachable(f"{BASE}/actuator/health", 1), reason="Gateway not up")
def test_gateway_health():
    assert _reachable(f"{BASE}/actuator/health")


@pytest.mark.parametrize("path,ok", [
    ("/api/v1/auth/register", False),
])
def test_paths_exist(path, ok):
    if not _reachable(BASE):
        pytest.skip("stack down")
    try:
        req = urllib.request.Request(f"{BASE}{path}", method="POST", data=b"{}",
                                     headers={"Content-Type": "application/json"})
        urllib.request.urlopen(req, timeout=3)
    except urllib.error.HTTPError as e:
        assert e.code in (400, 401, 404, 405) or ok


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
