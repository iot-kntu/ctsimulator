package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.SimulationStartEvent;

import java.util.Objects;
import java.util.PriorityQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StSimulator {

    private final PriorityQueue<StEvent> eventQueue = new PriorityQueue<>();
    private final SimulationContext context;
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private StSimulator(NetGraph graph, StApplication application) {
        Objects.requireNonNull(graph);
        Objects.requireNonNull(application);

        this.context = new SimulationContext();
        this.context.netGraph = graph;
        this.context.rootApplication = application;
    }

    public static StSimulator createInstance(NetGraph graph, StApplication application) {
        StSimulator instance = new StSimulator(graph, application);
        instance.context.simulator = instance;
        return instance;
    }

    public void start() {

        scheduleEvent(new SimulationStartEvent(0));

        context.time = -1;
        while (!eventQueue.isEmpty()) {

            try {
                final StEvent event = eventQueue.poll();
                logger.log(Level.FINE, "Event popped:" + event);

                final int timeDiff = (int) (event.getTime() - context.time);
                if (timeDiff > 0) {
                    context.time = event.getTime();
                    context.getApplication().simulationTimeProgressed(context);
                }

                event.handle(context);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Error in simulation start method", e);
            }
        }
    }

    public void scheduleEvent(StEvent stEvent) {
        eventQueue.add(stEvent);
        logger.log(Level.FINE, "Event scheduled:" + stEvent);
    }

}
