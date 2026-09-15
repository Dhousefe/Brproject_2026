# brproject-data

Versioned **schema + config examples** pack for BrProject (Phase 5).

Game XML/HTML under `game/data/` remains in the main tree (or a future split).  
This pack owns:

| Path | Purpose |
|------|---------|
| `VERSION` | SemVer of this data pack (must match release notes) |
| `migrations/` | Flyway SQL (`V*_*.sql`) |
| `config-examples/` | Safe templates (no secrets) |
| `SQL_MANIFEST.txt` | Ordered list of source files from `tools/sql` |

## Version policy

- Tag format: `data-vX.Y.Z` (optional) or ship as folder next to `server.jar` with same app version.
- Server reads DB via Flyway at migrate time; runtime does not require this folder after migrate.

## Apply schema

```bash
# From repo root (Gradle Flyway module)
./gradlew :db-migrate:flywayMigrate \
  -PdbUrl=jdbc:mariadb://localhost:3306/l2jdb \
  -PdbUser=brproject \
  -PdbPassword=brproject

# Or helper script
./tools/migrate-db.sh
```

## Sync examples into local runtime

```bash
./tools/sync-brproject-data.sh
```

Copies `config-examples` → `game/config/*.example` and `login/config/*.example` (never overwrites live secrets).

## Regenerate Flyway baseline from `tools/sql`

```bash
python3 tools/generate_flyway_migrations.py
```
