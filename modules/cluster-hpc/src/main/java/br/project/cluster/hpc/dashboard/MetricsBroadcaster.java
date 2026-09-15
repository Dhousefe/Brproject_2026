package br.project.cluster.hpc.dashboard;

import br.project.cluster.hpc.disruptor.DisruptorMetrics;

public final class MetricsBroadcaster {
    private final DisruptorMetrics metrics;
    public MetricsBroadcaster(DisruptorMetrics metrics) { this.metrics = metrics; }
    public String snapshotJson() {
        final Runtime rt = Runtime.getRuntime();
        final long used = rt.totalMemory() - rt.freeMemory();
        return "{\"disruptor.backlog\":" + metrics.backlog()
            + ",\"db.batch.last.ns\":" + metrics.lastBatchNanos()
            + ",\"db.queue.last.ns\":" + metrics.lastQueueDelayNanos()
            + ",\"jvm.heap.used.bytes\":" + used
            + ",\"jvm.heap.max.bytes\":" + rt.maxMemory() + "}";
    }
}
