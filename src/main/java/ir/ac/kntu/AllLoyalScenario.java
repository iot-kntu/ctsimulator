package ir.ac.kntu;

import ir.ac.kntu.concurrenttransmission.CiMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.util.BitSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AllLoyalScenario implements BlueFloodListener {
    private final Logger logger = Logger.getLogger("AllLoyalScenario");



    @Override
    public void ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets, FloodPacket<?> selectedPacket) {

    }

    @Override
    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        logger.log(Level.INFO, "PKT lost due to " +
                ((arePacketsSimilar) ? "general loss" : "conflict"));

    }

    @Override
    public CiMessage<?> initiateMessage(ContextView context, CtNode initiator, int whichRepeat) {
        if (initiator.getId() == 0) {
            BitSet bitSet = new BitSet(context.getNetGraph().getNodeCount());
            bitSet.set(0);
            Message message = new Message(bitSet, "");
            return new CiMessage<>(initiator, message);
        }
        else {

        }
    }

    @Override
    public CiMessage<?> getMessage(ContextView context, CtNode sender, CiMessage<?> receivedMessage, int whichRepeat) {
        return receivedMessage;
    }

    record Message(BitSet bitSet, String body) {

    }
}
