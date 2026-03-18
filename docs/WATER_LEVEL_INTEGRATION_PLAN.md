# Water Level Monitor Integration Plan

## What We're Integrating

The **AutoWaterLevelControl** project is an ESP8266 firmware that monitors farm water tanks using ultrasonic/lidar sensors. It already publishes water level data via MQTT. We need to bring this data into the SudarshanChakra dashboard, alert system, and Android app.

## Current AutoWaterLevelControl Setup

```
Hardware:  ESP8266 (NodeMCU) + US-100 ultrasonic sensor
Tank:      Circular, 1350mm diameter, 1704mm height, 2438 liters
Firmware:  PlatformIO/Arduino (C++), web UI on port 80
MQTT:      Publishes to broker at configurable IP, port 1883
Repo:      github.com/mandnArgiTech/AutoWaterLevelControl
Branch:    feature/fluid-level-monitor-esp8266
```

### What It Already Publishes via MQTT

**Topic format:** `{deviceTag}/{topicPrefix}/{subtopic}`
Example: `tank1_a9ad51/water/level`

**Level payload (every 60 seconds):**
```json
{
    "percentFilled": 72.5,
    "percentRemaining": 27.5,
    "waterHeightMm": 1235,
    "waterHeightCm": 123.5,
    "volumeLiters": 1768.4,
    "volumeRemaining": 669.6,
    "distanceMm": 469,
    "distanceCm": 46.9,
    "temperatureC": 28.5,
    "state": "normal",
    "error": 0
}
```

**Status payload (retained, on connect/disconnect):**
```json
{
    "online": true,
    "deviceTag": "tank1_a9ad51",
    "deviceName": "tank1",
    "ip": "192.168.1.50",
    "uptime": 86400
}
```

**Topics:**
```
tank1_a9ad51/water/level    → level data (every 60s)
tank1_a9ad51/water/status   → online/offline (retained, LWT)
```

---

## Integration Architecture

```
┌─────────────────────┐
│ ESP8266 Water Tank   │
│ US-100 Sensor        │
│ Publishes every 60s  │
└──────────┬──────────┘
           │ MQTT (tank1_a9ad51/water/level)
           │
           ▼
┌─────────────────────────────────────────────────┐
│              RabbitMQ on VPS                      │
│  (MQTT plugin — already running)                 │
│                                                   │
│  New queue: water.level                          │
│  Binding: tank1_*/water/level → water.level      │
│  Binding: tank1_*/water/status → water.status    │
└──────────┬──────────────────────┬───────────────┘
           │                      │
    ┌──────▼──────┐       ┌──────▼──────┐
    │ alert-service│       │ device-svc  │
    │              │       │              │
    │ Water alerts:│       │ Stores tank │
    │ Low (<20%)   │       │ readings in │
    │ Critical(<10)│       │ PostgreSQL  │
    │ Overflow(>95)│       │              │
    └──────┬──────┘       └──────┬──────┘
           │                      │
    ┌──────▼──────────────────────▼──────┐
    │         React Dashboard            │
    │  New: Water Tank Status Widget     │
    │  - Current level (gauge visual)    │
    │  - Volume in liters                │
    │  - 24h level history chart         │
    │  - Tank online/offline status      │
    │  - Low water alert indicator       │
    └──────┬─────────────────────────────┘
           │
    ┌──────▼──────┐
    │ Android App │
    │ Water card  │
    │ on home     │
    │ screen      │
    └─────────────┘
```

---

## Implementation Plan — 8 Tasks

### Task 1: Point ESP8266 MQTT to Your VPS Broker

**No code changes needed in AutoWaterLevelControl.**

Just update the ESP8266 config to point to your RabbitMQ broker instead of the local IP.

**Option A — Via the ESP8266 web UI:**
1. Connect to ESP8266 web UI: `http://<esp8266-ip>/`
2. Go to Settings → MQTT
3. Set:
   - Server: `vivasvan-tech.in` (or VPS IP)
   - Port: `1883`
   - Username: `water-publisher` (create in RabbitMQ)
   - Password: `<your-password>`
   - Topic Prefix: `water`
   - Device Name: `tank1`
   - Publish Interval: `60000` (60 seconds)
4. Save → device reconnects to VPS broker

