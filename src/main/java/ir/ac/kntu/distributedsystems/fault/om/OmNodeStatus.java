package ir.ac.kntu.distributedsystems.fault.om;

import ir.ac.kntu.concurrenttransmission.CtNode;

import java.util.HashMap;

/**
 * A record to keep status of a node in Oral Message scenarios
 */
public final class OmNodeStatus {

    private final CtNode node;
    private final HashMap<CtNode, OmAction> receivedNodeActions;
    private boolean isFinished;

    public OmNodeStatus(CtNode node) {
        this.node = node;
        this.receivedNodeActions = new HashMap<>();
    }

    public CtNode getNode() {
        return node;
    }

    public HashMap<CtNode, OmAction> getReceivedNodeActions() {
        return receivedNodeActions;
    }

    public void setFinished(boolean finished) {
        isFinished = finished;
    }

    public boolean isFinished() {
        return isFinished;
    }

    /**
     * Returns the action is associated to the node with nodeId or returns the
     * defaultAction
     * @param nodeId the ID of the node searching for its recorded action
     * @param defaultAction the action is returned if no record found for the given nodeId
     *
     */
    public OmAction findActionOfNode(int nodeId, OmAction defaultAction) {
        for (CtNode ctNode : receivedNodeActions.keySet()) {
            if(ctNode.getId() == nodeId)
                return receivedNodeActions.get(ctNode);
        }

        return defaultAction;
    }

    public OmAction findActionOfNode(CtNode node, OmAction defaultAction) {
        for (CtNode ctNode : receivedNodeActions.keySet()) {
            if(ctNode.equals(node))
                return receivedNodeActions.get(ctNode);
        }

        return defaultAction;
    }

    public boolean isActionRecordedForNode(CtNode node) {
        return receivedNodeActions.keySet().stream().anyMatch(ctNode -> ctNode.equals(node));
    }
}
