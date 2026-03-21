import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { buildAlertPayloadFromZone } from "./api/alertFromZone";
import {
  filterZonesForNode,
  loadDeviceInventory,
  type DeviceCamera,
  type DeviceNode,
  type DeviceZone,
} from "./api/deviceInventory";
import { SimulatorMqttClient } from "./mqtt/MqttClient";
import { ALERT_SCENARIOS, SCENARIOS, type ScenarioKey } from "./scenarios";
import { SEQUENCES } from "./scenarios/sequences";
import type { LogEntry, ScenarioCategory } from "./types";
import type { AlertScenario } from "./types";
import type { WaterScenario } from "./types";
import type { MotorScenario } from "./types";
import type { SirenScenario } from "./types";
import type { SystemScenario } from "./types";

const BROKER_DEFAULT = "ws://localhost:15675/ws";
/** Dev: empty → fetch `/api/...` via Vite proxy to gateway :8080 */
const API_DEFAULT = import.meta.env.DEV ? "" : "http://localhost:8080";
/** Match `RABBITMQ_DEFAULT_USER` / `RABBITMQ_DEFAULT_PASS` in cloud/.env or Docker env */
const MQTT_USER_DEFAULT = "admin";
const MQTT_PASS_DEFAULT = "devpassword123";

const DOCS_SEED = "/docs/POSTGRES_DOCKER_SQL.md";

const PRIORITY_COLORS: Record<string, { bg: string; border: string; text: string; dot: string }> = {
  critical: { bg: "#3a0a08", border: "#c8553d", text: "#e88070", dot: "#ff4433" },
  high: { bg: "#3a2008", border: "#d4832f", text: "#e8a860", dot: "#ff8833" },
  warning: { bg: "#3a3008", border: "#b8962e", text: "#d4c060", dot: "#eebb33" },
  info: { bg: "#142428", border: "#3a8acc", text: "#8ac0e8", dot: "#55aaff" },
};

const CATEGORY_META: Record<ScenarioKey | "custom", { icon: string; color: string; label: string }> = {
  alerts: { icon: "⚠", color: "#c8553d", label: "Alerts" },
  water: { icon: "◈", color: "#3a8acc", label: "Water Level" },
  motor: { icon: "⟳", color: "#6a5acd", label: "Motor/Pump" },
  siren: { icon: "◉", color: "#cc3344", label: "Siren" },
  system: { icon: "◆", color: "#4a8c5c", label: "System Events" },
  custom: { icon: "✎", color: "#8a8aaa", label: "Custom" },
};

type NonAlertCategory = Exclude<ScenarioCategory, "alerts">;
type NonAlertScenario = WaterScenario | MotorScenario | SirenScenario | SystemScenario;

