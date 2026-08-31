-- =====================================================================
-- V5: Revenue, the financial ledger, partner settlements, payments
--     and budgets.  SRS §23–§27
--
-- The settlement tables are where SRS §12's "audit trail showing how
-- every amount was calculated" actually lives: settlement_calculations
-- stores one row per step of the waterfall, naming the agreement rule
-- that produced it and the value it was applied to.  Nothing in the
-- partner statement is a number without a parent row explaining it.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Buyers (gold buyers / refineries / offtake partners)
-- ---------------------------------------------------------------------
CREATE TABLE buyers (
    id             BIGSERIAL PRIMARY KEY,
    company_id     BIGINT       NOT NULL REFERENCES companies(id),
    code           VARCHAR(30)  NOT NULL UNIQUE,
    name           VARCHAR(200) NOT NULL,
    buyer_type     VARCHAR(40),   -- REFINERY | TRADER | EXPORT | LOCAL
    contact_person VARCHAR(160),
    phone          VARCHAR(40),
    email          VARCHAR(160),
    address        TEXT,
    tax_number     VARCHAR(60),
    default_currency CHAR(3),
    licence_number VARCHAR(80),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    notes          TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(160),
    updated_at     TIMESTAMP,
    updated_by     VARCHAR(160),
    deleted_at     TIMESTAMP,
    deleted_by     VARCHAR(160)
);

