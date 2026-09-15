package br.project.cluster.hpc.disruptor;

import com.lmax.disruptor.RingBuffer;

/** Lock-free producer used by game-loop threads. */
public final class StateChangeEventProducer {
    private final RingBuffer<StateChangeEvent> ringBuffer;
    private final DisruptorMetrics metrics;

    public StateChangeEventProducer(RingBuffer<StateChangeEvent> ringBuffer, DisruptorMetrics metrics) {
        this.ringBuffer = ringBuffer;
        this.metrics = metrics;
    }

    public void publishPosition(int objectId, int x, int y, int z, int heading, long nowMillis) {
        final long sequence = ringBuffer.next();
        try {
            final StateChangeEvent e = ringBuffer.get(sequence);
            e.type = StateChangeType.PLAYER_POSITION;
            e.objectId = objectId;
            e.intA = x; e.intB = y; e.intC = z;
            e.longA = heading;
            e.longB = nowMillis;
            e.publishedNanos = System.nanoTime();
        } finally {
            ringBuffer.publish(sequence);
            metrics.backlog(ringBuffer.getBufferSize() - ringBuffer.remainingCapacity());
        }
    }
}
