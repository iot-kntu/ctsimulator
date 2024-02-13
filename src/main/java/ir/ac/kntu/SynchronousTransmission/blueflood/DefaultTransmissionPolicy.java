package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.*;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Default transmission policy is listen all slots to receive a valid packet
 * then flood N consecutive slots, then go to deep sleep.
 */
public class DefaultTransmissionPolicy implements TransmissionPolicy {

    private final int floodRepeatCount;
    private final NetGraph netGraph;
    private final SortedMap<StNetworkTime, SortedMap<Node, List<NodeState>>> stateHistory;
    private SortedMap<Node, List<NodeState>> nodeStateMap;

    public DefaultTransmissionPolicy(BlueFloodSettings settings, NetGraph netGraph) {
        this.floodRepeatCount = settings.floodRepeatCount();
        this.netGraph = netGraph;
        this.stateHistory = new TreeMap<>();
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

        this.nodeStateMap = new TreeMap<>();
        netGraph.getNodes().forEach(node ->
                                    {
                                        List<NodeState> states = new ArrayList<>(getTotalSlotsOfRound());
                                        for (int i = 0; i < getTotalSlotsOfRound(); i++)
                                            states.add(NodeState.Listen);
                                        this.nodeStateMap.put(node, states);
                                    }
        );

        IntStream.range(0, floodRepeatCount).forEach(i -> nodeStateMap.get(initiator).set(i, NodeState.Flood));
        IntStream.range(floodRepeatCount, getTotalSlotsOfRound())
                 .forEach(i -> nodeStateMap.get(initiator).set(i, NodeState.Sleep));

    }

    @Override
    public void newSlot(int slot) {
    }

    @Override
    public void newPacketReceived(Node node, int slot) {
        // when a new packet is received by a node, it floods
        //  it for floodRepeatCount times and then goes to sleep
        for (int i = slot + 1; i <= slot + floodRepeatCount; i++)
            nodeStateMap.get(node).set(i, NodeState.Flood);

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

        builder.append(String.format("%1$5s", "R"));

        for (StNetworkTime StNetworkTime : stateHistory.keySet()) {
            for (int i = 0; i < getTotalSlotsOfRound(); i++) {
                builder.append(String.format("%1$5s", StNetworkTime.round()));
            }

            builder.append(String.format("%1$5s", "|"));
        }
        builder.append('\n');

        builder.append(String.format("%1$5s", "S"));
        final int rounds = stateHistory.keySet().size();
        for (int i = 0; i < rounds; i++) {
            for (int j = 0; j < getTotalSlotsOfRound(); j++) {
                builder.append(String.format("%1$5s", j));
            }

            builder.append(String.format("%1$5s", "|"));
        }
        builder.append('\n');

        builder.append(String.format("%1$5s", "|"));
        for (int i = 0; i < rounds; i++) {
            for (int j = 0; j < getTotalSlotsOfRound(); j++) {
                builder.append(String.format("%1$5s", "-----"));
            }
            builder.append(String.format("%1$5s", "|"));
        }
        builder.append('\n');

        for (Node node : netGraph.getNodes()) {
            StringBuilder row = new StringBuilder(String.format("%1$5s", "N[" + node.getId() + "]"));
            final Collection<SortedMap<Node, List<NodeState>>> values = stateHistory.values();
            for (Map<Node, List<NodeState>> value : values) {
                final List<NodeState> states = value.get(node);
                for (NodeState state : states)
                    row.append(String.format("%1$5c", state.getSymbol()));

                row.append(String.format("%1$5s", "|"));
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
