#!/usr/bin/env python3
"""
edge_gui.py — Flask Web GUI for Virtual Fence Polygon Drawing
==============================================================
Local web interface on Edge Node (port 5000) that allows farm managers
to view camera snapshots and draw virtual fence polygons by clicking
on the image. Polygons are saved to /app/config/zones.json.

Access: http://<edge-node-ip>:5000
"""

import json
import os
import shutil
import cv2
import time
import threading
from flask import Flask, render_template_string, request, jsonify, send_file, abort, Response

# Latest snapshot cache per camera
_snapshot_cache = {}  # camera_id → (timestamp, jpeg_bytes)
_snapshot_lock = threading.Lock()


def update_snapshot(camera_id: str, frame):
    """Called by inference pipeline to cache latest frame per camera."""
    _, jpeg = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
    with _snapshot_lock:
        _snapshot_cache[camera_id] = (time.time(), jpeg.tobytes())


def create_app(zone_engine, cameras, config_dir, mqtt_client=None, pipeline=None,
               model_pt_path=None, node_id=None, alert_engine=None,
               video_recorder=None, storage_manager=None):
    """Factory function — creates and returns Flask app."""
    app = Flask(__name__)

    ZONES_PATH = os.path.join(config_dir, "zones.json")
    SNAPSHOT_DIR = os.getenv("SNAPSHOT_DIR", "/tmp/snapshots")
    _node_id = node_id or os.getenv("NODE_ID", "edge-node")
    _model_pt = model_pt_path or os.getenv("PT_PATH", "/app/models/yolov8n_farm.pt")

    # ── HTML Template with embedded JS polygon editor ──
    EDITOR_HTML = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>SudarshanChakra — Edge Zone Editor</title>
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body {
                font-family: 'Segoe UI', system-ui, sans-serif;
                background: #0a0e17; color: #f1f5f9;
            }
            .header {
                display: flex; justify-content: space-between; align-items: center;
                padding: 16px 24px; background: #111827;
                border-bottom: 1px solid #1e293b;
            }
            .header h1 { font-size: 18px; color: #f59e0b; letter-spacing: 1px; }
            .header .node-badge {
                padding: 4px 12px; background: #22c55e22; border: 1px solid #22c55e44;
                border-radius: 20px; color: #22c55e; font-size: 12px;
            }
            .container { display: flex; height: calc(100vh - 56px); }
            .sidebar {
                width: 320px; background: #111827; border-right: 1px solid #1e293b;
                padding: 16px; overflow-y: auto; flex-shrink: 0;
            }
            .main { flex: 1; padding: 16px; display: flex; flex-direction: column; }
            .camera-select {
                display: grid; grid-template-columns: 1fr 1fr; gap: 8px;
                margin-bottom: 16px;
            }
            .cam-btn {
                padding: 10px; background: #1a2235; border: 1px solid #1e293b;
                border-radius: 8px; color: #94a3b8; cursor: pointer; font-size: 12px;
                text-align: center; transition: all 0.2s;
            }
            .cam-btn.active { border-color: #f59e0b; color: #f59e0b; background: #f59e0b15; }
            .cam-btn:hover { border-color: #94a3b8; }
            #canvas-container {
                flex: 1; position: relative; background: #111827;
                border-radius: 12px; overflow: hidden; border: 1px solid #1e293b;
            }
            #editor-canvas { cursor: crosshair; display: block; width: 100%; height: 100%; }
            .zone-list { margin-top: 16px; }
            .zone-item {
                padding: 12px; background: #1a2235; border: 1px solid #1e293b;
                border-radius: 8px; margin-bottom: 8px;
            }
            .zone-item .zone-name { font-weight: 600; font-size: 14px; }
            .zone-item .zone-meta { color: #64748b; font-size: 12px; margin-top: 4px; }
            .zone-item .zone-actions { margin-top: 8px; display: flex; gap: 6px; }
            .btn {
                padding: 6px 14px; border-radius: 6px; border: 1px solid;
                cursor: pointer; font-size: 12px; font-weight: 600;
            }
            .btn-primary { background: #f59e0b22; border-color: #f59e0b44; color: #f59e0b; }
            .btn-danger { background: #ef444422; border-color: #ef444444; color: #ef4444; }
            .btn-success { background: #22c55e22; border-color: #22c55e44; color: #22c55e; }
            .btn-secondary { background: #64748b22; border-color: #64748b44; color: #94a3b8; }
            .form-group { margin-bottom: 12px; }
            .form-group label { display: block; font-size: 12px; color: #94a3b8; margin-bottom: 4px; }
            .form-group input, .form-group select {
                width: 100%; padding: 8px 12px; background: #0a0e17;
                border: 1px solid #1e293b; border-radius: 6px; color: #f1f5f9;
                font-size: 13px;
            }
            .instructions {
                padding: 12px; background: #1a2235; border-radius: 8px;
                border: 1px solid #1e293b; margin-bottom: 16px;
                font-size: 12px; color: #94a3b8; line-height: 1.6;
            }
            .point-count {
                text-align: center; padding: 8px; color: #f59e0b;
                font-size: 13px; font-weight: 600;
            }
        </style>
    </head>
    <body>
        <div class="header">
            <h1>◈ SUDARSHAN CHAKRA — Zone Editor</h1>
            <span class="node-badge">● {{ node_id }} Online</span>
        </div>
        <div class="container">
            <div class="sidebar">
                <div class="instructions">
                    <strong>How to draw a zone:</strong><br>
                    1. Select a camera below<br>
                    2. Click points on the image to define polygon vertices<br>
                    3. Fill in zone name, type, and target classes<br>
                    4. Click "Save Zone" to save to config
                </div>

                <h3 style="font-size: 14px; margin-bottom: 8px; color: #f59e0b;">Cameras</h3>
                <div class="camera-select" id="camera-grid"></div>

                <h3 style="font-size: 14px; margin: 16px 0 8px; color: #f59e0b;">New Zone</h3>
                <div class="form-group">
                    <label>Zone Name</label>
                    <input type="text" id="zone-name" placeholder="e.g., Pond Danger Zone">
                </div>
                <div class="form-group">
                    <label>Zone Type</label>
                    <select id="zone-type">
                        <option value="intrusion">Intrusion Detection</option>
                        <option value="zero_tolerance">Zero Tolerance (Pond/Danger)</option>
                        <option value="livestock_containment">Livestock Containment</option>
                        <option value="hazard">Hazard Zone (Snake/Fire)</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Priority</label>
                    <select id="zone-priority">
                        <option value="critical">Critical</option>
                        <option value="high">High</option>
                        <option value="warning">Warning</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Target Classes (comma-separated)</label>
                    <input type="text" id="zone-targets" placeholder="person, child, snake"
                           value="person">
                </div>
                <div class="point-count" id="point-count">Click on image to add points (0 points)</div>
                <div style="display: flex; gap: 8px; margin-top: 8px;">
                    <button class="btn btn-success" onclick="saveZone()">Save Zone</button>
                    <button class="btn btn-secondary" onclick="clearPoints()">Clear Points</button>
                    <button class="btn btn-danger" onclick="undoPoint()">Undo</button>
                </div>

                <h3 style="font-size: 14px; margin: 24px 0 8px; color: #f59e0b;">
                    Existing Zones
                </h3>
                <div class="zone-list" id="zone-list"></div>
            </div>

            <div class="main">
                <div id="canvas-container">
                    <canvas id="editor-canvas"></canvas>
                </div>
            </div>
        </div>

        <script>
            const cameras = {{ cameras_json | safe }};
            let currentCamera = cameras.length > 0 ? cameras[0].id : null;
            let points = [];
            let existingZones = {};
            let canvasImg = null;

            const canvas = document.getElementById('editor-canvas');
            const ctx = canvas.getContext('2d');

            // Initialize camera grid
            function initCameraGrid() {
                const grid = document.getElementById('camera-grid');
                cameras.forEach(cam => {
                    const btn = document.createElement('div');
                    btn.className = 'cam-btn' + (cam.id === currentCamera ? ' active' : '');
                    btn.textContent = cam.name;
                    btn.dataset.camId = cam.id;
                    btn.onclick = () => selectCamera(cam.id);
                    grid.appendChild(btn);
                });
            }

            function selectCamera(camId) {
                currentCamera = camId;
                points = [];
                updatePointCount();
                document.querySelectorAll('.cam-btn').forEach(b => {
                    b.classList.toggle('active', b.dataset.camId === camId);
                });
                loadSnapshot();
                loadZones();
            }

            function loadSnapshot() {
                if (!currentCamera) return;
                const img = new Image();
                img.onload = () => {
                    canvasImg = img;
                    resizeCanvas();
                    draw();
                };
                img.src = '/api/snapshot/' + currentCamera + '?t=' + Date.now();
            }

            function resizeCanvas() {
                const container = document.getElementById('canvas-container');
                canvas.width = container.clientWidth;
                canvas.height = container.clientHeight;
            }

            function draw() {
                ctx.clearRect(0, 0, canvas.width, canvas.height);

                if (canvasImg) {
                    // Draw image scaled to fit canvas
                    const scale = Math.min(
                        canvas.width / canvasImg.width,
                        canvas.height / canvasImg.height
                    );
                    const w = canvasImg.width * scale;
                    const h = canvasImg.height * scale;
                    const x = (canvas.width - w) / 2;
                    const y = (canvas.height - h) / 2;
                    ctx.drawImage(canvasImg, x, y, w, h);
                }

                // Draw existing zones for this camera
                const camZones = existingZones[currentCamera] || [];
                camZones.forEach(zone => {
                    drawPolygon(zone.polygon, '#22c55e44', '#22c55e', zone.name);
                });

                // Draw current polygon being drawn
                if (points.length > 0) {
                    drawPolygon(points, '#f59e0b44', '#f59e0b', 'Drawing...');
                    // Draw points
                    points.forEach((p, i) => {
                        ctx.beginPath();
                        ctx.arc(p[0], p[1], 5, 0, Math.PI * 2);
                        ctx.fillStyle = '#f59e0b';
                        ctx.fill();
                        ctx.strokeStyle = '#0a0e17';
                        ctx.lineWidth = 2;
                        ctx.stroke();
                        // Label
                        ctx.fillStyle = '#f59e0b';
                        ctx.font = '11px monospace';
                        ctx.fillText(String(i + 1), p[0] + 8, p[1] - 8);
                    });
                }
            }

            function drawPolygon(pts, fill, stroke, label) {
                if (pts.length < 2) return;
                ctx.beginPath();
                ctx.moveTo(pts[0][0], pts[0][1]);
                for (let i = 1; i < pts.length; i++) {
                    ctx.lineTo(pts[i][0], pts[i][1]);
                }
                if (pts.length > 2) ctx.closePath();
                ctx.fillStyle = fill;
                ctx.fill();
                ctx.strokeStyle = stroke;
                ctx.lineWidth = 2;
                ctx.stroke();
                // Label
                if (label && pts.length > 0) {
                    ctx.fillStyle = stroke;
                    ctx.font = 'bold 12px monospace';
                    ctx.fillText(label, pts[0][0] + 4, pts[0][1] - 10);
                }
            }

            // Canvas click handler
            canvas.addEventListener('click', (e) => {
                const rect = canvas.getBoundingClientRect();
                const x = Math.round(e.clientX - rect.left);
                const y = Math.round(e.clientY - rect.top);
                points.push([x, y]);
                updatePointCount();
                draw();
            });

            function updatePointCount() {
                document.getElementById('point-count').textContent =
                    `Click on image to add points (${points.length} points)`;
            }

            function clearPoints() {
                points = [];
                updatePointCount();
                draw();
            }

            function undoPoint() {
                points.pop();
                updatePointCount();
                draw();
            }

            // Scale canvas points back to image coordinates
            function canvasToImageCoords(canvasPoints) {
                if (!canvasImg) return canvasPoints;
                const scale = Math.min(
                    canvas.width / canvasImg.width,
                    canvas.height / canvasImg.height
                );
                const offsetX = (canvas.width - canvasImg.width * scale) / 2;
                const offsetY = (canvas.height - canvasImg.height * scale) / 2;
                return canvasPoints.map(([cx, cy]) => [
                    Math.round((cx - offsetX) / scale),
                    Math.round((cy - offsetY) / scale),
                ]);
            }

            async function saveZone() {
                const name = document.getElementById('zone-name').value.trim();
                const type = document.getElementById('zone-type').value;
                const priority = document.getElementById('zone-priority').value;
                const targets = document.getElementById('zone-targets').value
                    .split(',').map(s => s.trim()).filter(Boolean);

                if (!name) { alert('Please enter a zone name'); return; }
                if (points.length < 3) { alert('Need at least 3 points for a polygon'); return; }
                if (!currentCamera) { alert('Select a camera first'); return; }

                const imagePoints = canvasToImageCoords(points);

                const zone = {
                    id: 'zone-' + name.toLowerCase().replace(/[^a-z0-9]/g, '-'),
                    name: name,
                    type: type,
                    priority: priority,
                    target_classes: targets,
                    polygon: imagePoints,
                };

                try {
                    const resp = await fetch('/api/zones/' + currentCamera, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(zone),
                    });
                    const data = await resp.json();
                    if (data.success) {
                        clearPoints();
                        document.getElementById('zone-name').value = '';
                        loadZones();
                        alert('Zone saved successfully!');
                    } else {
                        alert('Error: ' + data.error);
                    }
                } catch (e) {
                    alert('Failed to save zone: ' + e.message);
                }
            }

            async function loadZones() {
                try {
                    const resp = await fetch('/api/zones');
                    existingZones = await resp.json();
                    renderZoneList();
                    draw();
                } catch (e) {
                    console.error('Failed to load zones:', e);
                }
            }

            function renderZoneList() {
                const list = document.getElementById('zone-list');
                const camZones = existingZones[currentCamera] || [];
                list.innerHTML = camZones.map(z => `
                    <div class="zone-item">
                        <div class="zone-name">${z.name}</div>
                        <div class="zone-meta">
                            ${z.type} · ${z.priority} · ${z.target_classes.join(', ')}
                            · ${z.polygon.length} pts
                        </div>
                        <div class="zone-actions">
                            <button class="btn btn-danger" onclick="deleteZone('${z.id}')">
                                Delete
                            </button>
                        </div>
                    </div>
                `).join('');
            }

            async function deleteZone(zoneId) {
                if (!confirm('Delete this zone?')) return;
                try {
                    await fetch('/api/zones/' + currentCamera + '/' + zoneId, {
                        method: 'DELETE',
                    });
                    loadZones();
                } catch (e) {
                    alert('Failed to delete zone: ' + e.message);
                }
            }

            // Auto-refresh snapshot every 2 seconds
            setInterval(loadSnapshot, 2000);

            // Handle window resize
            window.addEventListener('resize', () => {
                resizeCanvas();
                draw();
            });

            // Initialize
            initCameraGrid();
            loadSnapshot();
            loadZones();
        </script>
    </body>
    </html>
    """

    @app.route("/")
    def index():
        cameras_json = json.dumps([
            {"id": c.id, "name": c.name} for c in cameras
        ])
        return render_template_string(
            EDITOR_HTML,
            cameras_json=cameras_json,
            node_id=os.getenv("NODE_ID", "edge-node-a"),
        )

    @app.route("/api/snapshot/<camera_id>")
    def get_snapshot(camera_id):
        with _snapshot_lock:
            entry = _snapshot_cache.get(camera_id)

        if entry:
            ts, jpeg_bytes = entry
            return Response(jpeg_bytes, mimetype="image/jpeg")
        else:
            import numpy as np
            # Dark gray (not pure black) so "waiting" is visible in dashboard thumbnails
            placeholder = np.full((480, 640, 3), (48, 48, 56), dtype=np.uint8)
            cv2.putText(placeholder, f"Waiting for {camera_id}...",
                        (40, 240), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (200, 200, 210), 2)
            cv2.putText(placeholder, "No frames in cache yet",
                        (40, 290), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (160, 160, 175), 2)
            _, jpeg = cv2.imencode(".jpg", placeholder)
            return Response(jpeg.tobytes(), mimetype="image/jpeg")

    @app.route("/api/zones")
    def get_all_zones():
        """Return all zones grouped by camera_id."""
        try:
            with open(ZONES_PATH) as f:
                data = json.load(f)
            # Normalize structure to {camera_id: [zones]}
            result = {}
            if "cameras" in data:
                for cam in data["cameras"]:
                    result[cam["camera_id"]] = cam.get("zones", [])
            elif "camera_id" in data:
                result[data["camera_id"]] = data.get("zones", [])
            return jsonify(result)
        except FileNotFoundError:
            return jsonify({})

    @app.route("/api/zones/<camera_id>", methods=["POST"])
    def add_zone(camera_id):
        """Add a new zone for a camera."""
        zone = request.json

        try:
            with open(ZONES_PATH) as f:
                data = json.load(f)
        except FileNotFoundError:
            data = {"cameras": []}

        # Ensure structure
        if "cameras" not in data:
            data = {"cameras": [{"camera_id": camera_id, "zones": []}]}

        # Find or create camera entry
        cam_entry = None
        for cam in data["cameras"]:
            if cam["camera_id"] == camera_id:
                cam_entry = cam
                break

        if not cam_entry:
            cam_entry = {"camera_id": camera_id, "zones": []}
            data["cameras"].append(cam_entry)

        # Check for duplicate zone ID
        existing_ids = [z["id"] for z in cam_entry.get("zones", [])]
        if zone["id"] in existing_ids:
            return jsonify({"success": False, "error": "Zone ID already exists"})

        cam_entry.setdefault("zones", []).append(zone)

        with open(ZONES_PATH, "w") as f:
            json.dump(data, f, indent=2)

        # Reload zone engine
        zone_engine.reload()

        return jsonify({"success": True, "zone_id": zone["id"]})

    @app.route("/api/zones/<camera_id>/<zone_id>", methods=["DELETE"])
    def delete_zone(camera_id, zone_id):
        """Delete a zone."""
        try:
            with open(ZONES_PATH) as f:
                data = json.load(f)
        except FileNotFoundError:
            return jsonify({"success": False, "error": "No zones file"})

        for cam in data.get("cameras", []):
            if cam["camera_id"] == camera_id:
                cam["zones"] = [z for z in cam.get("zones", []) if z["id"] != zone_id]

        with open(ZONES_PATH, "w") as f:
            json.dump(data, f, indent=2)

        zone_engine.reload()
        return jsonify({"success": True})

    @app.route("/snapshots/<path:filename>")
    def serve_alert_snapshot(filename):
        safe = os.path.basename(filename)
        if not safe or safe != filename.replace("\\", "/").split("/")[-1]:
            abort(404)
        base = os.path.abspath(SNAPSHOT_DIR)
        path = os.path.abspath(os.path.join(base, safe))
        if not path.startswith(base) or not os.path.isfile(path):
            abort(404)
        return send_file(path, mimetype="image/jpeg")

    def _health_payload():
        mqtt_ok = bool(mqtt_client and mqtt_client.is_connected())
        stats = pipeline.get_stats() if pipeline and hasattr(pipeline, "get_stats") else {}
        cams = stats.get("cameras_connected", stats.get("cameras_total", 0))
        model_ok = os.path.isfile(_model_pt) or os.path.isfile(
            os.getenv("ENGINE_PATH", "/app/models/yolov8n_farm.engine"))
        try:
            zones = sum(len(z) for z in zone_engine.get_all_zones().values())
        except Exception:
            zones = 0
        try:
            du = shutil.disk_usage(SNAPSHOT_DIR)
            disk_gb = round(du.free / (1024 ** 3), 2)
        except Exception:
            disk_gb = 0.0
        ok = mqtt_ok and (cams > 0 or stats.get("mode") == "MOCK") and model_ok and disk_gb > 0.05
        return {
            "status": "healthy" if ok else "degraded",
            "node_id": _node_id,
            "checks": {
                "mqtt": mqtt_ok,
                "cameras_connected": cams,
                "model": model_ok,
                "zones_configured": zones,
                "disk_free_gb": disk_gb,
            },
        }

    @app.route("/metrics")
    def prom_metrics():
        try:
            from metrics import render_prometheus
            return Response(render_prometheus(), mimetype="text/plain; version=0.0.4")
        except ImportError:
            return Response("# metrics unavailable\n", mimetype="text/plain")

    @app.route("/health")
    def health():
        p = _health_payload()
        code = 200 if p["status"] == "healthy" else 503
        return jsonify(p), code

    @app.route("/api/status")
    def api_status():
        stats = pipeline.get_stats() if pipeline and hasattr(pipeline, "get_stats") else {}
        return jsonify({
            "node_id": _node_id,
            "pipeline": stats,
            "mqtt_connected": bool(mqtt_client and mqtt_client.is_connected()),
            "cameras_configured": len(cameras),
            "zones_by_camera": {k: len(v) for k, v in zone_engine.get_all_zones().items()},
            "timestamp": time.time(),
        })

    @app.route("/api/cameras/status")
    def api_cameras_status():
        stats = pipeline.get_stats() if pipeline and hasattr(pipeline, "get_stats") else {}
        out = []
        if pipeline and hasattr(pipeline, "grabbers"):
            for g in pipeline.grabbers:
                out.append({
                    "camera_id": g.config.id,
                    "name": g.config.name,
                    "connected": bool(getattr(g, "connected", False)),
                    "frames": int(getattr(g, "frame_count", 0)),
                })
        elif pipeline and hasattr(pipeline, "mock_cameras"):
            for cid, grab in pipeline.mock_cameras.items():
                out.append({
                    "camera_id": cid,
                    "connected": True,
                    "frames": int(getattr(grab, "frame_count", 0)),
                })
        else:
            for c in cameras:
                out.append({"camera_id": c.id, "name": c.name, "configured": True})
        return jsonify({"node_id": _node_id, "cameras": out, "pipeline": stats})

    @app.route("/api/model")
    def api_model():
        mp = _model_pt
        if pipeline and getattr(pipeline, "model_path", None):
            mp = pipeline.model_path
        base = os.path.basename(mp) if mp else None
        return jsonify({
            "node_id": _node_id,
            "model_path": mp,
            "weights_file": base,
        })

    STATUS_HTML = """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>Edge Status</title>
    <style>body{font-family:system-ui;background:#0f172a;color:#e2e8f0;padding:24px;}
    pre{background:#1e293b;padding:16px;border-radius:8px;overflow:auto;}</style></head>
    <body><h1>Node {{ node_id }}</h1><p><a href="/health">/health</a> · <a href="/api/status">/api/status</a></p>
    <pre id="j">Loading…</pre>
    <script>fetch('/api/status').then(r=>r.json()).then(d=>{
      document.getElementById('j').textContent = JSON.stringify(d,null,2);
    });</script></body></html>
    """

    @app.route("/status")
    def status_page():
        return render_template_string(STATUS_HTML, node_id=_node_id)

    @app.route("/api/alerts")
    def api_alerts():
        if alert_engine is None or not hasattr(alert_engine, "get_alert_history"):
            return jsonify([])
        return jsonify(alert_engine.get_alert_history())

    CAMERAS_HTML = """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>Cameras</title>
    <style>body{background:#0f172a;color:#e2e8f0;font-family:system-ui;padding:16px;}
    h1{color:#f59e0b;} .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;max-width:1400px;}
    .cell{background:#1e293b;border-radius:8px;padding:8px;text-align:center;}
    img{width:100%;max-height:220px;object-fit:cover;border-radius:4px;}</style></head>
    <body><h1>Camera grid</h1><div class="grid" id="g"></div>
    <script>
    const cams = {{ cams|tojson }};
    const g = document.getElementById('g');
    cams.forEach(c => {
      const d = document.createElement('div'); d.className = 'cell';
      d.innerHTML = '<div>'+c.name+'</div><img src="/api/snapshot/'+c.id+'?t='+Date.now()+'">';
      g.appendChild(d);
    });
    setInterval(()=>{ document.querySelectorAll('.cell img').forEach((img,i)=>{
      img.src='/api/snapshot/'+cams[i].id+'?t='+Date.now(); }); }, 3000);
    </script></body></html>
    """

    @app.route("/cameras")
    def cameras_page():
        cams = [{"id": c.id, "name": c.name} for c in cameras]
        return render_template_string(CAMERAS_HTML, cams=cams)

    ALERTS_HTML = (
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
        "<title>Alert history</title>"
        "<style>body{background:#0f172a;color:#e2e8f0;font-family:system-ui;padding:16px;}"
        "table{border-collapse:collapse;width:100%;max-width:1000px;}"
        "th,td{border:1px solid #334155;padding:8px;text-align:left;}"
        "th{background:#1e293b;color:#f59e0b;}</style></head>"
        "<body><h1>Recent alerts</h1>"
        "<table><thead><tr>"
        "<th>Time</th><th>Camera</th><th>Class</th><th>Zone</th><th>Priority</th>"
        "</tr></thead><tbody id=\"t\"></tbody></table>"
        "<script>\n"
        "async function load(){\n"
        "  const r = await fetch('/api/alerts');\n"
        "  const d = await r.json();\n"
        "  const row = (a) => '<tr><td>'+new Date(a.timestamp*1000).toLocaleString()"
        "+'</td><td>'+a.camera_id+'</td><td>'+a.class+'</td><td>'+a.zone+'</td>"
        "<td>'+a.priority+'</td></tr>';\n"
        "  document.getElementById('t').innerHTML = "
        "d.slice().reverse().map(row).join('') || "
        "'<tr><td colspan=\"5\">No alerts yet</td></tr>';\n"
        "}\n"
        "load(); setInterval(load, 5000);\n"
        "</script></body></html>"
    )

    @app.route("/alerts")
    def alerts_page():
        return render_template_string(ALERTS_HTML)

    # ── MJPEG Live Stream ──
    @app.route("/api/live/<camera_id>")
    def live_stream(camera_id):
        """MJPEG multipart stream from latest inference frames."""
        def generate():
            while True:
                with _snapshot_lock:
                    entry = _snapshot_cache.get(camera_id)
                if entry:
                    _ts, jpeg_bytes = entry
                    yield (
                        b"--frame\r\n"
                        b"Content-Type: image/jpeg\r\n"
                        b"Content-Length: " + str(len(jpeg_bytes)).encode() + b"\r\n"
                        b"\r\n" + jpeg_bytes + b"\r\n"
                    )
                time.sleep(0.33)

        return Response(
            generate(),
            mimetype="multipart/x-mixed-replace; boundary=frame",
        )

    # ── Serve recorded video segments with Range support ──
    @app.route("/api/video/<camera_id>/<path:subpath>")
    def serve_video(camera_id, subpath):
        """Serve MP4 segment with HTTP Range support for seeking."""
        if video_recorder is None:
            abort(503, description="Video recorder not available")
        base = video_recorder.get_base_path(camera_id)
        safe_path = os.path.normpath(os.path.join(base, subpath))
        if not safe_path.startswith(os.path.abspath(base)):
            abort(403)
        if not os.path.isfile(safe_path):
            abort(404)
        return send_file(safe_path, mimetype="video/mp4", conditional=True)

    # ── List recordings ──
    @app.route("/api/recordings/<camera_id>")
    def list_recordings(camera_id):
        """List recording dates/hours/segments for a camera."""
        if video_recorder is None:
            return jsonify({"dates": []})
        base = video_recorder.get_base_path(camera_id)
        date_filter = request.args.get("date")
        result = {"camera_id": camera_id, "dates": []}
        if not os.path.isdir(base):
            return jsonify(result)
        for date_dir in sorted(os.listdir(base)):
            date_path = os.path.join(base, date_dir)
            if not os.path.isdir(date_path) or len(date_dir) != 10:
                continue
            if date_filter and date_dir != date_filter:
                continue
            date_entry = {"date": date_dir, "hours": []}
            for hour_dir in sorted(os.listdir(date_path)):
                hour_path = os.path.join(date_path, hour_dir)
                if not os.path.isdir(hour_path):
                    continue
                segments = sorted([
                    f for f in os.listdir(hour_path) if f.endswith(".mp4")
                ])
                if segments:
                    date_entry["hours"].append({
                        "hour": hour_dir,
                        "segments": segments,
                    })
            result["dates"].append(date_entry)
        return jsonify(result)

    # ── Alert clips ──
    @app.route("/api/clips/<alert_id>.mp4")
    def serve_clip(alert_id):
        """Serve a 30-second alert clip."""
        clips_dir = os.path.join(
            os.getenv("VIDEO_BASE_PATH", "/data/video"), "clips"
        )
        clip_path = os.path.join(clips_dir, f"{alert_id}.mp4")
        if not os.path.isfile(clip_path):
            abort(404)
        return send_file(clip_path, mimetype="video/mp4", conditional=True)

    # ── Storage status ──
    @app.route("/api/storage/status")
    def storage_status():
        """Return disk usage stats from StorageManager."""
        if storage_manager is None:
            return jsonify({"error": "Storage manager not available"}), 503
        return jsonify(storage_manager.get_status())

    # ── PTZ Control Routes ──
    _onvif_mgr = None
    try:
        from onvif_controller import OnvifManager
        _onvif_mgr = OnvifManager()
    except ImportError:
        pass

    def _get_onvif(camera_id):
        if _onvif_mgr is None:
            return None
        return _onvif_mgr.get(camera_id)

    @app.route("/api/ptz/<camera_id>/capabilities")
    def ptz_capabilities(camera_id):
        ctrl = _get_onvif(camera_id)
        if ctrl is None:
            return jsonify({"camera_id": camera_id, "supported": False,
                            "message": "ONVIF not configured for this camera"})
        return jsonify({"camera_id": camera_id, **ctrl.get_capabilities()})

    @app.route("/api/ptz/<camera_id>/move", methods=["POST"])
    def ptz_move(camera_id):
        ctrl = _get_onvif(camera_id)
        if ctrl is None:
            return jsonify({"ok": False, "error": "No ONVIF"}), 404
        body = request.json or {}
        ok = ctrl.continuous_move(
            pan=float(body.get("pan", 0)),
            tilt=float(body.get("tilt", 0)),
            zoom=float(body.get("zoom", 0)),
        )
        return jsonify({"ok": ok})

    @app.route("/api/ptz/<camera_id>/stop", methods=["POST"])
    def ptz_stop(camera_id):
        ctrl = _get_onvif(camera_id)
        if ctrl is None:
            return jsonify({"ok": False, "error": "No ONVIF"}), 404
        return jsonify({"ok": ctrl.stop()})

    @app.route("/api/ptz/<camera_id>/absolute", methods=["POST"])
    def ptz_absolute(camera_id):
        ctrl = _get_onvif(camera_id)
        if ctrl is None:
            return jsonify({"ok": False, "error": "No ONVIF"}), 404
        body = request.json or {}
        ok = ctrl.absolute_move(
            x=float(body.get("x", 0)),
            y=float(body.get("y", 0)),
            z=float(body.get("z", 0)),
        )
        return jsonify({"ok": ok})

    @app.route("/api/ptz/<camera_id>/presets")
    def ptz_presets(camera_id):
        ctrl = _get_onvif(camera_id)
        if ctrl is None:
            return jsonify([])
        return jsonify(ctrl.get_presets())

    @app.route("/api/ptz/<camera_id>/preset/goto", methods=["POST"])
    def ptz_goto_preset(camera_id):
        ctrl = _get_onvif(camera_id)
        if ctrl is None:
            return jsonify({"ok": False, "error": "No ONVIF"}), 404
        body = request.json or {}
        token = body.get("token", "")
        return jsonify({"ok": ctrl.goto_preset(token)})

    @app.route("/api/ptz/<camera_id>/preset/save", methods=["PUT"])
    def ptz_save_preset(camera_id):
        ctrl = _get_onvif(camera_id)
        if ctrl is None:
            return jsonify({"ok": False, "error": "No ONVIF"}), 404
        body = request.json or {}
        name = body.get("name", "")
        token = ctrl.set_preset(name)
        return jsonify({"ok": token is not None, "token": token})

    # Expose ONVIF manager for farm_edge_node to register cameras
    app.onvif_manager = _onvif_mgr

    return app
