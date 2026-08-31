# SAIComex Mining Platform

Integrated mining operations, production, commercial and financial management
system for SAIComex Mining Company. Built to run alongside the existing SAI
Fleet system on the same Hetzner VPS, in its own containers, under its own
subdomain.

## Stack

- **Frontend:** Angular 21, PrimeNG, Chart.js (`saicomex-ui/`)
- **Backend:** Spring Boot 3.3 / Java 21, Spring Security + JWT, Spring Data JPA (`saicomex-api/`)
- **Database:** PostgreSQL 16, schema managed by Flyway (7 migrations, 64 tables)
- **Object storage:** MinIO (documents, photos)

## Status

Phase 1 of 5: foundation (identity, hierarchy, partners, contracts), the
commercial calculation engine, settlements, and the executive dashboard are
built and verified. Fuel, explosives, inventory, equipment, maintenance,
budgets, mobile capture and offline sync are **not** built — see
[`CLAUDE.md`](CLAUDE.md) for the full breakdown.

## Run it locally

1. Start PostgreSQL 16 locally, listening on `5432`, with a database named
   `saicomex` and a user able to connect to it (see
   [`docs/ENVIRONMENTS.md`](docs/ENVIRONMENTS.md) for the exact connection
   string).
2. `cd saicomex-api && ./mvnw spring-boot:run` — Flyway applies all 7
   migrations and seeds reference data (roles, permissions, currencies,
   units, the `admin@saicomex.com` bootstrap account) on first start. API on
   `http://localhost:8080`.
3. `cd saicomex-ui && npm install`
4. `npm start` (`ng serve`) — UI on `http://localhost:4300`.
5. Sign in as `admin@saicomex.com`. The password is set at first login — the
   seeded account has `must_change_password = true`, so the first sign-in
   forces you to choose one. No password is documented anywhere in this
   repository.

## Documentation

| Doc | Covers |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Orientation for an AI session: state, conventions, landmines |
| [`AGENTS.md`](AGENTS.md) | Pointer to `CLAUDE.md` for agent tooling that reads `AGENTS.md` |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Layers, request path, security model, API surface |
| [`docs/ERD.md`](docs/ERD.md) | All 64 tables by domain, key relationships |
| [`docs/COMMERCIAL_ENGINE.md`](docs/COMMERCIAL_ENGINE.md) | How the calculation waterfall works, worked examples |
| [`docs/ENVIRONMENTS.md`](docs/ENVIRONMENTS.md) | Local/production ports, URLs, connection strings, commands |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | Deploying to the Hetzner server |
| [`docs/STABILITY_RULES.md`](docs/STABILITY_RULES.md) | Things that must never be changed casually, and why |
| [`docs/API.md`](docs/API.md) | Endpoint reference by controller |
| [`docs/SRS_COVERAGE.md`](docs/SRS_COVERAGE.md) | Spec-to-code traceability matrix |
