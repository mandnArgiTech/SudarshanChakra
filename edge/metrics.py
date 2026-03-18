"""Minimal Prometheus text metrics for edge node."""
import time

_start = time.time()
_counters = {"detections": 0, "alerts": 0, "errors": 0}


def inc_detections(n: int = 1):
    _counters["detections"] += n


def inc_alerts(n: int = 1):
    _counters["alerts"] += n


def render_prometheus() -> str:
    uptime = time.time() - _start
    lines = [
        "# HELP sc_edge_uptime_seconds Process uptime",
        "# TYPE sc_edge_uptime_seconds gauge",
        f"sc_edge_uptime_seconds {uptime:.3f}",
        "# HELP sc_edge_detections_total Mock/real detection counter",
        "# TYPE sc_edge_detections_total counter",
        f"sc_edge_detections_total {_counters['detections']}",
        "# HELP sc_edge_alerts_published_total",
        "# TYPE sc_edge_alerts_published_total counter",
        f"sc_edge_alerts_published_total {_counters['alerts']}",
    ]
    return "\n".join(lines) + "\n"
