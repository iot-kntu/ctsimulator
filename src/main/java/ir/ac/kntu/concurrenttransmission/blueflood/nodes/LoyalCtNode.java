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
        FaultModel faultModel = resolveFaultModel(context);
        FailureReason suppression = suppressionReason(faultModel, initiatorNode);
        int round = resolveRound(context);
        MetricsCollector metrics = resolveMetrics(context);

        final CtMessage<?> ctMessage = ((CtBlueFloodApplication) context
                .getApplication())
                .getRoundInitiationMessage(context, initiatorNode, 0);

        if (ctMessage.isNull())
            return;

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

        Objects.requireNonNull(context);
        Objects.requireNonNull(sender);
        Objects.requireNonNull(message);

        final int floodRepeatCount = context.getApplication().getTransmissionPolicy().getFloodRepeatCount();
        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(sender);
        FaultModel faultModel = resolveFaultModel(context);
        FailureReason suppression = suppressionReason(faultModel, sender);
        int round = resolveRound(context);
        MetricsCollector metrics = resolveMetrics(context);

        if (suppression != null) {
            for (CtNode node : neighbors) {
                if (metrics != null) {
                    metrics.recordSendSuppressed(round, sender.getId(), node.getId(), suppression);
                }
                recordScenarioFailure(context, sender.getId(), node.getId(), suppression);
            }
            return;
        }

        for (CtNode node : neighbors) {
            for (int repeat = 0; repeat < floodRepeatCount; repeat++) {
                int jitter = faultModel != null ? faultModel.sampleJitterSlots() : 0;
                if (metrics != null) {
                    metrics.recordSendAttempt(round, sender.getId(), node.getId());
                }
                final FloodPacket<T> stFloodPacket = new FloodPacket<>(context.getTime() + delay + repeat + jitter,
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
