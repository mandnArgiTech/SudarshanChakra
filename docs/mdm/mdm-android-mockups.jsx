import { useState } from "react";

/*
 * SudarshanChakra MDM — Android Kiosk Mockup Screens (REVISED)
 * 
 * Changes from v1:
 *   - REMOVED: Usage Detail screen (not needed)
 *   - REMOVED: Device Management section from Settings
 *   - REMOVED: Allowed Apps section from Settings
 *   - REMOVED: "KIOSK MODE ACTIVE" text from lock state
 *   - REMOVED: Cameras tab from kiosk nav (MDM users don't get cameras)
 *   - REMOVED: Water tab from kiosk nav (MDM users don't get water)
 *   - REMOVED: "ALLOWED APPS" label from app grid
 *   - Kiosk home shows only restricted alerts (worker's assigned zone alerts)
 *   - Lock state is a clean, branded screen — no kiosk indicators
 *   - Settings shows only: user info, server connection, sync status, version (escape hatch)
 *   - Bottom app row has no label, just the icons (natural, not prison-like)
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
      <div style={{ background: mc.bg, borderRadius: 26, overflow: "hidden", minHeight: 520, display: "flex", flexDirection: "column", position: "relative" }}>
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

// ═══ SCREEN 1: Kiosk Home — Restricted alerts + app grid ═══
function KioskHome() {
  return (
    <Phone title="SudarshanChakra" navBar={<KioskNavBar active="alerts" />}>
      <div style={{ flex: 1, padding: 10 }}>
        {/* Restricted alerts for this worker's assigned zones only */}
        {[
          { pri: "critical", text: "Snake near storage", time: "2 min ago", zone: "Storage Perimeter" },
          { pri: "high", text: "Person at east gate", time: "15 min ago", zone: "East Gate" },
          { pri: "warning", text: "Cow left pen area", time: "1 hr ago", zone: "Livestock Pen" },
        ].map((a, i) => (
          <div key={i} style={{ background: mc.card, border: `1px solid ${a.pri === "critical" ? mc.critical + "40" : mc.border}`, borderRadius: 10, padding: "10px 12px", marginBottom: 8, borderLeft: `3px solid ${a.pri === "critical" ? mc.critical : a.pri === "high" ? mc.orange : mc.amber}` }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: a.pri === "critical" ? mc.critical : mc.text }}>{a.text}</span>
              <span style={{ fontSize: 9, color: mc.muted }}>{a.time}</span>
            </div>
            <span style={{ fontSize: 10, color: mc.dim }}>{a.zone}</span>
          </div>
        ))}
      </div>

      {/* App grid — no label, just icons. Feels like a normal phone */}
      <div style={{ background: mc.surface, borderTop: `1px solid ${mc.border}`, padding: "12px 24px 14px" }}>
        <div style={{ display: "flex", justifyContent: "space-around" }}>
          {[
            { name: "WhatsApp", color: "#25D366", icon: "W" },
            { name: "YouTube", color: "#FF0000", icon: "Y" },
            { name: "Maps", color: "#4285F4", icon: "M" },
            { name: "Phone", color: "#34A853", icon: "P" },
          ].map(app => (
            <div key={app.name} style={{ textAlign: "center", cursor: "pointer" }}>
              <div style={{ width: 44, height: 44, borderRadius: 12, background: app.color, display: "flex", alignItems: "center", justifyContent: "center", color: "#fff", fontWeight: 700, fontSize: 18 }}>{app.icon}</div>
              <div style={{ fontSize: 9, color: mc.dim, marginTop: 4 }}>{app.name}</div>
            </div>
          ))}
        </div>
      </div>
    </Phone>
  );
}

