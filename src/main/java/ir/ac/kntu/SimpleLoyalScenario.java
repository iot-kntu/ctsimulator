package ir.ac.kntu;

import ir.ac.kntu.concurrenttransmission.CiMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SimpleLoyalScenario implements BlueFloodNodeListener {

    enum Actions {Unknown, Retreat, Attack}

    record Message(Actions[] actionVector, String body) {
    }

    record MessageStatus(CtNode receivedNode, Actions[] nodesVector, CtNode initiator){

    }

    final static Actions DEFAULT_ACTION = Actions.Retreat;

    private final Logger logger = Logger.getLogger("AllLoyalScenario");

    /**
     * key: message body
     * value:
     */
    private Map<CiMessage, MessageStatus> msgCache;
    private int networkSize;

    public SimpleLoyalScenario() {

    }

    @Override
    public void ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets, FloodPacket<?> selectedPacket, boolean areSimilar) {
        CtNode initiator = selectedPacket.ciMessage().initiator();
        final CtNode sender = selectedPacket.sender();
        Message msg = (Message) selectedPacket.ciMessage().content();
        final Actions[] nodeActionVector = msg.actionVector();

        MessageStatus status = new MessageStatus(sender, nodeActionVector, initiator);
        msgCache.put(selectedPacket.ciMessage(), status);

        // TODO: 3/6/24 implement if majority has voted set as finished
    }

    @Override
    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        logger.log(Level.INFO, "PKT lost due to " +
                ((arePacketsSimilar) ? "general loss" : "conflict"));
    }

    @Override
    public CiMessage<?> initiateMessage(ContextView context, CtNode initiator, int whichRepeat) {
        // FIXME: 3/9/24 not implemented
        return null;
    }

    /**
     * Acting as a loyal node
     */
    @Override
    public CiMessage<?> getMessage(ContextView context, CtNode sender, CiMessage<?> receivedMessage, int whichRepeat) {
        return receivedMessage;
    }

}
