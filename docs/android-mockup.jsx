import { useState } from "react";

const C = {
  bg: "#f8f7f4",
  surface: "#ffffff",
  surfaceAlt: "#f1f0ec",
  border: "#e5e3dc",
  accent: "#c8553d",
  accentBg: "#c8553d11",
  dark: "#1a1a1a",
  text: "#2d2d2d",
  textDim: "#6b6b6b",
  textMuted: "#9e9e9e",
  critical: "#c8553d",
  high: "#d4832f",
  warning: "#b8962e",
  success: "#4a8c5c",
  info: "#4a7ab5",
};

const mockAlerts = [
  { id: 1, type: "critical", icon: "⚠", title: "CHILD near Pond", zone: "Pond Danger Zone", cam: "CAM-03", time: "Just now", conf: 92, status: "new" },
  { id: 2, type: "critical", icon: "⚠", title: "Person in Pond Zone", zone: "Pond Danger Zone", cam: "CAM-03", time: "2m ago", conf: 88, status: "new" },
  { id: 3, type: "high", icon: "◆", title: "Intruder Detected", zone: "Farm Perimeter", cam: "CAM-01", time: "8m ago", conf: 85, status: "new" },
  { id: 4, type: "high", icon: "◆", title: "Snake Detected", zone: "Snake Zone Alpha", cam: "CAM-05", time: "14m ago", conf: 79, status: "ack" },
  { id: 5, type: "warning", icon: "◇", title: "Cow Outside Pasture", zone: "Pasture Area", cam: "CAM-07", time: "22m ago", conf: 91, status: "ack" },
  { id: 6, type: "high", icon: "◆", title: "Smoke Detected", zone: "Storage Shed", cam: "CAM-02", time: "1h ago", conf: 73, status: "resolved" },
];

const typeColor = (t) => t === "critical" ? C.critical : t === "high" ? C.high : C.warning;

function PhoneFrame({ children, title }) {
  return (
    <div style={{
      width: 375, height: 812, background: C.bg, borderRadius: 40,
      border: `8px solid #1a1a1a`, overflow: "hidden", position: "relative",
      boxShadow: "0 25px 80px rgba(0,0,0,0.25), 0 5px 20px rgba(0,0,0,0.15)",
      display: "flex", flexDirection: "column",
    }}>
      {/* Status Bar */}
      <div style={{
        display: "flex", justifyContent: "space-between", alignItems: "center",
        padding: "12px 24px 6px", fontSize: 12, fontWeight: 600, color: C.dark,
        flexShrink: 0,
      }}>
        <span>9:41</span>
        <div style={{ width: 120, height: 28, background: "#1a1a1a", borderRadius: 14 }} />
        <div style={{ display: "flex", gap: 4, alignItems: "center" }}>
          <span style={{ fontSize: 10 }}>▂▄▆█</span>
          <span style={{ fontSize: 14 }}>◐</span>
        </div>
      </div>
      {children}
    </div>
  );
}

