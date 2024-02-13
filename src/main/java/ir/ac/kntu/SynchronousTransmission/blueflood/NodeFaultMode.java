package ir.ac.kntu.SynchronousTransmission.blueflood;

public enum NodeFaultMode {

    /**
     * No faulty
     */
    Normal,

    /**
     * Node does not send anything
     */
    OnlySilent,

    /**
     * Node sends faulty packet only in its turn
     * and may send different packets in flood repeats.
     */
    FaultyInitiate,

    /**
     * Node floods faulty packet in all of its slots.
     */
    FaultyFlood,

    /**
     * Node floods faulty packets or it is silent. This is the combination of
     * the previous three modes.
     */
    SilentAndFaulty,

    /**
     * Node floods faulty packet in all slots, not just its slots
     */
    AlwaysFaulty,
}

