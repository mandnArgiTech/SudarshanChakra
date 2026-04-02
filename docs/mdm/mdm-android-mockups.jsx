import { useState } from "react";

/*
 * SudarshanChakra MDM — Android Kiosk Mockup Screens
 * Aligned with existing Android theme: Terracotta/Cream Material 3 palette.
 * For Cursor: these are the reference screens for KioskLauncherActivity,
 * DevEscapeDialog, MDM status in SettingsScreen, and telemetry status.
 */

const mc = {
  bg: "#F8F7F4", card: "#FFFFFF", text: "#2D2A26", dim: "#6B6560",
  muted: "#9E9891", accent: "#C8553D", accentDark: "#B04530",
  green: "#4A8C5C", blue: "#3A8ACC", orange: "#D4832F",
  amber: "#B8962E", border: "#E8E5E0", surface: "#F0EDE8",
  navBg: "#FFFFFF", navInactive: "#ADA8A2",
  critical: "#C8553D", success: "#4A8C5C",
};

function Phone({ title, subtitle, children, statusBar = true, navBar = null }) {
  return (
    <div style={{ width: 260, background: "#111", borderRadius: 32, padding: "8px 7px", boxShadow: "0 8px 30px rgba(0,0,0,0.4)" }}>
      <div style={{ background: mc.bg, borderRadius: 26, overflow: "hidden", minHeight: 520, display: "flex", flexDirection: "column" }}>
        {statusBar && (
          <div style={{ background: mc.accent, padding: "6px 14px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: 10, color: "#fff", fontWeight: 600 }}>{title}</span>
            {subtitle && <span style={{ fontSize: 9, color: "rgba(255,255,255,0.7)" }}>{subtitle}</span>}
          </div>
        )}
        <div style={{ flex: 1, display: "flex", flexDirection: "column" }}>
          {children}
        </div>
        {navBar}
      </div>
    </div>
  );
}

function KioskNavBar({ active }) {
  const tabs = [
    { id: "alerts", label: "Alerts", icon: "!" },
    { id: "cameras", label: "Cameras", icon: "◉" },
    { id: "water", label: "Water", icon: "◆" },
    { id: "settings", label: "Settings", icon: "⚙" },
  ];
  return (
    <div style={{ display: "flex", borderTop: `1px solid ${mc.border}`, background: mc.navBg, padding: "6px 4px 8px" }}>
      {tabs.map(t => (
        <div key={t.id} style={{ flex: 1, textAlign: "center", cursor: "pointer" }}>
          <div style={{ fontSize: 16, color: t.id === active ? mc.accent : mc.navInactive }}>{t.icon}</div>
          <div style={{ fontSize: 9, fontWeight: t.id === active ? 600 : 400, color: t.id === active ? mc.accent : mc.navInactive, marginTop: 1 }}>{t.label}</div>
        </div>
      ))}
    </div>
  );
}

// ═══ SCREEN 1: Kiosk Home Launcher ═══
function KioskHome() {
  return (
    <Phone title="SudarshanChakra" subtitle="Kiosk Mode" navBar={<KioskNavBar active="alerts" />}>
      <div style={{ flex: 1, padding: 10 }}>
        <div style={{ fontSize: 11, color: mc.dim, fontWeight: 600, marginBottom: 6 }}>LIVE ALERTS</div>
        {[
          { pri: "critical", text: "Snake near storage", time: "2 min ago", cam: "cam-05" },
          { pri: "high", text: "Person at east perimeter", time: "15 min ago", cam: "cam-04" },
        ].map((a, i) => (
          <div key={i} style={{ background: mc.card, border: `1px solid ${a.pri === "critical" ? mc.critical + "40" : mc.border}`, borderRadius: 10, padding: "8px 10px", marginBottom: 6, borderLeft: `3px solid ${a.pri === "critical" ? mc.critical : mc.orange}` }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: a.pri === "critical" ? mc.critical : mc.text }}>{a.text}</span>
              <span style={{ fontSize: 9, color: mc.muted }}>{a.time}</span>
            </div>
            <span style={{ fontSize: 10, color: mc.dim }}>{a.cam}</span>
          </div>
        ))}

        <div style={{ fontSize: 11, color: mc.dim, fontWeight: 600, marginTop: 12, marginBottom: 6 }}>FARM STATUS</div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 6 }}>
          <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 10, padding: 10, textAlign: "center" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: mc.blue }}>72%</div>
            <div style={{ fontSize: 10, color: mc.dim }}>Water Tank</div>
          </div>
          <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 10, padding: 10, textAlign: "center" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: mc.green }}>8/8</div>
            <div style={{ fontSize: 10, color: mc.dim }}>Cameras Online</div>
          </div>
        </div>
      </div>

      {/* Whitelisted app grid at bottom */}
      <div style={{ background: mc.surface, borderTop: `1px solid ${mc.border}`, padding: "10px 16px" }}>
        <div style={{ fontSize: 9, color: mc.muted, marginBottom: 6, textAlign: "center" }}>ALLOWED APPS</div>
        <div style={{ display: "flex", justifyContent: "space-around" }}>
          {[
            { name: "WhatsApp", color: "#25D366", icon: "W" },
            { name: "YouTube", color: "#FF0000", icon: "Y" },
            { name: "Maps", color: "#4285F4", icon: "M" },
            { name: "Phone", color: "#34A853", icon: "P" },
          ].map(app => (
            <div key={app.name} style={{ textAlign: "center", cursor: "pointer" }}>
              <div style={{ width: 40, height: 40, borderRadius: 10, background: app.color, display: "flex", alignItems: "center", justifyContent: "center", color: "#fff", fontWeight: 700, fontSize: 16 }}>{app.icon}</div>
              <div style={{ fontSize: 9, color: mc.dim, marginTop: 3 }}>{app.name}</div>
            </div>
          ))}
        </div>
      </div>
    </Phone>
  );
}

