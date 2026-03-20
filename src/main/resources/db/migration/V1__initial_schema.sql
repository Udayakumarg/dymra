-- V1__initial_schema.sql
-- TirupurConnect — complete schema + seed data
-- Flyway runs this once on a clean database

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

-- ── tenants ──────────────────────────────────────────────────
CREATE TABLE tenants (
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    slug                   VARCHAR(50)  NOT NULL UNIQUE,
    city_name              VARCHAR(100) NOT NULL,
    default_zone_radius_km SMALLINT     NOT NULL DEFAULT 15,
    vitality_weights       JSONB        NOT NULL DEFAULT '{}',
    seasonal_pauses        JSONB        NOT NULL DEFAULT '[]',
    search_weights         JSONB        NOT NULL DEFAULT '{}',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO tenants (id, slug, city_name, default_zone_radius_km, vitality_weights, seasonal_pauses, search_weights)
VALUES (
    'f0000000-0000-0000-0000-000000000001',
    'tiruppur-zone1',
    'Tiruppur',
    15,
    '{"wa_response":35,"inquiry_responded":28,"catalogue_updated":20,"phone_verified":12,"app_login":5}',
    '[{"name":"Pongal","start":"01-14","end":"01-17"},{"name":"Tamil New Year","start":"04-14","end":"04-14"},{"name":"Diwali","start":"10-20","end":"11-05"}]',
    '{"bm25_weight":0.50,"trust_weight":0.35,"freshness_weight":0.15}'
);

-- ── users ────────────────────────────────────────────────────
CREATE TYPE user_role AS ENUM ('SUPPLIER', 'BUYER', 'ADMIN');

CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    phone          VARCHAR(15)  NOT NULL,
    phone_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    role           user_role    NOT NULL,
    name           VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, phone)
);

CREATE INDEX idx_users_phone ON users(phone);

INSERT INTO users (id, tenant_id, phone, phone_verified, role, name)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', '+919800000001', true, 'ADMIN',    'Admin User'),
    ('a0000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000001', '+919800000002', true, 'SUPPLIER', 'Arunachalam K'),
    ('a0000000-0000-0000-0000-000000000003', 'f0000000-0000-0000-0000-000000000001', '+919800000003', true, 'SUPPLIER', 'Senthil Murugan'),
    ('a0000000-0000-0000-0000-000000000004', 'f0000000-0000-0000-0000-000000000001', '+919800000004', true, 'SUPPLIER', 'Kumar Rajan'),
    ('a0000000-0000-0000-0000-000000000005', 'f0000000-0000-0000-0000-000000000001', '+919800000005', true, 'BUYER',    'Ramesh Buyer'),
    ('a0000000-0000-0000-0000-000000000006', 'f0000000-0000-0000-0000-000000000001', '+919800000006', true, 'BUYER',    'Priya Buyer');

-- ── suppliers ────────────────────────────────────────────────
CREATE TYPE supplier_status AS ENUM ('ACTIVE', 'DORMANT', 'FADING', 'GHOST', 'CLOSED');

