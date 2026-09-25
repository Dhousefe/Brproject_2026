# Database Migrations

Flyway migrations organized by database vendor.

## Structure

```
migrations/
├── mariadb/    ← MariaDB/MySQL legacy-compatible schema
├── postgresql/ ← PostgreSQL (official server database)
├── sqlite/     ← SQLite (zero-config, dev/demo)
└── README.md
```

## Usage

The migration path is selected automatically based on `sql.url` in your config:

- `jdbc:sqlite:...` → uses `sqlite/`
- `jdbc:mariadb:...` → uses `mariadb/`
- `jdbc:mysql:...` → uses `mariadb/`
- `jdbc:postgresql:...` → uses `postgresql/`
- `jdbc:sqlserver:...` → uses `mariadb/` (compatible)

Override with `--locations=filesystem:/custom/path` on the CLI or pass
`locations=...` via env/CLI to bypass auto-detection.

## Conventions

- Files: `V<version>__<description>.sql` (Flyway naming).
- Each vendor subtree must keep the same `V<version>` prefix so the Flyway
  `flyway_schema_history` table is interchangeable across vendors.
- SQLite-specific migrations must remain portable SQLite dialect (no
  `AUTO_INCREMENT`, `ENGINE=`, `CHARSET=`, `ON UPDATE CURRENT_TIMESTAMP`,
  `ENUM(...)` — see `sqlite/V1.0.0__baseline_schema.sql` for conversions).
- MariaDB-only hygiene migrations (V1.0.1 ENGINE conversion,
  V1.0.2 charset conversion) are present as `SELECT 1;` no-ops in `sqlite/`
  so version numbering stays aligned.
