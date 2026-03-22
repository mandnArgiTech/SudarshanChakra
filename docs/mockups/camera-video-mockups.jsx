import { useState } from "react";

const sc = {
  bg: "#0a0e17", surface: "#111827", surfaceAlt: "#1a2235", border: "#1e293b",
  accent: "#f59e0b", accentDim: "#92400e",
  critical: "#ef4444", high: "#f97316", warning: "#eab308",
  success: "#22c55e", info: "#3b82f6",
  text: "#f1f5f9", textDim: "#94a3b8", textMuted: "#64748b",
};

function Card({ children, style }) {
  return <div style={{ background: sc.surface, border: `1px solid ${sc.border}`, borderRadius: 12, ...style }}>{children}</div>;
}
function Badge({ children, color }) {
  return <span style={{ background: `${color}20`, color, border: `1px solid ${color}40`, borderRadius: 6, padding: "2px 10px", fontSize: 11, fontWeight: 600 }}>{children}</span>;
}
function Btn({ children, variant = "default", onClick, style }) {
  const v = { default: { background: sc.surfaceAlt, color: sc.text, border: `1px solid ${sc.border}` }, primary: { background: sc.accent, color: "#000", border: "none" }, danger: { background: `${sc.critical}20`, color: sc.critical, border: `1px solid ${sc.critical}40` }, success: { background: `${sc.success}20`, color: sc.success, border: `1px solid ${sc.success}40` } };
  return <button onClick={onClick} style={{ borderRadius: 8, padding: "8px 16px", fontSize: 13, fontWeight: 500, cursor: "pointer", fontFamily: "inherit", ...v[variant], ...style }}>{children}</button>;
}

