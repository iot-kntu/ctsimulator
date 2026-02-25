package ir.ac.kntu.concurrenttransmission.blueflood.nodes;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.CtBlueFloodApplication;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

import java.util.List;
import java.util.Objects;

/**
 * A default implementation of a CtNode which behaves loyally
 * which means this CtNode acts non-faulty according to the given
 * TransmissionPolicy
 */
public class LoyalCtNode implements CtNode {

    private final int id;

    public LoyalCtNode(Integer id) {
        this.id = id;
    }

    @Override
    public void initiateFlood(ContextView context, CtNode initiatorNode) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(initiatorNode);

        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();
        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(initiatorNode);
        int round = resolveRound(context);
        MetricsCollector metrics = resolveMetrics(context);

        final CtMessage<?> ctMessage = ((CtBlueFloodApplication) context
                .getApplication())
                .getRoundInitiationMessage(context, initiatorNode, 0);

        if (ctMessage.isNull())
            return;

        for (CtNode node : neighbors) {
            for (int repeat = 0; repeat < floodRepeatCount; repeat++) {
                if (metrics != null) {
                    metrics.recordSendAttempt(round, initiatorNode.getId(), node.getId());
                }
                final FloodPacket<?> stFloodPacket = new FloodPacket<>(context.getTime() + repeat, ctMessage,
                        initiatorNode, node);
                context.getSimulator().schedulePacket(stFloodPacket);
            }
        }
    }

    @Override
    public <T> void floodMessage(ContextView context, long delay, CtNode sender, CtMessage<T> message) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(sender);
        Objects.requireNonNull(message);

        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();
        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(sender);
        int round = resolveRound(context);
        MetricsCollector metrics = resolveMetrics(context);

        for (CtNode node : neighbors) {
            for (int repeat = 0; repeat < floodRepeatCount; repeat++) {
                if (metrics != null) {
                    metrics.recordSendAttempt(round, sender.getId(), node.getId());
                }
                final FloodPacket<T> stFloodPacket = new FloodPacket<>(context.getTime() + delay + repeat,
                        message, sender, node);
                context.getSimulator().schedulePacket(stFloodPacket);
            }
        }
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LoyalCtNode node = (LoyalCtNode) o;
        return id == node.id;
    }

    @Override
    public String toString() {
        return "N[" + getId() + "]";
    }

    private int resolveRound(ContextView context) {
        CtNetworkTime time = context.getApplication().getNetworkTime();
        return time != null ? time.round() : 0;
    }

    private MetricsCollector resolveMetrics(ContextView context) {
        return context.getApplication() instanceof MetricsEmitter emitter ? emitter.getMetricsCollector() : null;
    }
}
