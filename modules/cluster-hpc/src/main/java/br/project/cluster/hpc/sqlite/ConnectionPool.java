package br.project.cluster.hpc.sqlite;

import java.sql.SQLException;

/** Read pool + exclusive writer for SQLite WAL mode. */
public final class ConnectionPool implements AutoCloseable {
    private final ReadOnlyPool readOnlyPool;
    private final SingleWriterHandle singleWriter;

    public ConnectionPool(String jdbcUrl, int readPoolSize) throws SQLException {
        final SQLiteConnectionFactory factory = new SQLiteConnectionFactory(jdbcUrl);
        this.readOnlyPool = new ReadOnlyPool(factory, readPoolSize);
        this.singleWriter = new SingleWriterHandle(factory);
    }

    public ReadOnlyPool readOnlyPool() { return readOnlyPool; }
    public SingleWriterHandle singleWriter() { return singleWriter; }

    @Override public void close() throws SQLException {
        SQLException first = null;
        try { singleWriter.close(); } catch (SQLException e) { first = e; }
        try { readOnlyPool.close(); } catch (SQLException e) { if (first == null) first = e; else first.addSuppressed(e); }
        if (first != null) throw first;
    }
}
