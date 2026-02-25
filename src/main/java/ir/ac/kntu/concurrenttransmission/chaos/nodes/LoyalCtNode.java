package ir.ac.kntu.concurrenttransmission.chaos.nodes;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.chaos.*;
import ir.ac.kntu.concurrenttransmission.chaos.state.NodeState;
import ir.ac.kntu.concurrenttransmission.chaos.state.primitive.RecoveryFloodingState;
import ir.ac.kntu.concurrenttransmission.events.Event;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimEventPriority;
import ir.ac.kntu.metrics.MetricsCollector;
import ir.ac.kntu.metrics.MetricsEmitter;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

/**
 * A default implementation of a CtNode which behaves loyally
 * which means this CtNode acts non-faulty according to the given
 * TransmissionPolicy
 */
public class LoyalCtNode implements StatefulNode {

    private final int id;
    private static final Logger logger = Logger.getLogger(LoyalCtNode.class.getSimpleName());
    private static final int MIN_LISTEN_TIMEOUT_SLOTS = 5;
    private static final int BACKOFF_MIN_SLOTS = 1;
    private static final int BACKOFF_MAX_SLOTS = 5;

    private NodeState currentState;
    private NodeState pendingState;
    private long pendingActivationTime = Long.MIN_VALUE;
    private CtMessage<ChaosMessage> knowledge;
    private ChaosStateLogger stateLogger;
    private ChaosTransmissionPolicy policy;
    private long lastProgressTime = Long.MIN_VALUE;
    private long listeningSinceTime = Long.MIN_VALUE;
    private long pendingRecoveryTime = Long.MIN_VALUE;
    private int recoveryBackoffSlots = BACKOFF_MIN_SLOTS;
    // private long recoveryEpoch = 0;
    private final Random recoveryRandom;

    public LoyalCtNode(Integer id) {
        this.id = id;
        this.recoveryRandom = new Random(0x9E3779B97F4A7C15L ^ (long) id);
    }

    /**
     * Initializes the node at the start of a new round.
     */
    @Override
    public void initializeForNewRound(ContextView context, CtMessage<ChaosMessage> initialKnowledge,
            ChaosStateLogger logger, NodeState startingPoint) {
        this.knowledge = initialKnowledge;
        this.stateLogger = logger;
        this.policy = (ChaosTransmissionPolicy) context.getApplication().getTransmissionPolicy();
        this.lastProgressTime = context.getTime();
        this.listeningSinceTime = Long.MIN_VALUE;
        invalidateRecovery();
        this.recoveryBackoffSlots = sampleRecoveryBackoff(context);
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
        this.lastProgressTime = context.getTime();
        invalidateRecovery();
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
        maybeScheduleRecovery(context);
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

        invalidateRecovery();

        if (newState.isListening()) {
            listeningSinceTime = context.getTime();
        } else {
            listeningSinceTime = Long.MIN_VALUE;
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
        boolean receiverKnowsMore = currentContent.flags().getParticipationCount() > receivedContent.flags()
                .getParticipationCount();

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
    public <T> void floodMessage(ContextView context, long delay, CtNode sender, CtMessage<T> message) {
        this.floodMessage(context, delay, sender, message, false);
    }

    @Override
    public <T> void floodMessage(ContextView context, long delay, CtNode sender, CtMessage<T> message,
            boolean finalFlood) {
        int repeatCount = finalFlood
                ? policy.getFinalFloodRepeatCount() // In FinalFloodingState, we send one packet at a time and decrement
                                                    // counter
                : policy.getFloodRepeatCount();
        final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(sender);
        int round = resolveRound(context);
        MetricsCollector metrics = resolveMetrics(context);

        for (int repeat = 0; repeat < repeatCount; repeat++) {
            for (CtNode node : neighbors) {
                if (metrics != null) {
                    metrics.recordSendAttempt(round, sender.getId(), node.getId());
                }
                final FloodPacket<T> floodPacket = new FloodPacket<>(context.getTime() + delay + repeat,
                        message, sender, node);
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

    private int resolveRound(ContextView context) {
        CtNetworkTime time = context.getApplication().getNetworkTime();
        return time != null ? time.round() : 0;
    }

    private MetricsCollector resolveMetrics(ContextView context) {
        return context.getApplication() instanceof MetricsEmitter emitter ? emitter.getMetricsCollector() : null;
    }

    private void maybeScheduleRecovery(ContextView context) {
        if (context == null || currentState == null || !currentState.isListening()) {
            return;
        }
        long now = context.getTime();
        if (pendingRecoveryTime != Long.MIN_VALUE && now < pendingRecoveryTime) {
            return;
        }
        long idleSince = listeningSinceTime != Long.MIN_VALUE
                ? Math.max(listeningSinceTime, lastProgressTime)
                : lastProgressTime;
        if (idleSince == Long.MIN_VALUE || now - idleSince < recoveryBackoffSlots) {
            return;
        }
        long scheduledAt = now;
        long scheduledTime = now + 1;
        // long scheduledEpoch = recoveryEpoch;
        pendingRecoveryTime = scheduledTime;

        context.getSimulator().scheduleEvent(
                Event.create("ListenTimeoutFlood", scheduledTime, SimEventPriority.High, (ctx) -> {
                    if (currentState == null || !currentState.isListening()) {
                        invalidateRecovery();
                        return;
                    }                
                    if (lastProgressTime > scheduledAt) {
                        invalidateRecovery();
                        return;
                    }
                    recoveryBackoffSlots = sampleRecoveryBackoff(context);
                    pendingRecoveryTime = Long.MIN_VALUE;
                    NodeState previousState = currentState;
                    setState(new RecoveryFloodingState(previousState), ctx, true);
                }));
    }

    private int sampleRecoveryBackoff(ContextView context) {
        Random rng = recoveryRandom;
        int range = BACKOFF_MAX_SLOTS - BACKOFF_MIN_SLOTS + 1;
        int listenTimeoutSlots = resolveListenTimeoutSlots(context);
        if (range <= 0) {
            return BACKOFF_MIN_SLOTS + listenTimeoutSlots;
        }

        int b = rng.nextInt(range);
        int result = b + listenTimeoutSlots;

        return result;
    }

    private int resolveListenTimeoutSlots(ContextView context) {
        if (context == null || context.getNetGraph() == null) {
            return MIN_LISTEN_TIMEOUT_SLOTS;
        }
        int diameterBased = (context.getNetGraph().getDiameter() * 2) + 1;
        return Math.max(MIN_LISTEN_TIMEOUT_SLOTS, diameterBased);
    }

    private void invalidateRecovery() {
        pendingRecoveryTime = Long.MIN_VALUE;
        // recoveryEpoch++;
    }
}
