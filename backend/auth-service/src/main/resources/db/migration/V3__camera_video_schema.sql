-- V3: Camera video enhancements + recording tables

ALTER TABLE cameras ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) DEFAULT 'rtsp';
ALTER TABLE cameras ADD COLUMN IF NOT EXISTS source_url TEXT;
ALTER TABLE cameras ADD COLUMN IF NOT EXISTS recording_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE cameras ADD COLUMN IF NOT EXISTS has_ptz BOOLEAN DEFAULT FALSE;
ALTER TABLE cameras ADD COLUMN IF NOT EXISTS ptz_presets JSONB DEFAULT '[]';
ALTER TABLE cameras ADD COLUMN IF NOT EXISTS onvif_host VARCHAR(255);
ALTER TABLE cameras ADD COLUMN IF NOT EXISTS onvif_port INTEGER DEFAULT 80;
ALTER TABLE cameras ADD COLUMN IF NOT EXISTS onvif_user VARCHAR(100);
ALTER TABLE cameras ADD COLUMN IF NOT EXISTS onvif_pass VARCHAR(255);
ALTER TABLE cameras ADD COLUMN IF NOT EXISTS onvif_info JSONB DEFAULT '{}';

CREATE TABLE IF NOT EXISTS video_recordings (
    id BIGSERIAL PRIMARY KEY,
    camera_id VARCHAR(50) REFERENCES cameras(id),
    node_id VARCHAR(50),
    segment_start TIMESTAMPTZ NOT NULL,
    segment_end TIMESTAMPTZ NOT NULL,
    file_path TEXT NOT NULL,
    file_size_bytes BIGINT,
    duration_seconds REAL,
    storage_location VARCHAR(20) DEFAULT 'ssd',
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_video_recordings_camera ON video_recordings(camera_id);
CREATE INDEX IF NOT EXISTS idx_video_recordings_start ON video_recordings(segment_start);

CREATE TABLE IF NOT EXISTS alert_video_clips (
    alert_id UUID PRIMARY KEY,
    recording_id BIGINT REFERENCES video_recordings(id),
    clip_path TEXT,
    offset_seconds REAL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
