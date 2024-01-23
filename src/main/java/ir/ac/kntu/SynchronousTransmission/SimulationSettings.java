package ir.ac.kntu.SynchronousTransmission;

public record SimulationSettings(int floodRepeats,
                                 StInitiatorStrategy stInitiatorStrategy,
                                 double lossProbability) {

    public static SimulationSettings DefaultSettings = new SimulationSettings(1, StInitiatorStrategy.RoundRobin, 0);
}
