-- =====================================================================
-- V4: Operational data — production, expenses, fuel, explosives,
--     inventory, equipment, maintenance, employees.
--     SRS §13–§22
--
-- Every operational row carries the full hierarchy (project_id,
-- mining_operation_id, shaft_id) rather than only its immediate parent.
-- That is deliberate: SRS §57 makes drill-down and traceability the
-- governing design principle, and a group-level aggregate over eight
-- million rows cannot afford a four-table join to find the project.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Employees / workforce (SRS §18)
-- ---------------------------------------------------------------------
CREATE TABLE employees (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    employee_number     VARCHAR(40)  NOT NULL UNIQUE,
    first_name          VARCHAR(80)  NOT NULL,
    last_name           VARCHAR(80)  NOT NULL,
    id_number           VARCHAR(60),
    phone               VARCHAR(40),
    email               VARCHAR(160),
    job_title           VARCHAR(120),
    employment_type     VARCHAR(30),   -- PERMANENT | CONTRACT | CASUAL | SUBCONTRACTOR
    project_id          BIGINT       REFERENCES projects(id),
    mining_operation_id BIGINT       REFERENCES mining_operations(id),
    shaft_id            BIGINT       REFERENCES shafts(id),
    partner_id          BIGINT       REFERENCES partners(id),  -- partner-supplied labour
    user_id             BIGINT       REFERENCES users(id),     -- if they also log in
    start_date          DATE,
    end_date            DATE,
    daily_rate          NUMERIC(18,4),
    rate_currency       CHAR(3),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160)
);
CREATE INDEX idx_employees_shaft ON employees (shaft_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- Suppliers (SRS §19)
-- ---------------------------------------------------------------------
CREATE TABLE suppliers (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    code                VARCHAR(30)  NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    supplier_type       VARCHAR(60),   -- FUEL | EXPLOSIVES | SPARES | SERVICES | GENERAL
    contact_person      VARCHAR(160),
    phone               VARCHAR(40),
    email               VARCHAR(160),
    address             TEXT,
    tax_number          VARCHAR(60),
    payment_terms       VARCHAR(80),
    default_currency    CHAR(3),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160)
);

-- ---------------------------------------------------------------------
-- Production units are configurable (SRS §13: grams / kg / tonnes / oz)
-- ---------------------------------------------------------------------
CREATE TABLE production_units (
    code            VARCHAR(20)  PRIMARY KEY,
    name            VARCHAR(80)  NOT NULL,
    unit_class      VARCHAR(20)  NOT NULL,   -- MASS | VOLUME | COUNT
    -- Factor to the class base unit (MASS base = gram), so 850 g and
    -- 1.05 kg can be summed on one dashboard tile without guesswork.
    base_factor     NUMERIC(18,8) NOT NULL DEFAULT 1,
    decimal_places  SMALLINT     NOT NULL DEFAULT 2,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order   INT          NOT NULL DEFAULT 100
);

