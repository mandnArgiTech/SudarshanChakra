#!/usr/bin/env python3
"""
storage_manager.py — SSD Usage Monitor + Circular Overwrite
============================================================
Monitors disk usage hourly. When usage exceeds the high-watermark
(default 85 %), deletes the oldest video segments first. Optionally
triggers archival to external HDD before deletion.
"""

import json
import logging
import os
import shutil
import threading
import time
from typing import Dict, List, Optional

from storage_json_config import get_storage_config_path, load_storage_config_dict

log = logging.getLogger("storage_manager")

STORAGE_CONFIG_PATH = get_storage_config_path()


class StorageManager:
    """
    Periodically scans SSD pools and enforces disk limits.

    Thread-safe: ``get_status()`` can be called from any thread.
    """

    def __init__(self, storage_config: Optional[dict] = None,
                 scan_interval: int = 3600):
        self._config = storage_config or self._load_config()
        self._scan_interval = scan_interval
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self._last_scan: Optional[float] = None
        self._lock = threading.Lock()
        self._pool_stats: Dict[str, dict] = {}

    @staticmethod
    def _load_config() -> dict:
        cfg = load_storage_config_dict()
        if cfg is not None:
            return cfg
        return {
            "ssd_pools": [{"path": "/data/video", "cameras": []}],
            "retention_days": 10,
            "high_watermark_percent": 85,
            "archive": {"enabled": False, "path": "/archive/video"},
        }

    def start(self):
        self._stop.clear()
        self._thread = threading.Thread(
            target=self._loop, daemon=True, name="storage-mgr",
        )
        self._thread.start()
        log.info("StorageManager started (scan every %ds)", self._scan_interval)

    def stop(self):
        self._stop.set()

    def _loop(self):
        self._scan()
        while not self._stop.wait(timeout=self._scan_interval):
            self._scan()

    def _scan(self):
        watermark = self._config.get("high_watermark_percent", 85)
        retention = self._config.get("retention_days", 10)
        archive_cfg = self._config.get("archive", {})
        archive_enabled = archive_cfg.get("enabled", False)
        archive_path = archive_cfg.get("path", "/archive/video")

        for pool in self._config.get("ssd_pools", []):
            pool_path = pool["path"]
            if not os.path.isdir(pool_path):
                continue

            try:
                usage = shutil.disk_usage(pool_path)
                pct = (usage.used / usage.total) * 100 if usage.total > 0 else 0
            except OSError as e:
                log.error("Cannot stat %s: %s", pool_path, e)
                continue

            with self._lock:
                self._pool_stats[pool_path] = {
                    "total_gb": round(usage.total / (1024 ** 3), 2),
                    "used_gb": round(usage.used / (1024 ** 3), 2),
                    "free_gb": round(usage.free / (1024 ** 3), 2),
                    "percent": round(pct, 1),
                }

            if pct >= watermark:
                log.warning("Pool %s at %.1f%% (watermark %d%%), cleaning...",
                            pool_path, pct, watermark)
                self._cleanup_oldest(pool_path, watermark, archive_enabled,
                                     archive_path)

            self._enforce_retention(pool_path, retention, archive_enabled,
                                    archive_path)

        self._last_scan = time.time()
        log.debug("Storage scan complete")

    def _cleanup_oldest(self, pool_path: str, watermark: int,
                        archive_enabled: bool, archive_path: str):
        """Delete oldest day-folders until usage drops below watermark."""
        segments = self._list_day_folders(pool_path)
        for day_path in segments:
            try:
                usage = shutil.disk_usage(pool_path)
                pct = (usage.used / usage.total) * 100 if usage.total > 0 else 0
            except OSError:
                break

            if pct < watermark - 5:
                break

            if archive_enabled:
                self._archive_folder(day_path, pool_path, archive_path)

            log.info("Deleting old recordings: %s", day_path)
            try:
                shutil.rmtree(day_path)
            except OSError as e:
                log.error("Failed to delete %s: %s", day_path, e)

    def _enforce_retention(self, pool_path: str, retention_days: int,
                           archive_enabled: bool, archive_path: str):
        """Delete day-folders older than retention_days."""
        cutoff = time.time() - (retention_days * 86400)
        for day_path in self._list_day_folders(pool_path):
            try:
                mtime = os.path.getmtime(day_path)
            except OSError:
                continue
            if mtime < cutoff:
                if archive_enabled:
                    self._archive_folder(day_path, pool_path, archive_path)
                log.info("Retention delete: %s", day_path)
                try:
                    shutil.rmtree(day_path)
                except OSError as e:
                    log.error("Retention delete failed %s: %s", day_path, e)

    @staticmethod
    def _list_day_folders(pool_path: str) -> List[str]:
        """Return sorted list of cam/YYYY-MM-DD paths (oldest first)."""
        folders = []
        try:
            for cam_dir in sorted(os.listdir(pool_path)):
                cam_path = os.path.join(pool_path, cam_dir)
                if not os.path.isdir(cam_path):
                    continue
                for day_dir in sorted(os.listdir(cam_path)):
                    day_path = os.path.join(cam_path, day_dir)
                    if os.path.isdir(day_path) and len(day_dir) == 10:
                        folders.append(day_path)
        except OSError:
            pass
        folders.sort(key=lambda p: os.path.basename(p))
        return folders

    @staticmethod
    def _archive_folder(day_path: str, pool_path: str, archive_path: str):
        """Copy day folder to archive before deletion."""
        rel = os.path.relpath(day_path, pool_path)
        dest = os.path.join(archive_path, rel)
        try:
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            if not os.path.exists(dest):
                shutil.copytree(day_path, dest)
                metadata = {
                    "archived_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
                    "source": day_path,
                    "files": os.listdir(day_path),
                    "alerts_in_period": [],
                    "training_notes": {
                        "has_night_footage": None,
                        "detected_classes": [],
                    },
                }
                with open(os.path.join(dest, "metadata.json"), "w") as f:
                    json.dump(metadata, f, indent=2)
                log.info("Archived %s → %s", day_path, dest)
        except OSError as e:
            log.error("Archive failed %s: %s", day_path, e)

    def get_status(self) -> dict:
        with self._lock:
            return {
                "pools": dict(self._pool_stats),
                "last_scan": self._last_scan,
                "retention_days": self._config.get("retention_days", 10),
                "high_watermark_percent": self._config.get(
                    "high_watermark_percent", 85),
            }
