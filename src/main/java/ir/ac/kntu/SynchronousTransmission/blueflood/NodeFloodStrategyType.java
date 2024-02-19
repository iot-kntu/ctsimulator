package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.blueflood.floodstrategies.*;

public enum NodeFloodStrategyType {
    /**
     * No faulty
     */
    Normal(NonFaultyFloodStrategy.class),

    /**
     * Node does not send anything
     */
    Silent(SilentFloodStrategy.class),

    /**
     * Node sends faulty packet only in its turn
     * and may send different packets in flood repeats.
     */
    FaultyInitiate(FaultyInitiateFloodStrategy.class),

    /**
     * Node floods faulty packet in all of its slots.
     */
    FaultyFlood(FaultyFloodStrategy.class),

    /**
     * Node floods faulty packets or it is silent. This is the combination of
     * the previous three modes.
     */
    SilentAndFaulty(SilentAndFaultyFloodStrategy.class);

    private final Class<? extends NodeFloodStrategy> floodStrategy;

    NodeFloodStrategyType(Class<? extends NodeFloodStrategy> floodStrategy) {
        this.floodStrategy = floodStrategy;
    }

    public Class<? extends NodeFloodStrategy> getFloodStrategyClass() {
        return floodStrategy;
    }

}

