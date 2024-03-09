package ir.ac.kntu;

import ir.ac.kntu.common.IntCounterMap;
import ir.ac.kntu.concurrenttransmission.CiMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The implementation of Oral Message algorithm for coordinator-based group
 * which includes one Major General and n-1 lieutenants. The implementation
 * is done for only one message.
 */
public class CoordinatorBasedOralMessage implements BlueFloodNodeListener {

    final static Actions DEFAULT_ACTION = Actions.Retreat;

    private final Logger logger = Logger.getLogger("AllLoyalScenario");

    /**
     * We assume only one action is circulated in the graph
     */
    private HashSet<MessageStatus> msgCache;
    private int networkSize;
    private boolean finished;


    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets,
                                     FloodPacket<?> selectedPacket,
                                     boolean areSimilar) {

        if (this.finished)
            return false;

        CtNode thisNode = selectedPacket.receiver();

        initialize(context);

        boolean newPacket = false;
        for (FloodPacket<?> packet : packets) {
            final CtNode sender = packet.sender();
            final CiMessage<Message> ciMessage = (CiMessage<Message>) packet.ciMessage();
            CtNode initiator = ciMessage.initiator();
            final Actions nodeAction = ciMessage.content().nodeAction();

            MessageStatus status = new MessageStatus(sender, nodeAction, initiator);

            newPacket = msgCache.add(status);
        }

        // if the node has received message from all of its neighbors
        if (newPacket && msgCache.size() == context.getNetGraph().getNodeNeighbors(thisNode).size()) {
            this.finished = true;

            final IntCounterMap<Actions> actionMap = findMajorActionInCache();

            // add DEFAULT_ACTION for nodes, this node has not received any data
            for (int i = actionMap.size(); i < networkSize; i++)
                actionMap.inc(DEFAULT_ACTION);

            if (actionMap.hasSeveralMaxKeys() <= 1) {
                Actions decision = actionMap.getMaxKey().getKey();

                logger.log(Level.INFO, "FINAL_DECISION::Node-" + selectedPacket.receiver().getId() +
                        " decided as " + decision
                        + " in round " + context.getApplication().getNetworkTime().round());
            }
            else {
                logger.log(Level.INFO, "FINAL_DECISION::Node-" + selectedPacket.receiver().getId() +
                        " CANNOT DECIDE in round " +
                        context.getApplication().getNetworkTime().round() +
                        " due to equal number of different decisions"
                );

            }
        }

        return newPacket;
    }

    @Override
    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        logger.log(Level.INFO, "PKT lost due to " +
                ((arePacketsSimilar) ? "general loss" : "conflict"));
    }

    @Override
    public CiMessage<?> initiateMessage(ContextView context, CtNode initiator, int whichRepeat) {
        initialize(context);

        // node0 is Major General!
        if (initiator.getId() == 0) {
            return new CiMessage<>(initiator, new Message(Actions.Attack, ""));
        }

        for (MessageStatus messageStatus : msgCache) {
            // find if this node has received the command directly from the general
            if (messageStatus.senderNode().equals(messageStatus.initiator())) {
                return new CiMessage<>(initiator, new Message(messageStatus.nodeAction, ""));
            }
        }

        // when execution reaches here, the node has received the general command through other
        //  intermediate nodes. The node sends the mostly-received command
        final IntCounterMap<Actions> actionCounter = findMajorActionInCache();
        final Actions maxAction = actionCounter.getMaxKey().getKey();

        return new CiMessage<>(initiator, new Message(maxAction, ""));
    }

    @Override
    public CiMessage<?> getMessage(ContextView context, CtNode sender, CiMessage<?> receivedMessage, int whichRepeat) {
        // relay the same message, acting as a loyal node
        return receivedMessage;
    }

    @NotNull
    private IntCounterMap<Actions> findMajorActionInCache() {
        IntCounterMap<Actions> actionCounter = new IntCounterMap<>();
        for (MessageStatus status : msgCache) {
            Actions action = status.nodeAction();
            if (action == Actions.Unknown)
                action = DEFAULT_ACTION;

            actionCounter.inc(action);
        }
        return actionCounter;
    }

    private void initialize(ContextView context) {
        if (networkSize == 0) {
            networkSize = context.getNetGraph().getNodeCount();
            msgCache = new HashSet<>();
        }
    }

    enum Actions {Unknown, Retreat, Attack}

    record Message(Actions nodeAction, String body) {
    }

    record MessageStatus(CtNode senderNode, Actions nodeAction, CtNode initiator) {
    }

}
