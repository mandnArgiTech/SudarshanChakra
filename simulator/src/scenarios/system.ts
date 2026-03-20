import type { SystemScenario } from "../types";

export const SYSTEM_SCENARIOS: SystemScenario[] = [
  { id: "node_online", label: "Edge node online" },
  { id: "node_offline", label: "Edge node offline" },
  { id: "cam_offline", label: "Camera offline (cam-03)" },
  { id: "cam_online", label: "Camera online (cam-03)" },
  { id: "pa_offline", label: "PA system offline" },
];