function buildPayload(
  category: NonAlertCategory,
  scenario: NonAlertScenario,
  ctx: { nodeId: string; defaultCameraId?: string },
): { topic: string; payload: Record<string, unknown> } {
  const now = Date.now() / 1000;
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
      payload: { motor_id: "motor-1", state: s.state, node_id: ctx.nodeId, timestamp: now },
    };
  }
  if (category === "siren") {
    const s = scenario as SirenScenario;
    const cmd = s.id === "siren_trigger" ? "trigger" : "stop";
    return {
      topic: `farm/siren/${cmd}`,
      payload: {
        command: cmd,
        node_id: ctx.nodeId,
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
        topic: `farm/nodes/${ctx.nodeId}/status`,
        payload: { node_id: ctx.nodeId, status: s.id === "node_online" ? "online" : "offline", timestamp: now },
      };
    }
    if (s.id.startsWith("cam_")) {
      const cam = ctx.defaultCameraId;
      if (!cam) {
        return { topic: "farm/test", payload: { _skip: true, reason: "no_camera_in_inventory" } };
      }
      return {
        topic: "farm/events/camera_status",
        payload: { camera_id: cam, event: s.id === "cam_online" ? "online" : "offline", ts: now },
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
  const url = base ? `${base}/api/v1/alerts` : "/api/v1/alerts";
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token?.trim()) headers.Authorization = `Bearer ${token.trim()}`;
  const res = await fetch(url, { method: "POST", headers, body: JSON.stringify(payload) });
  const txt = await res.text().catch(() => "");
  return `${res.status} ${res.statusText}${txt ? ` — ${txt.slice(0, 200)}` : ""}`;
}

export default function FarmSimulator() {
  const [broker, setBroker] = useState(BROKER_DEFAULT);
  const [mqttUser, setMqttUser] = useState(MQTT_USER_DEFAULT);
  const [mqttPassword, setMqttPassword] = useState(MQTT_PASS_DEFAULT);
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

  const [inventoryNodes, setInventoryNodes] = useState<DeviceNode[]>([]);
  const [inventoryCameras, setInventoryCameras] = useState<DeviceCamera[]>([]);
  const [inventoryZones, setInventoryZones] = useState<DeviceZone[]>([]);
  const [inventoryLoading, setInventoryLoading] = useState(false);
  const [inventoryError, setInventoryError] = useState<string | null>(null);
  const [zoneClassPick, setZoneClassPick] = useState<Record<string, string>>({});
  const [zoneConfidence, setZoneConfidence] = useState<Record<string, number>>({});
  const [selectedZoneId, setSelectedZoneId] = useState<string | null>(null);

  const clientRef = useRef<SimulatorMqttClient | null>(null);
  const autoRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const waterRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const seqTimersRef = useRef<number[]>([]);
  const logEndRef = useRef<HTMLDivElement | null>(null);

  const camerasForNode = useMemo(
    () => inventoryCameras.filter((c) => c.nodeId === nodeId),
    [inventoryCameras, nodeId],
  );

  const nodeZones = useMemo(() => filterZonesForNode(camerasForNode, inventoryZones), [camerasForNode, inventoryZones]);

  const defaultCameraId = camerasForNode[0]?.id;

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [log]);

  useEffect(() => {
    setZoneClassPick((prev) => {
      const next = { ...prev };
      for (const z of nodeZones) {
        if (next[z.id] === undefined) {
          next[z.id] = z.targetClasses?.[0] ?? "";
        }
      }
      return next;
    });
    setZoneConfidence((prev) => {
      const next = { ...prev };
      for (const z of nodeZones) {
        if (next[z.id] === undefined) next[z.id] = 0.9;
      }
      return next;
    });
  }, [nodeZones]);

  const addLog = useCallback((entry: Omit<LogEntry, "time">) => {
    setLog((prev) => [...prev.slice(-199), { ...entry, time: new Date().toLocaleTimeString() }]);
  }, []);

  const refreshInventory = useCallback(async () => {
    setInventoryLoading(true);
    setInventoryError(null);
    try {
      const { nodes, cameras, zones } = await loadDeviceInventory(apiBase, bearerToken || undefined);
      setInventoryNodes(nodes);
      setInventoryCameras(cameras);
      setInventoryZones(zones);
      addLog({
        type: "success",
        msg: `Inventory: ${nodes.length} nodes, ${cameras.length} cameras, ${zones.length} zones`,
      });
      setNodeId((prev) => (nodes.length && !nodes.some((n) => n.id === prev) ? nodes[0]!.id : prev));
    } catch (e) {
      const msg = (e as Error).message;
      setInventoryError(msg);
      addLog({ type: "error", msg: `Inventory load failed: ${msg}` });
    } finally {
      setInventoryLoading(false);
    }
  }, [apiBase, bearerToken, addLog]);

  const publishMqtt = useCallback(
    (topic: string, payload: Record<string, unknown>) => {
      if (payload._skip === true) return;
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
      if (payload._skip === true) {
        addLog({ type: "warn", msg: "Skipped publish (no camera for system event — refresh inventory)" });
        return;
      }
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

  const publishZoneAlert = useCallback(
    async (zone: DeviceZone, detectionClass: string, confidence: number) => {
      const cls = detectionClass.trim();
      const targets = zone.targetClasses?.filter(Boolean) ?? [];
      if (!cls) {
        addLog({ type: "warn", msg: "Choose or enter a detection class for this zone" });
        return;
      }
      if (targets.length > 0 && !targets.includes(cls)) {
        addLog({
          type: "warn",
          msg: `detection_class "${cls}" must be one of: ${targets.join(", ")}`,
        });
        return;
      }
      const { topic, payload } = buildAlertPayloadFromZone(zone, nodeId, cls, confidence);
      await publish(topic, payload);
    },
    [nodeId, publish, addLog],
  );

  const fireScenario = useCallback(
    (category: NonAlertCategory, scenario: NonAlertScenario) => {
      const { topic, payload } = buildPayload(category, scenario, { nodeId, defaultCameraId });
      void publish(topic, payload);
    },
    [nodeId, defaultCameraId, publish],
  );

  const doConnect = useCallback(() => {
    setConnecting(true);
    addLog({ type: "info", msg: `Connecting MQTT ${broker}…` });
    const c = new SimulatorMqttClient();
    clientRef.current = c;
    c.connect(broker, { username: mqttUser, password: mqttPassword })
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
  }, [broker, mqttUser, mqttPassword, addLog]);

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
    const nonAlertCats = (Object.keys(SCENARIOS) as ScenarioKey[]).filter((k) => k !== "alerts");
    const pool: ScenarioKey[] = nodeZones.length > 0 ? ([...nonAlertCats, "alerts"] as ScenarioKey[]) : nonAlertCats;
    if (pool.length === 0) {
      addLog({ type: "warn", msg: "No scenarios / inventory — load zones first" });
      return;
    }
    const cat = pool[Math.floor(Math.random() * pool.length)]!;
    if (cat === "alerts") {
      const z = nodeZones[Math.floor(Math.random() * nodeZones.length)]!;
      const cls = z.targetClasses?.[0] ?? zoneClassPick[z.id] ?? "";
      const conf = zoneConfidence[z.id] ?? 0.9;
      if (!cls.trim()) {
        addLog({ type: "warn", msg: "Random alert skipped: zone has no target_classes" });
        return;
      }
      void publishZoneAlert(z, cls, conf);
      return;
    }
    const items = SCENARIOS[cat];
    const item = items[Math.floor(Math.random() * items.length)]!;
    fireScenario(cat, item as NonAlertScenario);
  }, [nodeZones, zoneClassPick, zoneConfidence, publishZoneAlert, fireScenario, addLog]);

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
    let delay = 0;
    (Object.keys(SCENARIOS) as ScenarioKey[]).forEach((cat) => {
      if (cat === "alerts") {
        nodeZones.forEach((z) => {
          const cls = z.targetClasses?.[0] ?? zoneClassPick[z.id] ?? "";
          if (!cls.trim()) return;
          window.setTimeout(() => void publishZoneAlert(z, cls, zoneConfidence[z.id] ?? 0.9), delay);
          delay += 300;
        });
        return;
      }
      SCENARIOS[cat].forEach((s) => {
        window.setTimeout(() => fireScenario(cat, s as NonAlertScenario), delay);
        delay += 300;
      });
    });
  }, [fireScenario, publishZoneAlert, nodeZones, zoneClassPick, zoneConfidence, addLog]);

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
      const { topic, payload } = buildPayload("water", scenario, { nodeId, defaultCameraId });
      publishMqtt(topic, payload);
    }, waterSim === "fluctuate" ? 30000 : 2000);
    return () => {
      if (waterRef.current) clearInterval(waterRef.current);
      waterRef.current = null;
    };
  }, [waterSim, nodeId, defaultCameraId, publishMqtt]);

  const playSequence = useCallback(
    (key: string) => {
      seqTimersRef.current.forEach((id) => window.clearTimeout(id));
      seqTimersRef.current = [];
      const seq = SEQUENCES[key];
      if (!seq) return;
      addLog({ type: "info", msg: `Sequence: ${seq.label}` });
      seq.steps.forEach((step) => {
        const id = window.setTimeout(() => {
          if (step.category === "alerts") {
            const z = nodeZones.find((nz) => nz.id === step.zoneId);
            if (!z) {
              addLog({ type: "warn", msg: `Sequence skip: zone "${step.zoneId}" not in inventory for node ${nodeId}` });
              return;
            }
            void publishZoneAlert(z, step.detectionClass, zoneConfidence[z.id] ?? 0.9);
            return;
          }
          const list = SCENARIOS[step.category];
          const sc = list.find((x) => x.id === step.scenarioId);
          if (sc) fireScenario(step.category, sc as NonAlertScenario);
        }, step.delayMs);
        seqTimersRef.current.push(id);
      });
    },
    [addLog, fireScenario, nodeZones, nodeId, publishZoneAlert, zoneConfidence],
  );

  const applyPresetClass = useCallback(
    (preset: AlertScenario) => {
      if (!selectedZoneId) {
        addLog({ type: "warn", msg: "Select a zone first (click a zone card), then apply a preset class" });
        return;
      }
      const z = nodeZones.find((nz) => nz.id === selectedZoneId);
      if (!z) return;
      const targets = z.targetClasses?.filter(Boolean) ?? [];
      if (targets.length > 0 && !targets.includes(preset.cls)) {
        addLog({
          type: "warn",
          msg: `Preset "${preset.label}" uses class "${preset.cls}" — not in zone targets [${targets.join(", ")}]`,
        });
        return;
      }
      setZoneClassPick((p) => ({ ...p, [selectedZoneId]: preset.cls }));
      addLog({ type: "info", msg: `Preset "${preset.label}" → detection_class=${preset.cls} for zone ${z.name}` });
    },
    [selectedZoneId, nodeZones, addLog],
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
            <div style={{ fontSize: 11, color: "#6a665e" }}>SudarshanChakra — MQTT + REST (inventory-driven alerts)</div>
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
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 8 }}>
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
              API base (gateway){import.meta.env.DEV && <span style={{ color: "#4a8c5c" }}> · dev: leave empty for /api proxy</span>}
              <input
                value={apiBase}
                onChange={(e) => setApiBase(e.target.value)}
                style={inp}
                placeholder={import.meta.env.DEV ? "(empty) or http://host:8080" : "http://localhost:8080"}
              />
            </label>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 12 }}>
            <label style={{ fontSize: 11, color: "#6a665e" }}>
              MQTT user (RabbitMQ)
              <input
                value={mqttUser}
                onChange={(e) => setMqttUser(e.target.value)}
                style={inp}
                placeholder="admin"
                autoComplete="username"
              />
            </label>
            <label style={{ fontSize: 11, color: "#6a665e" }}>
              MQTT password
              <input
                type="password"
                value={mqttPassword}
                onChange={(e) => setMqttPassword(e.target.value)}
                style={inp}
                placeholder="RABBITMQ_DEFAULT_PASS"
                autoComplete="current-password"
              />
            </label>
          </div>
          <input
            value={bearerToken}
            onChange={(e) => setBearerToken(e.target.value)}
            placeholder="Optional JWT for REST (paste from dashboard login)"
            style={{ ...inp, width: "100%", marginBottom: 8 }}
          />

          <div
            style={{
              marginBottom: 16,
              padding: 12,
              background: "#141820",
              border: "1px solid #2a2c34",
              borderRadius: 8,
            }}
          >
            <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center", marginBottom: 8 }}>
              <span style={{ fontFamily: "Outfit", fontWeight: 600, fontSize: 12, color: "#8a8680" }}>Device inventory</span>
              <button type="button" onClick={() => void refreshInventory()} disabled={inventoryLoading} style={btn(false)}>
                {inventoryLoading ? "Loading…" : "Refresh inventory"}
              </button>
              <label style={{ fontSize: 11, color: "#6a665e", display: "flex", alignItems: "center", gap: 6 }}>
                Node
                <select
                  value={nodeId}
                  onChange={(e) => setNodeId(e.target.value)}
                  style={{ ...inp, minWidth: 160 }}
                >
                  {inventoryNodes.length === 0 && <option value={nodeId}>{nodeId}</option>}
                  {inventoryNodes.map((n) => (
                    <option key={n.id} value={n.id}>
                      {n.displayName || n.id}
                    </option>
                  ))}
                </select>
              </label>
              <span style={{ fontSize: 11, color: "#5a564e" }}>
                {camerasForNode.length} cams · {nodeZones.length} zones
              </span>
            </div>
            {inventoryError && <div style={{ fontSize: 11, color: "#c8553d", marginBottom: 6 }}>{inventoryError}</div>}
            {nodeZones.length === 0 && !inventoryLoading && (
              <div style={{ fontSize: 11, color: "#6a665e", lineHeight: 1.5 }}>
                No zones for this node in the API. Seed cameras/zones (see{" "}
                <code style={{ color: "#8a8680" }}>{DOCS_SEED}</code>) or register via device-service /{" "}
                <code style={{ color: "#8a8680" }}>scripts/register_camera.sh</code>.
              </div>
            )}
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 120px auto", gap: 8, marginBottom: 16 }}>
            <div style={{ fontSize: 11, color: "#5a564e", padding: "8px 0" }}>
              MQTT uses Node ID above; alerts use the same node_id in payloads.
            </div>
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

          {activeTab === "alerts" ? (
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <div style={{ fontSize: 11, color: "#6a665e" }}>
                Fire alerts from <strong>real zones</strong> for the selected node (device-service / DB). Select a zone for presets.
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 8 }}>
                {nodeZones.map((z) => {
                  const pc = PRIORITY_COLORS[z.priority] || PRIORITY_COLORS.info;
                  const sel = selectedZoneId === z.id;
                  const targets = z.targetClasses?.filter(Boolean) ?? [];
                  const clsVal = zoneClassPick[z.id] ?? "";
                  const confVal = zoneConfidence[z.id] ?? 0.9;
                  return (
                    <div
                      key={z.id}
                      role="button"
                      tabIndex={0}
                      onClick={() => setSelectedZoneId(z.id)}
                      onKeyDown={(e) => e.key === "Enter" && setSelectedZoneId(z.id)}
                      style={{
                        background: pc.bg,
                        border: `1px solid ${sel ? "#d4832f" : pc.border}`,
                        borderRadius: 8,
                        padding: "12px 14px",
                        cursor: "pointer",
                        color: "#c8c4bc",
                      }}
                    >
                      <div style={{ fontSize: 13, fontWeight: 600, color: pc.text }}>{z.name}</div>
                      <div style={{ fontSize: 10, color: "#5a564e", marginBottom: 8 }}>
                        {z.id} · cam {z.cameraId} · {z.zoneType} · {z.priority}
                      </div>
                      <label style={{ fontSize: 10, color: "#6a665e", display: "block", marginBottom: 4 }}>detection_class</label>
                      {targets.length > 0 ? (
                        <select
                          value={clsVal}
                          onChange={(e) => {
                            e.stopPropagation();
                            setZoneClassPick((p) => ({ ...p, [z.id]: e.target.value }));
                          }}
                          onClick={(e) => e.stopPropagation()}
                          style={{ ...inp, marginBottom: 8, fontSize: 12 }}
                        >
                          {targets.map((t) => (
                            <option key={t} value={t}>
                              {t}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <input
                          value={clsVal}
                          onChange={(e) => {
                            e.stopPropagation();
                            setZoneClassPick((p) => ({ ...p, [z.id]: e.target.value }));
                          }}
                          onClick={(e) => e.stopPropagation()}
                          placeholder="required (no target_classes in zone)"
                          style={{ ...inp, marginBottom: 8, fontSize: 12 }}
                        />
                      )}
                      <label style={{ fontSize: 10, color: "#6a665e", display: "block", marginBottom: 4 }}>confidence</label>
                      <input
                        type="number"
                        step={0.01}
                        min={0}
                        max={1}
                        value={confVal}
                        onChange={(e) => {
                          e.stopPropagation();
                          setZoneConfidence((p) => ({ ...p, [z.id]: +e.target.value }));
                        }}
                        onClick={(e) => e.stopPropagation()}
                        style={{ ...inp, marginBottom: 8, fontSize: 12 }}
                      />
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          void publishZoneAlert(z, clsVal, confVal);
                        }}
                        style={{ ...btn(false), width: "100%", fontSize: 12 }}
                      >
                        Fire alert
                      </button>
                    </div>
                  );
                })}
              </div>
              {nodeZones.length > 0 && (
                <div>
                  <div style={{ fontSize: 11, color: "#6a665e", marginBottom: 6 }}>
                    Presets (set <strong>detection_class</strong> for selected zone — must match zone target_classes)
                  </div>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                    {ALERT_SCENARIOS.map((p) => (
                      <button key={p.id} type="button" onClick={() => applyPresetClass(p)} style={{ ...btn(false), fontSize: 11 }}>
                        {p.label}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : activeTab !== "custom" ? (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 8 }}>
              {SCENARIOS[activeTab].map((s) => {
                const meta = CATEGORY_META[activeTab];
                return (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => fireScenario(activeTab as NonAlertCategory, s as NonAlertScenario)}
                    style={{
                      background: "#141620",
                      border: `1px solid ${meta.color}44`,
                      borderRadius: 8,
                      padding: "12px 14px",
                      cursor: "pointer",
                      textAlign: "left",
                      color: "#c8c4bc",
                      fontFamily: "inherit",
                    }}
                  >
                    <div style={{ fontSize: 13, fontWeight: 500, color: "#c8c4bc" }}>{s.label}</div>
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
