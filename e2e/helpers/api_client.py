"""HTTP helpers for E2E tests against the API gateway."""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Optional, Tuple

DEFAULT_TIMEOUT = 15


def load_e2e_config(path: Optional[str] = None) -> dict:
    """Load YAML config; return {} if missing or PyYAML absent."""
    cfg_path = path or os.environ.get("E2E_CONFIG", "")
    if not cfg_path:
        default = Path(__file__).resolve().parent.parent / "config" / "e2e_config.yml"
        if default.is_file():
            cfg_path = str(default)
        else:
            return {}
    p = Path(cfg_path)
    if not p.is_file():
        return {}
    try:
        import yaml

        with p.open() as f:
            return yaml.safe_load(f) or {}
    except ImportError:
        return {}


def api_base_from_config(cfg: dict) -> str:
    be = cfg.get("backend") or {}
    return (be.get("api_base") or os.environ.get("E2E_API") or "http://127.0.0.1:8080").rstrip("/")


def gateway_reachable(base: str, timeout: float = 2.0) -> bool:
    try:
        urllib.request.urlopen(f"{base}/actuator/health", timeout=timeout)
        return True
    except Exception:
        return False


def api_request(
    base: str,
    method: str,
    path: str,
    body: Optional[dict] = None,
    token: Optional[str] = None,
    timeout: int = DEFAULT_TIMEOUT,
) -> Tuple[int, Any]:
    url = f"{base}{path}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
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


def http_get(url: str, timeout: int = 10) -> Tuple[int, str]:
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except Exception as e:
        return 0, str(e)


def tcp_open(host: str, port: int, timeout: float = 5.0) -> bool:
    import socket

    try:
        sock = socket.create_connection((host, port), timeout=timeout)
        sock.close()
        return True
    except OSError:
        return False


def host_from_url(url: str) -> str:
    u = url.replace("https://", "").replace("http://", "")
    return u.split(":")[0].split("/")[0]
