package ir.ac.kntu.SynchronousTransmission;

/**
 * Encapsulates round and slot
 * @param round rounds the simulation executed
 * @param slot the slot number in a round
 */
public record StNetworkTime(int round, int slot) {

}
