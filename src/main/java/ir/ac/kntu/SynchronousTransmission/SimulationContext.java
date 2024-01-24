package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimulationContext implements ReadOnlyContext {

    StSimulator simulator;
    NetGraph netGraph;
    List<StApplication> applications;
    Node roundInitiator;
    long time;
    int round;
    int slot;

    public SimulationContext() {
        applications = new ArrayList<>();
        roundInitiator = Node.NULL_NODE;
    }

    @Override
    public StSimulator getSimulator() {
        return simulator;
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

    @Override
    public List<StApplication> getApplications() {
        return Collections.unmodifiableList(applications);
    }

    void addApplication(StApplication application){
        applications.add(application);
    }

    /**
     * Only the main application which given by user and it is stored in the 0 position of the
     * applications list gives the final message.
     * @param context the read only context
     * @return the generated message by the user-given application
     */

    @Override
    public void onTimeProgress(ReadOnlyContext context) {
        applications.forEach(application -> application.onTimeProgress(this));
    }

    @Override
    public StMessage<?> onInitiateFlood(ReadOnlyContext context) {
        final StMessage<?> stMessage = applications.get(0).onInitiateFlood(this);
        for (int i = 1; i < applications.size(); i++) {
            final StApplication app = applications.get(i);
            app.onInitiateFlood(context);
        }

        return stMessage;
    }

    @Override
    public void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context){
        applications.forEach(application -> application.onPacketReceive(packet, this));
    }
}
