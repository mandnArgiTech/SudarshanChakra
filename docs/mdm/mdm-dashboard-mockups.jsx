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
function Btn({ children, variant = "default", style }) {
  const v = { default: { background: sc.surfaceAlt, color: sc.text, border: `1px solid ${sc.border}` }, primary: { background: sc.accent, color: "#000", border: "none" }, danger: { background: `${sc.critical}20`, color: sc.critical, border: `1px solid ${sc.critical}40` }, success: { background: `${sc.success}20`, color: sc.success, border: `1px solid ${sc.success}40` } };
  return <button style={{ borderRadius: 8, padding: "8px 16px", fontSize: 13, fontWeight: 500, cursor: "pointer", fontFamily: "inherit", ...v[variant], ...style }}>{children}</button>;
}

const devices = [
  { id: "d1", name: "Ramesh's Phone", model: "Samsung A14", os: "Android 14", appVer: "2.1.0", kiosk: true, lockTask: true, status: "active", lastSeen: "2 min ago", screenToday: 8.2, whatsapp: 57, youtube: 23, sc: 304 },
  { id: "d2", name: "Suresh's Phone", model: "Redmi Note 12", os: "Android 13", appVer: "2.1.0", kiosk: true, lockTask: true, status: "active", lastSeen: "15 min ago", screenToday: 6.5, whatsapp: 72, youtube: 45, sc: 240 },
  { id: "d3", name: "Priya's Phone", model: "Moto G54", os: "Android 14", appVer: "-", kiosk: false, lockTask: false, status: "pending", lastSeen: "Never", screenToday: 0, whatsapp: 0, youtube: 0, sc: 0 },
];

const calls = [
  { time: "10:15 AM", type: "outgoing", number: "****5678", duration: "3m 0s", contact: "Supplier" },
  { time: "09:42 AM", type: "incoming", number: "****1234", duration: "1m 15s", contact: "Farm Office" },
  { time: "09:10 AM", type: "missed", number: "****9012", duration: "—", contact: "Unknown" },
  { time: "Yesterday 6:30 PM", type: "outgoing", number: "****3456", duration: "5m 22s", contact: "Vet Clinic" },
  { time: "Yesterday 4:15 PM", type: "incoming", number: "****7890", duration: "45s", contact: "Ramesh" },
];

const commands = [
  { time: "Mar 22 14:30", cmd: "SYNC_TELEMETRY", status: "executed", color: sc.success },
  { time: "Mar 22 10:00", cmd: "UPDATE_APP", status: "installed v2.1.0", color: sc.success },
  { time: "Mar 21 08:00", cmd: "SET_POLICY", status: "applied", color: sc.success },
  { time: "Mar 20 16:00", cmd: "LOCK_SCREEN", status: "executed", color: sc.warning },
];

const usageDays = [
  { day: "Today", hours: 8.2, sc: 62, wa: 12, yt: 5, maps: 2, phone: 2, other: 17 },
  { day: "Yesterday", hours: 6.5, sc: 58, wa: 18, yt: 11, maps: 3, phone: 3, other: 7 },
  { day: "Mar 20", hours: 7.8, sc: 65, wa: 10, yt: 8, maps: 1, phone: 2, other: 14 },
  { day: "Mar 19", hours: 5.1, sc: 70, wa: 8, yt: 5, maps: 4, phone: 1, other: 12 },
  { day: "Mar 18", hours: 9.0, sc: 55, wa: 15, yt: 12, maps: 2, phone: 3, other: 13 },
];

