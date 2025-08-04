package ir.ac.kntu.distributedsystems.a2;

import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.util.BitSet;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Aggregation implements ChaosNodeListener {
    private static final Logger logger = Logger.getLogger(Aggregation.class.getSimpleName());

    private final Queue<Integer> messages;
    private ChaosMessage roundMessage;

    private int networkSize = 0;

    public Aggregation(Queue<Integer> nodeStatusMap) {
        this.messages = nodeStatusMap;
    }


    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets, FloodPacket<?> selectedPacket, boolean areSimilar) {
        initialize(context);
        return true;
    }

    @Override
    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        logger.log(Level.INFO, "PKT lost due to " + ((arePacketsSimilar) ? "general loss" : "conflict"));
    }

    @Override
    public CtMessage<?> newRound(ContextView context, CtNode initiator) {
        int networkSize = context.getNetGraph().getNodeCount();
        BitSet initialFlags = new BitSet(networkSize);
        initialFlags.set(initiator.getId());

        // Get the next value from the queue for this round.
        Integer initialPayload = messages.poll();
        if (initialPayload == null) {
            // Default to node ID if queue is empty.
            initialPayload = initiator.getId();
        }

        roundMessage = new ChaosMessage(initialFlags, initialPayload);

        return new CtMessage<>(initiator, roundMessage);
    }

    @Override
    public CtMessage<?> getRoundMessage(ContextView context, CtNode initiator, int whichRepeat) {

        initialize(context);

        if (roundMessage == null) {
            return null;
        }
        logger.log(Level.INFO, "Sending " + roundMessage);


        return new CtMessage<>(initiator, roundMessage);
    }

    @Override
    public CtMessage<?> merge(ContextView context, FloodPacket<?> receivedPacket) {
        initialize(context);
        StatefulNode receiver = (StatefulNode) receivedPacket.receiver();
        CtMessage<ChaosMessage> currentMessage = receiver.getKnowledge();
        CtMessage<ChaosMessage> receivedMessage = (CtMessage<ChaosMessage>) receivedPacket.ctMessage();

        ChaosMessage currentContent = currentMessage.content();
        ChaosMessage receivedContent = receivedMessage.content();

        // 1. Merge flags using bitwise OR.
        BitSet mergedFlags = (BitSet) currentContent.flags().clone();
        mergedFlags.or(receivedContent.flags());

        // 2. Merge payload using the max function.
        int maxPayload = Math.max((Integer) currentContent.payload(), (Integer) receivedContent.payload());

        logger.fine(String.format("Node[%d] merged its value %d with received %d. New max is %d",
                receiver.getId(), currentContent.payload(), receivedContent.payload(), maxPayload));

        // 3. Create the new, merged message.
        ChaosMessage mergedContent = new ChaosMessage(mergedFlags, maxPayload);
        return new CtMessage<>(currentMessage.initiator(), mergedContent);
    }

    private void initialize(ContextView context) {
        if (networkSize == 0) {
            networkSize = context.getNetGraph().getNodeCount();
        }
    }


    @Override
    public CtMessage<?> getMessage(ContextView context, CtNode sender, CtMessage<?> receivedMessage, int whichRepeat) {
        return null;
    }
}
