package ir.ac.kntu.concurrenttransmission.blueflood.nodes;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodApplication;
import ir.ac.kntu.concurrenttransmission.blueflood.CtBlueFloodApplication;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.metrics.FailureReason;
import ir.ac.kntu.metrics.FaultModel;
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
        FaultModel faultModel = resolveFaultModel(context);
        FailureReason suppression = suppressionReason(faultModel, initiatorNode);
        int round = resolveRound(context);
        MetricsCollector metrics = resolveMetrics(context);

        if (suppression != null) {
            for (CtNode node : neighbors) {
                if (metrics != null) {
                    metrics.recordSendSuppressed(round, initiatorNode.getId(), node.getId(), suppression);
                }
                recordScenarioFailure(context, initiatorNode.getId(), node.getId(), suppression);
            }
            return;
        }

        for (CtNode node : neighbors) {
            for (int repeat = 0; repeat < floodRepeatCount; repeat++) {

                final CtMessage<?> ctMessage = ((CtBlueFloodApplication) context
                        .getApplication())
                        .getRoundInitiationMessage(context, initiatorNode, repeat);

                if (ctMessage.isNull())
                    continue;

                int jitter = faultModel != null ? faultModel.sampleJitterSlots() : 0;
                if (metrics != null) {
                    metrics.recordSendAttempt(round, initiatorNode.getId(), node.getId());
                }
                final FloodPacket<?> stFloodPacket = new FloodPacket<>(context.getTime() + repeat + jitter, ctMessage,
                        initiatorNode, node);
                context.getSimulator().schedulePacket(stFloodPacket);
            }
        }

    }

    @Override
    public <T> void floodMessage(ContextView context, long delay, CtNode sender, CtMessage<T> message) {

        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(sender);
        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();
        FaultModel faultModel = resolveFaultModel(context);
        FailureReason suppression = suppressionReason(faultModel, sender);
        int round = resolveRound(context);
        MetricsCollector metrics = resolveMetrics(context);

        if (suppression != null) {
            for (CtNode neighbor : neighbors) {
                if (metrics != null) {
                    metrics.recordSendSuppressed(round, sender.getId(), neighbor.getId(), suppression);
                }
                recordScenarioFailure(context, sender.getId(), neighbor.getId(), suppression);
            }
            return;
        }

        for (int i = 0; i < floodRepeatCount; i++) {

            CtMessage<?> newMessage = context.getApplication().getMessage(context, sender, message, i);

            for (CtNode neighbor : neighbors) {
                int jitter = faultModel != null ? faultModel.sampleJitterSlots() : 0;
                if (metrics != null) {
                    metrics.recordSendAttempt(round, sender.getId(), neighbor.getId());
                }
                final FloodPacket<?> packet = new FloodPacket<>(context.getTime() + delay + i + jitter,
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

    private FaultModel resolveFaultModel(ContextView context) {
        return context.getApplication() instanceof ir.ac.kntu.metrics.FaultModelProvider provider
                ? provider.getFaultModel()
                : null;
    }

    private FailureReason suppressionReason(FaultModel faultModel, CtNode sender) {
        if (faultModel == null || sender == null) {
            return null;
        }
        if (faultModel.isSilent(sender.getId())) {
            return FailureReason.SILENT;
        }
        if (faultModel.isFaulty(sender.getId())) {
            return FailureReason.FAULTY;
        }
        return null;
    }

    private void recordScenarioFailure(ContextView context, int from, int to, FailureReason reason) {
        if (context.getApplication() instanceof BlueFloodApplication app) {
            CtNetworkTime time = context.getApplication().getNetworkTime();
            if (time != null) {
                app.getScenarioRecorder().recordEvent(time,
                        ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodScenarioRecorder.TransmissionEvent
                                .failure("flood", from, to, reason));
            }
        }
    }

}
