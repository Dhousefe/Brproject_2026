package ext.mods.commons.jdbc;

import ext.mods.commons.logging.CLogger;

/**
 * Resolves and validates the JDBC driver for the configured database.
 * Falls back to SQLite if no URL is configured or if the configured driver is unavailable.
 */
public final class DatabaseDriverResolver
{
    private static final CLogger LOGGER = new CLogger(DatabaseDriverResolver.class.getName());

    private DatabaseDriverResolver() {}

    /**
     * Resolve the database configuration from a JDBC URL.
     * If the URL is null/empty, defaults to SQLite.
     * If the detected driver is not on the classpath, falls back to SQLite.
     *
     * @param jdbcUrl the configured JDBC URL (nullable)
     * @param user the configured username (nullable for SQLite)
     * @param password the configured password (nullable for SQLite)
     * @return a resolved DatabaseConfig with validated driver
     */
    public static DatabaseConfig resolve(String jdbcUrl, String user, String password)
    {
        // No URL configured → SQLite
        if (jdbcUrl == null || jdbcUrl.isBlank())
        {
            LOGGER.info("No database URL configured. Using embedded SQLite (zero-config mode).");
            return createSqliteConfig();
        }

        final SupportedDatabase db = SupportedDatabase.fromUrl(jdbcUrl);

        // Check if driver is available
        if (!db.isDriverAvailable())
        {
            LOGGER.warn(db.getDisplayName() + " driver not found on classpath (" + db.getDriverClass() + "). Falling back to SQLite.");
            return createSqliteConfig();
        }

        LOGGER.info("Database detected: " + db.getDisplayName() + " (driver: " + db.getDriverClass() + ")");

        return new DatabaseConfig(
            db,
            jdbcUrl,
            user != null ? user : "",
            password != null ? password : "",
            db.getTestQuery(),
            db.getDefaultMaxPool(),
            db.getDefaultMinIdle()
        );
    }

    private static DatabaseConfig createSqliteConfig()
    {
        return new DatabaseConfig(
            SupportedDatabase.SQLITE,
            SupportedDatabase.getDefaultSqliteUrl(),
            "",
            "",
            SupportedDatabase.SQLITE.getTestQuery(),
            SupportedDatabase.SQLITE.getDefaultMaxPool(),
            SupportedDatabase.SQLITE.getDefaultMinIdle()
        );
    }

    /**
     * Immutable configuration result from driver resolution.
     */
    public record DatabaseConfig(
        SupportedDatabase database,
        String url,
        String user,
        String password,
        String testQuery,
        int maxPoolSize,
        int minIdle
    )
    {
        public boolean isSqlite()
        {
            return database == SupportedDatabase.SQLITE;
        }

        public boolean isEmbedded()
        {
            return isSqlite();
        }
    }
}