// ═══ SCREEN 2: Lock State — Clean branded screen, no kiosk hints ═══
function LockState() {
  return (
    <Phone title="" statusBar={false}>
      <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: 30, background: mc.bg }}>
        <div style={{ width: 72, height: 72, borderRadius: 20, background: `linear-gradient(135deg, ${mc.accent}, ${mc.orange})`, display: "flex", alignItems: "center", justifyContent: "center", marginBottom: 20 }}>
          <span style={{ color: "#fff", fontSize: 24, fontWeight: 700 }}>SC</span>
        </div>
        <div style={{ fontSize: 18, fontWeight: 700, color: mc.text, marginBottom: 4 }}>SudarshanChakra</div>
        <div style={{ fontSize: 12, color: mc.dim }}>Farm Security System</div>
      </div>
    </Phone>
  );
}

// ═══ SCREEN 3: Settings — Clean, minimal. Only what the worker needs. ═══
function KioskSettings() {
  const [tapCount, setTapCount] = useState(0);
  const [showEscape, setShowEscape] = useState(false);

  return (
    <Phone title="Settings" navBar={<KioskNavBar active="settings" />}>
      <div style={{ flex: 1, padding: 10, overflow: "auto" }}>
        {/* User info */}
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 12, padding: 14, marginBottom: 10 }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: mc.text }}>Ramesh Kumar</div>
          <div style={{ fontSize: 12, color: mc.dim, marginTop: 2 }}>Operator · Sanga Reddy Farm</div>
        </div>

        {/* Server connection */}
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 12, padding: 14, marginBottom: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: mc.text, marginBottom: 8 }}>Connection</div>
          <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: "50%", background: mc.green }} />
            <span style={{ fontSize: 12, color: mc.green }}>Connected</span>
          </div>
          <div style={{ fontSize: 11, color: mc.muted }}>vivasvan-tech.in</div>
        </div>

        {/* Sync status */}
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 12, padding: 14, marginBottom: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: mc.text, marginBottom: 8 }}>Sync</div>
          <div style={{ fontSize: 12, color: mc.dim }}>Last synced: 12 min ago</div>
          <div style={{ marginTop: 8, background: mc.accent, color: "#fff", borderRadius: 8, padding: "8px 0", textAlign: "center", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Sync Now</div>
        </div>

        {/* Version — 7 tap escape hatch */}
        <div
          style={{ textAlign: "center", padding: "12px 0", cursor: "pointer" }}
          onClick={() => {
            const next = tapCount + 1;
            setTapCount(next);
            if (next >= 7) { setShowEscape(true); setTapCount(0); }
          }}
        >
          <span style={{ fontSize: 12, color: mc.muted }}>v2.1.0 (build 87)</span>
          {tapCount > 2 && tapCount < 7 && (
            <div style={{ fontSize: 9, color: mc.muted, marginTop: 2 }}>{7 - tapCount} more</div>
          )}
        </div>
      </div>

      {/* Escape dialog */}
      {showEscape && (
        <div style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 10 }}>
          <div style={{ background: mc.card, borderRadius: 16, padding: 20, width: 220 }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: mc.text, marginBottom: 12 }}>Admin Access</div>
            <div style={{ display: "flex", justifyContent: "center", gap: 8, marginBottom: 16 }}>
              {[1,2,3,4].map(i => (
                <div key={i} style={{ width: 36, height: 36, borderRadius: 8, border: `2px solid ${mc.border}`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18, color: mc.text }}>*</div>
              ))}
            </div>
            <div style={{ display: "grid", gap: 8 }}>
              <div style={{ background: `${mc.orange}15`, color: mc.orange, border: `1px solid ${mc.orange}40`, borderRadius: 8, padding: "10px 0", textAlign: "center", fontSize: 12, fontWeight: 600 }}>Exit Kiosk</div>
              <div style={{ background: `${mc.critical}15`, color: mc.critical, border: `1px solid ${mc.critical}40`, borderRadius: 8, padding: "10px 0", textAlign: "center", fontSize: 12, fontWeight: 600 }}>Decommission</div>
              <div style={{ background: mc.surface, color: mc.dim, borderRadius: 8, padding: "10px 0", textAlign: "center", fontSize: 12, cursor: "pointer" }} onClick={() => setShowEscape(false)}>Cancel</div>
            </div>
          </div>
        </div>
      )}
    </Phone>
  );
}

