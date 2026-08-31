---
name: deploy-comex
description: Deploy the SAIComex Mining Platform to local or production. Operator-invoked only — runs when the operator types /deploy-comex. Encodes the Flyway auto-apply check, the port-collision guard against the co-hosted SAI Fleet stacks, and the settlement-integrity checks that must pass before a release touches financial data.
disable-model-invocation: true
---

# Deploy SAIComex

> **This skill never runs on Claude's initiative.** It executes only when the operator types
> `/deploy-comex`. Nothing here — not a build, not a restart, not a psql write — happens
> because a deploy "seemed like the next step".

Server: `89.167.106.195` (Hetzner, shared with the SAI Fleet stacks).
Key: `C:/Users/ramya/.ssh/hetzner_key`. App directory: `/opt/saicomex`.
JDK: `C:/Program Files/OpenLogic/jdk-21.0.10.7-hotspot`.

**This host runs three other applications.** `fleet-backend`, `fleet-backend-uat` and their
databases live on the same box. Nothing in this procedure may restart, rebuild or reconfigure
a container whose name does not start with `comex-`.

---

## Hard stop before any production write

Do not run the first remote command until all of these are true. State each one back to the
operator with its **actual value** — not "checked", but the number you saw.

1. **Explicit go-ahead in this conversation.** `/deploy-comex` starts the process; it is not
   authorization to finish it. Ask, and wait for the answer.

2. **You know which migrations will apply.** Flyway is enabled in production and runs on every
   backend start. There is no manual gate: deploying a jar that contains a new migration
   applies it. Report both numbers before proceeding:

   ```bash
   ls saicomex-api/src/main/resources/db/migration | tail -3
   ssh -i $KEY root@$HOST 'docker exec comex-postgres psql -U comex_admin -d saicomex -tAc \
     "SELECT MAX(version::int) FROM flyway_schema_history;"'
   ```

   Then say, in words, which versions will run on restart and what each one changes.

3. **A database backup exists from today.**

   ```bash
   ssh -i $KEY root@$HOST 'ls -la /opt/saicomex/backups | tail -5'
   ```

   If the newest dump is not from today, take one before continuing:

   ```bash
   ssh -i $KEY root@$HOST 'docker exec comex-db-backup /backup.sh'
   ```

4. **The engine tests pass.** The commercial calculation engine decides what every partner is
   owed. A release that changes it and has not been tested is not a release.

   ```bash
   cd saicomex-api && mvn -B test -Dtest=CommercialCalculationEngineTest
   ```

   The SRS §25 worked example is in that suite. If it fails, stop — the contract with the
   business has been broken, whatever else the change was meant to do.

5. **No approved settlement is about to change meaning.** If the release touches
   `CommercialCalculationEngine`, `SettlementService`, or any `agreement_*` table, say so
   explicitly and confirm with the operator. Approved settlements are financial records;
   changing how they would be computed does not change them retroactively, but it does mean
   a recalculation would now disagree with a statement already sent to a partner.

---

## Port guard

Before `docker compose up`, confirm nothing else has taken this stack's ports. A collision
does not fail loudly — the container simply refuses to start while the rest come up, and the
symptom is a half-working application.

```bash
ssh -i $KEY root@$HOST "ss -lntp | grep -E ':(8090|8091|5434|9200|9201)\b'"
```

Expected: only `comex-` containers, or nothing at all on a first deploy.

---

## Local

```bash
docker compose -f deploy/docker-compose.local.yml up -d
cd saicomex-api && mvn spring-boot:run          # http://localhost:8080
cd saicomex-ui  && npm start                    # http://localhost:4300
```

Check the startup banner: `StartupValidator` prints the company, role count, active user count
and reporting currency, and refuses to boot if any of them is missing. If it fails, the
database has not been seeded — do not "fix" it by disabling the validator.

---

## Production

Prefer the script; it does the whole loop and waits for health.

```powershell
.\deploy\deploy.ps1                 # both
.\deploy\deploy.ps1 -Target api     # backend only
.\deploy\deploy.ps1 -Target ui      # frontend only
```

Manual equivalent, if the script cannot run:

```bash
# 1. build
cd saicomex-api && mvn -B clean package -DskipTests
cd ../saicomex-ui && npm ci && npm run build -- --configuration production

# 2. ship and build the images on the server
scp -i $KEY -r saicomex-api/{pom.xml,Dockerfile,src} root@$HOST:/opt/saicomex/build/api/
ssh -i $KEY root@$HOST 'cd /opt/saicomex/build/api && docker build -t saicomex-api:latest .'

scp -i $KEY -r saicomex-ui/dist/saicomex-ui/browser root@$HOST:/opt/saicomex/build/ui/dist/
scp -i $KEY saicomex-ui/nginx.conf root@$HOST:/opt/saicomex/build/ui/
ssh -i $KEY root@$HOST 'cd /opt/saicomex/build/ui && docker build -t saicomex-ui:latest .'

# 3. restart — this is the point at which Flyway applies pending migrations
ssh -i $KEY root@$HOST 'cd /opt/saicomex && docker compose up -d'
```

---

## Verify, every time

Do not report a successful deploy until all four pass, and quote the output.

```bash
# 1. health
ssh -i $KEY root@$HOST 'curl -s http://127.0.0.1:8090/actuator/health'

# 2. migrations applied cleanly — no failed rows
ssh -i $KEY root@$HOST 'docker exec comex-postgres psql -U comex_admin -d saicomex -tAc \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"'

# 3. the SPA is served and is the new build
curl -sI https://comex.saifleet.co.za | head -3

# 4. settlement figures are unchanged where they should be
ssh -i $KEY root@$HOST 'docker exec comex-postgres psql -U comex_admin -d saicomex -tAc \
  "SELECT count(*), COALESCE(SUM(partner_net_payable),0) FROM settlements WHERE deleted_at IS NULL AND status = '"'"'APPROVED'"'"';"'
```

Record that last figure **before** the deploy as well. An approved settlement total that moves
across a release means something recalculated that should not have.

---

## Rollback

```bash
# revert to the previous image
ssh -i $KEY root@$HOST 'cd /opt/saicomex && docker compose down api && \
  docker tag saicomex-api:previous saicomex-api:latest && docker compose up -d api'
```

A schema migration does **not** roll back with the image. If the release applied one, restoring
the database from the pre-deploy dump is the only correct rollback, and it loses everything
recorded since. This is why step 3 of the hard stop exists.

---

## Never

- Restart, rebuild or edit any container not named `comex-*`.
- Edit an already-applied Flyway migration. Add a new one.
- Delete rows from `audit_logs`, `settlement_calculations` or `settlement_lines`.
- Paste a password into a file in this repository, including this one.
- Enable `SWAGGER_ENABLED` on production and leave it on.
