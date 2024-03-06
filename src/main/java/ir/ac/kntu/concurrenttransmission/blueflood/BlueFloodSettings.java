package ir.ac.kntu.concurrenttransmission.blueflood;

/**
 *
 * @param lossProbability general loss probability
 * @param conflictProbability loss probability when receiving different packets
 * @param roundLimit maximum number of rounds, the application must be executed
 */
public record BlueFloodSettings(double lossProbability,
                                double conflictProbability,
                                int roundLimit) {

}
