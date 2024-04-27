package ir.ac.kntu.distributedsystems.fault.om;

import ir.ac.kntu.common.IntCounterMap;
import ir.ac.kntu.concurrenttransmission.CiMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ParentOralMessageSystem implements BlueFloodNodeListener {

    protected final Logger logger = Logger.getLogger(getClass().getSimpleName());
    protected final OmAction defaultAction;
    protected int networkSize;

    public ParentOralMessageSystem(OmAction defaultAction) {
        this.defaultAction = defaultAction;
    }

    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        logger.log(Level.INFO, "PKT lost due to " +
                ((arePacketsSimilar) ? "general loss" : "conflict"));
    }

    @Override
    public CiMessage<?> getMessage(ContextView context, CtNode sender, CiMessage<?> receivedMessage, int whichRepeat) {
        // relay the same message, acting as a loyal node
        return receivedMessage;
    }

    protected OmAction makeDecision(ContextView context, OmNodeStatus thisNodeStatus) {
        IntCounterMap<OmAction> actionsMap = new IntCounterMap<>();

        for (CtNode ctNode : context.getNetGraph().getNodes()) {
            final OmAction action = thisNodeStatus.findActionOfNode(ctNode, defaultAction);
            actionsMap.inc(action);
        }

        final OmAction maxAction = actionsMap.getMaxKey().getKey();
        return maxAction;
    }
}
