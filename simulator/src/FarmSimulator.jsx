import { useState, useCallback, useRef, useEffect } from "react";

const BROKER_DEFAULT = "ws://localhost:9001/mqtt";

// ── Scenario Presets ──
const SCENARIOS = {
  alerts: [
    { id: "snake_storage", label: "Snake near storage", priority: "critical", cls: "snake", zone: "Storage Perimeter", cam: "cam-05", conf: 0.78 },
    { id: "child_pond", label: "Child near pond", priority: "critical", cls: "child", zone: "Pond Safety", cam: "cam-03", conf: 0.85 },
    { id: "fire_shed", label: "Fire at shed", priority: "critical", cls: "fire", zone: "Equipment Shed", cam: "cam-06", conf: 0.91 },
    { id: "intruder_gate", label: "Intruder at gate", priority: "high", cls: "person", zone: "Front Gate", cam: "cam-01", conf: 0.88 },
    { id: "scorpion_path", label: "Scorpion on path", priority: "high", cls: "scorpion", zone: "Walkway Hazard", cam: "cam-04", conf: 0.65 },
    { id: "cow_escaped", label: "Cow left pen", priority: "warning", cls: "cow", zone: "Livestock Pen", cam: "cam-02", conf: 0.92 },
    { id: "smoke_field", label: "Smoke in field", priority: "high", cls: "smoke", zone: "North Field", cam: "cam-07", conf: 0.74 },
    { id: "fall_child", label: "Child fall detected", priority: "critical", cls: "fall_detected", zone: "Pond Safety", cam: "esp32", conf: 1.0 },
  ],
  water: [
    { id: "tank_full", label: "Tank full (95%)", pct: 95, vol: 2316, temp: 28.5 },
    { id: "tank_normal", label: "Tank normal (65%)", pct: 65, vol: 1585, temp: 27.2 },
    { id: "tank_low", label: "Tank low (18%)", pct: 18, vol: 439, temp: 26.0 },
    { id: "tank_critical", label: "Tank critical (6%)", pct: 6, vol: 146, temp: 25.5 },
    { id: "tank_empty", label: "Tank empty (1%)", pct: 1, vol: 24, temp: 24.0 },
  ],
  motor: [
    { id: "motor_start", label: "Motor started", state: "RUNNING" },
    { id: "motor_stop", label: "Motor stopped", state: "IDLE" },
    { id: "motor_error", label: "Motor error (overload)", state: "ERROR" },
  ],
  siren: [
    { id: "siren_trigger", label: "Trigger siren" },
    { id: "siren_stop", label: "Stop siren" },
  ],
  system: [
    { id: "node_online", label: "Edge node online" },
    { id: "node_offline", label: "Edge node offline" },
    { id: "cam_offline", label: "Camera offline (cam-03)" },
    { id: "cam_online", label: "Camera online (cam-03)" },
    { id: "pa_offline", label: "PA system offline" },
  ],
};

const PRIORITY_COLORS = {
  critical: { bg: "#3a0a08", border: "#c8553d", text: "#e88070", dot: "#ff4433" },
  high:     { bg: "#3a2008", border: "#d4832f", text: "#e8a860", dot: "#ff8833" },
  warning:  { bg: "#3a3008", border: "#b8962e", text: "#d4c060", dot: "#eebb33" },
};

const CATEGORY_META = {
  alerts:  { icon: "⚠", color: "#c8553d", label: "Alerts" },
  water:   { icon: "◈", color: "#3a8acc", label: "Water Level" },
  motor:   { icon: "⟳", color: "#6a5acd", label: "Motor/Pump" },
  siren:   { icon: "◉", color: "#cc3344", label: "Siren" },
  system:  { icon: "◆", color: "#4a8c5c", label: "System Events" },
};

function uuid() {
  return "xxxxxxxx-xxxx-4xxx".replace(/x/g, () => ((Math.random() * 16) | 0).toString(16));
}

function ts() { return new Date().toISOString(); }

