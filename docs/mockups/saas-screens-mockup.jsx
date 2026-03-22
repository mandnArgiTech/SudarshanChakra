import { useState } from "react";

const plans = {
  full: { label: "Full Platform", modules: ["alerts","cameras","sirens","water","pumps","zones","devices","workers","analytics"], color: "#4a8c5c" },
  water_only: { label: "Water & Pumps", modules: ["water","pumps","alerts"], color: "#3a8acc" },
  security: { label: "Farm Security", modules: ["alerts","cameras","sirens","zones","devices"], color: "#c8553d" },
  monitoring: { label: "Monitoring", modules: ["alerts","cameras","water","analytics"], color: "#d4832f" },
};

const roles = ["super_admin","admin","manager","operator","viewer"];

const allModules = [
  { id: "alerts", label: "Alerts", icon: "⚠" },
  { id: "cameras", label: "Cameras", icon: "◉" },
  { id: "sirens", label: "Sirens", icon: "◈" },
  { id: "water", label: "Water", icon: "◆" },
  { id: "pumps", label: "Pumps", icon: "⟳" },
  { id: "zones", label: "Zones", icon: "▣" },
  { id: "devices", label: "Devices", icon: "▢" },
  { id: "workers", label: "Workers", icon: "◎" },
  { id: "analytics", label: "Analytics", icon: "▥" },
];

const farms = [
  { id: "f1", name: "Sanga Reddy Farm", slug: "sanga-reddy", plan: "full", status: "active", users: 3, cameras: 8, nodes: 2 },
  { id: "f2", name: "AquaFarm Solutions", slug: "aquafarm", plan: "water_only", status: "active", users: 2, cameras: 0, nodes: 0 },
  { id: "f3", name: "Green Valley Estate", slug: "green-valley", plan: "security", status: "trial", users: 5, cameras: 4, nodes: 1 },
];

const users = [
  { id: "u1", name: "Devi Prasad", username: "deviprasad", role: "super_admin", farm: "Sanga Reddy Farm", active: true, lastLogin: "2 hours ago" },
  { id: "u2", name: "Ramesh Kumar", username: "ramesh", role: "manager", farm: "Sanga Reddy Farm", active: true, lastLogin: "1 day ago" },
  { id: "u3", name: "Suresh Reddy", username: "suresh", role: "operator", farm: "Sanga Reddy Farm", active: true, lastLogin: "3 hours ago" },
  { id: "u4", name: "Priya Sharma", username: "priya", role: "viewer", farm: "Sanga Reddy Farm", active: false, lastLogin: "30 days ago" },
  { id: "u5", name: "Ravi Aqua", username: "ravi_aqua", role: "admin", farm: "AquaFarm Solutions", active: true, lastLogin: "5 hours ago" },
];

const auditLogs = [
  { time: "14:32:10", user: "deviprasad", action: "siren.trigger", entity: "Siren Node-A", detail: "Manual trigger from Android" },
  { time: "14:28:45", user: "ramesh", action: "zone.create", entity: "Cow Zone B", detail: "livestock_containment, cam-02" },
  { time: "14:15:22", user: "suresh", action: "alert.acknowledge", entity: "ALT-4892", detail: "Snake alert, cam-05" },
  { time: "13:58:01", user: "system", action: "alert.create", entity: "ALT-4892", detail: "snake detected, critical, 78%" },
  { time: "13:45:30", user: "ravi_aqua", action: "pump.start", entity: "Motor-1", detail: "Manual start from dashboard" },
  { time: "13:30:00", user: "system", action: "water.critical", entity: "Tank-1", detail: "Level dropped to 8%" },
  { time: "12:00:00", user: "deviprasad", action: "user.create", entity: "suresh", detail: "Role: operator" },
  { time: "11:30:00", user: "deviprasad", action: "farm.update", entity: "AquaFarm", detail: "Plan: water_only → active" },
];

const C = {
  bg: "#0e1118", surface: "#161820", surfaceAlt: "#1c1e28", border: "#252830",
  text: "#e0dcd4", textDim: "#8a8680", textMuted: "#5a564e",
  accent: "#c8553d", accentDim: "#983d2d", gold: "#d4a530",
  green: "#4a8c5c", blue: "#3a8acc", red: "#c8553d", amber: "#d4832f",
};

function Badge({ children, color }) {
  return <span style={{ background: `${color}22`, color, border: `1px solid ${color}44`, borderRadius: 6, padding: "2px 10px", fontSize: 11, fontWeight: 600, letterSpacing: 0.5 }}>{children}</span>;
}

