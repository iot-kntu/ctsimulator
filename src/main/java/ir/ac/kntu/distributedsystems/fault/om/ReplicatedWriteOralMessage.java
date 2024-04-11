package ir.ac.kntu.distributedsystems.fault.om;

import ir.ac.kntu.common.IntCounterMap;
import ir.ac.kntu.concurrenttransmission.CiMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.util.List;

public class ReplicatedWriteOralMessage extends ParentOralMessageSystem implements BlueFloodNodeListener {

    private boolean finished;

    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets, FloodPacket<?> selectedPacket,
                                     boolean areSimilar) {

        // ensure data structures initialization
        initialize(context);

        // if the node has made the final decision just ignore incoming messages
        if (this.finished)
            return false;

        if (!msgCache.isEmpty()) {
            // all nodes have initiated a message and General have sent a command again!
            this.finished = true;
            makeDecision(context, selectedPacket);
            return false;
        }

        // for debug
        CtNode thisNode = selectedPacket.receiver();

        final CiMessage<OmMessage> ciMessage = (CiMessage<OmMessage>) selectedPacket.ciMessage();
        CtNode initiator = ciMessage.initiator();
        final OmActions nodeAction = ciMessage.content().nodeAction();

        OmMessageStatus status = new OmMessageStatus(initiator, nodeAction);
        boolean newPacket = msgCache.add(status);

        // if the node has received message from all of its neighbors
        if (newPacket && msgCache.size() == networkSize - 1) {
            this.finished = true;
            makeDecision(context, selectedPacket);
        }

        return newPacket;

    }

    @Override
    public CiMessage<?> initiateMessage(ContextView context, CtNode initiator, int whichRepeat) {

        initialize(context);

        // node0 is Major General!
        if (initiator.getId() == PrimaryBasedOralMessage.COORDINATOR_ID)
            if (this.finished)
                return CiMessage.NULL_MESSAGE;
            else
                return new CiMessage<>(initiator, new OmMessage(OmActions.Attack, ""));

        // when execution reaches here, the node has received the general command through other
        //  intermediate nodes. The node sends the mostly-received command or the default action
        if (msgCache.isEmpty()) {
            return new CiMessage<>(initiator, new OmMessage(DEFAULT_ACTION, ""));
        }
        else {
            final IntCounterMap<OmActions> actionCounter = findMajorActionInCache();
            final OmActions maxAction = actionCounter.getMaxKey().getKey();

            return new CiMessage<>(initiator, new OmMessage(maxAction, ""));
        }
    }

    @Override
    public CiMessage<?> getMessage(ContextView context, CtNode sender, CiMessage<?> receivedMessage, int whichRepeat) {
        return receivedMessage;
    }


}
