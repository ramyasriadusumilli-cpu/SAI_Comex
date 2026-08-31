# Entity Relationship Documentation

64 tables across 7 Flyway migrations (`V1__core_and_security.sql` through
`V7__reference_data.sql`; V7 is data-only — currencies, units, roles,
permissions, contract types, agreement rule types, expense categories,
approval thresholds, system config, report catalogue, alert rules, and the
bootstrap administrator).

## Conventions

- **Surrogate keys.** Every table's primary key is `id` (`BIGSERIAL`), never
  a domain-specific name. Every foreign key is `REFERENCES <table>(id)`.
- **Money** is stored as up to four columns per amount, never a bare
  number: `<x>_amount` (transaction currency), `<x>_currency` (`CHAR(3)`),
  `<x>_exchange_rate` (rate actually used, frozen at the time), and
  `<x>_base_amount` (converted to the group reporting currency at that
  frozen rate). A historical figure is therefore never affected by today's
  exchange rate.
- **Soft delete.** Tables that carry financial or audit weight have
  `deleted_at` / `deleted_by` instead of ever being physically `DELETE`d.
  Not every table has these columns — pure reference/lookup tables and
  append-only logs (e.g. `audit_logs`, `settlement_calculations`,
  `ledger_entries`) do not, because they are either never deleted or
  deletion is nonsensical for their role.
- **Audit columns.** Most tables carry `created_at` / `created_by` and
  `updated_at` / `updated_by`.

## Tables by domain

### Identity & access (10)

| Table | Purpose | Key FKs |
|---|---|---|
| `companies` | The group entity everything hangs off (one row: SAIComex) | — |
| `currencies` | Currency reference (USD, ZAR, ZWG seeded) | — |
| `exchange_rates` | Daily rate per currency pair, dated | `from_currency`/`to_currency → currencies` |
| `roles` | The 11 seeded roles (DIRECTOR … FIELD_OPERATOR) | — |
| `permissions` | One row per `module.action` (161 seeded) | — |
| `role_permissions` | Role → permission grants | `role_id → roles`, `permission_id → permissions` |
| `users` | Application users; MFA columns present but unused | `company_id → companies`, `role_id → roles` |
| `user_project_access` | Explicit project scoping for a user (empty = unrestricted) | `user_id → users`, `project_id → projects` |
| `user_shaft_access` | Explicit shaft scoping for a user (empty = unrestricted) | `user_id → users`, `shaft_id → shafts` |
| `revoked_tokens` | Logged-out / force-revoked JWTs, by `jti` | — |

### Hierarchy (4)

| Table | Purpose | Key FKs |
|---|---|---|
| `locations` | Shared GPS/site record used by projects, operations, shafts, stores, equipment | — |
| `projects` | Top operational level under the company (mine/exploration project) | `company_id → companies`, `location_id → locations`, `project_manager_id → users` |
| `mining_operations` | Grouping of shafts within a project (may be absent) | `project_id → projects`, `location_id → locations`, `manager_id → users` |
| `shafts` | The primary operational entity — every financial and production record ultimately traces to one | `project_id → projects`, `mining_operation_id → mining_operations`, `location_id → locations`, `owner_partner_id → partners`, `shaft_manager_id → users` |

### Partners & contracts (5)

| Table | Purpose | Key FKs |
|---|---|---|
| `partners` | Shaft owners / tribute partners; banking fields access-restricted | `company_id → companies` |
| `contract_types` | Configurable contract type catalogue (TRIBUTE, PROFIT_SHARE, …) | — |
| `contracts` | One shaft ↔ partner arrangement; exactly one `ACTIVE` per shaft (partial unique index) | `project_id → projects`, `mining_operation_id → mining_operations`, `shaft_id → shafts`, `partner_id → partners`, `contract_type_id → contract_types` |
| `contract_versions` | Amendment history; each amendment is a new version, old one preserved | `contract_id → contracts` |
| `approvals` | Generic approval-history log, reused across CONTRACT/AGREEMENT/EXPENSE/PAYMENT/PRODUCTION/SETTLEMENT | `entity_type` + `entity_id` (polymorphic, no FK constraint) |

### Commercial agreements (4)

| Table | Purpose | Key FKs |
|---|---|---|
| `commercial_agreements` | The header: settlement basis, default split, currency, rounding rule — one `ACTIVE` per contract | `contract_id → contracts`, `contract_version_id → contract_versions` |
| `agreement_rule_types` | Catalogue of every configurable rule type, mapped to its waterfall stage | — |
| `agreement_rules` | The configurable parameters themselves — percentages, deductions, caps, recovery. **No percentage exists anywhere outside this table.** | `agreement_id → commercial_agreements`, `rule_type → agreement_rule_types`, `expense_category_id → expense_categories` |
| `agreement_rule_tiers` | Banded splits for a `TIERED` rule ("first 500g at 70/30, above that 60/40") | `rule_id → agreement_rules` |

