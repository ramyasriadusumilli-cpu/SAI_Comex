-- =====================================================================
-- V2: the operating hierarchy — Project → Mining Operation → Shaft
--     plus Partners / Shaft Owners and shared location records.
--     SRS §3, §6, §7, §8, §9, §34
-- =====================================================================

-- ---------------------------------------------------------------------
-- Locations & GPS (SRS §34).  Shared by projects, operations, shafts,
-- stores and equipment so "show me the portfolio on a map" is one query.
-- ---------------------------------------------------------------------
CREATE TABLE locations (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(160) NOT NULL,
    location_type VARCHAR(40)  NOT NULL,   -- PROJECT | OPERATION | SHAFT | STORE | OFFICE | PLANT
    address       TEXT,
    region        VARCHAR(120),
    country       VARCHAR(80),
    latitude      NUMERIC(10,7),
    longitude     NUMERIC(10,7),
    -- GeoJSON polygon of the site boundary. Kept as text rather than PostGIS
    -- so the platform runs on stock postgres:16-alpine like the fleet stack.
    boundary_geojson TEXT,
    notes         TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(160),
    updated_at    TIMESTAMP,
    updated_by    VARCHAR(160)
);
CREATE INDEX idx_locations_type ON locations (location_type);

-- ---------------------------------------------------------------------
-- Projects (SRS §6)
-- ---------------------------------------------------------------------
CREATE TABLE projects (
    id                     BIGSERIAL PRIMARY KEY,
    company_id             BIGINT       NOT NULL REFERENCES companies(id),
    code                   VARCHAR(30)  NOT NULL UNIQUE,
    name                   VARCHAR(200) NOT NULL,
    project_type           VARCHAR(60),          -- Gold / Chrome / Alluvial / Exploration …
    description            TEXT,
    location_id            BIGINT       REFERENCES locations(id),
    location_name          VARCHAR(200),         -- denormalised for list screens
    latitude               NUMERIC(10,7),
    longitude              NUMERIC(10,7),
    boundary_geojson       TEXT,
    project_manager_id     BIGINT       REFERENCES users(id),
    start_date             DATE,
    planned_completion_date DATE,
    actual_completion_date  DATE,
    status                 VARCHAR(30)  NOT NULL DEFAULT 'PROPOSED',
    -- PROPOSED | PLANNING | PROSPECTING | DEVELOPMENT | ACTIVE | SUSPENDED | COMPLETED | CLOSED
    budget_amount          NUMERIC(18,4),
    budget_currency        CHAR(3),
    licence_number         VARCHAR(80),
    licence_expiry_date    DATE,
    permit_number          VARCHAR(80),
    permit_expiry_date     DATE,
    notes                  TEXT,
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by             VARCHAR(160),
    updated_at             TIMESTAMP,
    updated_by             VARCHAR(160),
    deleted_at             TIMESTAMP,
    deleted_by             VARCHAR(160)
);
CREATE INDEX idx_projects_company ON projects (company_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_projects_status  ON projects (status)     WHERE deleted_at IS NULL;

ALTER TABLE user_project_access
    ADD CONSTRAINT fk_upa_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------
-- Mining operations (SRS §7).  A project may have many; a shaft may hang
-- directly off a project (Project B in the SRS tree has no operation
-- layer) which is why shafts.mining_operation_id is nullable.
-- ---------------------------------------------------------------------
CREATE TABLE mining_operations (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES projects(id),
    code            VARCHAR(30)  NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    operation_type  VARCHAR(60)  NOT NULL,  -- SHAFT_MINING | ALLUVIAL | RIVER | PROCESSING | MILLING | OTHER
    description     TEXT,
    location_id     BIGINT       REFERENCES locations(id),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    manager_id      BIGINT       REFERENCES users(id),
    start_date      DATE,
    end_date        DATE,
    status          VARCHAR(30)  NOT NULL DEFAULT 'PROPOSED',
    -- PROPOSED | DEVELOPMENT | ACTIVE | SUSPENDED | CLOSED
    budget_amount   NUMERIC(18,4),
    budget_currency CHAR(3),
    notes           TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(160),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(160),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(160)
);
CREATE INDEX idx_operations_project ON mining_operations (project_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_operations_status  ON mining_operations (status)     WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- Partners / shaft owners (SRS §9).  Central database — one partner may
-- own or participate in many shafts across many projects.
-- ---------------------------------------------------------------------
CREATE TABLE partners (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    code                VARCHAR(30)  NOT NULL UNIQUE,
    legal_name          VARCHAR(200) NOT NULL,
    trading_name        VARCHAR(200),
    partner_type        VARCHAR(30)  NOT NULL DEFAULT 'COMPANY', -- COMPANY | INDIVIDUAL | COOPERATIVE | JV
    registration_number VARCHAR(60),
    tax_number          VARCHAR(60),
    id_number           VARCHAR(60),
    contact_person      VARCHAR(160),
    phone               VARCHAR(40),
    alternate_phone     VARCHAR(40),
    email               VARCHAR(160),
    address             TEXT,
    city                VARCHAR(120),
    country             VARCHAR(80),
    -- Banking is restricted data: only roles holding 'partners.banking' see
    -- these columns; the DTO layer strips them for everyone else.
    bank_name           VARCHAR(160),
    bank_branch         VARCHAR(120),
    bank_account_name   VARCHAR(160),
    bank_account_number VARCHAR(60),
    bank_swift          VARCHAR(30),
    payment_currency    CHAR(3),
    payment_method      VARCHAR(40),          -- EFT | CASH | MOBILE | CHEQUE
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | INACTIVE | BLACKLISTED
    onboarded_date      DATE,
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160)
);
CREATE INDEX idx_partners_status ON partners (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_partners_name   ON partners (LOWER(legal_name));

-- ---------------------------------------------------------------------
-- Shafts (SRS §8) — the primary operational entity.  Every shaft carries
-- its own financial and operational account.
-- ---------------------------------------------------------------------
CREATE TABLE shafts (
    id                    BIGSERIAL PRIMARY KEY,
    project_id            BIGINT       NOT NULL REFERENCES projects(id),
    mining_operation_id   BIGINT       REFERENCES mining_operations(id),
    code                  VARCHAR(30)  NOT NULL UNIQUE,
    name                  VARCHAR(200) NOT NULL,
    shaft_number          VARCHAR(40),
    description           TEXT,
    location_id           BIGINT       REFERENCES locations(id),
    latitude              NUMERIC(10,7),
    longitude             NUMERIC(10,7),
    -- Current owner is denormalised from the active contract for fast list
    -- screens; the contract remains the authority (see contracts.partner_id).
    owner_partner_id      BIGINT       REFERENCES partners(id),
    shaft_manager_id      BIGINT       REFERENCES users(id),
    depth_metres          NUMERIC(10,2),
    commissioned_date     DATE,
    start_date            DATE,
    closure_date          DATE,
    status                VARCHAR(30)  NOT NULL DEFAULT 'PROPOSED',
    -- PROPOSED | CONTRACT_PENDING | CONTRACTED | MOBILISATION | DEVELOPMENT
    -- | ACTIVE | TEMPORARILY_STOPPED | SUSPENDED | CLOSED
    production_target      NUMERIC(18,4),
    production_target_unit VARCHAR(20),
    production_target_period VARCHAR(20),        -- DAILY | WEEKLY | MONTHLY
    notes                 TEXT,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(160),
    updated_at            TIMESTAMP,
    updated_by            VARCHAR(160),
    deleted_at            TIMESTAMP,
    deleted_by            VARCHAR(160)
);
CREATE INDEX idx_shafts_project   ON shafts (project_id)          WHERE deleted_at IS NULL;
CREATE INDEX idx_shafts_operation ON shafts (mining_operation_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_shafts_owner     ON shafts (owner_partner_id)    WHERE deleted_at IS NULL;
CREATE INDEX idx_shafts_status    ON shafts (status)              WHERE deleted_at IS NULL;

ALTER TABLE user_shaft_access
    ADD CONSTRAINT fk_usa_shaft FOREIGN KEY (shaft_id) REFERENCES shafts(id) ON DELETE CASCADE;

-- A shaft's operation, when set, must belong to the shaft's project.
-- Enforced by trigger because a composite FK would force a redundant
-- (id, project_id) unique key on mining_operations.
CREATE OR REPLACE FUNCTION check_shaft_operation_project() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.mining_operation_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM mining_operations o
            WHERE o.id = NEW.mining_operation_id
              AND o.project_id = NEW.project_id
        ) THEN
            RAISE EXCEPTION
              'Mining operation % does not belong to project % (shaft %)',
              NEW.mining_operation_id, NEW.project_id, NEW.code;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_shaft_operation_project
    BEFORE INSERT OR UPDATE ON shafts
    FOR EACH ROW EXECUTE FUNCTION check_shaft_operation_project();
