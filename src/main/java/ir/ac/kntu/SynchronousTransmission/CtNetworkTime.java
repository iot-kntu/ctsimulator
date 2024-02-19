package ir.ac.kntu.SynchronousTransmission;

/**
 * Concurrent Transmission network time which encapsulates round and slot
 * @param round rounds the simulation executed
 * @param slot the slot number in a round
 */
public record CtNetworkTime(int round, int slot) implements Comparable<CtNetworkTime> {

    @Override
    public int compareTo(CtNetworkTime o) {

        return this.round() == o.round()
                ? this.slot() - o.slot()
                : this.round() - o.round();
    }

}