CREATE TABLE suppliers (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id            UUID             NOT NULL REFERENCES tenants(id),
    user_id              UUID             NOT NULL REFERENCES users(id),
    business_name        VARCHAR(200)     NOT NULL,
    owner_phone          VARCHAR(15)      NOT NULL,
    gst_number           VARCHAR(15),
    location             GEOGRAPHY(POINT, 4326),
    zone_visibility      SMALLINT         NOT NULL DEFAULT 1,
    trust_score          SMALLINT         NOT NULL DEFAULT 0,
    vitality_score       SMALLINT         NOT NULL DEFAULT 0,
    status               supplier_status  NOT NULL DEFAULT 'DORMANT',
    profile_complete_pct SMALLINT         NOT NULL DEFAULT 0,
    is_verified          BOOLEAN          NOT NULL DEFAULT FALSE,
    export_certified     BOOLEAN          NOT NULL DEFAULT FALSE,
    business_hours       JSONB,
    last_active_at       TIMESTAMPTZ,
    created_at           TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_suppliers_tenant_status ON suppliers(tenant_id, status);
CREATE INDEX idx_suppliers_location      ON suppliers USING GIST(location);
CREATE INDEX idx_suppliers_trust         ON suppliers(trust_score DESC);
CREATE INDEX idx_suppliers_user          ON suppliers(user_id);

INSERT INTO suppliers (id, tenant_id, user_id, business_name, owner_phone, gst_number,
    location, zone_visibility, trust_score, vitality_score, status,
    profile_complete_pct, is_verified, export_certified, last_active_at)
VALUES
    (
        'b0000000-0000-0000-0000-000000000001',
        'f0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000002',
        'Sri Murugan Yarn Traders', '+919800000002', '33AAAAA0001A1Z5',
        ST_SetSRID(ST_MakePoint(77.3411, 11.1085), 4326)::geography,
        1, 82, 75, 'ACTIVE', 100, true, false,
        NOW() - INTERVAL '1 day'
    ),
    (
        'b0000000-0000-0000-0000-000000000002',
        'f0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000003',
        'Arunachalam Knitting Works', '+919800000003', '33BBBBB0002B2Z6',
        ST_SetSRID(ST_MakePoint(77.3520, 11.1150), 4326)::geography,
        1, 75, 68, 'ACTIVE', 90, true, false,
        NOW() - INTERVAL '2 days'
    ),
    (
        'b0000000-0000-0000-0000-000000000003',
        'f0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000004',
        'Kumar Dyeing & Processing', '+919800000004', '33CCCCC0003C3Z7',
        ST_SetSRID(ST_MakePoint(77.3300, 11.1020), 4326)::geography,
        1, 68, 55, 'ACTIVE', 80, false, false,
        NOW() - INTERVAL '5 days'
    );

-- ── categories ───────────────────────────────────────────────
CREATE TABLE categories (
    id        SERIAL PRIMARY KEY,
    slug      VARCHAR(100) NOT NULL UNIQUE,
    name_en   VARCHAR(200) NOT NULL,
    name_ta   VARCHAR(200),
    parent_id INT REFERENCES categories(id)
);

INSERT INTO categories (slug, name_en) VALUES
    ('yarn',        'Yarn'),
    ('fabric',      'Fabric'),
    ('dyeing',      'Dyeing Service'),
    ('knitting',    'Knitting'),
    ('embroidery',  'Embroidery'),
    ('testing-lab', 'Testing Lab'),
    ('freight',     'Freight Agent');

-- ── listings ─────────────────────────────────────────────────
CREATE TYPE listing_type AS ENUM ('PRODUCT', 'SERVICE');

CREATE TABLE listings (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    supplier_id UUID         NOT NULL REFERENCES suppliers(id),
    type        listing_type NOT NULL,
    title_en    VARCHAR(300) NOT NULL,
    title_ta    VARCHAR(300),
    description TEXT,
    category_id INT          REFERENCES categories(id),
    tags        TEXT[]       NOT NULL DEFAULT '{}',
    is_active   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_listings_supplier ON listings(supplier_id);
CREATE INDEX idx_listings_active   ON listings(is_active) WHERE is_active = TRUE;

INSERT INTO listings (supplier_id, type, title_en, description, category_id, is_active)
VALUES
    ('b0000000-0000-0000-0000-000000000001', 'PRODUCT', 'Grey Cotton Yarn 30s Count',
     'High quality grey cotton yarn, 30s count. Bulk from 100kg. Consistent quality.',
     (SELECT id FROM categories WHERE slug = 'yarn'), true),

    ('b0000000-0000-0000-0000-000000000001', 'PRODUCT', 'White Combed Yarn 40s Count',
     'Premium white combed yarn, 40s count. Ideal for fine knitting. Min 50kg.',
     (SELECT id FROM categories WHERE slug = 'yarn'), true),

    ('b0000000-0000-0000-0000-000000000002', 'SERVICE', 'Circular Knitting All GSM',
     'Circular knitting for all GSM. 20 machines. 3-day turnaround.',
     (SELECT id FROM categories WHERE slug = 'knitting'), true),

    ('b0000000-0000-0000-0000-000000000002', 'SERVICE', 'Flat Knitting Custom Designs',
     'Flat knitting for custom patterns. Sample in 24 hours.',
     (SELECT id FROM categories WHERE slug = 'knitting'), true),

    ('b0000000-0000-0000-0000-000000000003', 'SERVICE', 'Reactive Dyeing Service',
     'Reactive dyeing for cotton fabrics. All shades. Lab dip 24h, bulk 5 days.',
     (SELECT id FROM categories WHERE slug = 'dyeing'), true),

    ('b0000000-0000-0000-0000-000000000003', 'SERVICE', 'Pigment Dyeing Fast Turnaround',
     'Pigment dyeing all fabric types. Excellent wash fastness. Min 50kg per shade.',
     (SELECT id FROM categories WHERE slug = 'dyeing'), true);

-- ── inquiries ────────────────────────────────────────────────
CREATE TYPE inquiry_status AS ENUM ('OPEN', 'RESPONDED', 'CLOSED');

CREATE TABLE inquiries (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    supplier_id           UUID           NOT NULL REFERENCES suppliers(id),
    buyer_id              UUID           NOT NULL REFERENCES users(id),
    search_result_item_id UUID,
    message               TEXT           NOT NULL,
    status                inquiry_status NOT NULL DEFAULT 'OPEN',
    responded_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inquiries_supplier ON inquiries(supplier_id);
CREATE INDEX idx_inquiries_buyer    ON inquiries(buyer_id);
CREATE INDEX idx_inquiries_created  ON inquiries(created_at DESC);

-- ── search_log ───────────────────────────────────────────────
CREATE TABLE search_log (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id     UUID        NOT NULL,
    tenant_id      VARCHAR(50) NOT NULL,
    buyer_id       UUID,
    query_text     TEXT        NOT NULL,
    location       GEOGRAPHY(POINT, 4326),
    zone           SMALLINT,
    result_count   INT         NOT NULL DEFAULT 0,
    is_zero_result BOOLEAN     NOT NULL DEFAULT FALSE,
    searched_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_search_log_tenant ON search_log(tenant_id, searched_at DESC);
CREATE INDEX idx_search_log_zero   ON search_log(is_zero_result) WHERE is_zero_result = TRUE;

-- ── search_result_item ───────────────────────────────────────
CREATE TABLE search_result_item (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    search_log_id   UUID     NOT NULL REFERENCES search_log(id),
    supplier_id     UUID     NOT NULL REFERENCES suppliers(id),
    query_text      TEXT,
    position_shown  SMALLINT NOT NULL,
    relevance_score NUMERIC(8, 4),
    buyer_rating    SMALLINT,
    contacted       BOOLEAN  NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_sri_search_log ON search_result_item(search_log_id);
CREATE INDEX idx_sri_supplier   ON search_result_item(supplier_id);

-- ── vitality_events ──────────────────────────────────────────
CREATE TYPE vitality_signal AS ENUM (
    'WA_RESPONSE', 'INQUIRY_RESPONDED', 'CATALOGUE_UPDATED',
    'PHONE_VERIFIED', 'APP_LOGIN', 'INQUIRY_CREATED'
);

CREATE TABLE vitality_events (
    id          BIGSERIAL PRIMARY KEY,
    supplier_id UUID            NOT NULL REFERENCES suppliers(id),
    signal      vitality_signal NOT NULL,
    points      SMALLINT        NOT NULL,
    occurred_at TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vitality_supplier_time ON vitality_events(supplier_id, occurred_at DESC);

INSERT INTO vitality_events (supplier_id, signal, points, occurred_at) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'WA_RESPONSE',       35, NOW() - INTERVAL '2 days'),
    ('b0000000-0000-0000-0000-000000000001', 'INQUIRY_RESPONDED', 28, NOW() - INTERVAL '5 days'),
    ('b0000000-0000-0000-0000-000000000001', 'CATALOGUE_UPDATED', 20, NOW() - INTERVAL '10 days'),
    ('b0000000-0000-0000-0000-000000000002', 'WA_RESPONSE',       35, NOW() - INTERVAL '3 days'),
    ('b0000000-0000-0000-0000-000000000002', 'CATALOGUE_UPDATED', 20, NOW() - INTERVAL '7 days'),
    ('b0000000-0000-0000-0000-000000000003', 'WA_RESPONSE',       35, NOW() - INTERVAL '6 days'),
    ('b0000000-0000-0000-0000-000000000003', 'APP_LOGIN',          5, NOW() - INTERVAL '4 days');

-- ── outbox_events ─────────────────────────────────────────────
CREATE TYPE outbox_status AS ENUM ('PENDING', 'PROCESSED', 'FAILED');

CREATE TABLE outbox_events (
    id           BIGSERIAL PRIMARY KEY,
    event_id     UUID          NOT NULL UNIQUE DEFAULT uuid_generate_v4(),
    aggregate_id VARCHAR(100)  NOT NULL,
    event_type   VARCHAR(100)  NOT NULL,
    tenant_id    VARCHAR(50)   NOT NULL,
    payload      JSONB         NOT NULL,
    status       outbox_status NOT NULL DEFAULT 'PENDING',
    retry_count  SMALLINT      NOT NULL DEFAULT 0,
    last_error   TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_failed  ON outbox_events(status, retry_count) WHERE status = 'FAILED';

-- ── notification_log ─────────────────────────────────────────
CREATE TABLE notification_log (
    id           BIGSERIAL PRIMARY KEY,
    supplier_id  UUID        NOT NULL REFERENCES suppliers(id),
    channel      VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    message_type VARCHAR(50) NOT NULL,
    phone        VARCHAR(15) NOT NULL,
    delivered    BOOLEAN,
    error        TEXT,
    sent_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_supplier ON notification_log(supplier_id, sent_at DESC);
