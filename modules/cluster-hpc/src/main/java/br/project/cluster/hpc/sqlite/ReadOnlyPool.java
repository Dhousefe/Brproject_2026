package br.project.cluster.hpc.sqlite;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Fixed-size read-only connection pool. Intended size: 4-8. */
public final class ReadOnlyPool implements AutoCloseable {
    private final ArrayDeque<Connection> idle;
    private final Semaphore permits;

    public ReadOnlyPool(SQLiteConnectionFactory factory, int size) throws SQLException {
        if (size < 1 || size > 64) throw new IllegalArgumentException("Invalid read pool size: " + size);
        this.idle = new ArrayDeque<>(size);
        this.permits = new Semaphore(size, true);
        for (int i = 0; i < size; i++) idle.add(factory.openReadOnly());
    }

    public Lease acquire(long timeoutMillis) throws SQLException, InterruptedException {
        if (!permits.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
            throw new SQLException("Timed out waiting for SQLite read connection");
        }
        synchronized (idle) {
            return new Lease(this, Objects.requireNonNull(idle.removeFirst()));
        }
    }

    private void release(Connection connection) {
        synchronized (idle) { idle.addLast(connection); }
        permits.release();
    }

    @Override public void close() throws SQLException {
        SQLException first = null;
        synchronized (idle) {
            while (!idle.isEmpty()) {
                try { idle.removeFirst().close(); }
                catch (SQLException e) { if (first == null) first = e; else first.addSuppressed(e); }
            }
        }
        if (first != null) throw first;
    }

    public static final class Lease implements AutoCloseable {
        private final ReadOnlyPool pool;
        private Connection connection;
        private Lease(ReadOnlyPool pool, Connection connection) { this.pool = pool; this.connection = connection; }
        public Connection connection() { return connection; }
        @Override public void close() {
            final Connection c = connection;
            if (c != null) { connection = null; pool.release(c); }
        }
    }
}
