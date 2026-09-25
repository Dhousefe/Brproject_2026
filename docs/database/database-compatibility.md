# Database compatibility boundary

The official local stack uses PostgreSQL. MariaDB remains a supported compatibility target so that changing database engine does not require changing game rules.

## Dialect boundary

`modules/commons/src/main/java/ext/mods/commons/jdbc/DatabaseDialect.java` is the canonical boundary for non-portable SQL operations such as upsert and SQLite replacement. `SqlDialect` remains as a deprecated compatibility facade while legacy callers are migrated.

New persistence code must not embed `ON DUPLICATE KEY UPDATE`, `REPLACE INTO`, or vendor-specific identifier quoting in game logic. It should use a repository and `DatabaseDialect.upsert(...)` where an upsert is required.

## Docker profiles

PostgreSQL is the default:

```powershell
docker compose --env-file .env -f deploy/docker/docker-compose.yml up -d --build
```

MariaDB uses the override profile and a separate named volume:

```powershell
docker compose --env-file .env `
  -f deploy/docker/docker-compose.yml `
  -f deploy/docker/docker-compose.mariadb.yml up -d --build
```

Set `MARIADB_PASSWORD` and `MARIADB_ROOT_PASSWORD` in `.env` before starting the MariaDB profile. The PostgreSQL and MariaDB profiles must not be started against the same database volume.

## Persistence contract test

The optional `DatabaseDialectPersistenceTest` performs a real insert/update/read cycle. It is skipped during ordinary unit builds unless `DB_TEST_URL` is provided:

```powershell
$env:DB_TEST_URL = 'jdbc:postgresql://localhost:5433/l2jdb'
$env:DB_TEST_USER = 'brproject'
$env:DB_TEST_PASSWORD = '...'
./gradlew.bat --no-daemon --no-parallel :commons:test --tests '*DatabaseDialectPersistenceTest'
```

The same test can run against MariaDB using `jdbc:mariadb://localhost:3307/l2jdb`.

## Current debt and protection

The legacy code still contains database-specific statements. The compatibility scan records them as existing debt and fails when a change introduces additional occurrences. Existing statements are migrated incrementally by domain, starting with account, character, skills, inventory, Premium and Seven Signs persistence.

Run the scan directly with:

```powershell
./gradlew.bat --no-daemon --no-parallel checkDatabaseSql
```

When a reviewed migration changes the legacy count, update the baseline explicitly with `-PupdateDatabaseSqlBaseline` in the same pull request. The baseline is a debt register, not a way to hide unreviewed SQL.