**Option B — Via config.json:**
```json
{
    "mqtt": {
        "enabled": true,
        "server": "vivasvan-tech.in",
        "port": 1883,
        "username": "water-publisher",
        "password": "your-password",
        "deviceName": "tank1",
        "topicPrefix": "water",
        "publishInterval": 60000
    }
}
```

**Option C — If ESP8266 is on farm LAN (no internet):**
Point it to the Edge Node's VPN IP instead, and have the edge node bridge the data to the VPS:
```
ESP8266 → 192.168.1.x:1883 (Edge Node MQTT) → VPN → VPS RabbitMQ
```

---

### Task 2: Add RabbitMQ Queue for Water Data

**File:** `cloud/scripts/rabbitmq_init.py`

Add new queue and bindings:

```python
# Water level queue
channel.queue_declare(
    queue="water.level",
    durable=True,
    arguments={"x-message-ttl": 86400000},  # 24h TTL
)

channel.queue_declare(
    queue="water.status",
    durable=True,
)

# Bind — wildcard matches any tank device tag
channel.queue_bind(queue="water.level", exchange="amq.topic",
                   routing_key="*.water.level")
channel.queue_bind(queue="water.status", exchange="amq.topic",
                   routing_key="*.water.status")

# Create MQTT user for ESP8266
print("""
  rabbitmqctl add_user water-publisher <password>
  rabbitmqctl set_permissions water-publisher "^$" ".*" "^$"
""")
```

---

### Task 3: Add PostgreSQL Tables for Water Data

**File:** `cloud/db/init.sql` — add these tables:

```sql
-- Water Tanks
CREATE TABLE water_tanks (
    id VARCHAR(50) PRIMARY KEY,          -- e.g., "tank1"
    farm_id UUID NOT NULL,
    display_name VARCHAR(100),
    device_tag VARCHAR(100),             -- e.g., "tank1_a9ad51"
    tank_type VARCHAR(20) DEFAULT 'circular',
    diameter_mm REAL,
    height_mm REAL,
    capacity_liters REAL,
    location_description TEXT,
    low_threshold_percent REAL DEFAULT 20.0,
    critical_threshold_percent REAL DEFAULT 10.0,
    overflow_threshold_percent REAL DEFAULT 95.0,
    status VARCHAR(20) DEFAULT 'unknown',
    last_reading_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Water Level Readings (time-series, high volume)
CREATE TABLE water_level_readings (
    id BIGSERIAL PRIMARY KEY,
    tank_id VARCHAR(50) REFERENCES water_tanks(id),
    percent_filled REAL NOT NULL,
    volume_liters REAL,
    water_height_mm REAL,
    distance_mm REAL,
    temperature_c REAL,
    state VARCHAR(20),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_water_readings_tank_time ON water_level_readings(tank_id, created_at DESC);

-- Retention: keep only 30 days of readings
-- (Run as a daily cron job)
-- DELETE FROM water_level_readings WHERE created_at < NOW() - INTERVAL '30 days';
```

---

### Task 4: Add Water Level Consumer in alert-service

**File:** New `backend/alert-service/src/main/java/com/sudarshanchakra/alert/service/WaterLevelConsumer.java`

