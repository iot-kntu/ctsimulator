package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StInitiateFloodEvent;

import java.util.Objects;
import java.util.PriorityQueue;
import java.util.logging.Level;
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
        this.context.rootApplication = application;
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
        scheduleEvent(initiateFloodEvent);

        while (!eventQueue.isEmpty()) {

            try {
                final StEvent event = eventQueue.poll();
                logger.log(Level.FINE, "Event popped:" + event);
                final int timeDiff = (int) (event.getTime() - context.time);
                if (timeDiff > 0) {
                    context.slot = timeDiff + 1;
                    context.getApplication().onTimeProgress(context);
                }
                context.time = event.getTime();

                event.handle(context);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Error in simulation start method", e);
            }
        }
    }

    public void roundFinished() {

        context.round++;
        context.slot = 1;

        if (settings.stInitiatorStrategy() == StInitiatorStrategy.RoundRobin)
            turnNodeId = (turnNodeId + 1) % context.netGraph.getNodeCount();

        context.roundInitiator = context.netGraph.getNodeById(turnNodeId);
        StInitiateFloodEvent initiateFloodEvent = new StInitiateFloodEvent(context.time + 1);
        scheduleEvent(initiateFloodEvent);
    }

    public SimulationSettings getSettings() {
        return settings;
    }

    public void scheduleEvent(StEvent stEvent) {
        eventQueue.add(stEvent);
        logger.log(Level.FINE, "Event scheduled:" + stEvent);
    }

}
