package br.project.cluster.hpc.sqlite;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * One and only one writer connection. All writes must be routed through the Disruptor consumer.
 *
 * Uses explicit BEGIN IMMEDIATE / COMMIT instead of JDBC autoCommit=false. This avoids SQLite's
 * implicit deferred transactions and acquires the writer lock up-front.
 */
public final class SingleWriterHandle implements AutoCloseable {
    private final Connection connection;

    public SingleWriterHandle(SQLiteConnectionFactory factory) throws SQLException {
        this.connection = factory.openReadWrite();
        this.connection.setAutoCommit(true);
    }

    public Connection connection() { return connection; }

    public void beginImmediate() throws SQLException {
        connection.createStatement().execute("BEGIN IMMEDIATE TRANSACTION");
    }

    public void commit() throws SQLException {
        connection.createStatement().execute("COMMIT");
    }

    public void rollbackQuietly() {
        try { connection.createStatement().execute("ROLLBACK"); } catch (SQLException ignored) { }
    }

    public void checkpointAndVacuum() throws SQLException {
        connection.createStatement().execute("PRAGMA wal_checkpoint(PASSIVE)");
        connection.createStatement().execute("PRAGMA incremental_vacuum(1000)");
    }

    @Override public void close() throws SQLException { connection.close(); }
}
