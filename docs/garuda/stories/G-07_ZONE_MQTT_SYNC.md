# G-07: Edge Zone Reload via MQTT Subscriber

## Status
NOT DONE — Backend `DeviceService.publishZoneReload()` publishes MQTT events on zone create/delete, but edge has no subscriber to receive them.

## Context
- Backend publishes to exchange with routing key on zone changes
- Edge `zone_engine.reload()` exists and works (called from Flask GUI)
- Missing: MQTT subscriber in `farm_edge_node.py` that bridges the two

## File to MODIFY

### `edge/farm_edge_node.py`

Read the file first. Find where the MQTT client connects and subscribes to topics (look for `client.subscribe` or `on_connect` callback).

Add after the existing subscriptions:
```python
# ── Zone reload subscriber ──
ZONE_RELOAD_TOPIC = "farm/admin/zone_updated"

def _on_zone_update(client, userdata, msg):
    """Reload zone engine when a zone is created/updated/deleted via dashboard or API."""
    try:
        payload = json.loads(msg.payload.decode())
        log.info("Zone update event received: %s — reloading zone engine", payload.get("event", "unknown"))
        zone_engine.reload()
        log.info("Zone engine reloaded successfully, %d zones active", len(zone_engine.zones))
    except Exception as e:
        log.error("Failed to process zone update: %s", e)

# In the connect/subscribe section, add:
mqtt_client.subscribe(ZONE_RELOAD_TOPIC)
mqtt_client.message_callback_add(ZONE_RELOAD_TOPIC, _on_zone_update)
log.info("Subscribed to %s for zone hot-reload", ZONE_RELOAD_TOPIC)
```

**Important:** Ensure `zone_engine` is accessible from the callback. It should be a module-level variable or passed via userdata.

## Also verify backend publishes correctly

Read `backend/device-service/src/main/java/com/sudarshanchakra/device/service/DeviceService.java`.
Find `publishZoneReload` method. Verify it publishes to a topic/exchange that maps to MQTT topic `farm/admin/zone_updated`.

If the backend publishes via AMQP exchange (not MQTT directly), ensure RabbitMQ has a binding that routes the message to the MQTT topic. The RabbitMQ MQTT plugin automatically maps between AMQP routing keys and MQTT topics using dot↔slash conversion:
- AMQP routing key: `farm.admin.zone_updated`
- MQTT topic: `farm/admin/zone_updated`

## Verification
```bash
# Start edge node
cd edge && docker compose -f docker-compose.dev.yml up -d

# Create a zone via API
curl -X POST http://localhost:8080/api/v1/zones \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cameraId":"cam-01","name":"G07 Test Zone","zoneType":"intrusion","priority":"high","targetClasses":["person"],"polygon":"[[100,100],[500,100],[500,400],[100,400]]"}'

# Check edge logs within 5 seconds
docker logs edge-ai 2>&1 | grep "zone_updated\|Zone update\|reloaded"
# Expected: "Zone update event received" + "Zone engine reloaded successfully"
```
