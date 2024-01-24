package ir.ac.kntu.SynchronousTransmission;

public class SimulationContext implements ReadOnlyContext {

    StSimulator simulator;
    NetGraph netGraph;
    StApplication rootApplication;
    Node roundInitiator;
    long time;
    int round;
    int slot;

    public SimulationContext() {
        roundInitiator = Node.NULL_NODE;
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

    @Override
    public int getRound() {
        return round;
    }

    @Override
    public int getSlot() {
        return slot;
    }

    @Override
    public NetGraph getNetGraph() {
        return netGraph;
    }

    @Override
    public Node getRoundInitiator() {
        return roundInitiator;
    }

}