-- ---------------------------------------------------------------------
-- Production (SRS §13, §14).  Recorded at the lowest practical level:
-- date + project + operation + shaft + production.
-- ---------------------------------------------------------------------
CREATE TABLE production_records (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    project_id          BIGINT       NOT NULL REFERENCES projects(id),
    mining_operation_id BIGINT       REFERENCES mining_operations(id),
    shaft_id            BIGINT       NOT NULL REFERENCES shafts(id),
    contract_id         BIGINT       REFERENCES contracts(id),

    production_date     DATE         NOT NULL,
    shift               VARCHAR(20),            -- DAY | NIGHT | MORNING | AFTERNOON
    period_type         VARCHAR(20)  NOT NULL DEFAULT 'DAILY', -- DAILY | SHIFT | WEEKLY | MONTHLY

    ore_tonnes          NUMERIC(18,4),
    grade               NUMERIC(12,6),          -- g/t
    recovery_percent    NUMERIC(9,4),
    gold_recovered      NUMERIC(18,4),
    quantity            NUMERIC(18,4) NOT NULL, -- headline production figure
    unit_code           VARCHAR(20)   NOT NULL REFERENCES production_units(code),
    processing_output   NUMERIC(18,4),

    target_quantity     NUMERIC(18,4),
    variance_quantity   NUMERIC(18,4),          -- computed on save = quantity − target

    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | SUBMITTED | VERIFIED | APPROVED | REJECTED | CORRECTED
    recorded_by_user_id BIGINT       REFERENCES users(id),
    verified_by_user_id BIGINT       REFERENCES users(id),
    verified_at         TIMESTAMP,
    approved_by_user_id BIGINT       REFERENCES users(id),
    approved_at         TIMESTAMP,

    -- Corrections never overwrite (SRS §14 "Production records must not be
    -- silently deleted").  A correction is a NEW row pointing back here,
    -- and this row moves to CORRECTED.
    corrects_record_id  BIGINT       REFERENCES production_records(id),
    correction_reason   TEXT,

    source              VARCHAR(20)  NOT NULL DEFAULT 'WEB',  -- WEB | MOBILE | IMPORT
    -- Idempotency key for offline mobile sync (SRS §33) — a replayed batch
    -- collides on the unique index instead of double-counting production.
    client_uuid         VARCHAR(64),
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160),
    CONSTRAINT ck_production_qty CHECK (quantity >= 0)
);
CREATE INDEX idx_production_shaft_date ON production_records (shaft_id, production_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_production_project_date ON production_records (project_id, production_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_production_status ON production_records (status) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_production_client_uuid ON production_records (client_uuid) WHERE client_uuid IS NOT NULL;

-- Batches group production into a saleable lot (smelt / pour / parcel).
CREATE TABLE production_batches (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    project_id          BIGINT       NOT NULL REFERENCES projects(id),
    shaft_id            BIGINT       REFERENCES shafts(id),
    batch_number        VARCHAR(50)  NOT NULL UNIQUE,
    batch_date          DATE         NOT NULL,
    total_quantity      NUMERIC(18,4) NOT NULL DEFAULT 0,
    unit_code           VARCHAR(20)  NOT NULL REFERENCES production_units(code),
    grade               NUMERIC(12,6),
    assay_reference     VARCHAR(80),
    status              VARCHAR(30)  NOT NULL DEFAULT 'OPEN', -- OPEN | CLOSED | SOLD
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160)
);

CREATE TABLE production_batch_lines (
    batch_id             BIGINT NOT NULL REFERENCES production_batches(id) ON DELETE CASCADE,
    production_record_id BIGINT NOT NULL REFERENCES production_records(id),
    quantity             NUMERIC(18,4) NOT NULL,
    PRIMARY KEY (batch_id, production_record_id)
);

-- ---------------------------------------------------------------------
-- Expense categories (SRS §15) — configurable, hierarchical.
-- ---------------------------------------------------------------------
CREATE TABLE expense_categories (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(40)  NOT NULL UNIQUE,
    name           VARCHAR(120) NOT NULL,
    parent_id      BIGINT       REFERENCES expense_categories(id),
    -- OPEX vs CAPEX matters to the agreement engine: capex_share and
    -- opex_share are separate contractual parameters (SRS §11).
    expense_class  VARCHAR(20)  NOT NULL DEFAULT 'OPEX',  -- OPEX | CAPEX
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order  INT          NOT NULL DEFAULT 100,
    description    TEXT
);
CREATE INDEX idx_expense_categories_parent ON expense_categories (parent_id);

ALTER TABLE agreement_rules
    ADD CONSTRAINT fk_agreement_rule_category
    FOREIGN KEY (expense_category_id) REFERENCES expense_categories(id);

-- ---------------------------------------------------------------------
-- Expenses (SRS §15, §16)
-- ---------------------------------------------------------------------
CREATE TABLE expenses (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    expense_number      VARCHAR(50)  NOT NULL UNIQUE,
    project_id          BIGINT       NOT NULL REFERENCES projects(id),
    mining_operation_id BIGINT       REFERENCES mining_operations(id),
    -- NULL shaft_id = a shared expense; the split lives in expense_allocations.
    shaft_id            BIGINT       REFERENCES shafts(id),
    category_id         BIGINT       NOT NULL REFERENCES expense_categories(id),
    supplier_id         BIGINT       REFERENCES suppliers(id),

    expense_date        DATE         NOT NULL,
    description         TEXT         NOT NULL,
    quantity            NUMERIC(18,4),
    unit                VARCHAR(20),
    unit_cost           NUMERIC(18,6),

    amount              NUMERIC(18,4) NOT NULL,
    currency            CHAR(3)      NOT NULL,
    exchange_rate       NUMERIC(18,8) NOT NULL DEFAULT 1,
    base_amount         NUMERIC(18,4) NOT NULL,   -- in the group reporting currency
    tax_amount          NUMERIC(18,4) NOT NULL DEFAULT 0,

    -- Shared-cost handling (SRS §15)
    allocation_method   VARCHAR(30)  NOT NULL DEFAULT 'DIRECT',
    -- DIRECT | MANUAL | PERCENTAGE | QUANTITY | EQUAL | COST_DRIVER
    is_shared           BOOLEAN      NOT NULL DEFAULT FALSE,

    reference           VARCHAR(120),
    invoice_number      VARCHAR(80),
    payment_method      VARCHAR(40),

    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | SUBMITTED | PENDING_APPROVAL | APPROVED | REJECTED | PAID | CANCELLED
    approval_stage      VARCHAR(40),
    submitted_by_user_id BIGINT      REFERENCES users(id),
    approved_by_user_id  BIGINT      REFERENCES users(id),
    approved_at         TIMESTAMP,
    rejection_reason    TEXT,
    paid_at             TIMESTAMP,

    source              VARCHAR(20)  NOT NULL DEFAULT 'WEB',
    client_uuid         VARCHAR(64),
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160),
    CONSTRAINT ck_expense_amount CHECK (amount >= 0)
);
CREATE INDEX idx_expenses_shaft_date   ON expenses (shaft_id, expense_date DESC)   WHERE deleted_at IS NULL;
CREATE INDEX idx_expenses_project_date ON expenses (project_id, expense_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_expenses_category     ON expenses (category_id)                   WHERE deleted_at IS NULL;
CREATE INDEX idx_expenses_status       ON expenses (status)                        WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_expenses_client_uuid ON expenses (client_uuid) WHERE client_uuid IS NOT NULL;

-- One row per shaft an expense is spread across.  A DIRECT expense also
-- gets exactly one row here, so every settlement query reads a single
-- table and never has to special-case the shared ones.
CREATE TABLE expense_allocations (
    id                  BIGSERIAL PRIMARY KEY,
    expense_id          BIGINT        NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    project_id          BIGINT        NOT NULL REFERENCES projects(id),
    mining_operation_id BIGINT        REFERENCES mining_operations(id),
    shaft_id            BIGINT        NOT NULL REFERENCES shafts(id),
    allocation_percent  NUMERIC(9,6),
    allocation_quantity NUMERIC(18,4),
    amount              NUMERIC(18,4) NOT NULL,
    base_amount         NUMERIC(18,4) NOT NULL,
    basis_note          TEXT,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160)
);
CREATE INDEX idx_expense_alloc_shaft   ON expense_allocations (shaft_id);
CREATE INDEX idx_expense_alloc_expense ON expense_allocations (expense_id);