// ═══ SCREEN 1: Camera Grid with Live Feeds ═══
function CameraGrid() {
  const cameras = [
    { id: "cam-01", name: "Front Gate", model: "VIGI C540-W", status: "online", hasPtz: true, fps: 2.5, alerts: 0 },
    { id: "cam-02", name: "Storage Shed", model: "Tapo C320WS", status: "online", hasPtz: false, fps: 2.3, alerts: 0 },
    { id: "cam-03", name: "Pond Area", model: "VIGI C540-W", status: "alert", hasPtz: true, fps: 3.0, alerts: 2 },
    { id: "cam-04", name: "East Perimeter", model: "Tapo C210", status: "online", hasPtz: true, fps: 2.0, alerts: 0 },
    { id: "cam-05", name: "Snake Zone", model: "Tapo C320WS", status: "online", hasPtz: false, fps: 2.3, alerts: 1 },
    { id: "cam-06", name: "Cattle Pen", model: "Tapo C210", status: "offline", hasPtz: true, fps: 0, alerts: 0 },
  ];
  const stColor = { online: sc.success, alert: sc.critical, offline: sc.textMuted };
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div><div style={{ fontSize: 18, fontWeight: 600, color: sc.text }}>Cameras</div><div style={{ fontSize: 12, color: sc.textDim }}>{cameras.filter(c=>c.status!=="offline").length} online</div></div>
        <Btn variant="primary" style={{ fontSize: 12 }}>+ Add Camera</Btn>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 10 }}>
        {cameras.map(cam => (
          <Card key={cam.id} style={{ overflow: "hidden" }}>
            <div style={{ background: "#000", aspectRatio: "16/9", position: "relative", display: "flex", alignItems: "center", justifyContent: "center" }}>
              {cam.status === "offline" ? <span style={{ color: sc.textMuted, fontSize: 12 }}>OFFLINE</span> : (
                <>
                  <div style={{ position: "absolute", top: 0, left: 0, right: 0, padding: "4px 8px", background: "linear-gradient(180deg, rgba(0,0,0,0.7) 0%, transparent 100%)", display: "flex", justifyContent: "space-between" }}>
                    <span style={{ fontSize: 10, color: "#fff", fontWeight: 500 }}>{cam.name}</span>
                    <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                      {cam.alerts > 0 && <span style={{ fontSize: 9, background: sc.critical, color: "#fff", padding: "1px 6px", borderRadius: 4 }}>{cam.alerts}</span>}
                      <span style={{ width: 7, height: 7, borderRadius: "50%", background: stColor[cam.status], boxShadow: `0 0 6px ${stColor[cam.status]}60` }} />
                    </div>
                  </div>
                  <div style={{ width: "100%", height: "100%", background: "linear-gradient(135deg, #0a1020, #0f1a30, #0a1020)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                    <span style={{ color: sc.textMuted, fontSize: 11 }}>MJPEG · {cam.fps} FPS</span>
                  </div>
                  <div style={{ position: "absolute", bottom: 4, left: 6, display: "flex", alignItems: "center", gap: 4 }}>
                    <span style={{ width: 6, height: 6, borderRadius: "50%", background: sc.critical }} /><span style={{ fontSize: 9, color: "#fff" }}>REC</span>
                  </div>
                  {cam.hasPtz && <div style={{ position: "absolute", bottom: 4, right: 6, fontSize: 9, background: "rgba(0,0,0,0.6)", color: sc.accent, padding: "1px 6px", borderRadius: 4 }}>PTZ</div>}
                </>
              )}
            </div>
            <div style={{ padding: "8px 10px" }}>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ fontSize: 12, fontWeight: 500, color: sc.text }}>{cam.id}</span>
                <span style={{ fontSize: 10, color: sc.textMuted }}>{cam.model}</span>
              </div>
              <div style={{ display: "flex", gap: 4, marginTop: 8 }}>
                <button style={{ flex: 1, background: sc.surfaceAlt, border: `1px solid ${sc.border}`, borderRadius: 6, padding: "5px 0", fontSize: 10, color: sc.textDim, cursor: "pointer" }}>▶ Recordings</button>
                {cam.hasPtz && <button style={{ flex: 1, background: sc.surfaceAlt, border: `1px solid ${sc.accent}30`, borderRadius: 6, padding: "5px 0", fontSize: 10, color: sc.accent, cursor: "pointer" }}>⊕ PTZ</button>}
                <button style={{ flex: 1, background: sc.surfaceAlt, border: `1px solid ${sc.info}30`, borderRadius: 6, padding: "5px 0", fontSize: 10, color: sc.info, cursor: "pointer" }}>▣ Zones</button>
              </div>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

// ═══ SCREEN 2: PTZ Camera Control ═══
function PtzControl() {
  const [zoom, setZoom] = useState(30);
  const presets = ["Front Gate", "Pond View", "Storage", "Driveway"];
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 16 }}>
        <Btn style={{ padding: "6px 10px" }}>← Back</Btn>
        <div><div style={{ fontSize: 18, fontWeight: 600, color: sc.text }}>cam-01: Front Gate — PTZ Control</div><div style={{ fontSize: 12, color: sc.textDim }}>VIGI C540-W · Pan 360° · Tilt 115° · Zoom 4×</div></div>
        <Badge color={sc.success}>Online</Badge>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 260px", gap: 16 }}>
        <Card style={{ overflow: "hidden" }}>
          <div style={{ background: "#000", aspectRatio: "16/9", position: "relative", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <div style={{ width: "100%", height: "100%", background: "linear-gradient(135deg, #0a1020, #0f1a30, #0a1020)", display: "flex", alignItems: "center", justifyContent: "center" }}><span style={{ color: sc.textMuted }}>MJPEG Live · 3.0 FPS</span></div>
            <div style={{ position: "absolute", top: "50%", left: "50%", transform: "translate(-50%,-50%)", width: 40, height: 40 }}>
              <div style={{ position: "absolute", top: 0, left: "50%", width: 1, height: "100%", background: `${sc.accent}40` }} />
              <div style={{ position: "absolute", top: "50%", left: 0, width: "100%", height: 1, background: `${sc.accent}40` }} />
            </div>
            <div style={{ position: "absolute", top: 8, right: 8, display: "flex", alignItems: "center", gap: 4, background: "rgba(0,0,0,0.6)", padding: "2px 8px", borderRadius: 4 }}>
              <span style={{ width: 6, height: 6, borderRadius: "50%", background: sc.critical }} /><span style={{ fontSize: 10, color: "#fff" }}>REC</span>
            </div>
            <div style={{ position: "absolute", bottom: 8, left: "50%", transform: "translateX(-50%)", fontSize: 10, color: sc.accent, background: "rgba(0,0,0,0.5)", padding: "2px 12px", borderRadius: 4 }}>Swipe to pan · Pinch to zoom</div>
          </div>
        </Card>
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <Card style={{ padding: 14 }}>
            <div style={{ fontSize: 11, color: sc.textMuted, marginBottom: 10, fontWeight: 600, letterSpacing: 1 }}>PAN / TILT</div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 4, width: 130, margin: "0 auto" }}>
              <div /><Btn style={{ padding: "10px 0", fontSize: 14, textAlign: "center" }}>▲</Btn><div />
              <Btn style={{ padding: "10px 0", fontSize: 14, textAlign: "center" }}>◀</Btn>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "center" }}><div style={{ width: 10, height: 10, borderRadius: "50%", background: sc.accent }} /></div>
              <Btn style={{ padding: "10px 0", fontSize: 14, textAlign: "center" }}>▶</Btn>
              <div /><Btn style={{ padding: "10px 0", fontSize: 14, textAlign: "center" }}>▼</Btn><div />
            </div>
          </Card>
          <Card style={{ padding: 14 }}>
            <div style={{ fontSize: 11, color: sc.textMuted, marginBottom: 8, fontWeight: 600, letterSpacing: 1 }}>ZOOM</div>
            <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
              <Btn style={{ padding: "4px 8px", fontSize: 14 }}>−</Btn>
              <input type="range" min="0" max="100" value={zoom} onChange={e=>setZoom(+e.target.value)} style={{ flex: 1, accentColor: sc.accent }} />
              <Btn style={{ padding: "4px 8px", fontSize: 14 }}>+</Btn>
            </div>
            <div style={{ textAlign: "center", fontSize: 11, color: sc.textDim, marginTop: 4 }}>{(1+zoom*3/100).toFixed(1)}×</div>
          </Card>
          <Card style={{ padding: 14 }}>
            <div style={{ fontSize: 11, color: sc.textMuted, marginBottom: 8, fontWeight: 600, letterSpacing: 1 }}>PRESETS</div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 4 }}>
              {presets.map(p => <Btn key={p} style={{ fontSize: 10, padding: "5px 6px" }}>{p}</Btn>)}
            </div>
            <Btn variant="primary" style={{ width: "100%", marginTop: 8, fontSize: 10 }}>+ Save Position</Btn>
          </Card>
          <Card style={{ padding: 10 }}>
            <div style={{ fontSize: 10, color: sc.textMuted, lineHeight: 1.6 }}>Model: VIGI C540-W<br/>Firmware: 1.2.4 · ONVIF Profile S<br/>Pan: 360° · Tilt: 115° · Zoom: 4×</div>
          </Card>
        </div>
      </div>
    </div>
  );
}

