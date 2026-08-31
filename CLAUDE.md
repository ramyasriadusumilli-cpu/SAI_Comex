# CLAUDE.md

Orientation for an AI session working in this repository. Read this first;
it links to `docs/` for anything long enough to deserve its own file.

## What this is

SAIComex Mining Platform — integrated mining operations, production,
commercial-agreement and financial management system for SAIComex Mining
Company (small-to-mid-scale gold/chrome/alluvial mining, Zimbabwe). It
manages the group's hierarchy of projects, mining operations and shafts; the
partners who mine those shafts under contract; the commercial agreements
that decide how revenue and costs are split with those partners; and the
production, expense, sales, settlement and payment records that flow through
that structure.

It is built to run alongside the existing **SAI Fleet** system on the same
Hetzner VPS (`89.167.106.195`), in its own containers, on its own ports, on
its own subdomain. The two systems share a server and, in places, the same
operators — they do not share a database, a container network, or code.

## Stack

- **Frontend:** Angular 21 + PrimeNG (`saicomex-ui/`)
- **Backend:** Spring Boot 3.3, Java 21, Spring Security + JWT, Spring Data JPA (`saicomex-api/`)
- **Database:** PostgreSQL 16, schema owned entirely by Flyway
- **Object storage:** MinIO (documents, photos, attachments)

## Reference docs

| Doc | Read it when you need to |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Understand layers, the request path, the security model, the API surface at a glance |
| [`docs/ERD.md`](docs/ERD.md) | Understand the data model — all 64 tables, grouped by domain |
| [`docs/COMMERCIAL_ENGINE.md`](docs/COMMERCIAL_ENGINE.md) | Touch `CommercialCalculationEngine` or `SettlementService`, or explain a settlement figure |
| [`docs/ENVIRONMENTS.md`](docs/ENVIRONMENTS.md) | Run this locally, or connect to local/prod databases |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | Ship a change to the Hetzner server |
| [`docs/STABILITY_RULES.md`](docs/STABILITY_RULES.md) | Before touching schema, config, currency, or cache setup — read this first |
| [`docs/API.md`](docs/API.md) | Look up an endpoint, its permission, or its request/response shape |
| [`docs/SRS_COVERAGE.md`](docs/SRS_COVERAGE.md) | Check whether a requirement is built, partly built, or not built |

## Public URLs

| Environment | Frontend | API |
|---|---|---|
| Local dev | `http://localhost:4300` | `http://localhost:8080` |
| Production (planned) | `https://comex.saifleet.co.za` | `https://comex.saifleet.co.za/api` |

`comex.saifleet.co.za` is a **placeholder** — it has not been provisioned.
It is the one config value (DNS record, nginx `server_name`,
`CORS_ALLOWED_ORIGINS`, the Angular API base URL) that needs to change if
the real subdomain differs. See `docs/DEPLOYMENT.md`.

## Current state

This is **Phase 1 of a planned five-phase build.** Be honest about this —
do not describe unbuilt modules as available.

**Built and verified** (booted against a real PostgreSQL 16, all 25 UI
screens driven in a headless browser):

- Identity & access: users, roles, 161 permissions, project/shaft data
  scoping, JWT auth, password reset, MFA columns (schema only — no MFA flow
  wired up yet)
- Hierarchy: companies → projects → mining operations → shafts
- Partners and their banking details (redacted for callers without
  `partners.banking`)
- Contracts, contract versioning, commercial agreements, agreement rules
  (full CRUD, activation)
- **The commercial calculation engine** — the waterfall, cost-share rules,
  tiers, guard rails, minimum payment, capital recovery — with a test suite
  covering the SRS §25 worked example
- Settlements — preview, calculate, recalculate, approve, cancel, partner
  statement, full audit trail (`settlement_calculations`)
- Production records, corrections, verification/approval workflow
- Expenses, expense allocation across shafts, approval workflow
- Sales/revenue
- Payments
- Documents (MinIO-backed upload/download with presigned URLs)
- Alerts and notifications — **listing, acknowledge, resolve, and in-app
  notifications work; automatic threshold evaluation does not** (see Known
  issues)
- Audit trail (read-only query API)
- System configuration (typed key/value, editable via `settings.edit`)
- Executive dashboard with project/shaft drill-down

**Not built** (schema exists for some of these — see `docs/ERD.md` — but
there is no service, no controller, and no UI screen):

- Phase 2: Fuel transactions, explosives/controlled-item issuing, inventory
  & stores, purchase orders
- Phase 3: Equipment/asset register, equipment allocation history,
  maintenance records