-- Configurable approval thresholds (SRS §16 — "These thresholds must be
-- configurable").  Matched by category class and amount band.
CREATE TABLE approval_thresholds (
    id             BIGSERIAL PRIMARY KEY,
    entity_type    VARCHAR(40)  NOT NULL DEFAULT 'EXPENSE',
    project_id     BIGINT       REFERENCES projects(id),   -- NULL = group default
    expense_class  VARCHAR(20),                            -- NULL = any
    min_amount     NUMERIC(18,4) NOT NULL DEFAULT 0,
    max_amount     NUMERIC(18,4),                          -- NULL = no upper bound
    currency       CHAR(3)      NOT NULL DEFAULT 'USD',
    step_no        INT          NOT NULL DEFAULT 1,
    step_name      VARCHAR(80)  NOT NULL,
    required_role  VARCHAR(40)  NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_approval_thresholds_lookup ON approval_thresholds (entity_type, project_id, min_amount);

-- ---------------------------------------------------------------------
-- Inventory / stores (SRS §19) — covers fuel, explosives and consumables
-- under one item master so the stock ledger has a single shape.
-- ---------------------------------------------------------------------
CREATE TABLE inventory_items (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    code                VARCHAR(40)  NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    item_type           VARCHAR(30)  NOT NULL,  -- FUEL | EXPLOSIVE | CONSUMABLE | SPARE | PPE | CHEMICAL | OTHER
    category_id         BIGINT       REFERENCES expense_categories(id),
    unit                VARCHAR(20)  NOT NULL,  -- litre | kg | each | box
    -- Explosives are licence-controlled (SRS §18): issues require an
    -- authorised recipient and a permit reference.
    is_controlled       BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_permit     BOOLEAN      NOT NULL DEFAULT FALSE,
    minimum_stock       NUMERIC(18,4),
    maximum_stock       NUMERIC(18,4),
    reorder_level       NUMERIC(18,4),
    standard_cost       NUMERIC(18,6),
    cost_currency       CHAR(3),
    valuation_method    VARCHAR(20)  NOT NULL DEFAULT 'WEIGHTED_AVG', -- WEIGHTED_AVG | FIFO
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160)
);
CREATE INDEX idx_inventory_items_type ON inventory_items (item_type) WHERE is_active;

CREATE TABLE store_locations (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT       NOT NULL REFERENCES companies(id),
    code          VARCHAR(30)  NOT NULL UNIQUE,
    name          VARCHAR(160) NOT NULL,
    project_id    BIGINT       REFERENCES projects(id),
    shaft_id      BIGINT       REFERENCES shafts(id),
    location_id   BIGINT       REFERENCES locations(id),
    store_type    VARCHAR(30)  NOT NULL DEFAULT 'GENERAL', -- GENERAL | FUEL_BAY | MAGAZINE
    keeper_user_id BIGINT      REFERENCES users(id),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Running stock position per item per store.  Maintained by the service
-- layer inside the same transaction as the movement row.
CREATE TABLE inventory_balances (
    item_id        BIGINT        NOT NULL REFERENCES inventory_items(id),
    store_id       BIGINT        NOT NULL REFERENCES store_locations(id),
    quantity       NUMERIC(18,4) NOT NULL DEFAULT 0,
    average_cost   NUMERIC(18,6) NOT NULL DEFAULT 0,
    cost_currency  CHAR(3),
    last_movement_at TIMESTAMP,
    PRIMARY KEY (item_id, store_id)
);

-- Every movement: user + date + item + quantity + location + project/shaft
-- + reason (SRS §19).
CREATE TABLE inventory_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT        NOT NULL REFERENCES companies(id),
    transaction_number  VARCHAR(50)   NOT NULL UNIQUE,
    item_id             BIGINT        NOT NULL REFERENCES inventory_items(id),
    store_id            BIGINT        NOT NULL REFERENCES store_locations(id),
    transaction_type    VARCHAR(30)   NOT NULL,
    -- RECEIPT | ISSUE | TRANSFER_OUT | TRANSFER_IN | ADJUSTMENT | COUNT | RETURN
    transaction_date    TIMESTAMP     NOT NULL DEFAULT NOW(),
    quantity            NUMERIC(18,4) NOT NULL,   -- signed: + in, − out
    unit_cost           NUMERIC(18,6),
    total_cost          NUMERIC(18,4),
    currency            CHAR(3),
    balance_after       NUMERIC(18,4),

    project_id          BIGINT        REFERENCES projects(id),
    mining_operation_id BIGINT        REFERENCES mining_operations(id),
    shaft_id            BIGINT        REFERENCES shafts(id),
    equipment_id        BIGINT,       -- FK added after equipment table below
    recipient_employee_id BIGINT      REFERENCES employees(id),
    recipient_name      VARCHAR(160),
    supplier_id         BIGINT        REFERENCES suppliers(id),
    transfer_store_id   BIGINT        REFERENCES store_locations(id),
    expense_id          BIGINT        REFERENCES expenses(id),   -- auto-created expense
    permit_reference    VARCHAR(80),                             -- controlled items
    reason              TEXT          NOT NULL,
    reference           VARCHAR(120),
    source              VARCHAR(20)   NOT NULL DEFAULT 'WEB',
    client_uuid         VARCHAR(64),
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160)  NOT NULL,
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160)
);
CREATE INDEX idx_inv_txn_item_date ON inventory_transactions (item_id, transaction_date DESC);
CREATE INDEX idx_inv_txn_shaft     ON inventory_transactions (shaft_id, transaction_date DESC);
CREATE UNIQUE INDEX uq_inv_txn_client_uuid ON inventory_transactions (client_uuid) WHERE client_uuid IS NOT NULL;