function buildPayload(category, scenario, nodeId) {
  const now = Date.now() / 1000;
  if (category === "alerts") {
    return {
      topic: `farm/alerts/${scenario.priority}`,
      payload: {
        alert_id: uuid(), node_id: nodeId, camera_id: scenario.cam,
        zone_id: scenario.zone.toLowerCase().replace(/ /g, "_"),
        zone_name: scenario.zone, zone_type: scenario.priority === "critical" ? "zero_tolerance" : "intrusion",
        detection_class: scenario.cls, confidence: scenario.conf,
        priority: scenario.priority, status: "new",
        timestamp: now, created_at: ts(), mock: true, simulator: true,
      },
    };
  }
  if (category === "water") {
    return {
      topic: "tank1_sim/water/level",
      payload: {
        percentFilled: scenario.pct, percentRemaining: 100 - scenario.pct,
        waterHeightMm: Math.round(scenario.pct * 17.04), waterHeightCm: +(scenario.pct * 1.704).toFixed(1),
        volumeLiters: scenario.vol, volumeRemaining: +(2438 - scenario.vol).toFixed(1),
        distanceMm: Math.round((100 - scenario.pct) * 17.04), distanceCm: +((100 - scenario.pct) * 1.704).toFixed(1),
        temperatureC: scenario.temp, state: scenario.pct <= 10 ? "critical" : scenario.pct <= 20 ? "low" : "normal",
        battery: { voltage: 3.72, percent: 85, state: "charging" },
        error: 0, timestamp: now,
      },
    };
  }
  if (category === "motor") {
    return {
      topic: "motor1_sim/motor/status",
      payload: { motor_id: "motor-1", state: scenario.state, node_id: nodeId, timestamp: now },
    };
  }
  if (category === "siren") {
    const cmd = scenario.id === "siren_trigger" ? "trigger" : "stop";
    return {
      topic: `farm/siren/${cmd}`,
      payload: { command: cmd, node_id: nodeId, triggered_by: "simulator", siren_url: "http://sim/alert.mp3", timestamp: now },
    };
  }
  if (category === "system") {
    if (scenario.id.startsWith("node_")) {
      return {
        topic: `farm/nodes/${nodeId}/status`,
        payload: { node_id: nodeId, status: scenario.id === "node_online" ? "online" : "offline", timestamp: now },
      };
    }
    if (scenario.id.startsWith("cam_")) {
      return {
        topic: "farm/events/camera_status",
        payload: { camera_id: "cam-03", event: scenario.id === "cam_online" ? "online" : "offline", ts: now },
      };
    }
    return {
      topic: "pa/health",
      payload: { status: "offline", reason: "heartbeat_timeout", ts: now },
    };
  }
  return { topic: "farm/test", payload: {} };
}

