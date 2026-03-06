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
    mqtt_client_id VARCHAR(100),  -- MQTT client ID for direct push
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
