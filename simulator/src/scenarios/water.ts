import type { WaterScenario } from "../types";

export const WATER_SCENARIOS: WaterScenario[] = [
  { id: "tank_full", label: "Tank full (95%)", pct: 95, vol: 2316, temp: 28.5 },
  { id: "tank_normal", label: "Tank normal (65%)", pct: 65, vol: 1585, temp: 27.2 },
  { id: "tank_low", label: "Tank low (18%)", pct: 18, vol: 439, temp: 26.0 },
  { id: "tank_critical", label: "Tank critical (6%)", pct: 6, vol: 146, temp: 25.5 },
  { id: "tank_empty", label: "Tank empty (1%)", pct: 1, vol: 24, temp: 24.0 },
];
