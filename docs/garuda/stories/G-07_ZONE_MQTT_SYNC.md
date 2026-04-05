# G-07: Edge zone reload via MQTT

## Status
**COMPLETE (signal path)** — Backend publishes zone create/delete notifications on **`farm.events`** with routing key **`farm.admin.reload_config`**, which RabbitMQ’s MQTT adapter exposes as topic **`farm/admin/reload_config`**. The edge already subscribes and reloads `zones.json` + suppression rules.

## Architecture

| Layer | Detail |
|-------|--------|
| **device-service** | [`DeviceService.publishZoneReload`](backend/device-service/src/main/java/com/sudarshanchakra/device/service/DeviceService.java) sends JSON `{"event":"zone_created|zone_deleted","zone_id":"...","camera_id":"..."}` to exchange **`farm.events`**, routing key **`farm.admin.reload_config`**. |
| **RabbitMQ** | [`mqtt.exchange = farm.events`](cloud/rabbitmq/rabbitmq.conf) — MQTT clients must use the same exchange; publishing only to `amq.topic` did not reach MQTT subscribers. |
| **edge** | [`farm_edge_node.py`](edge/farm_edge_node.py) subscribes to `farm/admin/reload_config`, calls `_mqtt_admin_reload` → `zone_engine.reload()` + `reload_suppression_rules()`. Logs `reload_config MQTT: event=...` when payload is JSON. |

## Limitations (not fixed by G-07)

- **`zones.json` vs PostgreSQL:** Reload re-reads **local** `zones.json` only. Zones created via the cloud API are in the DB until something writes or syncs them to the edge (e.g. Flask GUI, future zone pull). The MQTT message is a **reload hint**, not a data push.
- **No zone PUT/PATCH:** Only create/delete trigger publish today.
- **Broadcast:** All edges on the broker receive reload; payload is not filtered by `node_id`.

## Verification

1. Run RabbitMQ (compose with `rabbitmq.conf` that sets `mqtt.exchange`).
2. Start edge with valid `VPN_BROKER_IP` / `MQTT_USER` / `MQTT_PASS`.
3. Create or delete a zone via gateway API (JWT with `zones:manage`).
4. Edge logs should include `reload_config MQTT: event=zone_created` (or `zone_deleted`) and `Admin reload: zones + suppression_rules applied` / zone engine loaded counts.

```bash
# Example: edge container logs
docker logs <edge-container> 2>&1 | grep -E "reload_config MQTT|Admin reload|Zone engine loaded"
```

## Historical note

An older story suggested topic `farm/admin/zone_updated` and claimed the edge had no subscriber. The codebase already used **`farm/admin/reload_config`**; the missing piece was publishing to **`farm.events`** so MQTT clients see the message.