// ═══ SCREEN 3: Zone Drawing on Live Feed ═══
function ZoneDrawer() {
  const types = ["intrusion", "zero_tolerance", "livestock_containment", "hazard"];
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 16 }}>
        <Btn style={{ padding: "6px 10px" }}>← Back</Btn>
        <div><div style={{ fontSize: 18, fontWeight: 600, color: sc.text }}>Draw Zone — cam-01</div><div style={{ fontSize: 12, color: sc.textDim }}>Click on the live feed to place polygon points</div></div>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 280px", gap: 16 }}>
        <Card style={{ overflow: "hidden" }}>
          <div style={{ background: "#000", aspectRatio: "16/9", position: "relative" }}>
            <div style={{ width: "100%", height: "100%", background: "linear-gradient(135deg, #0a1020, #0f1a30, #0a1020)" }} />
            <svg style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} viewBox="0 0 640 360">
              <polygon points="100,60 400,40 450,250 80,280" fill={`${sc.info}25`} stroke={sc.info} strokeWidth="2" strokeDasharray="6 3" />
              {[[100,60],[400,40],[450,250],[80,280]].map(([x,y],i) => (
                <circle key={i} cx={x} cy={y} r="6" fill={sc.accent} stroke="#000" strokeWidth="1.5" style={{ cursor: "move" }} />
              ))}
              <text x="260" y="160" textAnchor="middle" fill={sc.info} fontSize="14" fontWeight="600">Cow Containment</text>
            </svg>
            <div style={{ position: "absolute", bottom: 8, left: "50%", transform: "translateX(-50%)", display: "flex", gap: 6 }}>
              <Btn style={{ fontSize: 10, padding: "4px 12px" }}>Undo</Btn>
              <Btn style={{ fontSize: 10, padding: "4px 12px" }}>Clear All</Btn>
            </div>
          </div>
        </Card>
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <Card style={{ padding: 14 }}>
            <div style={{ fontSize: 11, color: sc.textMuted, marginBottom: 10, fontWeight: 600, letterSpacing: 1 }}>ZONE CONFIG</div>
            <div style={{ marginBottom: 10 }}>
              <div style={{ fontSize: 11, color: sc.textDim, marginBottom: 4 }}>Zone Name</div>
              <input defaultValue="Cow Containment Area" style={{ width: "100%", background: sc.surfaceAlt, border: `1px solid ${sc.border}`, borderRadius: 8, padding: "8px 10px", color: sc.text, fontSize: 12, outline: "none", boxSizing: "border-box" }} />
            </div>
            <div style={{ marginBottom: 10 }}>
              <div style={{ fontSize: 11, color: sc.textDim, marginBottom: 4 }}>Zone Type</div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 4 }}>
                {types.map(t => <button key={t} style={{ background: t === "livestock_containment" ? `${sc.accent}20` : sc.surfaceAlt, color: t === "livestock_containment" ? sc.accent : sc.textDim, border: `1px solid ${t === "livestock_containment" ? sc.accent+"40" : sc.border}`, borderRadius: 6, padding: "5px 4px", fontSize: 9, cursor: "pointer", textTransform: "capitalize" }}>{t.replace("_"," ")}</button>)}
              </div>
            </div>
            <div style={{ marginBottom: 10 }}>
              <div style={{ fontSize: 11, color: sc.textDim, marginBottom: 4 }}>Priority</div>
              <div style={{ display: "flex", gap: 4 }}>
                {[["critical",sc.critical],["high",sc.high],["warning",sc.warning]].map(([p,c]) => <button key={p} style={{ flex: 1, background: p==="warning" ? `${c}20` : sc.surfaceAlt, color: p==="warning" ? c : sc.textDim, border: `1px solid ${p==="warning" ? c+"40" : sc.border}`, borderRadius: 6, padding: "5px", fontSize: 10, cursor: "pointer", textTransform: "capitalize" }}>{p}</button>)}
              </div>
            </div>
            <div style={{ marginBottom: 10 }}>
              <div style={{ fontSize: 11, color: sc.textDim, marginBottom: 4 }}>Target Classes</div>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
                {["person","child","cow","snake","scorpion","fire","smoke","dog"].map(c => <span key={c} style={{ padding: "3px 8px", borderRadius: 6, fontSize: 10, background: ["cow","person"].includes(c) ? `${sc.success}20` : sc.surfaceAlt, color: ["cow","person"].includes(c) ? sc.success : sc.textMuted, border: `1px solid ${["cow","person"].includes(c) ? sc.success+"40" : sc.border}`, cursor: "pointer" }}>{c}</span>)}
              </div>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 12 }}>
              <input type="checkbox" defaultChecked /><span style={{ fontSize: 11, color: sc.textDim }}>Suppress with worker tag</span>
            </div>
          </Card>
          <Btn variant="primary" style={{ width: "100%" }}>Save Zone</Btn>
          <Btn variant="danger" style={{ width: "100%", fontSize: 12 }}>Cancel</Btn>
        </div>
      </div>
    </div>
  );
}

