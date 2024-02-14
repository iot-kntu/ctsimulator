package ir.ac.kntu.SynchronousTransmission.blueflood;

import ir.ac.kntu.SynchronousTransmission.BlueFloodSettings;

import java.lang.reflect.InvocationTargetException;

public enum NodeFloodStrategy {
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
    FaultyInitiate(SilentFloodStrategy.class),

    /**
     * Node floods faulty packet in all of its slots.
     */
    FaultyFlood(SilentFloodStrategy.class),

    /**
     * Node floods faulty packets or it is silent. This is the combination of
     * the previous three modes.
     */
    SilentAndFaulty(SilentFloodStrategy.class),

    /**
     * Node floods faulty packet in all slots, not just its slots
     */
    AlwaysFaulty(SilentFloodStrategy.class);

    private final Class<? extends FloodStrategy> floodStrategy;

    NodeFloodStrategy(Class<? extends FloodStrategy> floodStrategy) {
        this.floodStrategy = floodStrategy;
    }

    public Class<? extends FloodStrategy> getFloodStrategy() {
        return floodStrategy;
    }

    public FloodStrategy getStrategy(BlueFloodSettings settings) throws NoSuchMethodException,
            InvocationTargetException, InstantiationException, IllegalAccessException {

        return getFloodStrategy().getDeclaredConstructor(BlueFloodSettings.class).newInstance(settings);
    }


}

