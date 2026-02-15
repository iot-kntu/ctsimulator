package ir.ac.kntu.distributedsystems.a2.wmultipaxos;

import java.util.Objects;

public record WirelessMultiPaxosPayload(
        WirelessMultiPaxosPhase phase,
        int proposalNumber,
        int instanceId,
        int minProposal,
        int acceptedProposal,
        Object acceptedValue,
        Object value,
        boolean done) {

    public WirelessMultiPaxosPayload {
        Objects.requireNonNull(phase, "phase");
    }

    public static WirelessMultiPaxosPayload prepare(int proposalNumber,
                                                    int instanceId,
                                                    int minProposal,
                                                    int acceptedProposal,
                                                    Object acceptedValue) {
        return new WirelessMultiPaxosPayload(WirelessMultiPaxosPhase.PREPARE, proposalNumber, instanceId,
                minProposal, acceptedProposal, acceptedValue, null, false);
    }

    public static WirelessMultiPaxosPayload accept(int proposalNumber,
                                                   int instanceId,
                                                   int minProposal,
                                                   int acceptedProposal,
                                                   Object acceptedValue,
                                                   Object value,
                                                   boolean done) {
        return new WirelessMultiPaxosPayload(WirelessMultiPaxosPhase.ACCEPT, proposalNumber, instanceId,
                minProposal, acceptedProposal, acceptedValue, value, done);
    }
}
