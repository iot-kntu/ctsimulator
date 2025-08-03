package ir.ac.kntu.distributedsystems.a2;

import ir.ac.kntu.concurrenttransmission.CtMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.nodes.CtNode;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosMessage;
import ir.ac.kntu.concurrenttransmission.chaos.ChaosNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.util.BitSet;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Aggregation implements ChaosNodeListener {
    private final int nodeId;
    private static final Logger logger = Logger.getLogger(Aggregation.class.getSimpleName());

    private final Queue<Integer> nodeStatusMap;
    private ChaosMessage roundMessage;

    private int networkSize = 0;

    public Aggregation(Queue<Integer> nodeStatusMap, int nodeId) {
        this.nodeStatusMap = nodeStatusMap;
        this.nodeId = nodeId;
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
        BitSet flags = new BitSet(networkSize);
        flags.set(nodeId);
        roundMessage = new ChaosMessage(flags, nodeStatusMap.poll());
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

        CtMessage<?> ctMessage = receivedPacket.ctMessage();
        ChaosMessage message = (ChaosMessage) ctMessage.content();
        BitSet flags = new BitSet(networkSize);
        flags.set(0, networkSize);
        flags.and(message.flags());
        flags.set(receivedPacket.receiver().getId());

        if (roundMessage != null) {
            int max = Math.max((int) message.payload(), (int) roundMessage.payload());
            logger.log(Level.INFO, "Received " + message.payload() + " and max is " + max);
            roundMessage = new ChaosMessage(flags, max);
            return new CtMessage<>(context.getNetGraph().getNodeById(nodeId), roundMessage);
        }

        return new CtMessage<>(context.getNetGraph().getNodeById(nodeId), new ChaosMessage(flags, message.payload()));
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
