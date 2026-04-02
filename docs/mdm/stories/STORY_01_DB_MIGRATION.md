# Story 01: Database Migration V4 — MDM Tables

## Prerequisites
- None (first story)

## Goal
Create 6 new tables for MDM without touching any existing tables.

## Files to CREATE

### 1. `cloud/db/flyway/V4__mdm_tables.sql`

```sql
-- ═══════════════════════════════════════════════════════════════
-- V4: MDM Kiosk & Device Management Tables
-- Non-destructive: new tables only
-- ═══════════════════════════════════════════════════════════════

BEGIN;

CREATE TABLE mdm_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    user_id UUID REFERENCES users(id),
    device_name VARCHAR(200) NOT NULL,
    android_id VARCHAR(64) UNIQUE NOT NULL,
    model VARCHAR(100),
    os_version VARCHAR(20),
    app_version VARCHAR(20),
    serial_number VARCHAR(100),
    imei VARCHAR(20),
    phone_number VARCHAR(20),
    is_device_owner BOOLEAN DEFAULT FALSE,
    is_lock_task_active BOOLEAN DEFAULT FALSE,
    kiosk_pin_hash VARCHAR(255),
    whitelisted_apps JSONB DEFAULT '["com.sudarshanchakra","com.whatsapp","com.google.android.youtube","com.google.android.apps.maps","com.android.camera2","com.android.dialer"]'::jsonb,
    policies JSONB DEFAULT '{"status_bar_disabled":true,"safe_boot_blocked":true,"factory_reset_blocked":true,"wifi_config_locked":true,"mobile_data_forced":true}'::jsonb,
    last_heartbeat TIMESTAMPTZ,
    last_telemetry_sync TIMESTAMPTZ,
    mqtt_client_id VARCHAR(100),
    status VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending','active','locked','wiped','decommissioned')),
    provisioned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_mdm_devices_farm ON mdm_devices(farm_id);
CREATE INDEX idx_mdm_devices_user ON mdm_devices(user_id);
CREATE INDEX idx_mdm_devices_status ON mdm_devices(status);

CREATE TABLE mdm_app_usage (
    id BIGSERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES mdm_devices(id) ON DELETE CASCADE,
    farm_id UUID NOT NULL,
    date DATE NOT NULL,
    package_name VARCHAR(200) NOT NULL,
    app_label VARCHAR(200),
    foreground_time_sec INT NOT NULL DEFAULT 0,
    launch_count INT DEFAULT 0,
    category VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_mdm_usage_device_date ON mdm_app_usage(device_id, date DESC);
CREATE INDEX idx_mdm_usage_farm_date ON mdm_app_usage(farm_id, date DESC);
CREATE UNIQUE INDEX idx_mdm_usage_unique ON mdm_app_usage(device_id, date, package_name);

CREATE TABLE mdm_call_logs (
    id BIGSERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES mdm_devices(id) ON DELETE CASCADE,
    farm_id UUID NOT NULL,
    phone_number_masked VARCHAR(20),
    call_type VARCHAR(20) NOT NULL CHECK (call_type IN ('incoming','outgoing','missed','rejected')),
    call_timestamp TIMESTAMPTZ NOT NULL,
    duration_sec INT DEFAULT 0,
    contact_name VARCHAR(200),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_mdm_calls_device_time ON mdm_call_logs(device_id, call_timestamp DESC);
CREATE INDEX idx_mdm_calls_farm_time ON mdm_call_logs(farm_id, call_timestamp DESC);

CREATE TABLE mdm_screen_time (
    id BIGSERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES mdm_devices(id) ON DELETE CASCADE,
    farm_id UUID NOT NULL,
    date DATE NOT NULL,
    total_screen_time_sec INT DEFAULT 0,
    unlock_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_mdm_screen_device_date ON mdm_screen_time(device_id, date);

CREATE TABLE mdm_commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES mdm_devices(id) ON DELETE CASCADE,
    farm_id UUID NOT NULL,
    command VARCHAR(50) NOT NULL,
    payload JSONB,
    status VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending','delivered','executed','failed')),
    issued_by UUID REFERENCES users(id),
    issued_at TIMESTAMPTZ DEFAULT NOW(),
    delivered_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    result JSONB
);

CREATE INDEX idx_mdm_commands_device ON mdm_commands(device_id, issued_at DESC);

CREATE TABLE mdm_ota_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL,
    version VARCHAR(20) NOT NULL,
    apk_url TEXT NOT NULL,
    apk_sha256 VARCHAR(64) NOT NULL,
    apk_size_bytes BIGINT,
    release_notes TEXT,
    mandatory BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_mdm_ota_farm ON mdm_ota_packages(farm_id, created_at DESC);

COMMIT;
```

### 2. Also copy to `backend/mdm-service/src/main/resources/db/migration/V4__mdm_tables.sql`
Same file — Flyway in mdm-service will also use it.

## Files to MODIFY

### 1. `cloud/db/init.sql`
Append the same SQL at the bottom (after the existing V3 camera tables section) so greenfield deployments get all tables.

## Verification
```bash
# Check SQL is valid (no syntax errors)
docker exec -i postgres psql -U postgres -d sudarshanchakra -f /path/to/V4__mdm_tables.sql

# Verify tables exist
docker exec -i postgres psql -U postgres -d sudarshanchakra -c "\dt mdm_*"
# Should show: mdm_devices, mdm_app_usage, mdm_call_logs, mdm_screen_time, mdm_commands, mdm_ota_packages
```
