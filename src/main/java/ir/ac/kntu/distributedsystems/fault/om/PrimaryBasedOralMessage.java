package ir.ac.kntu.distributedsystems.fault.om;

import ir.ac.kntu.concurrenttransmission.CiMessage;
import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.blueflood.BlueFloodNodeListener;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

/**
 * The implementation of Oral Message algorithm for coordinator-based group
 * which includes one Major General and n-1 lieutenants. The implementation
 * is done for only one message.
 */
public class PrimaryBasedOralMessage extends ParentOralMessageSystem implements BlueFloodNodeListener {

    public static final int COORDINATOR_ID = 0;
    private final OmAction coordinatorAction;

    private HashMap<CtNode, OmNodeStatus> nodeStatusMap = new HashMap<>();

    public PrimaryBasedOralMessage(OmAction DEFAULT_ACTION, OmAction coordinatorAction) {
        super(DEFAULT_ACTION);
        this.coordinatorAction = coordinatorAction;
    }

    @Override
    public boolean ctPacketsReceived(ContextView context, List<FloodPacket<?>> packets,
                                     FloodPacket<?> packet,
                                     boolean areSimilar) {
        // ensure data structures initialization
        initialize(context);

        final CtNode thisNode = packet.receiver();
        final OmNodeStatus thisNodeStatus = getNodeStatus(thisNode);

        // this has made the decision
        if (thisNodeStatus.isFinished())
            return false;

        final CiMessage<OmMessage> ciMessage = (CiMessage<OmMessage>) packet.ciMessage();
        CtNode initiator = ciMessage.initiator();
        final OmAction nodeAction = ciMessage.content().nodeAction();

        // if this is a new message
        if (!thisNodeStatus.isActionRecordedForNode(initiator)) {
            thisNodeStatus.getReceivedNodeActions().put(initiator, nodeAction);

            // if the node has received message from all of its neighbors
            if (thisNodeStatus.getReceivedNodeActions().size() == networkSize - 1) {

                thisNodeStatus.setFinished(true);

                final OmAction decision = makeDecision(context, thisNodeStatus);

                logger.log(Level.INFO, context.getApplication().getNetworkTime() + "::" +
                        thisNode + " made the decision as " + decision);
            }

            return true;
        }

        return false;
    }

    @Override
    public CiMessage<?> initiateMessage(ContextView context, CtNode initiator, int whichRepeat) {
        initialize(context);

        final CtNode thisNode = initiator;
        final OmNodeStatus thisNodeStatus = getNodeStatus(thisNode);

        // if node is the General
        if (thisNode.getId() == COORDINATOR_ID) {
            if (thisNodeStatus.isFinished())
                return CiMessage.NULL_MESSAGE;
            else {
                // this app is written only for one message decision
                thisNodeStatus.setFinished(true);
                return new CiMessage<>(initiator, new OmMessage(coordinatorAction, ""));
            }
        }

        OmAction action = thisNodeStatus.findActionOfNode(COORDINATOR_ID, defaultAction);
        return new CiMessage<>(initiator, new OmMessage(action, ""));
    }

    protected void initialize(ContextView context) {
        if (networkSize == 0) {
            networkSize = context.getNetGraph().getNodeCount();
            nodeStatusMap = new HashMap<>();
        }
    }

    @NotNull
    private OmNodeStatus getNodeStatus(CtNode node) {
        OmNodeStatus nodeStatus = nodeStatusMap.get(node);
        if (nodeStatus == null) {
            nodeStatus = new OmNodeStatus(node);
            nodeStatusMap.put(node, nodeStatus);
        }

        return nodeStatus;
    }


}
