# SRS Coverage — Traceability Matrix

Maps each numbered section of the source specification to what exists in
this codebase. Section titles below are not copied from a separate SRS
document — they are inferred from the `SRS §N` comments scattered through
the migrations and Java source, which is the only record of the
specification present in this repository. Where no comment anywhere in
`saicomex-api/` or `saicomex-ui/` references a section number, that is
stated explicitly rather than guessed at — see the note at the bottom.

Status legend: **Built** — has schema, service, controller and (where
applicable) a UI screen, and was exercised in the verification pass.
**Partially built** — schema exists but the application layer (service,
controller, or UI) is incomplete or entirely absent. **Not built** —
nothing exists yet, for any layer, beyond the section being named in a
comment.

| § | Title (inferred) | Status | Note | File / table |
|---|---|---|---|---|
| 1 | *(not referenced in code)* | Unknown | No comment anywhere names §1's scope. | — |
| 2 | Group company | Built | Single `companies` row, seeded. | `V1__core_and_security.sql` (`companies`), `V7` seed |
| 3 | Organisational hierarchy | Built | Company → project → operation → shaft, enforced by a trigger that a shaft's operation belongs to its project. | `V2__hierarchy_and_partners.sql` |
| 4 | *(not referenced in code)* | Unknown | No comment anywhere names §4's scope. | — |
| 5 | Executive dashboard | Built | Group KPIs, status counts. | `DashboardController.executive`, `DashboardService` |
| 6 | Projects | Built | Full CRUD, status lifecycle, data scoping. | `ProjectController`, `ProjectService`, `projects` table |
| 7 | Mining operations | Built | Full CRUD, optional layer under a project. | `MiningOperationController`, `mining_operations` table |
| 8 | Shafts | Built | Full CRUD, status transitions, the primary operational entity. | `ShaftController`, `shafts` table |
| 9 | Partners / shaft owners | Built | Full CRUD; banking fields gated behind `partners.banking`. | `PartnerController`, `partners` table |
| 10 | Contracts | Built | CRUD, versioning, activate/terminate/amend, one `ACTIVE` per shaft enforced by a partial unique index. | `ContractController`, `contracts`, `contract_versions` |
| 11 | Commercial agreements | Built | Rule-based agreement model — every split/deduction/fee is an `agreement_rules` row, never a code constant. | `CommercialAgreementController`, `commercial_agreements`, `agreement_rules`, `agreement_rule_tiers` |
| 12 | The calculation waterfall | Built | Gross → deductions → net distributable → allocation → adjustments → payable, with a full audit trail. See `docs/COMMERCIAL_ENGINE.md`. | `CommercialCalculationEngine`, tested against the §25 worked example |
| 13 | Production records | Built | Daily/shift capture, configurable units, target vs. variance. | `ProductionController`, `production_records` |
| 14 | Production corrections | Built | A correction is a new row (`corrects_record_id`); the original moves to `CORRECTED`, never edited in place. | `ProductionController.correct`, `production_records.corrects_record_id` |
| 15 | Expense categories & expenses | Built | Hierarchical, configurable categories; OPEX/CAPEX split. | `ExpenseController`, `expense_categories`, `expenses` |
| 16 | Configurable approval thresholds | Built | Amount-band → required-role chain, overridable per project. | `approval_thresholds` table, consumed by `ExpenseService` approval flow |
| 17 | Fuel | Partially built (Phase 2) | Schema only — `fuel_transactions` links a stock movement, an expense and fuel-specific detail (odometer, hours). No `FuelController`, no service, no UI. | `V4__operations.sql` (`fuel_transactions`) |
| 18 | Workforce & explosives (licence-controlled items) | Partially built (Phase 2) | `employees` and `inventory_items.is_controlled`/`requires_permit` exist in schema. No `EmployeeController`, no controlled-item issuing flow, no UI. | `V4__operations.sql` (`employees`, `inventory_items`) |
| 19 | Suppliers, inventory & stores | Partially built (Phase 2) | `suppliers`, `inventory_items`, `store_locations`, `inventory_balances`, `inventory_transactions`, `purchase_orders`/`purchase_order_lines` all exist. No controller, service, or UI for any of it. | `V4__operations.sql` |
| 20 | Equipment & assets | Partially built (Phase 3) | `equipment` table with ownership, placement, service-interval columns. No controller/service/UI. | `V4__operations.sql` (`equipment`) |
| 21 | Equipment allocation history | Partially built (Phase 3) | `equipment_allocations` models current-vs-historical placement (one open row per asset via `uq_equip_alloc_current`). No controller/service/UI. | `V4__operations.sql` (`equipment_allocations`) |
| 22 | Maintenance | Partially built (Phase 3) | `maintenance_records`/`maintenance_parts` exist. No controller/service/UI. | `V4__operations.sql` |
| 23 | Sales / revenue | Built | Full CRUD, confirm/cancel, deductions, settlement-status tracking. | `SaleController`, `sales`, `sale_deductions` |
| 24 | Financial ledger | Partially built (Phase 4) | `ledger_entries` table exists, designed as the single append-only source for "trace any number to its source." Nothing writes to it or reads from it yet — no revenue/expense/payment/settlement event creates a ledger row. | `V5__financials.sql` (`ledger_entries`) |
| 25 | Settlements | Built | Preview/calculate/recalculate/approve/cancel, partner statement, full step-by-step audit trail. Verified against the SRS §25 worked example in `CommercialCalculationEngineTest`. | `SettlementController`, `SettlementService`, `settlements`, `settlement_lines`, `settlement_calculations` |
| 26 | Budgets | Partially built (Phase 4) | `budgets`/`budget_lines` exist, with an explicit design note that actuals must be computed from `ledger_entries` on read, never cached. No controller/service/UI, and §24's ledger isn't written to yet either — budget-vs-actual cannot function until both exist. | `V5__financials.sql` (`budgets`, `budget_lines`) |
| 27 | Payments | Built | CRUD, approve, mark-paid; links to settlements, expenses, purchase orders. | `PaymentController`, `payments` |
| 28 | Report catalogue | Partially built (Phase 4) | `report_definitions` seeded with 27 report entries (group/project/shaft/operational/financial). No report-generation engine reads it — no controller produces a PDF/Excel/CSV for any of them, despite `poi-ooxml` already being a dependency for exactly this. | `V6__platform.sql` (`report_definitions`), `pom.xml` (POI dependency, unused) |
| 29 | Dashboard trend / KPI detail | Built | Part of the executive dashboard response. | `DashboardService.executive` |
| 30 | Shaft comparison table | Built | Side-by-side shaft performance. | `DashboardController.shafts`, `DashboardService.shaftPerformance` |
| 31 | Alert engine | Partially built | Rules, raising, listing, acknowledge/resolve, dedup by `dedupe_key`, and notification fan-out all work end to end **if something calls `AlertService.raise(...)`.** Nothing does — there is no `@Scheduled` evaluator reading `alert_rules` against live data, despite `app.alerts.cron` existing in `application.yml`. Alerts never fire on their own. | `AlertController`, `AlertService`, `alert_rules`, `alerts`; see `CLAUDE.md` Known issues |
| 32 | Daily / weekly site reports | Partially built (Phase 4) | `daily_reports` table (headcount, production, fuel/explosives used, incidents, activities) exists. No controller/service/UI. | `V6__platform.sql` (`daily_reports`) |
| 33 | Offline mobile sync | Not built (Phase 5) | `sync_batches`/`sync_conflicts` tables and `client_uuid` idempotency columns exist throughout the schema (`production_records`, `expenses`, `inventory_transactions`, `fuel_transactions`, `daily_reports`) in anticipation of this. No mobile client, no batch-sync endpoint. | `V6__platform.sql` (`sync_batches`, `sync_conflicts`) |
| 34 | Locations & GPS | Built | Shared location/boundary record used by projects, operations, shafts (and, once built, stores/equipment). | `V2__hierarchy_and_partners.sql` (`locations`) |
| 35 | Documents | Built | Polymorphic upload/list/presigned-download/delete against MinIO, any entity type. | `DocumentController`, `documents` table |
| 36 | Users | Built | CRUD, status lifecycle, admin password reset, project/shaft assignment. | `UserController`, `users`, `user_project_access`, `user_shaft_access` |
| 37 | Roles & permissions | Built | 11 seeded roles, 161 seeded permissions, role→permission grants editable via UI; permission checks read the database, never a code switch. | `RoleController`, `roles`, `permissions`, `role_permissions` |
| 38 | Authentication & MFA | Partially built | JWT auth, BCrypt passwords, lockout counters, rate limiting all work. `users.mfa_enabled`/`mfa_secret` columns exist; no MFA enrolment or challenge flow is implemented anywhere in `AuthController`/`AuthService`. | `SecurityConfig`, `JwtAuthFilter`, `AuthController`; `users.mfa_enabled` (unused) |
| 39 | Audit trail & soft delete | Built | Insert-only `audit_logs`; soft delete (`deleted_at`/`deleted_by`) enforced by explicit query filters everywhere, never a Hibernate `@Where`. | `AuditController`, `AuditService`, `audit_logs`, `SoftDeletableEntity` |
| 40 | Currencies & exchange rates | Built | Multi-currency amounts everywhere (amount/currency/exchange_rate/base_amount); rate table dated per pair. | `currencies`, `exchange_rates`; used throughout `expenses`, `sales`, `payments`, `settlements` |
| 41 | System / catalogue configuration | Built | Typed key/value config (editable via `settings.edit`), plus every "configurable, not an enum" catalogue this system relies on (contract types, agreement rule types, expense categories, approval thresholds). | `SystemConfigController`, `system_config`; `V3`/`V4`/`V7` catalogue tables |
| 42 | *(not referenced in code)* | Unknown | No comment anywhere names §42's scope. | — |
| 43 | REST API conventions | Built | Every domain controller follows the same thin-controller / permission-in-service pattern. | All of `controller/`; see `docs/API.md` |
| 44 | Application shell & navigation | Built | Permission-filtered sidebar, lazy-loaded routes matching server-side permission codes 1:1 (presentation only — the guard is not the security boundary). | `saicomex-ui/src/app/layout/shell.ts`, `app.routes.ts` |
| 45 | Dashboard drill-down | Built | Mandatory total → project → shaft → category path; level 5 (individual transaction) reuses the existing filtered expense list rather than a new endpoint. | `DashboardController`, `docs/ARCHITECTURE.md` |
| 46 | Notifications | Built | In-app notifications, unread count, mark-all-read; email/push columns exist on `notifications` but nothing in this codebase sends email or push — only in-app delivery is wired up. | `NotificationController`, `notifications` |
| 47 | *(not referenced in code)* | Unknown | No comment anywhere names §47's scope. | — |
| 48 | Weekly site reports | Partially built (Phase 4) | Same table as §32 (`daily_reports.period_type`-style distinction is implied by `shift`, not a separate weekly table); no controller/service/UI. | `V6__platform.sql` (`daily_reports`) |
| 49 | *(not referenced in code)* | Unknown | No comment anywhere names §49's scope. | — |
| 50 | *(not referenced in code)* | Unknown | No comment anywhere names §50's scope. | — |
| 51 | *(not referenced in code)* | Unknown | No comment anywhere names §51's scope. | — |
| 52 | *(not referenced in code)* | Unknown | No comment anywhere names §52's scope. | — |
| 53 | *(not referenced in code)* | Unknown | No comment anywhere names §53's scope. | — |
| 54 | *(not referenced in code)* | Unknown | No comment anywhere names §54's scope. | — |
| 55 | Audit trail visibility | Built | Explicitly not gated behind a config flag — always on for anyone holding `audit.view`. | `AuditQueryService` |
| 56 | *(not referenced in code)* | Unknown | No comment anywhere names §56's scope. | — |
| 57 | Traceability ("drill down to source") | Built | The governing principle behind `settlement_lines` (settlement → source sale/expense/production row), the dashboard drill-down, and every operational table carrying its full `project_id`/`mining_operation_id`/`shaft_id` lineage rather than only its immediate parent. | `settlement_lines`, `SettlementService.persistLines`, `V4__operations.sql` header comment |
| 58 | *(not referenced in code)* | Unknown | No comment anywhere names §58's scope. | — |
| 59 | *(not referenced in code)* | Unknown | No comment anywhere names §59's scope. | — |
| 60 | No hard-coded business rules | Built | The governing constraint on the entire commercial agreement model — see `docs/COMMERCIAL_ENGINE.md`. Not a feature with its own table; a design rule the schema and engine both honour. | `agreement_rules`, `CommercialCalculationEngine` |
| 61 | *(not referenced in code)* | Unknown | No comment anywhere names §61's scope. | — |

