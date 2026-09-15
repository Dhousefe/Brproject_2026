package br.project.cluster.hpc.disruptor;

import java.util.concurrent.atomic.LongAdder;

public final class DisruptorMetrics {
    private final LongAdder batches = new LongAdder();
    private final LongAdder events = new LongAdder();
    private volatile long lastBatchNanos;
    private volatile long lastQueueDelayNanos;
    private volatile long backlog;

    public void recordBatch(int eventCount, long batchNanos, long queueDelayNanos) {
        batches.increment();
        events.add(eventCount);
        lastBatchNanos = batchNanos;
        lastQueueDelayNanos = queueDelayNanos;
    }

    public void backlog(long backlog) { this.backlog = backlog; }
    public long batches() { return batches.sum(); }
    public long events() { return events.sum(); }
    public long lastBatchNanos() { return lastBatchNanos; }
    public long lastQueueDelayNanos() { return lastQueueDelayNanos; }
    public long backlog() { return backlog; }
}