function AlertFeedScreen({ onAlertTap, onSirenTap }) {
  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden" }}>
      {/* Header */}
      <div style={{ padding: "12px 20px 16px", flexShrink: 0 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <div style={{ fontSize: 28, fontWeight: 800, color: C.dark, fontFamily: "Georgia, serif", letterSpacing: -0.5 }}>Alerts</div>
            <div style={{ fontSize: 12, color: C.textMuted, marginTop: 2 }}>SudarshanChakra Farm</div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <div style={{
              width: 40, height: 40, borderRadius: 20, background: C.surface,
              border: `1px solid ${C.border}`, display: "flex", alignItems: "center",
              justifyContent: "center", fontSize: 18, cursor: "pointer", position: "relative",
            }}>
              🔔
              <div style={{
                position: "absolute", top: 2, right: 2, width: 14, height: 14,
                borderRadius: 7, background: C.critical, fontSize: 9,
                display: "flex", alignItems: "center", justifyContent: "center",
                color: "white", fontWeight: 700,
              }}>3</div>
            </div>
          </div>
        </div>

        {/* Emergency Siren Button */}
        <button onClick={onSirenTap} style={{
          width: "100%", padding: "14px", marginTop: 16, borderRadius: 14,
          border: `2px solid ${C.critical}`, background: C.accentBg,
          color: C.critical, fontSize: 15, fontWeight: 700, cursor: "pointer",
          display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
          letterSpacing: 1,
        }}>
          <span style={{ fontSize: 20 }}>◉</span> EMERGENCY SIREN
        </button>

        {/* Filter Pills */}
        <div style={{ display: "flex", gap: 8, marginTop: 16, overflowX: "auto" }}>
          {[
            { label: "All", count: 6, active: true },
            { label: "Critical", count: 2, color: C.critical },
            { label: "High", count: 3, color: C.high },
            { label: "Warning", count: 1, color: C.warning },
          ].map((f, i) => (
            <div key={i} style={{
              padding: "6px 14px", borderRadius: 20, fontSize: 13, fontWeight: 600,
              whiteSpace: "nowrap", cursor: "pointer",
              background: f.active ? C.dark : C.surface,
              color: f.active ? "white" : C.textDim,
              border: `1px solid ${f.active ? C.dark : C.border}`,
            }}>
              {f.label} ({f.count})
            </div>
          ))}
        </div>
      </div>

      {/* Alert List */}
      <div style={{ flex: 1, overflow: "auto", padding: "0 20px 20px" }}>
        {mockAlerts.map((a) => (
          <div key={a.id} onClick={() => onAlertTap(a)} style={{
            background: C.surface, borderRadius: 14, padding: 16, marginBottom: 10,
            border: `1px solid ${a.status === "new" ? typeColor(a.type) + "44" : C.border}`,
            cursor: "pointer", position: "relative",
            boxShadow: a.status === "new" ? `0 2px 12px ${typeColor(a.type)}15` : "none",
          }}>
            {a.status === "new" && (
              <div style={{
                position: "absolute", top: 16, right: 16, width: 8, height: 8,
                borderRadius: 4, background: typeColor(a.type),
              }} />
            )}
            <div style={{ display: "flex", gap: 12, alignItems: "flex-start" }}>
              <div style={{
                width: 40, height: 40, borderRadius: 10, flexShrink: 0,
                background: `${typeColor(a.type)}15`, display: "flex",
                alignItems: "center", justifyContent: "center",
                color: typeColor(a.type), fontSize: 18, fontWeight: 700,
              }}>{a.icon}</div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 15, fontWeight: 700, color: C.dark }}>{a.title}</div>
                <div style={{ fontSize: 13, color: C.textDim, marginTop: 3 }}>{a.zone}</div>
                <div style={{ display: "flex", gap: 12, marginTop: 8, alignItems: "center" }}>
                  <span style={{ fontSize: 12, color: C.textMuted }}>{a.cam}</span>
                  <span style={{ fontSize: 12, color: C.textMuted }}>·</span>
                  <span style={{ fontSize: 12, color: C.textMuted }}>{a.conf}%</span>
                  <span style={{ fontSize: 12, color: C.textMuted }}>·</span>
                  <span style={{ fontSize: 12, color: a.status === "new" ? typeColor(a.type) : C.textMuted, fontWeight: a.status === "new" ? 600 : 400 }}>{a.time}</span>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Bottom Nav */}
      <div style={{
        display: "flex", justifyContent: "space-around", padding: "10px 0 28px",
        borderTop: `1px solid ${C.border}`, background: C.surface, flexShrink: 0,
      }}>
        {[
          { icon: "⬡", label: "Alerts", active: true },
          { icon: "◎", label: "Cameras" },
          { icon: "◉", label: "Siren" },
          { icon: "◈", label: "Devices" },
          { icon: "◐", label: "Profile" },
        ].map((n, i) => (
          <div key={i} style={{
            display: "flex", flexDirection: "column", alignItems: "center", gap: 3,
            cursor: "pointer", opacity: n.active ? 1 : 0.5,
          }}>
            <span style={{ fontSize: 20, color: n.active ? C.accent : C.textMuted }}>{n.icon}</span>
            <span style={{ fontSize: 10, color: n.active ? C.accent : C.textMuted, fontWeight: n.active ? 700 : 400 }}>{n.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function AlertDetailScreen({ alert, onBack, onSiren }) {
  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden" }}>
      {/* Header */}
      <div style={{
        display: "flex", alignItems: "center", gap: 12, padding: "8px 20px 12px",
        borderBottom: `1px solid ${C.border}`, flexShrink: 0,
      }}>
        <div onClick={onBack} style={{ fontSize: 24, cursor: "pointer", color: C.accent }}>←</div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 18, fontWeight: 700, color: C.dark }}>Alert Detail</div>
        </div>
        <div style={{
          padding: "4px 12px", borderRadius: 20, fontSize: 11, fontWeight: 700,
          textTransform: "uppercase", color: typeColor(alert.type),
          background: `${typeColor(alert.type)}15`, border: `1px solid ${typeColor(alert.type)}33`,
        }}>{alert.type}</div>
      </div>

      <div style={{ flex: 1, overflow: "auto", padding: 20 }}>
        {/* Snapshot Placeholder */}
        <div style={{
          height: 200, background: `linear-gradient(135deg, ${C.surfaceAlt}, #e0ddd6)`,
          borderRadius: 16, display: "flex", alignItems: "center", justifyContent: "center",
          marginBottom: 20, border: `1px solid ${C.border}`, position: "relative",
        }}>
          <span style={{ fontSize: 48, opacity: 0.2 }}>◎</span>
          <div style={{
            position: "absolute", bottom: 12, left: 12, padding: "4px 12px",
            background: "rgba(0,0,0,0.6)", borderRadius: 8, fontSize: 12, color: "white",
          }}>Snapshot from {alert.cam}</div>
          <div style={{
            position: "absolute", top: 12, right: 12, padding: "4px 12px",
            background: typeColor(alert.type), borderRadius: 8, fontSize: 11,
            color: "white", fontWeight: 700,
          }}>AI Detection: {alert.conf}%</div>
        </div>

        {/* Details */}
        <div style={{ background: C.surface, borderRadius: 14, border: `1px solid ${C.border}`, overflow: "hidden" }}>
          {[
            { label: "Detection", value: alert.title },
            { label: "Zone", value: alert.zone },
            { label: "Camera", value: alert.cam },
            { label: "Confidence", value: `${alert.conf}%` },
            { label: "Time", value: alert.time },
            { label: "Status", value: alert.status.toUpperCase() },
            { label: "Node", value: "Edge Node A" },
          ].map((d, i) => (
            <div key={i} style={{
              display: "flex", justifyContent: "space-between", padding: "13px 16px",
              borderBottom: `1px solid ${C.border}`,
            }}>
              <span style={{ fontSize: 14, color: C.textMuted }}>{d.label}</span>
              <span style={{ fontSize: 14, color: C.dark, fontWeight: 600 }}>{d.value}</span>
            </div>
          ))}
        </div>

        {/* Action Buttons */}
        <div style={{ marginTop: 20, display: "flex", flexDirection: "column", gap: 10 }}>
          {alert.status === "new" && (
            <button style={{
              width: "100%", padding: 16, borderRadius: 14, border: `1px solid ${C.accent}`,
              background: C.accent, color: "white", fontSize: 16, fontWeight: 700,
              cursor: "pointer",
            }}>Acknowledge Alert</button>
          )}
          <button onClick={onSiren} style={{
            width: "100%", padding: 16, borderRadius: 14, border: `2px solid ${C.critical}`,
            background: C.accentBg, color: C.critical, fontSize: 16, fontWeight: 700,
            cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
          }}>
            <span style={{ fontSize: 20 }}>◉</span> Trigger Siren
          </button>
          <button style={{
            width: "100%", padding: 16, borderRadius: 14, border: `1px solid ${C.border}`,
            background: C.surface, color: C.textDim, fontSize: 16, fontWeight: 600,
            cursor: "pointer",
          }}>Mark as False Positive</button>
        </div>
      </div>
    </div>
  );
}

function SirenScreen({ onBack }) {
  const [active, setActive] = useState(false);

  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden" }}>
      <div style={{
        display: "flex", alignItems: "center", gap: 12, padding: "8px 20px 12px",
        borderBottom: `1px solid ${C.border}`, flexShrink: 0,
      }}>
        <div onClick={onBack} style={{ fontSize: 24, cursor: "pointer", color: C.accent }}>←</div>
        <div style={{ fontSize: 18, fontWeight: 700, color: C.dark }}>Siren Control</div>
      </div>

      <div style={{ flex: 1, overflow: "auto", padding: 20 }}>
        {/* Big Siren Button */}
        <div style={{
          background: active ? `${C.critical}11` : C.surface,
          borderRadius: 20, padding: "40px 20px", textAlign: "center",
          border: `2px solid ${active ? C.critical : C.border}`, marginBottom: 24,
        }}>
          <div style={{
            width: 100, height: 100, borderRadius: 50, margin: "0 auto 20px",
            background: active ? `${C.critical}22` : C.surfaceAlt,
            border: `3px solid ${active ? C.critical : C.border}`,
            display: "flex", alignItems: "center", justifyContent: "center",
            fontSize: 40, color: active ? C.critical : C.textMuted,
          }}>
            {active ? "◉" : "○"}
          </div>
          <div style={{
            fontSize: 22, fontWeight: 800, letterSpacing: 3,
            color: active ? C.critical : C.textMuted, textTransform: "uppercase",
          }}>{active ? "SIREN ACTIVE" : "STANDBY"}</div>

          <button onClick={() => setActive(!active)} style={{
            margin: "24px auto 0", padding: "16px 48px", borderRadius: 14,
            border: `2px solid ${active ? C.success : C.critical}`,
            background: active ? `${C.success}15` : `${C.critical}15`,
            color: active ? C.success : C.critical,
            fontSize: 16, fontWeight: 800, cursor: "pointer",
            textTransform: "uppercase", letterSpacing: 2,
          }}>{active ? "⬛ STOP" : "◉ TRIGGER ALL"}</button>
        </div>

        {/* Per-Node Controls */}
        <div style={{ fontSize: 14, fontWeight: 700, color: C.dark, marginBottom: 12 }}>Target Selection</div>
        {["Edge Node A — 10.8.0.10", "Edge Node B — 10.8.0.11"].map((n, i) => (
          <div key={i} style={{
            display: "flex", justifyContent: "space-between", alignItems: "center",
            background: C.surface, borderRadius: 14, padding: "14px 16px",
            marginBottom: 8, border: `1px solid ${C.border}`,
          }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <div style={{ width: 8, height: 8, borderRadius: 4, background: C.success }} />
              <span style={{ fontSize: 14, color: C.dark }}>{n}</span>
            </div>
            <button style={{
              padding: "8px 20px", borderRadius: 8, border: `1px solid ${C.high}44`,
              background: `${C.high}11`, color: C.high, fontSize: 12,
              fontWeight: 700, cursor: "pointer",
            }}>SIREN</button>
          </div>
        ))}

        {/* Recent History */}
        <div style={{ fontSize: 14, fontWeight: 700, color: C.dark, margin: "24px 0 12px" }}>Recent Activations</div>
        {[
          { time: "06:12 AM", reason: "Child near pond", by: "Auto" },
          { time: "Yesterday 22:45", reason: "Intruder detected", by: "Ravi" },
          { time: "Mar 04 14:30", reason: "Snake detected", by: "Auto" },
        ].map((h, i) => (
          <div key={i} style={{
            background: C.surface, borderRadius: 12, padding: "12px 16px",
            marginBottom: 6, border: `1px solid ${C.border}`,
          }}>
            <div style={{ fontSize: 14, color: C.dark }}>{h.reason}</div>
            <div style={{ fontSize: 12, color: C.textMuted, marginTop: 4 }}>{h.time} · By: {h.by}</div>
          </div>
        ))}
      </div>

      {/* Bottom Nav */}
      <div style={{
        display: "flex", justifyContent: "space-around", padding: "10px 0 28px",
        borderTop: `1px solid ${C.border}`, background: C.surface, flexShrink: 0,
      }}>
        {[
          { icon: "⬡", label: "Alerts" },
          { icon: "◎", label: "Cameras" },
          { icon: "◉", label: "Siren", active: true },
          { icon: "◈", label: "Devices" },
          { icon: "◐", label: "Profile" },
        ].map((n, i) => (
          <div key={i} style={{
            display: "flex", flexDirection: "column", alignItems: "center", gap: 3,
            opacity: n.active ? 1 : 0.5,
          }}>
            <span style={{ fontSize: 20, color: n.active ? C.accent : C.textMuted }}>{n.icon}</span>
            <span style={{ fontSize: 10, color: n.active ? C.accent : C.textMuted, fontWeight: n.active ? 700 : 400 }}>{n.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function App() {
  const [screen, setScreen] = useState("feed"); // feed | detail | siren
  const [selectedAlert, setSelectedAlert] = useState(null);

  return (
    <div style={{
      display: "flex", gap: 40, justifyContent: "center", alignItems: "center",
      minHeight: "100vh", background: "#e8e6e1", padding: 40, flexWrap: "wrap",
      fontFamily: "'SF Pro Display', -apple-system, system-ui, sans-serif",
    }}>
      <style>{`* { box-sizing: border-box; margin: 0; padding: 0; }`}</style>

      {/* Screen 1: Alert Feed */}
      <div style={{ textAlign: "center" }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: "#6b6b6b", marginBottom: 16, letterSpacing: 2, textTransform: "uppercase" }}>Alert Feed</div>
        <PhoneFrame>
          <AlertFeedScreen
            onAlertTap={(a) => { setSelectedAlert(a); setScreen("detail"); }}
            onSirenTap={() => setScreen("siren")}
          />
        </PhoneFrame>
      </div>

      {/* Screen 2: Alert Detail */}
      <div style={{ textAlign: "center" }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: "#6b6b6b", marginBottom: 16, letterSpacing: 2, textTransform: "uppercase" }}>Alert Detail</div>
        <PhoneFrame>
          <AlertDetailScreen
            alert={selectedAlert || mockAlerts[0]}
            onBack={() => setScreen("feed")}
            onSiren={() => setScreen("siren")}
          />
        </PhoneFrame>
      </div>

      {/* Screen 3: Siren Control */}
      <div style={{ textAlign: "center" }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: "#6b6b6b", marginBottom: 16, letterSpacing: 2, textTransform: "uppercase" }}>Siren Control</div>
        <PhoneFrame>
          <SirenScreen onBack={() => setScreen("feed")} />
        </PhoneFrame>
      </div>
    </div>
  );
}
