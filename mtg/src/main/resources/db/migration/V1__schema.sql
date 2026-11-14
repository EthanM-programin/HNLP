-- V1__schema.sql : MTG Warehouse System (Epic Cure)
-- Safety rules are deterministic (clinical_condition + ruleset snapshot); the LLM never decides safety.

CREATE TABLE dietary_tier (
  id                    BIGSERIAL PRIMARY KEY,
  code                  VARCHAR(30)  NOT NULL UNIQUE,
  name                  VARCHAR(100) NOT NULL,
  description           TEXT,
  produce_lb_per_person NUMERIC(6,2) NOT NULL,
  protein_lb_per_person NUMERIC(6,2) NOT NULL,
  dairy_units_per_person NUMERIC(6,2) NOT NULL,
  grain_lb_per_person   NUMERIC(6,2) NOT NULL
);

-- Per-serving ceilings per ICD-10 condition. NULL = no limit. Strictest (MIN) wins across conditions.
CREATE TABLE clinical_condition (
  id                BIGSERIAL PRIMARY KEY,
  icd10_prefix      VARCHAR(10)  NOT NULL UNIQUE,
  name              VARCHAR(150) NOT NULL,
  dietary_tier_id   BIGINT REFERENCES dietary_tier(id),
  max_sodium_mg     INTEGER,
  max_sugar_g       NUMERIC(6,2),
  max_potassium_mg  INTEGER,
  max_phosphorus_mg INTEGER,
  max_sat_fat_g     NUMERIC(6,2),
  max_carbs_g       NUMERIC(6,2),
  source_note       TEXT
);

