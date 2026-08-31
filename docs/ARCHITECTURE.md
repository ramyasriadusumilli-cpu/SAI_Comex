# Architecture

## Layers

```
saicomex-ui/   Angular 21 SPA — standalone components, lazy-loaded routes,
               PrimeNG + Chart.js. Talks to the API over HTTPS/JSON only.

saicomex-api/  Spring Boot 3.3 (Java 21) REST API.
    controller/   Thin HTTP adapters: bind request → call one service
                  method → return DTO. No business logic, no permission
                  checks (SystemConfigController is the one deliberate
                  exception — see its class comment).
    service/      Business logic, transaction boundaries, permission and
                  scope checks (via PermissionService), orchestration of
                  repositories. This is where SRS requirements live.
    engine/       CommercialCalculationEngine — a pure function over
                  CalculationInput → CalculationResult. No repositories,
                  no Spring annotations beyond @Component. Testable
                  without a database (see CommercialCalculationEngineTest).
    entity/       JPA entities. Raw id columns for associations, never
                  @ManyToOne (see CLAUDE.md conventions).
    repository/   Spring Data JPA interfaces + @Query JPQL. Every query
                  filters deleted_at IS NULL explicitly.
    dto/          Request/response records, one file per feature area
                  (e.g. SettlementDtos, AgreementDtos).
    security/     JwtAuthFilter, JwtUtil, rate limiters.
    config/       SecurityConfig, CacheConfig, DemoDataLoader.
    common/       BaseEntity, SoftDeletableEntity, AuditContext.

    resources/db/migration/   Flyway migrations V1–V7. Own the schema
                               completely — Hibernate ddl-auto is `none`.
```

## Request path

```
Angular SPA
   │  HTTPS, Bearer JWT in Authorization header
   ▼
nginx (reverse proxy, TLS termination — production only)
   │
   ▼
GlobalRateLimitFilter → LoginRateLimitFilter → JwtAuthFilter
   │  (SecurityConfig filter chain, all before UsernamePasswordAuthenticationFilter)
   ▼
@RestController          bind request, delegate
   │
   ▼
@Service                 permissions.require("<module>.<action>")
   │                     permissions.requireProjectAccess / requireShaftAccess
   │                     business logic, transaction boundary
   ▼
@Repository (Spring Data JPA / JPQL)
   │
   ▼
PostgreSQL 16
```

Every authenticated request carries a JWT with the user's email, role, and a
snapshot of permission codes taken at login (used only to drive UI nav
visibility — see below). `JwtAuthFilter` validates the token, checks it is
not on the revocation list (`revoked_tokens` / `TokenBlacklistService`),
confirms the account is still active (`UserCacheService`, cached under the
`userActive` cache name), and installs a Spring Security `Authentication`
whose authorities are `ROLE_<CODE>` plus every permission code from the
token as a bare authority.

## Security model

Three independent layers, each enforced server-side:

1. **Authentication** — JWT, `HS`-family signing (`app.jwt.secret`),
   stateless (`SessionCreationPolicy.STATELESS`), no server-side session.
   `/api/auth/login`, `/forgot-password`, `/reset-password`, `/refresh` and
   `/actuator/health` are the only endpoints that do not require a token.
2. **Permission** — "may this role perform this action at all?" Read from
   `role_permissions` (161 seeded permission rows across 25 modules ×
   6 actions, plus 5 fine-grained permissions) via `PermissionService`,
   cached per role id (`rolePermissions` cache). The JWT's own permission
   claim is **not** trusted for authorization — it is a login-time snapshot
   used only to filter sidebar nav items in the SPA (`Shell.visibleGroups`).
   Every service method calls `permissions.require("<code>")` itself, so
   revoking a permission from a role takes effect on the caller's very next
   request, not their next login.
3. **Scope** — "may this particular user act on this particular project or
   shaft?" `DIRECTOR`, `ADMIN`, `EXECUTIVE`, `FINANCE` and `AUDITOR` are
   unscoped (group-wide visibility by role). Every other role is scoped by
   `user_project_access` / `user_shaft_access` rows — with the deliberate
   rule that a scoped user with **no** assignment rows yet sees everything
   ("empty means unrestricted"), so a newly created Project Manager is not
   staring at a blank application before anyone assigns them a project.
   `PermissionService.requireShaftAccess(shaftId, projectId, role)` checks
   shaft assignment first, then falls back to project assignment, before
   refusing.

Transport hardening (`SecurityConfig`): CSRF disabled (stateless JSON API,
no cookies), CORS restricted to `app.cors.allowed-origins`, HSTS
(1 year, includeSubDomains), `frame-ancestors 'none'` + `X-Frame-Options:
DENY`, a locked-down CSP (`default-src 'none'` — the API serves JSON only,
the SPA is a separate container), `Referrer-Policy: no-referrer`,
`Permissions-Policy` denying camera/microphone/geolocation. Passwords are
BCrypt, strength 10.

## Storage model

Object storage is MinIO, addressed through `StorageService`. Files never
transit the application server for download — `documents.storage_key`
records the MinIO object key, and `GET /api/documents/{id}/url` returns a
time-limited (60-minute) presigned URL the browser fetches directly from
MinIO. Uploads are polymorphic: `documents.entity_type` +
`documents.entity_id` attach a file to any record (project, contract,
expense, equipment, whatever) without a join table per entity type.
`checksum_sha256` and `version_no` / `supersedes_id` support integrity
checking and document versioning. See `docs/STABILITY_RULES.md` for why
`MINIO_ENDPOINT` must be a public URL in production.