- Phase 4: Budgets and budget-vs-actual, the financial ledger as a queried
  feature (the `ledger_entries` table exists; nothing writes to or reads
  from it yet), the report catalogue as a generator (`report_definitions`
  is seeded; there is no report engine behind it), daily/weekly site
  reports
- Phase 5: Mobile data capture and offline sync (`sync_batches` /
  `sync_conflicts` tables and the `client_uuid` idempotency columns exist
  throughout the schema in anticipation of this; no mobile client exists)

## Critical known issues and landmines

- **Alert rules are not evaluated.** `alert_rules` is seeded with real
  thresholds, `application.yml` has an `app.alerts.cron` schedule, and
  `AlertService.raise(...)` exists to create an alert — but nothing calls
  it. There is no `@Scheduled` job anywhere in the codebase. The Alerts
  screen will only ever show alerts someone inserts by hand. Do not assume
  alerts fire; do not remove the config values, either — they are the
  contract for the evaluator Phase 2+ is expected to add.
- **Every nullable JPQL parameter needs an explicit `CAST`.** See
  Conventions below — this cost a real debugging cycle before it was
  understood, and it is easy to reintroduce by copying a repository method
  without the cast.
- **`MINIO_ENDPOINT` must be a public HTTPS URL in production, never a
  Docker-internal hostname.** Presigned URLs are opened by the operator's
  browser, which cannot resolve a container name. See
  `docs/STABILITY_RULES.md`.
- **Group reporting currency is fixed at `USD` by convention, not by
  constraint.** `system_config.group.reporting_currency` and
  `companies.reporting_currency` can technically be edited once
  transactions exist, but every stored `base_amount` was translated at the
  old currency and will not be retranslated. Don't.
- **Ports for this system are non-standard by design** (see
  `docs/ENVIRONMENTS.md`) so they never collide with SAI Fleet's containers
  on the same host. Do not "fix" them back to 8080/5432/4200 in a compose
  file meant for the shared server.

## Conventions

These are deliberate departures from common Spring/JPA defaults, made for
reasons documented in the migration and entity source comments. Follow them.

- **`id` is always the surrogate key name.** Every table's primary key
  column is `id` (`BIGSERIAL`), full stop — never `<entity>_id`. This is a
  direct reaction to the SAI Fleet schema, where `vehicles.vehicle_id` as a
  PK means every foreign key referencing it has to remember a
  non-standard column name. Here every FK is simply `REFERENCES
  <table>(id)`.
- **Soft delete everywhere, with an explicit `deletedAt IS NULL` filter on
  every query — never a Hibernate `@Where`.** Every table that carries
  financial or audit weight has `deleted_at` / `deleted_by` columns; a row
  is never physically `DELETE`d. Filtering is done explicitly in every
  repository query (see `SoftDeletableEntity` and any `*Repository`
  interface) rather than through a class-level `@Where` annotation, because
  `@Where` is silently ignored by native queries and by `JOIN FETCH` on the
  inverse side of a relationship — exactly the two places a "deleted" row
  would quietly leak back into a financial total. If you add a repository
  method, add `AndDeletedAtIsNull` (or the equivalent explicit `WHERE`
  clause) yourself; there is no annotation doing it for you.
- **No `@ManyToOne` mappings anywhere.** Every association is a raw
  `Long` id column plus an explicit join in the repository query that needs
  it, not an object-graph traversal. Every list screen in this application
  is an aggregate over the project → operation → shaft hierarchy, and a
  graph of `@ManyToOne` associations turns that into an N+1 query storm.
  Joins that matter are written out in a `@Query` projection instead.
