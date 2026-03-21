import type { DeviceZone } from "./deviceInventory";

function uuid(): string {
  if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function ts(): string {
  return new Date().toISOString();
}

/**
 * Build MQTT topic + payload for farm/alerts/{priority} matching alert-service / REST expectations.
 */
export function buildAlertPayloadFromZone(
  zone: DeviceZone,
  nodeId: string,
  detectionClass: string,
  confidence: number,
): { topic: string; payload: Record<string, unknown> } {
  const now = Date.now() / 1000;
  const topic = `farm/alerts/${zone.priority}`;
  const payload: Record<string, unknown> = {
    alert_id: uuid(),
    node_id: nodeId,
    camera_id: zone.cameraId,
    zone_id: zone.id,
    zone_name: zone.name,
    zone_type: zone.zoneType,
    detection_class: detectionClass,
    confidence,
    priority: zone.priority,
    status: "new",
    timestamp: now,
    created_at: ts(),
    simulator: true,
  };
  return { topic, payload };
}
