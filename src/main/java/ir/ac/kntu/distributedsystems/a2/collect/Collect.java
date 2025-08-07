package ir.ac.kntu.distributedsystems.a2.collect;

import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.FlagField;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import ir.ac.kntu.distributedsystems.a2.aggregation.ParticipationFlag;


import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of ChaosNodeListener for network-wide data collection.
 * Each node contributes a piece of data, and at the end, all nodes
 * will have the complete map of data from all other nodes.
 */
public class Collect implements ChaosNodeListener {
    private static final Logger logger = Logger.getLogger(Collect.class.getSimpleName());

    // A queue of data items for this node to contribute in each round.
    private final Queue<Object> myData;
    private ChaosMessage roundMessage;

    public Collect(Queue<Object> myData) {
        this.myData = myData;
    }

    /**
     * Creates the initial message for this node at the start of a new round.
     * The payload will contain only this node's own data.
     */
    @Override
    public CtMessage<ChaosMessage> initiateMessage(ContextView context, CtNode self, CtNode initiator) {
        Object initialPayloadData = myData.poll();
        if (initialPayloadData == null) {
            initialPayloadData = self.getId(); // Fallback data
        }

        // The payload is a map containing only this node's data initially.
        CollectPayload initialPayload = new CollectPayload(Map.of(self.getId(), initialPayloadData));

        // For Collect, we use simple participation flags.
        FlagField initialFlags = FlagField.initial(self.getId(), ParticipationFlag.PARTICIPATED);

        roundMessage = new ChaosMessage(initialFlags, initialPayload);
        return new CtMessage<>(initiator, roundMessage);
    }

    @Override
    public CtMessage<?> getRoundMessage(ContextView context, CtNode initiator, int whichRepeat) {
        if (roundMessage == null) {
            return null;
        }

        return new CtMessage<>(initiator, roundMessage);
    }

    /**
     * Merges the received data map with the node's current data map.
     */
    @Override
    public CtMessage<ChaosMessage> merge(ContextView context, FloodPacket<?> receivedPacket) {
        StatefulNode receiver = (StatefulNode) receivedPacket.receiver();

        CtMessage<ChaosMessage> currentMessage = receiver.getKnowledge();
        CtMessage<ChaosMessage> receivedMessage = (CtMessage<ChaosMessage>) receivedPacket.ctMessage();

        ChaosMessage currentContent = currentMessage.content();
        ChaosMessage receivedContent = receivedMessage.content();

        FlagField mergedFlags = currentContent.flags().merge(receivedContent.flags());

        CollectPayload currentPayload = (CollectPayload) currentContent.payload();
        CollectPayload receivedPayload = (CollectPayload) receivedContent.payload();
        CollectPayload mergedPayload = currentPayload.merge(receivedPayload);

        logger.fine(String.format("Node[%d] merged data. New map size: %d, flags: %s ,peyload: %s", receiver.getId(), mergedPayload.dataMap().size(), mergedFlags.flags(), mergedPayload.dataMap()));

        ChaosMessage mergedContent = new ChaosMessage(mergedFlags, mergedPayload);
        return new CtMessage<>(currentMessage.initiator(), mergedContent);
    }

    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return null;
    }

    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets, FloodPacket<?> selectedPacket, boolean areSimilar) {
        return true;
    }

    @Override
    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        logger.warning("Packets lost at node " + packets.get(0).receiver().getId() + " due to capture failure.");
    }

}