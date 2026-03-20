export type ScenarioCategory = "alerts" | "water" | "motor" | "siren" | "system";

export interface AlertScenario {
  id: string;
  label: string;
  priority: string;
  cls: string;
  zone: string;
  cam: string;
  conf: number;
}

export interface WaterScenario {
  id: string;
  label: string;
  pct: number;
  vol: number;
  temp: number;
}

export interface MotorScenario {
  id: string;
  label: string;
  state: string;
}

export interface SirenScenario {
  id: string;
  label: string;
}

export interface SystemScenario {
  id: string;
  label: string;
}

export type AnyScenario =
  | AlertScenario
  | WaterScenario
  | MotorScenario
  | SirenScenario
  | SystemScenario;

export interface LogEntry {
  time: string;
  type: "info" | "success" | "warn" | "error" | "pub" | "in";
  msg: string;
  topic?: string;
}