## Audit model

`audit_logs` is an insert-only table (`AuditService`, running in
`REQUIRES_NEW` so an audit write survives even when the surrounding
business transaction later rolls back). Every write path that matters
records who, what, when, old value, new value, and a human-readable reason
where one was supplied — see `AuditQueryService` and `GET /api/audit`.
Separately, `settlement_calculations` is a purpose-built audit trail: one
row per step of the commercial waterfall, in execution order, naming the
agreement rule that produced it (see `docs/COMMERCIAL_ENGINE.md`). Neither
table is ever pruned or physically deleted from by application code — see
`docs/STABILITY_RULES.md`.

## Alert model

`alert_rules` holds configurable thresholds (comparison operator,
threshold value/unit, evaluation window, severity, who to notify, which
channels) scoped group-wide or to one project/shaft. `alerts` holds raised
instances; `alerts.dedupe_key` plus a partial unique index
(`status = 'OPEN'`) stops the same condition raising a duplicate open
alert. `AlertService.raise(...)` creates an alert and fans it out to
`notifications` for each relevant user (SRS §31/§46 treated as one
service — an alert and its notifications are the same event from two
angles).

**As built, nothing calls `raise()`.** There is no scheduled evaluator
reading `alert_rules` against live data — `app.alerts.cron` in
`application.yml` is unused configuration, staged for whichever phase adds
the evaluator. The Alerts screen, `AlertController`, acknowledge/resolve,
and the notification fan-out all work; only automatic triggering does not
exist yet.

## API surface

Every group below is a `@RestController` under `saicomex-api/.../controller/`.
Permission codes are enforced in the service layer (see `docs/API.md` for
the full per-endpoint table).

| Path prefix | Controller | What it does | Typical permission |
|---|---|---|---|
| `/api/auth` | `AuthController` | Login, logout, current user, password change/reset | none (public) / authenticated |
| `/api/projects` | `ProjectController` | Project CRUD, dropdown options | `projects.*` |
| `/api/operations` | `MiningOperationController` | Mining operation CRUD | `operations.*` |
| `/api/shafts` | `ShaftController` | Shaft CRUD, status transitions | `shafts.*` |
| `/api/partners` | `PartnerController` | Partner CRUD (banking fields redacted without `partners.banking`) | `partners.*` |
| `/api/contracts` | `ContractController` | Contract CRUD, versioning, activate/terminate/amend, expiring-soon list | `contracts.*` |
| `/api/agreements` | `CommercialAgreementController` | Commercial agreement + rule CRUD, activation, rule-type catalogue | `agreements.*` |
| `/api/settlements` | `SettlementController` | Preview/calculate/recalculate/approve/cancel a settlement, partner statement | `settlements.*` |
| `/api/production` | `ProductionController` | Production records, submit/verify/approve, corrections | `production.*` |
| `/api/expenses` | `ExpenseController` | Expense CRUD, submit/approve/reject | `expenses.*` |
| `/api/sales` | `SaleController` | Sale CRUD, confirm/cancel | `sales.*` |
| `/api/payments` | `PaymentController` | Payment CRUD, approve, mark-paid | `payments.*` |
| `/api/documents` | `DocumentController` | Upload/list/download-url/delete, any entity | `documents.*` |
| `/api/dashboard` | `DashboardController` | Executive KPIs, project/shaft drill-down, expense breakdown | `dashboard.view` |
| `/api/alerts` | `AlertController` | List, summary, acknowledge, resolve | `alerts.*` |
| `/api/notifications` | `NotificationController` | Current user's own notifications | authenticated only |
| `/api/audit` | `AuditController` | Audit log query, per-entity history | `audit.view` |
| `/api/users` | `UserController` | User CRUD, status, password reset | `users.*` |
| `/api/roles` | `RoleController` | Role CRUD, permission catalogue | `roles.*` |
| `/api/settings` | `SystemConfigController` | Typed config key/value read/update | `settings.*` |
| `/api/reference` | `ReferenceDataController` | Currencies, units, dropdown bootstrap | authenticated only |

## Component diagram

```
                              ┌───────────────────────────┐
                              │   Angular 21 SPA           │
                              │   saicomex-ui              │
                              │   (nginx-served static)    │
                              └──────────────┬─────────────┘
                                             │ HTTPS / JSON
                                             │ Bearer JWT
                              ┌──────────────▼─────────────┐
                              │   nginx (prod only)         │
                              │   TLS termination,          │
                              │   reverse proxy              │
                              └──────────────┬─────────────┘
                                             │
                              ┌──────────────▼─────────────┐
                              │  Spring Boot API            │
                              │  saicomex-api : 8080/8090   │
                              │                              │
                              │  filters: rate-limit, JWT    │
                              │  controller → service        │
                              │  → repository                │
                              │                              │
                              │  ┌────────────────────────┐  │
                              │  │ CommercialCalculation   │  │
                              │  │ Engine (pure function)  │  │
                              │  └────────────────────────┘  │
                              └───────┬───────────────┬──────┘
                                      │               │
                       JDBC (Flyway-owned schema)     │ S3-compatible API
                                      │               │ (presigned URLs)
                        ┌─────────────▼───────┐  ┌────▼─────────────┐
                        │  PostgreSQL 16       │  │  MinIO           │
                        │  db: saicomex        │  │  bucket:         │
                        │  64 tables, 7        │  │  comex-files     │
                        │  Flyway migrations   │  │                  │
                        └──────────────────────┘  └──────────────────┘
```
