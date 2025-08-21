package ir.ac.kntu.concurrenttransmission.events;

import ir.ac.kntu.concurrenttransmission.BaseSimEvent;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.FloodingState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.ListeningState;

public class FinishedFloodEvent extends BaseSimEvent {
    private final StatefulNode targetNode;

    public FinishedFloodEvent(long time, StatefulNode targetNode) {
        super(time, SimEventPriority.BelowNormal);
        this.targetNode = targetNode;
    }

    @Override
    public void handle(ContextView context) {
        // Only transition back to Listen if the node is still in a Flooding state.
        // It might have already transitioned due to another event.
        if (targetNode.getCurrentState() instanceof FloodingState) {
            targetNode.setState(new ListeningState(), context);
        }
    }

    @Override
    public String toString() {
        return String.format("FinishedFloodEvent[t=%d, node=%d]", getTime(), targetNode.getId());
    }
}
