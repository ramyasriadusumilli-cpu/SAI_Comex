-- =====================================================================
-- V7: Reference data — currencies, units, roles, permissions, contract
--     types, agreement rule types, expense categories, approval
--     thresholds, config, report catalogue, and the bootstrap admin.
--     SRS §37, §40, §41
-- =====================================================================

-- ---------------------------------------------------------------------
-- Currencies (SRS §40 — USD / ZAR / ZWG initially, extensible)
-- ---------------------------------------------------------------------
INSERT INTO currencies (code, name, symbol, decimal_places, display_order) VALUES
    ('USD', 'United States Dollar', '$',   2, 10),
    ('ZAR', 'South African Rand',   'R',   2, 20),
    ('ZWG', 'Zimbabwe Gold',        'ZiG', 2, 30);

-- ---------------------------------------------------------------------
-- Production units.  base_factor converts to the class base unit, so a
-- dashboard can total grams and kilograms without asking the operator.
-- ---------------------------------------------------------------------
INSERT INTO production_units (code, name, unit_class, base_factor, decimal_places, display_order) VALUES
    ('G',      'Gram',        'MASS',   1,          3, 10),
    ('KG',     'Kilogram',    'MASS',   1000,       4, 20),
    ('T',      'Tonne',       'MASS',   1000000,    4, 30),
    ('OZ',     'Troy Ounce',  'MASS',   31.1034768, 4, 40),
    ('CT',     'Carat',       'MASS',   0.2,        3, 50),
    ('M3',     'Cubic Metre', 'VOLUME', 1,          2, 60),
    ('L',      'Litre',       'VOLUME', 0.001,      2, 70),
    ('EACH',   'Each',        'COUNT',  1,          0, 80);

-- ---------------------------------------------------------------------
-- Company (SRS §2 — the group entity everything hangs off)
-- ---------------------------------------------------------------------
INSERT INTO companies (code, name, trading_name, country, reporting_currency, created_by)
VALUES ('SAICOMEX', 'SAIComex Mining Company', 'SAIComex', 'Zimbabwe', 'USD', 'system');

-- ---------------------------------------------------------------------
-- Roles (SRS §37).  is_system roles cannot be deleted through the UI —
-- the permission model stays configurable, the role set stays coherent.
-- ---------------------------------------------------------------------
INSERT INTO roles (code, name, description, is_system, display_order, created_by) VALUES
    ('DIRECTOR',          'Director',             'Full system access, including configuration and user management.', TRUE, 10, 'system'),
    ('EXECUTIVE',         'Executive Management', 'Full operational and financial visibility across the group.',       TRUE, 20, 'system'),
    ('ADMIN',             'System Administrator', 'Configuration, users, roles and integrations.',                     TRUE, 30, 'system'),
    ('PROJECT_MANAGER',   'Project Manager',      'Full access within assigned projects.',                             TRUE, 40, 'system'),
    ('SITE_MANAGER',      'Site Manager',         'Operational access within assigned operations and shafts.',         TRUE, 50, 'system'),
    ('SHAFT_MANAGER',     'Shaft Manager',        'Production and operational access for assigned shafts.',            TRUE, 60, 'system'),
    ('FINANCE',           'Finance',              'Financial, settlement, payment and budget access.',                 TRUE, 70, 'system'),
    ('STOREKEEPER',       'Storekeeper',          'Inventory, stores and fuel access.',                                TRUE, 80, 'system'),
    ('EQUIPMENT_MANAGER', 'Equipment Manager',    'Asset register and maintenance access.',                            TRUE, 90, 'system'),
    ('AUDITOR',           'Auditor',              'Read-only access to everything, including the audit trail.',        TRUE,100, 'system'),
    ('FIELD_OPERATOR',    'Field Operator',       'Restricted mobile data-entry for assigned shafts.',                 TRUE,110, 'system');

-- ---------------------------------------------------------------------
-- Permissions: one row per (module, action).  Generated rather than
-- typed out so a new module is one line in the modules list below.
-- ---------------------------------------------------------------------
INSERT INTO permissions (code, module, action, description)
SELECT m.module || '.' || a.action,
       m.module,
       a.action,
       initcap(a.action) || ' ' || m.label
