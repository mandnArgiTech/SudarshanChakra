import { ALERT_SCENARIOS } from "./alerts";
import { WATER_SCENARIOS } from "./water";
import { MOTOR_SCENARIOS } from "./motor";
import { SIREN_SCENARIOS } from "./siren";
import { SYSTEM_SCENARIOS } from "./system";

export const SCENARIOS = {
  alerts: ALERT_SCENARIOS,
  water: WATER_SCENARIOS,
  motor: MOTOR_SCENARIOS,
  siren: SIREN_SCENARIOS,
  system: SYSTEM_SCENARIOS,
} as const;

export type ScenarioKey = keyof typeof SCENARIOS;
