import type { ScenarioCategory } from "../types";

/** Non-alert steps reference SCENARIOS[category] by id. */
export type SequenceStep =
  | { delayMs: number; category: "alerts"; zoneId: string; detectionClass: string }
  | { delayMs: number; category: Exclude<ScenarioCategory, "alerts">; scenarioId: string };

export const SEQUENCES: Record<string, { label: string; steps: SequenceStep[] }> = {
  intruder_breach: {
    label: "Intruder breach",
    steps: [
      { delayMs: 0, category: "alerts", zoneId: "front_gate", detectionClass: "person" },
      { delayMs: 3000, category: "alerts", zoneId: "front_gate", detectionClass: "person" },
      { delayMs: 5000, category: "alerts", zoneId: "pond_safety", detectionClass: "child" },
      { delayMs: 6000, category: "siren", scenarioId: "siren_trigger" },
    ],
  },
  water_emergency: {
    label: "Water emergency",
    steps: [
      { delayMs: 0, category: "water", scenarioId: "tank_normal" },
      { delayMs: 10000, category: "water", scenarioId: "tank_low" },
      { delayMs: 20000, category: "water", scenarioId: "tank_critical" },
      { delayMs: 30000, category: "motor", scenarioId: "motor_start" },
      { delayMs: 45000, category: "water", scenarioId: "tank_low" },
    ],
  },
  fire_detection: {
    label: "Fire detection",
    steps: [
      { delayMs: 0, category: "alerts", zoneId: "north_field", detectionClass: "smoke" },
      { delayMs: 3000, category: "alerts", zoneId: "north_field", detectionClass: "smoke" },
      { delayMs: 6000, category: "alerts", zoneId: "equipment_shed", detectionClass: "fire" },
      { delayMs: 7000, category: "siren", scenarioId: "siren_trigger" },
    ],
  },
};
