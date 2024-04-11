package ir.ac.kntu.distributedsystems.fault.om;

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

public abstract class ParentOralMessageSystem implements BlueFloodNodeListener {

    protected final static OmActions DEFAULT_ACTION = OmActions.Retreat;
    protected final Logger logger = Logger.getLogger(getClass().getSimpleName());
    protected int networkSize;
    /**
     * We assume only one action is circulated in the graph
     */
    protected HashSet<OmMessageStatus> msgCache;

    public void ctPacketsLost(ContextView context, List<FloodPacket<?>> packets, boolean arePacketsSimilar) {
        logger.log(Level.INFO, "PKT lost due to " +
                ((arePacketsSimilar) ? "general loss" : "conflict"));
    }

    @Override
    public CiMessage<?> getMessage(ContextView context, CtNode sender, CiMessage<?> receivedMessage, int whichRepeat) {
        // relay the same message, acting as a loyal node
        return receivedMessage;
    }

    protected void makeDecision(ContextView context, FloodPacket<?> selectedPacket) {
        final IntCounterMap<OmActions> actionMap = findMajorActionInCache();

        // add DEFAULT_ACTION for nodes, this node has not received any data
        for (int i = actionMap.size(); i < networkSize - 1; i++)
            actionMap.inc(DEFAULT_ACTION);

        if (actionMap.hasSeveralMaxKeys() <= 1) {
            OmActions decision = actionMap.getMaxKey().getKey();

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

    @NotNull
    protected IntCounterMap<OmActions> findMajorActionInCache() {
        IntCounterMap<OmActions> actionCounter = new IntCounterMap<>();
        for (OmMessageStatus status : msgCache) {
            OmActions action = status.nodeAction();
            if (action == OmActions.Unknown)
                action = DEFAULT_ACTION;

            actionCounter.inc(action);
        }
        return actionCounter;
    }

    protected void initialize(ContextView context) {
        if (networkSize == 0) {
            networkSize = context.getNetGraph().getNodeCount();
            msgCache = new HashSet<>();
        }
    }

}