- **Every nullable parameter in a JPQL "optional filter" `IS NULL` test
  needs an explicit `CAST`.** The common "optional filter" pattern —
  `AND (:status IS NULL OR p.status = :status)` — fails against PostgreSQL
  because the driver cannot infer the parameter's type from a bare `IS
  NULL` comparison and throws at query-plan time. Every repository in this
  codebase writes it as `AND (CAST(:status AS string) IS NULL OR p.status =
  :status)` instead (see `ProjectRepository`, `ShaftRepository`,
  `UserRepository`, `AlertRepository`, and others). This is not
  stylistic — it is the fix for a real, previously-hit bug. Any new
  "optional filter" JPQL method must follow the same pattern or it will
  fail the same way.
- **Permissions live in the database, never in a Java `switch`.**
  `permissions`, `role_permissions`, and the 161 seeded permission rows
  (generated as `module.action` pairs, plus a handful of fine-grained ones
  like `settlements.calculate`) are the single source of truth for "may
  this role do this." `PermissionService.require(String permissionCode)`
  is the only gate; it reads `role_permissions` through a cached lookup
  (`rolePermissions` cache, keyed by role id) rather than encoding role
  logic in application code. Data *scoping* (which projects/shafts a
  user may see) is a second, separate layer on top of permission checks —
  see `PermissionService.requireShaftAccess` / `requireProjectAccess` — and
  the two must not be conflated: a role can hold `shafts.edit` and still be
  refused a specific shaft it is not assigned to.
- **Money is always three (or four) columns**, never a bare number:
  `<x>_amount` (transaction currency), `<x>_currency`, `<x>_exchange_rate`,
  `<x>_base_amount` (group reporting currency, at the rate actually used —
  never recomputed later). See `docs/ERD.md` and `docs/STABILITY_RULES.md`.
- **The commercial calculation engine (`CommercialCalculationEngine`)
  contains no percentages, no category names and no ordering assumptions.**
  Every number it uses comes from `agreement_rules` rows, resolved by
  `SettlementService` before the engine ever runs. Do not "simplify" a
  settlement bug by hard-coding a value into the engine — see
  `docs/COMMERCIAL_ENGINE.md`.
- **A settlement is computed against the agreement that was ACTIVE during
  its period, never the one active today.** `SettlementService.resolve()`
  calls `findActiveOn` / `findEffectiveOn` with the settlement's
  `periodEnd`, not `LocalDate.now()`. Do not "simplify" this to "the
  current agreement" — it silently re-prices history the next time a
  contract is amended.

## Design system

Tokens live in `saicomex-ui/src/styles.css` as CSS custom properties on
`:root`. Every component reads these variables; none defines its own hex
value. The palette is deliberately distinct from SAI Fleet's red — the two
systems share a server and, often, an operator's two open tabs.

```css
--bg:        #F5F5F3;   /* page background */
--card:      #FFFFFF;   /* card surfaces */
--ink:       #1E2124;   /* primary text */
--ink-soft:  #4A4F55;   /* secondary text */
--mut:       #8A8F96;   /* muted text, labels */
--line:      #E4E2DC;   /* borders and dividers */
--line-soft: #F0EEE9;

--brand:     #B87514;   /* primary — buttons, active nav, links */
--brand-d:   #8F5A0F;   /* hover / active */
--brand-l:   #FBF0DC;   /* tints, pills, hover backgrounds */
--brand-xl:  #FDF9F1;

--green:  #15803D;  --green-l:  #DCF5E5;
--amber:  #B45309;  --amber-l:  #FDEDD3;
--red:    #B91C1C;  --red-l:    #FBE2E2;
--blue:   #1D4ED8;  --blue-l:   #DEE7FD;
--violet: #6D28D9;  --violet-l: #EAE1FC;
--slate:  #475569;  --slate-l:  #E7EBF0;

--radius-card: 14px;
--radius-btn: 8px;
--radius-pill: 20px;

--font: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
--mono: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
```

Burnished gold on charcoal ("mining" without novelty), 13px base font size,
tabular numerals on every monetary/count value (`font-variant-numeric:
tabular-nums`). Light mode only — `color-scheme: light` is set explicitly;
there is no dark theme.

## Pending next steps, by phase

- **Phase 2 — Fuel, explosives & inventory.** Schema exists (V4). Needs:
  `FuelService`/`FuelController` wired to `fuel_transactions` and
  `inventory_transactions` together (a fuel issue is both), an
  `InventoryController` for stores/stock, a controlled-item issuing flow
  with permit reference for explosives, and UI screens under a new
  "Inventory" nav group.
- **Phase 3 — Equipment & maintenance.** Schema exists (V4). Needs:
  `EquipmentController`, `MaintenanceController`, allocation-history UI
  (`equipment_allocations` already models "current vs. historical
  placement" — use it, don't overwrite `equipment.shaft_id` in place).
- **Phase 4 — Budgets, ledger, reporting.** Schema exists (V5, V6). Needs:
  a `BudgetController` with budget-vs-actual computed from
  `ledger_entries` on read (the schema comment on `budget_lines` is
  explicit that actuals must never be cached), a service that actually
  writes `ledger_entries` from every revenue/expense/payment/settlement
  event, and a report engine reading `report_definitions` with PDF/Excel/CSV
  export (POI is already a dependency).
- **Phase 5 — Mobile & offline sync.** Schema exists (`client_uuid`
  columns throughout, `sync_batches`, `sync_conflicts`). Needs a mobile
  client and a batch-sync endpoint that resolves conflicts against those
  tables.
- **Cross-cutting, any phase:** wire up the alert evaluator (see Known
  issues above) — the config and the seed data are waiting for it.
