-- ============================================================================
-- SudarshanChakra — PostgreSQL Schema
-- Enterprise Smart Farm Hazard Detection & Security System
-- ============================================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- for gen_random_uuid()

-- ============================================================================
-- Users & Authentication
-- ============================================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('admin', 'manager', 'viewer')),
    mqtt_client_id VARCHAR(100),
    last_login TIMESTAMPTZ,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_users_farm ON users(farm_id);

-- ============================================================================
-- Edge Nodes
-- ============================================================================
CREATE TABLE edge_nodes (
    id VARCHAR(50) PRIMARY KEY,
    farm_id UUID NOT NULL,
    display_name VARCHAR(100),
    vpn_ip INET,
    local_ip INET,
    status VARCHAR(20) DEFAULT 'unknown'
        CHECK (status IN ('online', 'offline', 'degraded', 'unknown')),
    last_heartbeat TIMESTAMPTZ,
    hardware_info JSONB,
    config JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- Cameras
-- ============================================================================
CREATE TABLE cameras (
    id VARCHAR(50) PRIMARY KEY,
    node_id VARCHAR(50) REFERENCES edge_nodes(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    rtsp_url TEXT NOT NULL,
    model VARCHAR(100),
    location_description TEXT,
    fps_target REAL DEFAULT 2.0,
    resolution VARCHAR(20) DEFAULT '640x480',
    enabled BOOLEAN DEFAULT TRUE,
    status VARCHAR(20) DEFAULT 'unknown'
        CHECK (status IN ('active', 'offline', 'error', 'unknown')),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_cameras_node ON cameras(node_id);

-- ============================================================================
-- Virtual Fence Zones
-- ============================================================================
CREATE TABLE zones (
    id VARCHAR(50) PRIMARY KEY,
    camera_id VARCHAR(50) REFERENCES cameras(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    zone_type VARCHAR(30) NOT NULL
        CHECK (zone_type IN ('intrusion', 'zero_tolerance', 'livestock_containment', 'hazard')),
    priority VARCHAR(20) NOT NULL
        CHECK (priority IN ('critical', 'high', 'warning', 'info')),
    target_classes TEXT[] NOT NULL,
    polygon JSONB NOT NULL,
    color VARCHAR(7) DEFAULT '#FF0000',
    enabled BOOLEAN DEFAULT TRUE,
    suppress_with_worker_tag BOOLEAN DEFAULT TRUE,
    dedup_window_seconds INTEGER DEFAULT 30,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_zones_camera ON zones(camera_id);
CREATE INDEX idx_zones_type ON zones(zone_type);

-- ============================================================================
-- Alerts (Core — high write volume)
-- ============================================================================
CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    node_id VARCHAR(50) REFERENCES edge_nodes(id),
    camera_id VARCHAR(50) REFERENCES cameras(id),
    zone_id VARCHAR(50) REFERENCES zones(id),
    zone_name VARCHAR(100),
    zone_type VARCHAR(30),
    priority VARCHAR(20) NOT NULL
        CHECK (priority IN ('critical', 'high', 'warning', 'info')),
    detection_class VARCHAR(50) NOT NULL,
    confidence REAL,
    bbox REAL[],
    snapshot_url TEXT,
    thumbnail_url TEXT,
    worker_suppressed BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'new'
        CHECK (status IN ('new', 'acknowledged', 'resolved', 'false_positive')),
    acknowledged_by UUID REFERENCES users(id),
    acknowledged_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users(id),
    resolved_at TIMESTAMPTZ,
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Performance indexes for common queries
CREATE INDEX idx_alerts_priority_created ON alerts(priority, created_at DESC);
CREATE INDEX idx_alerts_status_created ON alerts(status, created_at DESC);
CREATE INDEX idx_alerts_zone_created ON alerts(zone_id, created_at DESC);
CREATE INDEX idx_alerts_node_created ON alerts(node_id, created_at DESC);
CREATE INDEX idx_alerts_created ON alerts(created_at DESC);
CREATE INDEX idx_alerts_class ON alerts(detection_class);

-- Partial index for active alerts (most common query)
CREATE INDEX idx_alerts_active ON alerts(priority, created_at DESC)
    WHERE status IN ('new', 'acknowledged');

-- ============================================================================
-- Siren Actions (Audit Trail)
-- ============================================================================
CREATE TABLE siren_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    triggered_by UUID REFERENCES users(id),
    triggered_by_system BOOLEAN DEFAULT FALSE,
    target_node VARCHAR(50) REFERENCES edge_nodes(id),
    action VARCHAR(20) NOT NULL CHECK (action IN ('trigger', 'stop')),
    alert_id UUID REFERENCES alerts(id),
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at TIMESTAMPTZ,
    siren_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_siren_actions_created ON siren_actions(created_at DESC);

-- ============================================================================
-- Worker Tags (ESP32/LoRa)
-- ============================================================================
CREATE TABLE worker_tags (
    tag_id VARCHAR(50) PRIMARY KEY,
    worker_name VARCHAR(100) NOT NULL,
    farm_id UUID NOT NULL,
    role VARCHAR(50),
    phone VARCHAR(20),
    active BOOLEAN DEFAULT TRUE,
    last_seen TIMESTAMPTZ,
    last_rssi INTEGER,
    last_node VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_worker_tags_farm ON worker_tags(farm_id);

-- ============================================================================
-- Suppression Log (Worker-identified events)
-- ============================================================================
CREATE TABLE suppression_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id VARCHAR(50) REFERENCES edge_nodes(id),
    camera_id VARCHAR(50) REFERENCES cameras(id),
    zone_id VARCHAR(50) REFERENCES zones(id),
    tag_id VARCHAR(50) REFERENCES worker_tags(tag_id),
    worker_name VARCHAR(100),
    detection_class VARCHAR(50),
    confidence REAL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_suppression_created ON suppression_log(created_at DESC);

-- ============================================================================
-- Node Health Log
-- ============================================================================
CREATE TABLE node_health_log (
    id BIGSERIAL PRIMARY KEY,
    node_id VARCHAR(50) REFERENCES edge_nodes(id),
    event_type VARCHAR(20) NOT NULL
        CHECK (event_type IN ('online', 'offline', 'heartbeat', 'gpu_warning', 'vpn_reconnect')),
    gpu_usage REAL,
    memory_usage REAL,
    cpu_temp REAL,
    vpn_latency_ms REAL,
    active_cameras INTEGER,
    inference_fps REAL,
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_node_health_node_created ON node_health_log(node_id, created_at DESC);

-- Partition by month for health logs (high volume)
-- ALTER TABLE node_health_log PARTITION BY RANGE (created_at);

-- ============================================================================
-- Analytics: Daily Summary (materialized for dashboard)
-- ============================================================================
CREATE TABLE daily_alert_summary (
    date DATE NOT NULL,
    node_id VARCHAR(50) REFERENCES edge_nodes(id),
    zone_id VARCHAR(50),
    detection_class VARCHAR(50),
    priority VARCHAR(20),
    total_count INTEGER DEFAULT 0,
    suppressed_count INTEGER DEFAULT 0,
    false_positive_count INTEGER DEFAULT 0,
    avg_confidence REAL,
    PRIMARY KEY (date, node_id, zone_id, detection_class)
);

-- ============================================================================
-- Helper Functions
-- ============================================================================

-- Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_edge_nodes_updated_at BEFORE UPDATE ON edge_nodes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_zones_updated_at BEFORE UPDATE ON zones
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_worker_tags_updated_at BEFORE UPDATE ON worker_tags
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- Seed Data
-- ============================================================================
INSERT INTO edge_nodes (id, farm_id, display_name, vpn_ip, status) VALUES
    ('edge-node-a', 'a0000000-0000-0000-0000-000000000001', 'Edge Node A', '10.8.0.10', 'online'),
    ('edge-node-b', 'a0000000-0000-0000-0000-000000000001', 'Edge Node B', '10.8.0.11', 'online');

-- ============================================================================
-- Water Level Monitor Integration
-- AutoWaterLevelControl × SudarshanChakra
-- ============================================================================

-- Water Tanks (one row per physical tank / ESP8266 sensor node)
CREATE TABLE water_tanks (
    id VARCHAR(50) PRIMARY KEY,                   -- e.g. "farm_tank1", "home_sump"
    farm_id UUID NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    device_tag VARCHAR(100),                      -- e.g. "farm_tank1_a9ad51"
    location VARCHAR(50)                          -- "farm" | "home"
        CHECK (location IN ('farm', 'home')),
    tank_type VARCHAR(20) DEFAULT 'circular'
        CHECK (tank_type IN ('circular', 'rectangular')),
    diameter_mm REAL,
    height_mm REAL,
    capacity_liters REAL,
    location_description TEXT,
    low_threshold_percent REAL DEFAULT 20.0,
    critical_threshold_percent REAL DEFAULT 10.0,
    overflow_threshold_percent REAL DEFAULT 95.0,
    status VARCHAR(20) DEFAULT 'unknown'
        CHECK (status IN ('online', 'offline', 'unknown')),
    last_reading_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_water_tanks_farm ON water_tanks(farm_id);
CREATE INDEX idx_water_tanks_location ON water_tanks(location);

CREATE TRIGGER update_water_tanks_updated_at BEFORE UPDATE ON water_tanks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Seed: Sangareddy farm tanks
INSERT INTO water_tanks (id, farm_id, display_name, location, tank_type, diameter_mm, height_mm, capacity_liters, low_threshold_percent, critical_threshold_percent)
VALUES
    ('farm_tank1', 'a0000000-0000-0000-0000-000000000001', 'Farm Tank 1', 'farm', 'circular', 1350, 1704.5, 2438, 20, 10),
    ('farm_tank2', 'a0000000-0000-0000-0000-000000000001', 'Farm Tank 2', 'farm', 'circular', 1350, 1704.5, 2438, 20, 10),
    ('farm_tank3', 'a0000000-0000-0000-0000-000000000001', 'Farm Tank 3', 'farm', 'circular', 1350, 1704.5, 2438, 20, 10),
    ('home_sump',  'a0000000-0000-0000-0000-000000000001', 'Home Sump',   'home', 'circular', 1000, 1200,   942,  30, 15),
    ('home_overhead', 'a0000000-0000-0000-0000-000000000001', 'Home Overhead Tank', 'home', 'circular', 800, 900, 452, 25, 10);

-- Water Level Readings (time-series, high volume)
CREATE TABLE water_level_readings (
    id BIGSERIAL PRIMARY KEY,
    tank_id VARCHAR(50) REFERENCES water_tanks(id) ON DELETE CASCADE,
    percent_filled REAL NOT NULL,
    volume_liters REAL,
    water_height_mm REAL,
    distance_mm REAL,
    temperature_c REAL,
    state VARCHAR(20),
    sensor_ok BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_water_readings_tank_time ON water_level_readings(tank_id, created_at DESC);

-- Motor Controllers (one per pump — relay or SMS)
CREATE TABLE water_motor_controllers (
    id VARCHAR(50) PRIMARY KEY,                   -- e.g. "farm_motor", "home_motor"
    farm_id UUID NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    device_tag VARCHAR(100),                      -- e.g. "farm_motor_b1c2d3"
    location VARCHAR(50)
        CHECK (location IN ('farm', 'home')),
    control_type VARCHAR(10) NOT NULL
        CHECK (control_type IN ('relay', 'sms')),

    -- SMS config (Taro Smart Panel)
    gsm_target_phone VARCHAR(25),
    gsm_on_message VARCHAR(100),                  -- configurable — e.g. "START PUMP"
    gsm_off_message VARCHAR(100),                 -- configurable — e.g. "STOP PUMP"

    -- Auto mode thresholds (applied across all linked tanks)
    auto_mode BOOLEAN DEFAULT TRUE,
    pump_on_percent REAL DEFAULT 20.0,
    pump_off_percent REAL DEFAULT 85.0,
    max_run_minutes INTEGER DEFAULT 30,

    -- Runtime state (updated by motor/status MQTT messages)
    state VARCHAR(20) DEFAULT 'stopped'
        CHECK (state IN ('stopped', 'running', 'pending', 'disabled', 'unknown')),
    mode VARCHAR(10) DEFAULT 'auto'
        CHECK (mode IN ('off', 'auto', 'on')),
    run_seconds INTEGER DEFAULT 0,

    status VARCHAR(20) DEFAULT 'unknown'
        CHECK (status IN ('online', 'offline', 'unknown')),
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_motor_farm ON water_motor_controllers(farm_id);
CREATE TRIGGER update_motor_updated_at BEFORE UPDATE ON water_motor_controllers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Seed: farm motor (SMS — Taro Smart Panel) and home motor (relay)
INSERT INTO water_motor_controllers (id, farm_id, display_name, location, control_type, gsm_on_message, gsm_off_message, pump_on_percent, pump_off_percent, max_run_minutes)
VALUES
    ('farm_motor', 'a0000000-0000-0000-0000-000000000001', 'Farm Motor (5HP)', 'farm', 'sms', 'START PUMP', 'STOP PUMP', 20, 85, 60),
    ('home_motor', 'a0000000-0000-0000-0000-000000000001', 'Home Motor',       'home', 'relay', null, null, 25, 90, 30);

-- Maps tanks to their motor (many-to-one: multiple tanks served by one motor)
CREATE TABLE water_tank_motor_map (
    tank_id VARCHAR(50) REFERENCES water_tanks(id) ON DELETE CASCADE,
    motor_id VARCHAR(50) REFERENCES water_motor_controllers(id) ON DELETE CASCADE,
    is_primary BOOLEAN DEFAULT TRUE,
    PRIMARY KEY (tank_id, motor_id)
);

-- Seed: farm tanks → farm motor; home tanks → home motor
INSERT INTO water_tank_motor_map (tank_id, motor_id, is_primary) VALUES
    ('farm_tank1', 'farm_motor', true),
    ('farm_tank2', 'farm_motor', false),
    ('farm_tank3', 'farm_motor', false),
    ('home_sump',  'home_motor', true),
    ('home_overhead', 'home_motor', false);

-- Motor run history (start/stop events)
CREATE TABLE motor_run_log (
    id BIGSERIAL PRIMARY KEY,
    motor_id VARCHAR(50) REFERENCES water_motor_controllers(id) ON DELETE CASCADE,
    event VARCHAR(20) NOT NULL
        CHECK (event IN ('started', 'stopped', 'sms_sent', 'sms_failed', 'dry_run_blocked', 'max_runtime')),
    trigger_source VARCHAR(20)           -- 'auto', 'manual_app', 'manual_mqtt'
        CHECK (trigger_source IN ('auto', 'manual_app', 'manual_mqtt')),
    tank_level_percent REAL,
    run_seconds INTEGER,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_motor_run_log_motor ON motor_run_log(motor_id, created_at DESC);