function DeviceList() {
  const stColor = { active: sc.success, pending: sc.warning, locked: sc.critical, wiped: sc.textMuted };
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div><div style={{ fontSize: 18, fontWeight: 600, color: sc.text }}>Device Management</div><div style={{ fontSize: 12, color: sc.textDim }}>3 devices · 2 active · 1 pending</div></div>
        <Btn variant="primary">+ Register Device</Btn>
      </div>
      <div style={{ display: "grid", gap: 8 }}>
        {devices.map(d => (
          <Card key={d.id} style={{ padding: 16 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <span style={{ width: 10, height: 10, borderRadius: "50%", background: stColor[d.status], boxShadow: `0 0 8px ${stColor[d.status]}60` }} />
                <div>
                  <div style={{ fontSize: 15, fontWeight: 600, color: sc.text }}>{d.name}</div>
                  <div style={{ fontSize: 12, color: sc.textDim }}>{d.model} · {d.os} · v{d.appVer}</div>
                </div>
              </div>
              <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                {d.kiosk && <Badge color={sc.accent}>Kiosk ON</Badge>}
                {d.lockTask && <Badge color={sc.info}>Lock Task</Badge>}
                <Badge color={stColor[d.status]}>{d.status}</Badge>
              </div>
            </div>
            {d.status === "active" && (
              <div style={{ marginTop: 12, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div style={{ display: "flex", gap: 16, fontSize: 12, color: sc.textDim }}>
                  <span>Screen: {d.screenToday}h</span>
                  <span>WhatsApp: {d.whatsapp}m</span>
                  <span>YouTube: {d.youtube}m</span>
                  <span>SC: {Math.floor(d.sc/60)}h{d.sc%60}m</span>
                  <span>Last seen: {d.lastSeen}</span>
                </div>
                <div style={{ display: "flex", gap: 4 }}>
                  <Btn style={{ fontSize: 11, padding: "5px 12px" }}>View Details</Btn>
                  <Btn style={{ fontSize: 11, padding: "5px 12px" }}>Send Command</Btn>
                </div>
              </div>
            )}
            {d.status === "pending" && (
              <div style={{ marginTop: 12, padding: 10, background: `${sc.warning}10`, border: `1px dashed ${sc.warning}30`, borderRadius: 8, fontSize: 12, color: sc.warning }}>
                Not provisioned. Run: adb shell dpm set-device-owner com.sudarshanchakra/.mdm.SudarshanDeviceAdminReceiver
              </div>
            )}
          </Card>
        ))}
      </div>
    </div>
  );
}

function DeviceDetail() {
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 16 }}>
        <Btn style={{ padding: "6px 10px" }}>← Back</Btn>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 18, fontWeight: 600, color: sc.text }}>Ramesh's Phone</div>
          <div style={{ fontSize: 12, color: sc.textDim }}>Samsung A14 · Android 14 · v2.1.0 · Kiosk ON · Last seen: 2 min ago</div>
        </div>
        <Badge color={sc.success}>Active</Badge>
      </div>
      <div style={{ display: "flex", gap: 6, marginBottom: 16 }}>
        <Btn variant="success" style={{ fontSize: 12 }}>Force Sync</Btn>
        <Btn variant="primary" style={{ fontSize: 12 }}>Push OTA</Btn>
        <Btn style={{ fontSize: 12 }}>Lock Screen</Btn>
        <Btn variant="danger" style={{ fontSize: 12 }}>Wipe Device</Btn>
      </div>

      <Card style={{ padding: 16, marginBottom: 12 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: sc.text, marginBottom: 12 }}>Screen time (5 days)</div>
        {usageDays.map((d, i) => (
          <div key={i} style={{ marginBottom: 10 }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: sc.textDim, marginBottom: 4 }}>
              <span>{d.day}</span><span>{d.hours}h total</span>
            </div>
            <div style={{ display: "flex", height: 20, borderRadius: 6, overflow: "hidden" }}>
              <div style={{ width: `${d.sc}%`, background: sc.success }} title={`SC ${d.sc}%`} />
              <div style={{ width: `${d.wa}%`, background: "#25D366" }} title={`WhatsApp ${d.wa}%`} />
              <div style={{ width: `${d.yt}%`, background: "#FF0000" }} title={`YouTube ${d.yt}%`} />
              <div style={{ width: `${d.maps}%`, background: sc.info }} title={`Maps ${d.maps}%`} />
              <div style={{ width: `${d.phone}%`, background: sc.accent }} title={`Phone ${d.phone}%`} />
              <div style={{ width: `${d.other}%`, background: sc.surfaceAlt }} title={`Other ${d.other}%`} />
            </div>
          </div>
        ))}
        <div style={{ display: "flex", gap: 12, marginTop: 8, fontSize: 11, color: sc.textDim }}>
          <span><span style={{ display: "inline-block", width: 8, height: 8, borderRadius: 2, background: sc.success, marginRight: 4 }}/>SC</span>
          <span><span style={{ display: "inline-block", width: 8, height: 8, borderRadius: 2, background: "#25D366", marginRight: 4 }}/>WhatsApp</span>
          <span><span style={{ display: "inline-block", width: 8, height: 8, borderRadius: 2, background: "#FF0000", marginRight: 4 }}/>YouTube</span>
          <span><span style={{ display: "inline-block", width: 8, height: 8, borderRadius: 2, background: sc.info, marginRight: 4 }}/>Maps</span>
          <span><span style={{ display: "inline-block", width: 8, height: 8, borderRadius: 2, background: sc.accent, marginRight: 4 }}/>Phone</span>
        </div>
      </Card>

      <Card style={{ padding: 16, marginBottom: 12 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: sc.text, marginBottom: 12 }}>Call log</div>
        <div style={{ display: "grid", gap: 4 }}>
          {calls.map((c, i) => {
            const typeColor = { incoming: sc.success, outgoing: sc.info, missed: sc.critical }[c.type];
            return (
              <div key={i} style={{ display: "grid", gridTemplateColumns: "120px 80px 90px 70px 1fr", alignItems: "center", padding: "6px 8px", background: i%2===0 ? sc.surfaceAlt : "transparent", borderRadius: 6, fontSize: 12 }}>
                <span style={{ color: sc.textMuted }}>{c.time}</span>
                <Badge color={typeColor}>{c.type}</Badge>
                <span style={{ color: sc.textDim, fontFamily: "monospace" }}>{c.number}</span>
                <span style={{ color: sc.textDim }}>{c.duration}</span>
                <span style={{ color: sc.text }}>{c.contact}</span>
              </div>
            );
          })}
        </div>
      </Card>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <Card style={{ padding: 16 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: sc.text, marginBottom: 12 }}>Policies</div>
          {[["Status bar disabled","ON"],["Safe boot blocked","ON"],["Factory reset blocked","ON"],["Wi-Fi config locked","ON"],["Mobile data forced","ON"]].map(([k,v]) => (
            <div key={k} style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: `1px solid ${sc.border}`, fontSize: 12 }}>
              <span style={{ color: sc.textDim }}>{k}</span>
              <Badge color={sc.success}>{v}</Badge>
            </div>
          ))}
          <div style={{ marginTop: 8, fontSize: 11, color: sc.textMuted }}>Whitelisted: SC, WhatsApp, YouTube, Maps, Dialer</div>
          <Btn style={{ marginTop: 8, fontSize: 11, width: "100%" }}>Edit Policies</Btn>
        </Card>
        <Card style={{ padding: 16 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: sc.text, marginBottom: 12 }}>Command history</div>
          {commands.map((c, i) => (
            <div key={i} style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: `1px solid ${sc.border}`, fontSize: 12 }}>
              <span style={{ color: sc.textMuted }}>{c.time}</span>
              <span style={{ color: sc.textDim }}>{c.cmd}</span>
              <Badge color={c.color}>{c.status}</Badge>
            </div>
          ))}
        </Card>
      </div>
    </div>
  );
}