-- ---------------------------------------------------------------------
-- Sales / revenue (SRS §23).  Every sale is linked to
-- project + operation + shaft so revenue is attributable.
-- ---------------------------------------------------------------------
CREATE TABLE sales (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    sale_number         VARCHAR(50)  NOT NULL UNIQUE,
    project_id          BIGINT       NOT NULL REFERENCES projects(id),
    mining_operation_id BIGINT       REFERENCES mining_operations(id),
    shaft_id            BIGINT       REFERENCES shafts(id),
    contract_id         BIGINT       REFERENCES contracts(id),
    batch_id            BIGINT       REFERENCES production_batches(id),
    buyer_id            BIGINT       REFERENCES buyers(id),

    sale_date           DATE         NOT NULL,
    product             VARCHAR(80)  NOT NULL DEFAULT 'GOLD',
    quantity            NUMERIC(18,4) NOT NULL,
    unit_code           VARCHAR(20)  NOT NULL REFERENCES production_units(code),
    grade               NUMERIC(12,6),
    assay_reference     VARCHAR(80),
    assay_percent       NUMERIC(9,4),

    unit_price          NUMERIC(18,6) NOT NULL,
    currency            CHAR(3)      NOT NULL,
    exchange_rate       NUMERIC(18,8) NOT NULL DEFAULT 1,
    gross_amount        NUMERIC(18,4) NOT NULL,
    deductions_amount   NUMERIC(18,4) NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(18,4) NOT NULL DEFAULT 0,
    royalty_amount      NUMERIC(18,4) NOT NULL DEFAULT 0,
    net_amount          NUMERIC(18,4) NOT NULL,
    gross_base_amount   NUMERIC(18,4) NOT NULL,
    net_base_amount     NUMERIC(18,4) NOT NULL,

    payment_status      VARCHAR(20)  NOT NULL DEFAULT 'UNPAID', -- UNPAID | PARTIAL | PAID
    payment_date        DATE,
    amount_received     NUMERIC(18,4) NOT NULL DEFAULT 0,

    -- Settlement state: has this revenue already been split with the partner?
    settlement_status   VARCHAR(20)  NOT NULL DEFAULT 'UNSETTLED', -- UNSETTLED | SETTLED
    settlement_id       BIGINT,      -- FK added after settlements table

    invoice_number      VARCHAR(80),
    reference           VARCHAR(120),
    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | CONFIRMED | CANCELLED
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160),
    CONSTRAINT ck_sale_amounts CHECK (quantity >= 0 AND gross_amount >= 0)
);
CREATE INDEX idx_sales_shaft_date   ON sales (shaft_id, sale_date DESC)   WHERE deleted_at IS NULL;
CREATE INDEX idx_sales_project_date ON sales (project_id, sale_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_sales_settlement   ON sales (settlement_status)          WHERE deleted_at IS NULL;

CREATE TABLE sale_deductions (
    id           BIGSERIAL PRIMARY KEY,
    sale_id      BIGINT        NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    deduction_type VARCHAR(60) NOT NULL,   -- REFINING | TRANSPORT | ASSAY | ROYALTY | OTHER
    description  VARCHAR(300),
    amount       NUMERIC(18,4) NOT NULL,
    currency     CHAR(3)
);

-- ---------------------------------------------------------------------
-- Settlements (SRS §25).  One settlement = one partner, one shaft, one
-- period, computed from the contract that was ACTIVE in that period.
-- ---------------------------------------------------------------------
CREATE TABLE settlements (
    id                    BIGSERIAL PRIMARY KEY,
    company_id            BIGINT       NOT NULL REFERENCES companies(id),
    settlement_number     VARCHAR(50)  NOT NULL UNIQUE,
    project_id            BIGINT       NOT NULL REFERENCES projects(id),
    mining_operation_id   BIGINT       REFERENCES mining_operations(id),
    shaft_id              BIGINT       NOT NULL REFERENCES shafts(id),
    partner_id            BIGINT       NOT NULL REFERENCES partners(id),
    contract_id           BIGINT       NOT NULL REFERENCES contracts(id),
    -- Pinned to the exact agreement version used, so re-opening a 2026
    -- statement never re-computes it against a 2027 amendment.
    agreement_id          BIGINT       NOT NULL REFERENCES commercial_agreements(id),
    contract_version_id   BIGINT       REFERENCES contract_versions(id),

    period_start          DATE         NOT NULL,
    period_end            DATE         NOT NULL,
    settlement_date       DATE,

    currency              CHAR(3)      NOT NULL,
    exchange_rate         NUMERIC(18,8) NOT NULL DEFAULT 1,

    -- The SRS §12 waterfall, materialised.
    gross_revenue         NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_deductions      NUMERIC(18,4) NOT NULL DEFAULT 0,
    net_distributable     NUMERIC(18,4) NOT NULL DEFAULT 0,
    saicomex_share        NUMERIC(18,4) NOT NULL DEFAULT 0,
    partner_share         NUMERIC(18,4) NOT NULL DEFAULT 0,
    partner_adjustments   NUMERIC(18,4) NOT NULL DEFAULT 0,
    partner_net_payable   NUMERIC(18,4) NOT NULL DEFAULT 0,
    amount_paid           NUMERIC(18,4) NOT NULL DEFAULT 0,
    amount_retained       NUMERIC(18,4) NOT NULL DEFAULT 0,
    amount_outstanding    NUMERIC(18,4) NOT NULL DEFAULT 0,

    total_production      NUMERIC(18,4) NOT NULL DEFAULT 0,
    production_unit       VARCHAR(20),
    total_expenses        NUMERIC(18,4) NOT NULL DEFAULT 0,

    status                VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | CALCULATED | PENDING_APPROVAL | APPROVED | PARTIALLY_PAID | PAID | CANCELLED
    calculated_at         TIMESTAMP,
    calculated_by         VARCHAR(160),
    -- Hash of the inputs the calculation consumed. Recomputing a settlement
    -- whose inputs changed produces a different hash, which is how a stale
    -- statement is detected instead of quietly disagreeing with the ledger.
    calculation_hash      VARCHAR(64),
    approved_by           VARCHAR(160),
    approved_at           TIMESTAMP,
    notes                 TEXT,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(160),
    updated_at            TIMESTAMP,
    updated_by            VARCHAR(160),
    deleted_at            TIMESTAMP,
    deleted_by            VARCHAR(160),
    CONSTRAINT ck_settlement_period CHECK (period_end >= period_start)
);
CREATE INDEX idx_settlements_shaft   ON settlements (shaft_id, period_end DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_settlements_partner ON settlements (partner_id, period_end DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_settlements_status  ON settlements (status) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_settlement_period
    ON settlements (shaft_id, partner_id, period_start, period_end)
    WHERE deleted_at IS NULL AND status <> 'CANCELLED';

ALTER TABLE sales
    ADD CONSTRAINT fk_sales_settlement FOREIGN KEY (settlement_id) REFERENCES settlements(id);

-- The source rows a settlement consumed — sales in, expenses out.
CREATE TABLE settlement_lines (
    id             BIGSERIAL PRIMARY KEY,
    settlement_id  BIGINT        NOT NULL REFERENCES settlements(id) ON DELETE CASCADE,
    line_type      VARCHAR(30)   NOT NULL,  -- REVENUE | EXPENSE | PRODUCTION | ADJUSTMENT
    source_table   VARCHAR(40)   NOT NULL,  -- sales | expenses | production_records | manual
    source_id      BIGINT,
    line_date      DATE,
    description    VARCHAR(300)  NOT NULL,
    category_code  VARCHAR(40),
    quantity       NUMERIC(18,4),
    unit_code      VARCHAR(20),
    amount         NUMERIC(18,4) NOT NULL,
    currency       CHAR(3),
    base_amount    NUMERIC(18,4) NOT NULL,
    included       BOOLEAN       NOT NULL DEFAULT TRUE,
    exclusion_reason TEXT
);
CREATE INDEX idx_settlement_lines ON settlement_lines (settlement_id, line_type);
CREATE INDEX idx_settlement_lines_source ON settlement_lines (source_table, source_id);

-- ---------------------------------------------------------------------
-- Calculation audit (SRS §12).  One row per step, in execution order.
-- Reading these rows top to bottom IS the explanation of the statement.
-- ---------------------------------------------------------------------
CREATE TABLE settlement_calculations (
    id                BIGSERIAL PRIMARY KEY,
    settlement_id     BIGINT        NOT NULL REFERENCES settlements(id) ON DELETE CASCADE,
    step_no           INT           NOT NULL,
    stage             VARCHAR(20)   NOT NULL,   -- DEDUCTION | ALLOCATION | ADJUSTMENT | TOTAL
    rule_id           BIGINT        REFERENCES agreement_rules(id),
    rule_type         VARCHAR(40),
    rule_name         VARCHAR(200),
    -- Human-readable arithmetic, e.g. "75,000.00 × 70.000000% = 52,500.00"
    expression        TEXT          NOT NULL,
    input_amount      NUMERIC(18,4),
    percent_applied   NUMERIC(9,6),
    rate_applied      NUMERIC(18,6),
    result_amount     NUMERIC(18,4) NOT NULL,
    running_balance   NUMERIC(18,4),
    beneficiary       VARCHAR(20),             -- SAICOMEX | PARTNER | NONE
    currency          CHAR(3),
    notes             TEXT,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_settlement_step UNIQUE (settlement_id, step_no)
);
CREATE INDEX idx_settlement_calcs ON settlement_calculations (settlement_id, step_no);

-- ---------------------------------------------------------------------
-- Payments (SRS §27) — supplier, partner, employee, contractor.
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    payment_number      VARCHAR(50)  NOT NULL UNIQUE,
    payment_type        VARCHAR(30)  NOT NULL,
    -- PARTNER | SUPPLIER | EMPLOYEE | CONTRACTOR | OTHER
    payment_date        DATE         NOT NULL,

    partner_id          BIGINT       REFERENCES partners(id),
    supplier_id         BIGINT       REFERENCES suppliers(id),
    employee_id         BIGINT       REFERENCES employees(id),
    recipient_name      VARCHAR(200) NOT NULL,

    project_id          BIGINT       REFERENCES projects(id),
    mining_operation_id BIGINT       REFERENCES mining_operations(id),
    shaft_id            BIGINT       REFERENCES shafts(id),
    settlement_id       BIGINT       REFERENCES settlements(id),
    expense_id          BIGINT       REFERENCES expenses(id),
    purchase_order_id   BIGINT       REFERENCES purchase_orders(id),
    category_id         BIGINT       REFERENCES expense_categories(id),

    amount              NUMERIC(18,4) NOT NULL,
    currency            CHAR(3)      NOT NULL,
    exchange_rate       NUMERIC(18,8) NOT NULL DEFAULT 1,
    base_amount         NUMERIC(18,4) NOT NULL,

    payment_method      VARCHAR(40)  NOT NULL,   -- EFT | CASH | MOBILE | CHEQUE | TRANSFER
    bank_reference      VARCHAR(120),
    reference           VARCHAR(120),
    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | PENDING_APPROVAL | APPROVED | PAID | REJECTED | CANCELLED
    approved_by         VARCHAR(160),
    approved_at         TIMESTAMP,
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160),
    CONSTRAINT ck_payment_amount CHECK (amount > 0)
);
CREATE INDEX idx_payments_partner    ON payments (partner_id, payment_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_payments_settlement ON payments (settlement_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_payments_shaft      ON payments (shaft_id, payment_date DESC) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- Financial ledger (SRS §24).  Append-only. Every revenue, expense,
-- payment and settlement writes one row here with a pointer back to the
-- record that caused it — this is the table that makes "trace any number
-- to its source" a single query rather than a union of nine tables.
-- ---------------------------------------------------------------------
CREATE TABLE ledger_entries (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    entry_date          DATE         NOT NULL,
    entry_type          VARCHAR(30)  NOT NULL,
    -- REVENUE | EXPENSE | PAYMENT | SETTLEMENT | CAPEX | INVENTORY | ADJUSTMENT
    direction           VARCHAR(10)  NOT NULL,  -- DEBIT | CREDIT
    project_id          BIGINT       REFERENCES projects(id),
    mining_operation_id BIGINT       REFERENCES mining_operations(id),
    shaft_id            BIGINT       REFERENCES shafts(id),
    partner_id          BIGINT       REFERENCES partners(id),
    contract_id         BIGINT       REFERENCES contracts(id),
    category_id         BIGINT       REFERENCES expense_categories(id),
    description         VARCHAR(300) NOT NULL,
    amount              NUMERIC(18,4) NOT NULL,
    currency            CHAR(3)      NOT NULL,
    exchange_rate       NUMERIC(18,8) NOT NULL DEFAULT 1,
    base_amount         NUMERIC(18,4) NOT NULL,
    source_table        VARCHAR(40)  NOT NULL,
    source_id           BIGINT       NOT NULL,
    reversal_of_id      BIGINT       REFERENCES ledger_entries(id),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160)
);
CREATE INDEX idx_ledger_shaft_date   ON ledger_entries (shaft_id, entry_date DESC);
CREATE INDEX idx_ledger_project_date ON ledger_entries (project_id, entry_date DESC);
CREATE INDEX idx_ledger_source       ON ledger_entries (source_table, source_id);
CREATE INDEX idx_ledger_type_date    ON ledger_entries (entry_type, entry_date DESC);

-- ---------------------------------------------------------------------
-- Budgets (SRS §26) — at group, project, operation or shaft level.
-- ---------------------------------------------------------------------
CREATE TABLE budgets (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    name                VARCHAR(200) NOT NULL,
    budget_level        VARCHAR(20)  NOT NULL,  -- GROUP | PROJECT | OPERATION | SHAFT
    project_id          BIGINT       REFERENCES projects(id),
    mining_operation_id BIGINT       REFERENCES mining_operations(id),
    shaft_id            BIGINT       REFERENCES shafts(id),
    fiscal_year         INT          NOT NULL,
    period_type         VARCHAR(20)  NOT NULL DEFAULT 'ANNUAL', -- ANNUAL | QUARTERLY | MONTHLY
    period_start        DATE         NOT NULL,
    period_end          DATE         NOT NULL,
    currency            CHAR(3)      NOT NULL,
    total_amount        NUMERIC(18,4) NOT NULL DEFAULT 0,
    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | PENDING_APPROVAL | APPROVED | ACTIVE | CLOSED
    approved_by         VARCHAR(160),
    approved_at         TIMESTAMP,
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160),
    CONSTRAINT ck_budget_period CHECK (period_end >= period_start)
);
CREATE INDEX idx_budgets_scope ON budgets (budget_level, project_id, shaft_id) WHERE deleted_at IS NULL;

CREATE TABLE budget_lines (
    id            BIGSERIAL PRIMARY KEY,
    budget_id     BIGINT        NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    category_id   BIGINT        NOT NULL REFERENCES expense_categories(id),
    line_no       INT           NOT NULL,
    description   VARCHAR(300),
    budgeted_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    -- Actuals are computed from ledger_entries on read, never stored — a
    -- cached actual is a cached lie the first time an expense is back-dated.
    notes         TEXT,
    CONSTRAINT uq_budget_line UNIQUE (budget_id, line_no)
);
CREATE INDEX idx_budget_lines_category ON budget_lines (category_id);
