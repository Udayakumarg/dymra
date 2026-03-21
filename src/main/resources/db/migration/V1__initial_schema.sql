-- V1__initial_schema.sql
-- TirupurConnect — idempotent schema + rich seed data
-- Safe to run multiple times (IF NOT EXISTS everywhere)

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

-- ── custom types (safe re-run) ────────────────────────────────
DO $$ BEGIN CREATE TYPE user_role AS ENUM ('SUPPLIER','BUYER','ADMIN');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE supplier_status AS ENUM ('ACTIVE','DORMANT','FADING','GHOST','CLOSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE listing_type AS ENUM ('PRODUCT','SERVICE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE inquiry_status AS ENUM ('OPEN','RESPONDED','CLOSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE vitality_signal AS ENUM ('WA_RESPONSE','INQUIRY_RESPONDED','CATALOGUE_UPDATED','PHONE_VERIFIED','APP_LOGIN','INQUIRY_CREATED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE outbox_status AS ENUM ('PENDING','PROCESSED','FAILED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ── tenants ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tenants (
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
    'tiruppur-zone1', 'Tiruppur', 15,
    '{"wa_response":35,"inquiry_responded":28,"catalogue_updated":20,"phone_verified":12,"app_login":5}',
    '[{"name":"Pongal","start":"01-14","end":"01-17"},{"name":"Tamil New Year","start":"04-14","end":"04-14"},{"name":"Diwali","start":"10-20","end":"11-05"}]',
    '{"bm25_weight":0.50,"trust_weight":0.35,"freshness_weight":0.15}'
) ON CONFLICT DO NOTHING;

-- ── users ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    phone          VARCHAR(15)  NOT NULL,
    phone_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    role           user_role    NOT NULL,
    name           VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, phone)
);

CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);

INSERT INTO users (id, tenant_id, phone, phone_verified, role, name) VALUES
    ('a0000000-0000-0000-0000-000000000001','f0000000-0000-0000-0000-000000000001','+919800000001',true,'ADMIN',   'Admin User'),
    ('a0000000-0000-0000-0000-000000000002','f0000000-0000-0000-0000-000000000001','+919800000002',true,'SUPPLIER','Arunachalam Kandasamy'),
    ('a0000000-0000-0000-0000-000000000003','f0000000-0000-0000-0000-000000000001','+919800000003',true,'SUPPLIER','Senthil Murugan'),
    ('a0000000-0000-0000-0000-000000000004','f0000000-0000-0000-0000-000000000001','+919800000004',true,'SUPPLIER','Kumar Rajan'),
    ('a0000000-0000-0000-0000-000000000005','f0000000-0000-0000-0000-000000000001','+919800000005',true,'SUPPLIER','Velmurugan S'),
    ('a0000000-0000-0000-0000-000000000006','f0000000-0000-0000-0000-000000000001','+919800000006',true,'SUPPLIER','Rajendran Palani'),
    ('a0000000-0000-0000-0000-000000000007','f0000000-0000-0000-0000-000000000001','+919800000007',true,'SUPPLIER','Mani Exports'),
    ('a0000000-0000-0000-0000-000000000008','f0000000-0000-0000-0000-000000000001','+919800000008',true,'SUPPLIER','Sundaram Knits'),
    ('a0000000-0000-0000-0000-000000000009','f0000000-0000-0000-0000-000000000001','+919800000009',true,'SUPPLIER','Palani Dyeing Works'),
    ('a0000000-0000-0000-0000-000000000010','f0000000-0000-0000-0000-000000000001','+919800000010',true,'SUPPLIER','Karuppasamy Fabrics'),
    ('a0000000-0000-0000-0000-000000000011','f0000000-0000-0000-0000-000000000001','+919800000011',true,'SUPPLIER','Ganesan Embroidery'),
    ('a0000000-0000-0000-0000-000000000012','f0000000-0000-0000-0000-000000000001','+919800000012',true,'SUPPLIER','Murugesan Testing Lab'),
    ('a0000000-0000-0000-0000-000000000013','f0000000-0000-0000-0000-000000000001','+919800000013',true,'SUPPLIER','Selvaraj Hosiery'),
    ('a0000000-0000-0000-0000-000000000014','f0000000-0000-0000-0000-000000000001','+919800000014',true,'SUPPLIER','Balamurugan Textiles'),
    ('a0000000-0000-0000-0000-000000000015','f0000000-0000-0000-0000-000000000001','+919800000015',true,'SUPPLIER','Annamalai Spinning'),
    ('a0000000-0000-0000-0000-000000000016','f0000000-0000-0000-0000-000000000001','+919800000016',true,'SUPPLIER','Thangavel Garments'),
    ('a0000000-0000-0000-0000-000000000017','f0000000-0000-0000-0000-000000000001','+919800000017',true,'SUPPLIER','Perumal Freight Services'),
    ('a0000000-0000-0000-0000-000000000018','f0000000-0000-0000-0000-000000000001','+919800000018',true,'BUYER',   'Ramesh Kumar'),
    ('a0000000-0000-0000-0000-000000000019','f0000000-0000-0000-0000-000000000001','+919800000019',true,'BUYER',   'Priya Exports'),
    ('a0000000-0000-0000-0000-000000000020','f0000000-0000-0000-0000-000000000001','+919800000020',true,'BUYER',   'Suresh Traders'),
    ('a0000000-0000-0000-0000-000000000021','f0000000-0000-0000-0000-000000000001','+919800000021',true,'BUYER',   'Meena Garments'),
    ('a0000000-0000-0000-0000-000000000022','f0000000-0000-0000-0000-000000000001','+919800000022',true,'BUYER',   'Vijay Buyer')