export default function MdmMockups() {
  const [screen, setScreen] = useState("list");
  return (
    <div style={{ minHeight: "100vh", background: sc.bg, color: sc.text, fontFamily: "'DM Sans', system-ui, sans-serif" }}>
      <link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&display=swap" rel="stylesheet" />
      <div style={{ borderBottom: `1px solid ${sc.border}`, padding: "12px 20px", display: "flex", alignItems: "center", gap: 10 }}>
        <div style={{ width: 28, height: 28, borderRadius: 6, background: `linear-gradient(135deg, ${sc.critical}, ${sc.accent})`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, color: "#fff" }}>SC</div>
        <span style={{ fontSize: 14, fontWeight: 600 }}>MDM Device Management — Mockups</span>
      </div>
      <div style={{ display: "flex", gap: 4, padding: "10px 20px", borderBottom: `1px solid ${sc.border}` }}>
        {[["list","Device List"],["detail","Device Detail"]].map(([id,label]) => (
          <button key={id} onClick={() => setScreen(id)} style={{ background: screen === id ? sc.surfaceAlt : "transparent", color: screen === id ? sc.text : sc.textMuted, border: `1px solid ${screen === id ? sc.border : "transparent"}`, borderRadius: 8, padding: "6px 14px", fontSize: 12, fontWeight: 500, cursor: "pointer" }}>{label}</button>
        ))}
      </div>
      <div style={{ padding: 20, maxWidth: 900, margin: "0 auto" }}>
        {screen === "list" && <DeviceList />}
        {screen === "detail" && <DeviceDetail />}
      </div>
    </div>
  );
}
