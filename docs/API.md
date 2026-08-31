# API Reference

Base path: `/api`. All endpoints require a valid `Authorization: Bearer
<JWT>` header except the five marked **public** below. Every permission
check is enforced in the service layer (`PermissionService.require(...)`)
regardless of what the controller does — this table reflects what the
service actually checks, not just the controller's own annotations, which
carry none. Data-scoping checks (`requireProjectAccess` /
`requireShaftAccess`) are noted separately where they apply on top of the
listed permission — see `docs/ARCHITECTURE.md` for what scoping means.

Derived by reading every file in `saicomex-api/src/main/java/com/saicomex/controller/`.

## `/api/auth` — `AuthController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| POST | `/api/auth/login` | Authenticate, issue JWT | **public** | Body: email, password. Response includes the JWT and a permission-code snapshot used only for SPA nav visibility. |
| GET | `/api/auth/me` | Current user's profile | authenticated | |
| POST | `/api/auth/logout` | Revoke the caller's token | authenticated | Adds the token's `jti` to `revoked_tokens`. |
| POST | `/api/auth/change-password` | Change own password | authenticated | |
| POST | `/api/auth/forgot-password` | Request a reset email | **public** | Always returns the same 200 body whether or not the address exists — not an account-enumeration oracle. |
| POST | `/api/auth/reset-password` | Complete a reset | **public** | Requires a valid reset token. |

## `/api/projects` — `ProjectController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/projects` | Paged list, filter by status/type/search | `projects.view` | |
| GET | `/api/projects/options` | Unpaged list for dropdowns | `projects.view` | |
| GET | `/api/projects/{id}` | Detail | `projects.view` | + `requireProjectAccess` |
| POST | `/api/projects` | Create | `projects.create` | |
| PUT | `/api/projects/{id}` | Update | `projects.edit` | + `requireProjectAccess` |
| DELETE | `/api/projects/{id}?reason=` | Soft delete | `projects.delete` | |

## `/api/operations` — `MiningOperationController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/operations` | Paged list, filter by status/project/search | `operations.view` | |
| GET | `/api/operations/options?projectId=` | Unpaged list for dropdowns / hierarchy tree | `operations.view` | |
| GET | `/api/operations/{id}` | Detail | `operations.view` | + `requireProjectAccess` |
| POST | `/api/operations` | Create | `operations.create` | + `requireProjectAccess` on target project |
| PUT | `/api/operations/{id}` | Update | `operations.edit` | + `requireProjectAccess` |
| DELETE | `/api/operations/{id}?reason=` | Soft delete | `operations.delete` | + `requireProjectAccess` |

## `/api/shafts` — `ShaftController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/shafts` | Paged list, filter by status/project/operation/partner/search | `shafts.view` | |
| GET | `/api/shafts/options?projectId=&operationId=` | Unpaged list for dropdowns / hierarchy tree | `shafts.view` | |
| GET | `/api/shafts/{id}` | Detail | `shafts.view` | + `requireShaftAccess` |
| POST | `/api/shafts` | Create | `shafts.create` | + `requireProjectAccess` on parent project |
| PUT | `/api/shafts/{id}` | Update | `shafts.edit` | + `requireShaftAccess`, `requireProjectAccess` |
| PATCH | `/api/shafts/{id}/status` | Status transition (e.g. ACTIVE → SUSPENDED) | `shafts.edit` | + `requireShaftAccess` |
| DELETE | `/api/shafts/{id}?reason=` | Soft delete | `shafts.delete` | + `requireShaftAccess` |

## `/api/partners` — `PartnerController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/partners` | Paged list, filter by status/search | `partners.view` | Banking fields redacted without `partners.banking`. |
| GET | `/api/partners/options` | Unpaged list for dropdowns | `partners.view` | |
| GET | `/api/partners/{id}` | Detail | `partners.view` | Banking fields redacted without `partners.banking`. |
| POST | `/api/partners` | Create | `partners.create` | |
| PUT | `/api/partners/{id}` | Update | `partners.edit` | |
| DELETE | `/api/partners/{id}?reason=` | Soft delete | `partners.delete` | |

