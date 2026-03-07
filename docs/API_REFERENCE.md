# SudarshanChakra — API Reference

## Complete REST API & MQTT Topic Documentation

---

## Base URL

```
Production: https://vivasvan-tech.in/api/v1
Development: http://localhost:8080/api/v1
```

All requests require `Authorization: Bearer <jwt_token>` header except `/auth/login` and `/auth/register`.

---

## 1. Authentication API

### POST `/auth/login`

Authenticate a user and receive JWT tokens.

**Request:**
```json
{
  "username": "admin",
  "password": "password123"
}
```

**Response (200):**
```json
{
  "token": "eyJhbG...",
  "refreshToken": "eyJhbG...",
  "user": {
    "id": "uuid",
    "username": "admin",
    "email": "admin@farm.local",
    "role": "admin"
  }
}
```

**Errors:** `401 Invalid credentials`, `423 Account locked`

### POST `/auth/register`

Register a new user (admin only).

**Request:**
```json
{
  "username": "ravi",
  "password": "securepass",
  "email": "ravi@farm.local",
  "role": "manager"
}
```

**Roles:** `admin`, `manager`, `viewer`

---

## 2. Alerts API

### GET `/alerts`

List alerts with filtering and pagination.

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `priority` | string | all | Filter: `critical`, `high`, `warning` |
| `status` | string | all | Filter: `new`, `acknowledged`, `resolved`, `false_positive` |
| `nodeId` | string | all | Filter by edge node ID |
| `page` | int | 0 | Page number (0-indexed) |
| `size` | int | 20 | Page size (max 100) |
| `sort` | string | `createdAt,desc` | Sort field and direction |

**Response (200):**
```json
{
  "content": [
    {
      "id": "6c8e00db-7002-4477-9f0e-936d3b7e9429",
      "nodeId": "edge-node-a",
      "cameraId": "cam-03",
      "zoneId": "zone-pond",
      "zoneName": "Pond Danger Zone",
      "zoneType": "zero_tolerance",
      "priority": "critical",
      "detectionClass": "child",
      "confidence": 0.92,
      "bbox": [120.0, 200.0, 280.0, 450.0],
      "snapshotUrl": "http://10.8.0.10:5000/snapshots/uuid.jpg",
      "workerSuppressed": false,
      "status": "new",
      "acknowledgedBy": null,
      "acknowledgedAt": null,
      "createdAt": "2026-03-06T06:47:09Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8
}
```

### GET `/alerts/{id}`

Get a single alert by ID.

### PATCH `/alerts/{id}/acknowledge`

Acknowledge an alert. Requires `ADMIN` or `MANAGER` role.

**Response (200):**
```json
{
  "id": "uuid",
  "status": "acknowledged",
  "acknowledgedBy": "user-uuid",
  "acknowledgedAt": "2026-03-06T07:00:00Z"
}
```

### PATCH `/alerts/{id}/resolve`

Resolve an alert.

### PATCH `/alerts/{id}/false-positive`

Mark an alert as a false positive.

---

## 3. Devices API

### GET `/nodes`

List all edge nodes with status.

**Response:**
```json
[
  {
    "id": "edge-node-a",
    "displayName": "Edge Node A",
    "vpnIp": "10.8.0.10",
    "status": "online",
    "lastHeartbeat": "2026-03-06T06:59:30Z",
    "hardwareInfo": {
      "gpuUtil": 34.0,
      "gpuMemMb": 1100.0,
      "gpuTempC": 52.0
    }
  }
]
```

### GET `/cameras`

List cameras. Optional filter: `?nodeId=edge-node-a`

### GET `/zones`

List virtual fence zones. Optional filter: `?cameraId=cam-03`

### POST `/zones`

Create a new zone.

**Request:**
```json
{
  "cameraId": "cam-03",
  "name": "Pond Danger Zone",
  "zoneType": "zero_tolerance",
  "priority": "critical",
  "targetClasses": ["person", "child"],
  "polygon": [[100,200], [300,200], [300,400], [100,400]]
}
```

### DELETE `/zones/{id}`

Delete a zone.

### GET `/tags`

List worker/child LoRa tags.

### POST `/tags`

Register a new tag.

**Request:**
```json
{
  "tagId": "TAG-W004",
  "workerName": "New Worker",
  "role": "farmer"
}
```

---

## 4. Siren API

### POST `/siren/trigger`

