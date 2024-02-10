package ir.ac.kntu.SynchronousTransmission;

public class SimulationContext implements ContextView {

    StSimulator simulator;
    NetGraph netGraph;
    StApplication rootApplication;
    long time;
    //int round;
    //int slot;

    public SimulationContext() {

    }

    @Override
    public StSimulator getSimulator() {
        return simulator;
    }

    @Override
    public StApplication getApplication() {
        return rootApplication;
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