## `/api/contracts` — `ContractController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/contracts` | Paged list, filter by status/project/shaft/partner/type/search | `contracts.view` | |
| GET | `/api/contracts/expiring?days=30` | Contracts expiring within N days | `contracts.view` | Feeds the `CONTRACT_EXPIRING` alert rule's intent — see Known Issues in `CLAUDE.md` re: no automatic evaluator. |
| GET | `/api/contracts/{id}` | Detail | `contracts.view` | |
| GET | `/api/contracts/{id}/versions` | Version/amendment history | `contracts.view` | |
| POST | `/api/contracts` | Create (status `DRAFT`) | `contracts.create` | |
| PUT | `/api/contracts/{id}` | Update | `contracts.edit` | |
| POST | `/api/contracts/{id}/activate` | Move to `ACTIVE` | `contracts.approve` | Enforces one `ACTIVE` contract per shaft. |
| POST | `/api/contracts/{id}/terminate` | Move to `TERMINATED` | `contracts.approve` | Body: reason. |
| POST | `/api/contracts/{id}/amend` | Create a new version | `contracts.edit` | Amendment = new `contract_versions` row; old one preserved. |

## `/api/agreements` — `CommercialAgreementController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/agreements?contractId=` | List agreements for a contract | `agreements.view` | |
| GET | `/api/agreements/rule-types` | Rule type catalogue for the rule builder UI | `agreements.view` | |
| GET | `/api/agreements/{id}` | Detail, including rules and tiers | `agreements.view` | |
| POST | `/api/agreements` | Create (status `DRAFT`) | `agreements.create` | |
| PUT | `/api/agreements/{id}` | Update (rules, tiers, default split, rounding) | `agreements.edit` | |
| POST | `/api/agreements/{id}/activate` | Move to `ACTIVE` | `agreements.approve` | Enforces one `ACTIVE` agreement per contract. See `docs/STABILITY_RULES.md` — never edit an active one instead of superseding. |

## `/api/settlements` — `SettlementController`

