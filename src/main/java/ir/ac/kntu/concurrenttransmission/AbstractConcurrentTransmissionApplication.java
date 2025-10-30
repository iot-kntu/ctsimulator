package ir.ac.kntu.concurrenttransmission;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Common support for concurrent transmission applications that need to track
 * network time and a set of node listeners.
 */
public abstract class AbstractConcurrentTransmissionApplication<L>
        implements ConcurrentTransmissionApplication {

    private final SortedMap<CtNode, L> listeners = new TreeMap<>();
    private CtNetworkTime networkTime;

    protected AbstractConcurrentTransmissionApplication() {
    }

    protected void updateNetworkTime(CtNetworkTime networkTime) {
        this.networkTime = Objects.requireNonNull(networkTime);
    }

    protected SortedMap<CtNode, L> listenersView() {
        return Collections.unmodifiableSortedMap(listeners);
    }

    protected L getListener(CtNode node) {
        L listener = listeners.get(node);
        if (listener == null) {
            throw new IllegalStateException("No listener defined for node " + node);
        }
        return listener;
    }

    public void setListener(CtNode node, L listener) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(listener);
        listeners.put(node, listener);
    }

    @Override
    public CtNetworkTime getNetworkTime() {
        return networkTime;
    }
}
