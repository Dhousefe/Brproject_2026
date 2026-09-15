package br.project.cluster.hpc.disruptor;

/** Batch flush policy: size or elapsed time, whichever comes first. */
public record BatchPolicy(int maxEvents, long maxNanos) {
    public static BatchPolicy defaultPolicy() { return new BatchPolicy(256, 50_000_000L); }
    public boolean shouldFlush(int pending, long firstNanos, long now) {
        return pending >= maxEvents || (pending > 0 && now - firstNanos >= maxNanos);
    }
}