function StatusDot({ active }) {
  return <span style={{ display: "inline-block", width: 8, height: 8, borderRadius: "50%", background: active ? C.green : C.textMuted, marginRight: 6 }} />;
}

function Card({ children, style }) {
  return <div style={{ background: C.surface, border: `1px solid ${C.border}`, borderRadius: 10, padding: 16, ...style }}>{children}</div>;
}

function SectionTitle({ children }) {
  return <div style={{ fontSize: 11, fontWeight: 600, color: C.textMuted, letterSpacing: 2, textTransform: "uppercase", marginBottom: 12, marginTop: 20 }}>{children}</div>;
}

// ══════════════════════════════════════════════════════════════════
// SCREEN 1: Super Admin — Farm Management
// ══════════════════════════════════════════════════════════════════
function FarmManagement() {
  const [selected, setSelected] = useState(null);
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <div style={{ fontSize: 18, fontWeight: 600, color: C.text }}>Farm Management</div>
          <div style={{ fontSize: 12, color: C.textDim }}>Super Admin — manage all tenant farms</div>
        </div>
        <button style={{ background: C.accent, color: "#fff", border: "none", borderRadius: 8, padding: "8px 20px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>+ New Farm</button>
      </div>
      <div style={{ display: "grid", gap: 10 }}>
        {farms.map(f => (
          <Card key={f.id} style={{ cursor: "pointer", borderColor: selected === f.id ? C.accent : C.border, transition: "border-color 0.15s" }} onClick={() => setSelected(f.id === selected ? null : f.id)}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <div style={{ width: 40, height: 40, borderRadius: 8, background: `${plans[f.plan].color}22`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18, color: plans[f.plan].color, fontWeight: 700 }}>
                  {f.name[0]}
                </div>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: C.text }}>{f.name}</div>
                  <div style={{ fontSize: 11, color: C.textDim }}>{f.slug}.sudarshanchakra.com</div>
                </div>
              </div>
              <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                <Badge color={plans[f.plan].color}>{plans[f.plan].label}</Badge>
                <Badge color={f.status === "active" ? C.green : C.amber}>{f.status}</Badge>
              </div>
            </div>
            <div style={{ display: "flex", gap: 24, marginTop: 12, fontSize: 12, color: C.textDim }}>
              <span>{f.users} users</span>
              <span>{f.cameras} cameras</span>
              <span>{f.nodes} nodes</span>
            </div>
            {selected === f.id && (
              <div style={{ marginTop: 12, paddingTop: 12, borderTop: `1px solid ${C.border}` }}>
                <div style={{ fontSize: 11, color: C.textMuted, marginBottom: 8 }}>Enabled modules:</div>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
                  {allModules.map(m => {
                    const enabled = plans[f.plan].modules.includes(m.id);
                    return (
                      <span key={m.id} style={{ padding: "3px 10px", borderRadius: 6, fontSize: 11, background: enabled ? `${C.green}22` : `${C.textMuted}11`, color: enabled ? C.green : C.textMuted, border: `1px solid ${enabled ? C.green + "44" : C.textMuted + "22"}` }}>
                        {m.icon} {m.label}
                      </span>
                    );
                  })}
                </div>
                <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
                  <button style={{ background: C.surfaceAlt, color: C.text, border: `1px solid ${C.border}`, borderRadius: 6, padding: "6px 14px", fontSize: 12, cursor: "pointer" }}>Edit Farm</button>
                  <button style={{ background: C.surfaceAlt, color: C.text, border: `1px solid ${C.border}`, borderRadius: 6, padding: "6px 14px", fontSize: 12, cursor: "pointer" }}>Manage Users</button>
                  <button style={{ background: C.surfaceAlt, color: C.amber, border: `1px solid ${C.amber}33`, borderRadius: 6, padding: "6px 14px", fontSize: 12, cursor: "pointer" }}>
                    {f.status === "active" ? "Suspend" : "Activate"}
                  </button>
                </div>
              </div>
            )}
          </Card>
        ))}
      </div>
    </div>
  );
}

