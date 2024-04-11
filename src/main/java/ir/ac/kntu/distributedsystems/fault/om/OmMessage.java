package ir.ac.kntu.distributedsystems.fault.om;

/**
 * Represents the flood message exchanged by nodes in Oral Message scenarios
 * @param nodeAction
 * @param body
 */
public record OmMessage(OmActions nodeAction, String body) {
}