// ═══ SCREEN 2: Kiosk Locked Screen (no status bar, no nav) ═══
function KioskLocked() {
  return (
    <Phone title="" statusBar={false}>
      <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: 20, background: mc.bg }}>
        <div style={{ width: 60, height: 60, borderRadius: 30, background: `${mc.accent}15`, display: "flex", alignItems: "center", justifyContent: "center", marginBottom: 16 }}>
          <div style={{ width: 30, height: 30, borderRadius: 15, background: mc.accent, display: "flex", alignItems: "center", justifyContent: "center", color: "#fff", fontSize: 18, fontWeight: 700 }}>SC</div>
        </div>
        <div style={{ fontSize: 16, fontWeight: 700, color: mc.text, marginBottom: 4 }}>SudarshanChakra</div>
        <div style={{ fontSize: 11, color: mc.dim, marginBottom: 20 }}>Farm Security System</div>
        <div style={{ background: `${mc.critical}10`, border: `1px solid ${mc.critical}30`, borderRadius: 10, padding: "10px 20px", textAlign: "center" }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: mc.critical }}>KIOSK MODE ACTIVE</div>
          <div style={{ fontSize: 10, color: mc.dim, marginTop: 4 }}>Status bar disabled</div>
          <div style={{ fontSize: 10, color: mc.dim }}>Home button locked</div>
          <div style={{ fontSize: 10, color: mc.dim }}>Recent apps blocked</div>
        </div>
        <div style={{ marginTop: 20, fontSize: 10, color: mc.muted }}>Tap screen to unlock dashboard</div>
      </div>
    </Phone>
  );
}

