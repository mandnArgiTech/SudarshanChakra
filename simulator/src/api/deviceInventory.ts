/**
 * Load edge nodes, cameras, and zones from device-service via API gateway.
 * Same auth as alert POST (optional Bearer JWT).
 */

export interface DeviceNode {
  id: string;
  farmId?: string;
  displayName?: string;
  vpnIp?: string;
  localIp?: string;
  status?: string;
}

export interface DeviceCamera {
  id: string;
  nodeId?: string;
  name: string;
  rtspUrl: string;
  model?: string;
  locationDescription?: string;
  fpsTarget?: number;
  resolution?: string;
  enabled?: boolean;
  status?: string;
}

export interface DeviceZone {
  id: string;
  cameraId: string;
  name: string;
  zoneType: string;
  priority: string;
  targetClasses?: string[] | null;
  polygon?: string;
  color?: string;
  enabled?: boolean;
  suppressWithWorkerTag?: boolean;
  dedupWindowSeconds?: number;
}

export interface InventoryLoadResult {
  nodes: DeviceNode[];
  cameras: DeviceCamera[];
  zones: DeviceZone[];
}

function normalizeBase(base: string): string {
  return base.replace(/\/$/, "");
}

function authHeaders(token?: string): HeadersInit {
  const h: Record<string, string> = { Accept: "application/json" };
  if (token?.trim()) h.Authorization = `Bearer ${token.trim()}`;
  return h;
}

async function fetchJson<T>(url: string, token?: string): Promise<T> {
  const res = await fetch(url, { headers: authHeaders(token) });
  if (!res.ok) {
    const t = await res.text().catch(() => "");
    throw new Error(`${res.status} ${res.statusText}${t ? `: ${t.slice(0, 200)}` : ""}`);
  }
  return res.json() as Promise<T>;
}

/** Zones visible for a node: zone.cameraId must belong to one of that node's cameras. */
export function filterZonesForNode(cameras: DeviceCamera[], zones: DeviceZone[]): DeviceZone[] {
  const ids = new Set(cameras.map((c) => c.id));
  return zones.filter((z) => z.enabled !== false && ids.has(z.cameraId));
}

export async function loadDeviceInventory(apiBase: string, token?: string): Promise<InventoryLoadResult> {
  const base = normalizeBase(apiBase);
  const prefix = base ? `${base}/api/v1` : "/api/v1";
  const [nodes, zones] = await Promise.all([
    fetchJson<DeviceNode[]>(`${prefix}/nodes`, token),
    fetchJson<DeviceZone[]>(`${prefix}/zones`, token),
  ]);
  const cameras: DeviceCamera[] = [];
  for (const n of nodes) {
    const list = await fetchJson<DeviceCamera[]>(`${prefix}/cameras?nodeId=${encodeURIComponent(n.id)}`, token);
    cameras.push(...list);
  }
  return { nodes, cameras, zones };
}

export async function loadCamerasForNode(apiBase: string, nodeId: string, token?: string): Promise<DeviceCamera[]> {
  const base = normalizeBase(apiBase);
  const prefix = base ? `${base}/api/v1` : "/api/v1";
  return fetchJson<DeviceCamera[]>(`${prefix}/cameras?nodeId=${encodeURIComponent(nodeId)}`, token);
}
