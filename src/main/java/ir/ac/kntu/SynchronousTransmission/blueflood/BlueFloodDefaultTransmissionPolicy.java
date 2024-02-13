package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.NetGraph;
import ir.ac.kntu.SynchronousTransmission.Node;
import ir.ac.kntu.SynchronousTransmission.NodeState;
import ir.ac.kntu.SynchronousTransmission.StNetworkTime;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Default transmission policy is listen all slots to receive a valid packet
 * then flood N consecutive slots, then go to deep sleep.
 */
public class BlueFloodDefaultTransmissionPolicy implements BlueFloodTransmissionPolicy {

    private final int floodRepeatCount;
    private final NetGraph netGraph;
    private final Map<StNetworkTime, Map<Node, List<NodeState>>> stateHistory;
    private Map<Node, List<NodeState>> nodeStateMap;

    public BlueFloodDefaultTransmissionPolicy(int floodRepeatCount, NetGraph netGraph) {
        this.floodRepeatCount = floodRepeatCount;
        this.netGraph = netGraph;
        this.stateHistory = new HashMap<>();
    }

    @Override
    public int getFloodRepeatCount() {
        return floodRepeatCount;
    }

    @Override
    public StNetworkTime getNetworkTime(long time) {
        final int totalSlotsOfRound = getTotalSlotsOfRound();
        int round = (int) (1.0 * time / totalSlotsOfRound);
        int slot = (int) (time % totalSlotsOfRound);

        return new StNetworkTime(round, slot);
    }

    @Override
    public void newRound(StNetworkTime networkTime, Node initiator) {
        Objects.requireNonNull(networkTime);
        Objects.requireNonNull(initiator);

        if (networkTime.round() > 0)
            stateHistory.put(networkTime, nodeStateMap);

        this.nodeStateMap = new HashMap<>(netGraph.getNodeCount());
        netGraph.getNodes().forEach(node ->
                                    {
                                        List<NodeState> states = new ArrayList<>(getTotalSlotsOfRound());
                                        for (int i = 0; i < getTotalSlotsOfRound(); i++)
                                            states.add(NodeState.Listen);
                                        this.nodeStateMap.put(node, states);
                                    }
        );

        if(nodeStateMap.get(initiator) == null)
            System.out.println("Wow");
        nodeStateMap.get(initiator).set(0, NodeState.Flood);
        IntStream.range(1, getTotalSlotsOfRound())
                 .forEach(i -> nodeStateMap.get(initiator).set(i, NodeState.Sleep));

    }

    @Override
    public void newSlot(int slot) {
    }

    @Override
    public void newPacketReceived(Node node, int slot) {
        // when a new packet is received by a node, it floods
        //  it for floodRepeatCount times and then goes to sleep
        for (int i = 1; i <= floodRepeatCount; i++)
            nodeStateMap.get(node).set(slot + i, NodeState.Flood);

        for (int i = slot + floodRepeatCount + 1; i < getTotalSlotsOfRound(); i++)
            nodeStateMap.get(node).set(i, NodeState.Sleep);
    }

    @Override
    public NodeState getNodeState(Node node, int slot) {
        return nodeStateMap.get(node).get(slot);
    }

    @Override
    public int getTotalSlotsOfRound() {
        return 2 * netGraph.getDiameter() + floodRepeatCount;
    }

    @Override
    public String printHistory() {

        StringBuilder builder = new StringBuilder();

        builder.append(String.format("%1$5s", "*"));

        for (StNetworkTime StNetworkTime : stateHistory.keySet())
            builder.append(String.format("%1$5s", StNetworkTime.round()));
        builder.append('\n');

        builder.append(String.format("%1$5s", "*"));
        for (StNetworkTime StNetworkTime : stateHistory.keySet())
            builder.append(String.format("%1$5s", StNetworkTime.slot()));
        builder.append('\n');

        for (Node node : netGraph.getNodes()) {
            StringBuilder row = new StringBuilder(String.format("%1$5s", "N[" + node.getId() + "]"));
            final Collection<Map<Node, List<NodeState>>> values = stateHistory.values();
            for (Map<Node, List<NodeState>> value : values) {
                final List<NodeState> states = value.get(node);
                for (NodeState state : states)
                    row.append(String.format("%1$5c", state.getSymbol()));
            }
            builder.append(row).append('\n');
        }
        return builder.toString();
    }

    @Override
    public void printCurrentNodeStates() {

        StringBuilder builder = new StringBuilder();

        for (Node node : netGraph.getNodes()) {
            StringBuilder row = new StringBuilder(String.format("%1$5s", "N[" + node.getId() + "]"));
            nodeStateMap.get(node).stream()
                        .map(state -> String.format("%1$5c", state.getSymbol()))
                        .forEach(row::append);
            builder.append(row).append('\n');
        }

        System.out.println(builder);

    }

}
