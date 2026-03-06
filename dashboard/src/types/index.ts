export interface Alert {
  id: string;
  nodeId: string;
  cameraId: string;
  zoneId: string;
  zoneName: string;
  zoneType: string;
  priority: 'critical' | 'high' | 'warning';
  detectionClass: string;
  confidence: number;
  bbox: number[] | null;
  snapshotUrl: string | null;
  thumbnailUrl: string | null;
  workerSuppressed: boolean;
  status: 'new' | 'acknowledged' | 'resolved' | 'false_positive';
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
  notes: string | null;
  metadata: string | null;
  createdAt: string;
}

export interface EdgeNode {
  id: string;
  farmId: string;
  displayName: string;
  vpnIp: string;
  localIp: string;
  status: 'online' | 'offline' | 'unknown';
  lastHeartbeat: string | null;
  hardwareInfo: string | null;
  config: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Camera {
  id: string;
  nodeId: string;
  name: string;
  rtspUrl: string;
  model: string | null;
  locationDescription: string | null;
  fpsTarget: number;
  resolution: string;
  enabled: boolean;
  status: 'active' | 'offline' | 'alert' | 'unknown';
  createdAt: string;
}

export interface Zone {
  id: string;
  cameraId: string;
  name: string;
  zoneType: string;
  priority: string;
  targetClasses: string[];
  polygon: string;
  color: string;
  enabled: boolean;
  suppressWithWorkerTag: boolean;
  dedupWindowSeconds: number;
  createdAt: string;
  updatedAt: string;
}

export interface WorkerTag {
  tagId: string;
  workerName: string;
  farmId: string;
  role: string | null;
  phone: string | null;
  active: boolean;
  lastSeen: string | null;
  lastRssi: number | null;
  lastNode: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SirenAction {
  id: string;
  triggeredBy: string | null;
  triggeredBySystem: boolean;
  targetNode: string;
  action: string;
  alertId: string | null;
  acknowledged: boolean;
  acknowledgedAt: string | null;
  sirenUrl: string | null;
  createdAt: string;
}

export interface User {
  id: string;
  username: string;
  email: string;
  role: 'admin' | 'manager' | 'viewer';
  farmId: string;
  active: boolean;
}

export interface AuthResponse {
  token: string;
  refreshToken: string;
  user: User;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
  role: string;
  farmId: string;
}

export interface SirenRequest {
  nodeId: string;
  sirenUrl?: string;
  alertId?: string;
}

export interface SirenResponse {
  status: string;
  nodeId: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}
