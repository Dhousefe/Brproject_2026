package ext.mods.commons.jdbc;

/**
 * Runtime SQL dialect adapter.
 *
 * <p>The legacy server contains a large amount of SQL written for
 * MySQL/MariaDB.  New persistence code must not duplicate that assumption.
 * This class is the small, dependency-free adapter used by repositories and
 * by the remaining legacy persistence paths to generate the appropriate
 * upsert statement for the configured database.</p>
 *
 * <p>Usage: {@code SqlDialect.adapt(sql)} at query execution sites,
 * or pre-adapt static constants at class-load time via {@code SqlDialect.adapt(QUERY)}.
 */
public final class SqlDialect
{
    private SqlDialect() {}

    private static volatile SupportedDatabase _activeDb = SupportedDatabase.MARIADB;

    /**
     * Called once during ConnectionPool.init() to set the active database type.
     */
    public static void setActiveDatabase(SupportedDatabase db)
    {
        _activeDb = db;
    }

    public static SupportedDatabase getActiveDatabase()
    {
        return _activeDb;
    }

    public static boolean isSqlite()
    {
        return _activeDb == SupportedDatabase.SQLITE;
    }

    public static boolean isPostgresql()
    {
        return _activeDb == SupportedDatabase.POSTGRESQL;
    }

    /**
     * Build an INSERT/UPDATE statement for a table with a known unique key.
     *
     * @param table table name
     * @param columns comma-separated insert columns
     * @param values JDBC placeholders for the insert values
     * @param conflictColumns comma-separated primary/unique key columns
     * @param updateColumns columns that must be replaced by the inserted value
     * @return SQL compatible with the active JDBC database
     */
    public static String upsert(String table, String columns, String values, String conflictColumns, String updateColumns)
    {
        final String insert = "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ")";
        final String[] updates = updateColumns.split(",");

        if (_activeDb == SupportedDatabase.POSTGRESQL)
        {
            final String assignments = java.util.Arrays.stream(updates)
                .map(String::trim)
                .filter(column -> !column.isEmpty())
                .map(column -> column + "=EXCLUDED." + column)
                .collect(java.util.stream.Collectors.joining(","));
            return insert + " ON CONFLICT (" + conflictColumns + ") DO UPDATE SET " + assignments;
        }

        if (_activeDb == SupportedDatabase.SQLITE)
            return insert.replaceFirst("^INSERT INTO", "INSERT OR REPLACE INTO");

        final String assignments = java.util.Arrays.stream(updates)
            .map(String::trim)
            .filter(column -> !column.isEmpty())
            .map(column -> column + "=VALUES(" + column + ")")
            .collect(java.util.stream.Collectors.joining(","));
        return insert + " ON DUPLICATE KEY UPDATE " + assignments;
    }

    /**
     * Adapt a legacy SQL statement for the active database.
     * SQLite support is kept for local compatibility; new code should use
     * {@link #upsert(String, String, String, String, String)} instead.
     */
    public static String adapt(String sql)
    {
        if (!isSqlite() || sql == null)
        {
            return sql;
        }
        if (sql.contains("ON DUPLICATE KEY UPDATE"))
        {
            return rewriteOnDuplicateKey(sql);
        }
        String trimmed = sql.stripLeading();
        if (trimmed.startsWith("TRUNCATE ") || trimmed.startsWith("TRUNCATE\t"))
        {
            return rewriteTruncate(trimmed);
        }
        return sql;
    }

    // ==================== TRUNCATE ====================

    /**
     * TRUNCATE tableName → DELETE FROM tableName
     */
    private static String rewriteTruncate(String sql)
    {
        String remainder = sql.substring("TRUNCATE".length()).trim();
        if (remainder.toUpperCase().startsWith("TABLE "))
        {
            remainder = remainder.substring("TABLE ".length()).trim();
        }
        return "DELETE FROM " + remainder;
    }

    // ==================== ON DUPLICATE KEY UPDATE ====================

    /**
     * Rewrite INSERT ... ON DUPLICATE KEY UPDATE col=VALUES(col), ...
     * to INSERT OR REPLACE INTO ... (SQLite compatible).
     *
     * Semantically equivalent when the table has a PRIMARY KEY or UNIQUE
     * constraint covering the conflict columns — which is the case for all
     * BrProject tables.
     */
    private static String rewriteOnDuplicateKey(String sql)
    {
        int odkIndex = sql.toUpperCase().indexOf("ON DUPLICATE KEY UPDATE");
        if (odkIndex < 0)
        {
            return sql;
        }

        String insertPart = sql.substring(0, odkIndex).trim();

        // Replace "INSERT INTO" with "INSERT OR REPLACE INTO"
        String upper = insertPart.toUpperCase();
        int insertIntoIdx = upper.indexOf("INSERT INTO");
        if (insertIntoIdx >= 0)
        {
            return insertPart.substring(0, insertIntoIdx)
                + "INSERT OR REPLACE INTO"
                + insertPart.substring(insertIntoIdx + "INSERT INTO".length());
        }

        // Fallback: just strip the ON DUPLICATE KEY part
        return insertPart;
    }
}
