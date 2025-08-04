package ir.ac.kntu.concurrenttransmission.chaos.nodes;

import ir.ac.kntu.concurrenttransmission.*;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosStateLogger;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosTransmissionPolicy;
import ir.ac.kntu.concurrenttransmission.chaos.NodeStateBehavior;
import ir.ac.kntu.concurrenttransmission.chaos.state.ChaosNodeState;
import ir.ac.kntu.concurrenttransmission.chaos.state.FinalFloodingState;
import ir.ac.kntu.concurrenttransmission.chaos.state.FloodingState;
import ir.ac.kntu.concurrenttransmission.chaos.state.ListeningState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * A default implementation of a CtNode which behaves loyally
 * which means this CtNode acts non-faulty according to the given
 * TransmissionPolicy
 */
public class LoyalCtNode implements StatefulNode {

    private final int id;
    private static final Logger logger = Logger.getLogger(LoyalCtNode.class.getSimpleName());

    private NodeStateBehavior currentState;
    private CtMessage<ChaosMessage> knowledge;
    private ChaosStateLogger stateLogger;
    private int finalFloodCounter;
    private ChaosTransmissionPolicy policy;

    public LoyalCtNode(Integer id) {
        this.id = id;
    }

    /**
     * Initializes the node at the start of a new round.
     */
    @Override
    public void initializeForNewRound(ContextView context, CtMessage<ChaosMessage> initialKnowledge, ChaosStateLogger logger) {
        this.knowledge = initialKnowledge;
        this.stateLogger = logger;
        this.policy = (ChaosTransmissionPolicy) context.getApplication().getTransmissionPolicy();
        this.finalFloodCounter = policy.getFinalFloodRepeatCount();
        setState(new ListeningState(), context);
    }

    /**
     * The main entry point for a node to process a received packet.
     * It delegates the action to its current state object.
     */
    @Override
    public void handlePacket(ContextView context, FloodPacket<?> packet) {
        this.currentState.onPacketReceived(this, context, packet);
    }

    /**
     * Central method for changing the node's state.
     * This method AUTOMATICALLY logs the state change.
     */
    @Override
    public void setState(NodeStateBehavior newState, ContextView context) {
        if (this.currentState == null || this.currentState.getClass() != newState.getClass()) {
            this.currentState = newState;
            CtNetworkTime netTime = context.getApplication().getNetworkTime();

            stateLogger.setState(netTime, this, newState.getStateAsEnum());
            logger.fine(String.format("Node[%d] at t=%s transitioned to state %s",
                    this.id, netTime, newState.getStateAsEnum().name()));

            // Trigger the onEnter action for the new state
            newState.onEnter(this, context);
        }
    }


    @Override
    public NodeStateBehavior getCurrentState() {
        return currentState;
    }

    @Override
    public CtMessage<ChaosMessage> getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(CtMessage<ChaosMessage> newKnowledge) {
        this.knowledge = newKnowledge;
    }

    @Override
    public boolean shouldFlood(CtMessage<ChaosMessage> currentKnowledge, CtMessage<ChaosMessage> receivedMessage) {
        ChaosMessage currentContent = currentKnowledge.content();
        ChaosMessage receivedContent = receivedMessage.content();

        BitSet combinedFlags = (BitSet) currentContent.flags().clone();
        combinedFlags.or(receivedContent.flags());
        boolean hasNewInfo = !combinedFlags.equals(currentContent.flags());
        boolean receiverKnowsMore = currentContent.flags().cardinality() > receivedContent.flags().cardinality();

        return hasNewInfo || receiverKnowsMore;
    }

    @Override
    public int getFinalFloodCounter() {
        return finalFloodCounter;
    }

    @Override
    public void decrementFinalFloodCounter() {
        this.finalFloodCounter--;
    }


    @Override
    public void initiateFlood(ContextView context, CtNode initiatorNode) {
        setState(new FloodingState(), context);
    }

    @Override
    public <T> void floodMessage(ContextView context, CtNode sender, CtMessage<T> message) {
        // This method now handles both regular and final floods
        int repeatCount = (currentState instanceof FinalFloodingState)
                ? policy.getFinalFloodRepeatCount() // In FinalFloodingState, we send one packet at a time and decrement counter
                : policy.getFloodRepeatCount();

        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(sender);
        for (CtNode node : neighbors) {
            for (int repeat = 0; repeat < repeatCount; repeat++) {
                final FloodPacket<T> floodPacket = new FloodPacket<>(context.getTime() + 1 + repeat, message, sender, node);
                context.getSimulator().schedulePacket(floodPacket);
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
}

