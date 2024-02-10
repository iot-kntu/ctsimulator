package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.NodeState;
import ir.ac.kntu.SynchronousTransmission.StNetworkTime;

/**
 * Default transmission policy is listen all slots to receive a valid packet
 * then flood N consecutive slots, then go to deep sleep.
 */
public class BlueFloodDefaultTransmissionPolicy implements BlueFloodTransmissionPolicy {

    private final int floodRepeatCount;
    private final int graphDiameter;
    private final int graphNodeCount;

    public BlueFloodDefaultTransmissionPolicy(int floodRepeatCount, int graphDiameter, int graphNodeCount) {
        this.floodRepeatCount = floodRepeatCount;
        this.graphDiameter = graphDiameter;
        this.graphNodeCount = graphNodeCount;
    }

    @Override
    public int getFloodRepeatCount() {
        return floodRepeatCount;
    }

    @Override
    public StNetworkTime getNetworkTime(long time) {
        int totalSlotsOfRound = graphDiameter + floodRepeatCount;
        int round = (int) (1.0 * time / totalSlotsOfRound);
        int slot = (int) (time % totalSlotsOfRound);

        return new StNetworkTime(round, slot);
    }

    public NodeState getNodeState(Node node, int round, int slot){

        int totalSlotsOfRound = graphDiameter + floodRepeatCount;

        int initiatorId = round % graphNodeCount;
        boolean isInitiateSlot = slot == 0;


        //Note that nodeId start from 1
        // FIXME: 2/4/24 return correct node state
        return NodeState.Sleep;
    }


    @Override
    public void newRoundStarted(Node inode) {
        //Map<Node, NodeState> nodesStateMap = new HashMap<>();
        //for (Node node : netGraph.getNodes()) {
        //    if(node.getId() == initiatorId)
        //        nodesStateMap.put(node, NodeState.Flood);
        //}

    }
}
