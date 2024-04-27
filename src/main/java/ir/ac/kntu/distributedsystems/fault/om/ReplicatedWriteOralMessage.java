package ir.ac.kntu.distributedsystems.fault.om;

import ir.ac.kntu.concurrenttransmission.CiMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.util.List;

// FIXME: 4/11/24 NotImplemented
public class ReplicatedWriteOralMessage extends ParentOralMessageSystem implements BlueFloodNodeListener {

    private boolean finished;

    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets, FloodPacket<?> selectedPacket,
                                     boolean areSimilar) {

        return true;
    }

    @Override
    public CiMessage<?> initiateMessage(ContextView context, CtNode initiator, int whichRepeat) {
        return CiMessage.NULL_MESSAGE;
    }

    protected void initialize(ContextView context) {
        if (networkSize == 0) {
            networkSize = context.getNetGraph().getNodeCount();
            //nodeStatusMap = new HashMap<>();
        }
    }

}
