-- =====================================================================
-- V6: Cross-cutting platform tables — documents, daily reports, alerts,
--     notifications, audit trail, offline sync, configuration.
--     SRS §31, §32, §33, §35, §39, §41, §46, §48
-- =====================================================================

-- ---------------------------------------------------------------------
-- Documents (SRS §35).  Polymorphic attachment: any entity, any file.
-- Bytes live in MinIO; this table holds the object key and metadata.
-- ---------------------------------------------------------------------
CREATE TABLE documents (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT       NOT NULL REFERENCES companies(id),
    entity_type       VARCHAR(40)  NOT NULL,
    -- COMPANY | PROJECT | OPERATION | SHAFT | PARTNER | CONTRACT | AGREEMENT
    -- | EXPENSE | PURCHASE | SALE | EQUIPMENT | PRODUCTION | PAYMENT
    -- | SETTLEMENT | MAINTENANCE | EMPLOYEE | INVENTORY
    entity_id         BIGINT       NOT NULL,
    document_type     VARCHAR(60),   -- CONTRACT | LICENCE | INVOICE | RECEIPT | ASSAY | PHOTO | ID | OTHER
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    file_name         VARCHAR(300) NOT NULL,
    storage_key       VARCHAR(500) NOT NULL,     -- MinIO object key
    content_type      VARCHAR(120),
    file_size_bytes   BIGINT,
    checksum_sha256   VARCHAR(64),
    version_no        INT          NOT NULL DEFAULT 1,
    supersedes_id     BIGINT       REFERENCES documents(id),
    is_confidential   BOOLEAN      NOT NULL DEFAULT FALSE,
    expiry_date       DATE,                       -- licences / permits / insurance
    uploaded_by_user_id BIGINT     REFERENCES users(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(160),
    updated_at        TIMESTAMP,
    updated_by        VARCHAR(160),
    deleted_at        TIMESTAMP,
    deleted_by        VARCHAR(160)
);
CREATE INDEX idx_documents_entity ON documents (entity_type, entity_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_expiry ON documents (expiry_date) WHERE deleted_at IS NULL AND expiry_date IS NOT NULL;

-- ---------------------------------------------------------------------
-- Daily / weekly site reports (SRS §32, §48)
-- ---------------------------------------------------------------------
CREATE TABLE daily_reports (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    report_number       VARCHAR(50)  NOT NULL UNIQUE,
    report_date         DATE         NOT NULL,
    project_id          BIGINT       NOT NULL REFERENCES projects(id),
    mining_operation_id BIGINT       REFERENCES mining_operations(id),
    shaft_id            BIGINT       REFERENCES shafts(id),
    shift               VARCHAR(20),
    reported_by_user_id BIGINT       REFERENCES users(id),

    headcount           INT,
    hours_worked        NUMERIC(10,2),
    production_quantity NUMERIC(18,4),
    production_unit     VARCHAR(20),
    ore_tonnes          NUMERIC(18,4),
    diesel_used_litres  NUMERIC(18,4),
    explosives_used     NUMERIC(18,4),
    equipment_hours     NUMERIC(12,2),
    downtime_hours      NUMERIC(12,2),
    weather             VARCHAR(80),
    safety_incidents    INT          NOT NULL DEFAULT 0,
    incident_notes      TEXT,
    activities          TEXT,
    issues              TEXT,
    plan_next_shift     TEXT,

    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | SUBMITTED | VERIFIED | APPROVED
    submitted_at        TIMESTAMP,
    verified_by_user_id BIGINT       REFERENCES users(id),
    verified_at         TIMESTAMP,
    source              VARCHAR(20)  NOT NULL DEFAULT 'WEB',
    client_uuid         VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(160)
);
CREATE INDEX idx_daily_reports_shaft_date ON daily_reports (shaft_id, report_date DESC) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_daily_report_client_uuid ON daily_reports (client_uuid) WHERE client_uuid IS NOT NULL;

-- ---------------------------------------------------------------------
-- Alert engine (SRS §31).  Thresholds are rows, not constants.
-- ---------------------------------------------------------------------
CREATE TABLE alert_rules (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES companies(id),
    code                VARCHAR(60)  NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    category            VARCHAR(30)  NOT NULL,
    -- PRODUCTION | EXPENSE | CONTRACT | INVENTORY | EQUIPMENT | REPORTING | FINANCIAL
    description         TEXT,
    severity            VARCHAR(20)  NOT NULL DEFAULT 'WARNING', -- INFO | WARNING | CRITICAL
    -- Scope: NULL project/shaft = applies group-wide.
    project_id          BIGINT       REFERENCES projects(id),
    shaft_id            BIGINT       REFERENCES shafts(id),
    comparison          VARCHAR(20)  NOT NULL DEFAULT 'LESS_THAN',
    -- LESS_THAN | GREATER_THAN | PERCENT_BELOW | PERCENT_ABOVE | NO_ACTIVITY_DAYS | DAYS_BEFORE
    threshold_value     NUMERIC(18,4),
    threshold_unit      VARCHAR(20),
    evaluation_window_days INT       NOT NULL DEFAULT 1,
    notify_roles        VARCHAR(300),   -- comma-separated role codes
    notify_emails       VARCHAR(500),
    channels            VARCHAR(120) NOT NULL DEFAULT 'IN_APP',  -- IN_APP,EMAIL,PUSH
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(160),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(160)
);

CREATE TABLE alerts (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES companies(id),
    alert_rule_id   BIGINT       REFERENCES alert_rules(id),
    category        VARCHAR(30)  NOT NULL,
    severity        VARCHAR(20)  NOT NULL DEFAULT 'WARNING',
    title           VARCHAR(200) NOT NULL,
    message         TEXT         NOT NULL,
    project_id      BIGINT       REFERENCES projects(id),
    mining_operation_id BIGINT   REFERENCES mining_operations(id),
    shaft_id        BIGINT       REFERENCES shafts(id),
    entity_type     VARCHAR(40),
    entity_id       BIGINT,
    actual_value    NUMERIC(18,4),
    threshold_value NUMERIC(18,4),
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN', -- OPEN | ACKNOWLEDGED | RESOLVED | DISMISSED
    acknowledged_by VARCHAR(160),
    acknowledged_at TIMESTAMP,
    resolved_at     TIMESTAMP,
    resolution_note TEXT,
    triggered_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    -- Stops the nightly evaluator re-raising the same open alert every run.
    dedupe_key      VARCHAR(200)
);
CREATE INDEX idx_alerts_status ON alerts (status, triggered_at DESC);
CREATE INDEX idx_alerts_shaft  ON alerts (shaft_id, triggered_at DESC);
CREATE UNIQUE INDEX uq_alerts_open_dedupe ON alerts (dedupe_key) WHERE status = 'OPEN' AND dedupe_key IS NOT NULL;

-- ---------------------------------------------------------------------
-- Notifications (SRS §46) — in-app, email, push.
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alert_id      BIGINT       REFERENCES alerts(id),
    category      VARCHAR(40)  NOT NULL,
    title         VARCHAR(200) NOT NULL,
    message       TEXT         NOT NULL,
    link_url      VARCHAR(500),
    severity      VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    is_read       BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at       TIMESTAMP,
    email_sent_at TIMESTAMP,
    push_sent_at  TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user ON notifications (user_id, is_read, created_at DESC);

-- ---------------------------------------------------------------------
-- Audit trail (SRS §39).  Every critical action, with old → new values
-- and the stated reason for the change.
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    occurred_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    user_email    VARCHAR(160),
    user_id       BIGINT,
    user_role     VARCHAR(40),
    action        VARCHAR(60)  NOT NULL,
    -- CREATE | UPDATE | DELETE | APPROVE | REJECT | LOGIN | LOGOUT | EXPORT
    -- | ISSUE | RECEIVE | CALCULATE | PAY | READ
    entity_type   VARCHAR(40)  NOT NULL,
    entity_id     BIGINT,
    entity_label  VARCHAR(200),         -- human key: "Shaft 3", "Contract C-0007"
    project_id    BIGINT,
    shaft_id      BIGINT,
    field_name    VARCHAR(80),
    old_value     TEXT,
    new_value     TEXT,
    reason        TEXT,
    summary       TEXT,
    ip_address    VARCHAR(60),
    user_agent    VARCHAR(300),
    request_id    VARCHAR(64)
);
CREATE INDEX idx_audit_entity  ON audit_logs (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_user    ON audit_logs (user_email, occurred_at DESC);
CREATE INDEX idx_audit_time    ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_shaft   ON audit_logs (shaft_id, occurred_at DESC);

-- ---------------------------------------------------------------------
-- Offline sync log (SRS §33).  Mobile posts a batch with client UUIDs;
-- replays land here and are rejected as duplicates rather than double-
-- counting, and genuine conflicts are flagged for review.
-- ---------------------------------------------------------------------
CREATE TABLE sync_batches (
    id            BIGSERIAL PRIMARY KEY,
    batch_uuid    VARCHAR(64)  NOT NULL UNIQUE,
    user_id       BIGINT       REFERENCES users(id),
    device_id     VARCHAR(120),
    received_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    record_count  INT          NOT NULL DEFAULT 0,
    accepted_count INT         NOT NULL DEFAULT 0,
    duplicate_count INT        NOT NULL DEFAULT 0,
    conflict_count  INT        NOT NULL DEFAULT 0,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PROCESSED',
    payload_summary TEXT
);

CREATE TABLE sync_conflicts (
    id            BIGSERIAL PRIMARY KEY,
    batch_id      BIGINT       REFERENCES sync_batches(id),
    entity_type   VARCHAR(40)  NOT NULL,
    client_uuid   VARCHAR(64)  NOT NULL,
    server_id     BIGINT,
    reason        VARCHAR(200) NOT NULL,
    client_payload TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | RESOLVED | DISCARDED
    resolved_by   VARCHAR(160),
    resolved_at   TIMESTAMP,
    resolution_note TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- Configuration engine (SRS §41).  Typed key/value so an administrator
-- can change behaviour without a redeploy.
-- ---------------------------------------------------------------------
CREATE TABLE system_config (
    config_key    VARCHAR(80)  PRIMARY KEY,
    config_value  TEXT,
    value_type    VARCHAR(20)  NOT NULL DEFAULT 'STRING', -- STRING | NUMBER | BOOLEAN | JSON
    category      VARCHAR(40)  NOT NULL DEFAULT 'GENERAL',
    label         VARCHAR(200),
    description   TEXT,
    is_editable   BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMP,
    updated_by    VARCHAR(160)
);

-- Saved report definitions (SRS §28) so the report list is data-driven.
CREATE TABLE report_definitions (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(60)  NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    report_group  VARCHAR(30)  NOT NULL,  -- GROUP | PROJECT | SHAFT | OPERATIONAL | FINANCIAL
    description   TEXT,
    required_permission VARCHAR(80),
    supports_pdf  BOOLEAN      NOT NULL DEFAULT TRUE,
    supports_excel BOOLEAN     NOT NULL DEFAULT TRUE,
    supports_csv  BOOLEAN      NOT NULL DEFAULT TRUE,
    default_period VARCHAR(20) NOT NULL DEFAULT 'MONTH',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 100
);