// ═══ SCREEN 3: Settings with MDM Status + Escape Hatch ═══
function KioskSettings() {
  const [tapCount, setTapCount] = useState(0);
  const [showEscape, setShowEscape] = useState(false);

  return (
    <Phone title="Settings" navBar={<KioskNavBar active="settings" />}>
      <div style={{ flex: 1, padding: 10, overflow: "auto" }}>
        {/* User info */}
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 10, padding: 12, marginBottom: 8 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: mc.text }}>Ramesh Kumar</div>
          <div style={{ fontSize: 11, color: mc.dim }}>Role: Operator · Sanga Reddy Farm</div>
        </div>

        {/* MDM Status card */}
        <div style={{ background: mc.card, border: `1px solid ${mc.green}30`, borderRadius: 10, padding: 12, marginBottom: 8 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: mc.text, marginBottom: 8 }}>Device Management</div>
          {[
            ["Kiosk mode", "Active", mc.green],
            ["Lock task", "Engaged", mc.green],
            ["Status bar", "Disabled", mc.critical],
            ["Factory reset", "Blocked", mc.critical],
            ["Wi-Fi config", "Locked", mc.critical],
            ["Mobile data", "Forced ON", mc.green],
          ].map(([k, v, c]) => (
            <div key={k} style={{ display: "flex", justifyContent: "space-between", padding: "3px 0", borderBottom: `1px solid ${mc.border}` }}>
              <span style={{ fontSize: 11, color: mc.dim }}>{k}</span>
              <span style={{ fontSize: 11, fontWeight: 600, color: c }}>{v}</span>
            </div>
          ))}
        </div>

        {/* Telemetry sync status */}
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 10, padding: 12, marginBottom: 8 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: mc.text, marginBottom: 6 }}>Telemetry Sync</div>
          <div style={{ fontSize: 11, color: mc.dim }}>Last sync: 12 min ago</div>
          <div style={{ fontSize: 11, color: mc.dim }}>Pending: 0 usage · 0 calls</div>
          <div style={{ fontSize: 11, color: mc.dim }}>Next sync: ~18 min</div>
          <div style={{ marginTop: 6, background: mc.green, color: "#fff", borderRadius: 8, padding: "6px 0", textAlign: "center", fontSize: 11, fontWeight: 600 }}>Force Sync Now</div>
        </div>

        {/* Whitelisted apps */}
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 10, padding: 12, marginBottom: 8 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: mc.text, marginBottom: 6 }}>Allowed Apps</div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
            {["SudarshanChakra", "WhatsApp", "YouTube", "Maps", "Phone"].map(a => (
              <span key={a} style={{ background: `${mc.green}15`, color: mc.green, border: `1px solid ${mc.green}30`, borderRadius: 6, padding: "3px 8px", fontSize: 10 }}>{a}</span>
            ))}
          </div>
        </div>

        {/* Connection */}
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 10, padding: 12, marginBottom: 8 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: mc.text, marginBottom: 6 }}>Server Connection</div>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: "50%", background: mc.green }} />
            <span style={{ fontSize: 11, color: mc.green }}>MQTT Connected</span>
          </div>
          <div style={{ fontSize: 10, color: mc.muted, marginTop: 4 }}>vivasvan-tech.in:8883</div>
        </div>

        {/* App version — tap 7 times for escape */}
        <div
          style={{ textAlign: "center", padding: "8px 0", cursor: "pointer" }}
          onClick={() => {
            const next = tapCount + 1;
            setTapCount(next);
            if (next >= 7) { setShowEscape(true); setTapCount(0); }
          }}
        >
          <span style={{ fontSize: 11, color: mc.muted }}>v2.1.0 (build 87)</span>
          {tapCount > 2 && tapCount < 7 && (
            <div style={{ fontSize: 9, color: mc.muted }}>{7 - tapCount} taps to developer menu</div>
          )}
        </div>
      </div>

      {/* Escape dialog overlay */}
      {showEscape && (
        <div style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,0.6)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 10 }}>
          <div style={{ background: mc.card, borderRadius: 16, padding: 20, width: 220 }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: mc.critical, marginBottom: 4 }}>Developer Menu</div>
            <div style={{ fontSize: 11, color: mc.dim, marginBottom: 12 }}>Enter admin PIN to proceed</div>
            <div style={{ display: "flex", justifyContent: "center", gap: 8, marginBottom: 16 }}>
              {[1,2,3,4].map(i => (
                <div key={i} style={{ width: 36, height: 36, borderRadius: 8, border: `2px solid ${mc.border}`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18, color: mc.text }}>*</div>
              ))}
            </div>
            <div style={{ display: "grid", gap: 6 }}>
              <div style={{ background: `${mc.orange}15`, color: mc.orange, border: `1px solid ${mc.orange}40`, borderRadius: 8, padding: "8px 0", textAlign: "center", fontSize: 12, fontWeight: 600 }}>Exit Kiosk Mode</div>
              <div style={{ background: `${mc.critical}15`, color: mc.critical, border: `1px solid ${mc.critical}40`, borderRadius: 8, padding: "8px 0", textAlign: "center", fontSize: 12, fontWeight: 600 }}>Decommission Device</div>
              <div style={{ background: mc.surface, color: mc.dim, border: `1px solid ${mc.border}`, borderRadius: 8, padding: "8px 0", textAlign: "center", fontSize: 12 }} onClick={() => setShowEscape(false)}>Cancel</div>
            </div>
          </div>
        </div>
      )}
    </Phone>
  );
}

