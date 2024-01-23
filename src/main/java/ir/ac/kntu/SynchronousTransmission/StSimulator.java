package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.logging.Logger;

public class StSimulator {

    private static StSimulator instance;
    private final SimulationSettings settings;
    private final PriorityQueue<StEvent> eventQueue = new PriorityQueue<>();
    private final SimulationContext context;
    private Logger logger = Logger.getLogger(this.getClass().getName());
    /**
     * Which node id's turn
     */
    private int turnNodeId = 0;

    private StSimulator(SimulationSettings settings, NetGraph graph, StApplication application) {
        this.settings = settings;
        this.context = new SimulationContext();
        this.context.netGraph = graph;
        this.context.application = application;
    }

    public static StSimulator createInstance(SimulationSettings settings, NetGraph graph, StApplication application) {
        Objects.requireNonNull(graph);
        Objects.requireNonNull(settings);

        instance = new StSimulator(settings, graph, application);
        instance.context.simulator = instance;
        return instance;
    }

    public static StSimulator getInstance() {
        Objects.requireNonNull(instance, "StSimulator instance has not been created.");
        return instance;
    }

    public void start() {

        context.round = 1;
        context.slot = 1;

        context.roundInitiator = context.netGraph.getNodeById(turnNodeId);

        while (!eventQueue.isEmpty()) {

            final StEvent event = eventQueue.poll();
            context.slot = (int) (event.getTime() - context.time);
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
    }

    public <T> void flood(Node sender, StMessage<T> message) {
        final List<Node> neighbors = context.netGraph.getNodeNeighbors(sender);
        for (int i = 1; i <= settings.floodRepeats(); i++) {
            int finalI = i;
            neighbors.forEach(node -> addStEvent(
                    new StFloodPacket<T>(context.time + finalI, message, sender , node)));
        }
    }

    private void addStEvent(StEvent stEvent) {
        eventQueue.add(stEvent);
    }

}