See `docs/COMMERCIAL_ENGINE.md` for what each of these actually computes.

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/settlements` | Paged list, filter by status/project/shaft/partner/date range | `settlements.view` | |
| GET | `/api/settlements/{id}` | Detail — full waterfall + source lines | `settlements.view` | + `requireShaftAccess` |
| POST | `/api/settlements/preview` | Dry run — compute, write nothing | `settlements.calculate` | Body: shaftId, periodStart, periodEnd. |
| POST | `/api/settlements` | Compute and persist | `settlements.create` **and** `settlements.calculate` | Refused if the period overlaps an existing non-cancelled settlement for the shaft. |
| POST | `/api/settlements/{id}/recalculate` | Recompute in place | `settlements.calculate` | Only while `DRAFT`/`CALCULATED` — refused once `APPROVED`. |
| POST | `/api/settlements/{id}/approve` | Move to `APPROVED` | `settlements.approve` | |
| POST | `/api/settlements/{id}/cancel?reason=` | Move to `CANCELLED` | `settlements.edit` | Refused once payments exist against it. |
| GET | `/api/settlements/partner/{partnerId}/statement` | Partner's position across every shaft | `settlements.view` | Earned / paid / retained / outstanding, all non-cancelled settlements. |

## `/api/production` — `ProductionController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/production` | Paged list, filter by status/project/shaft/date range | `production.view` | |
| GET | `/api/production/{id}` | Detail | `production.view` | + `requireShaftAccess` |
| GET | `/api/production/shaft/{shaftId}` | Paged history for one shaft | `production.view` | + `requireShaftAccess` |
| POST | `/api/production` | Create (status `DRAFT`) | `production.create` | + `requireShaftAccess` |
| PUT | `/api/production/{id}` | Update | `production.edit` | + `requireShaftAccess` |
| POST | `/api/production/{id}/submit` | Move to `SUBMITTED` | `production.edit` | |
| POST | `/api/production/{id}/verify` | Move to `VERIFIED` | `production.verify` | |
| POST | `/api/production/{id}/approve` | Move to `APPROVED` | `production.approve` | |
| POST | `/api/production/{id}/correct` | Create a correcting record | `production.approve` | Never edits the original — a new row points back via `corrects_record_id`; the original moves to `CORRECTED`. |
| DELETE | `/api/production/{id}?reason=` | Soft delete | `production.delete` | |

## `/api/expenses` — `ExpenseController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/expenses` | Paged list, filter by status/project/shaft/category/date range/search | `expenses.view` | |
| GET | `/api/expenses/{id}` | Detail | `expenses.view` | |
| POST | `/api/expenses` | Create (status `DRAFT`) | `expenses.create` | + `requireShaftAccess`/`requireProjectAccess` |
| PUT | `/api/expenses/{id}` | Update | `expenses.edit` | + `requireShaftAccess`/`requireProjectAccess` |
| POST | `/api/expenses/{id}/submit` | Move to `SUBMITTED`/`PENDING_APPROVAL` | `expenses.edit` | Approval chain driven by `approval_thresholds`. |
| POST | `/api/expenses/{id}/approve` | Approve one step | `expenses.approve` | |
| POST | `/api/expenses/{id}/reject` | Reject with reason | `expenses.approve` | |
| DELETE | `/api/expenses/{id}?reason=` | Soft delete | `expenses.delete` | |

## `/api/sales` — `SaleController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/sales` | Paged list, filter by status/project/shaft/buyer/date range | `sales.view` | |
| GET | `/api/sales/{id}` | Detail | `sales.view` | |
| POST | `/api/sales` | Create (status `DRAFT`) | `sales.create` | + `requireShaftAccess`/`requireProjectAccess` |
| PUT | `/api/sales/{id}` | Update | `sales.edit` | |
| POST | `/api/sales/{id}/confirm` | Move to `CONFIRMED` | `sales.approve` | Confirmed sales are what `SettlementService` picks up for a period. |
| POST | `/api/sales/{id}/cancel?reason=` | Move to `CANCELLED` | `sales.approve` | |
| DELETE | `/api/sales/{id}?reason=` | Soft delete | `sales.delete` | |

## `/api/payments` — `PaymentController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/payments` | Paged list, filter by status/type/partner/project/shaft/date range | `payments.view` | |
| GET | `/api/payments/{id}` | Detail | `payments.view` | |
| POST | `/api/payments` | Create (status `DRAFT`) | `payments.create` | + `requireShaftAccess`/`requireProjectAccess` |
| PUT | `/api/payments/{id}` | Update | `payments.edit` | |
| POST | `/api/payments/{id}/approve` | Move to `APPROVED` | `payments.approve` | |
| POST | `/api/payments/{id}/mark-paid` | Move to `PAID` | `payments.approve` | Updates `settlements.amount_paid`/`amount_outstanding` when linked to a settlement. |
| DELETE | `/api/payments/{id}?reason=` | Soft delete | `payments.delete` | |

## `/api/documents` — `DocumentController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| POST | `/api/documents` (multipart) | Upload, attach to any entity | `documents.create` | Fields: entityType, entityId, documentType, title, description, expiryDate, file. Bytes go to MinIO; only the object key is stored. |
| GET | `/api/documents?entityType=&entityId=` | List for one entity | `documents.view` | |
| GET | `/api/documents/{id}/url` | Presigned download URL | `documents.view` | 60-minute expiry; must be a public MinIO URL in production — see `docs/STABILITY_RULES.md`. |
| DELETE | `/api/documents/{id}?reason=` | Soft delete | `documents.delete` | |

## `/api/dashboard` — `DashboardController`

