# PostgreSQL in Docker — run `.sql` scripts

Use this when Postgres runs in a container (e.g. name **`postgres`**, image **`postgres:16-alpine`**) and you need to apply **`init.sql`**, seeds, or migrations from the repo.

**Default local credentials** (see `cloud/.env` / `docs/PORTS_AND_CREDENTIALS.md`):

| Setting   | Typical value        |
|-----------|----------------------|
| Host      | `127.0.0.1` (mapped) |
| Port      | `5432`               |
| Database  | `sudarshanchakra`    |
| User      | `scadmin`            |
| Password  | `devpassword123`     |

Adjust user/database/password if you created the container with different `-e` values.

---

## 1. Pipe a file from the host (simplest)

From the **SudarshanChakra repo root** on the host:

```bash
# Optional: avoid password prompt
export PGPASSWORD=devpassword123

docker exec -i postgres psql -U scadmin -d sudarshanchakra < cloud/db/seed_simulator_cameras_zones.sql
```

Or without `PGPASSWORD` (you will be prompted — may not work well in all shells; prefer env var):

```bash
docker exec -i postgres psql -U scadmin -d sudarshanchanchakra < cloud/db/seed_simulator_cameras_zones.sql
```

**Examples:**

| Script | Purpose |
|--------|---------|
| `cloud/db/seed_simulator_cameras_zones.sql` | Cameras + zones for farm simulator / REST alerts |
| `cloud/db/init.sql` | **Full schema** — only for empty DBs; do not blindly re-run on production (drops/conflicts). |

---

## 2. Copy file into the container, then `psql -f`

```bash
docker cp cloud/db/seed_simulator_cameras_zones.sql postgres:/tmp/seed.sql
docker exec postgres psql -U scadmin -d sudarshanchakra -f /tmp/seed.sql
```

---

## 3. `psql` on the host (if `postgresql-client` is installed)

Container maps **`127.0.0.1:5432`** → Postgres:

```bash
export PGPASSWORD=devpassword123
psql -h 127.0.0.1 -p 5432 -U scadmin -d sudarshanchakra -f cloud/db/seed_simulator_cameras_zones.sql
```

---

## 4. Interactive shell

```bash
docker exec -it postgres psql -U scadmin -d sudarshanchakra
```

Then run `\dt`, `SELECT …`, or paste SQL.

---

## Notes

- **First-time DB only:** If the container was created with `init` scripts mounted into `/docker-entrypoint-initdb.d/`, those run **once** when the data volume is empty. Changing `init.sql` later does **not** re-apply automatically — use `docker exec … psql < …` for updates (e.g. seeds).
- **Wrong container name:** List names with `docker ps` and replace `postgres` with your container name.
- **Permission errors:** Ensure the SQL file is readable and paths are correct relative to where you run the command.

---

## Quick check that DB is reachable

```bash
docker exec -i postgres psql -U scadmin -d sudarshanchakra -c "SELECT 1 AS ok;"
```