// ═══ SCREEN 4: Telemetry Status Detail ═══
function TelemetryStatus() {
  return (
    <Phone title="My Usage Today" navBar={<KioskNavBar active="settings" />}>
      <div style={{ flex: 1, padding: 10 }}>
        <div style={{ fontSize: 11, color: mc.dim, fontWeight: 600, marginBottom: 6 }}>SCREEN TIME</div>
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 10, padding: 12, marginBottom: 10 }}>
          <div style={{ fontSize: 28, fontWeight: 700, color: mc.text }}>8h 12m</div>
          <div style={{ fontSize: 11, color: mc.dim }}>Total screen time today · 45 unlocks</div>

          <div style={{ marginTop: 10, display: "grid", gap: 6 }}>
            {[
              { app: "SudarshanChakra", time: "5h 4m", pct: 62, color: mc.green },
              { app: "WhatsApp", time: "57m", pct: 12, color: "#25D366" },
              { app: "YouTube", time: "23m", pct: 5, color: "#FF0000" },
              { app: "Maps", time: "12m", pct: 2, color: "#4285F4" },
              { app: "Phone", time: "8m", pct: 2, color: mc.orange },
            ].map(a => (
              <div key={a.app}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: mc.dim, marginBottom: 2 }}>
                  <span>{a.app}</span>
                  <span>{a.time}</span>
                </div>
                <div style={{ height: 6, background: mc.surface, borderRadius: 3, overflow: "hidden" }}>
                  <div style={{ width: `${a.pct}%`, height: "100%", background: a.color, borderRadius: 3 }} />
                </div>
              </div>
            ))}
          </div>
        </div>

        <div style={{ fontSize: 11, color: mc.dim, fontWeight: 600, marginBottom: 6 }}>RECENT CALLS</div>
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 10, padding: 10 }}>
          {[
            { time: "10:15", type: "outgoing", num: "****5678", dur: "3m", c: mc.blue },
            { time: "09:42", type: "incoming", num: "****1234", dur: "1m 15s", c: mc.green },
            { time: "09:10", type: "missed", num: "****9012", dur: "—", c: mc.critical },
          ].map((c, i) => (
            <div key={i} style={{ display: "flex", alignItems: "center", gap: 8, padding: "5px 0", borderBottom: i < 2 ? `1px solid ${mc.border}` : "none" }}>
              <span style={{ fontSize: 10, color: mc.muted, width: 36 }}>{c.time}</span>
              <span style={{ fontSize: 9, fontWeight: 600, color: c.c, width: 55 }}>{c.type}</span>
              <span style={{ fontSize: 10, color: mc.dim, fontFamily: "monospace", flex: 1 }}>{c.num}</span>
              <span style={{ fontSize: 10, color: mc.dim }}>{c.dur}</span>
            </div>
          ))}
        </div>

        <div style={{ fontSize: 11, color: mc.dim, fontWeight: 600, marginTop: 10, marginBottom: 6 }}>SYNC STATUS</div>
        <div style={{ background: mc.card, border: `1px solid ${mc.green}30`, borderRadius: 10, padding: 10 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: "50%", background: mc.green }} />
            <span style={{ fontSize: 11, color: mc.green, fontWeight: 500 }}>All data synced</span>
          </div>
          <div style={{ fontSize: 10, color: mc.muted, marginTop: 4 }}>Last: 2 min ago · Next: ~28 min</div>
        </div>
      </div>
    </Phone>
  );
}