// ═══ SCREEN 4: Video Recordings Player ═══
function VideoPlayer() {
  const segments = ["00:00","00:10","00:20","00:30","00:40","00:50","01:00","01:10","01:20","01:30","01:40","01:50"];
  const alerts = [{ time: "00:22", label: "snake", color: sc.critical }, { time: "01:15", label: "person", color: sc.high }];
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 16 }}>
        <Btn style={{ padding: "6px 10px" }}>← Back</Btn>
        <div><div style={{ fontSize: 18, fontWeight: 600, color: sc.text }}>cam-01: Front Gate — Recordings</div><div style={{ fontSize: 12, color: sc.textDim }}>2026-03-22 · 144 segments · 3.4 GB</div></div>
        <div style={{ marginLeft: "auto", display: "flex", gap: 6 }}>
          <Btn style={{ fontSize: 11 }}>← Prev Day</Btn>
          <Btn style={{ fontSize: 11, background: sc.surfaceAlt, color: sc.accent, border: `1px solid ${sc.accent}30` }}>Mar 22</Btn>
          <Btn style={{ fontSize: 11 }}>Next Day →</Btn>
        </div>
      </div>
      <Card style={{ overflow: "hidden", marginBottom: 12 }}>
        <div style={{ background: "#000", aspectRatio: "16/9", position: "relative", display: "flex", alignItems: "center", justifyContent: "center" }}>
          <div style={{ color: sc.textMuted, textAlign: "center" }}>
            <div style={{ fontSize: 40, marginBottom: 8 }}>▶</div>
            <div style={{ fontSize: 13 }}>HTML5 &lt;video&gt; MP4 Player</div>
            <div style={{ fontSize: 11, color: sc.textMuted }}>seek · pause · fullscreen · speed</div>
          </div>
          <div style={{ position: "absolute", bottom: 8, left: 8, right: 8, display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ fontSize: 11, color: "#fff" }}>02:15</span>
            <div style={{ flex: 1, height: 4, background: "#333", borderRadius: 2, position: "relative" }}>
              <div style={{ width: "22%", height: "100%", background: sc.accent, borderRadius: 2 }} />
              {alerts.map((a,i) => <div key={i} style={{ position: "absolute", top: -3, left: a.time === "00:22" ? "3.7%" : "12.5%", width: 8, height: 8, borderRadius: "50%", background: a.color, border: "1px solid #000" }} title={a.label} />)}
            </div>
            <span style={{ fontSize: 11, color: "#fff" }}>10:00</span>
          </div>
        </div>
      </Card>
      {/* Timeline with alert markers */}
      <Card style={{ padding: 12, marginBottom: 12 }}>
        <div style={{ fontSize: 11, color: sc.textMuted, marginBottom: 8, fontWeight: 600, letterSpacing: 1 }}>24-HOUR TIMELINE</div>
        <div style={{ display: "flex", height: 28, background: sc.surfaceAlt, borderRadius: 6, overflow: "hidden", position: "relative" }}>
          {Array.from({length: 24}, (_,h) => (
            <div key={h} style={{ flex: 1, borderRight: `1px solid ${sc.border}`, display: "flex", alignItems: "flex-end", justifyContent: "center", paddingBottom: 2 }}>
              <span style={{ fontSize: 8, color: sc.textMuted }}>{h}</span>
            </div>
          ))}
          <div style={{ position: "absolute", left: "1%", top: 2, width: 6, height: 6, borderRadius: "50%", background: sc.critical }} title="snake 00:22" />
          <div style={{ position: "absolute", left: "5.2%", top: 2, width: 6, height: 6, borderRadius: "50%", background: sc.high }} title="person 01:15" />
          <div style={{ position: "absolute", left: "3.3%", top: 0, bottom: 0, width: 2, background: sc.accent }} title="Current" />
        </div>
      </Card>
      {/* Segment buttons */}
      <Card style={{ padding: 12 }}>
        <div style={{ fontSize: 11, color: sc.textMuted, marginBottom: 8, fontWeight: 600, letterSpacing: 1 }}>SEGMENTS (Hour 00)</div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
          {segments.map((s,i) => <button key={s} style={{ background: i===2 ? `${sc.accent}20` : sc.surfaceAlt, color: i===2 ? sc.accent : sc.textDim, border: `1px solid ${i===2 ? sc.accent+"40" : sc.border}`, borderRadius: 6, padding: "6px 12px", fontSize: 11, cursor: "pointer" }}>{s}</button>)}
        </div>
      </Card>
      {/* Storage info */}
      <div style={{ display: "flex", gap: 12, marginTop: 12 }}>
        <Card style={{ flex: 1, padding: 12 }}>
          <div style={{ fontSize: 10, color: sc.textMuted, marginBottom: 4 }}>SSD Storage</div>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <div style={{ flex: 1, height: 6, background: sc.surfaceAlt, borderRadius: 3 }}><div style={{ width: "42%", height: "100%", background: sc.success, borderRadius: 3 }} /></div>
            <span style={{ fontSize: 11, color: sc.textDim }}>42%</span>
          </div>
          <div style={{ fontSize: 10, color: sc.textMuted, marginTop: 4 }}>120 GB / 288 GB · 8 days remaining</div>
        </Card>
        <Card style={{ flex: 1, padding: 12 }}>
          <div style={{ fontSize: 10, color: sc.textMuted, marginBottom: 4 }}>Archive (External HDD)</div>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <div style={{ flex: 1, height: 6, background: sc.surfaceAlt, borderRadius: 3 }}><div style={{ width: "15%", height: "100%", background: sc.info, borderRadius: 3 }} /></div>
            <span style={{ fontSize: 11, color: sc.textDim }}>15%</span>
          </div>
          <div style={{ fontSize: 10, color: sc.textMuted, marginTop: 4 }}>600 GB / 4 TB · 22 days archived</div>
        </Card>
      </div>
    </div>
  );
}

