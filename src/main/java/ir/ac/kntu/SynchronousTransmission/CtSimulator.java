package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.*;

import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Concurrent Transmission network simulator
 */
public class CtSimulator {

    private final PriorityQueue<SimEvent> eventQueue = new PriorityQueue<>();
    private final SimulationContext context;
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private CtSimulator(NetGraph graph, ConcurrentTransmissionApplication application) {
        Objects.requireNonNull(graph);
        Objects.requireNonNull(application);

        this.context = new SimulationContext();
        this.context.netGraph = graph;
        this.context.rootApplication = application;
    }

    public static CtSimulator createInstance(NetGraph graph, ConcurrentTransmissionApplication application) {
        CtSimulator instance = new CtSimulator(graph, application);
        instance.context.simulator = instance;
        return instance;
    }

    public void start() {

        scheduleEvent(new SimulationStartEvent(0));

        context.time = -1;
        while (!eventQueue.isEmpty()) {

            try {
                final SimEvent event = eventQueue.poll();
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

    public void schedulePacket(FloodPacket<?> packet) {
        final List<SimEvent> existingCtEvents = eventQueue
                .stream()
                .filter(ev -> ev instanceof CtPacketsEvent)
                .filter(ev -> {
                    CtPacketsEvent event = (CtPacketsEvent) ev;
                    return event.getTime() == packet.time() && event.getReceiver().equals(packet.receiver());
                }).toList();

        if (existingCtEvents.isEmpty()) {
            CtPacketsEvent packetsEvent = new CtPacketsEvent(packet.time(), packet);
            eventQueue.add(packetsEvent);
        }
        else {
            if (existingCtEvents.size() > 1)
                throw new IllegalStateException("Several CtEvents exists:\n" + existingCtEvents);

            final CtEvent existingCtEvent = (CtEvent) existingCtEvents.get(0);
            existingCtEvent.addPacket(packet);
        }
    }

    public void scheduleEvent(SimEvent simEvent) {
        eventQueue.add(simEvent);
        logger.log(Level.FINE, "Event scheduled:" + simEvent);
    }

}
