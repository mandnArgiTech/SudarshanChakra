import type { AlertScenario } from "../types";

export const ALERT_SCENARIOS: AlertScenario[] = [
  { id: "snake_storage", label: "Snake near storage", priority: "critical", cls: "snake", zone: "Storage Perimeter", cam: "cam-05", conf: 0.78 },
  { id: "child_pond", label: "Child near pond", priority: "critical", cls: "child", zone: "Pond Safety", cam: "cam-03", conf: 0.85 },
  { id: "fire_shed", label: "Fire at shed", priority: "critical", cls: "fire", zone: "Equipment Shed", cam: "cam-06", conf: 0.91 },
  { id: "intruder_gate", label: "Intruder at gate", priority: "high", cls: "person", zone: "Front Gate", cam: "cam-01", conf: 0.88 },
  { id: "scorpion_path", label: "Scorpion on path", priority: "high", cls: "scorpion", zone: "Walkway Hazard", cam: "cam-04", conf: 0.65 },
  { id: "cow_escaped", label: "Cow left pen", priority: "warning", cls: "cow", zone: "Livestock Pen", cam: "cam-02", conf: 0.92 },
  { id: "smoke_field", label: "Smoke in field", priority: "high", cls: "smoke", zone: "North Field", cam: "cam-07", conf: 0.74 },
  { id: "fall_child", label: "Child fall detected", priority: "critical", cls: "fall_detected", zone: "Pond Safety", cam: "esp32", conf: 1.0 },
];
