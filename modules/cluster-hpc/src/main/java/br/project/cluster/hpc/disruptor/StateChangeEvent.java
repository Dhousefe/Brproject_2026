package br.project.cluster.hpc.disruptor;

/**
 * Disruptor event. Padding prevents false sharing between producer and consumer cache lines.
 * No object allocations happen in the game loop: producer copies primitive fields only.
 */
public final class StateChangeEvent {
    @SuppressWarnings("unused") private long p01, p02, p03, p04, p05, p06, p07, p08;

    public StateChangeType type;
    public int objectId;
    public int ownerId;
    public int intA;
    public int intB;
    public int intC;
    public long longA;
    public long longB;
    public long publishedNanos;

    @SuppressWarnings("unused") private long q01, q02, q03, q04, q05, q06, q07, q08;

    public void reset() {
        type = null;
        objectId = ownerId = intA = intB = intC = 0;
        longA = longB = publishedNanos = 0L;
    }
}
