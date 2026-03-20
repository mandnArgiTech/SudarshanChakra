#!/usr/bin/env python3
"""
LRU audio URL cache for PA / mpv — download HTTP(S) streams to disk, reuse local path.

Use from pa_controller before play(): resolved = audio_cache.resolve(url)
Environment:
  PA_AUDIO_CACHE   — cache directory (default /tmp/pa-cache)
  PA_CACHE_MAX_MB  — max total cache size before eviction (default 200)
"""
from __future__ import annotations

import hashlib
import logging
import os
import threading
import time
import urllib.request
from collections import OrderedDict

log = logging.getLogger("audio_cache")

DEFAULT_DIR = os.getenv("PA_AUDIO_CACHE", "/tmp/pa-cache")
MAX_BYTES = int(os.getenv("PA_CACHE_MAX_MB", "200")) * 1024 * 1024

_lock = threading.Lock()
_lru: OrderedDict[str, str] = OrderedDict()  # url -> path


def _key(url: str) -> str:
    h = hashlib.sha256(url.encode("utf-8", errors="replace")).hexdigest()[:24]
    ext = ".mp3"
    low = url.lower().split("?", 1)[0]
    if low.endswith(".wav"):
        ext = ".wav"
    elif low.endswith(".ogg"):
        ext = ".ogg"
    elif low.endswith(".m4a"):
        ext = ".m4a"
    return h + ext


def _total_size(cache_dir: str) -> int:
    total = 0
    try:
        for name in os.listdir(cache_dir):
            p = os.path.join(cache_dir, name)
            if os.path.isfile(p):
                total += os.path.getsize(p)
    except OSError:
        pass
    return total


def _evict(cache_dir: str) -> None:
    """Remove oldest files until under MAX_BYTES."""
    while _total_size(cache_dir) > MAX_BYTES and _lru:
        try:
            _, old_path = _lru.popitem(last=False)
            if os.path.isfile(old_path):
                os.remove(old_path)
                log.info("Cache evicted %s", old_path)
        except OSError as e:
            log.warning("Evict failed: %s", e)
            break


def resolve(url: str, cache_dir: str | None = None) -> str:
    """
    If url is local file, return as-is.
    If http(s), download to cache (if missing) and return local path.
    """
    if not url or not url.startswith(("http://", "https://")):
        return url

    d = cache_dir or DEFAULT_DIR
    os.makedirs(d, mode=0o755, exist_ok=True)
    path = os.path.join(d, _key(url))

    with _lock:
        if url in _lru:
            _lru.move_to_end(url)
            if os.path.isfile(_lru[url]):
                return _lru[url]

        if os.path.isfile(path):
            _lru[url] = path
            _lru.move_to_end(url)
            return path

        log.info("Downloading audio cache: %s", url[:80])
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "SudarshanChakra-PA/1.0"})
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = resp.read()
            tmp = path + ".part"
            with open(tmp, "wb") as f:
                f.write(data)
            os.replace(tmp, path)
        except Exception as e:
            log.error("Cache download failed: %s", e)
            return url

        _lru[url] = path
        _lru.move_to_end(url)
        _evict(d)
        return path


def clear_memory_index() -> None:
    with _lock:
        _lru.clear()