```java
@Service
@Slf4j
public class WaterLevelConsumer {

    @Autowired private WaterTankRepository tankRepo;
    @Autowired private WaterLevelReadingRepository readingRepo;
    @Autowired private AlertService alertService;

    @RabbitListener(queues = "water.level")
    public void handleWaterLevel(String message) {
        try {
            WaterLevelPayload payload = objectMapper.readValue(message, WaterLevelPayload.class);
            
            // Store reading
            WaterLevelReading reading = WaterLevelReading.builder()
                .tankId(payload.getDeviceName())
                .percentFilled(payload.getPercentFilled())
                .volumeLiters(payload.getVolumeLiters())
                .waterHeightMm(payload.getWaterHeightMm())
                .distanceMm(payload.getDistanceMm())
                .temperatureC(payload.getTemperatureC())
                .state(payload.getState())
                .build();
            readingRepo.save(reading);
            
            // Update tank last_reading
            tankRepo.updateLastReading(payload.getDeviceName(), Instant.now());
            
            // Check thresholds → generate alerts
            checkThresholds(payload);
            
        } catch (Exception e) {
            log.error("Failed to process water level: {}", e.getMessage());
        }
    }
    
    private void checkThresholds(WaterLevelPayload payload) {
        WaterTank tank = tankRepo.findById(payload.getDeviceName()).orElse(null);
        if (tank == null) return;
        
        float level = payload.getPercentFilled();
        
        if (level <= tank.getCriticalThresholdPercent()) {
            // CRITICAL: tank nearly empty
            alertService.createWaterAlert(tank, "critical",
                String.format("Water tank '%s' critically low: %.1f%%", 
                    tank.getDisplayName(), level));
        } 
        else if (level <= tank.getLowThresholdPercent()) {
            // WARNING: tank getting low
            alertService.createWaterAlert(tank, "warning",
                String.format("Water tank '%s' low: %.1f%%",
                    tank.getDisplayName(), level));
        }
        else if (level >= tank.getOverflowThresholdPercent()) {
            // WARNING: tank about to overflow
            alertService.createWaterAlert(tank, "warning",
                String.format("Water tank '%s' near overflow: %.1f%%",
                    tank.getDisplayName(), level));
        }
    }
}
```

---

### Task 5: Add Water REST API in device-service

**File:** New `backend/device-service/src/main/java/com/sudarshanchakra/device/controller/WaterTankController.java`

```
GET  /api/v1/water/tanks              → List all tanks with latest reading
GET  /api/v1/water/tanks/{id}         → Single tank detail
GET  /api/v1/water/tanks/{id}/history → Readings for last 24h (for chart)
POST /api/v1/water/tanks              → Register a new tank
PUT  /api/v1/water/tanks/{id}         → Update thresholds
```

**Response example:**
```json
{
    "id": "tank1",
    "displayName": "Main Farm Tank",
    "deviceTag": "tank1_a9ad51",
    "status": "online",
    "currentLevel": {
        "percentFilled": 72.5,
        "volumeLiters": 1768.4,
        "waterHeightCm": 123.5,
        "temperatureC": 28.5,
        "lastReadingAt": "2026-03-07T10:15:30Z"
    },
    "thresholds": {
        "low": 20.0,
        "critical": 10.0,
        "overflow": 95.0
    },
    "tank": {
        "type": "circular",
        "diameterMm": 1350,
        "heightMm": 1704,
        "capacityLiters": 2438
    }
}
```

---

### Task 6: React Dashboard — Water Tank Widget

**File:** New `dashboard/src/components/WaterTankGauge.tsx`
**File:** Modify `dashboard/src/pages/DashboardPage.tsx`

Add a water tank card to the dashboard showing:

```
┌─────────────────────────────────┐
│  💧 Main Farm Tank              │
│                                  │
│    ┌──────────────────────┐     │
│    │                      │     │
│    │   ████████████████   │ 72% │
│    │   ████████████████   │     │
│    │   ████████████████   │     │
│    │                      │     │
│    │                      │     │
│    └──────────────────────┘     │
│                                  │
│  1,768 / 2,438 liters           │
│  28.5°C  •  Updated 2m ago      │
│  Status: ● Online               │
└─────────────────────────────────┘
```

Plus a dedicated water page:
```
GET /water → Full water tank management page
  - All tanks with gauge visualizations
  - 24-hour level history chart (Recharts AreaChart)
  - Threshold configuration
  - Tank online/offline status
```

---

### Task 7: Android App — Water Card on Home Screen

**File:** New `android/.../ui/components/WaterTankCard.kt`
**File:** Modify `android/.../ui/screens/alerts/AlertFeedScreen.kt`

Add a compact water tank card at the top of the alert feed:

```
┌─────────────────────────────┐
│ 💧 Farm Tank  72% │ 1,768L │
│ ████████████░░░░░  │ 28.5°C │
└─────────────────────────────┘
```

Tapping opens a full water detail screen with history chart.

---

### Task 8: Edge Node — Water Level MQTT Bridge (Optional)

**Only needed if ESP8266 can't reach VPS directly** (e.g., farm LAN has no internet, only Edge Node has VPN).

**File:** New `edge/water_bridge.py`