// ═══ SCREEN 5: Alert Detail with Video Clip ═══
function AlertWithClip() {
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 16 }}>
        <Btn style={{ padding: "6px 10px" }}>← Back</Btn>
        <div style={{ flex: 1 }}><div style={{ fontSize: 18, fontWeight: 600, color: sc.critical }}>CRITICAL: Snake detected at Storage Shed</div><div style={{ fontSize: 12, color: sc.textDim }}>cam-05 · 2026-03-22 02:15:30 · Confidence: 78%</div></div>
        <Badge color={sc.critical}>Critical</Badge>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 12 }}>
        <Card style={{ overflow: "hidden" }}>
          <div style={{ padding: "8px 12px", borderBottom: `1px solid ${sc.border}`, fontSize: 11, color: sc.textMuted, fontWeight: 600 }}>SNAPSHOT</div>
          <div style={{ background: "#000", aspectRatio: "16/9", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <div style={{ border: `2px solid ${sc.critical}`, padding: 20, borderRadius: 4 }}><span style={{ color: sc.critical, fontSize: 13 }}>snake · 78%</span></div>
          </div>
        </Card>
        <Card style={{ overflow: "hidden" }}>
          <div style={{ padding: "8px 12px", borderBottom: `1px solid ${sc.border}`, fontSize: 11, color: sc.accent, fontWeight: 600 }}>VIDEO CLIP (30 seconds)</div>
          <div style={{ background: "#000", aspectRatio: "16/9", display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 6 }}>
            <div style={{ fontSize: 32, color: sc.accent }}>▶</div>
            <span style={{ color: sc.textDim, fontSize: 11 }}>15s before → alert → 15s after</span>
            <span style={{ color: sc.textMuted, fontSize: 10 }}>cam-05_02-10.mp4 @ offset 5:30</span>
          </div>
        </Card>
      </div>
      <div style={{ display: "flex", gap: 8 }}>
        <Btn variant="success">Acknowledge</Btn>
        <Btn>Mark False Positive</Btn>
        <Btn variant="danger">Trigger Siren</Btn>
        <Btn style={{ marginLeft: "auto" }}>View Full Recording →</Btn>
      </div>
    </div>
  );
}