// ══════════════════════════════════════════════════════════════════
// SCREEN 2: User Management
// ══════════════════════════════════════════════════════════════════
function UserManagement() {
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <div style={{ fontSize: 18, fontWeight: 600, color: C.text }}>User Management</div>
          <div style={{ fontSize: 12, color: C.textDim }}>Manage users, roles, and module access</div>
        </div>
        <button style={{ background: C.accent, color: "#fff", border: "none", borderRadius: 8, padding: "8px 20px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>+ Add User</button>
      </div>
      <div style={{ display: "grid", gap: 8 }}>
        {users.map(u => {
          const roleColor = { super_admin: C.red, admin: C.amber, manager: C.blue, operator: C.green, viewer: C.textDim }[u.role];
          return (
            <Card key={u.id}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <StatusDot active={u.active} />
                  <div>
                    <div style={{ fontSize: 14, fontWeight: 500, color: u.active ? C.text : C.textMuted }}>{u.name}</div>
                    <div style={{ fontSize: 11, color: C.textDim }}>@{u.username} · {u.farm}</div>
                  </div>
                </div>
                <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  <Badge color={roleColor}>{u.role.replace("_", " ")}</Badge>
                  <span style={{ fontSize: 11, color: C.textMuted }}>{u.lastLogin}</span>
                  <button style={{ background: "none", border: `1px solid ${C.border}`, borderRadius: 6, padding: "4px 10px", fontSize: 11, color: C.textDim, cursor: "pointer" }}>Edit</button>
                </div>
              </div>
            </Card>
          );
        })}
      </div>
    </div>
  );
}

// ══════════════════════════════════════════════════════════════════
// SCREEN 3: Audit Log
// ══════════════════════════════════════════════════════════════════
function AuditLog() {
  const [filter, setFilter] = useState("all");
  const actionColor = { "siren.trigger": C.red, "zone.create": C.blue, "alert.acknowledge": C.green, "alert.create": C.amber, "pump.start": C.blue, "water.critical": C.red, "user.create": C.green, "farm.update": C.amber };
  return (
    <div>
      <div style={{ fontSize: 18, fontWeight: 600, color: C.text, marginBottom: 4 }}>Audit Log</div>
      <div style={{ fontSize: 12, color: C.textDim, marginBottom: 16 }}>Every action tracked for compliance and security</div>
      <div style={{ display: "flex", gap: 6, marginBottom: 16 }}>
        {["all","siren","zone","alert","pump","water","user","farm"].map(f => (
          <button key={f} onClick={() => setFilter(f)} style={{ background: filter === f ? C.surfaceAlt : "transparent", color: filter === f ? C.text : C.textMuted, border: `1px solid ${filter === f ? C.border : "transparent"}`, borderRadius: 6, padding: "4px 12px", fontSize: 11, cursor: "pointer", textTransform: "capitalize" }}>{f}</button>
        ))}
      </div>
      <div style={{ display: "grid", gap: 4 }}>
        {auditLogs.filter(l => filter === "all" || l.action.startsWith(filter)).map((l, i) => (
          <div key={i} style={{ display: "grid", gridTemplateColumns: "70px 90px 140px 1fr", gap: 12, padding: "8px 12px", background: i % 2 === 0 ? C.surface : "transparent", borderRadius: 6, fontSize: 12, alignItems: "center" }}>
            <span style={{ color: C.textMuted, fontFamily: "monospace", fontSize: 11 }}>{l.time}</span>
            <span style={{ color: C.textDim }}>{l.user}</span>
            <Badge color={actionColor[l.action] || C.textDim}>{l.action}</Badge>
            <span style={{ color: C.textDim }}>{l.entity} — {l.detail}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ══════════════════════════════════════════════════════════════════
// SCREEN 4: Water-Only Customer View (module filtering demo)
// ══════════════════════════════════════════════════════════════════
function WaterOnlyDashboard() {
  return (
    <div>
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 16 }}>
        <div style={{ width: 32, height: 32, borderRadius: 8, background: `${C.blue}22`, display: "flex", alignItems: "center", justifyContent: "center", color: C.blue, fontWeight: 700 }}>A</div>
        <div>
          <div style={{ fontSize: 16, fontWeight: 600, color: C.text }}>AquaFarm Solutions</div>
          <div style={{ fontSize: 11, color: C.textDim }}>Plan: Water & Pumps · 2 users</div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 16 }}>
        <Card>
          <div style={{ fontSize: 11, color: C.textDim, marginBottom: 8 }}>Main Tank</div>
          <div style={{ fontSize: 32, fontWeight: 700, color: C.blue }}>72%</div>
          <div style={{ height: 8, background: C.surfaceAlt, borderRadius: 4, marginTop: 8, overflow: "hidden" }}>
            <div style={{ width: "72%", height: "100%", background: C.blue, borderRadius: 4 }} />
          </div>
          <div style={{ fontSize: 11, color: C.textDim, marginTop: 6 }}>1,768 / 2,438 L · 28.5°C</div>
        </Card>
        <Card>
          <div style={{ fontSize: 11, color: C.textDim, marginBottom: 8 }}>Pump Motor</div>
          <div style={{ fontSize: 32, fontWeight: 700, color: C.green }}>IDLE</div>
          <div style={{ fontSize: 11, color: C.textDim, marginTop: 8 }}>Last run: 2h ago · 12 min</div>
          <button style={{ marginTop: 12, background: `${C.green}22`, color: C.green, border: `1px solid ${C.green}44`, borderRadius: 8, padding: "8px 16px", fontSize: 12, fontWeight: 600, cursor: "pointer", width: "100%" }}>Start Pump</button>
        </Card>
      </div>

      <SectionTitle>Recent Alerts</SectionTitle>
      <Card>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
          <span style={{ width: 6, height: 6, borderRadius: "50%", background: C.amber }} />
          <span style={{ fontSize: 13, color: C.text }}>Tank low at 18%</span>
          <span style={{ fontSize: 11, color: C.textMuted, marginLeft: "auto" }}>2h ago</span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ width: 6, height: 6, borderRadius: "50%", background: C.amber }} />
          <span style={{ fontSize: 13, color: C.text }}>Pump ran for 45 min (max: 30)</span>
          <span style={{ fontSize: 11, color: C.textMuted, marginLeft: "auto" }}>5h ago</span>
        </div>
      </Card>

      <SectionTitle>Navigation (only enabled modules)</SectionTitle>
      <div style={{ display: "flex", gap: 8 }}>
        {[{ label: "Water", icon: "◆", active: true }, { label: "Pumps", icon: "⟳", active: false }, { label: "Alerts", icon: "⚠", active: false }, { label: "Settings", icon: "⚙", active: false }].map((t, i) => (
          <div key={i} style={{ flex: 1, padding: "10px 0", textAlign: "center", background: t.active ? `${C.blue}15` : "transparent", borderRadius: 8, border: `1px solid ${t.active ? C.blue + "44" : C.border}`, cursor: "pointer" }}>
            <div style={{ fontSize: 18 }}>{t.icon}</div>
            <div style={{ fontSize: 10, color: t.active ? C.blue : C.textMuted, marginTop: 2 }}>{t.label}</div>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 8, padding: 10, background: `${C.amber}11`, border: `1px dashed ${C.amber}44`, borderRadius: 8, fontSize: 11, color: C.amber, textAlign: "center" }}>
        Cameras, Sirens, Zones, Devices, Workers, Analytics — hidden (not in subscription)
      </div>
    </div>
  );
}

// ══════════════════════════════════════════════════════════════════
// SCREEN 5: Create Farm Form
// ══════════════════════════════════════════════════════════════════
function CreateFarm() {
  const [plan, setPlan] = useState("full");
  return (
    <div>
      <div style={{ fontSize: 18, fontWeight: 600, color: C.text, marginBottom: 4 }}>Create New Farm</div>
      <div style={{ fontSize: 12, color: C.textDim, marginBottom: 20 }}>Provision a new tenant with admin user and selected modules</div>
      
      <div style={{ display: "grid", gap: 12 }}>
        {[{ label: "Farm Name", placeholder: "Green Valley Estate", type: "text" },
          { label: "URL Slug", placeholder: "green-valley", type: "text" },
          { label: "Owner Name", placeholder: "Suresh Reddy", type: "text" },
          { label: "Contact Email", placeholder: "admin@greenvalley.com", type: "email" },
          { label: "Contact Phone", placeholder: "+91 9876543210", type: "tel" },
        ].map((f, i) => (
          <div key={i}>
            <div style={{ fontSize: 11, color: C.textDim, marginBottom: 4 }}>{f.label}</div>
            <input placeholder={f.placeholder} type={f.type} style={{ width: "100%", background: C.surfaceAlt, border: `1px solid ${C.border}`, borderRadius: 8, padding: "10px 14px", color: C.text, fontSize: 13, outline: "none", boxSizing: "border-box" }} />
          </div>
        ))}
      </div>

      <SectionTitle>Subscription Plan</SectionTitle>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8 }}>
        {Object.entries(plans).map(([k, v]) => (
          <div key={k} onClick={() => setPlan(k)} style={{ background: plan === k ? `${v.color}15` : C.surface, border: `1px solid ${plan === k ? v.color : C.border}`, borderRadius: 10, padding: 14, cursor: "pointer", transition: "all 0.15s" }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: plan === k ? v.color : C.text }}>{v.label}</div>
            <div style={{ fontSize: 11, color: C.textDim, marginTop: 4 }}>{v.modules.length} modules</div>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 3, marginTop: 8 }}>
              {v.modules.map(m => <span key={m} style={{ fontSize: 9, color: v.color, background: `${v.color}15`, padding: "1px 6px", borderRadius: 4 }}>{m}</span>)}
            </div>
          </div>
        ))}
      </div>

      <SectionTitle>Initial Admin User</SectionTitle>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <div>
          <div style={{ fontSize: 11, color: C.textDim, marginBottom: 4 }}>Username</div>
          <input placeholder="admin" style={{ width: "100%", background: C.surfaceAlt, border: `1px solid ${C.border}`, borderRadius: 8, padding: "10px 14px", color: C.text, fontSize: 13, outline: "none", boxSizing: "border-box" }} />
        </div>
        <div>
          <div style={{ fontSize: 11, color: C.textDim, marginBottom: 4 }}>Password</div>
          <input type="password" placeholder="••••••••" style={{ width: "100%", background: C.surfaceAlt, border: `1px solid ${C.border}`, borderRadius: 8, padding: "10px 14px", color: C.text, fontSize: 13, outline: "none", boxSizing: "border-box" }} />
        </div>
      </div>

      <button style={{ marginTop: 20, width: "100%", background: C.accent, color: "#fff", border: "none", borderRadius: 10, padding: "12px 20px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
        Provision Farm + Deploy
      </button>
    </div>
  );
}