FROM (VALUES
        ('dashboard',   'the executive dashboard'),
        ('projects',    'projects'),
        ('operations',  'mining operations'),
        ('shafts',      'shafts'),
        ('partners',    'partners and shaft owners'),
        ('contracts',   'contracts'),
        ('agreements',  'commercial agreements'),
        ('production',  'production records'),
        ('expenses',    'expenses'),
        ('fuel',        'fuel transactions'),
        ('inventory',   'inventory and stores'),
        ('equipment',   'equipment and assets'),
        ('maintenance', 'maintenance records'),
        ('sales',       'sales and revenue'),
        ('payments',    'payments'),
        ('settlements', 'partner settlements'),
        ('budgets',     'budgets'),
        ('employees',   'employees'),
        ('suppliers',   'suppliers'),
        ('documents',   'documents'),
        ('reports',     'reports'),
        ('alerts',      'alerts'),
        ('users',       'users'),
        ('roles',       'roles and permissions'),
        ('audit',       'the audit trail'),
        ('settings',    'system configuration')
     ) AS m(module, label)
CROSS JOIN (VALUES
        ('view'), ('create'), ('edit'), ('delete'), ('approve'), ('export')
     ) AS a(action);

-- Fine-grained permissions that are not a plain CRUD action on a module.
INSERT INTO permissions (code, module, action, description) VALUES
    ('partners.banking',    'partners',    'banking',   'View and edit partner banking details'),
    ('financial.view',      'financial',   'view',      'See monetary values anywhere in the application'),
    ('settlements.calculate','settlements','calculate', 'Run the commercial calculation engine'),
    ('production.verify',   'production',  'verify',    'Verify submitted production before approval'),
    ('agreements.override', 'agreements',  'override',  'Override a computed settlement figure (always audited)');

-- ---------------------------------------------------------------------
-- Role → permission grants.
--
-- Expressed as (role, module, action-set) tuples and expanded by a join,
-- which keeps the intent readable.  'ALL' in the action column means
-- every action defined for that module.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE tmp_grants (role_code VARCHAR(40), module VARCHAR(40), actions VARCHAR(200));

