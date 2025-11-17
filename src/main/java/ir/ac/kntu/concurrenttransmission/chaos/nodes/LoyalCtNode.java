package ir.ac.kntu.concurrenttransmission.chaos.nodes;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.chaos.*;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

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

    private NodeState currentState;
    private NodeState pendingState;
    private long pendingActivationTime = Long.MIN_VALUE;
    private CtMessage<ChaosMessage> knowledge;
    private ChaosStateLogger stateLogger;
    private ChaosTransmissionPolicy policy;

    public LoyalCtNode(Integer id) {
        this.id = id;
    }

    /**
     * Initializes the node at the start of a new round.
     */
    @Override
    public void initializeForNewRound(ContextView context, CtMessage<ChaosMessage> initialKnowledge, ChaosStateLogger logger, NodeState startingPoint) {
        this.knowledge = initialKnowledge;
        this.stateLogger = logger;
        this.policy = (ChaosTransmissionPolicy) context.getApplication().getTransmissionPolicy();
        if (startingPoint != null) {
            setState(startingPoint, context, true);
        }
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
    public void beginSlot(ContextView context) {
        if (pendingState != null && context.getTime() >= pendingActivationTime) {
            NodeState next = pendingState;
            pendingState = null;
            pendingActivationTime = Long.MIN_VALUE;
            applyState(next, context);
        }
    }

    @Override
    public void setState(NodeState newState, ContextView context, boolean immediate) {
        Objects.requireNonNull(newState, "newState");
        if (immediate || this.currentState == null) {
            pendingState = null;
            pendingActivationTime = Long.MIN_VALUE;
            applyState(newState, context);
            return;
        }

        if ((this.currentState != null && this.currentState.getClass() == newState.getClass())
                || (this.pendingState != null && this.pendingState.getClass() == newState.getClass())) {
            return;
        }
        this.pendingState = newState;
        this.pendingActivationTime = context.getTime() + 1;
    }

    private void applyState(NodeState newState, ContextView context) {
        NodeState oldState = this.currentState;
        this.currentState = newState;
        CtNetworkTime netTime = context.getApplication().getNetworkTime();

        stateLogger.setState(netTime, this, newState.toString());
        logger.fine(String.format("Node[%d] at t=%s transitioned to state %s",
                this.id, netTime, newState));

        if (context.getApplication() instanceof ChaosApplication chaosApp) {
            chaosApp.onNodeStateChanged(this, oldState, newState);
            chaosApp.checkRoundCompletion(context);
        }

        newState.onEnter(this, context);
    }


    @Override
    public NodeState getCurrentState() {
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

        // Ensure content is not null before proceeding
        if (currentContent == null || receivedContent == null) {
            return false;
        }

        FlagField mergedFlags = currentContent.flags().merge(receivedContent.flags());

        boolean hasNewInfo = !mergedFlags.equals(currentContent.flags());
        boolean receiverKnowsMore = currentContent.flags().getParticipationCount() > receivedContent.flags().getParticipationCount();

        return hasNewInfo || receiverKnowsMore;
    }

    @Override
    public int getFinalFloodCounter() {
        return policy.getFinalFloodRepeatCount();
    }


    @Override
    public void initiateFlood(ContextView context, CtNode initiatorNode) {
        ChaosTransmissionPolicy policy = (ChaosTransmissionPolicy) context.getApplication().getTransmissionPolicy();
        setState(policy.getInitialFloodState(), context, true);
    }

    @Override
    public <T> void floodMessage(ContextView context, CtNode sender, CtMessage<T> message) {
        this.floodMessage(context, sender, message, false);
    }

    @Override
    public <T> void floodMessage(ContextView context, CtNode sender, CtMessage<T> message, boolean finalFlood) {
        int repeatCount = finalFlood
                ? policy.getFinalFloodRepeatCount() // In FinalFloodingState, we send one packet at a time and decrement counter
                : policy.getFloodRepeatCount();


        for (int repeat = 0; repeat < repeatCount; repeat++) {
            this.sendMessage(context, repeat, sender, message);
        }
    }

    @Override
    public <T> void sendMessage(ContextView context, long delay, CtNode sender, CtMessage<T> message) {
        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(sender);
        for (CtNode node : neighbors) {
            final FloodPacket<T> floodPacket = new FloodPacket<>(context.getTime() + delay, message, sender, node);
            context.getSimulator().schedulePacket(floodPacket);
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
