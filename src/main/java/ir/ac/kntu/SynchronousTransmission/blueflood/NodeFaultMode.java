package ir.ac.kntu.SynchronousTransmission.blueflood;

public enum NodeFaultMode {

    /**
     * No faulty
     */
    Normal,

    /**
     * Randomly a mode is selected at each turn
     */
    Random,

    /**
     * Node does not send anything
     */
    Silent,

    /**
     * Node sends faulty packet only in its turn
     */
    FaultyInitiate,

    /**
     * Node floods faulty packet in its slot
     */
    FaultyFlood,

    /**
     * Node floods faulty packet in all slots
     */
    AlwaysFaulty,
}