-- Purchase orders / goods received (SRS §19)
CREATE TABLE purchase_orders (
    id             BIGSERIAL PRIMARY KEY,
    company_id     BIGINT       NOT NULL REFERENCES companies(id),
    po_number      VARCHAR(50)  NOT NULL UNIQUE,
    supplier_id    BIGINT       NOT NULL REFERENCES suppliers(id),
    project_id     BIGINT       REFERENCES projects(id),
    shaft_id       BIGINT       REFERENCES shafts(id),
    store_id       BIGINT       REFERENCES store_locations(id),
    order_date     DATE         NOT NULL,
    expected_date  DATE,
    currency       CHAR(3)      NOT NULL,
    subtotal       NUMERIC(18,4) NOT NULL DEFAULT 0,
    tax_amount     NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_amount   NUMERIC(18,4) NOT NULL DEFAULT 0,
    status         VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | SUBMITTED | APPROVED | PARTIALLY_RECEIVED | RECEIVED | CANCELLED
    approved_by    VARCHAR(160),
    approved_at    TIMESTAMP,
    notes          TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(160),
    updated_at     TIMESTAMP,
    updated_by     VARCHAR(160),
    deleted_at     TIMESTAMP,
    deleted_by     VARCHAR(160)
);

CREATE TABLE purchase_order_lines (
    id                BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT        NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    line_no           INT           NOT NULL,
    item_id           BIGINT        REFERENCES inventory_items(id),
    description       VARCHAR(300)  NOT NULL,
    quantity          NUMERIC(18,4) NOT NULL,
    received_quantity NUMERIC(18,4) NOT NULL DEFAULT 0,
    unit              VARCHAR(20),
    unit_cost         NUMERIC(18,6) NOT NULL,
    line_total        NUMERIC(18,4) NOT NULL,
    CONSTRAINT uq_po_line UNIQUE (purchase_order_id, line_no)
);

