import { useState } from "react";

const COLORS = {
  bg: "#0a0e17",
  surface: "#111827",
  surfaceAlt: "#1a2235",
  border: "#1e293b",
  accent: "#f59e0b",
  accentDim: "#92400e",
  critical: "#ef4444",
  high: "#f97316",
  warning: "#eab308",
  success: "#22c55e",
  info: "#3b82f6",
  text: "#f1f5f9",
  textDim: "#94a3b8",
  textMuted: "#64748b",
};

const sidebarItems = [
  { icon: "⬡", label: "Dashboard", id: "dashboard" },
  { icon: "⚠", label: "Alerts", id: "alerts", badge: 7 },
  { icon: "◎", label: "Cameras", id: "cameras" },
  { icon: "⬢", label: "Zones", id: "zones" },
  { icon: "◈", label: "Devices", id: "devices" },
  { icon: "◉", label: "Siren", id: "siren" },
  { icon: "◐", label: "Workers", id: "workers" },
  { icon: "▤", label: "Analytics", id: "analytics" },
  { icon: "⚙", label: "Settings", id: "settings" },
];

const mockAlerts = [
  { id: "a1", priority: "critical", zone: "Pond Danger Zone", class: "child", confidence: 0.92, camera: "CAM-03", time: "2 min ago", status: "new", node: "Node A" },
  { id: "a2", priority: "critical", zone: "Pond Danger Zone", class: "person", confidence: 0.88, camera: "CAM-03", time: "2 min ago", status: "new", node: "Node A" },
  { id: "a3", priority: "high", zone: "Farm Perimeter", class: "person", confidence: 0.85, camera: "CAM-01", time: "8 min ago", status: "new", node: "Node A" },
  { id: "a4", priority: "high", zone: "Snake Zone Alpha", class: "snake", confidence: 0.79, camera: "CAM-05", time: "14 min ago", status: "acknowledged", node: "Node B" },
  { id: "a5", priority: "warning", zone: "Pasture Area", class: "cow", confidence: 0.91, camera: "CAM-07", time: "22 min ago", status: "acknowledged", node: "Node B" },
  { id: "a6", priority: "high", zone: "Storage Shed", class: "fire", confidence: 0.73, camera: "CAM-02", time: "1 hr ago", status: "resolved", node: "Node A" },
  { id: "a7", priority: "warning", zone: "Farm Perimeter", class: "scorpion", confidence: 0.67, camera: "CAM-04", time: "2 hr ago", status: "false_positive", node: "Node B" },
];

const priorityColor = (p) => p === "critical" ? COLORS.critical : p === "high" ? COLORS.high : COLORS.warning;
const statusColor = (s) => s === "new" ? COLORS.critical : s === "acknowledged" ? COLORS.accent : s === "resolved" ? COLORS.success : COLORS.textMuted;

