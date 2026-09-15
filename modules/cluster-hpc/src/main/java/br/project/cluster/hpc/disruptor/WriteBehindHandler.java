package br.project.cluster.hpc.disruptor;

import java.sql.SQLException;
import java.util.concurrent.atomic.LongAdder;

import br.project.cluster.hpc.sqlite.SingleWriterHandle;
import com.lmax.disruptor.EventHandler;

/** Single-consumer write-behind handler: the only writer to SQLite. */
public final class WriteBehindHandler implements EventHandler<StateChangeEvent>, AutoCloseable {
    private final SingleWriterHandle writer;
    private final WriteBehindJournal journal;
    private final BatchPolicy policy;
    private final DisruptorMetrics metrics;
    private int pending;
    private long firstNanos;
    private final LongAdder failedBatches = new LongAdder();

    public WriteBehindHandler(SingleWriterHandle writer, BatchPolicy policy, DisruptorMetrics metrics) throws SQLException {
        this.writer = writer;
        this.journal = new WriteBehindJournal(writer.connection());
        this.policy = policy;
        this.metrics = metrics;
    }

    @Override public void onEvent(StateChangeEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (pending == 0) firstNanos = System.nanoTime();
        journal.add(event);
        pending++;
        event.reset();
        final long now = System.nanoTime();
        if (endOfBatch || policy.shouldFlush(pending, firstNanos, now)) flush(now);
    }

    public void flush(long nowNanos) throws SQLException {
        if (pending == 0) return;
        final long started = System.nanoTime();
        try {
            writer.beginImmediate();
            journal.executeBatches();
            writer.commit();
            metrics.recordBatch(pending, System.nanoTime() - started, nowNanos - firstNanos);
        } catch (SQLException ex) {
            failedBatches.increment();
            writer.rollbackQuietly();
            throw ex;
        } finally {
            pending = 0;
            firstNanos = 0L;
        }
    }

    public long failedBatches() { return failedBatches.sum(); }

    @Override public void close() throws SQLException {
        flush(System.nanoTime());
        journal.close();
    }
}
