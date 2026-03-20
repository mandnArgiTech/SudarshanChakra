# Farm Event Simulator — Implementation Plan

## What It Does

A standalone React web app that publishes MQTT events to test the SudarshanChakra dashboard and Android app. Instead of waiting for real snakes or fires, you click a button and watch alerts flow through the entire system.

## How It Works

```
Simulator (browser)
  → publishes MQTT message to RabbitMQ broker
  → RabbitMQ routes to alert-service / device-service
  → alert-service stores in PostgreSQL, broadcasts via WebSocket
  → Dashboard updates in real-time
  → Android app receives via MQTT foreground service → notification
```

## Reference JSX

The `FarmSimulator.jsx` artifact has been created with the complete UI. It includes:

- **8 alert scenarios**: snake, child-at-pond, fire, intruder, scorpion, cow-escaped, smoke, fall-detected
- **5 water level scenarios**: full (95%), normal (65%), low (18%), critical (6%), empty (1%)
- **3 motor scenarios**: started, stopped, error
- **2 siren scenarios**: trigger, stop
- **5 system events**: node online/offline, camera online/offline, PA offline
- **Custom publisher**: arbitrary topic + JSON payload
- **Auto mode**: fire random events at configurable interval (1-60 seconds)
- **Chaos mode**: fire ALL scenarios in rapid succession
- **Event log**: timestamped log of every published message

## Implementation Tasks for Cursor

### Task 1: Create the Simulator App

**Location:** `simulator/` (new directory at repo root)

```
simulator/
├── package.json
├── vite.config.ts
├── index.html
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── FarmSimulator.tsx      ← From the JSX artifact
│   ├── mqtt/
│   │   └── MqttClient.ts     ← MQTT.js WebSocket client wrapper
│   ├── scenarios/
│   │   ├── alerts.ts          ← Alert scenario definitions
│   │   ├── water.ts           ← Water level scenarios
│   │   ├── motor.ts           ← Motor/pump scenarios
│   │   ├── siren.ts           ← Siren scenarios
│   │   └── system.ts          ← System event scenarios
│   └── types.ts               ← TypeScript types
├── Dockerfile                 ← For Docker deployment
└── README.md
```

### Task 2: Wire Real MQTT Publishing

The JSX artifact has the UI but uses placeholder publishing. Wire it to a real MQTT.js client:

```typescript
// simulator/src/mqtt/MqttClient.ts
import mqtt from "mqtt";

export class SimulatorMqttClient {
  private client: mqtt.MqttClient | null = null;

  connect(brokerUrl: string): Promise<void> {
    return new Promise((resolve, reject) => {
      this.client = mqtt.connect(brokerUrl, {
        clientId: `simulator-${Date.now()}`,
        clean: true,
      });
      this.client.on("connect", () => resolve());
      this.client.on("error", (err) => reject(err));
    });
  }

  publish(topic: string, payload: object): void {
    this.client?.publish(topic, JSON.stringify(payload), { qos: 1 });
  }

  disconnect(): void {
    this.client?.end();
    this.client = null;
  }

  get isConnected(): boolean {
    return this.client?.connected ?? false;
  }
}
```

### Task 3: Also Publish via REST API (Dual Mode)

Some events should also hit the REST API directly to ensure they reach the backend even without RabbitMQ MQTT plugin WebSocket:

```typescript
// When an alert is fired, ALSO POST to the backend API
async function publishAlert(payload: AlertPayload, apiBase: string) {
  // MQTT publish (for Android app real-time notification)
  mqttClient.publish(`farm/alerts/${payload.priority}`, payload);

  // REST API publish (for dashboard + database storage)
  await fetch(`${apiBase}/api/v1/alerts`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload),
  });
}
```

### Task 4: Add Water Level Continuous Simulation

Beyond one-shot scenarios, add a mode that simulates water level changing over time:

```
"Filling tank" → publishes level going from 10% → 95% over 2 minutes
"Draining tank" → publishes level going from 80% → 5% over 3 minutes
"Normal fluctuation" → random ±2% every 30 seconds around 65%
```

### Task 5: Add Siren Acknowledgment Listener

Subscribe to `farm/siren/ack` so the simulator shows when the edge node or mock siren acknowledges the command:

```
Simulator fires: farm/siren/trigger
Log shows: → Published farm/siren/trigger
Log shows: ← Received farm/siren/ack {"status": "siren_activated", "node_id": "edge-node-a"}
```

### Task 6: Docker Compose Integration

Add the simulator to `cloud/docker-compose.vps.yml` as an optional service:

```yaml
simulator:
  build: ../simulator
  container_name: farm-simulator
  ports:
    - "3001:80"
  depends_on:
    - rabbitmq
  profiles:
    - dev    # Only starts with: docker compose --profile dev up
```

### Task 7: RabbitMQ WebSocket Configuration

RabbitMQ needs the MQTT WebSocket plugin enabled for browser-based MQTT:

```
# rabbitmq/enabled_plugins (add web_mqtt)
[rabbitmq_management,rabbitmq_mqtt,rabbitmq_web_mqtt].

# rabbitmq.conf (add WebSocket listener)
web_mqtt.tcp.port = 15675
```

Expose port 15675 in docker-compose.

### Task 8: Scenario Sequence Playback

Add a "Scenario" feature that plays a sequence of events simulating a real incident:

```
"Intruder breach" scenario:
  T+0s:  Person detected at perimeter (cam-01, high priority)
  T+3s:  Same person detected closer (cam-01, high priority)
  T+5s:  Person enters zero-tolerance zone (cam-03, CRITICAL)
  T+6s:  Siren auto-triggered
  T+8s:  PA announcement: "Intruder detected at pond area"
  T+15s: Siren acknowledgment received

"Water emergency" scenario:
  T+0s:  Water level 65% (normal)
  T+10s: Water level 45%
  T+20s: Water level 25%
  T+30s: Water level 18% (low alert)
  T+40s: Motor started
  T+50s: Water level 22% (recovering)
  T+80s: Water level 40%

"Fire detection" scenario:
  T+0s:  Smoke detected (cam-07, high)
  T+3s:  Smoke confirmed (temporal filter pass)
  T+6s:  Fire detected (cam-07, CRITICAL)
  T+7s:  Siren triggered
  T+8s:  PA announcement: "Fire detected at north field"
```

---

## Connection Options

| Scenario | Broker URL | Notes |
|:---------|:-----------|:------|
| Local dev stack | `ws://localhost:15675/ws` | RabbitMQ web_mqtt plugin |
| Local Mosquitto | `ws://localhost:9001/mqtt` | Mosquitto WebSocket listener |
| VPS | `ws://vivasvan-tech.in:15675/ws` | RabbitMQ on VPS |

---

## Running It

```bash
cd simulator
npm install
npm run dev
# Opens at http://localhost:3001

# Or with Docker
docker compose --profile dev up simulator
# Opens at http://localhost:3001
```
