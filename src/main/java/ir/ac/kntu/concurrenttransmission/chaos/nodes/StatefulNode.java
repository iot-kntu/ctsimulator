package ir.ac.kntu.concurrenttransmission.chaos.nodes;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosStateLogger;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

public interface StatefulNode extends CtNode {
    <T> void floodMessage(ContextView context, long delay, CtNode sender, CtMessage<T> message, boolean finalFlood);

    void initializeForNewRound(ContextView context, CtMessage<ChaosMessage> initialKnowledge, ChaosStateLogger logger,
            NodeState startingPoint);

    void handlePacket(ContextView context, FloodPacket<?> packet);

    default void beginSlot(ContextView context) {
    }

    default void setState(NodeState newState, ContextView context) {
        setState(newState, context, false);
    }

    void setState(NodeState newState, ContextView context, boolean immediate);

    NodeState getCurrentState();

    CtMessage<ChaosMessage> getKnowledge();

    void setKnowledge(CtMessage<ChaosMessage> newKnowledge);

    boolean shouldFlood(CtMessage<ChaosMessage> currentKnowledge, CtMessage<ChaosMessage> receivedMessage);

    int getFinalFloodCounter();
}