### Operations (21)

| Table | Purpose | Key FKs |
|---|---|---|
| `employees` | Workforce register (no controller/API yet — Phase 2+) | `company_id → companies`, `project_id/mining_operation_id/shaft_id`, `partner_id → partners`, `user_id → users` |
| `suppliers` | Supplier master (no controller/API yet) | `company_id → companies` |
| `production_units` | Configurable unit catalogue with conversion factor to a class base unit | — |
| `production_records` | Daily/shift production; corrections are new rows, never edits (`corrects_record_id`) | `project_id/mining_operation_id/shaft_id`, `contract_id → contracts`, `unit_code → production_units` |
| `production_batches` | Groups production into a saleable lot (smelt/pour/parcel) | `project_id → projects`, `shaft_id → shafts`, `unit_code → production_units` |
| `production_batch_lines` | Production records included in a batch | `batch_id → production_batches`, `production_record_id → production_records` |
| `expense_categories` | Hierarchical, configurable; OPEX/CAPEX split matters to the calculation engine | `parent_id → expense_categories` (self) |
| `expenses` | Operating/capital expenditure; may be shared across shafts | `project_id/mining_operation_id/shaft_id`, `category_id → expense_categories`, `supplier_id → suppliers` |
| `expense_allocations` | One row per shaft an expense is spread across — direct expenses get exactly one row too | `expense_id → expenses`, `project_id/mining_operation_id/shaft_id` |
| `approval_thresholds` | Configurable amount-band → required-role approval chain | `project_id → projects` (nullable = group default) |
| `inventory_items` | Item master covering fuel, explosives, consumables under one shape (no controller/API yet) | `category_id → expense_categories` |
| `store_locations` | Physical store/magazine/fuel-bay (no controller/API yet) | `project_id → projects`, `shaft_id → shafts`, `location_id → locations`, `keeper_user_id → users` |
| `inventory_balances` | Running stock position per item per store | `item_id → inventory_items`, `store_id → store_locations` |
| `inventory_transactions` | Every stock movement, signed quantity | `item_id → inventory_items`, `store_id → store_locations`, `equipment_id → equipment`, `recipient_employee_id → employees`, `supplier_id → suppliers`, `expense_id → expenses` |
| `purchase_orders` | PO header (no controller/API yet) | `supplier_id → suppliers`, `project_id/shaft_id`, `store_id → store_locations` |
| `purchase_order_lines` | PO line items | `purchase_order_id → purchase_orders`, `item_id → inventory_items` |
| `fuel_transactions` | Fuel-specific detail (vehicle, odometer, hours) layered on an inventory movement + expense (no controller/API yet) | `inventory_transaction_id → inventory_transactions`, `expense_id → expenses`, `item_id → inventory_items`, `equipment_id → equipment`, `supplier_id → suppliers` |
| `equipment` | Asset register (no controller/API yet) | `owner_partner_id → partners`, `supplier_id → suppliers`, `project_id/mining_operation_id/shaft_id`, `operator_employee_id → employees` |
| `equipment_allocations` | Historical placement of equipment across shafts, one open row (`to_date IS NULL`) per asset | `equipment_id → equipment`, `project_id/mining_operation_id/shaft_id`, `operator_employee_id → employees` |
| `maintenance_records` | Service/repair jobs (no controller/API yet) | `equipment_id → equipment`, `technician_employee_id → employees`, `supplier_id → suppliers`, `expense_id → expenses` |
| `maintenance_parts` | Parts consumed on a maintenance job | `maintenance_record_id → maintenance_records`, `item_id → inventory_items` |

### Financials (10)