## On the "Unknown" rows

Fourteen section numbers (§1, §4, §42, §47, §49–§54, §56, §58, §59, §61) are
never named in any code comment, schema comment, or seed data across
`saicomex-api/` or `saicomex-ui/`. That does not necessarily mean nothing
in the codebase satisfies them — some may be non-functional requirements
(performance, browser support, deployment targets) that don't map to a
single file the way a data-entry screen does, and some may simply not have
been annotated by whoever wrote the corresponding code. It does mean this
matrix cannot respond for them without inventing a scope. Confirm against
the actual SRS document (not present in this repository) before reporting
these as built, partially built, or not built.

## Summary by phase

- **Phase 1 (this build):** identity & access, hierarchy, partners,
  contracts, commercial agreements, the calculation engine, settlements,
  production, expenses, sales, payments, documents, the executive
  dashboard, audit trail, currencies, system/catalogue configuration, the
  application shell. Alerts and notifications are built but not
  self-triggering (see §31). MFA columns exist but no MFA flow (see §38).
- **Phase 2 (not built):** fuel, explosives/controlled items, inventory &
  stores, purchase orders (§17–§19).
- **Phase 3 (not built):** equipment, equipment allocation history,
  maintenance (§20–§22).
- **Phase 4 (not built):** budgets, the financial ledger as an active
  feature, the report engine, daily/weekly site reports (§24, §26, §28,
  §32, §48).
- **Phase 5 (not built):** mobile capture and offline sync (§33).