// ═══ SCREEN 6: Add Camera / Video Source ═══
function AddCamera() {
  const [sourceType, setSourceType] = useState("rtsp");
  return (
    <div>
      <div style={{ fontSize: 18, fontWeight: 600, color: sc.text, marginBottom: 4 }}>Add Camera / Video Source</div>
      <div style={{ fontSize: 12, color: sc.textDim, marginBottom: 20 }}>Register an RTSP camera, video file, or HTTP stream</div>
      <Card style={{ padding: 20 }}>
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 11, color: sc.textMuted, marginBottom: 6, fontWeight: 600 }}>SOURCE TYPE</div>
          <div style={{ display: "flex", gap: 6 }}>
            {[["rtsp","RTSP Camera"],["file","Video File (MP4)"],["http","HTTP Stream"]].map(([k,l]) => (
              <button key={k} onClick={()=>setSourceType(k)} style={{ flex: 1, padding: "10px", borderRadius: 8, fontSize: 12, cursor: "pointer", background: sourceType===k ? `${sc.accent}15` : sc.surfaceAlt, color: sourceType===k ? sc.accent : sc.textDim, border: `1px solid ${sourceType===k ? sc.accent+"40" : sc.border}` }}>{l}</button>
            ))}
          </div>
        </div>
        {sourceType === "rtsp" && (
          <div style={{ display: "grid", gap: 12 }}>
            <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr", gap: 8 }}>
              {[["IP Address","192.168.1.201"],["Username","farmadmin"],["Password","••••••"]].map(([l,p]) => (
                <div key={l}><div style={{ fontSize: 11, color: sc.textDim, marginBottom: 4 }}>{l}</div><input placeholder={p} type={l==="Password"?"password":"text"} style={{ width: "100%", background: sc.bg, border: `1px solid ${sc.border}`, borderRadius: 8, padding: "8px 10px", color: sc.text, fontSize: 12, outline: "none", boxSizing: "border-box" }} /></div>
              ))}
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
              <div><div style={{ fontSize: 11, color: sc.textDim, marginBottom: 4 }}>Stream</div>
                <select style={{ width: "100%", background: sc.bg, border: `1px solid ${sc.border}`, borderRadius: 8, padding: "8px 10px", color: sc.text, fontSize: 12 }}>
                  <option>stream2 (640×480, AI inference)</option><option>stream1 (1080p/2K, recording)</option>
                </select>
              </div>
              <div><div style={{ fontSize: 11, color: sc.textDim, marginBottom: 4 }}>FPS Target</div><input defaultValue="2.5" type="number" style={{ width: "100%", background: sc.bg, border: `1px solid ${sc.border}`, borderRadius: 8, padding: "8px 10px", color: sc.text, fontSize: 12, outline: "none", boxSizing: "border-box" }} /></div>
            </div>
            <Btn style={{ background: `${sc.info}15`, color: sc.info, border: `1px solid ${sc.info}40` }}>Test Connection</Btn>
            <div style={{ display: "flex", alignItems: "center", gap: 8, padding: 10, background: `${sc.success}10`, border: `1px solid ${sc.success}30`, borderRadius: 8 }}>
              <span style={{ width: 8, height: 8, borderRadius: "50%", background: sc.success }} />
              <span style={{ fontSize: 12, color: sc.success }}>Connected! PTZ: Yes (Pan 360°, Tilt 115°, Zoom 4×)</span>
            </div>
          </div>
        )}
        {sourceType === "file" && (
          <div style={{ display: "grid", gap: 12 }}>
            <div style={{ border: `2px dashed ${sc.border}`, borderRadius: 10, padding: 30, textAlign: "center" }}>
              <div style={{ fontSize: 24, color: sc.textMuted, marginBottom: 8 }}>📁</div>
              <div style={{ fontSize: 13, color: sc.textDim }}>Drag & drop MP4/AVI file or click to browse</div>
              <div style={{ fontSize: 11, color: sc.textMuted, marginTop: 4 }}>Selected: incident_2026-03-20.mp4 (245 MB)</div>
            </div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <input type="checkbox" /><span style={{ fontSize: 12, color: sc.textDim }}>Loop video</span>
              <div style={{ marginLeft: "auto" }}><span style={{ fontSize: 11, color: sc.textDim }}>FPS: </span><input defaultValue="5" type="number" style={{ width: 50, background: sc.bg, border: `1px solid ${sc.border}`, borderRadius: 6, padding: "4px 6px", color: sc.text, fontSize: 12, textAlign: "center" }} /></div>
            </div>
          </div>
        )}
        {sourceType === "http" && (
          <div><div style={{ fontSize: 11, color: sc.textDim, marginBottom: 4 }}>Stream URL</div><input placeholder="http://192.168.1.50/live/stream.mjpeg" style={{ width: "100%", background: sc.bg, border: `1px solid ${sc.border}`, borderRadius: 8, padding: "8px 10px", color: sc.text, fontSize: 12, outline: "none", boxSizing: "border-box" }} /></div>
        )}
        <div style={{ borderTop: `1px solid ${sc.border}`, marginTop: 16, paddingTop: 16, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
          <div><div style={{ fontSize: 11, color: sc.textDim, marginBottom: 4 }}>Camera Name</div><input placeholder="Front Gate Camera" style={{ width: "100%", background: sc.bg, border: `1px solid ${sc.border}`, borderRadius: 8, padding: "8px 10px", color: sc.text, fontSize: 12, outline: "none", boxSizing: "border-box" }} /></div>
          <div><div style={{ fontSize: 11, color: sc.textDim, marginBottom: 4 }}>Assign to Node</div>
            <select style={{ width: "100%", background: sc.bg, border: `1px solid ${sc.border}`, borderRadius: 8, padding: "8px 10px", color: sc.text, fontSize: 12 }}>
              <option>Edge Node A (10.8.0.10)</option><option>Edge Node B (10.8.0.11)</option>
            </select>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 6, marginTop: 12 }}>
          <input type="checkbox" defaultChecked /><span style={{ fontSize: 12, color: sc.textDim }}>Enable video recording</span>
        </div>
        <Btn variant="primary" style={{ width: "100%", marginTop: 16 }}>Register Camera</Btn>
      </Card>
    </div>
  );
}

