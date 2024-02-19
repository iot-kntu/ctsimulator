package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.blueflood.floodstrategies.*;

import java.lang.reflect.InvocationTargetException;

public enum NodeFloodStrategyTypes {
    /**
     * No faulty
     */
    Normal(NonFaultyFloodStrategy.class),

    /**
     * Node does not send anything
     */
    OnlySilent(SilentFloodStrategy.class),

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

    NodeFloodStrategyTypes(Class<? extends NodeFloodStrategy> floodStrategy) {
        this.floodStrategy = floodStrategy;
    }

    public Class<? extends NodeFloodStrategy> getFloodStrategy() {
        return floodStrategy;
    }

    public NodeFloodStrategy getStrategy() throws NoSuchMethodException,
            InvocationTargetException, InstantiationException, IllegalAccessException {

        return getFloodStrategy().getDeclaredConstructor()
                                 .newInstance();
    }


}

