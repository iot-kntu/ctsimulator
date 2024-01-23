package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;
import ir.ac.kntu.SynchronousTransmission.events.StInitiateFloodEvent;

import java.util.*;
import java.util.logging.Logger;

public class StSimulator {

    private final SimulationSettings settings;
    private final PriorityQueue<StEvent> eventQueue = new PriorityQueue<>();
    private final SimulationContext context;
    private Logger logger = Logger.getLogger(this.getClass().getName());
    /**
     * Which node id's turn
     */
    private int turnNodeId = 1;

    private StSimulator(SimulationSettings settings, NetGraph graph, StApplication application) {
        Objects.requireNonNull(settings);
        Objects.requireNonNull(graph);
        Objects.requireNonNull(application);

        this.settings = settings;
        this.context = new SimulationContext();
        this.context.netGraph = graph;
        this.context.addApplication(application);
    }

    public static StSimulator createInstance(SimulationSettings settings, NetGraph graph, StApplication application) {
        StSimulator instance = new StSimulator(settings, graph, application);
        instance.context.simulator = instance;
        return instance;
    }

    public void start() {

        context.round = 1;
        context.slot = 1;

        context.roundInitiator = context.netGraph.getNodeById(turnNodeId);
        StInitiateFloodEvent initiateFloodEvent = new StInitiateFloodEvent(context.time);
        addStEvent(initiateFloodEvent);

        while (!eventQueue.isEmpty()) {

            final StEvent event = eventQueue.poll();
            context.slot = (int) (event.getTime() - context.time) + 1;
            context.time = event.getTime();

            event.handle(context);
        }
    }

    public void roundFinished() {

        context.round++;
        context.slot = 1;

        if (settings.stInitiatorStrategy() == StInitiatorStrategy.RoundRobin)
            turnNodeId = (turnNodeId + 1) % context.netGraph.getNodeCount();

        context.roundInitiator = context.netGraph.getNodeById(turnNodeId);
        StInitiateFloodEvent initiateFloodEvent = new StInitiateFloodEvent(context.time + 1);
        addStEvent(initiateFloodEvent);
    }

    public <T> void flood(Node sender, StMessage<T> message) {
        final List<Node> neighbors = context.netGraph.getNodeNeighbors(sender);

        for (int i = 1; i <= settings.floodRepeats(); i++) {
            int finalI = i;
            neighbors.forEach(node -> addStEvent(
                    new StFloodPacket<>(context.time + finalI, message, sender, node)));
        }
    }

    private void addStEvent(StEvent stEvent) {
        eventQueue.add(stEvent);
    }

}
