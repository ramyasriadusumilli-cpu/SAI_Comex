# Deployment

Target: the Hetzner VPS at `89.167.106.195`, alongside the existing SAI
Fleet system, in its own containers, on its own subdomain
(`comex.saifleet.co.za` — placeholder, see `docs/ENVIRONMENTS.md`).

## Prerequisites

- Docker + Docker Compose on the server (already present for SAI Fleet).
- nginx on the host, already terminating TLS for other subdomains on this
  server (or a container-based nginx — match whatever SAI Fleet already
  does, for consistency).
- A DNS record for `comex.saifleet.co.za` (or the real subdomain, once
  chosen) pointing at `89.167.106.195`.
- Java 21 and Node available wherever the build happens (CI, or the
  server itself, or a developer machine — this repo does not assume a
  particular build host).

## First-time server setup

1. **Create the deployment directory:**

   ```
   sudo mkdir -p /opt/saicomex
   cd /opt/saicomex
   ```

2. **Write a Docker Compose file** (`/opt/saicomex/docker-compose.yml`)
   defining four services, all bound to `127.0.0.1` only — nginx is the
   only thing that talks to the internet:

   - `saicomex-postgres` — `postgres:16-alpine`, `127.0.0.1:5434:5432`,
     database `saicomex`, user `comex_admin`, a named volume for data.
   - `saicomex-minio` — `minio/minio`, `127.0.0.1:9200:9000` (API) and
     `127.0.0.1:9201:9001` (console), a named volume for data, bucket
     `comex-files` created and set to allow public read under
     `/comex-files/` (the frontend and any shared photo/document link
     depend on this).
   - `saicomex-api` — built from `saicomex-api/`'s `Dockerfile` (or a
     plain `openjdk:21-jre` image running the built jar),
     `127.0.0.1:8090:8080`, environment from `docs/ENVIRONMENTS.md`
     (production profile — every value required, no defaults), depends on
     `saicomex-postgres` and `saicomex-minio`.
   - `saicomex-ui` — the built Angular output served by a plain
     `nginx:alpine` (or `nginx` on the host serving static files directly
     from a bind mount) on `127.0.0.1:8091`.

3. **Write the nginx site** for `comex.saifleet.co.za`: serve the SPA's
   static files (or reverse-proxy to `127.0.0.1:8091`) at `/`,
   reverse-proxy `/api/` to `127.0.0.1:8090`, and reverse-proxy
   `/comex-files/` to `127.0.0.1:9200` (MinIO) so presigned URLs generated
   by the API resolve to a real public path — see
   `docs/STABILITY_RULES.md` on why `MINIO_ENDPOINT` must be this public
   URL, not the container hostname.

4. **Obtain a TLS certificate** for the subdomain (`certbot --nginx -d
   comex.saifleet.co.za`, or however SAI Fleet's other subdomains already
   do it on this server — match the existing pattern rather than
   introducing a second one).

5. **Set every required environment variable** (`DB_PASSWORD`, `JWT_SECRET`,
   `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MAIL_USERNAME`/`MAIL_PASSWORD`
   if mail is wanted, `CORS_ALLOWED_ORIGINS=https://comex.saifleet.co.za`,
   `ADMIN_EMAIL`, `RESET_URL=https://comex.saifleet.co.za/reset-password`)
   in whatever secret store or `.env` file feeds the compose file —
   **never commit these values**. `application-prod.yml` has no fallback
   for any of them by design: a missing variable fails the container at
   startup rather than silently booting against `localhost` or a
   development secret.

6. **First start:** `docker compose up -d`. Flyway runs on API startup,
   applies all 7 migrations against the empty database, and seeds
   reference data — roles, 161 permissions, currencies, production units,
   contract types, agreement rule types, expense categories, approval
   thresholds, system config, the report catalogue, default alert rules,
   and the bootstrap `admin@saicomex.com` account
   (`must_change_password = true`).

## Routine deploy loop