INSERT INTO tmp_grants VALUES
    -- DIRECTOR and ADMIN: everything.  Handled by the wildcard block below.

    -- EXECUTIVE — full visibility, approves, but does not administer users.
    ('EXECUTIVE','dashboard','view,export'),   ('EXECUTIVE','projects','view,create,edit,approve,export'),
    ('EXECUTIVE','operations','view,create,edit,approve,export'), ('EXECUTIVE','shafts','view,create,edit,approve,export'),
    ('EXECUTIVE','partners','view,create,edit,approve,export,banking'), ('EXECUTIVE','contracts','view,create,edit,approve,export'),
    ('EXECUTIVE','agreements','view,create,edit,approve,export'), ('EXECUTIVE','production','view,approve,export'),
    ('EXECUTIVE','expenses','view,approve,export'), ('EXECUTIVE','fuel','view,export'),
    ('EXECUTIVE','inventory','view,export'), ('EXECUTIVE','equipment','view,export'),
    ('EXECUTIVE','maintenance','view,export'), ('EXECUTIVE','sales','view,create,edit,approve,export'),
    ('EXECUTIVE','payments','view,approve,export'), ('EXECUTIVE','settlements','view,approve,export,calculate'),
    ('EXECUTIVE','budgets','view,create,edit,approve,export'), ('EXECUTIVE','employees','view,export'),
    ('EXECUTIVE','suppliers','view,export'), ('EXECUTIVE','documents','view,create,export'),
    ('EXECUTIVE','reports','view,export'), ('EXECUTIVE','alerts','view,edit'),
    ('EXECUTIVE','audit','view,export'), ('EXECUTIVE','financial','view'),

    -- PROJECT_MANAGER — full operational control inside assigned projects.
    ('PROJECT_MANAGER','dashboard','view,export'), ('PROJECT_MANAGER','projects','view,edit,export'),
    ('PROJECT_MANAGER','operations','view,create,edit,export'), ('PROJECT_MANAGER','shafts','view,create,edit,export'),
    ('PROJECT_MANAGER','partners','view,export'), ('PROJECT_MANAGER','contracts','view,create,edit,export'),
    ('PROJECT_MANAGER','agreements','view,export'), ('PROJECT_MANAGER','production','view,create,edit,approve,export,verify'),
    ('PROJECT_MANAGER','expenses','view,create,edit,approve,export'), ('PROJECT_MANAGER','fuel','view,create,edit,export'),
    ('PROJECT_MANAGER','inventory','view,create,edit,export'), ('PROJECT_MANAGER','equipment','view,create,edit,export'),
    ('PROJECT_MANAGER','maintenance','view,create,edit,export'), ('PROJECT_MANAGER','sales','view,create,edit,export'),
    ('PROJECT_MANAGER','payments','view,create,export'), ('PROJECT_MANAGER','settlements','view,export'),
    ('PROJECT_MANAGER','budgets','view,create,edit,export'), ('PROJECT_MANAGER','employees','view,create,edit,export'),
    ('PROJECT_MANAGER','suppliers','view,export'), ('PROJECT_MANAGER','documents','view,create,edit,export'),
    ('PROJECT_MANAGER','reports','view,export'), ('PROJECT_MANAGER','alerts','view,edit'),
    ('PROJECT_MANAGER','financial','view'),

    -- SITE_MANAGER — assigned operations / shafts.
    ('SITE_MANAGER','dashboard','view'), ('SITE_MANAGER','projects','view'),
    ('SITE_MANAGER','operations','view,edit'), ('SITE_MANAGER','shafts','view,edit'),
    ('SITE_MANAGER','partners','view'), ('SITE_MANAGER','contracts','view'),
    ('SITE_MANAGER','production','view,create,edit,export,verify'),
    ('SITE_MANAGER','expenses','view,create,edit,approve,export'), ('SITE_MANAGER','fuel','view,create,edit,export'),
    ('SITE_MANAGER','inventory','view,create,edit,export'), ('SITE_MANAGER','equipment','view,edit,export'),
    ('SITE_MANAGER','maintenance','view,create,edit,export'), ('SITE_MANAGER','employees','view,create,edit'),
    ('SITE_MANAGER','documents','view,create,export'), ('SITE_MANAGER','reports','view,export'),
    ('SITE_MANAGER','alerts','view'), ('SITE_MANAGER','financial','view'),

    -- SHAFT_MANAGER — production and operations for their shafts.
    ('SHAFT_MANAGER','dashboard','view'), ('SHAFT_MANAGER','projects','view'),
    ('SHAFT_MANAGER','operations','view'), ('SHAFT_MANAGER','shafts','view'),
    ('SHAFT_MANAGER','production','view,create,edit,export,verify'),
    ('SHAFT_MANAGER','expenses','view,create,export'), ('SHAFT_MANAGER','fuel','view,create,export'),
    ('SHAFT_MANAGER','inventory','view,create'), ('SHAFT_MANAGER','equipment','view'),
    ('SHAFT_MANAGER','maintenance','view,create'), ('SHAFT_MANAGER','employees','view'),
    ('SHAFT_MANAGER','documents','view,create'), ('SHAFT_MANAGER','reports','view,export'),
    ('SHAFT_MANAGER','alerts','view'),

    -- FINANCE — the money, end to end.
    ('FINANCE','dashboard','view,export'), ('FINANCE','projects','view,export'),
    ('FINANCE','operations','view'), ('FINANCE','shafts','view'),
    ('FINANCE','partners','view,create,edit,export,banking'), ('FINANCE','contracts','view,export'),
    ('FINANCE','agreements','view,export'), ('FINANCE','production','view,export'),
    ('FINANCE','expenses','view,create,edit,approve,export'), ('FINANCE','fuel','view,export'),
    ('FINANCE','inventory','view,export'), ('FINANCE','equipment','view,export'),
    ('FINANCE','maintenance','view,export'), ('FINANCE','sales','view,create,edit,approve,export'),
    ('FINANCE','payments','view,create,edit,approve,export'),
    ('FINANCE','settlements','view,create,edit,approve,export,calculate'),
    ('FINANCE','budgets','view,create,edit,export'), ('FINANCE','employees','view,export'),
    ('FINANCE','suppliers','view,create,edit,export'), ('FINANCE','documents','view,create,export'),
    ('FINANCE','reports','view,export'), ('FINANCE','alerts','view'),
    ('FINANCE','audit','view,export'), ('FINANCE','financial','view'),

    -- STOREKEEPER — stores, fuel, explosives.
    ('STOREKEEPER','dashboard','view'), ('STOREKEEPER','projects','view'), ('STOREKEEPER','shafts','view'),
    ('STOREKEEPER','inventory','view,create,edit,export'), ('STOREKEEPER','fuel','view,create,edit,export'),
    ('STOREKEEPER','suppliers','view,create,edit'), ('STOREKEEPER','equipment','view'),
    ('STOREKEEPER','employees','view'), ('STOREKEEPER','documents','view,create'),
    ('STOREKEEPER','reports','view,export'), ('STOREKEEPER','alerts','view'),

    -- EQUIPMENT_MANAGER — asset register and maintenance.
    ('EQUIPMENT_MANAGER','dashboard','view'), ('EQUIPMENT_MANAGER','projects','view'),
    ('EQUIPMENT_MANAGER','operations','view'), ('EQUIPMENT_MANAGER','shafts','view'),
    ('EQUIPMENT_MANAGER','equipment','view,create,edit,export'),
    ('EQUIPMENT_MANAGER','maintenance','view,create,edit,approve,export'),
    ('EQUIPMENT_MANAGER','fuel','view,export'), ('EQUIPMENT_MANAGER','inventory','view'),
    ('EQUIPMENT_MANAGER','suppliers','view'), ('EQUIPMENT_MANAGER','employees','view'),
    ('EQUIPMENT_MANAGER','documents','view,create'), ('EQUIPMENT_MANAGER','reports','view,export'),
    ('EQUIPMENT_MANAGER','alerts','view'),

    -- FIELD_OPERATOR — mobile capture only.
    ('FIELD_OPERATOR','shafts','view'), ('FIELD_OPERATOR','production','view,create'),
    ('FIELD_OPERATOR','expenses','view,create'), ('FIELD_OPERATOR','fuel','view,create'),
    ('FIELD_OPERATOR','inventory','view,create'), ('FIELD_OPERATOR','equipment','view'),
    ('FIELD_OPERATOR','maintenance','view,create'), ('FIELD_OPERATOR','documents','create');

