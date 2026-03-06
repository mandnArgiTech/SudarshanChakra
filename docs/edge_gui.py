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
import cv2
import base64
import time
import threading
from flask import Flask, render_template_string, request, jsonify

# Latest snapshot cache per camera
_snapshot_cache = {}  # camera_id → (timestamp, jpeg_bytes)
_snapshot_lock = threading.Lock()


def update_snapshot(camera_id: str, frame):
    """Called by inference pipeline to cache latest frame per camera."""
    _, jpeg = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
    with _snapshot_lock:
        _snapshot_cache[camera_id] = (time.time(), jpeg.tobytes())


def create_app(zone_engine, cameras, config_dir):
    """Factory function — creates and returns Flask app."""
    app = Flask(__name__)
    
    ZONES_PATH = os.path.join(config_dir, "zones.json")
    
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
            from flask import Response
            return Response(jpeg_bytes, mimetype="image/jpeg")
        else:
            # Return a placeholder dark image if no snapshot yet
            import numpy as np
            placeholder = np.zeros((480, 640, 3), dtype=np.uint8)
            cv2.putText(placeholder, f"Waiting for {camera_id}...",
                        (120, 240), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (100, 100, 100), 2)
            _, jpeg = cv2.imencode(".jpg", placeholder)
            from flask import Response
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
    
    return app