-- ---------------------------------------------------------------------
-- Fuel (SRS §17).  A fuel movement is an inventory_transaction; this
-- table adds the fuel-specific columns (vehicle, odometer, hours) and
-- links the three records the SRS example produces at once:
-- stock movement + expense + fuel record.
-- ---------------------------------------------------------------------
CREATE TABLE fuel_transactions (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT        NOT NULL REFERENCES companies(id),
    inventory_transaction_id BIGINT    REFERENCES inventory_transactions(id),
    expense_id           BIGINT        REFERENCES expenses(id),
    transaction_type     VARCHAR(20)   NOT NULL,   -- PURCHASE | ISSUE | TRANSFER | ADJUSTMENT
    transaction_date     TIMESTAMP     NOT NULL DEFAULT NOW(),
    fuel_type            VARCHAR(20)   NOT NULL,   -- DIESEL | PETROL | OIL
    item_id              BIGINT        REFERENCES inventory_items(id),
    store_id             BIGINT        REFERENCES store_locations(id),
    quantity_litres      NUMERIC(18,4) NOT NULL,
    unit_cost            NUMERIC(18,6),
    total_cost           NUMERIC(18,4),
    currency             CHAR(3),
    project_id           BIGINT        REFERENCES projects(id),
    mining_operation_id  BIGINT        REFERENCES mining_operations(id),
    shaft_id             BIGINT        REFERENCES shafts(id),
    equipment_id         BIGINT,       -- FK added after equipment table
    supplier_id          BIGINT        REFERENCES suppliers(id),
    recipient_employee_id BIGINT       REFERENCES employees(id),
    recipient_name       VARCHAR(160),
    odometer_reading     NUMERIC(12,2),
    hour_meter_reading   NUMERIC(12,2),
    opening_stock        NUMERIC(18,4),
    closing_stock        NUMERIC(18,4),
    reference            VARCHAR(120),
    source               VARCHAR(20)   NOT NULL DEFAULT 'WEB',
    client_uuid          VARCHAR(64),
    notes                TEXT,
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(160),
    deleted_at           TIMESTAMP,
    deleted_by           VARCHAR(160)
);
CREATE INDEX idx_fuel_shaft_date ON fuel_transactions (shaft_id, transaction_date DESC) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_fuel_client_uuid ON fuel_transactions (client_uuid) WHERE client_uuid IS NOT NULL;