One endpoint per drill-down level; SRS §45 makes the drill-down path
mandatory (total → project → shaft → category → transaction), and the
route names follow it literally.

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/dashboard/executive?from=&to=` | Level 1 — group KPIs | `dashboard.view` | |
| GET | `/api/dashboard/status-counts` | Status breakdown tiles | `dashboard.view` | |
| GET | `/api/dashboard/projects?from=&to=` | Level 2 — one row per project | `dashboard.view` | |
| GET | `/api/dashboard/shafts?projectId=&from=&to=` | Level 3 — one row per shaft; also the shaft comparison table | `dashboard.view` | |
| GET | `/api/dashboard/shafts/{shaftId}?from=&to=` | Single-shaft KPIs | `dashboard.view` | + `requireShaftAccess` |
| GET | `/api/dashboard/shafts/{shaftId}/expenses?from=&to=` | Level 4 — expense breakdown by category | `dashboard.view` | Level 5 (individual transactions) is `GET /api/expenses?shaftId=&categoryId=`, which already exists. |

## `/api/alerts` — `AlertController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/alerts` | Paged list, filter by status/severity/category/project/shaft | `alerts.view` | |
| GET | `/api/alerts/summary` | Open/acknowledged/resolved counts | `alerts.view` | |
| POST | `/api/alerts/{id}/acknowledge` | Acknowledge, with optional note | `alerts.acknowledge` | |
| POST | `/api/alerts/{id}/resolve` | Resolve, with optional note | `alerts.resolve` | |

## `/api/notifications` — `NotificationController`

Always the caller's own; no cross-user read exists.

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/notifications` | Paged list | authenticated only | |
| GET | `/api/notifications/unread-count` | Unread badge count | authenticated only | |
| POST | `/api/notifications/read-all` | Mark all read | authenticated only | |

## `/api/audit` — `AuditController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/audit` | Paged query, filter by action/entityType/userEmail/project/shaft/date range | `audit.view` | |
| GET | `/api/audit/entity/{entityType}/{entityId}` | History panel for one record | `audit.view` | |

## `/api/users` — `UserController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/users` | Paged list, filter by status/role/search | `users.view` | |
| GET | `/api/users/{id}` | Detail | `users.view` | |
| POST | `/api/users` | Create | `users.create` | Response includes a one-time generated credential path, not a password. |
| PUT | `/api/users/{id}` | Update | `users.edit` | |
| PATCH | `/api/users/{id}/status` | Status transition (ACTIVE/SUSPENDED/DISABLED) | `users.edit` | |
| POST | `/api/users/{id}/reset-password` | Admin-initiated reset | `users.edit` | |
| DELETE | `/api/users/{id}?reason=` | Soft delete | `users.delete` | |

## `/api/roles` — `RoleController`

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/roles` | List all roles | `roles.view` | |
| GET | `/api/roles/permissions` | Full permission catalogue, grouped by module | `roles.view` | Powers the permission-matrix UI. |
| GET | `/api/roles/{id}` | Detail, including granted permissions | `roles.view` | |
| POST | `/api/roles` | Create a custom role | `roles.create` | |
| PUT | `/api/roles/{id}` | Update name/description/permissions | `roles.edit` | Evicts the `rolePermissions` cache for that role. |
| DELETE | `/api/roles/{id}` | Delete | `roles.delete` | Refused for `is_system = true` roles. |

## `/api/settings` — `SystemConfigController`

The one controller where the permission check lives in the controller, not
the service — `SystemConfigService` is also called by startup/internal
code paths with no authenticated caller, so it cannot itself assume one.

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/settings` | All config, grouped by category | `settings.view` | |
| GET | `/api/settings/category/{category}` | Config for one category | `settings.view` | |
| PUT | `/api/settings/{key}` | Update one value | `settings.edit` | Refused if `is_editable = false` on that key. |

## `/api/reference` — `ReferenceDataController`

Nothing here is sensitive; any authenticated caller may read it.

| Method | Path | Purpose | Permission | Notes |
|---|---|---|---|---|
| GET | `/api/reference/all` | Everything the SPA needs at startup | authenticated only | Currencies, units, categories, contract types, etc. in one call. |
| GET | `/api/reference/currencies` | Currency list | authenticated only | |
| GET | `/api/reference/units` | Production unit list | authenticated only | |

## Not yet an endpoint

There is no controller for fuel, explosives, inventory/stores, purchase
orders, equipment, maintenance, employees, suppliers, budgets, the
financial ledger, daily reports, or reports — the underlying tables exist
(see `docs/ERD.md`) but nothing in `controller/` exposes them. See
`CLAUDE.md` → Current state, and `docs/SRS_COVERAGE.md`.
