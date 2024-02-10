package ir.ac.kntu.SynchronousTransmission;

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

        context.time = 0;

        context.getApplication().simulationStarting(context);

        //context.getApplication().simulationTimeProgressed(context);

        while (!eventQueue.isEmpty()) {

            try {
                final StEvent event = eventQueue.poll();
                logger.log(Level.FINE, "Event popped:" + event);

                final int timeDiff = (int) (event.getTime() - context.time);
                if (timeDiff > 0) {

                    context.getApplication().simulationTimeProgressed(context);
                }
                context.time = event.getTime();

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
