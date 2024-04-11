package ir.ac.kntu.distributedsystems.fault.om;

import ir.ac.kntu.concurrenttransmission.CtNode;

/**
 * A record to keep status of a message within one round in Oral Message scenarios
 * @param initiator
 * @param nodeAction
 */
public record OmMessageStatus(CtNode initiator, OmActions nodeAction) {
}
