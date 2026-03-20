"""Watchdog-based config reload (optional). pip install watchdog"""
import logging
import os

log = logging.getLogger("config_watcher")


def start_zone_reload_watcher(zone_engine, paths, on_reload=None):
    """
    Watch config files for changes. paths: e.g. [zones.json, suppression_rules.json].
    """
    try:
        from watchdog.observers import Observer
        from watchdog.events import FileSystemEventHandler
    except ImportError:
        log.warning("watchdog not installed — config hot-reload disabled")
        return None

    class H(FileSystemEventHandler):
        def on_modified(self, event):
            if event.is_directory:
                return
            p = event.src_path.replace("\\", "/")
            if p.endswith("zones.json"):
                zone_engine.reload()
                log.info("zones.json reloaded")
                if on_reload:
                    on_reload("zones")
            elif p.endswith("suppression_rules.json"):
                try:
                    from detection_filters import reload_suppression_rules
                    reload_suppression_rules()
                    log.info("suppression_rules.json reloaded")
                except Exception as e:
                    log.warning("suppression reload failed: %s", e)
                if on_reload:
                    on_reload("suppression")

    obs = Observer()
    seen = set()
    for p in paths:
        d = os.path.dirname(os.path.abspath(p)) or "."
        if d not in seen:
            seen.add(d)
            obs.schedule(H(), d, recursive=False)
    obs.start()
    return obs
