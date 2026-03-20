import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react";
import { SimulatorMqttClient } from "./mqtt/MqttClient";
import { SCENARIOS, type ScenarioKey } from "./scenarios";
import { SEQUENCES } from "./scenarios/sequences";
import type { LogEntry, ScenarioCategory } from "./types";
import type { AlertScenario } from "./types";
import type { WaterScenario } from "./types";
import type { MotorScenario } from "./types";
import type { SirenScenario } from "./types";
import type { SystemScenario } from "./types";

const BROKER_DEFAULT = "ws://localhost:15675/ws";
const API_DEFAULT = "http://localhost:8080";

const PRIORITY_COLORS: Record<string, { bg: string; border: string; text: string; dot: string }> = {
  critical: { bg: "#3a0a08", border: "#c8553d", text: "#e88070", dot: "#ff4433" },
  high: { bg: "#3a2008", border: "#d4832f", text: "#e8a860", dot: "#ff8833" },
  warning: { bg: "#3a3008", border: "#b8962e", text: "#d4c060", dot: "#eebb33" },
};

const CATEGORY_META: Record<ScenarioKey | "custom", { icon: string; color: string; label: string }> = {
  alerts: { icon: "⚠", color: "#c8553d", label: "Alerts" },
  water: { icon: "◈", color: "#3a8acc", label: "Water Level" },
  motor: { icon: "⟳", color: "#6a5acd", label: "Motor/Pump" },
  siren: { icon: "◉", color: "#cc3344", label: "Siren" },
  system: { icon: "◆", color: "#4a8c5c", label: "System Events" },
  custom: { icon: "✎", color: "#8a8aaa", label: "Custom" },
};

function uuid(): string {
  return "xxxxxxxx-xxxx-4xxx".replace(/x/g, () => ((Math.random() * 16) | 0).toString(16));
}

function ts(): string {
  return new Date().toISOString();
}

function buildPayload(
  category: ScenarioCategory,
  scenario: AlertScenario | WaterScenario | MotorScenario | SirenScenario | SystemScenario,
  nodeId: string,
): { topic: string; payload: Record<string, unknown> } {
  const now = Date.now() / 1000;
  if (category === "alerts") {
    const s = scenario as AlertScenario;
    return {
      topic: `farm/alerts/${s.priority}`,
      payload: {
        alert_id: uuid(),
        node_id: nodeId,
        camera_id: s.cam,
        zone_id: s.zone.toLowerCase().replace(/ /g, "_"),
        zone_name: s.zone,
        zone_type: s.priority === "critical" ? "zero_tolerance" : "intrusion",
        detection_class: s.cls,
        confidence: s.conf,
        priority: s.priority,
        status: "new",
        timestamp: now,
        created_at: ts(),
        mock: true,
        simulator: true,
      },
    };
  }
  if (category === "water") {
    const s = scenario as WaterScenario;
    return {
      topic: "tank1_sim/water/level",
      payload: {
        percentFilled: s.pct,
        percentRemaining: 100 - s.pct,
        waterHeightMm: Math.round(s.pct * 17.04),
        waterHeightCm: +(s.pct * 1.704).toFixed(1),
        volumeLiters: s.vol,
        volumeRemaining: +(2438 - s.vol).toFixed(1),
        distanceMm: Math.round((100 - s.pct) * 17.04),
        distanceCm: +((100 - s.pct) * 1.704).toFixed(1),
        temperatureC: s.temp,
        state: s.pct <= 10 ? "critical" : s.pct <= 20 ? "low" : "normal",
        battery: { voltage: 3.72, percent: 85, state: "charging" },
        error: 0,
        timestamp: now,
      },
    };
  }
  if (category === "motor") {
    const s = scenario as MotorScenario;
    return {
      topic: "motor1_sim/motor/status",
      payload: { motor_id: "motor-1", state: s.state, node_id: nodeId, timestamp: now },
    };
  }
  if (category === "siren") {
    const s = scenario as SirenScenario;
    const cmd = s.id === "siren_trigger" ? "trigger" : "stop";
    return {
      topic: `farm/siren/${cmd}`,
      payload: {
        command: cmd,
        node_id: nodeId,
        triggered_by: "simulator",
        siren_url: "http://sim/alert.mp3",
        timestamp: now,
      },
    };
  }
  if (category === "system") {
    const s = scenario as SystemScenario;
    if (s.id.startsWith("node_")) {
      return {
        topic: `farm/nodes/${nodeId}/status`,
        payload: { node_id: nodeId, status: s.id === "node_online" ? "online" : "offline", timestamp: now },
      };
    }
    if (s.id.startsWith("cam_")) {
      return {
        topic: "farm/events/camera_status",
        payload: { camera_id: "cam-03", event: s.id === "cam_online" ? "online" : "offline", ts: now },
      };
    }
    return {
      topic: "pa/health",
      payload: { status: "offline", reason: "heartbeat_timeout", ts: now },
    };
  }
  return { topic: "farm/test", payload: {} };
}

