package ir.ac.kntu.concurrenttransmission.chaos;

import ir.ac.kntu.common.IntCounterMap;
import ir.ac.kntu.concurrenttransmission.*;
import ir.ac.kntu.concurrenttransmission.events.CtPacketsEvent;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.concurrenttransmission.events.SimInitiateFloodEvent;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChaosApplication implements ConcurrentTransmissionApplication {

    private final Logger logger = Logger.getLogger("ChaosApplication");

    public static double DEFAULT_INTERFERENCE_PROB = 0.9;
    protected final ChaosStrategies strategies;

    private final ChaosSettings settings;

    private final Random random = new Random(new Date().getTime());

    private CtNetworkTime networkTime;

    private final SortedMap<CtNode, ChaosNodeListener> listeners;

    private final Map<CtNode, CiMessage<ChaosMessage>> nodeKnowledge;
    private final StateLogger stateLogger;
    private final Map<CtNode, Integer> finalFloodCounter;

    public ChaosApplication(ChaosSettings settings, ChaosStrategies strategies, NetGraph netGraph) {
        this.settings = settings;
        this.strategies = strategies;
        this.listeners = new TreeMap<>();
        this.stateLogger = new StateLogger(new ArrayList<>(netGraph.getNodes()), strategies.transmissionPolicy());
        this.nodeKnowledge = new TreeMap<>();
        this.finalFloodCounter = new TreeMap<>();
    }

    public void setListener(CtNode node, ChaosNodeListener listener) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(listener);

        this.listeners.put(node, listener);
    }

    public StateLogger getStateLogger() {
        return this.stateLogger;
    }

    @Override
    public CtNetworkTime getNetworkTime() {
        return networkTime;
    }

    @Override
    public void simulationStarting(ContextView context) {
        newRound(context);
    }


    @Override
    public void simulationFinishing(ContextView context) {

    }

    @Override
    public void simulationTimeProgressed(ContextView context) {
        Objects.requireNonNull(context);

        final ChaosTransmissionPolicy transmissionPolicy = strategies.transmissionPolicy();
        this.networkTime = transmissionPolicy.getNetworkTime(context.getTime());
    }

    @Override
    public void newRound(ContextView context) {
        if (this.networkTime != null && this.networkTime.round() > 0)
            logger.log(Level.INFO, "======== round " + getRound() + " completed ===========");

        if (getRound() < settings.roundLimit()) {
            nodeKnowledge.clear();
            finalFloodCounter.clear();

            context.getNetGraph().getNodes().forEach(node -> {
                CiMessage<ChaosMessage> initialMessage = (CiMessage<ChaosMessage>) getChaosListener(node).newRound(context, node);
                nodeKnowledge.put(node, initialMessage);
                for (int s = 0; s < strategies.transmissionPolicy().getTotalSlotsOfRound(); s++) {
                    stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), s), node, NodeState.Listen);
                }
            });

            final int nextInitiator = strategies.initiatorStrategy().getNextInitiatorId();
            SimInitiateFloodEvent initiateFloodEvent = new SimInitiateFloodEvent(context.getTime(), nextInitiator);
            context.getSimulator().scheduleEvent(initiateFloodEvent);

            // scheduling the next round to keep the simulation working even with faults
            context.getSimulator().scheduleEvent(new SimNewRoundEvent(context.getTime() + strategies.transmissionPolicy().getTotalSlotsOfRound()));
        }
    }

    @Override
    public void initiateFlood(ContextView context) throws RuntimeException {
        Objects.requireNonNull(context);

        final CtNode inode = getInitiatorNode(context);


        stateLogger.setState(getNetworkTime(), inode, NodeState.Flood);
        stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), getNetworkTime().slot() + 1), inode, NodeState.Listen);

        inode.initiateFlood(context, inode);


        logger.log(Level.INFO, "[" + context.getTime() + "] Node-" + inode.getId() + " initiated message");

    }

    @Override
    public void ctPacketsReceived(CtPacketsEvent ctEvent, ContextView context) {
        Objects.requireNonNull(ctEvent);
        Objects.requireNonNull(context);

        final List<FloodPacket<?>> packets = ctEvent.getPackets();

        if (packets.isEmpty()) return;


        final CtNode receiver = ctEvent.getReceiver();


        // --- FINAL FLOOD LOGIC: Check if node is already done ---
        if (finalFloodCounter.containsKey(receiver) && finalFloodCounter.get(receiver) <= 0) {
            return;
        }


        stateLogger.setState(getNetworkTime(), receiver, NodeState.Listen);

        FloodPacket<?> capturedPacket = packets.get(random.nextInt(packets.size()));
        CiMessage<ChaosMessage> receivedMessage = (CiMessage<ChaosMessage>) capturedPacket.ciMessage();
        CiMessage<ChaosMessage> currentMessage = nodeKnowledge.get(receiver);
        ChaosMessage currentContent = currentMessage.content();
        ChaosMessage receivedContent = receivedMessage.content();


        // --- MERGE AND UPDATE KNOWLEDGE ---
        CiMessage<ChaosMessage> mergedMessage = (CiMessage<ChaosMessage>) getChaosListener(receiver).merge(context, capturedPacket);
        nodeKnowledge.put(receiver, mergedMessage);
        ChaosMessage mergedContent = mergedMessage.content();


        // --- COMPLETION DETECTION ---
        int totalNodes = context.getNetGraph().getNodeCount();
        if (mergedContent.flags().cardinality() == totalNodes && !finalFloodCounter.containsKey(receiver)) {
            finalFloodCounter.put(receiver, getTransmissionPolicy().getFinalFloodRepeatCount());
            logger.log(Level.INFO, "Node " + receiver.getId() + " has reached COMPLETION. Starting final flood.");
        }


        // --- DECISION TO FLOOD ---
        boolean isInFinalFlood = finalFloodCounter.getOrDefault(receiver, 0) > 0;
        boolean hasNewInfo = !mergedContent.flags().equals(currentContent.flags());
        boolean receiverKnowsMore = currentContent.flags().cardinality() > receivedContent.flags().cardinality();


        if (isInFinalFlood || hasNewInfo || receiverKnowsMore) {
            logger.log(Level.INFO, "Node " + receiver.getId() + " decided to flood. FinalFlood: " + isInFinalFlood + ", NewInfo: " + hasNewInfo + ", KnowsMore: " + receiverKnowsMore);

            final List<CtNode> neighbors = context.getNetGraph().getNodeNeighbors(receiver);

            if (!isInFinalFlood) {
                for (CtNode node : neighbors) {
                    for (int repeat = 1; repeat <= strategies.transmissionPolicy().getFloodRepeatCount(); repeat++) {
                        final FloodPacket<?> stFloodPacket = new FloodPacket<>(context.getTime() + repeat, mergedMessage, receiver, node);
                        context.getSimulator().schedulePacket(stFloodPacket);
                    }

                    for (int repeat = 0; repeat < strategies.transmissionPolicy().getFloodRepeatCount(); repeat++) {
                        stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), getNetworkTime().slot() + repeat), receiver, NodeState.Flood);
                    }
                    stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), getNetworkTime().slot() + strategies.transmissionPolicy().getFloodRepeatCount()), receiver, NodeState.Listen);

                }
            }




            if (isInFinalFlood) {
                int remainingFloods = finalFloodCounter.get(receiver);

                for (CtNode node : neighbors) {
                    for (int repeat = 1; repeat <= remainingFloods; repeat++) {
                        final FloodPacket<?> stFloodPacket = new FloodPacket<>(context.getTime() + repeat, mergedMessage, receiver, node);
                        context.getSimulator().schedulePacket(stFloodPacket);
                    }
                }

                for (int repeat = 1; repeat <= remainingFloods; repeat++) {
                    stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), getNetworkTime().slot() + repeat), receiver, NodeState.Flood);
                }

                finalFloodCounter.put(receiver, 0);
                logger.log(Level.INFO, "Node " + receiver.getId() + " finished final flood. Will sleep.");

                for (int s =  remainingFloods; s < strategies.transmissionPolicy().getTotalSlotsOfRound(); s++) {
                    stateLogger.setState(new CtNetworkTime(getNetworkTime().round(), getNetworkTime().slot() + s + 1 ), receiver, NodeState.Sleep);
                }
            }
        }
    }


    @Override
    public CtNode getInitiatorNode(ContextView context) {
        return context.getNetGraph().getNodeById(strategies.initiatorStrategy().getCurrentInitiatorId());
    }

    @Override
    public NodeState getNodeState(CtNode node) {
        return strategies.transmissionPolicy().getNodeState(node, getSlot());
    }


    @Override
    public ChaosTransmissionPolicy getTransmissionPolicy() {
        return strategies.transmissionPolicy();
    }


    @Override
    public CiMessage<?> getMessage(ContextView context, CtNode sender, CiMessage<?> receivedMessage, int whichRepeat) {
        return getChaosListener(sender).getMessage(context, sender, receivedMessage, whichRepeat);
    }

    @Override
    public CiMessage<?> getRoundInitiationMessage(ContextView context, CtNode initiator, int whichRepeat) {
        return getChaosListener(initiator).getRoundMessage(context, initiator, whichRepeat);
    }


    public Logger getLogger() {
        return logger;
    }

    public int getRound() {
        return networkTime.round();
    }

    public int getSlot() {
        return networkTime.slot();
    }

    private ChaosNodeListener getChaosListener(CtNode node) {
        final ChaosNodeListener listener = listeners.get(node);
        if (listener == null) throw new IllegalStateException("No listener defined for node " + node);

        return listener;
    }
}
