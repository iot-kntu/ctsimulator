package ir.ac.kntu.distributedsystems.a2.vote;


import ir.ac.kntu.concurrenttransmission.chaos.Flag;

/**
 * A flag representing a node's vote (YES, NO, or UNDECIDED).
 */
public record VoteFlag(VoteValue value) implements Flag {

    public static final VoteFlag UNDECIDED = new VoteFlag(VoteValue.UNDECIDED);

    @Override
    public Flag merge(Flag other) {
        if (!(other instanceof VoteFlag otherVote)) {
            return this;
        }
        if (this.value != VoteValue.UNDECIDED) {
            return this;
        }
        return otherVote;
    }
}