// ═══ SCREEN 7: Android — Camera + PTZ + Zone (Phone Frames) ═══
function AndroidMockups() {
  const Phone = ({ title, children }) => (
    <div style={{ width: 200, background: "#000", borderRadius: 24, padding: "6px 5px", boxShadow: "0 4px 20px rgba(0,0,0,0.5)" }}>
      <div style={{ background: "#F8F7F4", borderRadius: 20, overflow: "hidden", minHeight: 380 }}>
        <div style={{ padding: "10px 12px", borderBottom: "1px solid #E8E5E0" }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: "#2D2A26" }}>{title}</div>
        </div>
        <div style={{ padding: 8 }}>{children}</div>
      </div>
    </div>
  );
  const mc = { bg: "#F8F7F4", card: "#fff", text: "#2D2A26", dim: "#6B6560", muted: "#9E9891", accent: "#C8553D", green: "#4A8C5C", blue: "#3A8ACC", border: "#E8E5E0" };

  return (
    <div>
      <div style={{ fontSize: 16, fontWeight: 600, color: sc.text, marginBottom: 4 }}>Android App — Camera Screens</div>
      <div style={{ fontSize: 12, color: sc.textDim, marginBottom: 16 }}>Terracotta/Cream palette · Material 3 · Jetpack Compose</div>
      <div style={{ display: "flex", gap: 14, overflowX: "auto", paddingBottom: 8 }}>
        <Phone title="Live Camera">
          <div style={{ background: "#000", borderRadius: 10, aspectRatio: "16/9", position: "relative", marginBottom: 8, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <span style={{ color: "#666", fontSize: 10 }}>Live JPEG Feed</span>
            <div style={{ position: "absolute", top: 4, right: 4, fontSize: 8, background: "rgba(0,0,0,0.5)", color: mc.accent, padding: "1px 6px", borderRadius: 4 }}>PTZ</div>
          </div>
          {[["Recordings","▶ Browse MP4 segments"],["PTZ Control","⊕ Pan / Tilt / Zoom"],["Draw Zone","▣ Tap to place polygon"]].map(([t,d]) => (
            <div key={t} style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 10, padding: "8px 10px", marginBottom: 6 }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: mc.text }}>{t}</div>
              <div style={{ fontSize: 9, color: mc.dim }}>{d}</div>
            </div>
          ))}
        </Phone>

        <Phone title="PTZ Control">
          <div style={{ background: "#000", borderRadius: 10, aspectRatio: "16/9", marginBottom: 8, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <span style={{ color: "#666", fontSize: 9 }}>Swipe to pan · Pinch to zoom</span>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 3, width: 100, margin: "0 auto 8px" }}>
            <div /><div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 6, padding: 6, textAlign: "center", fontSize: 12 }}>▲</div><div />
            <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 6, padding: 6, textAlign: "center", fontSize: 12 }}>◀</div>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "center" }}><div style={{ width: 8, height: 8, borderRadius: "50%", background: mc.accent }} /></div>
            <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 6, padding: 6, textAlign: "center", fontSize: 12 }}>▶</div>
            <div /><div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 6, padding: 6, textAlign: "center", fontSize: 12 }}>▼</div><div />
          </div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 3 }}>
            {["Gate","Pond","Shed","Save +"].map(p => <span key={p} style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 6, padding: "4px 8px", fontSize: 9, color: mc.text }}>{p}</span>)}
          </div>
        </Phone>

        <Phone title="Draw Zone (Touch)">
          <div style={{ background: "#000", borderRadius: 10, aspectRatio: "16/9", marginBottom: 8, position: "relative" }}>
            <svg style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} viewBox="0 0 320 180">
              <polygon points="40,30 200,20 220,140 30,150" fill="rgba(59,130,246,0.2)" stroke="#3b82f6" strokeWidth="1.5" />
              {[[40,30],[200,20],[220,140],[30,150]].map(([x,y],i) => <circle key={i} cx={x} cy={y} r="5" fill="#f59e0b" stroke="#000" />)}
            </svg>
          </div>
          <div style={{ fontSize: 9, color: mc.dim, marginBottom: 6, textAlign: "center" }}>Tap to add points · Drag to adjust</div>
          {[["Zone Name","Cow Area"],["Type","Containment"]].map(([l,v]) => (
            <div key={l} style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: `1px solid ${mc.border}` }}>
              <span style={{ fontSize: 10, color: mc.dim }}>{l}</span>
              <span style={{ fontSize: 10, color: mc.text, fontWeight: 500 }}>{v}</span>
            </div>
          ))}
          <div style={{ background: mc.accent, color: "#fff", borderRadius: 8, padding: "8px 0", textAlign: "center", fontSize: 11, fontWeight: 600, marginTop: 8 }}>Save Zone</div>
        </Phone>

        <Phone title="Alert + Video Clip">
          <div style={{ background: "#fff", border: `2px solid ${mc.accent}`, borderRadius: 10, padding: 8, marginBottom: 8 }}>
            <div style={{ fontSize: 10, fontWeight: 600, color: mc.accent }}>CRITICAL: Snake at Storage</div>
            <div style={{ fontSize: 9, color: mc.dim }}>cam-05 · 02:15 · 78%</div>
          </div>
          <div style={{ background: "#000", borderRadius: 10, aspectRatio: "16/9", display: "flex", alignItems: "center", justifyContent: "center", marginBottom: 8 }}>
            <div style={{ textAlign: "center" }}>
              <div style={{ fontSize: 24, color: "#f59e0b" }}>▶</div>
              <div style={{ fontSize: 9, color: "#666" }}>30s clip · ExoPlayer</div>
            </div>
          </div>
          <div style={{ display: "flex", gap: 4 }}>
            <div style={{ flex: 1, background: mc.green, color: "#fff", borderRadius: 6, padding: "6px 0", textAlign: "center", fontSize: 9, fontWeight: 600 }}>Acknowledge</div>
            <div style={{ flex: 1, background: mc.accent, color: "#fff", borderRadius: 6, padding: "6px 0", textAlign: "center", fontSize: 9, fontWeight: 600 }}>Siren</div>
          </div>
        </Phone>
      </div>
    </div>
  );
}