Trigger the PA siren on a specific edge node. Requires `ADMIN` or `MANAGER` role.

**Request:**
```json
{
  "nodeId": "edge-node-a",
  "sirenUrl": "http://example.com/audio/siren.mp3",
  "alertId": "uuid-optional"
}
```

**Response (200):**
```json
{
  "status": "siren_triggered",
  "nodeId": "edge-node-a"
}
```

### POST `/siren/stop`

Stop the siren on a node.

**Request:**
```json
{
  "nodeId": "edge-node-a"
}
```

### GET `/siren/history`

Get siren activation history. Paginated.

---

## 5. Users API

### GET `/users`

List all users (admin only).

### PATCH `/users/me/mqtt-client-id`

Update MQTT client ID for direct push notifications via the RabbitMQ broker.

**Request:**
```json
{
  "mqttClientId": "android-client-uuid"
}
```

**Response:** `204 No Content`

**Notes:**
- Called by the Android app after login when `MqttForegroundService` starts.
- Recommended format: `sc-android-<uuid>`.
- The value is used for direct push notification routing and client tracking.

---

## 6. WebSocket API

### Endpoint

```
ws://vivasvan-tech.in/ws/alerts (STOMP over SockJS)
```

### Subscriptions

| Topic | Payload | Description |
|-------|---------|-------------|
| `/topic/alerts` | Alert JSON | Real-time new alerts |
| `/topic/nodes` | Node status JSON | Node online/offline changes |

---

## 7. MQTT Topic Reference

### Edge → Cloud (Published by Edge Nodes)

| Topic | QoS | Payload | Description |
|-------|-----|---------|-------------|
| `farm/alerts/critical` | 1 | Alert JSON | Critical priority alerts |
| `farm/alerts/high` | 1 | Alert JSON | High priority alerts |
| `farm/alerts/warning` | 1 | Alert JSON | Warning alerts |
| `farm/nodes/{id}/status` | 1 (retain) | `{node_id, status, timestamp}` | Online/offline |
| `farm/nodes/{id}/heartbeat` | 0 | `{node_id, gpu_util, gpu_mem_mb, ...}` | Health metrics |
| `farm/events/worker_identified` | 0 | `{node_id, zone_id, workers, ...}` | Suppression audit |
| `farm/siren/ack` | 1 | `{node_id, status, timestamp}` | Siren command ack |

### Cloud → Edge (Published by Backend)

| Topic | QoS | Payload | Description |
|-------|-----|---------|-------------|
| `farm/siren/trigger` | 1 | `{command, targetNode, sirenUrl}` | Activate siren |
| `farm/siren/stop` | 1 | `{command, targetNode}` | Stop siren |
| `farm/admin/update` | 1 | `{model, version}` | OTA update command |

### Mobile Subscriptions (Android App)

| Topic Filter | QoS | Description |
|-------------|-----|-------------|
| `farm/alerts/#` | 1 | Primary alert topic for Android foreground MQTT service |
| `alerts/#` | 1 | Legacy fallback topic for backward compatibility |

### Alert Payload Schema

```json
{
  "alert_id": "uuid-v4",
  "node_id": "edge-node-a",
  "camera_id": "cam-03",
  "zone_id": "zone-pond",
  "zone_name": "Pond Danger Zone",
  "zone_type": "zero_tolerance",
  "priority": "critical",
  "detection_class": "child",
  "confidence": 0.92,
  "bbox": [120.0, 200.0, 280.0, 450.0],
  "bottom_center": [200.0, 450.0],
  "snapshot_url": "http://10.8.0.10:5000/snapshots/uuid.jpg",
  "worker_suppressed": false,
  "timestamp": 1709712000.0,
  "metadata": {
    "lora_workers_nearby": [],
    "frame_number": 14523
  }
}
```

---

## 8. Error Responses

All error responses follow this format:

```json
{
  "timestamp": "2026-03-06T07:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "JWT token expired",
  "path": "/api/v1/alerts"
}
```

| Status | Meaning |
|--------|---------|
| 400 | Bad Request — Invalid parameters |
| 401 | Unauthorized — Missing or invalid JWT |
| 403 | Forbidden — Insufficient role |
| 404 | Not Found — Resource does not exist |
| 409 | Conflict — Duplicate resource |
| 429 | Too Many Requests — Rate limit exceeded |
| 500 | Internal Server Error |