async function postAlertRest(apiBase: string, token: string | undefined, payload: Record<string, unknown>): Promise<string> {
  const base = apiBase.replace(/\/$/, "");
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token?.trim()) headers.Authorization = `Bearer ${token.trim()}`;
  const res = await fetch(`${base}/api/v1/alerts`, { method: "POST", headers, body: JSON.stringify(payload) });
  const txt = await res.text().catch(() => "");
  return `${res.status} ${res.statusText}${txt ? ` — ${txt.slice(0, 200)}` : ""}`;
}

export default function FarmSimulator() {
  const [broker, setBroker] = useState(BROKER_DEFAULT);
  const [apiBase, setApiBase] = useState(API_DEFAULT);
  const [bearerToken, setBearerToken] = useState("");
  const [nodeId, setNodeId] = useState("edge-node-a");
  const [connected, setConnected] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [log, setLog] = useState<LogEntry[]>([]);
  const [activeTab, setActiveTab] = useState<ScenarioKey | "custom">("alerts");
  const [customTopic, setCustomTopic] = useState("farm/alerts/high");
  const [customPayload, setCustomPayload] = useState('{"test": true}');
  const [autoMode, setAutoMode] = useState(false);
  const [autoInterval, setAutoInterval] = useState(5);
  const [waterSim, setWaterSim] = useState<"off" | "fill" | "drain" | "fluctuate">("off");
  const [sequenceKey, setSequenceKey] = useState<string>("");
  const clientRef = useRef<SimulatorMqttClient | null>(null);
  const autoRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const waterRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const seqTimersRef = useRef<number[]>([]);
  const logEndRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [log]);

  const addLog = useCallback((entry: Omit<LogEntry, "time">) => {
    setLog((prev) => [...prev.slice(-199), { ...entry, time: new Date().toLocaleTimeString() }]);
  }, []);

  const publishMqtt = useCallback(
    (topic: string, payload: Record<string, unknown>) => {
      const c = clientRef.current;
      if (!c?.isConnected) {
        addLog({ type: "warn", msg: "Not connected — MQTT publish skipped" });
        return;
      }
      c.publish(topic, payload);
      addLog({ type: "pub", topic, msg: JSON.stringify(payload).slice(0, 140) + (JSON.stringify(payload).length > 140 ? "…" : "") });
    },
    [addLog],
  );

  const publish = useCallback(
    async (topic: string, payload: Record<string, unknown>) => {
      publishMqtt(topic, payload);
      if (topic.startsWith("farm/alerts/")) {
        try {
          const line = await postAlertRest(apiBase, bearerToken || undefined, payload);
          addLog({ type: "info", msg: `REST POST /api/v1/alerts → ${line}` });
        } catch (e) {
          addLog({ type: "error", msg: `REST failed: ${(e as Error).message}` });
        }
      }
    },
    [addLog, apiBase, bearerToken, publishMqtt],
  );

  const fireScenario = useCallback(
    (category: ScenarioCategory, scenario: (typeof SCENARIOS)[ScenarioKey][number]) => {
      const { topic, payload } = buildPayload(category, scenario as never, nodeId);
      void publish(topic, payload);
    },
    [nodeId, publish],
  );

  const doConnect = useCallback(() => {
    setConnecting(true);
    addLog({ type: "info", msg: `Connecting MQTT ${broker}…` });
    const c = new SimulatorMqttClient();
    clientRef.current = c;
    c.connect(broker)
      .then(() => {
        setConnected(true);
        setConnecting(false);
        addLog({ type: "success", msg: "MQTT connected" });
        c.subscribe("farm/siren/ack", (_t, msg) => {
          addLog({ type: "in", topic: "farm/siren/ack", msg: `← ${msg.slice(0, 200)}` });
        });
      })
      .catch((e: Error) => {
        setConnecting(false);
        setConnected(false);
        addLog({ type: "error", msg: `MQTT failed: ${e.message}` });
      });
  }, [broker, addLog]);

  const doDisconnect = useCallback(() => {
    clientRef.current?.disconnect();
    clientRef.current = null;
    setConnected(false);
    if (autoRef.current) {
      clearInterval(autoRef.current);
      autoRef.current = null;
    }
    setAutoMode(false);
    if (waterRef.current) {
      clearInterval(waterRef.current);
      waterRef.current = null;
    }
    setWaterSim("off");
    seqTimersRef.current.forEach((id) => window.clearTimeout(id));
    seqTimersRef.current = [];
    addLog({ type: "warn", msg: "Disconnected" });
  }, [addLog]);

  const fireRandom = useCallback(() => {
    const cats = Object.keys(SCENARIOS) as ScenarioKey[];
    const cat = cats[Math.floor(Math.random() * cats.length)]!;
    const items = SCENARIOS[cat];
    const item = items[Math.floor(Math.random() * items.length)]!;
    fireScenario(cat, item);
  }, [fireScenario]);

  const toggleAuto = useCallback(() => {
    if (autoMode) {
      if (autoRef.current) clearInterval(autoRef.current);
      autoRef.current = null;
      setAutoMode(false);
      addLog({ type: "info", msg: "Auto mode stopped" });
    } else {
      setAutoMode(true);
      addLog({ type: "info", msg: `Auto: random event every ${autoInterval}s` });
      autoRef.current = setInterval(fireRandom, autoInterval * 1000);
    }
  }, [autoMode, autoInterval, fireRandom, addLog]);

  const fireCustom = useCallback(() => {
    try {
      const p = JSON.parse(customPayload) as Record<string, unknown>;
      void publish(customTopic, p);
    } catch {
      addLog({ type: "error", msg: "Invalid JSON" });
    }
  }, [customTopic, customPayload, publish, addLog]);

  const fireAll = useCallback(() => {
    addLog({ type: "info", msg: "CHAOS: firing all scenarios…" });
    (Object.keys(SCENARIOS) as ScenarioKey[]).forEach((cat) => {
      SCENARIOS[cat].forEach((s, i) => {
        window.setTimeout(() => fireScenario(cat, s), i * 300);
      });
    });
  }, [fireScenario, addLog]);

  useEffect(() => {
    if (waterSim === "off") {
      if (waterRef.current) clearInterval(waterRef.current);
      waterRef.current = null;
      return;
    }
    let pct = 50;
    if (waterSim === "fill") pct = 10;
    if (waterSim === "drain") pct = 80;
    if (waterSim === "fluctuate") pct = 65;
    const volFor = (p: number) => Math.round((p / 100) * 2438);
    waterRef.current = setInterval(() => {
      if (waterSim === "fill") pct = Math.min(95, pct + 2);
      else if (waterSim === "drain") pct = Math.max(3, pct - 2);
      else pct = Math.min(90, Math.max(40, pct + (Math.random() * 4 - 2)));
      const scenario: WaterScenario = {
        id: "live",
        label: "live",
        pct,
        vol: volFor(pct),
        temp: 26 + Math.random() * 2,
      };
      const { topic, payload } = buildPayload("water", scenario, nodeId);
      publishMqtt(topic, payload);
    }, waterSim === "fluctuate" ? 30000 : 2000);
    return () => {
      if (waterRef.current) clearInterval(waterRef.current);
      waterRef.current = null;
    };
  }, [waterSim, nodeId, publishMqtt]);

  const playSequence = useCallback(
    (key: string) => {
      seqTimersRef.current.forEach((id) => window.clearTimeout(id));
      seqTimersRef.current = [];
      const seq = SEQUENCES[key];
      if (!seq) return;
      addLog({ type: "info", msg: `Sequence: ${seq.label}` });
      seq.steps.forEach((step) => {
        const id = window.setTimeout(() => {
          const list = SCENARIOS[step.category as ScenarioKey];
          const sc = list.find((x) => x.id === step.scenarioId);
          if (sc) fireScenario(step.category, sc);
        }, step.delayMs);
        seqTimersRef.current.push(id);
      });
    },
    [addLog, fireScenario],
  );

  return (
    <div style={{ minHeight: "100vh", background: "#0e1118", color: "#c8c4bc", fontFamily: "'DM Mono', monospace" }}>
      <link
        href="https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=Outfit:wght@400;600;700&display=swap"
        rel="stylesheet"
      />

      <div style={{ borderBottom: "1px solid #1e2028", padding: "16px 24px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 8,
              background: "linear-gradient(135deg, #c8553d, #d4832f)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontFamily: "Outfit",
              fontWeight: 700,
              fontSize: 16,
              color: "#fff",
            }}
          >
            SC
          </div>
          <div>
            <div style={{ fontFamily: "Outfit", fontWeight: 600, fontSize: 16, color: "#e8e4dc" }}>Farm Event Simulator</div>
            <div style={{ fontSize: 11, color: "#6a665e" }}>SudarshanChakra — MQTT + REST</div>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12 }}>
          <div
            style={{
              width: 8,
              height: 8,
              borderRadius: "50%",
              background: connected ? "#4a8c5c" : connecting ? "#d4832f" : "#5a564e",
            }}
          />
          <span style={{ color: connected ? "#4a8c5c" : "#6a665e" }}>{connected ? "MQTT" : connecting ? "…" : "Off"}</span>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 0, minHeight: "calc(100vh - 69px)" }}>
        <div style={{ overflow: "auto", padding: 20 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 12 }}>
            <label style={{ fontSize: 11, color: "#6a665e" }}>
              MQTT (WebSocket)
              <input
                value={broker}
                onChange={(e) => setBroker(e.target.value)}
                style={inp}
                placeholder="ws://host:15675/ws"
              />
            </label>
            <label style={{ fontSize: 11, color: "#6a665e" }}>
              API base (gateway)
              <input value={apiBase} onChange={(e) => setApiBase(e.target.value)} style={inp} placeholder="http://localhost:8080" />
            </label>
          </div>
          <input
            value={bearerToken}
            onChange={(e) => setBearerToken(e.target.value)}
            placeholder="Optional JWT for REST (paste from dashboard login)"
            style={{ ...inp, width: "100%", marginBottom: 8 }}
          />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 120px auto", gap: 8, marginBottom: 16 }}>
            <input value={nodeId} onChange={(e) => setNodeId(e.target.value)} placeholder="Node ID" style={inp} />
            <button type="button" onClick={connected ? doDisconnect : doConnect} disabled={connecting} style={btn(connected)}>
              {connected ? "Disconnect" : "Connect"}
            </button>
          </div>

          <div style={{ marginBottom: 12 }}>
            <span style={{ fontSize: 11, color: "#6a665e" }}>Water simulation</span>
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 4 }}>
              {(["off", "fill", "drain", "fluctuate"] as const).map((m) => (
                <button
                  key={m}
                  type="button"
                  onClick={() => setWaterSim(m)}
                  style={{
                    ...btn(waterSim === m),
                    opacity: waterSim === m ? 1 : 0.7,
                    fontSize: 11,
                  }}
                >
                  {m}
                </button>
              ))}
            </div>
          </div>

          <div style={{ marginBottom: 12, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            <span style={{ fontSize: 11, color: "#6a665e" }}>Sequence</span>
            <select
              value={sequenceKey}
              onChange={(e) => setSequenceKey(e.target.value)}
              style={{ ...inp, maxWidth: 200 }}
            >
              <option value="">—</option>
              {Object.entries(SEQUENCES).map(([k, v]) => (
                <option key={k} value={k}>
                  {v.label}
                </option>
              ))}
            </select>
            <button type="button" style={btn(false)} onClick={() => sequenceKey && playSequence(sequenceKey)}>
              Play
            </button>
          </div>

          <div style={{ display: "flex", gap: 4, marginBottom: 16, borderBottom: "1px solid #1e2028", paddingBottom: 8, flexWrap: "wrap" }}>
            {(Object.keys(SCENARIOS) as ScenarioKey[]).map((key) => {
              const meta = CATEGORY_META[key];
              return (
                <button key={key} type="button" onClick={() => setActiveTab(key)} style={tab(activeTab === key, meta.color)}>
                  {meta.icon} {meta.label}
                </button>
              );
            })}
            <button type="button" onClick={() => setActiveTab("custom")} style={tab(activeTab === "custom", "#8a8aaa")}>
              Custom
            </button>
          </div>

          {activeTab !== "custom" ? (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 8 }}>
              {SCENARIOS[activeTab].map((s) => {
                const pc =
                  "priority" in s && s.priority
                    ? PRIORITY_COLORS[s.priority] || { bg: "#1a1c24", border: "#2a2c34", text: "#c8c4bc", dot: "#888" }
                    : { bg: "#141620", border: "#2a2c34", text: "#c8c4bc", dot: "#888" };
                const meta = CATEGORY_META[activeTab];
                return (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => fireScenario(activeTab, s)}
                    style={{
                      background: activeTab === "alerts" ? pc.bg : "#141620",
                      border: `1px solid ${activeTab === "alerts" ? pc.border : meta.color}44`,
                      borderRadius: 8,
                      padding: "12px 14px",
                      cursor: "pointer",
                      textAlign: "left",
                      color: "#c8c4bc",
                      fontFamily: "inherit",
                    }}
                  >
                    <div style={{ fontSize: 13, fontWeight: 500, color: activeTab === "alerts" ? pc.text : "#c8c4bc" }}>{s.label}</div>
                    {activeTab === "alerts" && "cam" in s && (
                      <div style={{ fontSize: 11, color: "#5a564e" }}>
                        {s.cam} · {s.zone} · {(s.conf * 100).toFixed(0)}%
                      </div>
                    )}
                    {activeTab === "water" && "pct" in s && (
                      <div style={{ fontSize: 11, color: "#5a564e" }}>
                        {s.pct}% · {s.vol}L · {s.temp}°C
                      </div>
                    )}
                    {activeTab === "motor" && "state" in s && (
                      <div style={{ fontSize: 11, color: "#5a564e" }}>→ {s.state}</div>
                    )}
                  </button>
                );
              })}
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              <input value={customTopic} onChange={(e) => setCustomTopic(e.target.value)} style={inp} />
              <textarea value={customPayload} onChange={(e) => setCustomPayload(e.target.value)} rows={6} style={{ ...inp, resize: "vertical" }} />
              <button type="button" onClick={fireCustom} style={btn(false)}>
                Publish custom
              </button>
            </div>
          )}

          <div style={{ display: "flex", gap: 8, marginTop: 20, paddingTop: 16, borderTop: "1px solid #1e2028", flexWrap: "wrap" }}>
            <button type="button" onClick={fireRandom} style={{ ...btn(false), flex: 1, minWidth: 100 }}>
              Random
            </button>
            <button type="button" onClick={toggleAuto} style={btn(autoMode)}>
              {autoMode ? "Stop auto" : "Auto"}
            </button>
            <input
              type="number"
              value={autoInterval}
              onChange={(e) => setAutoInterval(Math.max(1, +e.target.value))}
              min={1}
              style={{ width: 48, ...inp, textAlign: "center" }}
            />
            <button type="button" onClick={fireAll} style={{ ...btn(true), borderColor: "#3a1818" }}>
              CHAOS
            </button>
          </div>
        </div>

        <div style={{ borderLeft: "1px solid #1e2028", display: "flex", flexDirection: "column" }}>
          <div style={{ padding: "12px 16px", borderBottom: "1px solid #1e2028", display: "flex", justifyContent: "space-between" }}>
            <span style={{ fontFamily: "Outfit", fontWeight: 600, fontSize: 13, color: "#8a8680" }}>Log</span>
            <button type="button" onClick={() => setLog([])} style={{ background: "none", border: "none", color: "#5a564e", cursor: "pointer", fontSize: 11 }}>
              Clear
            </button>
          </div>
          <div style={{ flex: 1, overflow: "auto", padding: 8 }}>
            {log.length === 0 && <div style={{ padding: 20, textAlign: "center", color: "#3a3830", fontSize: 12 }}>No events yet.</div>}
            {log.map((entry, i) => (
              <div
                key={i}
                style={{
                  padding: "6px 10px",
                  marginBottom: 2,
                  borderRadius: 4,
                  fontSize: 11,
                  borderLeft: `2px solid ${
                    entry.type === "pub" ? "#3a8acc" : entry.type === "in" ? "#6a5acd" : entry.type === "success" ? "#4a8c5c" : entry.type === "warn" ? "#d4832f" : entry.type === "error" ? "#c8553d" : "#3a3830"
                  }`,
                }}
              >
                <span style={{ color: "#4a4840" }}>{entry.time}</span>
                {entry.topic && <span style={{ color: "#3a8acc", marginLeft: 6 }}>{entry.topic}</span>}
                <div style={{ color: entry.type === "error" ? "#c8553d" : "#8a8680", wordBreak: "break-all" }}>{entry.msg}</div>
              </div>
            ))}
            <div ref={logEndRef} />
          </div>
        </div>
      </div>
    </div>
  );
}

const inp: CSSProperties = {
  background: "#181a22",
  border: "1px solid #2a2c34",
  borderRadius: 6,
  padding: "8px 12px",
  color: "#c8c4bc",
  fontFamily: "inherit",
  fontSize: 13,
  outline: "none",
  width: "100%",
  boxSizing: "border-box",
};

function btn(danger: boolean): CSSProperties {
  return {
    background: danger ? "#2a1010" : "#1a2a1a",
    color: danger ? "#e85545" : "#4a8c5c",
    border: `1px solid ${danger ? "#3a1818" : "#2a3a2a"}`,
    borderRadius: 6,
    padding: "8px 16px",
    cursor: "pointer",
    fontFamily: "inherit",
    fontSize: 13,
  };
}

function tab(on: boolean, color: string): CSSProperties {
  return {
    background: on ? "#1a1c24" : "transparent",
    color: on ? color : "#5a564e",
    border: on ? `1px solid ${color}33` : "1px solid transparent",
    borderRadius: 6,
    padding: "6px 12px",
    cursor: "pointer",
    fontFamily: "inherit",
    fontSize: 12,
  };
}