-- Expand the tuples into role_permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tmp_grants g
JOIN roles r       ON r.code = g.role_code
JOIN permissions p ON p.module = g.module
                  AND p.action = ANY (string_to_array(g.actions, ','))
ON CONFLICT DO NOTHING;

-- DIRECTOR and ADMIN get every permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('DIRECTOR', 'ADMIN')
ON CONFLICT DO NOTHING;

-- AUDITOR: read-only everywhere — every 'view' and 'export', nothing else.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'AUDITOR' AND p.action IN ('view', 'export')
ON CONFLICT DO NOTHING;

DROP TABLE tmp_grants;

-- ---------------------------------------------------------------------
-- Contract types (SRS §10, §41 — configurable)
-- ---------------------------------------------------------------------
INSERT INTO contract_types (code, name, description, display_order) VALUES
    ('TRIBUTE',        'Tribute Agreement',       'Shaft owner mines and delivers ore/gold under an agreed production or revenue share.', 10),
    ('PROFIT_SHARE',   'Profit Share',            'Net proceeds after agreed costs are split between SAIComex and the partner.',          20),
    ('TOLL_MILLING',   'Toll Milling',            'SAIComex processes third-party ore for a fee or share of recovered metal.',            30),
    ('JOINT_VENTURE',  'Joint Venture',           'Shared capital and operating exposure with an agreed distribution.',                   40),
    ('LEASE',          'Shaft Lease',             'Fixed periodic payment for the right to mine, irrespective of production.',            50),
    ('CONTRACT_MINING','Contract Mining',         'A contractor mines on SAIComex account at an agreed rate per tonne or gram.',          60),
    ('OTHER',          'Other',                   'Bespoke arrangement — terms captured entirely in agreement rules.',                    99);

