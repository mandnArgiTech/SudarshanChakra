-- Flyway: idempotent SaaS multitenant deltas (see also cloud/db/migration_002_saas_multitenant.sql).
-- Baseline version 1 = schema already applied via cloud/db/init.sql on existing deployments.

CREATE TABLE IF NOT EXISTS farms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(50) UNIQUE NOT NULL,
    owner_name VARCHAR(200),
    contact_phone VARCHAR(20),
    contact_email VARCHAR(255),
    address TEXT,
    location_lat REAL,
    location_lng REAL,
    timezone VARCHAR(50) DEFAULT 'Asia/Kolkata',
    status VARCHAR(20) DEFAULT 'active'
        CHECK (status IN ('active', 'suspended', 'trial')),
    subscription_plan VARCHAR(50) DEFAULT 'full',
    modules_enabled JSONB DEFAULT '["alerts","cameras","sirens","water","pumps","zones","devices","workers","analytics"]'::jsonb,
    max_cameras INT DEFAULT 8,
    max_nodes INT DEFAULT 2,
    max_users INT DEFAULT 10,
    trial_ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_farms_slug ON farms(slug);
CREATE INDEX IF NOT EXISTS idx_farms_status ON farms(status);

INSERT INTO farms (id, name, slug, owner_name, subscription_plan, modules_enabled)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'Sanga Reddy Farm',
    'sanga-reddy',
    'Devi Prasad',
    'full',
    '["alerts","cameras","sirens","water","pumps","zones","devices","workers","analytics"]'::jsonb
)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name VARCHAR(200);
ALTER TABLE users ADD COLUMN IF NOT EXISTS permissions JSONB DEFAULT '[]'::jsonb;
ALTER TABLE users ADD COLUMN IF NOT EXISTS modules_override JSONB;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('super_admin', 'admin', 'manager', 'operator', 'viewer'));

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_farm_id_fkey;
ALTER TABLE users ADD CONSTRAINT users_farm_id_fkey FOREIGN KEY (farm_id) REFERENCES farms(id);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    farm_id UUID NOT NULL REFERENCES farms(id),
    user_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(100),
    details JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_farm_time ON audit_log(farm_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_log(action);
