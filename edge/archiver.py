#!/usr/bin/env python3
"""
archiver.py — External HDD Archive Manager
=============================================
Moves completed day-folders from SSD to external HDD and writes
a ``metadata.json`` manifest per archived day.

Runs on-demand from ``storage_manager.py`` or as a standalone script.
"""

import json
import logging
import os
import shutil
import time
from typing import Optional

from storage_json_config import get_storage_config_path, load_storage_config_dict

log = logging.getLogger("archiver")

STORAGE_CONFIG_PATH = get_storage_config_path()


class Archiver:
    """Moves day-folders from SSD pool(s) to an archive location."""

    def __init__(self, storage_config: Optional[dict] = None):
        self._config = storage_config or self._load_config()
        self._archive_cfg = self._config.get("archive", {})
        self._archive_path = self._archive_cfg.get("path", "/archive/video")
        self._enabled = self._archive_cfg.get("enabled", False)

    @staticmethod
    def _load_config() -> dict:
        cfg = load_storage_config_dict()
        return cfg if cfg is not None else {}

    def is_enabled(self) -> bool:
        return self._enabled

    def archive_day(self, day_path: str, pool_base: str) -> bool:
        """
        Archive a single day-folder.

        Args:
            day_path: Absolute path like ``/data/video/cam-01/2026-03-15``
            pool_base: The SSD pool root, e.g. ``/data/video``

        Returns:
            True if archived successfully, False otherwise.
        """
        if not self._enabled:
            log.debug("Archiving disabled, skipping %s", day_path)
            return False

        if not os.path.isdir(day_path):
            log.warning("Day path does not exist: %s", day_path)
            return False

        rel = os.path.relpath(day_path, pool_base)
        dest = os.path.join(self._archive_path, rel)

        if os.path.exists(dest):
            log.debug("Archive destination already exists: %s", dest)
            return True

        try:
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copytree(day_path, dest)
        except OSError as e:
            log.error("Failed to copy %s → %s: %s", day_path, dest, e)
            return False

        files = []
        total_bytes = 0
        for root, _dirs, fnames in os.walk(dest):
            for fn in fnames:
                fp = os.path.join(root, fn)
                sz = os.path.getsize(fp)
                files.append({"name": fn, "size_bytes": sz})
                total_bytes += sz

        metadata = {
            "archived_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "source": day_path,
            "total_bytes": total_bytes,
            "file_count": len(files),
            "files": files,
            # Stubs for future enrichment (training export / alert cross-ref)
            "alerts_in_period": [],
            "training_notes": {
                "has_night_footage": None,
                "detected_classes": [],
            },
        }
        meta_path = os.path.join(dest, "metadata.json")
        try:
            with open(meta_path, "w") as f:
                json.dump(metadata, f, indent=2)
        except OSError as e:
            log.error("Failed to write metadata: %s", e)

        log.info("Archived %s → %s (%d files, %.1f MB)",
                 day_path, dest, len(files), total_bytes / (1024 * 1024))
        return True

    def archive_pool(self, pool_path: str, older_than_days: int = 10) -> int:
        """
        Archive all day-folders in a pool older than ``older_than_days``.

        Returns:
            Number of day-folders archived.
        """
        cutoff = time.time() - (older_than_days * 86400)
        archived = 0

        for cam_dir in sorted(os.listdir(pool_path)):
            cam_path = os.path.join(pool_path, cam_dir)
            if not os.path.isdir(cam_path):
                continue
            for day_dir in sorted(os.listdir(cam_path)):
                day_path = os.path.join(cam_path, day_dir)
                if not os.path.isdir(day_path) or len(day_dir) != 10:
                    continue
                try:
                    mtime = os.path.getmtime(day_path)
                except OSError:
                    continue
                if mtime < cutoff:
                    if self.archive_day(day_path, pool_path):
                        archived += 1

        return archived


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO,
                        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
    archiver = Archiver()
    if not archiver.is_enabled():
        log.info("Archiving is disabled in storage.json")
    else:
        config = archiver._config
        for pool in config.get("ssd_pools", []):
            n = archiver.archive_pool(
                pool["path"],
                older_than_days=config.get("retention_days", 10),
            )
            log.info("Archived %d day-folders from %s", n, pool["path"])