-- ---------------------------------------------------------------------
-- Agreement rule types (SRS §11).  Every configurable parameter listed
-- in the SRS appears here, mapped to its stage in the §12 waterfall.
-- ---------------------------------------------------------------------
INSERT INTO agreement_rule_types (code, name, stage, default_sequence, description) VALUES
    ('OPEX_SHARE',            'Operating expense share',  'DEDUCTION',  10, 'How operating expenditure is shared before distribution.'),
    ('FUEL_COST_SHARE',       'Fuel cost share',          'DEDUCTION',  20, 'Diesel, petrol and oil.'),
    ('EXPLOSIVE_COST_SHARE',  'Explosive cost share',     'DEDUCTION',  30, 'Explosives and blasting accessories.'),
    ('LABOUR_COST_SHARE',     'Labour cost share',        'DEDUCTION',  40, 'Wages, contractor labour and related costs.'),
    ('EQUIPMENT_COST_SHARE',  'Equipment cost share',     'DEDUCTION',  50, 'Equipment hire, running and repair costs.'),
    ('PROCESSING_COST_SHARE', 'Processing cost share',    'DEDUCTION',  60, 'Milling, washing, elution and refining.'),
    ('TRANSPORT_COST_SHARE',  'Transport cost share',     'DEDUCTION',  70, 'Haulage of ore, product and consumables.'),
    ('CAPEX_SHARE',           'Capital expenditure share','DEDUCTION',  80, 'Capital items and development costs.'),
    ('MANAGEMENT_FEE',        'Management fee',           'DEDUCTION',  90, 'Fee retained by SAIComex before distribution.'),
    ('CAPITAL_RECOVERY',      'Capital recovery',         'DEDUCTION', 100, 'Recovery of capital advanced to the shaft, until repaid.'),
    ('SPECIAL_DEDUCTION',     'Special deduction',        'DEDUCTION', 110, 'Any contract-specific deduction taken before the split.'),
    ('PRODUCTION_SHARE',      'Production share',         'ALLOCATION',200, 'Split applied to physical production rather than revenue.'),
    ('REVENUE_SHARE',         'Revenue share',            'ALLOCATION',210, 'The primary split of distributable revenue.'),
    ('PROFIT_SHARE',          'Profit share',             'ALLOCATION',220, 'Split applied to net profit after all costs.'),
    ('MINIMUM_PAYMENT',       'Minimum payment',          'ADJUSTMENT',300, 'Floor on the partner''s payout for the period.'),
    ('ADVANCE_RECOVERY',      'Advance recovery',         'ADJUSTMENT',310, 'Recovery of advances or loans made to the partner.'),
    ('PENALTY',               'Penalty / retention',      'ADJUSTMENT',320, 'Contractual penalty or amount retained.'),
    ('SETTLEMENT_RULE',       'Settlement rule',          'ADJUSTMENT',330, 'Timing, rounding and payment-condition rules.');

-- ---------------------------------------------------------------------
-- Expense categories (SRS §15).  Two roots so the agreement engine can
-- treat OPEX and CAPEX under different contractual parameters.
-- ---------------------------------------------------------------------
INSERT INTO expense_categories (code, name, expense_class, display_order) VALUES
    ('DIESEL',        'Diesel',                 'OPEX',  10),
    ('PETROL',        'Petrol',                 'OPEX',  20),
    ('OIL',           'Oil & Lubricants',       'OPEX',  30),
    ('EXPLOSIVES',    'Explosives',             'OPEX',  40),
    ('LABOUR',        'Labour',                 'OPEX',  50),
    ('EQUIPMENT',     'Equipment Hire',         'OPEX',  60),
    ('REPAIRS',       'Repairs',                'OPEX',  70),
    ('MAINTENANCE',   'Maintenance',            'OPEX',  80),
    ('TRANSPORT',     'Transport',              'OPEX',  90),
    ('SECURITY',      'Security',               'OPEX', 100),
    ('ACCOMMODATION', 'Accommodation',          'OPEX', 110),
    ('FOOD',          'Food & Rations',         'OPEX', 120),
    ('PPE',           'PPE',                    'OPEX', 130),
    ('CHEMICALS',     'Chemicals',              'OPEX', 140),
    ('PROCESSING',    'Processing',             'OPEX', 150),
    ('LABORATORY',    'Laboratory / Assay',     'OPEX', 160),
    ('SPARES',        'Spare Parts',            'OPEX', 170),
    ('CONTRACTOR',    'Contractor Costs',       'OPEX', 180),
    ('ADMIN',         'Administrative Costs',   'OPEX', 190),
    ('ROYALTY',       'Royalties & Levies',     'OPEX', 200),
    ('OTHER_OPEX',    'Other Operating Costs',  'OPEX', 210),
    ('CAPEX_PLANT',   'Capital — Plant',        'CAPEX', 300),
    ('CAPEX_EQUIP',   'Capital — Equipment',    'CAPEX', 310),
    ('CAPEX_DEV',     'Capital — Development',  'CAPEX', 320),
    ('CAPEX_OTHER',   'Capital — Other',        'CAPEX', 330);

