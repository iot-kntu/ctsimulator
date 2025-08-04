package ir.ac.kntu.concurrenttransmission.chaos.state;

import ir.ac.kntu.concurrenttransmission.ContextView;
import ir.ac.kntu.concurrenttransmission.chaos.NodeStateBehavior;
import ir.ac.kntu.concurrenttransmission.chaos.nodes.StatefulNode;
import ir.ac.kntu.concurrenttransmission.events.FloodPacket;

/**
 * Behavior of a node when it has completed its work for the round and is asleep.
 */
public class SleepingState implements NodeStateBehavior {

    @Override
    public void onPacketReceived(StatefulNode node, ContextView context, FloodPacket<?> capturedPacket) {
        // A sleeping node does nothing.
    }

    @Override
    public void onEnter(StatefulNode node, ContextView context) {
        // Nothing to do when entering sleep state.
    }

    @Override
    public ChaosNodeState getStateAsEnum() {
        return ChaosNodeState.Sleep;
    }
}