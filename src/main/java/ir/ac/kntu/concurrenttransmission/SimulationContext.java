package ir.ac.kntu.concurrenttransmission;

public class SimulationContext implements ContextView {

    CtSimulator simulator;
    NetGraph netGraph;
    ConcurrentTransmissionApplication application;
    long time;
    //int round;
    //int slot;

    public SimulationContext() {

    }

    @Override
    public CtSimulator getSimulator() {
        return simulator;
    }

    @Override
    public ConcurrentTransmissionApplication getApplication() {
        return application;
    }

    @Override
    public long getTime() {
        return time;
    }

    //@Override
    //public int getRound() {
    //    return round;
    //}
    //
    //@Override
    //public int getSlot() {
    //    return slot;
    //}

    @Override
    public NetGraph getNetGraph() {
        return netGraph;
    }

}