-- ---------------------------------------------------------------------
-- Default approval thresholds (SRS §16).  Group-level defaults in USD;
-- a project may override by inserting rows with its own project_id.
-- ---------------------------------------------------------------------
INSERT INTO approval_thresholds (entity_type, min_amount, max_amount, currency, step_no, step_name, required_role) VALUES
    ('EXPENSE',     0,    500,   'USD', 1, 'Site Manager approval',    'SITE_MANAGER'),
    ('EXPENSE',   500,   5000,   'USD', 1, 'Project Manager approval', 'PROJECT_MANAGER'),
    ('EXPENSE',  5000,   NULL,   'USD', 1, 'Executive approval',       'EXECUTIVE'),
    ('PAYMENT',     0,   5000,   'USD', 1, 'Finance approval',         'FINANCE'),
    ('PAYMENT',  5000,   NULL,   'USD', 1, 'Executive approval',       'EXECUTIVE'),
    ('SETTLEMENT',  0,   NULL,   'USD', 1, 'Finance approval',         'FINANCE'),
    ('SETTLEMENT',  0,   NULL,   'USD', 2, 'Executive approval',       'EXECUTIVE');

-- ---------------------------------------------------------------------
-- System configuration (SRS §41)
-- ---------------------------------------------------------------------
INSERT INTO system_config (config_key, config_value, value_type, category, label, description) VALUES
    ('group.reporting_currency',      'USD',   'STRING',  'GENERAL',    'Group reporting currency',      'Currency every figure is converted to for consolidated reporting.'),
    ('group.default_production_unit', 'G',     'STRING',  'PRODUCTION', 'Default production unit',       'Unit pre-selected on new production entries.'),
    ('production.require_verification','true', 'BOOLEAN', 'PRODUCTION', 'Require shaft verification',    'Production must be verified by a shaft manager before approval.'),
    ('production.allow_backdate_days','7',     'NUMBER',  'PRODUCTION', 'Back-dating window (days)',     'How far back a field user may date a production entry.'),
    ('expense.require_document',      'false', 'BOOLEAN', 'EXPENSE',    'Require supporting document',   'Block expense submission without an attachment.'),
    ('settlement.default_frequency',  'MONTHLY','STRING', 'SETTLEMENT', 'Default settlement frequency',  'Used when a contract does not specify one.'),
    ('settlement.rounding_scale',     '2',     'NUMBER',  'SETTLEMENT', 'Settlement rounding scale',     'Decimal places applied to each allocation line.'),
    ('alert.no_production_days',      '3',     'NUMBER',  'ALERT',      'No-production alert (days)',    'Raise an alert when an active shaft records nothing for this many days.'),
    ('alert.production_below_target_pct','80', 'NUMBER',  'ALERT',      'Production below target (%)',   'Raise an alert when production falls below this share of target.'),
    ('alert.contract_expiry_days',    '30',    'NUMBER',  'ALERT',      'Contract expiry warning (days)','Days ahead of expiry to start warning.'),
    ('alert.budget_overrun_pct',      '100',   'NUMBER',  'ALERT',      'Budget overrun (%)',            'Raise an alert once spend passes this share of budget.'),
    ('security.session_idle_minutes', '30',    'NUMBER',  'SECURITY',   'Idle session timeout (minutes)','Automatic sign-out after this much inactivity.'),
    ('security.password_min_length',  '10',    'NUMBER',  'SECURITY',   'Minimum password length',       'Enforced on password set and reset.'),
    ('security.max_failed_logins',    '5',     'NUMBER',  'SECURITY',   'Failed logins before lockout',  'Account locks temporarily after this many failures.');