-- ---------------------------------------------------------------------
-- Equipment & assets (SRS §20, §21, §22)
-- ---------------------------------------------------------------------
CREATE TABLE equipment (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT       NOT NULL REFERENCES companies(id),
    asset_number         VARCHAR(40)  NOT NULL UNIQUE,
    name                 VARCHAR(200) NOT NULL,
    equipment_type       VARCHAR(60)  NOT NULL,
    -- EXCAVATOR | LOADER | TIPPER | COMPRESSOR | JACKHAMMER | WASH_PLANT
    -- | MILL | PUMP | GENERATOR | VEHICLE | OTHER
    description          TEXT,
    manufacturer         VARCHAR(120),
    model                VARCHAR(120),
    serial_number        VARCHAR(120),
    registration_number  VARCHAR(60),
    year_of_manufacture  INT,
    purchase_date        DATE,
    purchase_cost        NUMERIC(18,4),
    purchase_currency    CHAR(3),
    current_value        NUMERIC(18,4),
    depreciation_method  VARCHAR(30),
    depreciation_rate    NUMERIC(9,4),
    ownership            VARCHAR(20)  NOT NULL DEFAULT 'OWNED', -- OWNED | LEASED | PARTNER | HIRED
    owner_partner_id     BIGINT       REFERENCES partners(id),
    supplier_id          BIGINT       REFERENCES suppliers(id),

    -- Current placement (the history lives in equipment_allocations)
    project_id           BIGINT       REFERENCES projects(id),
    mining_operation_id  BIGINT       REFERENCES mining_operations(id),
    shaft_id             BIGINT       REFERENCES shafts(id),
    location_id          BIGINT       REFERENCES locations(id),
    latitude             NUMERIC(10,7),
    longitude            NUMERIC(10,7),
    operator_employee_id BIGINT       REFERENCES employees(id),

    operating_hours      NUMERIC(12,2) NOT NULL DEFAULT 0,
    odometer             NUMERIC(12,2),
    fuel_consumption_rate NUMERIC(12,4),          -- litres per hour
    service_interval_hours NUMERIC(12,2),
    next_service_hours   NUMERIC(12,2),
    next_service_date    DATE,

    insurance_policy     VARCHAR(80),
    insurance_expiry     DATE,
    licence_expiry       DATE,

    status               VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    -- ACTIVE | STANDBY | UNDER_MAINTENANCE | BREAKDOWN | DECOMMISSIONED | DISPOSED
    notes                TEXT,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(160),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR(160),
    deleted_at           TIMESTAMP,
    deleted_by           VARCHAR(160)
);
CREATE INDEX idx_equipment_shaft  ON equipment (shaft_id)  WHERE deleted_at IS NULL;
CREATE INDEX idx_equipment_status ON equipment (status)    WHERE deleted_at IS NULL;

