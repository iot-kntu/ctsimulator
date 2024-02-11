package ir.ac.kntu.SynchronousTransmission;

/**
 * Encapsulates round and slot
 * @param round rounds the simulation executed
 * @param slot the slot number in a round
 */
public record StNetworkTime(int round, int slot) implements Comparable<StNetworkTime> {

    @Override
    public int compareTo(StNetworkTime o) {

        return this.round() == o.round()
                ? this.slot() - o.slot()
                : this.round() - o.round();
    }

}