// ═══ MAIN APP ═══
export default function CameraVideoMockups() {
  const [screen, setScreen] = useState("grid");
  const screens = [
    { id: "grid", label: "Camera Grid" },
    { id: "ptz", label: "PTZ Control" },
    { id: "zones", label: "Zone Drawing" },
    { id: "player", label: "Video Player" },
    { id: "alert_clip", label: "Alert + Clip" },
    { id: "add_camera", label: "Add Camera" },
    { id: "android", label: "Android Screens" },
  ];
  return (
    <div style={{ minHeight: "100vh", background: sc.bg, color: sc.text, fontFamily: "'DM Sans', system-ui, sans-serif", padding: 0 }}>
      <link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&display=swap" rel="stylesheet" />
      <div style={{ borderBottom: `1px solid ${sc.border}`, padding: "12px 20px", display: "flex", alignItems: "center", gap: 10 }}>
        <div style={{ width: 28, height: 28, borderRadius: 6, background: `linear-gradient(135deg, ${sc.critical}, ${sc.accent})`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, color: "#fff" }}>SC</div>
        <span style={{ fontSize: 14, fontWeight: 600, letterSpacing: 0.5 }}>Camera, Video & Remote Control — Mockup Screens</span>
      </div>
      <div style={{ display: "flex", gap: 4, padding: "10px 20px", overflowX: "auto", borderBottom: `1px solid ${sc.border}` }}>
        {screens.map(s => (
          <button key={s.id} onClick={() => setScreen(s.id)} style={{ background: screen === s.id ? sc.surfaceAlt : "transparent", color: screen === s.id ? sc.text : sc.textMuted, border: `1px solid ${screen === s.id ? sc.border : "transparent"}`, borderRadius: 8, padding: "6px 14px", fontSize: 12, fontWeight: 500, cursor: "pointer", whiteSpace: "nowrap" }}>{s.label}</button>
        ))}
      </div>
      <div style={{ padding: 20, maxWidth: 960, margin: "0 auto" }}>
        {screen === "grid" && <CameraGrid />}
        {screen === "ptz" && <PtzControl />}
        {screen === "zones" && <ZoneDrawer />}
        {screen === "player" && <VideoPlayer />}
        {screen === "alert_clip" && <AlertWithClip />}
        {screen === "add_camera" && <AddCamera />}
        {screen === "android" && <AndroidMockups />}
      </div>
      <style>{`@keyframes pulse { 0%,100% { opacity:1 } 50% { opacity:0.3 } }`}</style>
    </div>
  );
}