// ═══ SCREEN 5: OTA Update Notification ═══
function OtaUpdate() {
  return (
    <Phone title="SudarshanChakra" subtitle="Update Available" navBar={<KioskNavBar active="settings" />}>
      <div style={{ flex: 1, padding: 10, display: "flex", flexDirection: "column" }}>
        <div style={{ flex: 1 }}>
          {/* Existing dashboard content faded behind */}
          <div style={{ opacity: 0.3, fontSize: 11, color: mc.dim, padding: 10 }}>
            Dashboard content (faded)...
          </div>
        </div>
        {/* OTA update dialog */}
        <div style={{ background: mc.card, border: `2px solid ${mc.accent}`, borderRadius: 16, padding: 16, marginBottom: 8 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
            <div style={{ width: 36, height: 36, borderRadius: 8, background: `${mc.accent}15`, display: "flex", alignItems: "center", justifyContent: "center", color: mc.accent, fontWeight: 700, fontSize: 14 }}>SC</div>
            <div>
              <div style={{ fontSize: 13, fontWeight: 700, color: mc.text }}>Update Available</div>
              <div style={{ fontSize: 11, color: mc.dim }}>v2.2.0 (15 MB)</div>
            </div>
          </div>
          <div style={{ fontSize: 11, color: mc.dim, marginBottom: 10, lineHeight: 1.5 }}>
            Bug fixes and security improvements. Camera PTZ stability, faster alert notifications.
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 10 }}>
            {/* Progress bar */}
            <div style={{ flex: 1, height: 6, background: mc.surface, borderRadius: 3 }}>
              <div style={{ width: "65%", height: "100%", background: mc.accent, borderRadius: 3 }} />
            </div>
            <span style={{ fontSize: 10, color: mc.dim }}>65%</span>
          </div>
          <div style={{ fontSize: 10, color: mc.muted, textAlign: "center" }}>Installing silently... App will restart automatically.</div>
        </div>
      </div>
    </Phone>
  );
}

// ═══ MAIN APP ═══
export default function MdmAndroidMockups() {
  const [screen, setScreen] = useState("kiosk_home");
  const screens = [
    { id: "kiosk_home", label: "Kiosk Home" },
    { id: "kiosk_locked", label: "Locked State" },
    { id: "settings", label: "Settings + MDM" },
    { id: "telemetry", label: "Usage Detail" },
    { id: "ota", label: "OTA Update" },
  ];

  return (
    <div style={{ minHeight: "100vh", background: "#1a1a2e", color: "#e0dcd4", fontFamily: "'DM Sans', system-ui, sans-serif", padding: 0 }}>
      <link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&display=swap" rel="stylesheet" />
      <div style={{ borderBottom: "1px solid #2a2a3e", padding: "12px 20px", display: "flex", alignItems: "center", gap: 10 }}>
        <div style={{ width: 28, height: 28, borderRadius: 6, background: `linear-gradient(135deg, ${mc.accent}, ${mc.orange})`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, color: "#fff" }}>SC</div>
        <span style={{ fontSize: 14, fontWeight: 600 }}>MDM Android Kiosk — Mockup Screens</span>
      </div>
      <div style={{ display: "flex", gap: 4, padding: "10px 20px", borderBottom: "1px solid #2a2a3e", overflowX: "auto" }}>
        {screens.map(s => (
          <button key={s.id} onClick={() => setScreen(s.id)} style={{ background: screen === s.id ? "#2a2a3e" : "transparent", color: screen === s.id ? "#e0dcd4" : "#64748b", border: `1px solid ${screen === s.id ? "#3a3a4e" : "transparent"}`, borderRadius: 8, padding: "6px 14px", fontSize: 12, fontWeight: 500, cursor: "pointer", whiteSpace: "nowrap" }}>{s.label}</button>
        ))}
      </div>
      <div style={{ padding: 30, display: "flex", justifyContent: "center" }}>
        {screen === "kiosk_home" && <KioskHome />}
        {screen === "kiosk_locked" && <KioskLocked />}
        {screen === "settings" && <KioskSettings />}
        {screen === "telemetry" && <TelemetryStatus />}
        {screen === "ota" && <OtaUpdate />}
      </div>
    </div>
  );
}