ALTER TABLE inventory_transactions
    ADD CONSTRAINT fk_inv_txn_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id);
ALTER TABLE fuel_transactions
    ADD CONSTRAINT fk_fuel_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id);

-- Historical allocation (SRS §21 — "The system must retain historical
-- allocation records").
CREATE TABLE equipment_allocations (
    id                  BIGSERIAL PRIMARY KEY,
    equipment_id        BIGINT     NOT NULL REFERENCES equipment(id),
    project_id          BIGINT     NOT NULL REFERENCES projects(id),
    mining_operation_id BIGINT     REFERENCES mining_operations(id),
    shaft_id            BIGINT     REFERENCES shafts(id),
    from_date           DATE       NOT NULL,
    to_date             DATE,                       -- NULL = current placement
    operator_employee_id BIGINT    REFERENCES employees(id),
    opening_hours       NUMERIC(12,2),
    closing_hours       NUMERIC(12,2),
    hire_rate           NUMERIC(18,4),
    hire_rate_unit      VARCHAR(20),                -- per hour / day / month
    rate_currency       CHAR(3),
    reason              TEXT,
    created_at          TIMESTAMP  NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    CONSTRAINT ck_alloc_dates CHECK (to_date IS NULL OR to_date >= from_date)
);
CREATE INDEX idx_equip_alloc_equipment ON equipment_allocations (equipment_id, from_date DESC);
CREATE INDEX idx_equip_alloc_shaft     ON equipment_allocations (shaft_id, from_date DESC);
CREATE UNIQUE INDEX uq_equip_alloc_current ON equipment_allocations (equipment_id) WHERE to_date IS NULL;

CREATE TABLE maintenance_records (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    equipment_id        BIGINT       NOT NULL REFERENCES equipment(id),
    job_number          VARCHAR(50)  NOT NULL UNIQUE,
    maintenance_type    VARCHAR(30)  NOT NULL,  -- PREVENTIVE | CORRECTIVE | INSPECTION | OVERHAUL
    priority            VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    reported_date       DATE,
    service_date        DATE,
    completed_date      DATE,
    next_service_date   DATE,
    next_service_hours  NUMERIC(12,2),
    hour_meter_reading  NUMERIC(12,2),
    description         TEXT         NOT NULL,
    work_performed      TEXT,
    technician_name     VARCHAR(160),
    technician_employee_id BIGINT    REFERENCES employees(id),
    supplier_id         BIGINT       REFERENCES suppliers(id),
    parts_cost          NUMERIC(18,4) NOT NULL DEFAULT 0,
    labour_cost         NUMERIC(18,4) NOT NULL DEFAULT 0,
    other_cost          NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_cost          NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency            CHAR(3),
    expense_id          BIGINT       REFERENCES expenses(id),
    downtime_hours      NUMERIC(12,2) NOT NULL DEFAULT 0,
    project_id          BIGINT       REFERENCES projects(id),
    shaft_id            BIGINT       REFERENCES shafts(id),
    status              VARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    -- OPEN | IN_PROGRESS | AWAITING_PARTS | COMPLETED | CANCELLED
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160)
);
CREATE INDEX idx_maintenance_equipment ON maintenance_records (equipment_id, service_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_maintenance_status    ON maintenance_records (status) WHERE deleted_at IS NULL;

CREATE TABLE maintenance_parts (
    id                    BIGSERIAL PRIMARY KEY,
    maintenance_record_id BIGINT        NOT NULL REFERENCES maintenance_records(id) ON DELETE CASCADE,
    item_id               BIGINT        REFERENCES inventory_items(id),
    description           VARCHAR(300)  NOT NULL,
    quantity              NUMERIC(18,4) NOT NULL,
    unit_cost             NUMERIC(18,6),
    total_cost            NUMERIC(18,4)
);
