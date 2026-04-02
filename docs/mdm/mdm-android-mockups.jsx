import { useState } from "react";

const mc = {
  bg: "#F8F7F4", card: "#FFFFFF", text: "#2D2A26", dim: "#6B6560",
  muted: "#9E9891", accent: "#C8553D", accentDark: "#B04530",
  green: "#4A8C5C", blue: "#3A8ACC", orange: "#D4832F",
  amber: "#B8962E", border: "#E8E5E0", surface: "#F0EDE8",
  navBg: "#FFFFFF", navInactive: "#ADA8A2",
  critical: "#C8553D", success: "#4A8C5C",
};

function StatusBar({ time = "10:32 AM", battery = 78, wifi = true, network = "4G" }) {
  return (
    <div style={{ background: "rgba(0,0,0,0.08)", padding: "4px 14px", display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: 10 }}>
      <span style={{ fontWeight: 600, color: mc.text }}>{time}</span>
      <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
        {network && <span style={{ color: mc.dim, fontWeight: 500 }}>{network}</span>}
        {wifi && (
          <svg width="12" height="10" viewBox="0 0 12 10" fill="none">
            <path d="M6 9a1 1 0 110-2 1 1 0 010 2z" fill={mc.green}/>
            <path d="M3.5 6.5a3.5 3.5 0 015 0" stroke={mc.green} strokeWidth="1.2" strokeLinecap="round" fill="none"/>
            <path d="M1.5 4.2a6 6 0 019 0" stroke={mc.green} strokeWidth="1.2" strokeLinecap="round" fill="none"/>
          </svg>
        )}
        {!wifi && (
          <svg width="12" height="10" viewBox="0 0 12 10" fill="none">
            <path d="M6 9a1 1 0 110-2 1 1 0 010 2z" fill={mc.muted}/>
            <path d="M1.5 4.2a6 6 0 019 0" stroke={mc.muted} strokeWidth="1.2" strokeLinecap="round" fill="none" strokeDasharray="2 2"/>
          </svg>
        )}
        <div style={{ display: "flex", alignItems: "flex-end", gap: 1, height: 10 }}>
          <div style={{ width: 3, height: 10, borderRadius: 1, border: `1px solid ${battery > 20 ? mc.green : mc.critical}`, position: "relative", overflow: "hidden" }}>
            <div style={{ position: "absolute", bottom: 0, width: "100%", height: `${battery}%`, background: battery > 20 ? mc.green : mc.critical }} />
          </div>
          <span style={{ fontSize: 9, color: battery > 20 ? mc.dim : mc.critical }}>{battery}%</span>
        </div>
      </div>
    </div>
  );
}

function Phone({ title, children, statusBar = true, battery = 78, wifi = true, network = "4G", navBar = null, bgImage = null }) {
  return (
    <div style={{ width: 260, background: "#111", borderRadius: 32, padding: "8px 7px", boxShadow: "0 8px 30px rgba(0,0,0,0.4)" }}>
      <div style={{ background: bgImage ? undefined : mc.bg, backgroundImage: bgImage ? `url(${bgImage})` : undefined, backgroundSize: "cover", backgroundPosition: "center", borderRadius: 26, overflow: "hidden", minHeight: 520, display: "flex", flexDirection: "column", position: "relative" }}>
        {bgImage && <div style={{ position: "absolute", inset: 0, background: "rgba(248,247,244,0.85)", borderRadius: 26 }} />}
        <div style={{ position: "relative", zIndex: 1, display: "flex", flexDirection: "column", flex: 1 }}>
          {statusBar && <StatusBar battery={battery} wifi={wifi} network={network} />}
          {title && (
            <div style={{ background: mc.accent, padding: "6px 14px" }}>
              <span style={{ fontSize: 11, color: "#fff", fontWeight: 600 }}>{title}</span>
            </div>
          )}
          <div style={{ flex: 1, display: "flex", flexDirection: "column" }}>
            {children}
          </div>
          {navBar}
        </div>
      </div>
    </div>
  );
}