export default function FarmSimulator() {
  const [broker, setBroker] = useState(BROKER_DEFAULT);
  const [nodeId, setNodeId] = useState("edge-node-a");
  const [connected, setConnected] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [log, setLog] = useState([]);
  const [activeTab, setActiveTab] = useState("alerts");
  const [customTopic, setCustomTopic] = useState("farm/alerts/high");
  const [customPayload, setCustomPayload] = useState('{"test": true}');
  const [autoMode, setAutoMode] = useState(false);
  const [autoInterval, setAutoInterval] = useState(5);
  const clientRef = useRef(null);
  const autoRef = useRef(null);
  const logEndRef = useRef(null);

  useEffect(() => {
    if (logEndRef.current) logEndRef.current.scrollIntoView({ behavior: "smooth" });
  }, [log]);

  const addLog = useCallback((entry) => {
    setLog((prev) => [...prev.slice(-99), { ...entry, time: new Date().toLocaleTimeString() }]);
  }, []);

  const doConnect = useCallback(() => {
    setConnecting(true);
    addLog({ type: "info", msg: `Connecting to ${broker}...` });
    try {
      const url = broker.startsWith("ws") ? broker : `ws://${broker}`;
      const ws = new WebSocket(url);
      ws._queue = [];
      ws.onopen = () => {
        setConnected(true); setConnecting(false);
        addLog({ type: "success", msg: "Connected" });
      };
      ws.onclose = () => {
        setConnected(false); setConnecting(false);
        addLog({ type: "warn", msg: "Disconnected" });
      };
      ws.onerror = () => {
        setConnecting(false);
        addLog({ type: "error", msg: "Connection failed — is the MQTT WebSocket broker running?" });
      };
      clientRef.current = ws;
    } catch (e) {
      setConnecting(false);
      addLog({ type: "error", msg: `Error: ${e.message}` });
    }
  }, [broker, addLog]);

  const doDisconnect = useCallback(() => {
    clientRef.current?.close();
    clientRef.current = null;
    setConnected(false);
    if (autoRef.current) { clearInterval(autoRef.current); autoRef.current = null; setAutoMode(false); }
  }, []);

  const publish = useCallback((topic, payload) => {
    addLog({ type: "pub", topic, msg: JSON.stringify(payload).slice(0, 120) + "..." });
    // For a real implementation, this would use an MQTT.js client over WebSocket.
    // For now, we log what WOULD be published and also POST to the backend API
    // if available, which is more practical for testing.
    const apiBase = broker.replace(/^ws/, "http").replace(/:\d+\/.*$/, ":8080");
    // Attempt REST API fallback for alert creation
    if (topic.startsWith("farm/alerts/")) {
      fetch(`${apiBase}/api/v1/alerts`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      }).catch(() => {});
    }
  }, [broker, addLog]);

  const fireScenario = useCallback((category, scenario) => {
    const { topic, payload } = buildPayload(category, scenario, nodeId);
    publish(topic, payload);
  }, [nodeId, publish]);

  const fireRandom = useCallback(() => {
    const cats = Object.keys(SCENARIOS);
    const cat = cats[Math.floor(Math.random() * cats.length)];
    const items = SCENARIOS[cat];
    const item = items[Math.floor(Math.random() * items.length)];
    fireScenario(cat, item);
  }, [fireScenario]);

  const toggleAuto = useCallback(() => {
    if (autoMode) {
      clearInterval(autoRef.current); autoRef.current = null; setAutoMode(false);
      addLog({ type: "info", msg: "Auto mode stopped" });
    } else {
      setAutoMode(true);
      addLog({ type: "info", msg: `Auto mode: random event every ${autoInterval}s` });
      autoRef.current = setInterval(fireRandom, autoInterval * 1000);
    }
  }, [autoMode, autoInterval, fireRandom, addLog]);

  const fireCustom = useCallback(() => {
    try {
      const p = JSON.parse(customPayload);
      publish(customTopic, p);
    } catch { addLog({ type: "error", msg: "Invalid JSON" }); }
  }, [customTopic, customPayload, publish]);

  const fireAll = useCallback(() => {
    addLog({ type: "info", msg: "CHAOS MODE: firing all scenarios..." });
    Object.entries(SCENARIOS).forEach(([cat, items]) => {
      items.forEach((s, i) => setTimeout(() => fireScenario(cat, s), i * 300));
    });
  }, [fireScenario, addLog]);

  return (
    <div style={{ minHeight: "100vh", background: "#0e1118", color: "#c8c4bc", fontFamily: "'DM Mono', 'JetBrains Mono', monospace" }}>
      <link href="https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=Outfit:wght@400;600;700&display=swap" rel="stylesheet" />

      {/* Header */}
      <div style={{ borderBottom: "1px solid #1e2028", padding: "16px 24px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ width: 36, height: 36, borderRadius: 8, background: "linear-gradient(135deg, #c8553d, #d4832f)", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "Outfit", fontWeight: 700, fontSize: 16, color: "#fff" }}>SC</div>
          <div>
            <div style={{ fontFamily: "Outfit", fontWeight: 600, fontSize: 16, color: "#e8e4dc", letterSpacing: 1 }}>Farm Event Simulator</div>
            <div style={{ fontSize: 11, color: "#6a665e", letterSpacing: 0.5 }}>SudarshanChakra Test Console</div>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12 }}>
            <div style={{ width: 8, height: 8, borderRadius: "50%", background: connected ? "#4a8c5c" : connecting ? "#d4832f" : "#5a564e", animation: connecting ? "pulse 1s infinite" : "none" }} />
            <span style={{ color: connected ? "#4a8c5c" : "#6a665e" }}>{connected ? "Connected" : connecting ? "Connecting..." : "Disconnected"}</span>
          </div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 0, height: "calc(100vh - 69px)" }}>
        {/* Left Panel — Controls */}
        <div style={{ overflow: "auto", padding: 20 }}>

          {/* Broker Config */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 160px auto", gap: 8, marginBottom: 20 }}>
            <input value={broker} onChange={(e) => setBroker(e.target.value)} placeholder="ws://broker:9001/mqtt" style={{ background: "#181a22", border: "1px solid #2a2c34", borderRadius: 6, padding: "8px 12px", color: "#c8c4bc", fontFamily: "inherit", fontSize: 13, outline: "none" }} />
            <input value={nodeId} onChange={(e) => setNodeId(e.target.value)} placeholder="Node ID" style={{ background: "#181a22", border: "1px solid #2a2c34", borderRadius: 6, padding: "8px 12px", color: "#c8c4bc", fontFamily: "inherit", fontSize: 13, outline: "none" }} />
            <button onClick={connected ? doDisconnect : doConnect} disabled={connecting} style={{ background: connected ? "#2a1a1a" : "#1a2a1a", color: connected ? "#c8553d" : "#4a8c5c", border: `1px solid ${connected ? "#3a2020" : "#2a3a2a"}`, borderRadius: 6, padding: "8px 16px", cursor: "pointer", fontFamily: "inherit", fontSize: 13, fontWeight: 500 }}>
              {connected ? "Disconnect" : "Connect"}
            </button>
          </div>

          {/* Category Tabs */}
          <div style={{ display: "flex", gap: 4, marginBottom: 16, borderBottom: "1px solid #1e2028", paddingBottom: 8 }}>
            {Object.entries(CATEGORY_META).map(([key, meta]) => (
              <button key={key} onClick={() => setActiveTab(key)} style={{ background: activeTab === key ? "#1a1c24" : "transparent", color: activeTab === key ? meta.color : "#5a564e", border: activeTab === key ? `1px solid ${meta.color}33` : "1px solid transparent", borderRadius: 6, padding: "6px 14px", cursor: "pointer", fontFamily: "inherit", fontSize: 12, fontWeight: 500, display: "flex", alignItems: "center", gap: 6, transition: "all 0.15s" }}>
                <span style={{ fontSize: 14 }}>{meta.icon}</span> {meta.label}
              </button>
            ))}
            <button onClick={() => setActiveTab("custom")} style={{ background: activeTab === "custom" ? "#1a1c24" : "transparent", color: activeTab === "custom" ? "#8a8aaa" : "#5a564e", border: activeTab === "custom" ? "1px solid #8a8aaa33" : "1px solid transparent", borderRadius: 6, padding: "6px 14px", cursor: "pointer", fontFamily: "inherit", fontSize: 12, fontWeight: 500 }}>
              Custom
            </button>
          </div>

          {/* Scenario Buttons */}
          {activeTab !== "custom" ? (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 8 }}>
              {SCENARIOS[activeTab]?.map((s) => {
                const pc = PRIORITY_COLORS[s.priority] || { bg: "#1a1c24", border: "#2a2c34", text: "#c8c4bc", dot: "#888" };
                return (
                  <button key={s.id} onClick={() => fireScenario(activeTab, s)} style={{ background: activeTab === "alerts" ? pc.bg : "#141620", border: `1px solid ${activeTab === "alerts" ? pc.border : CATEGORY_META[activeTab]?.color || "#2a2c34"}44`, borderRadius: 8, padding: "12px 14px", cursor: "pointer", textAlign: "left", transition: "all 0.15s", fontFamily: "inherit" }}
                    onMouseEnter={(e) => { e.currentTarget.style.transform = "translateY(-1px)"; e.currentTarget.style.borderColor = activeTab === "alerts" ? pc.border : CATEGORY_META[activeTab]?.color; }}
                    onMouseLeave={(e) => { e.currentTarget.style.transform = "none"; e.currentTarget.style.borderColor = `${activeTab === "alerts" ? pc.border : CATEGORY_META[activeTab]?.color || "#2a2c34"}44`; }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                      {activeTab === "alerts" && <div style={{ width: 6, height: 6, borderRadius: "50%", background: pc.dot, boxShadow: `0 0 6px ${pc.dot}88` }} />}
                      <span style={{ color: activeTab === "alerts" ? pc.text : "#c8c4bc", fontSize: 13, fontWeight: 500 }}>{s.label}</span>
                    </div>
                    {activeTab === "alerts" && <div style={{ fontSize: 11, color: "#5a564e" }}>{s.cam} · {s.zone} · {(s.conf * 100).toFixed(0)}%</div>}
                    {activeTab === "water" && <div style={{ fontSize: 11, color: "#5a564e" }}>{s.pct}% · {s.vol}L · {s.temp}°C</div>}
                    {activeTab === "motor" && <div style={{ fontSize: 11, color: "#5a564e" }}>State → {s.state}</div>}
                  </button>
                );
              })}
            </div>
          ) : (
            /* Custom Topic Publisher */
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              <input value={customTopic} onChange={(e) => setCustomTopic(e.target.value)} placeholder="Topic" style={{ background: "#181a22", border: "1px solid #2a2c34", borderRadius: 6, padding: "8px 12px", color: "#c8c4bc", fontFamily: "inherit", fontSize: 13, outline: "none" }} />
              <textarea value={customPayload} onChange={(e) => setCustomPayload(e.target.value)} rows={6} placeholder='{"key": "value"}' style={{ background: "#181a22", border: "1px solid #2a2c34", borderRadius: 6, padding: "10px 12px", color: "#c8c4bc", fontFamily: "inherit", fontSize: 12, outline: "none", resize: "vertical" }} />
              <button onClick={fireCustom} style={{ background: "#1a1c24", color: "#8a8aaa", border: "1px solid #2a2c34", borderRadius: 6, padding: "8px 16px", cursor: "pointer", fontFamily: "inherit", fontSize: 13, fontWeight: 500 }}>Publish Custom</button>
            </div>
          )}

          {/* Action Bar */}
          <div style={{ display: "flex", gap: 8, marginTop: 20, paddingTop: 16, borderTop: "1px solid #1e2028" }}>
            <button onClick={fireRandom} style={{ background: "#1a1c24", color: "#c8c4bc", border: "1px solid #2a2c34", borderRadius: 6, padding: "8px 16px", cursor: "pointer", fontFamily: "inherit", fontSize: 12, fontWeight: 500, flex: 1 }}>
              Fire Random
            </button>
            <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
              <button onClick={toggleAuto} style={{ background: autoMode ? "#2a1a1a" : "#1a2a1a", color: autoMode ? "#c8553d" : "#4a8c5c", border: `1px solid ${autoMode ? "#3a2020" : "#2a3a2a"}`, borderRadius: 6, padding: "8px 16px", cursor: "pointer", fontFamily: "inherit", fontSize: 12, fontWeight: 500 }}>
                {autoMode ? "Stop Auto" : "Auto Mode"}
              </button>
              <input type="number" value={autoInterval} onChange={(e) => setAutoInterval(Math.max(1, +e.target.value))} min={1} max={60} style={{ width: 48, background: "#181a22", border: "1px solid #2a2c34", borderRadius: 6, padding: "8px 6px", color: "#c8c4bc", fontFamily: "inherit", fontSize: 12, textAlign: "center", outline: "none" }} />
              <span style={{ fontSize: 11, color: "#5a564e" }}>sec</span>
            </div>
            <button onClick={fireAll} style={{ background: "#2a1010", color: "#e85545", border: "1px solid #3a1818", borderRadius: 6, padding: "8px 16px", cursor: "pointer", fontFamily: "inherit", fontSize: 12, fontWeight: 500 }}>
              CHAOS
            </button>
          </div>
        </div>

        {/* Right Panel — Event Log */}
        <div style={{ borderLeft: "1px solid #1e2028", display: "flex", flexDirection: "column" }}>
          <div style={{ padding: "12px 16px", borderBottom: "1px solid #1e2028", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontFamily: "Outfit", fontWeight: 600, fontSize: 13, color: "#8a8680", letterSpacing: 0.5 }}>Event Log</span>
            <button onClick={() => setLog([])} style={{ background: "none", border: "none", color: "#5a564e", cursor: "pointer", fontFamily: "inherit", fontSize: 11 }}>Clear</button>
          </div>
          <div style={{ flex: 1, overflow: "auto", padding: 8 }}>
            {log.length === 0 && <div style={{ padding: 20, textAlign: "center", color: "#3a3830", fontSize: 12 }}>No events yet. Fire a scenario to start.</div>}
            {log.map((entry, i) => (
              <div key={i} style={{ padding: "6px 10px", marginBottom: 2, borderRadius: 4, fontSize: 11, lineHeight: 1.5, background: entry.type === "error" ? "#1a0808" : "transparent", borderLeft: `2px solid ${entry.type === "pub" ? "#3a8acc" : entry.type === "success" ? "#4a8c5c" : entry.type === "warn" ? "#d4832f" : entry.type === "error" ? "#c8553d" : "#3a3830"}` }}>
                <span style={{ color: "#4a4840" }}>{entry.time}</span>
                {entry.topic && <span style={{ color: "#3a8acc", marginLeft: 6 }}>{entry.topic}</span>}
                <div style={{ color: entry.type === "error" ? "#c8553d" : "#8a8680", marginTop: 1, wordBreak: "break-all" }}>{entry.msg}</div>
              </div>
            ))}
            <div ref={logEndRef} />
          </div>
        </div>
      </div>

      <style>{`
        @keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }
        input:focus, textarea:focus, button:hover { border-color: #3a3c44 !important; }
        button:active { transform: scale(0.98) !important; }
        ::-webkit-scrollbar { width: 6px; }
        ::-webkit-scrollbar-track { background: transparent; }
        ::-webkit-scrollbar-thumb { background: #2a2c34; border-radius: 3px; }
      `}</style>
    </div>
  );
}
