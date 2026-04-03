-- ═══════════════════════════════════════════════════════════════
-- V4: MDM Kiosk & Device Management Tables
-- Non-destructive: new tables only (includes location history addendum).
-- No explicit BEGIN/COMMIT: Flyway wraps PostgreSQL migrations in a transaction.
-- Requires update_updated_at_column() from baseline/init schema.
-- ═══════════════════════════════════════════════════════════════

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
    policies JSONB DEFAULT '{"status_bar_disabled":true,"safe_boot_blocked":true,"factory_reset_blocked":true,"wifi_config_locked":true,"mobile_data_forced":true,"location_forced":true,"wifi_forced":true,"location_interval_sec":60}'::jsonb,
    last_heartbeat TIMESTAMPTZ,
    last_telemetry_sync TIMESTAMPTZ,
    last_latitude DOUBLE PRECISION,
    last_longitude DOUBLE PRECISION,
    last_location_at TIMESTAMPTZ,
    location_interval_sec INT DEFAULT 60,
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

CREATE TABLE mdm_location_history (
    id BIGSERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES mdm_devices(id) ON DELETE CASCADE,
    farm_id UUID NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy_meters REAL,
    altitude_meters REAL,
    speed_mps REAL,
    bearing REAL,
    provider VARCHAR(20),                  -- 'gps', 'network', 'fused'
    battery_percent INT,
    recorded_at TIMESTAMPTZ NOT NULL,      -- Device timestamp when location was captured
    created_at TIMESTAMPTZ DEFAULT NOW()   -- Server timestamp when record was stored
);

CREATE INDEX idx_mdm_location_device_time ON mdm_location_history(device_id, recorded_at DESC);
CREATE INDEX idx_mdm_location_farm_time ON mdm_location_history(farm_id, recorded_at DESC);

CREATE TRIGGER update_mdm_devices_updated_at BEFORE UPDATE ON mdm_devices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