ON CONFLICT DO NOTHING;

-- ── suppliers ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS suppliers (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id            UUID            NOT NULL REFERENCES tenants(id),
    user_id              UUID            NOT NULL REFERENCES users(id),
    business_name        VARCHAR(200)    NOT NULL,
    owner_phone          VARCHAR(15)     NOT NULL,
    gst_number           VARCHAR(15),
    location             GEOGRAPHY(POINT,4326),
    zone_visibility      SMALLINT        NOT NULL DEFAULT 1,
    trust_score          SMALLINT        NOT NULL DEFAULT 0,
    vitality_score       SMALLINT        NOT NULL DEFAULT 0,
    status               supplier_status NOT NULL DEFAULT 'DORMANT',
    profile_complete_pct SMALLINT        NOT NULL DEFAULT 0,
    is_verified          BOOLEAN         NOT NULL DEFAULT FALSE,
    export_certified     BOOLEAN         NOT NULL DEFAULT FALSE,
    business_hours       JSONB,
    last_active_at       TIMESTAMPTZ,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_suppliers_tenant_status ON suppliers(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_suppliers_location      ON suppliers USING GIST(location);
CREATE INDEX IF NOT EXISTS idx_suppliers_trust         ON suppliers(trust_score DESC);
CREATE INDEX IF NOT EXISTS idx_suppliers_user          ON suppliers(user_id);

INSERT INTO suppliers (id,tenant_id,user_id,business_name,owner_phone,gst_number,location,zone_visibility,trust_score,vitality_score,status,profile_complete_pct,is_verified,export_certified,last_active_at) VALUES

('b0000000-0000-0000-0000-000000000001','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000002',
 'Sri Murugan Yarn Traders','+919800000002','33AAAAA0001A1Z5',
 ST_SetSRID(ST_MakePoint(77.3411,11.1085),4326)::geography,1,91,88,'ACTIVE',100,true,true,NOW()-INTERVAL '6 hours'),

('b0000000-0000-0000-0000-000000000002','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000003',
 'Tiruppur Cotton Mills','+919800000003','33BBBBB0002B2Z6',
 ST_SetSRID(ST_MakePoint(77.3520,11.1150),4326)::geography,1,85,80,'ACTIVE',100,true,true,NOW()-INTERVAL '1 day'),

('b0000000-0000-0000-0000-000000000003','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000004',
 'Arunachalam Knitting Works','+919800000004','33CCCCC0003C3Z7',
 ST_SetSRID(ST_MakePoint(77.3300,11.1020),4326)::geography,1,78,72,'ACTIVE',90,true,false,NOW()-INTERVAL '2 days'),

('b0000000-0000-0000-0000-000000000004','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000005',
 'Sundaram Knits and Fabrics','+919800000005','33DDDDD0004D4Z8',
 ST_SetSRID(ST_MakePoint(77.3600,11.1200),4326)::geography,1,74,68,'ACTIVE',90,true,false,NOW()-INTERVAL '3 days'),

('b0000000-0000-0000-0000-000000000005','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000006',
 'Kumar Dyeing and Processing','+919800000006','33EEEEE0005E5Z9',
 ST_SetSRID(ST_MakePoint(77.3250,11.0980),4326)::geography,1,70,65,'ACTIVE',85,false,false,NOW()-INTERVAL '4 days'),

('b0000000-0000-0000-0000-000000000006','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000007',
 'Palani Colour House','+919800000007','33FFFFF0006F6Z0',
 ST_SetSRID(ST_MakePoint(77.3700,11.1300),4326)::geography,1,67,60,'ACTIVE',80,false,false,NOW()-INTERVAL '5 days'),

('b0000000-0000-0000-0000-000000000007','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000008',
 'Mani Exports and Imports','+919800000008','33GGGGG0007G7Z1',
 ST_SetSRID(ST_MakePoint(77.3450,11.1100),4326)::geography,1,88,82,'ACTIVE',100,true,true,NOW()-INTERVAL '12 hours'),

('b0000000-0000-0000-0000-000000000008','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000009',
 'Ganesan Embroidery Works','+919800000009','33HHHHH0008H8Z2',
 ST_SetSRID(ST_MakePoint(77.3380,11.1060),4326)::geography,1,65,58,'ACTIVE',80,false,false,NOW()-INTERVAL '6 days'),

('b0000000-0000-0000-0000-000000000009','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000010',
 'Tiruppur Textile Testing Lab','+919800000010','33IIIII0009I9Z3',
 ST_SetSRID(ST_MakePoint(77.3480,11.1130),4326)::geography,1,82,75,'ACTIVE',95,true,false,NOW()-INTERVAL '1 day'),

('b0000000-0000-0000-0000-000000000010','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000011',
 'Karuppasamy Cotton Fabrics','+919800000011','33JJJJJ0010J0Z4',
 ST_SetSRID(ST_MakePoint(77.3350,11.1040),4326)::geography,1,72,64,'ACTIVE',85,false,false,NOW()-INTERVAL '3 days'),

('b0000000-0000-0000-0000-000000000011','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000012',
 'Selvaraj Hosiery Mills','+919800000012','33KKKKK0011K1Z5',
 ST_SetSRID(ST_MakePoint(77.3550,11.1170),4326)::geography,1,76,70,'ACTIVE',90,true,false,NOW()-INTERVAL '2 days'),

('b0000000-0000-0000-0000-000000000012','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000013',
 'Balamurugan Textile Park','+919800000013','33LLLLL0012L2Z6',
 ST_SetSRID(ST_MakePoint(77.3620,11.1220),4326)::geography,1,69,62,'ACTIVE',80,false,false,NOW()-INTERVAL '4 days'),

('b0000000-0000-0000-0000-000000000013','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000014',
 'Annamalai Spinning Mills','+919800000014','33MMMMM0013M3Z7',
 ST_SetSRID(ST_MakePoint(77.3280,11.0960),4326)::geography,1,80,74,'ACTIVE',95,true,false,NOW()-INTERVAL '1 day'),

('b0000000-0000-0000-0000-000000000014','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000015',
 'Thangavel Export Garments','+919800000015','33NNNNN0014N4Z8',
 ST_SetSRID(ST_MakePoint(77.3490,11.1120),4326)::geography,1,83,76,'ACTIVE',100,true,true,NOW()-INTERVAL '8 hours'),

('b0000000-0000-0000-0000-000000000015','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000016',
 'Perumal Freight and Logistics','+919800000016','33OOOOO0015O5Z9',
 ST_SetSRID(ST_MakePoint(77.3420,11.1090),4326)::geography,1,71,65,'ACTIVE',85,false,false,NOW()-INTERVAL '2 days'),

-- Dormant suppliers for realistic data
('b0000000-0000-0000-0000-000000000016','f0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000017',
 'Rajendran Knit Fabrics','+919800000017',NULL,
 ST_SetSRID(ST_MakePoint(77.3550,11.1170),4326)::geography,1,42,38,'DORMANT',60,false,false,NOW()-INTERVAL '40 days')

ON CONFLICT DO NOTHING;

-- ── categories ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS categories (
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
    ('freight',     'Freight Agent')
ON CONFLICT DO NOTHING;

-- ── listings ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS listings (
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

CREATE INDEX IF NOT EXISTS idx_listings_supplier ON listings(supplier_id);
CREATE INDEX IF NOT EXISTS idx_listings_active   ON listings(is_active) WHERE is_active = TRUE;

INSERT INTO listings (supplier_id,type,title_en,description,category_id,is_active) VALUES

-- Sri Murugan Yarn Traders
('b0000000-0000-0000-0000-000000000001','PRODUCT','Grey Cotton Yarn 30s Count',
 'High quality grey cotton yarn 30s count. Uniform strength and consistent twist. Available from 100kg. Same day dispatch for orders before 12pm. Competitive pricing for regular buyers.',
 (SELECT id FROM categories WHERE slug='yarn'),true),

('b0000000-0000-0000-0000-000000000001','PRODUCT','White Combed Yarn 40s Count',
 'Premium white combed yarn 40s count. Zero neps and excellent elongation. Ideal for fine knitting and hosiery. Minimum order 50kg. Test reports available.',
 (SELECT id FROM categories WHERE slug='yarn'),true),

('b0000000-0000-0000-0000-000000000001','PRODUCT','Blended PC Yarn 60 40 Mix',
 'Polyester cotton blended yarn 60 40 ratio. Available in 30s and 40s count. Suitable for sportswear and workwear. Good pilling resistance. Min 200kg.',
 (SELECT id FROM categories WHERE slug='yarn'),true),

('b0000000-0000-0000-0000-000000000001','PRODUCT','Compact Yarn 50s Count',
 'Compact spun yarn 50s count for premium knitwear. Reduced hairiness and higher strength than ring spun. Min 100kg.',
 (SELECT id FROM categories WHERE slug='yarn'),true),

-- Tiruppur Cotton Mills
('b0000000-0000-0000-0000-000000000002','PRODUCT','Organic Cotton Yarn 20s GOTS Certified',
 'GOTS certified organic cotton yarn 20s count. Chemical free processing. Export quality with full traceability. Test reports included. Min 200kg.',
 (SELECT id FROM categories WHERE slug='yarn'),true),

('b0000000-0000-0000-0000-000000000002','PRODUCT','Carded Cotton Yarn 24s',
 'Carded cotton yarn 24s count. Good for mid-range knitting. Competitive bulk pricing above 500kg. Consistent lot-to-lot quality.',
 (SELECT id FROM categories WHERE slug='yarn'),true),

('b0000000-0000-0000-0000-000000000002','PRODUCT','Melange Yarn 30s Grey Mix',
 'Cotton melange yarn 30s count in grey mix shades. Ready to knit without dyeing. Popular for casual wear. Min 100kg per shade.',
 (SELECT id FROM categories WHERE slug='yarn'),true),

-- Arunachalam Knitting Works
('b0000000-0000-0000-0000-000000000003','SERVICE','Circular Knitting All GSM',
 'Circular knitting for 120 to 280 GSM single jersey. 24 machines operational. 3 to 5 day turnaround on bulk. Consistent width and GSM across rolls.',
 (SELECT id FROM categories WHERE slug='knitting'),true),

('b0000000-0000-0000-0000-000000000003','SERVICE','Rib Knitting 1x1 and 2x2',
 '1x1 and 2x2 rib knitting for collars and cuffs. All yarn counts accepted. Sample in 24 hours. Bulk lead time 3 days. SSIDC empanelled unit.',
 (SELECT id FROM categories WHERE slug='knitting'),true),

('b0000000-0000-0000-0000-000000000003','SERVICE','Interlock Fabric Knitting',
 'Double jersey interlock fabric. Smooth on both sides. 180 to 220 GSM. Ideal for premium t-shirts and polo shirts. Min 200kg per order.',
 (SELECT id FROM categories WHERE slug='knitting'),true),

-- Sundaram Knits
('b0000000-0000-0000-0000-000000000004','SERVICE','Flat Knitting Custom Designs',
 'Computerised flat knitting for custom designs. Fully fashioned garment panels. Intarsia and jacquard patterns available. Sample in 24 hours.',
 (SELECT id FROM categories WHERE slug='knitting'),true),

('b0000000-0000-0000-0000-000000000004','PRODUCT','Single Jersey Fabric 160 GSM',
 'Ready to cut single jersey fabric 160 GSM. Open width processing. Available in white and off-white. Min 200 metres. Consistent GSM guaranteed.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

('b0000000-0000-0000-0000-000000000004','PRODUCT','French Terry Fabric 280 GSM',
 'French terry loop fabric 280 GSM for sweatshirts and hoodies. 100 percent cotton. Soft inner loop and smooth outer face. Min 300 metres.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

-- Kumar Dyeing
('b0000000-0000-0000-0000-000000000005','SERVICE','Reactive Dyeing Cotton Fabrics',
 'Reactive dyeing for all cotton fabrics. 500 shade library with pantone matching. Lab dip in 24 hours. Bulk processing 5 to 7 days. ISO 9001 certified.',
 (SELECT id FROM categories WHERE slug='dyeing'),true),

('b0000000-0000-0000-0000-000000000005','SERVICE','Pigment Dyeing Fast Turnaround',
 'Pigment dyeing for cotton and blends. Excellent wash fastness grade 4 and above. Min 50kg per shade. 3 day turnaround. Good for dark shades.',
 (SELECT id FROM categories WHERE slug='dyeing'),true),

('b0000000-0000-0000-0000-000000000005','SERVICE','Enzyme Wash and Bio Wash',
 'Enzyme bio wash, stone wash, acid wash for knitwear. Soft hand feel and premium look. Min 100 pieces per style. 2 day turnaround.',
 (SELECT id FROM categories WHERE slug='dyeing'),true),

('b0000000-0000-0000-0000-000000000005','SERVICE','Softening and Finishing',
 'Fabric softening and finishing for all knit fabrics. Silicone and non-silicone options. Open width and tubular both available.',
 (SELECT id FROM categories WHERE slug='dyeing'),true),

-- Palani Colour House
('b0000000-0000-0000-0000-000000000006','SERVICE','Yarn Dyeing All Counts',
 'Package dyeing for all yarn counts. Reactive vat and direct dyes available. Consistent shade matching across lots. Min 50kg per shade.',
 (SELECT id FROM categories WHERE slug='dyeing'),true),

('b0000000-0000-0000-0000-000000000006','SERVICE','Fabric Bleaching and Whitening',
 'Optical bleaching and whitening for cotton fabrics. Blue white and neutral white options. Consistent whiteness index. Min 200kg.',
 (SELECT id FROM categories WHERE slug='dyeing'),true),

('b0000000-0000-0000-0000-000000000006','SERVICE','Vat Dyeing Dark Shades',
 'Vat dyeing for deep dark shades with excellent wash fastness. Navy black bottle green olive available. Min 100kg per shade.',
 (SELECT id FROM categories WHERE slug='dyeing'),true),

-- Mani Exports
('b0000000-0000-0000-0000-000000000007','PRODUCT','Polo Pique Fabric 220 GSM',
 'Pique fabric for polo shirts. 220 GSM 100 percent cotton. Available white for dyeing or yarn dyed. OEKO-TEX certified. Min 500 metres.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

('b0000000-0000-0000-0000-000000000007','PRODUCT','Terry Towel Fabric 380 GSM',
 'Loop terry fabric for towels and bathrobes. 380 GSM 100 percent cotton. OEKO-TEX certified. Export quality with test reports. Min 300 metres.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

('b0000000-0000-0000-0000-000000000007','PRODUCT','Fleece Fabric 300 GSM Anti-Pill',
 'Polar fleece fabric 300 GSM polyester. Anti-pill finish. Solid colours available. Ideal for jackets and sweatshirts. Min 500 metres.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

('b0000000-0000-0000-0000-000000000007','PRODUCT','Waffle Fabric 200 GSM',
 'Waffle texture knit fabric 200 GSM. 100 percent cotton. Popular for bathrobes and loungewear. White and ecru available. Min 200 metres.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

-- Ganesan Embroidery
('b0000000-0000-0000-0000-000000000008','SERVICE','Computerised Embroidery All Fabrics',
 'Computerised embroidery on all fabric types. Up to 15 colour threads. Logo and motif digitising included free. Min 100 pieces.',
 (SELECT id FROM categories WHERE slug='embroidery'),true),

('b0000000-0000-0000-0000-000000000008','SERVICE','Sequin and Bead Embellishment',
 'Machine and hand sequin work. Bead and stone embellishments. Suitable for ethnic and export garments. Sample in 2 days. Min 50 pieces.',
 (SELECT id FROM categories WHERE slug='embroidery'),true),

('b0000000-0000-0000-0000-000000000008','SERVICE','Applique and Patch Work',
 'Applique work and patch embroidery for garments and home textiles. Custom shapes and sizes. Min 200 pieces per design.',
 (SELECT id FROM categories WHERE slug='embroidery'),true),

-- Testing Lab
('b0000000-0000-0000-0000-000000000009','SERVICE','Fabric Testing NABL Accredited',
 'NABL accredited testing lab. GSM tensile strength shrinkage colour fastness pH. Reports in 3 working days. Accepted by all major international buyers.',
 (SELECT id FROM categories WHERE slug='testing-lab'),true),

('b0000000-0000-0000-0000-000000000009','SERVICE','REACH Chemical Compliance Testing',
 'REACH compliance testing for EU market. AZO dye formaldehyde heavy metals. Reports accepted by EU and US buyers. 5 day turnaround.',
 (SELECT id FROM categories WHERE slug='testing-lab'),true),

('b0000000-0000-0000-0000-000000000009','SERVICE','Yarn Count and Strength Testing',
 'Yarn count verification twist per metre tensile strength elongation. 24 hour turnaround. Bulk testing rates available. Online report delivery.',
 (SELECT id FROM categories WHERE slug='testing-lab'),true),

('b0000000-0000-0000-0000-000000000009','SERVICE','Wash Fastness and Colorfastness',
 'ISO and AATCC wash fastness testing. Rubbing perspiration light fastness. Quick results in 48 hours. Competitive rates for bulk testing.',
 (SELECT id FROM categories WHERE slug='testing-lab'),true),

-- Karuppasamy Fabrics
('b0000000-0000-0000-0000-000000000010','PRODUCT','Cotton Hosiery Fabric Grey',
 'Grey knitted hosiery fabric 160 to 200 GSM. Direct from knitting unit. No middleman pricing. Available in tubular and open width. Min 100kg.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

('b0000000-0000-0000-0000-000000000010','PRODUCT','Viscose Single Jersey 170 GSM',
 'Viscose single jersey 170 GSM. Soft and drapey. Ideal for ladies tops and premium t-shirts. White and off-white base. Min 200 metres.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

('b0000000-0000-0000-0000-000000000010','PRODUCT','Cotton Lycra Fabric 200 GSM',
 'Cotton lycra 95 5 fabric 200 GSM. Excellent stretch and recovery. Good for sportswear and fitted garments. Min 150 metres per colour.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

-- Selvaraj Hosiery Mills
('b0000000-0000-0000-0000-000000000011','PRODUCT','Hosiery T-Shirt Fabric 180 GSM',
 'Ready knit single jersey for t-shirts 180 GSM. Available in white grey and black. Consistent width 60 to 70 inches. Min 300 metres.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

('b0000000-0000-0000-0000-000000000011','SERVICE','Knitting Job Work Per KG',
 'Knitting job work for your yarn. All counts 20s to 60s accepted. Single jersey interlock rib available. 3 day turnaround. Transparent per kg pricing.',
 (SELECT id FROM categories WHERE slug='knitting'),true),

-- Balamurugan Textiles
('b0000000-0000-0000-0000-000000000012','PRODUCT','Printed Fabric Rotary and Digital',
 'Rotary screen printing and digital printing on cotton fabric. All over prints placement prints. Design support available. Min 300 metres per design.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

('b0000000-0000-0000-0000-000000000012','SERVICE','Heat Transfer Printing',
 'Heat transfer printing for garments and fabrics. Photo quality prints. No minimum order for samples. Bulk from 200 pieces.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

-- Annamalai Spinning
('b0000000-0000-0000-0000-000000000013','PRODUCT','Ring Spun Yarn 30s 40s 60s',
 'Premium ring spun cotton yarn. 30s 40s and 60s counts available. Consistent count CV below 2 percent. Long staple cotton. Export quality.',
 (SELECT id FROM categories WHERE slug='yarn'),true),

('b0000000-0000-0000-0000-000000000013','PRODUCT','Open End Yarn 10s 16s 20s',
 'Open end spun yarn for denim and heavy fabric. 10s 16s and 20s counts. Cost effective for bulk. Min 500kg per count.',
 (SELECT id FROM categories WHERE slug='yarn'),true),

('b0000000-0000-0000-0000-000000000013','PRODUCT','Slub Yarn for Fashion Fabric',
 'Slub yarn for fashion and textured fabrics. Custom slub parameters available. Adds visual interest to plain fabrics. Min 200kg.',
 (SELECT id FROM categories WHERE slug='yarn'),true),

-- Thangavel Garments
('b0000000-0000-0000-0000-000000000014','PRODUCT','Basic T-Shirts Bulk Ready',
 'Plain t-shirts for printing and embroidery. 180 GSM 100 percent cotton. Sizes XS to 5XL. White black grey navy available. Min 500 pieces.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

('b0000000-0000-0000-0000-000000000014','PRODUCT','Polo Shirts Blank for Branding',
 'Blank polo shirts for corporate and promotional branding. 220 GSM pique. All sizes. Quick delivery 7 days. Min 200 pieces.',
 (SELECT id FROM categories WHERE slug='fabric'),true),

('b0000000-0000-0000-0000-000000000014','SERVICE','CMT Garment Making Service',
 'Cut make trim service for knit garments. T-shirts polos sweatshirts hoodies. Experienced workers. Capacity 5000 pieces per day. Min 500 pieces.',
 (SELECT id FROM categories WHERE slug='knitting'),true),

-- Perumal Freight
('b0000000-0000-0000-0000-000000000015','SERVICE','Export Freight Air and Sea',
 'Export freight forwarding air and sea. CIF FOB pricing. All destinations USA UK Europe Australia. Customs clearance included. 15 years experience.',
 (SELECT id FROM categories WHERE slug='freight'),true),

('b0000000-0000-0000-0000-000000000015','SERVICE','Domestic Courier and Truck',
 'Domestic courier and truck transport for fabric and garments. Pan India delivery. Same day pickup in Tiruppur. GPS tracked vehicles.',
 (SELECT id FROM categories WHERE slug='freight'),true),

('b0000000-0000-0000-0000-000000000015','SERVICE','Custom Clearance and Documentation',
 'Import and export custom clearance. All documentation GSP FORM A certificate of origin. Quick clearance at Chennai and Coimbatore.',
 (SELECT id FROM categories WHERE slug='freight'),true)

ON CONFLICT DO NOTHING;

-- ── inquiries ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS inquiries (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    supplier_id           UUID           NOT NULL REFERENCES suppliers(id),
    buyer_id              UUID           NOT NULL REFERENCES users(id),
    search_result_item_id UUID,
    message               TEXT           NOT NULL,
    status                inquiry_status NOT NULL DEFAULT 'OPEN',
    responded_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_inquiries_supplier ON inquiries(supplier_id);
CREATE INDEX IF NOT EXISTS idx_inquiries_buyer    ON inquiries(buyer_id);
CREATE INDEX IF NOT EXISTS idx_inquiries_created  ON inquiries(created_at DESC);

INSERT INTO inquiries (supplier_id,buyer_id,message,status,responded_at,created_at) VALUES
    ('b0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000018','Need 500kg grey yarn 30s count urgently. Please share best price and delivery date.','RESPONDED',NOW()-INTERVAL '2 days',NOW()-INTERVAL '3 days'),
    ('b0000000-0000-0000-0000-000000000002','a0000000-0000-0000-0000-000000000018','Looking for organic cotton yarn for US export order. Need GOTS certificate and test reports.','RESPONDED',NOW()-INTERVAL '1 day',NOW()-INTERVAL '2 days'),
    ('b0000000-0000-0000-0000-000000000005','a0000000-0000-0000-0000-000000000019','Need reactive dyeing for 200kg single jersey fabric. All black shade. Can you complete in 3 days?','OPEN',NULL,NOW()-INTERVAL '5 hours'),
    ('b0000000-0000-0000-0000-000000000009','a0000000-0000-0000-0000-000000000020','Need NABL test report for shrinkage and colour fastness for US buyer approval.','RESPONDED',NOW()-INTERVAL '12 hours',NOW()-INTERVAL '2 days'),
    ('b0000000-0000-0000-0000-000000000007','a0000000-0000-0000-0000-000000000019','Need polo pique fabric 220 GSM in 1000 metres. White base for dyeing. Urgent.','CLOSED',NOW()-INTERVAL '5 days',NOW()-INTERVAL '7 days'),
    ('b0000000-0000-0000-0000-000000000013','a0000000-0000-0000-0000-000000000021','Interested in ring spun yarn 40s count. Need 1000kg per month regular supply.','RESPONDED',NOW()-INTERVAL '6 hours',NOW()-INTERVAL '1 day'),
    ('b0000000-0000-0000-0000-000000000014','a0000000-0000-0000-0000-000000000022','Need 1000 plain white t-shirts 180 GSM for printing. Delivery in 5 days possible?','OPEN',NULL,NOW()-INTERVAL '2 hours'),
    ('b0000000-0000-0000-0000-000000000003','a0000000-0000-0000-0000-000000000020','Circular knitting enquiry — need 500kg single jersey 160 GSM from my yarn.','RESPONDED',NOW()-INTERVAL '3 days',NOW()-INTERVAL '4 days')
ON CONFLICT DO NOTHING;

-- ── search_log ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS search_log (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id     UUID        NOT NULL,
    tenant_id      VARCHAR(50) NOT NULL,
    buyer_id       UUID,
    query_text     TEXT        NOT NULL,
    location       GEOGRAPHY(POINT,4326),
    zone           SMALLINT,
    result_count   INT         NOT NULL DEFAULT 0,
    is_zero_result BOOLEAN     NOT NULL DEFAULT FALSE,
    searched_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_search_log_tenant ON search_log(tenant_id, searched_at DESC);
CREATE INDEX IF NOT EXISTS idx_search_log_zero   ON search_log(is_zero_result) WHERE is_zero_result = TRUE;

INSERT INTO search_log (session_id,tenant_id,buyer_id,query_text,location,zone,result_count,is_zero_result,searched_at) VALUES
    (uuid_generate_v4(),'tiruppur-zone1','a0000000-0000-0000-0000-000000000018','grey yarn',ST_SetSRID(ST_MakePoint(77.3411,11.1085),4326)::geography,1,4,false,NOW()-INTERVAL '3 days'),
    (uuid_generate_v4(),'tiruppur-zone1','a0000000-0000-0000-0000-000000000019','dyeing service',ST_SetSRID(ST_MakePoint(77.3411,11.1085),4326)::geography,1,3,false,NOW()-INTERVAL '1 day'),
    (uuid_generate_v4(),'tiruppur-zone1',NULL,'organic cotton yarn',ST_SetSRID(ST_MakePoint(77.3411,11.1085),4326)::geography,1,2,false,NOW()-INTERVAL '12 hours'),
    (uuid_generate_v4(),'tiruppur-zone1',NULL,'knitting job work',ST_SetSRID(ST_MakePoint(77.3411,11.1085),4326)::geography,1,3,false,NOW()-INTERVAL '8 hours'),
    (uuid_generate_v4(),'tiruppur-zone1','a0000000-0000-0000-0000-000000000020','testing lab',ST_SetSRID(ST_MakePoint(77.3411,11.1085),4326)::geography,1,1,false,NOW()-INTERVAL '4 hours'),
    (uuid_generate_v4(),'tiruppur-zone1',NULL,'silk fabric',ST_SetSRID(ST_MakePoint(77.3411,11.1085),4326)::geography,1,0,true,NOW()-INTERVAL '6 hours'),
    (uuid_generate_v4(),'tiruppur-zone1',NULL,'linen yarn',ST_SetSRID(ST_MakePoint(77.3411,11.1085),4326)::geography,1,0,true,NOW()-INTERVAL '2 hours')
ON CONFLICT DO NOTHING;

-- ── search_result_item ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS search_result_item (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    search_log_id   UUID     NOT NULL REFERENCES search_log(id),
    supplier_id     UUID     NOT NULL REFERENCES suppliers(id),
    query_text      TEXT,
    position_shown  SMALLINT NOT NULL,
    relevance_score NUMERIC(8,4),
    buyer_rating    SMALLINT,
    contacted       BOOLEAN  NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_sri_search_log ON search_result_item(search_log_id);
CREATE INDEX IF NOT EXISTS idx_sri_supplier   ON search_result_item(supplier_id);

-- ── vitality_events ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS vitality_events (
    id          BIGSERIAL PRIMARY KEY,
    supplier_id UUID            NOT NULL REFERENCES suppliers(id),
    signal      vitality_signal NOT NULL,
    points      SMALLINT        NOT NULL,
    occurred_at TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vitality_supplier_time ON vitality_events(supplier_id, occurred_at DESC);

INSERT INTO vitality_events (supplier_id,signal,points,occurred_at) VALUES
    ('b0000000-0000-0000-0000-000000000001','WA_RESPONSE',35,NOW()-INTERVAL '1 day'),
    ('b0000000-0000-0000-0000-000000000001','INQUIRY_RESPONDED',28,NOW()-INTERVAL '3 days'),
    ('b0000000-0000-0000-0000-000000000001','CATALOGUE_UPDATED',20,NOW()-INTERVAL '7 days'),
    ('b0000000-0000-0000-0000-000000000001','PHONE_VERIFIED',12,NOW()-INTERVAL '30 days'),
    ('b0000000-0000-0000-0000-000000000002','WA_RESPONSE',35,NOW()-INTERVAL '1 day'),
    ('b0000000-0000-0000-0000-000000000002','INQUIRY_RESPONDED',28,NOW()-INTERVAL '2 days'),
    ('b0000000-0000-0000-0000-000000000002','CATALOGUE_UPDATED',20,NOW()-INTERVAL '5 days'),
    ('b0000000-0000-0000-0000-000000000003','WA_RESPONSE',35,NOW()-INTERVAL '2 days'),
    ('b0000000-0000-0000-0000-000000000003','CATALOGUE_UPDATED',20,NOW()-INTERVAL '4 days'),
    ('b0000000-0000-0000-0000-000000000003','INQUIRY_RESPONDED',28,NOW()-INTERVAL '6 days'),
    ('b0000000-0000-0000-0000-000000000004','WA_RESPONSE',35,NOW()-INTERVAL '3 days'),
    ('b0000000-0000-0000-0000-000000000004','CATALOGUE_UPDATED',20,NOW()-INTERVAL '8 days'),
    ('b0000000-0000-0000-0000-000000000005','WA_RESPONSE',35,NOW()-INTERVAL '4 days'),
    ('b0000000-0000-0000-0000-000000000005','CATALOGUE_UPDATED',20,NOW()-INTERVAL '9 days'),
    ('b0000000-0000-0000-0000-000000000006','WA_RESPONSE',35,NOW()-INTERVAL '5 days'),
    ('b0000000-0000-0000-0000-000000000006','APP_LOGIN',5,NOW()-INTERVAL '3 days'),
    ('b0000000-0000-0000-0000-000000000007','WA_RESPONSE',35,NOW()-INTERVAL '12 hours'),
    ('b0000000-0000-0000-0000-000000000007','INQUIRY_RESPONDED',28,NOW()-INTERVAL '1 day'),
    ('b0000000-0000-0000-0000-000000000007','CATALOGUE_UPDATED',20,NOW()-INTERVAL '3 days'),
    ('b0000000-0000-0000-0000-000000000007','PHONE_VERIFIED',12,NOW()-INTERVAL '15 days'),
    ('b0000000-0000-0000-0000-000000000008','WA_RESPONSE',35,NOW()-INTERVAL '6 days'),
    ('b0000000-0000-0000-0000-000000000008','APP_LOGIN',5,NOW()-INTERVAL '4 days'),
    ('b0000000-0000-0000-0000-000000000009','WA_RESPONSE',35,NOW()-INTERVAL '1 day'),
    ('b0000000-0000-0000-0000-000000000009','INQUIRY_RESPONDED',28,NOW()-INTERVAL '2 days'),
    ('b0000000-0000-0000-0000-000000000009','CATALOGUE_UPDATED',20,NOW()-INTERVAL '6 days'),
    ('b0000000-0000-0000-0000-000000000010','WA_RESPONSE',35,NOW()-INTERVAL '3 days'),
    ('b0000000-0000-0000-0000-000000000010','CATALOGUE_UPDATED',20,NOW()-INTERVAL '10 days'),
    ('b0000000-0000-0000-0000-000000000011','WA_RESPONSE',35,NOW()-INTERVAL '2 days'),
    ('b0000000-0000-0000-0000-000000000011','INQUIRY_RESPONDED',28,NOW()-INTERVAL '5 days'),
    ('b0000000-0000-0000-0000-000000000012','WA_RESPONSE',35,NOW()-INTERVAL '4 days'),
    ('b0000000-0000-0000-0000-000000000012','CATALOGUE_UPDATED',20,NOW()-INTERVAL '7 days'),
    ('b0000000-0000-0000-0000-000000000013','WA_RESPONSE',35,NOW()-INTERVAL '1 day'),
    ('b0000000-0000-0000-0000-000000000013','INQUIRY_RESPONDED',28,NOW()-INTERVAL '6 hours'),
    ('b0000000-0000-0000-0000-000000000013','CATALOGUE_UPDATED',20,NOW()-INTERVAL '4 days'),
    ('b0000000-0000-0000-0000-000000000013','PHONE_VERIFIED',12,NOW()-INTERVAL '20 days'),
    ('b0000000-0000-0000-0000-000000000014','WA_RESPONSE',35,NOW()-INTERVAL '8 hours'),
    ('b0000000-0000-0000-0000-000000000014','INQUIRY_RESPONDED',28,NOW()-INTERVAL '2 days'),
    ('b0000000-0000-0000-0000-000000000014','CATALOGUE_UPDATED',20,NOW()-INTERVAL '5 days'),
    ('b0000000-0000-0000-0000-000000000015','WA_RESPONSE',35,NOW()-INTERVAL '2 days'),
    ('b0000000-0000-0000-0000-000000000015','CATALOGUE_UPDATED',20,NOW()-INTERVAL '6 days'),
    ('b0000000-0000-0000-0000-000000000016','APP_LOGIN',5,NOW()-INTERVAL '40 days')
ON CONFLICT DO NOTHING;

-- ── outbox_events ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS outbox_events (
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

CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events(status,created_at) WHERE status='PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_failed  ON outbox_events(status,retry_count) WHERE status='FAILED';

-- ── notification_log ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notification_log (
    id           BIGSERIAL PRIMARY KEY,
    supplier_id  UUID        NOT NULL REFERENCES suppliers(id),
    channel      VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    message_type VARCHAR(50) NOT NULL,
    phone        VARCHAR(15) NOT NULL,
    delivered    BOOLEAN,
    error        TEXT,
    sent_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notif_supplier ON notification_log(supplier_id, sent_at DESC);
