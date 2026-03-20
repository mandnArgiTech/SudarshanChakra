import type { MotorScenario } from "../types";

export const MOTOR_SCENARIOS: MotorScenario[] = [
  { id: "motor_start", label: "Motor started", state: "RUNNING" },
  { id: "motor_stop", label: "Motor stopped", state: "IDLE" },
  { id: "motor_error", label: "Motor error (overload)", state: "ERROR" },
];
