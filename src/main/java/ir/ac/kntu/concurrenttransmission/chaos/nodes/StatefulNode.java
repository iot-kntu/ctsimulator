package ir.ac.kntu.concurrenttransmission.chaos.nodes;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.StateLogger;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosStateLogger;
import ir.ac.kntu.concurrenttransmission.chaos.NodeStateBehavior;
import ir.ac.kntu.concurrenttransmission.chaos.state.ChaosNodeState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

public interface StatefulNode extends CtNode {
    void initializeForNewRound(ContextView context, CtMessage<ChaosMessage> initialKnowledge, ChaosStateLogger logger);
    void handlePacket(ContextView context, FloodPacket<?> packet);
    void setState(NodeStateBehavior newState, ContextView context);
    NodeStateBehavior getCurrentState();
    CtMessage<ChaosMessage> getKnowledge();
    void setKnowledge(CtMessage<ChaosMessage> newKnowledge);
    boolean shouldFlood(CtMessage<ChaosMessage> currentKnowledge, CtMessage<ChaosMessage> receivedMessage);
    int getFinalFloodCounter();
    void decrementFinalFloodCounter();
}