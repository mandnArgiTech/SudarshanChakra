-- Run if deploy-backend failed with:
--   device-service: Schema-validation: missing table [water_level_readings]
--   alert-service: missing column [level_pct] (fixed in code — use current JPA + this DDL)
--
-- Usage (from host, password from your setup):
--   PGPASSWORD=devpassword123 psql -h localhost -U scadmin -d sudarshanchakra -f cloud/db/repair_water_tables.sql

-- water_level_readings (matches device-service / alert-service entities + init.sql)
CREATE TABLE IF NOT EXISTS water_level_readings (
    id BIGSERIAL PRIMARY KEY,
    tank_id VARCHAR(50) REFERENCES water_tanks(id) ON DELETE CASCADE,
    percent_filled REAL NOT NULL,
    volume_liters REAL,
    water_height_mm REAL,
    distance_mm REAL,
    temperature_c REAL,
    state VARCHAR(20),
    sensor_ok BOOLEAN DEFAULT TRUE,
    battery_voltage REAL,
    battery_percent SMALLINT,
    battery_state VARCHAR(10),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_water_readings_tank_time ON water_level_readings(tank_id, created_at DESC);