CREATE TABLE client (
  id                    BIGSERIAL PRIMARY KEY,
  client_code           VARCHAR(30)  NOT NULL UNIQUE,
  first_name            VARCHAR(80)  NOT NULL,
  last_name             VARCHAR(80)  NOT NULL,
  phone                 VARCHAR(30),
  household_size        INTEGER      NOT NULL CHECK (household_size > 0),
  enrollment_status     VARCHAR(20)  NOT NULL CHECK (enrollment_status IN ('ACTIVE','EXPIRED','PENDING')),
  enrollment_expires_on DATE,
  created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- PHI columns hold AES-GCM ciphertext (base64) written by EncryptedStringConverter.
CREATE TABLE client_medical_profile (
  id               BIGSERIAL PRIMARY KEY,
  client_id        BIGINT NOT NULL UNIQUE REFERENCES client(id),
  icd10_codes_enc  TEXT,
  allergies_enc    TEXT,
  notes_enc        TEXT,
  dietary_tier_id  BIGINT REFERENCES dietary_tier(id),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE biometric_reading (
  id            BIGSERIAL PRIMARY KEY,
  client_id     BIGINT      NOT NULL REFERENCES client(id),
  metric        VARCHAR(30) NOT NULL,           -- e.g. SYSTOLIC_BP, A1C, WEIGHT_LB
  reading_value NUMERIC(8,2) NOT NULL,
  recorded_on   DATE        NOT NULL
);
CREATE INDEX idx_biometric_client ON biometric_reading(client_id, recorded_on);

CREATE TABLE product (
  id                  BIGSERIAL PRIMARY KEY,
  sku                 VARCHAR(40)  NOT NULL UNIQUE,
  barcode             VARCHAR(40)  UNIQUE,
  name                VARCHAR(150) NOT NULL,
  category            VARCHAR(40)  NOT NULL,    -- PRODUCE, PROTEIN, DAIRY, GRAINS, CANNED, BAKERY, ...
  brand               VARCHAR(80),
  serving_desc        VARCHAR(80),
  serving_g           NUMERIC(8,2),
  calories            INTEGER,
  protein_g           NUMERIC(6,2),
  carbs_g             NUMERIC(6,2),
  sugar_g             NUMERIC(6,2),
  fiber_g             NUMERIC(6,2),
  sat_fat_g           NUMERIC(6,2),
  sodium_mg           INTEGER,
  potassium_mg        INTEGER,
  phosphorus_mg       INTEGER,
  allergens           VARCHAR(200),             -- comma list: PEANUT,TREE_NUT,MILK,EGG,WHEAT,SOY,FISH,SHELLFISH,SESAME
  ingredients         TEXT,
  weight_lb           NUMERIC(8,2) NOT NULL DEFAULT 1,
  min_floor_threshold INTEGER      NOT NULL DEFAULT 0,
  active              BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE bin_location (
  id       BIGSERIAL PRIMARY KEY,
  zone     VARCHAR(30) NOT NULL,                -- PRODUCE, COLD, DRY, PROTEIN, STOCKROOM
  aisle    VARCHAR(2)  NOT NULL,
  shelf    INTEGER     NOT NULL,
  bin      INTEGER     NOT NULL,
  is_floor BOOLEAN     NOT NULL,                -- false = back stockroom
  UNIQUE (aisle, shelf, bin, is_floor)
);

-- Stock lives on lots. FIFO order = expires_at ASC, received_at ASC. Lots with expires_at <= today+7 are flagged for restock/rescue.
CREATE TABLE inventory_lot (
  id                 BIGSERIAL PRIMARY KEY,
  product_id         BIGINT NOT NULL REFERENCES product(id),
  location_id        BIGINT NOT NULL REFERENCES bin_location(id),
  quantity_remaining INTEGER NOT NULL CHECK (quantity_remaining >= 0),
  received_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at         DATE,
  funding_stream     VARCHAR(80)
);
CREATE INDEX idx_lot_fifo ON inventory_lot(product_id, expires_at, received_at);
CREATE INDEX idx_lot_expiry ON inventory_lot(expires_at);

-- Self-healing queue: WAITING sessions hold a queue_position; ACTIVE sessions heartbeat via last_activity_at
-- (updated on every scan). Active_Floor_Shoppers is computed, never stored:
--   count(status='ACTIVE' AND last_activity_at > now() - interval '45 minutes')
CREATE TABLE shopping_session (
  id                BIGSERIAL PRIMARY KEY,
  session_token     VARCHAR(64) NOT NULL UNIQUE,
  client_id         BIGINT NOT NULL REFERENCES client(id),
  status            VARCHAR(20) NOT NULL CHECK (status IN ('WAITING','ACTIVE','COMPLETED','EXPIRED')),
  queue_position    INTEGER,
  tier_code         VARCHAR(30),
  household_size    INTEGER NOT NULL,
  ruleset_snapshot  TEXT,        -- JSON: strictest ceilings + allergen union, fixed for the visit
  ruleset_hash      VARCHAR(64),
  restricted_skus   TEXT,        -- the session's Restricted (Red) SKU array, computed at activation
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  activated_at      TIMESTAMPTZ,
  last_activity_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at          TIMESTAMPTZ
);
CREATE INDEX idx_session_active ON shopping_session(status, last_activity_at);
CREATE INDEX idx_session_queue  ON shopping_session(status, queue_position);

CREATE TABLE cart_item (
  id          BIGSERIAL PRIMARY KEY,
  session_id  BIGINT NOT NULL REFERENCES shopping_session(id),
  product_id  BIGINT NOT NULL REFERENCES product(id),
  quantity    INTEGER NOT NULL CHECK (quantity > 0),
  flag        VARCHAR(10) NOT NULL CHECK (flag IN ('GREEN','YELLOW','RED')),
  added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id, product_id)
);

CREATE TABLE checkout_log (
  id                 BIGSERIAL PRIMARY KEY,
  session_id         BIGINT NOT NULL UNIQUE REFERENCES shopping_session(id),
  volunteer_username VARCHAR(80) NOT NULL,
  item_count         INTEGER NOT NULL,
  total_weight_lb    NUMERIC(10,2) NOT NULL,
  receipt_json       TEXT,
  completed_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per lot drawn at checkout: powers FIFO audit and grant attribution (Epic 5).
CREATE TABLE checkout_line (
  id             BIGSERIAL PRIMARY KEY,
  checkout_id    BIGINT NOT NULL REFERENCES checkout_log(id),
  product_id     BIGINT NOT NULL REFERENCES product(id),
  lot_id         BIGINT NOT NULL REFERENCES inventory_lot(id),
  quantity       INTEGER NOT NULL,
  weight_lb      NUMERIC(10,2) NOT NULL,
  funding_stream VARCHAR(80)
);

-- Audit trail for the hard stop: every Red item is either REMOVED from the manifest or OVERRIDDEN (with reason).
CREATE TABLE checkout_override (
  id                 BIGSERIAL PRIMARY KEY,
  session_id         BIGINT NOT NULL REFERENCES shopping_session(id),
  product_id         BIGINT NOT NULL REFERENCES product(id),
  action             VARCHAR(12) NOT NULL CHECK (action IN ('REMOVED','OVERRIDDEN')),
  volunteer_username VARCHAR(80) NOT NULL,
  reason             TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_setting (
  setting_key   VARCHAR(60) PRIMARY KEY,
  setting_value VARCHAR(200) NOT NULL
);
INSERT INTO app_setting (setting_key, setting_value) VALUES ('max_floor_capacity', '10'), ('session_timeout_minutes', '45');
