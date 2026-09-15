package ext.mods.commons.jdbc;

/**
 * Supported database engines for BrProject.
 * Detected automatically from JDBC URL prefix.
 */
public enum SupportedDatabase
{
    SQLITE("jdbc:sqlite:", "org.sqlite.JDBC", "SQLite", "SELECT 1", 10, 5),
    MARIADB("jdbc:mariadb:", "org.mariadb.jdbc.Driver", "MariaDB", "SELECT 1", 50, 10),
    MYSQL("jdbc:mysql:", "com.mysql.cj.jdbc.Driver", "MySQL", "SELECT 1", 50, 10),
    POSTGRESQL("jdbc:postgresql:", "org.postgresql.Driver", "PostgreSQL", "SELECT 1", 50, 10),
    SQLSERVER("jdbc:sqlserver:", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "SQL Server", "SELECT 1", 50, 10);

    private final String _urlPrefix;
    private final String _driverClass;
    private final String _displayName;
    private final String _testQuery;
    private final int _defaultMaxPool;
    private final int _defaultMinIdle;

    SupportedDatabase(String urlPrefix, String driverClass, String displayName, String testQuery, int defaultMaxPool, int defaultMinIdle)
    {
        _urlPrefix = urlPrefix;
        _driverClass = driverClass;
        _displayName = displayName;
        _testQuery = testQuery;
        _defaultMaxPool = defaultMaxPool;
        _defaultMinIdle = defaultMinIdle;
    }

    public String getUrlPrefix() { return _urlPrefix; }
    public String getDriverClass() { return _driverClass; }
    public String getDisplayName() { return _displayName; }
    public String getTestQuery() { return _testQuery; }
    public int getDefaultMaxPool() { return _defaultMaxPool; }
    public int getDefaultMinIdle() { return _defaultMinIdle; }

    /**
     * Detect the database type from a JDBC URL.
     * @param jdbcUrl the JDBC connection URL
     * @return the matching SupportedDatabase, or SQLITE as fallback
     */
    public static SupportedDatabase fromUrl(String jdbcUrl)
    {
        if (jdbcUrl == null || jdbcUrl.isBlank())
            return SQLITE;

        final String lower = jdbcUrl.toLowerCase();
        for (SupportedDatabase db : values())
        {
            if (lower.startsWith(db._urlPrefix))
                return db;
        }
        return SQLITE;
    }

    /**
     * Check if the JDBC driver class is available on the classpath.
     */
    public boolean isDriverAvailable()
    {
        try
        {
            Class.forName(_driverClass);
            return true;
        }
        catch (ClassNotFoundException e)
        {
            return false;
        }
    }

    /**
     * Returns the default SQLite JDBC URL for embedded mode.
     */
    public static String getDefaultSqliteUrl()
    {
        return "jdbc:sqlite:data/brproject.db";
    }
}