```
# 1. Build the API jar
cd saicomex-api
./mvnw clean package -DskipTests   # or without -DskipTests if CI runs them

# 2. Build the UI
cd ../saicomex-ui
npm ci
npm run build

# 3. Copy to the server
scp saicomex-api/target/saicomex-api-*.jar  <user>@89.167.106.195:/opt/saicomex/api/
scp -r saicomex-ui/dist/saicomex-ui/*       <user>@89.167.106.195:/opt/saicomex/ui/

# 4. Restart
ssh <user>@89.167.106.195
cd /opt/saicomex
docker compose up -d --build saicomex-api saicomex-ui
```

(If images are built by CI and pushed to a registry instead of built
on-host, replace steps 3–4 with `docker compose pull && docker compose up
-d`. Either way, the sequence below applies.)

## Migration checklist

**Flyway applies pending migrations automatically when the API container
starts. There is no manual gate.** If the jar you are deploying contains a
new `V8__...sql` (or later) migration, restarting `saicomex-api` in
production applies it immediately, against production data, with no
confirmation step. Before deploying a jar with a new migration:

- [ ] The migration has been run against a copy of production data (or at
      minimum, local data seeded to a realistic size) — not only against
      an empty schema.
- [ ] The migration is additive or backward-compatible with the *currently
      running* jar for the duration of the rolling restart — the old jar's
      queries must not break the instant the new schema lands, because
      Flyway runs before the new jar's own code takes over.
- [ ] A recent database backup exists (see Rollback below) — Flyway does
      not create one for you.
- [ ] The migration does not modify or delete an already-applied migration
      file. Never edit `V1`–`V7` (or any later applied migration) — see
      `docs/STABILITY_RULES.md`. Fix a mistake with a new migration.
- [ ] `spring.flyway.validate-on-migrate: true` (set in both profiles)
      will refuse to start if the migration files on disk don't match what
      Flyway recorded as already applied — if startup fails on a checksum
      mismatch, something modified an applied migration file; find out
      what before doing anything else.

## Rollback

There is no automatic migration rollback — Flyway migrations in this
project are forward-only. To roll back a bad deploy:

1. **Application code only, no schema change:** redeploy the previous jar
   / UI build. Nothing schema-related to worry about.
2. **A bad migration was applied:** restoring the previous jar does *not*
   undo the migration — the schema stays changed. Either:
   - write and deploy a new forward migration that undoes the damage
     (preferred — keeps the migration history honest), or
   - restore the database from the pre-deploy backup and redeploy the
     previous jar together, as one operation (only when the migration is
     genuinely destructive and a forward fix isn't practical).
3. Never hand-edit Flyway's `flyway_schema_history` table to "unapply" a
   migration — that desynchronises what Flyway believes is true from what
   the schema actually contains, which is a worse problem than the one
   you started with.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| API container exits immediately on start | A required env var (`application-prod.yml` has no defaults) is missing — check `docker logs saicomex-api` for which `${VAR}` Spring failed to resolve. |
| `Flyway validate` fails on startup | An applied migration file was edited after the fact — see `docs/STABILITY_RULES.md`. Diff the file against what's in git history at the commit that was actually deployed. |
| Document/photo links return a broken image or a URL the browser can't reach | `MINIO_ENDPOINT` is set to the Docker-internal hostname instead of the public HTTPS URL — presigned URLs are opened by the operator's browser, which cannot resolve `saicomex-minio`. See `docs/STABILITY_RULES.md`. |
| CORS errors in the browser console | `CORS_ALLOWED_ORIGINS` does not match the exact origin the SPA is served from (scheme + host, no trailing slash). |
| Login works but every subsequent request is 401 with `X-Auth-Status: token-invalid` | Either the JWT was issued with a different `JWT_SECRET` than the API is currently running with (e.g. after a secret rotation without restarting all instances), or the account was disabled/deleted after the token was issued — `JwtAuthFilter` checks `UserCacheService.isActiveUser` on every request. |
| A settlement's numbers look wrong for a period in the past | Confirm which commercial agreement was `ACTIVE` on that period's `period_end`, not today's — see `docs/COMMERCIAL_ENGINE.md`. Re-running `POST /api/settlements/{id}/recalculate` on an already-`APPROVED` settlement is refused by design; create a new one instead. |
| Nightly alerts never appear | There is no alert evaluator wired up yet — this is a known gap, not a misconfiguration. See `docs/STABILITY_RULES.md` and `CLAUDE.md`. |
