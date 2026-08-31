-- =====================================================================
-- SAIComex Mining Platform — V1: company, currency, identity & access
-- =====================================================================
-- CONVENTION (differs deliberately from the fleet schema):
--   every table has a plain surrogate key named `id`.  The fleet schema's
--   `vehicles.vehicle_id` PK is a permanent footgun there — every migration
--   has to remember `REFERENCES vehicles(vehicle_id)`.  Here: always `id`.
--
--   Money is always stored as three columns:
--     <x>_amount     NUMERIC(18,4)  -- in the transaction's own currency
--     <x>_currency   CHAR(3)
--     <x>_base_amount NUMERIC(18,4) -- converted to the group reporting currency
--   plus the exchange_rate actually used, so a historical figure never moves
--   when today's rate changes (SRS §40).
--
--   Soft delete everywhere that carries financial or audit weight (SRS §39):
--   deleted_at / deleted_by, never a physical DELETE.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Group company (top of the hierarchy — SRS §3)
-- ---------------------------------------------------------------------
CREATE TABLE companies (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(20)  NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    trading_name        VARCHAR(200),
    registration_number VARCHAR(60),
    tax_number          VARCHAR(60),
    address             TEXT,
    country             VARCHAR(80),
    phone               VARCHAR(40),
    email               VARCHAR(160),
    website             VARCHAR(200),
    logo_url            VARCHAR(500),
    reporting_currency  CHAR(3)      NOT NULL DEFAULT 'USD',
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160)
);

-- ---------------------------------------------------------------------
-- Currencies + rates (SRS §40)
-- ---------------------------------------------------------------------
CREATE TABLE currencies (
    code            CHAR(3)      PRIMARY KEY,
    name            VARCHAR(80)  NOT NULL,
    symbol          VARCHAR(8),
    decimal_places  SMALLINT     NOT NULL DEFAULT 2,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order   INT          NOT NULL DEFAULT 100
);

CREATE TABLE exchange_rates (
    id              BIGSERIAL PRIMARY KEY,
    from_currency   CHAR(3)        NOT NULL REFERENCES currencies(code),
    to_currency     CHAR(3)        NOT NULL REFERENCES currencies(code),
    rate            NUMERIC(18,8)  NOT NULL CHECK (rate > 0),
    effective_date  DATE           NOT NULL,
    source          VARCHAR(60),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(160),
    CONSTRAINT uq_rate_pair_date UNIQUE (from_currency, to_currency, effective_date)
);
CREATE INDEX idx_exchange_rates_lookup ON exchange_rates (from_currency, to_currency, effective_date DESC);

-- ---------------------------------------------------------------------
-- Roles & permissions (SRS §37 — the model must be configurable, so
-- permissions live in tables, not in a Java switch statement)
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(40)  NOT NULL UNIQUE,
    name          VARCHAR(80)  NOT NULL,
    description   TEXT,
    is_system     BOOLEAN      NOT NULL DEFAULT FALSE,  -- system roles cannot be deleted
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 100,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(160),
    updated_at    TIMESTAMP,
    updated_by    VARCHAR(160)
);

CREATE TABLE permissions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(80)  NOT NULL UNIQUE,   -- e.g. 'shafts.edit', 'settlements.approve'
    module      VARCHAR(40)  NOT NULL,          -- e.g. 'shafts'  (drives nav visibility)
    action      VARCHAR(40)  NOT NULL,          -- view | create | edit | delete | approve | export
    description TEXT
);
CREATE INDEX idx_permissions_module ON permissions (module);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ---------------------------------------------------------------------
-- Users (SRS §36)
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    company_id            BIGINT       REFERENCES companies(id),
    email                 VARCHAR(160) NOT NULL UNIQUE,
    password_hash         VARCHAR(200) NOT NULL,
    first_name            VARCHAR(80)  NOT NULL,
    last_name             VARCHAR(80)  NOT NULL,
    phone                 VARCHAR(40),
    job_title             VARCHAR(120),
    department            VARCHAR(120),
    role_id               BIGINT       NOT NULL REFERENCES roles(id),
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | SUSPENDED | PENDING | DISABLED
    -- MFA (SRS §38). Secret is only populated once the user enrols.
    mfa_enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_secret            VARCHAR(120),
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    password_changed_at   TIMESTAMP,
    failed_login_count    INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP,
    last_login_at         TIMESTAMP,
    last_login_ip         VARCHAR(60),
    reset_token           VARCHAR(120),
    reset_token_expires   TIMESTAMP,
    preferred_currency    CHAR(3),
    avatar_url            VARCHAR(500),
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(160),
    updated_at            TIMESTAMP,
    updated_by            VARCHAR(160),
    deleted_at            TIMESTAMP,
    deleted_by            VARCHAR(160)
);
CREATE INDEX idx_users_role   ON users (role_id);
CREATE INDEX idx_users_status ON users (status) WHERE deleted_at IS NULL;

-- Data scoping: a Project Manager sees assigned projects, a Shaft Manager
-- assigned shafts (SRS §36 "Assigned projects / Assigned shafts").  Empty
-- assignment set = unrestricted, which is how DIRECTOR / EXECUTIVE / AUDITOR
-- get group-wide visibility without 80 rows each.
CREATE TABLE user_project_access (
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, project_id)
);

CREATE TABLE user_shaft_access (
    user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shaft_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, shaft_id)
);

-- Revoked JWTs (logout / forced sign-out).  Rows older than the token TTL
-- are purged by a scheduled job — the table stays small.
CREATE TABLE revoked_tokens (
    jti        VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP   NOT NULL,
    revoked_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    revoked_by VARCHAR(160)
);
CREATE INDEX idx_revoked_tokens_expiry ON revoked_tokens (expires_at);
