package ir.ac.kntu.concurrenttransmission.blueflood;

import ir.ac.kntu.concurrenttransmission.CtNetworkTime;
import ir.ac.kntu.concurrenttransmission.CtNode;
import ir.ac.kntu.concurrenttransmission.NetGraph;
import ir.ac.kntu.concurrenttransmission.NodeState;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Based on BlueFlood design, every node in DefaultTransmissionPolicy listens in all slots
 * by default to receive a valid packet. The initiator node state is changed to Flood and starts flooding.
 * Then, receiver nodes receive a valid packet and flood N consecutive slots, and then
 * go to deep sleep state until the next round.
 */
public class DefaultTransmissionPolicy implements TransmissionPolicy {

    private final int floodRepeatCount;
    private final NetGraph netGraph;
    private final SortedMap<CtNetworkTime, SortedMap<CtNode, List<NodeState>>> stateHistory;
    private SortedMap<CtNode, List<NodeState>> nodeStateMap;

    public DefaultTransmissionPolicy(int floodRepeatCount, NetGraph netGraph) {
        this.floodRepeatCount = floodRepeatCount;
        this.netGraph = netGraph;
        this.stateHistory = new TreeMap<>();
    }

    @Override
    public int getFloodRepeatCount() {
        return floodRepeatCount;
    }

    @Override
    public CtNetworkTime getNetworkTime(long time) {
        final int totalSlotsOfRound = getTotalSlotsOfRound();
        int round = (int) (1.0 * time / totalSlotsOfRound);
        int slot = (int) (time % totalSlotsOfRound);

        return new CtNetworkTime(round, slot);
    }

    @Override
    public void newRound(CtNetworkTime networkTime, CtNode initiator) {
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
    public void newPacketReceived(CtNode node, int slot) {
        // when a new packet is received by a node, it floods
        //  it for floodRepeatCount times and then goes to sleep
        for (int i = slot + 1; i <= slot + floodRepeatCount; i++)
            nodeStateMap.get(node).set(i, NodeState.Flood);

        for (int i = slot + floodRepeatCount + 1; i < getTotalSlotsOfRound(); i++)
            nodeStateMap.get(node).set(i, NodeState.Sleep);
    }

    @Override
    public NodeState getNodeState(CtNode node, int slot) {
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

        for (CtNetworkTime CtNetworkTime : stateHistory.keySet()) {
            for (int i = 0; i < getTotalSlotsOfRound(); i++) {
                builder.append(String.format("%1$5s", CtNetworkTime.round()));
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

        for (CtNode node : netGraph.getNodes()) {
            StringBuilder row = new StringBuilder(String.format("%1$5s", "N[" + node.getId() + "]"));
            final Collection<SortedMap<CtNode, List<NodeState>>> values = stateHistory.values();
            for (Map<CtNode, List<NodeState>> value : values) {
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

        for (CtNode node : netGraph.getNodes()) {
            StringBuilder row = new StringBuilder(String.format("%1$5s", "N[" + node.getId() + "]"));
            nodeStateMap.get(node).stream()
                        .map(state -> String.format("%1$5c", state.getSymbol()))
                        .forEach(row::append);
            builder.append(row).append('\n');
        }

        System.out.println(builder);

    }

}