-- ---------------------------------------------------------------------
-- Report catalogue (SRS §28)
-- ---------------------------------------------------------------------
INSERT INTO report_definitions (code, name, report_group, required_permission, display_order) VALUES
    ('GROUP_PERFORMANCE',      'Group Performance',            'GROUP',       'reports.view', 10),
    ('CONSOLIDATED_PRODUCTION','Consolidated Production',      'GROUP',       'reports.view', 20),
    ('CONSOLIDATED_REVENUE',   'Consolidated Revenue',         'GROUP',       'financial.view', 30),
    ('CONSOLIDATED_EXPENSES',  'Consolidated Expenses',        'GROUP',       'financial.view', 40),
    ('CONSOLIDATED_PROFIT',    'Consolidated Profitability',   'GROUP',       'financial.view', 50),
    ('PROJECT_PRODUCTION',     'Project Production',           'PROJECT',     'reports.view', 60),
    ('PROJECT_EXPENSES',       'Project Expenses',             'PROJECT',     'financial.view', 70),
    ('PROJECT_PROFIT',         'Project Profitability',        'PROJECT',     'financial.view', 80),
    ('PROJECT_BUDGET_ACTUAL',  'Project Budget vs Actual',     'PROJECT',     'financial.view', 90),
    ('SHAFT_PRODUCTION',       'Shaft Production',             'SHAFT',       'reports.view', 100),
    ('SHAFT_EXPENSES',         'Shaft Expenses',               'SHAFT',       'financial.view', 110),
    ('SHAFT_PROFIT',           'Shaft Profitability',          'SHAFT',       'financial.view', 120),
    ('SHAFT_UNIT_COST',        'Cost per Gram / per Tonne',    'SHAFT',       'financial.view', 130),
    ('SHAFT_PARTNER_ALLOC',    'Partner Allocation by Shaft',  'SHAFT',       'settlements.view', 140),
    ('FUEL_CONSUMPTION',       'Fuel Consumption',             'OPERATIONAL', 'fuel.view', 150),
    ('EXPLOSIVES_CONSUMPTION', 'Explosives Consumption',       'OPERATIONAL', 'inventory.view', 160),
    ('INVENTORY_POSITION',     'Inventory Position',           'OPERATIONAL', 'inventory.view', 170),
    ('EQUIPMENT_UTILISATION',  'Equipment Utilisation',        'OPERATIONAL', 'equipment.view', 180),
    ('EQUIPMENT_DOWNTIME',     'Equipment Downtime',           'OPERATIONAL', 'equipment.view', 190),
    ('MAINTENANCE_HISTORY',    'Maintenance History',          'OPERATIONAL', 'maintenance.view', 200),
    ('LABOUR_REPORT',          'Labour Report',                'OPERATIONAL', 'employees.view', 210),
    ('PRODUCTION_VARIANCE',    'Production Variance',          'OPERATIONAL', 'reports.view', 220),
    ('PARTNER_STATEMENT',      'Partner Statement',            'FINANCIAL',   'settlements.view', 230),
    ('SETTLEMENT_REPORT',      'Settlement Report',            'FINANCIAL',   'settlements.view', 240),
    ('PAYMENT_REPORT',         'Payment Report',               'FINANCIAL',   'payments.view', 250),
    ('OUTSTANDING_BALANCES',   'Outstanding Balances',         'FINANCIAL',   'financial.view', 260),
    ('BUDGET_VS_ACTUAL',       'Budget vs Actual',             'FINANCIAL',   'budgets.view', 270),
    ('PROFITABILITY',          'Profitability Analysis',       'FINANCIAL',   'financial.view', 280);

