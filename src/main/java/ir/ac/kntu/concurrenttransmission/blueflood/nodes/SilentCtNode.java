package ir.ac.kntu.concurrenttransmission.blueflood.nodes;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodApplication;
import ir.ac.kntu.metrics.FailureReason;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

public class SilentCtNode extends LoyalCtNode {

    public SilentCtNode(Integer id) {
        super(id);
    }

    @Override
    public void initiateFlood(ContextView context, CtNode initiatorNode) {
        recordSilentFailure(context, initiatorNode);
    }

    @Override
    public <T> void floodMessage(ContextView context, long delay, CtNode sender, CtMessage<T> message) {
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
