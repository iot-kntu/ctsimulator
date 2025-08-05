package ir.ac.kntu.distributedsystems.a2.aggregation;

import ir.ac.kntu.concurrenttransmission.chaos.Flag;

/**
 * A simple boolean flag representing whether a node has participated in the round.
 */
public enum ParticipationFlag implements Flag {
    PARTICIPATED,
    NOT_PARTICIPATED;

    @Override
    public Flag merge(Flag other) {
        if (!(other instanceof ParticipationFlag)) {
            return this; // Or throw an exception
        }
        // The merged state is PARTICIPATED if either this or the other is PARTICIPATED.
        return (this == PARTICIPATED || other == PARTICIPATED) ? PARTICIPATED : NOT_PARTICIPATED;
    }
}