-- ---------------------------------------------------------------------
-- Default alert rules (SRS §31).  Thresholds read from system_config on
-- first evaluation; these rows make the alerts visible and editable.
-- ---------------------------------------------------------------------
INSERT INTO alert_rules (company_id, code, name, category, severity, comparison, threshold_value, threshold_unit, evaluation_window_days, notify_roles, channels, created_by)
SELECT c.id, v.code, v.name, v.category, v.severity, v.comparison, v.threshold, v.unit, v.window_days, v.roles, 'IN_APP,EMAIL', 'system'
FROM companies c, (VALUES
    ('NO_PRODUCTION',        'No production recorded',        'PRODUCTION', 'CRITICAL', 'NO_ACTIVITY_DAYS',   3::NUMERIC,   'days',    3, 'DIRECTOR,EXECUTIVE,PROJECT_MANAGER'),
    ('PRODUCTION_BELOW',     'Production below target',       'PRODUCTION', 'WARNING',  'PERCENT_BELOW',      80::NUMERIC,  'percent', 7, 'DIRECTOR,EXECUTIVE,PROJECT_MANAGER'),
    ('EXPENSE_OVER_BUDGET',  'Expenditure above budget',      'EXPENSE',    'WARNING',  'PERCENT_ABOVE',      100::NUMERIC, 'percent', 30,'DIRECTOR,EXECUTIVE,FINANCE'),
    ('CONTRACT_EXPIRING',    'Contract expiring',             'CONTRACT',   'WARNING',  'DAYS_BEFORE',        30::NUMERIC,  'days',    1, 'DIRECTOR,EXECUTIVE,PROJECT_MANAGER'),
    ('CONTRACT_EXPIRED',     'Contract expired',              'CONTRACT',   'CRITICAL', 'DAYS_BEFORE',        0::NUMERIC,   'days',    1, 'DIRECTOR,EXECUTIVE'),
    ('LOW_FUEL_STOCK',       'Low fuel stock',                'INVENTORY',  'WARNING',  'LESS_THAN',          NULL,         'litres',  1, 'STOREKEEPER,PROJECT_MANAGER'),
    ('LOW_EXPLOSIVE_STOCK',  'Low explosives stock',          'INVENTORY',  'CRITICAL', 'LESS_THAN',          NULL,         'units',   1, 'STOREKEEPER,PROJECT_MANAGER'),
    ('MAINTENANCE_DUE',      'Maintenance due',               'EQUIPMENT',  'WARNING',  'LESS_THAN',          50::NUMERIC,  'hours',   1, 'EQUIPMENT_MANAGER'),
    ('EXCESSIVE_DOWNTIME',   'Excessive equipment downtime',  'EQUIPMENT',  'WARNING',  'GREATER_THAN',       24::NUMERIC,  'hours',   7, 'EQUIPMENT_MANAGER,PROJECT_MANAGER'),
    ('MISSING_DAILY_REPORT', 'Missing daily report',          'REPORTING',  'WARNING',  'NO_ACTIVITY_DAYS',   1::NUMERIC,   'days',    1, 'PROJECT_MANAGER,SITE_MANAGER'),
    ('UNAPPROVED_EXPENSE',   'Expense awaiting approval',     'REPORTING',  'INFO',     'GREATER_THAN',       3::NUMERIC,   'days',    1, 'FINANCE,PROJECT_MANAGER'),
    ('SETTLEMENT_OVERDUE',   'Partner settlement outstanding','FINANCIAL',  'WARNING',  'GREATER_THAN',       30::NUMERIC,  'days',    1, 'FINANCE,EXECUTIVE')
) AS v(code, name, category, severity, comparison, threshold, unit, window_days, roles)
WHERE c.code = 'SAICOMEX';

-- ---------------------------------------------------------------------
-- Bootstrap administrator.
--
-- Password hash below is bcrypt for the DOCUMENTED LOCAL-DEV password in
-- docs/ENVIRONMENTS.md.  must_change_password is TRUE, so the first prod
-- login is forced to set a real one — no shipped credential survives
-- contact with production.  Rotate immediately after first sign-in.
-- ---------------------------------------------------------------------
INSERT INTO users (company_id, email, password_hash, first_name, last_name, role_id, status, must_change_password, created_by)
SELECT c.id,
       'admin@saicomex.com',
       '$2b$10$Q1MWSfcEYId2sHJPM2ZRk.GYDnhpDXHVYzflAUBivHZ6.sPSOMzE2',
       'System', 'Administrator',
       r.id, 'ACTIVE', TRUE, 'system'
FROM companies c, roles r
WHERE c.code = 'SAICOMEX' AND r.code = 'DIRECTOR';
