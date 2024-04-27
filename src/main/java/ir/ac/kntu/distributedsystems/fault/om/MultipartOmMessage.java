package ir.ac.kntu.distributedsystems.fault.om;

import ir.ac.kntu.concurrenttransmission.CtNode;

import java.util.List;

record PartialOmMessage(CtNode initiator, int messageNo, OmAction action) {
}

public record MultipartOmMessage(List<PartialOmMessage> partialOmMessages, boolean hasRequestNextRound) {
}
