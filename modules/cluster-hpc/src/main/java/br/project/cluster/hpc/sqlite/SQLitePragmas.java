package br.project.cluster.hpc.sqlite;

/**
 * Record: SQLite pragmas for high-performance I/O — immutable configuration applied at connection init.
 *
 * WAL + NORMAL synchronous tolerates data loss up to ~50ms = 1 batch flush interval.
 * mmap_size=256MB enables zero-copy I/O on Linux/macOS (silently ignored on Windows).
 * busy_timeout=10000 allows login pings while writer is busy (no timeout errors).
 */
public record SQLitePragmas(
    String journalMode,            // WAL
    String synchronous,             // NORMAL
    long cacheSize,                 // -64000 (64MB)
    String tempStore,               // MEMORY
    long mmapSize,                  // 268_435_456 (256MB)
    long busyTimeout,               // 10_000 (10s)
    boolean foreignKeys,            // true
    String autoVacuum,              // INCREMENTAL
    long incrementalVacuum          // 1000 pages per checkpoint
) {
    public static final SQLitePragmas DEFAULT = new SQLitePragmas(
        "WAL",
        "NORMAL",
        -64_000L,
        "MEMORY",
        268_435_456L,
        10_000L,
        true,
        "INCREMENTAL",
        1_000L
    );

    /**
     * Apply all pragmas to a fresh SQLite connection.
     * This must run before any queries; otherwise WAL mode doesn't apply.
     */
    public String[] toPragmaSql() {
        return new String[]{
            "PRAGMA journal_mode=" + journalMode + ";",
            "PRAGMA synchronous=" + synchronous + ";",
            "PRAGMA cache_size=" + cacheSize + ";",
            "PRAGMA temp_store=" + tempStore + ";",
            "PRAGMA mmap_size=" + mmapSize + ";",
            "PRAGMA busy_timeout=" + busyTimeout + ";",
            "PRAGMA foreign_keys=" + (foreignKeys ? "ON" : "OFF") + ";",
            "PRAGMA auto_vacuum=" + autoVacuum + ";",
        };
    }
}
