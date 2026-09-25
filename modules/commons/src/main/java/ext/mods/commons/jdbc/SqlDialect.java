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
@Deprecated(forRemoval = false)
public final class SqlDialect
{
    private SqlDialect() {}

    /**
     * Called once during ConnectionPool.init() to set the active database type.
     */
    public static void setActiveDatabase(SupportedDatabase db)
    {
        DatabaseDialect.setActiveDatabase(db);
    }

    public static SupportedDatabase getActiveDatabase()
    {
        return DatabaseDialect.getActiveDatabase();
    }

    public static boolean isSqlite()
    {
        return DatabaseDialect.isSqlite();
    }

    public static boolean isPostgresql()
    {
        return DatabaseDialect.isPostgresql();
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
        return DatabaseDialect.upsert(table, columns, values, conflictColumns, updateColumns);
    }

    /**
     * Adapt a legacy SQL statement for the active database.
     * SQLite support is kept for local compatibility; new code should use
     * {@link #upsert(String, String, String, String, String)} instead.
     */
    public static String adapt(String sql)
    {
        return DatabaseDialect.adapt(sql);
    }
}