// ═══ SCREEN 4: OTA Silent Update ═══
function OtaUpdate() {
  return (
    <Phone title="SudarshanChakra" navBar={<KioskNavBar active="alerts" />}>
      <div style={{ flex: 1, padding: 10, display: "flex", flexDirection: "column" }}>
        <div style={{ flex: 1, opacity: 0.3, padding: 8 }}>
          <div style={{ background: mc.card, borderRadius: 10, padding: 10, marginBottom: 6, borderLeft: `3px solid ${mc.critical}` }}>
            <span style={{ fontSize: 12, color: mc.dim }}>Snake near storage...</span>
          </div>
          <div style={{ background: mc.card, borderRadius: 10, padding: 10, borderLeft: `3px solid ${mc.orange}` }}>
            <span style={{ fontSize: 12, color: mc.dim }}>Person at east gate...</span>
          </div>
        </div>
        <div style={{ background: mc.card, border: `2px solid ${mc.accent}`, borderRadius: 16, padding: 16, marginBottom: 8 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 10 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: `linear-gradient(135deg, ${mc.accent}, ${mc.orange})`, display: "flex", alignItems: "center", justifyContent: "center", color: "#fff", fontWeight: 700, fontSize: 14 }}>SC</div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 700, color: mc.text }}>Updating...</div>
              <div style={{ fontSize: 11, color: mc.dim }}>v2.2.0 · 15 MB</div>
            </div>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <div style={{ flex: 1, height: 6, background: mc.surface, borderRadius: 3 }}>
              <div style={{ width: "65%", height: "100%", background: mc.accent, borderRadius: 3 }} />
            </div>
            <span style={{ fontSize: 10, color: mc.dim }}>65%</span>
          </div>
          <div style={{ fontSize: 10, color: mc.muted, textAlign: "center", marginTop: 6 }}>App will restart when done</div>
        </div>
      </div>
    </Phone>
  );
}

// ═══ MAIN ═══
export default function MdmAndroidMockups() {
  const [screen, setScreen] = useState("home");
  const screens = [
    { id: "home", label: "Kiosk Home" },
    { id: "lock", label: "Lock Screen" },
    { id: "settings", label: "Settings" },
    { id: "ota", label: "OTA Update" },
  ];

  return (
    <div style={{ minHeight: "100vh", background: "#1a1a2e", color: "#e0dcd4", fontFamily: "'DM Sans', system-ui, sans-serif" }}>
      <link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&display=swap" rel="stylesheet" />
      <div style={{ borderBottom: "1px solid #2a2a3e", padding: "12px 20px", display: "flex", alignItems: "center", gap: 10 }}>
        <div style={{ width: 28, height: 28, borderRadius: 6, background: `linear-gradient(135deg, ${mc.accent}, ${mc.orange})`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, color: "#fff" }}>SC</div>
        <span style={{ fontSize: 14, fontWeight: 600 }}>MDM Android — Mockup Screens</span>
      </div>
      <div style={{ display: "flex", gap: 4, padding: "10px 20px", borderBottom: "1px solid #2a2a3e" }}>
        {screens.map(s => (
          <button key={s.id} onClick={() => setScreen(s.id)} style={{ background: screen === s.id ? "#2a2a3e" : "transparent", color: screen === s.id ? "#e0dcd4" : "#64748b", border: `1px solid ${screen === s.id ? "#3a3a4e" : "transparent"}`, borderRadius: 8, padding: "6px 14px", fontSize: 12, fontWeight: 500, cursor: "pointer" }}>{s.label}</button>
        ))}
      </div>
      <div style={{ padding: 30, display: "flex", justifyContent: "center" }}>
        {screen === "home" && <KioskHome />}
        {screen === "lock" && <LockState />}
        {screen === "settings" && <KioskSettings />}
        {screen === "ota" && <OtaUpdate />}
      </div>
    </div>
  );
}
