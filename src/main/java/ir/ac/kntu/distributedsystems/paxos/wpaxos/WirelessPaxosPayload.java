package ir.ac.kntu.distributedsystems.paxos.wpaxos;

/**
 * Wireless Paxos payload with exactly four fields as in "Paxos Made Wireless":
 * phase, proposal number, value (or empty), and a phase-specific number field.
 */
public record WirelessPaxosPayload(
        WirelessPaxosPhase phase,
        int proposalNumber,
        Object value,
        int phaseNumber) {

    public static WirelessPaxosPayload empty() {
        return WirelessPaxosPayload.prepare(-1, null, -1);
    }

    public static WirelessPaxosPayload prepare(int proposalNumber, Object acceptedValue, int acceptedProposal) {
        return new WirelessPaxosPayload(WirelessPaxosPhase.PREPARE, proposalNumber, acceptedValue, acceptedProposal);
    }

    public static WirelessPaxosPayload accept(int proposalNumber, Object value, int minimumProposal) {
        return new WirelessPaxosPayload(WirelessPaxosPhase.ACCEPT, proposalNumber, value, minimumProposal);
    }

    public WirelessPaxosPayload withAcceptedProposal(Object acceptedValue, int acceptedProposal) {
        if (phase != WirelessPaxosPhase.PREPARE) {
            return this;
        }
        return WirelessPaxosPayload.prepare(proposalNumber, acceptedValue, acceptedProposal);
    }

    public WirelessPaxosPayload withMinimumProposal(int minimumProposal) {
        if (phase != WirelessPaxosPhase.ACCEPT) {
            return this;
        }
        return WirelessPaxosPayload.accept(proposalNumber, value, minimumProposal);
    }

    public int acceptedProposal() {
        return phase == WirelessPaxosPhase.PREPARE ? phaseNumber : -1;
    }

    public int minimumProposal() {
        return phase == WirelessPaxosPhase.ACCEPT ? phaseNumber : -1;
    }

    public static WirelessPaxosPayload latest(WirelessPaxosPayload left, WirelessPaxosPayload right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }

        if (left.proposalNumber != right.proposalNumber) {
            return left.proposalNumber > right.proposalNumber ? left : right;
        }

        if (left.phase != right.phase) {
            return left.phase == WirelessPaxosPhase.ACCEPT ? left : right;
        }

        if (left.phase == WirelessPaxosPhase.PREPARE) {
            if (left.acceptedProposal() == right.acceptedProposal()) {
                return (left.value != null) ? left : right;
            }
            return left.acceptedProposal() > right.acceptedProposal() ? left : right;
        }

        if (left.phase == WirelessPaxosPhase.ACCEPT) {
            if (left.minimumProposal() == right.minimumProposal()) {
                return left.value != null ? left : right;
            }
            return left.minimumProposal() > right.minimumProposal() ? left : right;
        }
        return left;
    }
}
