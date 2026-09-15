package ext.mods.commons.jdbc;

/**
 * Runtime SQL dialect adapter.
 * Rewrites MySQL/MariaDB-specific syntax to SQLite-compatible equivalents
 * when running in SQLite mode. No-op for other databases.
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

    /**
     * Adapt a SQL statement for the active database.
     * For SQLite: rewrites ON DUPLICATE KEY UPDATE and TRUNCATE.
     * For MariaDB/MySQL/PostgreSQL: returns input unchanged.
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
