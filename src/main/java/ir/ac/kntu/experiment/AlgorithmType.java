package ir.ac.kntu.experiment;

public enum AlgorithmType {
    WIRELESS_PAXOS,
    WIRELESS_MULTIPAXOS,
    A2_WIRELESS_MULTIPAXOS,
    CHAOS_2PC,
    CHAOS_3PC,
    BLUEFLOOD_PAXOS,
    BLUEFLOOD_2PC,
    BLUEFLOOD_3PC,
    BLUEFLOOD_TOM;

    public static AlgorithmType fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase()
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "WIRELESS_PAXOS", "WPAXOS" -> WIRELESS_PAXOS;
            case "WIRELESS_MULTIPAXOS", "WIRELESS_MULTI_PAXOS", "WMULTIPAXOS" -> WIRELESS_MULTIPAXOS;
            case "A2_WIRELESS_MULTIPAXOS", "A2_WMULTIPAXOS", "A2_MULTIPAXOS" -> A2_WIRELESS_MULTIPAXOS;
            case "CHAOS_2PC", "2PC", "TWO_PC", "TWOPC" -> CHAOS_2PC;
            case "CHAOS_3PC", "3PC", "THREE_PC", "THREEPC" -> CHAOS_3PC;
            case "BLUEFLOOD_PAXOS", "BF_PAXOS" -> BLUEFLOOD_PAXOS;
            case "BLUEFLOOD_2PC", "BF_2PC" -> BLUEFLOOD_2PC;
            case "BLUEFLOOD_3PC", "BF_3PC" -> BLUEFLOOD_3PC;
            case "BLUEFLOOD_TOM", "BLUEFLOOD_TOTAL_ORDER", "TOM" -> BLUEFLOOD_TOM;
            default -> throw new IllegalArgumentException("Unknown algorithm: " + value);
        };
    }
}
