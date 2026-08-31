-- =====================================================================
-- V3: Contracts, contract versioning, and the Commercial Agreement Engine
--     SRS §10, §11, §12, §60
--
-- GOVERNING RULE (SRS §60): "No hard-coded business rule should be
-- introduced where the business requirement may vary by project, shaft
-- or contract."  Consequently there is NO percentage anywhere in the Java
-- source.  Every split, deduction, fee and recovery is a row in
-- agreement_rules, effective-dated and version-controlled.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Contract types are configurable (SRS §41) — not an enum.
-- ---------------------------------------------------------------------
CREATE TABLE contract_types (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(40)  NOT NULL UNIQUE,
    name          VARCHAR(120) NOT NULL,
    description   TEXT,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 100
);

-- ---------------------------------------------------------------------
-- Contracts (SRS §10).  A shaft may have several over its lifecycle;
-- exactly one may be ACTIVE at any instant (enforced by partial index).
-- ---------------------------------------------------------------------
CREATE TABLE contracts (
    id                  BIGSERIAL PRIMARY KEY,
    contract_number     VARCHAR(50)  NOT NULL UNIQUE,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    project_id          BIGINT       NOT NULL REFERENCES projects(id),
    mining_operation_id BIGINT       REFERENCES mining_operations(id),
    shaft_id            BIGINT       REFERENCES shafts(id),
    partner_id          BIGINT       NOT NULL REFERENCES partners(id),
    contract_type_id    BIGINT       REFERENCES contract_types(id),
    title               VARCHAR(200),
    effective_date      DATE         NOT NULL,
    expiry_date         DATE,
    renewal_date        DATE,
    signed_date         DATE,
    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | PENDING_APPROVAL | APPROVED | ACTIVE | EXPIRED | TERMINATED | SUPERSEDED
    current_version     INT          NOT NULL DEFAULT 1,
    settlement_currency CHAR(3)      NOT NULL DEFAULT 'USD',
    settlement_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY', -- WEEKLY | FORTNIGHTLY | MONTHLY | PER_SALE
    special_conditions  TEXT,
    termination_notes   TEXT,
    approved_by         VARCHAR(160),
    approved_at         TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160),
    CONSTRAINT ck_contract_dates CHECK (expiry_date IS NULL OR expiry_date >= effective_date)
);
CREATE INDEX idx_contracts_shaft   ON contracts (shaft_id)   WHERE deleted_at IS NULL;
CREATE INDEX idx_contracts_partner ON contracts (partner_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_contracts_status  ON contracts (status)     WHERE deleted_at IS NULL;
CREATE INDEX idx_contracts_expiry  ON contracts (expiry_date) WHERE deleted_at IS NULL AND status = 'ACTIVE';

-- One live contract per shaft. Superseding a contract means moving the old
-- one to SUPERSEDED first — which is exactly the audit trail we want.
CREATE UNIQUE INDEX uq_contract_active_per_shaft
    ON contracts (shaft_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL AND shaft_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Contract versions (SRS §10 "The system must support contract
-- versioning").  An amendment creates a new version row and a new
-- commercial_agreement; the previous pair stays intact so a historical
-- settlement can always be recomputed exactly as it was.
-- ---------------------------------------------------------------------
CREATE TABLE contract_versions (
    id             BIGSERIAL PRIMARY KEY,
    contract_id    BIGINT      NOT NULL REFERENCES contracts(id),
    version_number INT         NOT NULL,
    effective_from DATE        NOT NULL,
    effective_to   DATE,
    change_reason  TEXT        NOT NULL,
    change_summary TEXT,
    status         VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | PENDING_APPROVAL | ACTIVE | SUPERSEDED
    approved_by    VARCHAR(160),
    approved_at    TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(160),
    updated_at     TIMESTAMP,
    updated_by     VARCHAR(160),
    CONSTRAINT uq_contract_version UNIQUE (contract_id, version_number)
);
CREATE INDEX idx_contract_versions_period ON contract_versions (contract_id, effective_from, effective_to);

-- ---------------------------------------------------------------------
-- Commercial agreements (SRS §11).  The header: what basis the split is
-- computed on and what defaults apply when no specific rule matches.
-- ---------------------------------------------------------------------
CREATE TABLE commercial_agreements (
    id                     BIGSERIAL PRIMARY KEY,
    contract_id            BIGINT       NOT NULL REFERENCES contracts(id),
    contract_version_id    BIGINT       REFERENCES contract_versions(id),
    name                   VARCHAR(200) NOT NULL,
    description            TEXT,
    effective_from         DATE         NOT NULL,
    effective_to           DATE,
    status                 VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | PENDING_APPROVAL | ACTIVE | SUPERSEDED | TERMINATED

    -- What the contractual split is applied to.  NET_REVENUE is the SRS §12
    -- worked example (gross − deductions → allocate).  GROSS_REVENUE splits
    -- first and lets each side carry its own costs.  PRODUCTION splits the
    -- metal/ore itself before it is ever sold.
    settlement_basis       VARCHAR(30)  NOT NULL DEFAULT 'NET_REVENUE',
    -- NET_REVENUE | GROSS_REVENUE | PRODUCTION | PROFIT

    -- Fallback split used when no rule of a given type exists.  Held here
    -- rather than in code so an agreement is still complete and auditable
    -- with a single row of configuration.
    default_saicomex_percent NUMERIC(9,6),
    default_partner_percent  NUMERIC(9,6),

    currency               CHAR(3)      NOT NULL DEFAULT 'USD',
    -- Rounding applied to each computed allocation line.
    rounding_scale         SMALLINT     NOT NULL DEFAULT 2,
    rounding_mode          VARCHAR(20)  NOT NULL DEFAULT 'HALF_UP',

    notes                  TEXT,
    approved_by            VARCHAR(160),
    approved_at            TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by             VARCHAR(160),
    updated_at             TIMESTAMP,
    updated_by             VARCHAR(160),
    deleted_at             TIMESTAMP,
    deleted_by             VARCHAR(160),
    CONSTRAINT ck_agreement_default_split CHECK (
        default_saicomex_percent IS NULL
     OR default_partner_percent  IS NULL
     OR ROUND(default_saicomex_percent + default_partner_percent, 6) = 100
    ),
    CONSTRAINT ck_agreement_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_agreements_contract ON commercial_agreements (contract_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_agreements_period   ON commercial_agreements (effective_from, effective_to);

CREATE UNIQUE INDEX uq_agreement_active_per_contract
    ON commercial_agreements (contract_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- Agreement rules (SRS §11) — the configurable parameters.
--
-- rule_type is a VARCHAR against a lookup table, NOT a database enum, so
-- SAIComex can add an agreement parameter later "without requiring major
-- application redevelopment" (SRS §11 closing line).
-- ---------------------------------------------------------------------
CREATE TABLE agreement_rule_types (
    code          VARCHAR(40)  PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    description   TEXT,
    -- Where the rule acts in the SRS §12 waterfall:
    --   DEDUCTION  — reduces gross before the split
    --   ALLOCATION — divides the distributable amount
    --   ADJUSTMENT — applied to a party's share after allocation
    stage         VARCHAR(20)  NOT NULL,
    default_sequence INT       NOT NULL DEFAULT 100,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE agreement_rules (
    id                  BIGSERIAL PRIMARY KEY,
    agreement_id        BIGINT       NOT NULL REFERENCES commercial_agreements(id) ON DELETE CASCADE,
    rule_type           VARCHAR(40)  NOT NULL REFERENCES agreement_rule_types(code),
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    -- Order of application within the waterfall. Lower runs first.
    sequence_no         INT          NOT NULL DEFAULT 100,

    -- Scope: ALL, or narrowed to one expense category / product / cost type.
    scope               VARCHAR(30)  NOT NULL DEFAULT 'ALL',
    -- ALL | EXPENSE_CATEGORY | PRODUCT | COST_TYPE
    expense_category_id BIGINT,      -- FK added in V4 once the table exists
    scope_value         VARCHAR(80),

    calculation_method  VARCHAR(30)  NOT NULL DEFAULT 'PERCENTAGE',
    -- PERCENTAGE | FIXED_AMOUNT | RATE_PER_UNIT | TIERED | FULL_AMOUNT

    saicomex_percent    NUMERIC(9,6),
    partner_percent     NUMERIC(9,6),
    fixed_amount        NUMERIC(18,4),
    rate_amount         NUMERIC(18,6),
    rate_unit           VARCHAR(20),          -- per gram / tonne / litre / day
    currency            CHAR(3),

    -- Who carries this cost when the rule is a cost-share rule.
    borne_by            VARCHAR(20)  NOT NULL DEFAULT 'SHARED',
    -- SAICOMEX | PARTNER | SHARED

    -- Deduction rules only: taken off the gross before allocation.
    deduct_before_split BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Guard rails (SRS §11 "Minimum payment", "Special deductions").
    min_amount          NUMERIC(18,4),
    max_amount          NUMERIC(18,4),
    cap_percent         NUMERIC(9,6),

    -- Capital recovery: recover `fixed_amount` at `rate_amount` per period
    -- until recovered_to_date reaches the total.
    recoverable_total   NUMERIC(18,4),
    recovered_to_date   NUMERIC(18,4) NOT NULL DEFAULT 0,

    effective_from      DATE,
    effective_to        DATE,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),

    CONSTRAINT ck_rule_percent_split CHECK (
        calculation_method <> 'PERCENTAGE'
     OR saicomex_percent IS NULL
     OR partner_percent  IS NULL
     OR ROUND(saicomex_percent + partner_percent, 6) = 100
    ),
    CONSTRAINT ck_rule_percent_range CHECK (
        (saicomex_percent IS NULL OR (saicomex_percent >= 0 AND saicomex_percent <= 100))
    AND (partner_percent  IS NULL OR (partner_percent  >= 0 AND partner_percent  <= 100))
    )
);
CREATE INDEX idx_agreement_rules_agreement ON agreement_rules (agreement_id, sequence_no);
CREATE INDEX idx_agreement_rules_type      ON agreement_rules (rule_type);

-- Tiered arrangements: "first 500g at 70/30, above that 60/40".
CREATE TABLE agreement_rule_tiers (
    id               BIGSERIAL PRIMARY KEY,
    rule_id          BIGINT        NOT NULL REFERENCES agreement_rules(id) ON DELETE CASCADE,
    tier_no          INT           NOT NULL,
    from_value       NUMERIC(18,4) NOT NULL,
    to_value         NUMERIC(18,4),           -- NULL = open-ended top tier
    saicomex_percent NUMERIC(9,6),
    partner_percent  NUMERIC(9,6),
    fixed_amount     NUMERIC(18,4),
    rate_amount      NUMERIC(18,6),
    CONSTRAINT uq_rule_tier UNIQUE (rule_id, tier_no),
    CONSTRAINT ck_tier_bounds CHECK (to_value IS NULL OR to_value > from_value)
);

-- ---------------------------------------------------------------------
-- Approval history for contracts and agreements (SRS §10 "Approval
-- history", §39).  Generic so expense/payment approvals reuse it (V6).
-- ---------------------------------------------------------------------
CREATE TABLE approvals (
    id             BIGSERIAL PRIMARY KEY,
    entity_type    VARCHAR(40)  NOT NULL,   -- CONTRACT | AGREEMENT | EXPENSE | PAYMENT | PRODUCTION | SETTLEMENT
    entity_id      BIGINT       NOT NULL,
    step_no        INT          NOT NULL DEFAULT 1,
    step_name      VARCHAR(80),
    required_role  VARCHAR(40),
    action         VARCHAR(20)  NOT NULL,   -- SUBMITTED | APPROVED | REJECTED | RETURNED | CANCELLED
    actor_email    VARCHAR(160) NOT NULL,
    actor_role     VARCHAR(40),
    comments       TEXT,
    acted_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_approvals_entity ON approvals (entity_type, entity_id, acted_at DESC);
