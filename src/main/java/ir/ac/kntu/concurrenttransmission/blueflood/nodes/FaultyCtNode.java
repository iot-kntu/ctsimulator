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
 * Represents a complete faulty node. The faulty node may generate faulty
 * messages
 * in round initiation, or in flooding slots.
 */
public class FaultyCtNode extends LoyalCtNode {

    public FaultyCtNode(Integer id) {
        super(id);
    }

    @Override
    public void initiateFlood(ContextView context, CtNode initiatorNode) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(initiatorNode);

        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();
        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(initiatorNode);
        int round = resolveRound(context);
        MetricsCollector metrics = resolveMetrics(context);

        for (CtNode node : neighbors) {
            for (int repeat = 0; repeat < floodRepeatCount; repeat++) {

                final CtMessage<?> ctMessage = ((CtBlueFloodApplication) context
                        .getApplication())
                        .getRoundInitiationMessage(context, initiatorNode, repeat);

                if (ctMessage.isNull())
                    continue;

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

        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(sender);
        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();
        int round = resolveRound(context);
        MetricsCollector metrics = resolveMetrics(context);

        for (int i = 0; i < floodRepeatCount; i++) {

            CtMessage<?> newMessage = context.getApplication().getMessage(context, sender, message, i);

            for (CtNode neighbor : neighbors) {
                if (metrics != null) {
                    metrics.recordSendAttempt(round, sender.getId(), neighbor.getId());
                }
                final FloodPacket<?> packet = new FloodPacket<>(context.getTime() + delay + i,
                        newMessage, sender, neighbor);
                context.getSimulator().schedulePacket(packet);
            }
        }
    }

    private int resolveRound(ContextView context) {
        CtNetworkTime time = context.getApplication().getNetworkTime();
        return time != null ? time.round() : 0;
    }

    private MetricsCollector resolveMetrics(ContextView context) {
        return context.getApplication() instanceof MetricsEmitter emitter ? emitter.getMetricsCollector() : null;
    }
}