// ══════════════════════════════════════════════════════════════════
// SCREEN 6: Android Module-Filtered View (Side by Side)
// ══════════════════════════════════════════════════════════════════
function AndroidComparison() {
  const PhoneFrame = ({ title, plan, children }) => (
    <div style={{ width: 220, background: "#000", borderRadius: 28, padding: "8px 6px", boxShadow: "0 4px 20px rgba(0,0,0,0.5)" }}>
      <div style={{ background: C.bg, borderRadius: 22, overflow: "hidden", minHeight: 420 }}>
        <div style={{ padding: "12px 14px", borderBottom: `1px solid ${C.border}`, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <div style={{ fontSize: 12, fontWeight: 600, color: C.text }}>{title}</div>
            <div style={{ fontSize: 9, color: C.textMuted }}>{plan}</div>
          </div>
          <Badge color={plans[plan === "Full Platform" ? "full" : plan === "Water & Pumps" ? "water_only" : "security"].color}>{plan}</Badge>
        </div>
        <div style={{ padding: 10 }}>{children}</div>
      </div>
    </div>
  );

  const NavBar = ({ items }) => (
    <div style={{ display: "flex", borderTop: `1px solid ${C.border}`, padding: "6px 4px" }}>
      {items.map((t, i) => (
        <div key={i} style={{ flex: 1, textAlign: "center", padding: "4px 0" }}>
          <div style={{ fontSize: 14 }}>{t.icon}</div>
          <div style={{ fontSize: 8, color: i === 0 ? C.accent : C.textMuted }}>{t.label}</div>
        </div>
      ))}
    </div>
  );

  return (
    <div>
      <div style={{ fontSize: 16, fontWeight: 600, color: C.text, marginBottom: 4 }}>Android — Module Filtering</div>
      <div style={{ fontSize: 12, color: C.textDim, marginBottom: 16 }}>Same app, different subscription = different tabs visible</div>
      <div style={{ display: "flex", gap: 16, overflowX: "auto", paddingBottom: 8 }}>
        <PhoneFrame title="Sanga Reddy Farm" plan="Full Platform">
          <div style={{ fontSize: 10, color: C.textMuted, marginBottom: 6 }}>All 6 tabs visible:</div>
          {["Alerts (3 new)","Cameras (8 live)","Siren Control","Water (72%)","Devices (2 nodes)","Settings"].map((t, i) => (
            <div key={i} style={{ padding: "6px 8px", marginBottom: 4, background: C.surfaceAlt, borderRadius: 6, fontSize: 10, color: C.text }}>{t}</div>
          ))}
          <NavBar items={[{icon:"⚠",label:"Alerts"},{icon:"◉",label:"Cameras"},{icon:"◈",label:"Siren"},{icon:"◆",label:"Water"},{icon:"▢",label:"Devices"},{icon:"⚙",label:"Settings"}]} />
        </PhoneFrame>

        <PhoneFrame title="AquaFarm" plan="Water & Pumps">
          <div style={{ fontSize: 10, color: C.textMuted, marginBottom: 6 }}>Only 3 tabs visible:</div>
          {["Water Tank (72%)","Pump Control (IDLE)","Alerts (2 warnings)"].map((t, i) => (
            <div key={i} style={{ padding: "6px 8px", marginBottom: 4, background: C.surfaceAlt, borderRadius: 6, fontSize: 10, color: C.text }}>{t}</div>
          ))}
          <div style={{ padding: 6, background: `${C.amber}11`, borderRadius: 6, fontSize: 9, color: C.amber, textAlign: "center", marginTop: 8 }}>Cameras, Siren, Devices tabs not shown</div>
          <NavBar items={[{icon:"◆",label:"Water"},{icon:"⟳",label:"Pumps"},{icon:"⚠",label:"Alerts"},{icon:"⚙",label:"Settings"}]} />
        </PhoneFrame>

        <PhoneFrame title="Green Valley" plan="Security">
          <div style={{ fontSize: 10, color: C.textMuted, marginBottom: 6 }}>5 tabs visible:</div>
          {["Alerts (1 critical)","Cameras (4 live)","Siren Control","Zones (3 active)","Devices (1 node)"].map((t, i) => (
            <div key={i} style={{ padding: "6px 8px", marginBottom: 4, background: C.surfaceAlt, borderRadius: 6, fontSize: 10, color: C.text }}>{t}</div>
          ))}
          <div style={{ padding: 6, background: `${C.amber}11`, borderRadius: 6, fontSize: 9, color: C.amber, textAlign: "center", marginTop: 8 }}>Water, Pumps tabs not shown</div>
          <NavBar items={[{icon:"⚠",label:"Alerts"},{icon:"◉",label:"Cameras"},{icon:"◈",label:"Siren"},{icon:"▣",label:"Zones"},{icon:"⚙",label:"Settings"}]} />
        </PhoneFrame>
      </div>
    </div>
  );
}

// ══════════════════════════════════════════════════════════════════
// MAIN APP — Tabbed mockup viewer
// ══════════════════════════════════════════════════════════════════
export default function SaaSMockups() {
  const [screen, setScreen] = useState("farms");
  const screens = [
    { id: "farms", label: "Farm Management" },
    { id: "users", label: "User Management" },
    { id: "audit", label: "Audit Log" },
    { id: "water_customer", label: "Water-Only Customer" },
    { id: "create_farm", label: "Create Farm" },
    { id: "android", label: "Android Comparison" },
  ];

  return (
    <div style={{ minHeight: "100vh", background: C.bg, color: C.text, fontFamily: "'DM Sans', system-ui, sans-serif", padding: 0 }}>
      <link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&display=swap" rel="stylesheet" />
      
      <div style={{ borderBottom: `1px solid ${C.border}`, padding: "12px 20px", display: "flex", alignItems: "center", gap: 12 }}>
        <div style={{ width: 28, height: 28, borderRadius: 6, background: `linear-gradient(135deg, ${C.accent}, ${C.gold})`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, color: "#fff" }}>SC</div>
        <span style={{ fontSize: 14, fontWeight: 600, letterSpacing: 0.5 }}>SaaS Mockup Screens</span>
      </div>

      <div style={{ display: "flex", gap: 4, padding: "12px 20px", overflowX: "auto", borderBottom: `1px solid ${C.border}` }}>
        {screens.map(s => (
          <button key={s.id} onClick={() => setScreen(s.id)} style={{ background: screen === s.id ? C.surfaceAlt : "transparent", color: screen === s.id ? C.text : C.textMuted, border: `1px solid ${screen === s.id ? C.border : "transparent"}`, borderRadius: 8, padding: "6px 16px", fontSize: 12, fontWeight: 500, cursor: "pointer", whiteSpace: "nowrap", transition: "all 0.15s" }}>
            {s.label}
          </button>
        ))}
      </div>

      <div style={{ padding: 20, maxWidth: 800, margin: "0 auto" }}>
        {screen === "farms" && <FarmManagement />}
        {screen === "users" && <UserManagement />}
        {screen === "audit" && <AuditLog />}
        {screen === "water_customer" && <WaterOnlyDashboard />}
        {screen === "create_farm" && <CreateFarm />}
        {screen === "android" && <AndroidComparison />}
      </div>
    </div>
  );
}
