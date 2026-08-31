# Stability Rules

Things that must not be changed casually, and why. Every rule here was
already worth writing down before it caused a production incident, not
after.

## Never change `ddl-auto` from `none`

`application.yml` and `application-prod.yml` both set
`spring.jpa.hibernate.ddl-auto: none`, with the comment: *"Flyway owns the
schema. Hibernate must never touch DDL — a stray ddl-auto on a financial
database is an unrecoverable afternoon."* Flyway (`V1`–`V7`) is the single
source of truth for schema shape. Setting `ddl-auto` to `update` or
`validate` — even temporarily, even locally, while debugging — risks
Hibernate inferring and applying a DDL change from the entity mappings
that Flyway never recorded, which desynchronises the two permanently.

## Never edit an applied Flyway migration — add a new one

`V1__core_and_security.sql` through `V7__reference_data.sql` are applied.
`spring.flyway.validate-on-migrate: true` (set in both profiles) means the
API refuses to start if an applied migration's checksum no longer matches
what Flyway recorded when it ran — so editing one doesn't just risk
drift, it breaks startup outright, everywhere that migration has already
run. To change something a past migration got wrong, write `V8__...sql`
(or the next free number) that alters, corrects, or backfills — never
touch the file that already ran.

## `MINIO_ENDPOINT` must be a public HTTPS URL, never a Docker hostname

`application-prod.yml`'s comment on this is direct: *"presigned URLs are
opened by the operator's browser, which cannot resolve `minio`. This is
the single most common way to break every upload, view and delete at
once."* `DocumentController`/`StorageService` generate presigned URLs
server-side, but the browser fetches them directly — if
`MINIO_ENDPOINT` is set to a container-network name (`http://minio:9000`),
every document link in the application breaks for every user, while the
API's own health checks keep passing, because the API can resolve that
hostname even though no browser ever will.

## Never delete rows from `audit_logs` or `settlement_calculations`

Both are insert-only by design. `audit_logs` is the SRS §39 audit trail —
*"Records should not be physically deleted where financial/audit
integrity would be affected."* `settlement_calculations` is the only
record of how a partner statement's numbers were actually derived: delete
a row and the statement above it is no longer explained, only asserted.
Neither table has a `deleted_at` column — that absence is intentional,
not an oversight to fix.

## Never edit an ACTIVE commercial agreement — amend by creating a new one

`commercial_agreements` enforces exactly one `ACTIVE` row per contract
(`uq_agreement_active_per_contract`, a partial unique index). A settlement
is computed against whichever agreement was `ACTIVE` on the settlement
period's end date (`SettlementService.resolve`, via `findEffectiveOn`),
and the resolved `agreement_id` is pinned onto the `settlements` row.
Editing an active agreement's rules in place changes what a *future*
recalculation of a *past* settlement would produce, silently — the
correct way to change terms is to supersede: create a new agreement
version effective from the change date, activate it, and leave the old
one (and every settlement computed against it) untouched. This mirrors
`contract_versions`: an amendment is a new row, not a mutation of the old
one.

## Do not change the group reporting currency once transactions exist

`companies.reporting_currency` and `system_config.group.reporting_currency`
both default to `USD`. Every stored `base_amount` across the schema
(`expenses`, `sales`, `payments`, `ledger_entries`, and every settlement
figure) was converted to the reporting currency **at the exchange rate in
effect when that row was written**, and is never retranslated later —
that is the entire point of freezing `exchange_rate` alongside
`base_amount` instead of computing it on read. Changing the reporting
currency after transactions exist does not retranslate history; every
`base_amount` written before the change stays denominated in the old
currency while everything after it is denominated in the new one, with
nothing in the schema distinguishing which is which. If the reporting
currency must genuinely change, that is a data migration project, not a
config edit.

## The cache name list in `CacheConfig` must contain every `@Cacheable` name

`CacheConfig.cacheManager()` builds a `ConcurrentMapCacheManager`
constructed with an explicit, fixed list of cache names (`userActive`,
`rolePermissions`, `systemConfig`, `exchangeRates`, `referenceData`).
That constructor form is deliberately **fixed-size**: asking it for a
cache name not in the list returns `null`, and a `@Cacheable` method
backed by a `null` cache throws at call time. The class comment is blunt
about the consequence: this "surfaces as a 403 on every authenticated
request if `userActive` is the one missing," because `JwtAuthFilter`'s
per-request account check depends on it. Adding a `@Cacheable` or
`@CacheEvict` annotation anywhere in the codebase means adding its cache
name to this list in the same change — it will not fail at compile time,
only at the first request that hits it.

## Nullable JPQL parameters need an explicit `CAST`

The "optional filter" pattern — `AND (:status IS NULL OR p.status =
:status)` — throws against PostgreSQL, because the driver cannot infer a
bare `:status` parameter's type from an `IS NULL` comparison alone. Every
repository in this codebase writes it as `AND (CAST(:status AS string) IS
NULL OR p.status = :status)` instead (see `ProjectRepository`,
`ShaftRepository`, `UserRepository`, `AlertRepository`, and others). This
already cost a real debugging cycle before the pattern was standardised —
a new "optional filter" JPQL method that omits the cast will compile
cleanly and fail at query-plan time on first use, not at build time.

## A settlement must be computed against the agreement effective in the period, never today's

Covered in full in `docs/COMMERCIAL_ENGINE.md`, but it belongs here too
because it is the rule most likely to be "simplified" away by someone
who doesn't know why it's there: `SettlementService.resolve()` resolves
the contract and agreement using the settlement's `periodEnd`
(`findActiveOn(shaftId, periodEnd)` /
`findEffectiveOn(contractId, periodEnd)`), never `LocalDate.now()`. A
contract amended in October must never silently re-price a September
settlement. Do not change these lookups to use "the current agreement" —
it corrupts every historical settlement the next time a contract is
amended.
