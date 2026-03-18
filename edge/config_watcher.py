"""Watchdog-based config reload (optional). pip install watchdog"""
import logging
import os

log = logging.getLogger("config_watcher")


def start_zone_reload_watcher(zone_engine, paths, on_reload=None):
    try:
        from watchdog.observers import Observer
        from watchdog.events import FileSystemEventHandler
    except ImportError:
        log.warning("watchdog not installed — config hot-reload disabled")
        return None

    class H(FileSystemEventHandler):
        def on_modified(self, event):
            if event.src_path.endswith("zones.json"):
                zone_engine.reload()
                log.info("zones.json reloaded")
                if on_reload:
                    on_reload()

    obs = Observer()
    for p in paths:
        d = os.path.dirname(p) or "."
        if os.path.isdir(d):
            obs.schedule(H(), d, recursive=False)
    obs.start()
    return obs