```python
"""
Bridges water level MQTT messages from local farm network
to the VPS broker via VPN tunnel.

ESP8266 → local broker (192.168.1.x:1883) → edge bridge → VPN → VPS
"""

import paho.mqtt.client as mqtt
import os, json, logging

log = logging.getLogger("water_bridge")

LOCAL_BROKER = os.getenv("LOCAL_MQTT_BROKER", "192.168.1.1")
VPS_BROKER = os.getenv("VPN_BROKER_IP", "10.8.0.1")

# Subscribe to local water topics, republish to VPS
def on_local_message(client, userdata, msg):
    vps_client = userdata["vps_client"]
    # Forward the exact same topic and payload to VPS
    vps_client.publish(msg.topic, msg.payload, qos=1)
    log.debug("Bridged: %s → VPS", msg.topic)

def start_bridge():
    # Connect to VPS
    vps = mqtt.Client(client_id="edge-water-bridge-vps")
    vps.connect(VPS_BROKER, 1883)
    vps.loop_start()
    
    # Connect to local broker and subscribe to water topics
    local = mqtt.Client(client_id="edge-water-bridge-local")
    local.user_data_set({"vps_client": vps})
    local.on_message = on_local_message
    local.connect(LOCAL_BROKER, 1883)
    local.subscribe("+/water/#", qos=1)  # All tank topics
    local.loop_forever()
```

Add to `farm_edge_node.py`:
```python
WATER_BRIDGE_ENABLED = os.getenv("WATER_BRIDGE", "false").lower() == "true"
if WATER_BRIDGE_ENABLED:
    from water_bridge import start_bridge
    threading.Thread(target=start_bridge, daemon=True, name="water-bridge").start()
    log.info("Water level MQTT bridge started")
```

---

## Network Options

| Scenario | ESP8266 MQTT Broker | How |
|:---|:---|:---|
| ESP8266 has internet | VPS directly (`vivasvan-tech.in:1883`) | Simplest — just configure ESP8266 MQTT settings |
| ESP8266 on farm LAN only | Edge Node bridges to VPS | Task 8 — edge `water_bridge.py` forwards via VPN |
| ESP8266 on same LAN as Edge | Edge Node local Mosquitto | Edge subscribes to local + forwards to VPS |

---

## Alert Types Added

| Trigger | Priority | Example Message |
|:---|:---|:---|
| Level ≤ 10% | critical | "Water tank 'Main Farm Tank' critically low: 8.5%" |
| Level ≤ 20% | warning | "Water tank 'Main Farm Tank' low: 17.2%" |
| Level ≥ 95% | warning | "Water tank 'Main Farm Tank' near overflow: 96.1%" |
| Tank offline (LWT) | high | "Water tank 'Main Farm Tank' offline — sensor disconnected" |

These alerts flow through the same alert pipeline as AI detections → appear on dashboard, Android app, and can trigger PA announcements.

---

## API Routes Summary (New)

```
GET  /api/v1/water/tanks              → All tanks + latest readings
GET  /api/v1/water/tanks/{id}         → Single tank detail
GET  /api/v1/water/tanks/{id}/history → 24h readings (for chart)
POST /api/v1/water/tanks              → Register new tank
PUT  /api/v1/water/tanks/{id}         → Update thresholds/config
```

---

## What You DON'T Need to Change

- **AutoWaterLevelControl firmware** — no code changes, just point MQTT to VPS
- **Edge AI pipeline** — water data doesn't go through YOLO, it's pure MQTT
- **Zone engine** — water levels don't use virtual fences
- **LoRa system** — water sensor uses WiFi, not LoRa

---

## Implementation Order

```
1. Create RabbitMQ user + queue for water (15 min)
2. Point ESP8266 to VPS broker (5 min)
3. Verify data flows: mosquitto_sub -h vivasvan-tech.in -t "tank1_*/water/#"
4. Add PostgreSQL tables (10 min)
5. Build WaterLevelConsumer in alert-service
6. Build WaterTankController in device-service
7. Add WaterTankGauge to React dashboard
8. Add WaterTankCard to Android app
9. (Optional) Build edge water bridge if ESP8266 can't reach VPS
```

Steps 1-3 can be done today. Steps 4-8 are for the cloud agent.