function DashboardPage() {
  const stats = [
    { label: "Active Alerts", value: "7", color: COLORS.critical, sub: "2 critical" },
    { label: "Cameras Online", value: "8/8", color: COLORS.success, sub: "All operational" },
    { label: "Detections Today", value: "143", color: COLORS.info, sub: "23 escalated" },
    { label: "Workers On-Site", value: "3", color: COLORS.accent, sub: "Tags active" },
  ];

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16, marginBottom: 24 }}>
        {stats.map((s, i) => (
          <div key={i} style={{
            background: COLORS.surface, border: `1px solid ${COLORS.border}`,
            borderRadius: 12, padding: "20px 24px", borderTop: `3px solid ${s.color}`
          }}>
            <div style={{ color: COLORS.textDim, fontSize: 12, textTransform: "uppercase", letterSpacing: 1.5, fontFamily: "'JetBrains Mono', monospace" }}>{s.label}</div>
            <div style={{ color: s.color, fontSize: 36, fontWeight: 800, margin: "4px 0", fontFamily: "'JetBrains Mono', monospace" }}>{s.value}</div>
            <div style={{ color: COLORS.textMuted, fontSize: 12 }}>{s.sub}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 16 }}>
        <div style={{ background: COLORS.surface, border: `1px solid ${COLORS.border}`, borderRadius: 12, padding: 20 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
            <h3 style={{ color: COLORS.text, margin: 0, fontSize: 16 }}>Live Alert Feed</h3>
            <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
              <div style={{ width: 8, height: 8, borderRadius: "50%", background: COLORS.critical, animation: "pulse 2s infinite" }} />
              <span style={{ color: COLORS.textDim, fontSize: 12, fontFamily: "'JetBrains Mono', monospace" }}>LIVE</span>
            </div>
          </div>
          {mockAlerts.slice(0, 5).map((a) => (
            <div key={a.id} style={{
              display: "flex", alignItems: "center", gap: 12, padding: "12px 16px",
              background: a.status === "new" ? `${priorityColor(a.priority)}11` : "transparent",
              borderRadius: 8, marginBottom: 6, border: `1px solid ${a.status === "new" ? `${priorityColor(a.priority)}33` : COLORS.border}`,
              cursor: "pointer", transition: "background 0.2s"
            }}>
              <div style={{
                width: 10, height: 10, borderRadius: "50%",
                background: priorityColor(a.priority),
                boxShadow: a.status === "new" ? `0 0 8px ${priorityColor(a.priority)}88` : "none"
              }} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ color: COLORS.text, fontSize: 14, fontWeight: 600 }}>
                  {a.class.toUpperCase()} detected — {a.zone}
                </div>
                <div style={{ color: COLORS.textMuted, fontSize: 12, fontFamily: "'JetBrains Mono', monospace" }}>
                  {a.camera} · {a.node} · {(a.confidence * 100).toFixed(0)}% · {a.time}
                </div>
              </div>
              <div style={{
                padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 600,
                textTransform: "uppercase", letterSpacing: 0.5, fontFamily: "'JetBrains Mono', monospace",
                background: `${statusColor(a.status)}22`, color: statusColor(a.status),
                border: `1px solid ${statusColor(a.status)}44`,
              }}>{a.status}</div>
            </div>
          ))}
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <div style={{ background: COLORS.surface, border: `1px solid ${COLORS.border}`, borderRadius: 12, padding: 20 }}>
            <h3 style={{ color: COLORS.text, margin: "0 0 16px", fontSize: 16 }}>Node Status</h3>
            {[
              { name: "Edge Node A", ip: "10.8.0.10", status: "online", gpu: "34%", alerts: 4 },
              { name: "Edge Node B", ip: "10.8.0.11", status: "online", gpu: "28%", alerts: 3 },
            ].map((n, i) => (
              <div key={i} style={{
                padding: 14, background: COLORS.surfaceAlt, borderRadius: 8, marginBottom: 8,
                border: `1px solid ${COLORS.border}`
              }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <div style={{ width: 8, height: 8, borderRadius: "50%", background: COLORS.success }} />
                    <span style={{ color: COLORS.text, fontSize: 14, fontWeight: 600 }}>{n.name}</span>
                  </div>
                  <span style={{ color: COLORS.textMuted, fontSize: 11, fontFamily: "'JetBrains Mono', monospace" }}>{n.ip}</span>
                </div>
                <div style={{ display: "flex", gap: 16, marginTop: 8 }}>
                  <span style={{ color: COLORS.textDim, fontSize: 12 }}>GPU: <span style={{ color: COLORS.accent }}>{n.gpu}</span></span>
                  <span style={{ color: COLORS.textDim, fontSize: 12 }}>Alerts: <span style={{ color: COLORS.high }}>{n.alerts}</span></span>
                </div>
              </div>
            ))}
          </div>

          <div style={{ background: COLORS.surface, border: `1px solid ${COLORS.border}`, borderRadius: 12, padding: 20 }}>
            <h3 style={{ color: COLORS.text, margin: "0 0 16px", fontSize: 16 }}>Quick Siren Control</h3>
            <button style={{
              width: "100%", padding: "14px", borderRadius: 8, border: `2px solid ${COLORS.critical}`,
              background: `${COLORS.critical}22`, color: COLORS.critical, fontSize: 16, fontWeight: 700,
              cursor: "pointer", textTransform: "uppercase", letterSpacing: 2,
              fontFamily: "'JetBrains Mono', monospace",
            }}>
              ◉ TRIGGER SIREN — ALL NODES
            </button>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginTop: 8 }}>
              <button style={{
                padding: 10, borderRadius: 6, border: `1px solid ${COLORS.high}44`,
                background: `${COLORS.high}11`, color: COLORS.high, fontSize: 12,
                cursor: "pointer", fontFamily: "'JetBrains Mono', monospace",
              }}>Node A Siren</button>
              <button style={{
                padding: 10, borderRadius: 6, border: `1px solid ${COLORS.high}44`,
                background: `${COLORS.high}11`, color: COLORS.high, fontSize: 12,
                cursor: "pointer", fontFamily: "'JetBrains Mono', monospace",
              }}>Node B Siren</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function AlertsPage() {
  const [filter, setFilter] = useState("all");
  const filtered = filter === "all" ? mockAlerts : mockAlerts.filter(a => a.priority === filter);

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 20 }}>
        {["all", "critical", "high", "warning"].map(f => (
          <button key={f} onClick={() => setFilter(f)} style={{
            padding: "8px 20px", borderRadius: 20, border: `1px solid ${filter === f ? COLORS.accent : COLORS.border}`,
            background: filter === f ? `${COLORS.accent}22` : "transparent",
            color: filter === f ? COLORS.accent : COLORS.textDim,
            cursor: "pointer", fontSize: 13, fontFamily: "'JetBrains Mono', monospace",
            textTransform: "uppercase", letterSpacing: 1,
          }}>{f} {f !== "all" && `(${mockAlerts.filter(a => a.priority === f).length})`}</button>
        ))}
      </div>

      <div style={{ background: COLORS.surface, border: `1px solid ${COLORS.border}`, borderRadius: 12, overflow: "hidden" }}>
        <div style={{
          display: "grid", gridTemplateColumns: "40px 100px 1fr 120px 80px 100px 100px 120px",
          padding: "12px 16px", background: COLORS.surfaceAlt, borderBottom: `1px solid ${COLORS.border}`,
          color: COLORS.textMuted, fontSize: 11, textTransform: "uppercase", letterSpacing: 1.5,
          fontFamily: "'JetBrains Mono', monospace",
        }}>
          <div></div><div>Priority</div><div>Detection</div><div>Camera</div><div>Conf</div><div>Status</div><div>Time</div><div>Actions</div>
        </div>

        {filtered.map(a => (
          <div key={a.id} style={{
            display: "grid", gridTemplateColumns: "40px 100px 1fr 120px 80px 100px 100px 120px",
            padding: "14px 16px", borderBottom: `1px solid ${COLORS.border}`,
            alignItems: "center",
            background: a.status === "new" ? `${priorityColor(a.priority)}08` : "transparent",
          }}>
            <div><div style={{ width: 10, height: 10, borderRadius: "50%", background: priorityColor(a.priority), boxShadow: a.status === "new" ? `0 0 6px ${priorityColor(a.priority)}66` : "none" }} /></div>
            <div style={{ color: priorityColor(a.priority), fontSize: 12, fontWeight: 700, textTransform: "uppercase", fontFamily: "'JetBrains Mono', monospace" }}>{a.priority}</div>
            <div>
              <div style={{ color: COLORS.text, fontSize: 14, fontWeight: 600 }}>{a.class} — {a.zone}</div>
              <div style={{ color: COLORS.textMuted, fontSize: 11 }}>{a.node}</div>
            </div>
            <div style={{ color: COLORS.textDim, fontSize: 13, fontFamily: "'JetBrains Mono', monospace" }}>{a.camera}</div>
            <div style={{ color: COLORS.text, fontSize: 13, fontFamily: "'JetBrains Mono', monospace" }}>{(a.confidence * 100).toFixed(0)}%</div>
            <div style={{
              display: "inline-block", padding: "3px 10px", borderRadius: 20, fontSize: 10,
              fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5,
              background: `${statusColor(a.status)}22`, color: statusColor(a.status),
              border: `1px solid ${statusColor(a.status)}44`, fontFamily: "'JetBrains Mono', monospace",
            }}>{a.status}</div>
            <div style={{ color: COLORS.textMuted, fontSize: 12 }}>{a.time}</div>
            <div style={{ display: "flex", gap: 6 }}>
              {a.status === "new" && (
                <button style={{
                  padding: "4px 10px", borderRadius: 4, border: `1px solid ${COLORS.accent}44`,
                  background: "transparent", color: COLORS.accent, fontSize: 11, cursor: "pointer",
                  fontFamily: "'JetBrains Mono', monospace",
                }}>ACK</button>
              )}
              <button style={{
                padding: "4px 10px", borderRadius: 4, border: `1px solid ${COLORS.textMuted}44`,
                background: "transparent", color: COLORS.textDim, fontSize: 11, cursor: "pointer",
                fontFamily: "'JetBrains Mono', monospace",
              }}>View</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function CamerasPage() {
  const cameras = [
    { id: "CAM-01", name: "Front Gate", node: "A", status: "active" },
    { id: "CAM-02", name: "Storage Shed", node: "A", status: "active" },
    { id: "CAM-03", name: "Pond Area", node: "A", status: "alert" },
    { id: "CAM-04", name: "East Perimeter", node: "A", status: "active" },
    { id: "CAM-05", name: "Snake Zone", node: "B", status: "active" },
    { id: "CAM-06", name: "Cattle Pen", node: "B", status: "active" },
    { id: "CAM-07", name: "Pasture NW", node: "B", status: "active" },
    { id: "CAM-08", name: "Farmhouse", node: "B", status: "offline" },
  ];

  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12 }}>
      {cameras.map(c => (
        <div key={c.id} style={{
          background: COLORS.surface, borderRadius: 12, overflow: "hidden",
          border: `1px solid ${c.status === "alert" ? COLORS.critical + "66" : COLORS.border}`,
          cursor: "pointer",
        }}>
          <div style={{
            height: 140, background: c.status === "offline" ? COLORS.surfaceAlt : `linear-gradient(135deg, ${COLORS.surfaceAlt}, #1a2a3a)`,
            display: "flex", alignItems: "center", justifyContent: "center",
            position: "relative",
          }}>
            <span style={{ fontSize: 40, opacity: 0.3 }}>◎</span>
            {c.status === "alert" && (
              <div style={{
                position: "absolute", top: 8, right: 8, padding: "2px 8px",
                background: COLORS.critical, borderRadius: 4, fontSize: 10,
                color: "white", fontWeight: 700, fontFamily: "'JetBrains Mono', monospace",
              }}>ALERT</div>
            )}
            <div style={{
              position: "absolute", top: 8, left: 8, padding: "2px 8px",
              background: "rgba(0,0,0,0.6)", borderRadius: 4, fontSize: 10,
              color: COLORS.textDim, fontFamily: "'JetBrains Mono', monospace",
            }}>2.3 FPS</div>
          </div>
          <div style={{ padding: "12px 16px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span style={{ color: COLORS.text, fontSize: 14, fontWeight: 600 }}>{c.name}</span>
              <div style={{
                width: 8, height: 8, borderRadius: "50%",
                background: c.status === "offline" ? COLORS.textMuted : c.status === "alert" ? COLORS.critical : COLORS.success,
              }} />
            </div>
            <div style={{ color: COLORS.textMuted, fontSize: 11, fontFamily: "'JetBrains Mono', monospace", marginTop: 4 }}>
              {c.id} · Node {c.node}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

function SirenPage() {
  const [sirenActive, setSirenActive] = useState(false);
  const history = [
    { time: "Today 06:12", by: "System (Auto)", reason: "Child near pond — CAM-03", node: "A" },
    { time: "Yesterday 22:45", by: "Ravi Kumar", reason: "Intruder at perimeter — CAM-01", node: "A+B" },
    { time: "Mar 04, 14:30", by: "System (Auto)", reason: "Snake detected — CAM-05", node: "B" },
  ];

  return (
    <div>
      <div style={{
        background: sirenActive ? `${COLORS.critical}22` : COLORS.surface,
        border: `2px solid ${sirenActive ? COLORS.critical : COLORS.border}`,
        borderRadius: 16, padding: 32, textAlign: "center", marginBottom: 24,
      }}>
        <div style={{ fontSize: 64, marginBottom: 16 }}>{sirenActive ? "◉" : "○"}</div>
        <div style={{
          color: sirenActive ? COLORS.critical : COLORS.textDim,
          fontSize: 24, fontWeight: 800, textTransform: "uppercase", letterSpacing: 4,
          fontFamily: "'JetBrains Mono', monospace", marginBottom: 8,
        }}>
          {sirenActive ? "SIREN ACTIVE" : "SIREN STANDBY"}
        </div>
        <div style={{ color: COLORS.textMuted, fontSize: 13, marginBottom: 24 }}>
          {sirenActive ? "All farm sirens are currently sounding" : "Press to activate emergency siren on selected nodes"}
        </div>
        <div style={{ display: "flex", gap: 12, justifyContent: "center" }}>
          <button onClick={() => setSirenActive(!sirenActive)} style={{
            padding: "16px 48px", borderRadius: 12,
            border: `2px solid ${sirenActive ? COLORS.success : COLORS.critical}`,
            background: sirenActive ? `${COLORS.success}22` : `${COLORS.critical}22`,
            color: sirenActive ? COLORS.success : COLORS.critical,
            fontSize: 18, fontWeight: 800, cursor: "pointer",
            textTransform: "uppercase", letterSpacing: 3,
            fontFamily: "'JetBrains Mono', monospace",
          }}>
            {sirenActive ? "⬛ STOP SIREN" : "◉ TRIGGER ALL"}
          </button>
        </div>
        <div style={{ display: "flex", gap: 12, justifyContent: "center", marginTop: 16 }}>
          <button style={{
            padding: "10px 24px", borderRadius: 8, border: `1px solid ${COLORS.high}44`,
            background: `${COLORS.high}11`, color: COLORS.high, cursor: "pointer",
            fontFamily: "'JetBrains Mono', monospace", fontSize: 13,
          }}>Node A Only</button>
          <button style={{
            padding: "10px 24px", borderRadius: 8, border: `1px solid ${COLORS.high}44`,
            background: `${COLORS.high}11`, color: COLORS.high, cursor: "pointer",
            fontFamily: "'JetBrains Mono', monospace", fontSize: 13,
          }}>Node B Only</button>
        </div>
      </div>

      <div style={{ background: COLORS.surface, border: `1px solid ${COLORS.border}`, borderRadius: 12, padding: 20 }}>
        <h3 style={{ color: COLORS.text, margin: "0 0 16px", fontSize: 16 }}>Siren Activation History</h3>
        {history.map((h, i) => (
          <div key={i} style={{
            display: "flex", alignItems: "center", gap: 16, padding: "12px 16px",
            borderRadius: 8, marginBottom: 6, background: COLORS.surfaceAlt,
            border: `1px solid ${COLORS.border}`,
          }}>
            <div style={{ color: COLORS.critical, fontSize: 20 }}>◉</div>
            <div style={{ flex: 1 }}>
              <div style={{ color: COLORS.text, fontSize: 14 }}>{h.reason}</div>
              <div style={{ color: COLORS.textMuted, fontSize: 12, fontFamily: "'JetBrains Mono', monospace" }}>
                {h.time} · By: {h.by} · Node: {h.node}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

const pages = { dashboard: DashboardPage, alerts: AlertsPage, cameras: CamerasPage, siren: SirenPage };
const pageTitles = { dashboard: "Dashboard", alerts: "Alert Management", cameras: "Camera Grid", siren: "Siren Control", zones: "Virtual Fence Zones", devices: "Edge Devices", workers: "Worker Tags", analytics: "Analytics", settings: "Settings" };

export default function App() {
  const [activePage, setActivePage] = useState("dashboard");
  const PageComponent = pages[activePage];

  return (
    <div style={{ display: "flex", height: "100vh", background: COLORS.bg, fontFamily: "'Segoe UI', system-ui, sans-serif", color: COLORS.text, overflow: "hidden" }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600;700;800&display=swap');
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        ::-webkit-scrollbar { width: 6px; }
        ::-webkit-scrollbar-track { background: ${COLORS.bg}; }
        ::-webkit-scrollbar-thumb { background: ${COLORS.border}; border-radius: 3px; }
      `}</style>

      {/* Sidebar */}
      <div style={{
        width: 220, background: COLORS.surface, borderRight: `1px solid ${COLORS.border}`,
        display: "flex", flexDirection: "column", flexShrink: 0,
      }}>
        <div style={{ padding: "20px 20px 24px", borderBottom: `1px solid ${COLORS.border}` }}>
          <div style={{ color: COLORS.accent, fontSize: 18, fontWeight: 800, fontFamily: "'JetBrains Mono', monospace", letterSpacing: 1 }}>SUDARSHAN</div>
          <div style={{ color: COLORS.textMuted, fontSize: 10, fontFamily: "'JetBrains Mono', monospace", letterSpacing: 3, marginTop: 2 }}>CHAKRA</div>
        </div>

        <div style={{ flex: 1, padding: "12px 8px", overflowY: "auto" }}>
          {sidebarItems.map(item => (
            <div key={item.id} onClick={() => setActivePage(item.id)} style={{
              display: "flex", alignItems: "center", gap: 12, padding: "10px 12px",
              borderRadius: 8, cursor: "pointer", marginBottom: 2,
              background: activePage === item.id ? `${COLORS.accent}18` : "transparent",
              borderLeft: activePage === item.id ? `3px solid ${COLORS.accent}` : "3px solid transparent",
              transition: "all 0.15s ease",
            }}>
              <span style={{ fontSize: 16, width: 20, textAlign: "center", color: activePage === item.id ? COLORS.accent : COLORS.textMuted }}>{item.icon}</span>
              <span style={{ fontSize: 14, color: activePage === item.id ? COLORS.text : COLORS.textDim, fontWeight: activePage === item.id ? 600 : 400 }}>{item.label}</span>
              {item.badge && (
                <span style={{
                  marginLeft: "auto", background: COLORS.critical, color: "white",
                  borderRadius: 10, padding: "1px 7px", fontSize: 11, fontWeight: 700,
                  fontFamily: "'JetBrains Mono', monospace",
                }}>{item.badge}</span>
              )}
            </div>
          ))}
        </div>

        <div style={{ padding: 12, borderTop: `1px solid ${COLORS.border}` }}>
          {["Edge Node A", "Edge Node B"].map((n, i) => (
            <div key={i} style={{ display: "flex", alignItems: "center", gap: 8, padding: "6px 12px" }}>
              <div style={{ width: 6, height: 6, borderRadius: "50%", background: COLORS.success }} />
              <span style={{ color: COLORS.textDim, fontSize: 12 }}>{n}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Main Content */}
      <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden" }}>
        <div style={{
          display: "flex", justifyContent: "space-between", alignItems: "center",
          padding: "14px 24px", borderBottom: `1px solid ${COLORS.border}`,
          background: COLORS.surface, flexShrink: 0,
        }}>
          <h2 style={{ fontSize: 20, fontWeight: 700, color: COLORS.text }}>{pageTitles[activePage] || activePage}</h2>
          <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
            <div style={{
              padding: "6px 16px", background: COLORS.surfaceAlt, borderRadius: 8,
              border: `1px solid ${COLORS.border}`, color: COLORS.textMuted, fontSize: 13, minWidth: 200,
            }}>Search alerts, zones...</div>
            <div style={{ position: "relative", cursor: "pointer" }}>
              <span style={{ fontSize: 20, color: COLORS.textDim }}>🔔</span>
              <div style={{
                position: "absolute", top: -4, right: -4, width: 16, height: 16,
                borderRadius: "50%", background: COLORS.critical, fontSize: 10,
                display: "flex", alignItems: "center", justifyContent: "center",
                color: "white", fontWeight: 700,
              }}>3</div>
            </div>
            <div style={{
              width: 32, height: 32, borderRadius: "50%", background: `${COLORS.accent}33`,
              display: "flex", alignItems: "center", justifyContent: "center",
              color: COLORS.accent, fontWeight: 700, fontSize: 14, cursor: "pointer",
            }}>R</div>
          </div>
        </div>

        <div style={{ flex: 1, overflow: "auto", padding: 24 }}>
          {PageComponent ? <PageComponent /> : (
            <div style={{
              display: "flex", alignItems: "center", justifyContent: "center",
              height: "100%", color: COLORS.textMuted, fontSize: 16,
            }}>
              <div style={{ textAlign: "center" }}>
                <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>⬡</div>
                <div>{pageTitles[activePage]} — Screen Mockup</div>
                <div style={{ fontSize: 13, marginTop: 8, color: COLORS.textMuted }}>
                  Implementation pending for this view
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
