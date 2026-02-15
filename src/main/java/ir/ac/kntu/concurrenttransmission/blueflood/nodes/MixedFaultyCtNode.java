package ir.ac.kntu.concurrenttransmission.blueflood.nodes;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodApplication;
import ir.ac.kntu.metrics.FailureReason;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

import java.util.Date;
import java.util.Random;

/**
 * This node type somtimes sends faulty messages and sometimes remains silent
 * based on silencePercent parameter. The default silence probability is 50%
 */
public class MixedFaultyCtNode extends FaultyCtNode {

    private final Random random = new Random(new Date().getTime());
    private double silencePercent = 0.5;

    public MixedFaultyCtNode(Integer id) {
        super(id);
    }

    public double getSilencePercent() {
        return silencePercent;
    }

    public void setSilencePercent(double silencePercent) {
        this.silencePercent = silencePercent;
    }

    @Override
    public void initiateFlood(ContextView context, CtNode initiatorNode) {
        if (random.nextDouble() > silencePercent) {
            super.initiateFlood(context, initiatorNode);
            return;
        }
        recordSilentFailure(context, initiatorNode);
    }

    @Override
    public <T> void floodMessage(ContextView context, long delay, CtNode sender, CtMessage<T> message) {

        if (random.nextDouble() > silencePercent) {
            super.floodMessage(context, delay, sender, message);
            return;
        }
        recordSilentFailure(context, sender);
    }

    private void recordSilentFailure(ContextView context, CtNode sender) {
        if (context == null || sender == null) {
            return;
        }
        MetricsCollector metrics = context.getApplication() instanceof MetricsEmitter emitter
                ? emitter.getMetricsCollector()
                : null;
        CtNetworkTime time = context.getApplication().getNetworkTime();
        int round = time != null ? time.round() : 0;
        context.getNetGraph().getNodeNeighbors(sender).forEach(neighbor -> {
            if (metrics != null) {
                metrics.recordSendSuppressed(round, sender.getId(), neighbor.getId(), FailureReason.SILENT);
            }
            if (context.getApplication() instanceof BlueFloodApplication app && time != null) {
                app.getScenarioRecorder().recordEvent(time,
                        ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodScenarioRecorder.TransmissionEvent
                                .failure("flood", sender.getId(), neighbor.getId(), FailureReason.SILENT));
            }
        });
    }

}
