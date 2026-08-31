# Environments

> **No password is ever committed to this repository — in this file, any
> other doc, `.env` files, or source.** Every connection string below shows
> a placeholder (`${DB_PASSWORD}` etc.) where a real deployment supplies a
> secret through its own environment. The seeded administrator account's
> password is never written down anywhere: `admin@saicomex.com` has
> `must_change_password = true`, so it is set at first login, by whoever
> logs in first, and known only to them.

## Local development

| Component | Value |
|---|---|
| Frontend | `http://localhost:4300` (`ng serve`) |
| API | `http://localhost:8080` |
| PostgreSQL | `localhost:5432`, database `saicomex` |
| MinIO API | `http://localhost:9200` |
| MinIO console | `http://localhost:9201` |

Local connection string (from `application.yml` defaults):

```
jdbc:postgresql://localhost:5432/saicomex
user:     comex_admin
password: ${DB_PASSWORD}
```

Local MinIO defaults (`application.yml`): access key `comexadmin`, bucket
`comex-files` — these are placeholder dev-only values baked into the
default profile, not secrets worth protecting; production overrides every
one of them with no fallback (see below).

## Production (Hetzner VPS, `89.167.106.195`)

Runs alongside the existing SAI Fleet system on the same host, in its own
containers, on ports bound to `127.0.0.1` only (never exposed directly —
nginx is the only thing that reaches the internet). This keeps SAIComex's
ports from ever colliding with SAI Fleet's containers on the same box.

| Component | Container (suggested name) | Port (127.0.0.1 only) | Public URL |
|---|---|---|---|
| Frontend | `saicomex-ui` | `8091` | `https://comex.saifleet.co.za` |
| API | `saicomex-api` | `8090` | `https://comex.saifleet.co.za/api` |
| PostgreSQL | `saicomex-postgres` | `5434` | — (internal only) |
| MinIO API | `saicomex-minio` | `9200` | `https://comex.saifleet.co.za/comex-files/` (via nginx) |
| MinIO console | `saicomex-minio` | `9201` | — (internal only; tunnel if needed) |

Database: name `saicomex`, user `comex_admin`.

`comex.saifleet.co.za` is a **placeholder subdomain** — it has not been
provisioned. It is the single config value (DNS record, nginx
`server_name`, `CORS_ALLOWED_ORIGINS`, the frontend's API base URL) to
change if the real subdomain differs. See `docs/DEPLOYMENT.md`.

Production connection string (from `application-prod.yml` — every value is
a bare `${VAR}` with **no default**, so a missing environment variable
fails startup instead of silently connecting to the wrong thing):

```
DB_URL=jdbc:postgresql://saicomex-postgres:5432/saicomex   # container-internal port stays 5432
DB_USER=comex_admin
DB_PASSWORD=${DB_PASSWORD}
```

MinIO in production (also no defaults):

```
MINIO_ENDPOINT=https://comex.saifleet.co.za/comex-files/   # PUBLIC URL — see docs/STABILITY_RULES.md
MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}
MINIO_SECRET_KEY=${MINIO_SECRET_KEY}
MINIO_BUCKET=comex-files
```

Note the host-side port (`5434` for Postgres, `9200`/`9201` for MinIO) is
what the Docker Compose file maps on `127.0.0.1`; the API's own
`DB_URL`/`MINIO_ENDPOINT` values above talk to the other containers by
their container-network name and the service's normal internal port
(`5432`, `9000`) — the two numbers are not the same thing, and that is
expected.

## Common commands

**Start local PostgreSQL** (adjust to how it is installed):

```
pg_ctl -D /usr/local/var/postgresql@16 start   # Homebrew
# or
sudo systemctl start postgresql@16-main         # Debian/Ubuntu
```

Create the local database once:

```
createdb saicomex
createuser comex_admin
```

**Run the API** (Flyway applies all 7 migrations and seeds reference data
automatically on first start):

```
cd saicomex-api
./mvnw spring-boot:run
```

**Run the UI:**

```
cd saicomex-ui
npm install
npm start
```

**Connect to the local database:**

```
psql -h localhost -p 5432 -U comex_admin -d saicomex
```

**Connect to the production database** (from the server, or through an SSH
tunnel — never expose 5434 beyond `127.0.0.1`):

```
ssh -L 5434:127.0.0.1:5434 <user>@89.167.106.195
psql -h localhost -p 5434 -U comex_admin -d saicomex
```

**View container logs (production):**

```
docker logs -f saicomex-api
docker logs -f saicomex-ui
docker logs -f saicomex-postgres
```

**Restart containers (production):**

```
cd /opt/saicomex
docker compose restart saicomex-api
docker compose restart saicomex-ui
```
