import type { ScenarioCategory } from "../types";

export interface SequenceStep {
  delayMs: number;
  category: ScenarioCategory;
  scenarioId: string;
}

export const SEQUENCES: Record<string, { label: string; steps: SequenceStep[] }> = {
  intruder_breach: {
    label: "Intruder breach",
    steps: [
      { delayMs: 0, category: "alerts", scenarioId: "intruder_gate" },
      { delayMs: 3000, category: "alerts", scenarioId: "intruder_gate" },
      { delayMs: 5000, category: "alerts", scenarioId: "child_pond" },
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
      { delayMs: 0, category: "alerts", scenarioId: "smoke_field" },
      { delayMs: 3000, category: "alerts", scenarioId: "smoke_field" },
      { delayMs: 6000, category: "alerts", scenarioId: "fire_shed" },
      { delayMs: 7000, category: "siren", scenarioId: "siren_trigger" },
    ],
  },
};
