package ir.ac.kntu.concurrenttransmission;

import java.util.Random;

/**
 * A class to model and calculate received signal strength based on physical models.
 * This class implements the log-distance path loss model.
 */
public class SignalModel {
    /**
     * The initial transmit power of the nodes in dBm.
     */
    private static final double TRANSMIT_POWER_DBM = 0.0;

    /**
     * The path-loss exponent (n).
     */
    private static final double PATH_LOSS_EXPONENT = 2.7;

    /**
     * The standard deviation (in dB) of the log-normal shadowing (fading).
     * This adds randomness to the signal strength. A value of 2.0 is a reasonable start.
     */
    private static final double FADING_STANDARD_DEVIATION_DB = 2.0;

    private final Random random = new Random();

    /**
     * Calculates the received signal strength in dBm, including a random fading component.
     *
     * @param distance The distance between the transmitter and receiver.
     * @return The received signal strength in dBm.
     */
    public double calculateSignalStrengthDb(double distance) {
        if (distance < 1.0) {
            distance = 1.0;
        }

        // 1. Calculate the deterministic path loss
        double pathLoss = 10 * PATH_LOSS_EXPONENT * Math.log10(distance);
        double deterministicSignalStrength = TRANSMIT_POWER_DBM - pathLoss;

        // 2. Add a random component for fading (log-normal shadowing)
        // This simulates the unpredictable variations in a real environment.
        double fading = random.nextGaussian() * FADING_STANDARD_DEVIATION_DB;

        return deterministicSignalStrength + fading;
    }

    /**
     * Converts power from dBm to milliwatts (mW).
     * @param dbm The power in dBm.
     * @return The power in mW.
     */
    public double dbmToMilliwatts(double dbm) {
        return Math.pow(10, dbm / 10.0);
    }

    /**
     * Converts power from milliwatts (mW) to dBm.
     * @param milliwatts The power in mW.
     * @return The power in dBm.
     */
    public double milliwattsToDbm(double milliwatts) {
        if (milliwatts <= 0) {
            return -Double.MAX_VALUE;
        }
        return 10 * Math.log10(milliwatts);
    }
}