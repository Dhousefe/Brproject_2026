package br.project.cluster.hpc.sqlite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/** JDBC factory that injects SQLite HPC PRAGMAs on every connection. */
public final class SQLiteConnectionFactory {
    private final String jdbcUrl;
    private final SQLitePragmas pragmas;

    public SQLiteConnectionFactory(String jdbcUrl) {
        this(jdbcUrl, SQLitePragmas.DEFAULT);
    }

    public SQLiteConnectionFactory(String jdbcUrl, SQLitePragmas pragmas) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlite:")) {
            throw new IllegalArgumentException("Expected jdbc:sqlite: URL, got: " + jdbcUrl);
        }
        this.jdbcUrl = jdbcUrl;
        this.pragmas = pragmas == null ? SQLitePragmas.DEFAULT : pragmas;
    }

    public Connection openReadWrite() throws SQLException {
        final Connection connection = DriverManager.getConnection(jdbcUrl);
        applyPragmas(connection);
        connection.setAutoCommit(true);
        return connection;
    }

    public Connection openReadOnly() throws SQLException {
        final Properties props = new Properties();
        props.setProperty("open_mode", "1"); // SQLITE_OPEN_READONLY in xerial.
        final Connection connection = DriverManager.getConnection(jdbcUrl, props);
        applyPragmas(connection);
        connection.setReadOnly(true);
        connection.setAutoCommit(true);
        return connection;
    }

    private void applyPragmas(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : pragmas.toPragmaSql()) {
                statement.execute(sql);
            }
        }
    }
}
