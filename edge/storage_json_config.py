#!/usr/bin/env python3
"""
Shared loader for ``storage.json`` with optional env overrides.

Never commit real camera credentials. Use placeholders in the repo and set:
  TEST_CAMERA_RTSP      — override ``test_camera.rtsp_url``
  TEST_CAMERA_STREAM1   — override ``test_camera.stream1_url``

Or point at a local file (untracked):
  VIDEO_STORAGE_CONFIG  — full path to JSON (default: CONFIG_DIR/storage.json)
"""

from __future__ import annotations

import json
import os
from typing import Any, Dict, Optional

__all__ = ["get_storage_config_path", "load_storage_config_dict", "apply_test_camera_env_overrides"]


def get_storage_config_path() -> str:
    return os.getenv(
        "VIDEO_STORAGE_CONFIG",
        os.path.join(os.getenv("CONFIG_DIR", "/app/config"), "storage.json"),
    )


def apply_test_camera_env_overrides(cfg: Dict[str, Any]) -> Dict[str, Any]:
    """Merge TEST_CAMERA_* env vars into ``test_camera`` block if present."""
    tc = cfg.get("test_camera")
    if isinstance(tc, dict):
        rtsp = os.getenv("TEST_CAMERA_RTSP", "").strip()
        if rtsp:
            tc["rtsp_url"] = rtsp
        s1 = os.getenv("TEST_CAMERA_STREAM1", "").strip()
        if s1:
            tc["stream1_url"] = s1
    return cfg


def load_storage_config_dict() -> Optional[Dict[str, Any]]:
    """
    Load storage JSON from disk; return None if file missing.
    Applies ``TEST_CAMERA_*`` env overrides to ``test_camera``.
    """
    path = get_storage_config_path()
    try:
        with open(path, encoding="utf-8") as f:
            cfg = json.load(f)
    except FileNotFoundError:
        return None
    except json.JSONDecodeError:
        return None
    return apply_test_camera_env_overrides(cfg)
