package br.project.cluster.hpc.disruptor;

import com.lmax.disruptor.EventFactory;

public final class StateChangeEventFactory implements EventFactory<StateChangeEvent> {
    @Override public StateChangeEvent newInstance() { return new StateChangeEvent(); }
}