function KioskNavBar({ active }) {
  return (
    <div style={{ display: "flex", borderTop: `1px solid ${mc.border}`, background: mc.navBg, padding: "6px 4px 8px" }}>
      {[{ id: "alerts", label: "Alerts", icon: "!" }, { id: "settings", label: "Settings", icon: "⚙" }].map(t => (
        <div key={t.id} style={{ flex: 1, textAlign: "center", cursor: "pointer" }}>
          <div style={{ fontSize: 16, color: t.id === active ? mc.accent : mc.navInactive }}>{t.icon}</div>
          <div style={{ fontSize: 9, fontWeight: t.id === active ? 600 : 400, color: t.id === active ? mc.accent : mc.navInactive, marginTop: 1 }}>{t.label}</div>
        </div>
      ))}
    </div>
  );
}

// ═══ SCREEN 1: Kiosk Home ═══
function KioskHome() {
  return (
    <Phone title="SudarshanChakra" battery={78} wifi={true} network="4G" navBar={<KioskNavBar active="alerts" />}>
      <div style={{ flex: 1, padding: 10 }}>
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
      <div style={{ background: mc.surface, borderTop: `1px solid ${mc.border}`, padding: "12px 24px 14px" }}>
        <div style={{ display: "flex", justifyContent: "space-around" }}>
          {[
            { name: "WhatsApp", color: "#25D366", icon: "W" },
            { name: "YouTube", color: "#FF0000", icon: "Y" },
            { name: "Maps", color: "#4285F4", icon: "M" },
            { name: "Camera", color: "#4285F4", icon: "C" },
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

// ═══ SCREEN 2: Lock Screen — clean branded, background image option ═══
function LockScreen() {
  return (
    <Phone title="" statusBar={true} battery={65} wifi={true} network="4G" bgImage="https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=400&q=60">
      <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: 30 }}>
        <div style={{ width: 72, height: 72, borderRadius: 20, background: `linear-gradient(135deg, ${mc.accent}, ${mc.orange})`, display: "flex", alignItems: "center", justifyContent: "center", marginBottom: 20, boxShadow: "0 4px 16px rgba(0,0,0,0.15)" }}>
          <span style={{ color: "#fff", fontSize: 24, fontWeight: 700 }}>SC</span>
        </div>
        <div style={{ fontSize: 18, fontWeight: 700, color: mc.text, marginBottom: 4 }}>SudarshanChakra</div>
        <div style={{ fontSize: 12, color: mc.dim }}>Farm Security System</div>
      </div>
    </Phone>
  );
}

// ═══ SCREEN 3: Settings ═══
function KioskSettings() {
  const [tapCount, setTapCount] = useState(0);
  const [showEscape, setShowEscape] = useState(false);

  return (
    <Phone title="Settings" battery={78} wifi={true} network="4G" navBar={<KioskNavBar active="settings" />}>
      <div style={{ flex: 1, padding: 10, overflow: "auto" }}>
        {/* User info */}
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 12, padding: 14, marginBottom: 10 }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: mc.text }}>Ramesh Kumar</div>
          <div style={{ fontSize: 12, color: mc.dim, marginTop: 2 }}>Operator · Sanga Reddy Farm</div>
        </div>

        {/* Device status — battery, wifi, network, sync in one card */}
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 12, padding: 14, marginBottom: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: mc.text, marginBottom: 10 }}>Device Status</div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <div style={{ width: 18, height: 10, borderRadius: 2, border: `1.5px solid ${mc.green}`, position: "relative", overflow: "hidden" }}>
                <div style={{ position: "absolute", bottom: 0, width: "100%", height: "78%", background: mc.green }} />
              </div>
              <span style={{ fontSize: 12, color: mc.text }}>78%</span>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <svg width="14" height="11" viewBox="0 0 12 10" fill="none">
                <path d="M6 9a1 1 0 110-2 1 1 0 010 2z" fill={mc.green}/>
                <path d="M3.5 6.5a3.5 3.5 0 015 0" stroke={mc.green} strokeWidth="1.2" strokeLinecap="round" fill="none"/>
                <path d="M1.5 4.2a6 6 0 019 0" stroke={mc.green} strokeWidth="1.2" strokeLinecap="round" fill="none"/>
              </svg>
              <span style={{ fontSize: 12, color: mc.text }}>Wi-Fi Connected</span>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <svg width="14" height="12" viewBox="0 0 14 12" fill="none">
                <rect x="1" y="1" width="3" height="10" rx="1" fill={mc.green}/>
                <rect x="5.5" y="3" width="3" height="8" rx="1" fill={mc.green}/>
                <rect x="10" y="5" width="3" height="6" rx="1" fill={mc.green}/>
              </svg>
              <span style={{ fontSize: 12, color: mc.text }}>4G</span>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <span style={{ width: 8, height: 8, borderRadius: "50%", background: mc.green }} />
              <span style={{ fontSize: 12, color: mc.green }}>MQTT Online</span>
            </div>
          </div>
          <div style={{ borderTop: `1px solid ${mc.border}`, marginTop: 10, paddingTop: 8 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <div style={{ fontSize: 11, color: mc.dim }}>Auto-sync: every 30 min</div>
                <div style={{ fontSize: 11, color: mc.muted }}>Last synced: 12 min ago</div>
              </div>
              <span style={{ width: 8, height: 8, borderRadius: "50%", background: mc.green }} />
            </div>
          </div>
        </div>

        {/* Wallpaper setting */}
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 12, padding: 14, marginBottom: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: mc.text, marginBottom: 8 }}>Wallpaper</div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <div style={{ cursor: "pointer" }}>
              <div style={{ fontSize: 11, color: mc.dim, marginBottom: 4 }}>App background</div>
              <div style={{ height: 48, borderRadius: 8, background: `linear-gradient(135deg, #E8E5E0, #F0EDE8)`, border: `1px solid ${mc.border}`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                <span style={{ fontSize: 10, color: mc.muted }}>Change</span>
              </div>
            </div>
            <div style={{ cursor: "pointer" }}>
              <div style={{ fontSize: 11, color: mc.dim, marginBottom: 4 }}>Lock screen</div>
              <div style={{ height: 48, borderRadius: 8, background: "linear-gradient(135deg, #a8c686, #6d9f5a)", border: `1px solid ${mc.border}`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                <span style={{ fontSize: 10, color: "#fff" }}>Change</span>
              </div>
            </div>
          </div>
        </div>

        {/* Server */}
        <div style={{ background: mc.card, border: `1px solid ${mc.border}`, borderRadius: 12, padding: 14, marginBottom: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: mc.text, marginBottom: 6 }}>Server</div>
          <div style={{ fontSize: 12, color: mc.dim }}>vivasvan-tech.in</div>
        </div>

        {/* Version — 7 tap escape */}
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

// ═══ SCREEN 4: OTA Update ═══
function OtaUpdate() {
  return (
    <Phone title="SudarshanChakra" battery={72} wifi={true} network="4G" navBar={<KioskNavBar active="alerts" />}>
      <div style={{ flex: 1, padding: 10, display: "flex", flexDirection: "column" }}>
        <div style={{ flex: 1, opacity: 0.3, padding: 8 }}>
          <div style={{ background: mc.card, borderRadius: 10, padding: 10, marginBottom: 6, borderLeft: `3px solid ${mc.critical}` }}>
            <span style={{ fontSize: 12, color: mc.dim }}>Snake near storage...</span>
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
        {screen === "lock" && <LockScreen />}
        {screen === "settings" && <KioskSettings />}
        {screen === "ota" && <OtaUpdate />}
      </div>
    </div>
  );
}