| Table | Purpose | Key FKs |
|---|---|---|
| `buyers` | Gold buyers / refineries / offtake partners | `company_id → companies` |
| `sales` | Revenue transactions; carries settlement status once consumed by a settlement | `project_id/mining_operation_id/shaft_id`, `contract_id → contracts`, `batch_id → production_batches`, `buyer_id → buyers`, `settlement_id → settlements` |
| `sale_deductions` | Refining/transport/assay/royalty deductions on one sale | `sale_id → sales` |
| `settlements` | One partner, one shaft, one period, computed from the contract/agreement active in that period | `project_id/mining_operation_id/shaft_id`, `partner_id → partners`, `contract_id → contracts`, `agreement_id → commercial_agreements`, `contract_version_id → contract_versions` |
| `settlement_lines` | Source rows a settlement consumed — sales in, expenses out, production for reference | `settlement_id → settlements` |
| `settlement_calculations` | One row per waterfall step, in order, naming the rule that produced it — the audit trail | `settlement_id → settlements`, `rule_id → agreement_rules` |
| `payments` | Outbound payment to partner/supplier/employee/contractor | `partner_id/supplier_id/employee_id`, `project_id/mining_operation_id/shaft_id`, `settlement_id → settlements`, `expense_id → expenses`, `purchase_order_id → purchase_orders`, `category_id → expense_categories` |
| `ledger_entries` | Append-only financial ledger — every revenue/expense/payment/settlement writes one row here (table exists; nothing writes to it yet — Phase 4) | `project_id/mining_operation_id/shaft_id`, `partner_id → partners`, `contract_id → contracts`, `category_id → expense_categories`, `reversal_of_id → ledger_entries` (self) |
| `budgets` | Budget header at group/project/operation/shaft level (no controller/API yet) | `project_id/mining_operation_id/shaft_id` |
| `budget_lines` | Budgeted amount per category; actuals are computed from `ledger_entries` on read, never cached | `budget_id → budgets`, `category_id → expense_categories` |

### Platform (10)

| Table | Purpose | Key FKs |
|---|---|---|
| `documents` | Polymorphic attachment metadata; bytes live in MinIO | `entity_type` + `entity_id` (polymorphic), `uploaded_by_user_id → users`, `supersedes_id → documents` (self) |
| `daily_reports` | Daily/weekly site report (no controller/API yet) | `project_id/mining_operation_id/shaft_id`, `reported_by_user_id → users` |
| `alert_rules` | Configurable alert thresholds, scoped group-wide or to a project/shaft | `project_id → projects`, `shaft_id → shafts` |
| `alerts` | Raised alert instances; deduped by `dedupe_key` while `OPEN` | `alert_rule_id → alert_rules`, `project_id/mining_operation_id/shaft_id` |
| `notifications` | Per-user fan-out of an alert (in-app/email/push) | `user_id → users`, `alert_id → alerts` |
| `audit_logs` | Insert-only audit trail — who, what, when, old/new value, reason | `entity_type` + `entity_id` (polymorphic) |
| `sync_batches` | Offline mobile sync batch header (no mobile client yet — Phase 5) | `user_id → users` |
| `sync_conflicts` | Conflicts flagged from a sync batch, pending review | `batch_id → sync_batches` |
| `system_config` | Typed key/value configuration engine, editable via `settings.edit` | — |
| `report_definitions` | Report catalogue (seeded; no report engine reads it yet — Phase 4) | — |

## Core hierarchy

```
companies (1 row: SAIComex)
   │
   ▼
projects ──────────────────────────────► partners
   │                                          │
   ▼                                          │
mining_operations (optional)                  │
   │                                          │
   ▼                                          │
shafts ◄───────────────────────────────────────┘
   │  owner_partner_id (denormalised current owner)
   ▼
contracts ──► partner_id
   │  exactly one ACTIVE per shaft
   ▼
contract_versions
   │  each amendment = new version, old one preserved
   ▼
commercial_agreements
   │  exactly one ACTIVE per contract
   │  settlement_basis, default split, currency, rounding
   ▼
agreement_rules ──► agreement_rule_types (DEDUCTION | ALLOCATION | ADJUSTMENT)
   │
   ▼
agreement_rule_tiers (only for TIERED rules)
```

## Settlement path

```
   sales                expense_allocations         production_records
(revenue, one     (cost split across shafts,   (approved production for
 shaft/period)     one row per shaft even        the period, for total
     │              for a direct expense)         quantity and unit)
     │                      │                            │
     └──────────────┬───────┴──────────────┬─────────────┘
                     │                      │
                     ▼                      │
       CommercialCalculationEngine ◄─────────┘
       (fed by SettlementService.resolve():
        agreement + rules effective on
        the period, gross revenue, costs
        by category, total production)
                     │
                     ▼
                settlements
        (gross, deductions, net distributable,
         saicomex/partner share, adjustments,
         partner net payable, amount paid/
         outstanding, calculation_hash)
                     │
        ┌────────────┴─────────────┐
        ▼                          ▼
settlement_lines          settlement_calculations
(the source sales/       (one row per waterfall
 expense/production        step, in order —
 rows consumed, for        the audit trail)
 drill-down)
        │
        ▼
     payments
(partner payment against
 the settlement)
```
