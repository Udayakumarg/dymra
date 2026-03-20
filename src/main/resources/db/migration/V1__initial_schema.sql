-- V1__initial_schema.sql
-- TirupurConnect — initial schema
-- Requires: postgresql + postgis extension

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

-- ── drop all tables first (reverse dependency order) ─────────
DROP TABLE IF EXISTS notification_log       CASCADE;
DROP TABLE IF EXISTS outbox_events          CASCADE;
DROP TABLE IF EXISTS vitality_events        CASCADE;
DROP TABLE IF EXISTS search_result_item     CASCADE;
DROP TABLE IF EXISTS search_log             CASCADE;
DROP TABLE IF EXISTS inquiries              CASCADE;
DROP TABLE IF EXISTS listings               CASCADE;
DROP TABLE IF EXISTS categories             CASCADE;
DROP TABLE IF EXISTS suppliers              CASCADE;
DROP TABLE IF EXISTS users                  CASCADE;
DROP TABLE IF EXISTS tenants                CASCADE;

-- ── drop all custom types ─────────────────────────────────────
DROP TYPE IF EXISTS outbox_status    CASCADE;
DROP TYPE IF EXISTS vitality_signal  CASCADE;
DROP TYPE IF EXISTS inquiry_status   CASCADE;
DROP TYPE IF EXISTS listing_type     CASCADE;
DROP TYPE IF EXISTS supplier_status  CASCADE;
DROP TYPE IF EXISTS user_role        CASCADE;

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

INSERT INTO tenants (slug, city_name, default_zone_radius_km, vitality_weights, seasonal_pauses, search_weights)
VALUES (
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

-- ── categories ───────────────────────────────────────────────
CREATE TABLE categories (
    id        SERIAL PRIMARY KEY,
    slug      VARCHAR(100) NOT NULL UNIQUE,
    name_en   VARCHAR(200) NOT NULL,
    name_ta   VARCHAR(200),
    parent_id INT REFERENCES categories(id)
);

INSERT INTO categories (slug, name_en, name_ta) VALUES
    ('yarn',        'Yarn',           NULL),
    ('fabric',      'Fabric',         NULL),
    ('dyeing',      'Dyeing Service', NULL),
    ('knitting',    'Knitting',       NULL),
    ('embroidery',  'Embroidery',     NULL),
    ('testing-lab', 'Testing Lab',    NULL),
    ('freight',     'Freight Agent',  NULL);

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
