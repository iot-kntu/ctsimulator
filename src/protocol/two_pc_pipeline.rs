//! Non-blocking Two-Phase Commit (2PC) over CI Pipeline.
//!
//! Features implemented:
//! 1. Bounded Voting: A transaction has exactly one full pipeline cycle (N slots)
//!    to gather votes.
//! 2. Unanimous Commit / Fast Abort: commit only if all N senders voted YES by
//!    the deadline; any NO fast-aborts; any remaining UNKNOWN at the deadline
//!    aborts safely.
//! 3. Non-blocking Execution: overhearing lets any node finalize once it has the
//!    full picture, without waiting for a single coordinator.
//!
//! # Ternary vote window (on wire)
//!
//! Instead of a node-indexed current-term bitmap, each packet carries two
//! `N`-bit, term-indexed, sender-owned windows over the most recent
//! transactions (see [`crate::protocol::vote_window`]):
//!
//! - `vote_valid_window[j]`: the sender has evaluated the transaction mapped to
//!   position `j`.
//! - `vote_yes_window[j]`: meaningful only when valid; true = YES, false = NO.
//!
//! The only legal encodings are UNKNOWN (`valid=false, yes=false`), YES
//! (`valid=true, yes=true`) and NO (`valid=true, yes=false`). The pair
//! `valid=false, yes=true` is invalid and rejected in validation and tests.

use crate::protocol::vote_window::{is_padding, mapped_term, window_start};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TwoPcState {
    Prepare,
    Committed,
    Aborted,
}

/// A single sender's ternary vote for a transaction.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Vote {
    Unknown,
    Yes,
    No,
}

impl Vote {
    /// Decode a `(valid, yes)` window pair into a [`Vote`], rejecting the
    /// illegal `valid=false, yes=true` encoding.
    pub fn from_window_bits(valid: bool, yes: bool) -> Option<Vote> {
        match (valid, yes) {
            (false, false) => Some(Vote::Unknown),
            (true, true) => Some(Vote::Yes),
            (true, false) => Some(Vote::No),
            (false, true) => None, // invalid: never emitted, rejected on receipt
        }
    }

    /// Encode a [`Vote`] into its `(valid, yes)` window pair.
    pub fn to_window_bits(self) -> (bool, bool) {
        match self {
            Vote::Unknown => (false, false),
            Vote::Yes => (true, true),
            Vote::No => (true, false),
        }
    }
}

/// The unified CI packet for Pipeline 2PC.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TwoPcPacket {
    pub transaction_id: u64,
    pub state: TwoPcState,
    /// Sender-owned, term-indexed window: position `j` (mapped to transaction
    /// `window_start(transaction_id, N) + j`) is true when the sender has
    /// evaluated that transaction.
    pub vote_valid_window: Vec<bool>,
    /// Companion window: meaningful only where `vote_valid_window` is true;
    /// true = YES, false = NO.
    pub vote_yes_window: Vec<bool>,
    /// NACK field: used if a node misses the final commit/abort decision for an
    /// older transaction.
    pub nack_tx_id: u64,

    // Payload
    pub transaction_data: Vec<u8>,
    pub sender: usize,

    // Piggybacked recovery data for NACKs
    pub piggyback_tx: Option<u64>,
    pub piggyback_state: Option<TwoPcState>,
}

impl TwoPcPacket {
    /// Validate the ternary window encoding: equal lengths and no
    /// `valid=false, yes=true` position. Returns false for a malformed packet.
    pub fn windows_are_valid(&self) -> bool {
        if self.vote_valid_window.len() != self.vote_yes_window.len() {
            return false;
        }
        self.vote_valid_window
            .iter()
            .zip(&self.vote_yes_window)
            .all(|(&valid, &yes)| Vote::from_window_bits(valid, yes).is_some())
    }

    /// Full packet-shape validation against the runtime node count `n`. The
    /// protocol defines exactly `n` logical window positions and node ids in
    /// `0..n`, so a well-formed packet must have BOTH windows of length exactly
    /// `n` (no short window silently padded to UNKNOWN, no long window
    /// truncated), a legal ternary encoding at every position, and
    /// `sender < n`. A packet failing any of these is rejected wholesale before
    /// it can create a transaction, cast a vote, defer a vote, record an
    /// outcome, or mutate any metric.
    pub fn is_well_formed(&self, n: usize) -> bool {
        self.vote_valid_window.len() == n
            && self.vote_yes_window.len() == n
            && self.sender < n
            && self.windows_are_valid()
    }
}

#[derive(Debug, Clone)]
pub struct NodeTwoPcState {
    pub num_nodes: usize,
    /// Highest transaction ID seen.
    pub highest_seen_tx: Option<u64>,
    /// Log of finalized transactions: tx_id -> State (Committed/Aborted)
    pub log: Vec<(u64, TwoPcState)>,
    /// Currently active/pending transactions.
    pub pending: Vec<PendingTx>,
    /// This node's OWN sampled ternary vote per transaction, kept independently
    /// of `pending` aggregation and of the final-outcome `log`. Recorded exactly
    /// once, the first time the node evaluates a transaction (`adopt_tx`), and
    /// deliberately NOT erased by `finalize_tx`: a node must keep advertising its
    /// own YES/NO for as long as the transaction is still reportable inside its
    /// `N`-term sliding window, even after it has locally finalized (fast-abort)
    /// and dropped the tx from `pending`. A learned final outcome is never an own
    /// vote — nothing writes here on a piggyback/NACK-recovery finalize — so a
    /// node that never evaluated a tx stays UNKNOWN even after it learns
    /// Committed/Aborted. Pruned only by `prune_own_votes`, once a transaction has
    /// permanently left every future window.
    pub own_votes: Vec<(u64, Vote)>,
    /// Votes received for transactions whose data has not arrived yet, kept as
    /// `(tx_id, voter, vote)`. Reconciled once the transaction data appears via
    /// a flood or NACK/recovery, so an early vote is never fabricated away.
    pub deferred_votes: Vec<(u64, usize, Vote)>,
}

#[derive(Debug, Clone)]
pub struct PendingTx {
    pub tx_id: u64,
    pub data: Vec<u8>,
    /// Per-sender ternary votes, indexed by node id. This distinguishes each
    /// sender's UNKNOWN/YES/NO — a single global boolean would lose which sender
    /// voted NO.
    pub votes: Vec<Vote>,
    /// Slot (or round index) when this TX was first proposed.
    /// Used for bounded voting (max lifespan = num_nodes rounds).
    pub start_round: u64,
}

impl PendingTx {
    /// True if any sender has voted NO (explicit fast-abort trigger).
    pub fn any_no(&self) -> bool {
        self.votes.contains(&Vote::No)
    }

    /// True if every sender has voted YES.
    pub fn all_yes(&self) -> bool {
        self.votes.iter().all(|v| *v == Vote::Yes)
    }

    /// True once this tx has reached its bounded-voting deadline: it has had a
    /// full pipeline cycle (`num_nodes` rounds) since it was first proposed.
    pub fn deadline_reached(&self, round_num: u64, num_nodes: usize) -> bool {
        round_num >= self.start_round + num_nodes as u64
    }

    /// The bounded decision at the deadline: commit ONLY if every sender voted
    /// YES, otherwise abort (this is what turns a remaining UNKNOWN into a safe
    /// abort). This is the single production rule; both `prepare_round` and
    /// `process_round` route through [`NodeTwoPcState::deadline_resolutions`],
    /// which calls this, so there is no second copy of the decision to drift.
    pub fn bounded_decision(&self) -> TwoPcState {
        if self.all_yes() {
            TwoPcState::Committed
        } else {
            TwoPcState::Aborted
        }
    }
}

impl NodeTwoPcState {
    pub fn new(num_nodes: usize) -> Self {
        Self {
            num_nodes,
            highest_seen_tx: None,
            log: Vec::new(),
            pending: Vec::new(),
            own_votes: Vec::new(),
            deferred_votes: Vec::new(),
        }
    }

    /// Record this node's OWN vote for `tx_id` exactly once. A YES/NO is never
    /// overwritten by a later, conflicting value (votes are sampled once), and an
    /// UNKNOWN is never stored. Independent of `pending`/`log`, so it survives
    /// `finalize_tx` until the transaction leaves the window.
    fn record_own_vote(&mut self, tx_id: u64, vote: Vote) {
        if vote == Vote::Unknown {
            return;
        }
        if !self.own_votes.iter().any(|&(t, _)| t == tx_id) {
            self.own_votes.push((tx_id, vote));
        }
    }

    /// This node's own recorded vote for `tx_id` (UNKNOWN if it never evaluated
    /// the transaction — a learned final outcome is not an own vote).
    pub fn own_vote(&self, tx_id: u64) -> Vote {
        self.own_votes
            .iter()
            .find(|&&(t, _)| t == tx_id)
            .map(|&(_, v)| v)
            .unwrap_or(Vote::Unknown)
    }

    /// Drop own-vote history for transactions that have permanently left every
    /// future `N`-term window. A window ending at any `current_tx' >= current_tx`
    /// starts no earlier than `window_start(current_tx, N)`, so any tx strictly
    /// below that start can never be reported again and is safe to prune. Called
    /// with the highest tx this node will ever advertise next.
    pub fn prune_own_votes(&mut self, current_tx: u64) {
        let oldest_reportable = window_start(current_tx, self.num_nodes);
        self.own_votes.retain(|&(t, _)| t >= oldest_reportable);
    }

    /// Unanimity: every sender voted YES (no UNKNOWN, no NO).
    pub fn check_unanimity(&self, tx_id: u64) -> bool {
        self.pending
            .iter()
            .find(|p| p.tx_id == tx_id)
            .map(|p| p.all_yes())
            .unwrap_or(false)
    }

    /// Fast-abort condition: any sender voted NO for this transaction.
    pub fn check_explicit_abort(&self, tx_id: u64) -> bool {
        self.pending
            .iter()
            .find(|p| p.tx_id == tx_id)
            .map(|p| p.any_no())
            .unwrap_or(false)
    }

    /// Set `voter`'s vote for exactly `tx_id`, if pending. A NO or YES never
    /// widens to any other transaction. Deferred if the tx is not yet known.
    pub fn record_vote(&mut self, tx_id: u64, voter: usize, vote: Vote) {
        if vote == Vote::Unknown {
            return; // UNKNOWN carries no information to store
        }
        if let Some(p) = self.pending.iter_mut().find(|p| p.tx_id == tx_id) {
            if let Some(slot) = p.votes.get_mut(voter) {
                // A node evaluates a transaction at most once; do not overwrite a
                // recorded YES/NO with a conflicting later value.
                if *slot == Vote::Unknown {
                    *slot = vote;
                }
            }
        } else if !self.log.iter().any(|(lt, _)| *lt == tx_id)
            && !self
                .deferred_votes
                .iter()
                .any(|&(t, v, _)| t == tx_id && v == voter)
        {
            self.deferred_votes.push((tx_id, voter, vote));
        }
    }

    /// Apply the sender-owned ternary vote windows: each valid, non-padding
    /// position records the SENDER's vote for exactly the mapped transaction.
    pub fn apply_vote_windows(
        &mut self,
        current_tx: u64,
        sender: usize,
        valid_window: &[bool],
        yes_window: &[bool],
    ) {
        let n = self.num_nodes;
        for j in 0..n {
            if is_padding(current_tx, n, j) {
                continue;
            }
            let valid = valid_window.get(j).copied().unwrap_or(false);
            let yes = yes_window.get(j).copied().unwrap_or(false);
            // Reject the illegal (false,true) pair silently: never a YES vote.
            if let Some(vote) = Vote::from_window_bits(valid, yes) {
                if vote != Vote::Unknown {
                    self.record_vote(mapped_term(current_tx, n, j), sender, vote);
                }
            }
        }
    }

    /// Build this node's sender-owned windows ending at `current_tx`, reporting
    /// only `self_node`'s own evaluated votes from the persistent own-vote
    /// history (NOT inferred from `pending`, which `finalize_tx` empties). A vote
    /// therefore keeps being advertised while the tx is in the window even after
    /// this node has locally finalized it. Padding positions stay UNKNOWN.
    ///
    /// `self_node` is retained in the signature for call-site symmetry with the
    /// receive path and Paxos; the own-vote history is already this node's, so
    /// the argument is not re-read here.
    pub fn build_vote_windows(&self, current_tx: u64, _self_node: usize) -> (Vec<bool>, Vec<bool>) {
        let n = self.num_nodes;
        let mut valid = vec![false; n];
        let mut yes = vec![false; n];
        for (j, (valid_bit, yes_bit)) in valid.iter_mut().zip(yes.iter_mut()).enumerate() {
            if is_padding(current_tx, n, j) {
                continue;
            }
            let vote = self.own_vote(mapped_term(current_tx, n, j));
            let (v, y) = vote.to_window_bits();
            *valid_bit = v;
            *yes_bit = y;
        }
        (valid, yes)
    }

    /// Insert a transaction if not already known, seeding `self_node`'s own vote
    /// and draining any deferred votes recorded before the data arrived. The own
    /// vote is also recorded in the persistent `own_votes` history so it keeps
    /// being advertised after finalization while the tx stays in the window.
    pub fn adopt_tx(
        &mut self,
        tx_id: u64,
        data: Vec<u8>,
        self_vote: Vote,
        self_node: usize,
        start_round: u64,
    ) {
        // Record this node's own vote exactly once, independent of pending/log,
        // even if the tx is already known or already finalized.
        self.record_own_vote(tx_id, self_vote);
        if self.pending.iter().any(|p| p.tx_id == tx_id)
            || self.log.iter().any(|(t, _)| *t == tx_id)
        {
            return;
        }
        let mut votes = vec![Vote::Unknown; self.num_nodes];
        if self_node < votes.len() {
            votes[self_node] = self_vote;
        }
        // Reconcile any votes cached while the data was missing.
        self.deferred_votes.retain(|&(t, v, vote)| {
            if t == tx_id {
                if let Some(slot) = votes.get_mut(v) {
                    if *slot == Vote::Unknown {
                        *slot = vote;
                    }
                }
                false
            } else {
                true
            }
        });
        self.pending.push(PendingTx {
            tx_id,
            data,
            votes,
            start_round,
        });
    }

    /// Finalize a transaction to the log.
    pub fn finalize_tx(&mut self, tx_id: u64, state: TwoPcState) {
        if self.log.iter().any(|(t, _)| *t == tx_id) {
            return;
        }
        if let Some(pos) = self.pending.iter().position(|p| p.tx_id == tx_id) {
            self.pending.remove(pos);
        }
        self.log.push((tx_id, state));
    }

    pub fn detect_gap(&self, current_tx: u64) -> Option<u64> {
        // Find the lowest missing TX from 0 to current_tx
        for t in 0..current_tx {
            let in_log = self.log.iter().any(|(lt, _)| *lt == t);
            let in_pending = self.pending.iter().any(|p| p.tx_id == t);
            if !in_log && !in_pending {
                return Some(t);
            }
        }
        None
    }

    /// Every pending tx that has reached its bounded-voting deadline at
    /// `round_num`, paired with its bounded decision. This is the SINGLE
    /// production deadline path: both the initiator (`prepare_round`) and every
    /// receiver (`process_round`) call it, so the "commit iff all-YES, else
    /// abort" rule (and hence "a remaining UNKNOWN aborts safely") exists in
    /// exactly one place and cannot drift between the two call sites.
    pub fn deadline_resolutions(&self, round_num: u64, num_nodes: usize) -> Vec<(u64, TwoPcState)> {
        self.pending
            .iter()
            .filter(|p| p.deadline_reached(round_num, num_nodes))
            .map(|p| (p.tx_id, p.bounded_decision()))
            .collect()
    }
}

/// Serialize a TwoPcPacket to bytes.
///
/// NOT a wire format. This JSON encoding is an internal carrier for protocol
/// state: the simulator is a slot-level model, and the byte length produced here
/// is consumed by no metric, no energy term and no timing term (there is no
/// `payload.len()` anywhere in the crate). The packet length behind every
/// millisecond conversion is instead a *specified* bit-packed encoding,
/// `L_wire(N) = 2*ceil(N/8) + 22` octets for the two vote windows — see
/// `docs/wire-format.md` and the guard
/// `tests/validation.rs::specified_wire_packet_fits_in_one_ll_data_pdu`.
/// Do not quote `serialize_packet(..).len()` as a packet size.
pub fn serialize_packet(pkt: &TwoPcPacket) -> Vec<u8> {
    serde_json::to_vec(pkt).unwrap_or_default()
}

/// Deserialize a TwoPcPacket from bytes.
pub fn deserialize_packet(data: &[u8]) -> Option<TwoPcPacket> {
    serde_json::from_slice(data).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ternary_encoding_rejects_valid_false_yes_true() {
        assert_eq!(Vote::from_window_bits(false, false), Some(Vote::Unknown));
        assert_eq!(Vote::from_window_bits(true, true), Some(Vote::Yes));
        assert_eq!(Vote::from_window_bits(true, false), Some(Vote::No));
        assert_eq!(Vote::from_window_bits(false, true), None);
        // Round-trip.
        for v in [Vote::Unknown, Vote::Yes, Vote::No] {
            let (a, b) = v.to_window_bits();
            assert_eq!(Vote::from_window_bits(a, b), Some(v));
        }
    }

    #[test]
    fn two_pc_window_distinguishes_unknown_yes_and_no() {
        let n = 5;
        let mut st = NodeTwoPcState::new(n);
        st.adopt_tx(4, b"tx4".to_vec(), Vote::Unknown, 0, 0);
        // Three different senders vote differently for the same tx.
        st.record_vote(4, 1, Vote::Yes);
        st.record_vote(4, 2, Vote::No);
        // sender 3 stays UNKNOWN (never voted)
        let p = st.pending.iter().find(|p| p.tx_id == 4).unwrap();
        assert_eq!(p.votes[1], Vote::Yes);
        assert_eq!(p.votes[2], Vote::No);
        assert_eq!(p.votes[3], Vote::Unknown);
        assert!(p.any_no(), "a NO must be visible as a NO, not lost");
        assert!(!p.all_yes());
    }

    #[test]
    fn two_pc_no_aborts_only_its_target_transaction() {
        let n = 4;
        let mut st = NodeTwoPcState::new(n);
        for t in 3..=5u64 {
            st.adopt_tx(t, format!("tx{t}").into_bytes(), Vote::Yes, 0, 0);
        }
        // Sender 1 sends a NO for ONLY tx 5 (last window position), YES elsewhere.
        // Window ends at 5 -> covers 2,3,4,5. Positions 1,2 (tx 3,4) YES,
        // position 3 (tx 5) NO. Position 0 (tx 2) is not pending -> UNKNOWN.
        let valid = vec![false, true, true, true];
        let yes = vec![false, true, true, false];
        st.apply_vote_windows(5, 1, &valid, &yes);

        assert!(
            !st.check_explicit_abort(3),
            "NO for tx 5 must not abort tx 3"
        );
        assert!(
            !st.check_explicit_abort(4),
            "NO for tx 5 must not abort tx 4"
        );
        assert!(st.check_explicit_abort(5), "the NO must abort exactly tx 5");
    }

    #[test]
    fn two_pc_yes_for_later_tx_does_not_vote_for_earlier_tx() {
        let n = 4;
        let mut st = NodeTwoPcState::new(n);
        st.adopt_tx(5, b"tx5".to_vec(), Vote::Unknown, 0, 0);
        st.adopt_tx(6, b"tx6".to_vec(), Vote::Unknown, 0, 0);
        st.adopt_tx(7, b"tx7".to_vec(), Vote::Unknown, 0, 0);
        // Sender 2 votes YES only for tx 7 (last position of window ending at 7).
        let valid = vec![false, false, false, true];
        let yes = vec![false, false, false, true];
        st.apply_vote_windows(7, 2, &valid, &yes);

        for t in [5u64, 6] {
            let p = st.pending.iter().find(|p| p.tx_id == t).unwrap();
            assert_eq!(
                p.votes[2],
                Vote::Unknown,
                "YES for tx 7 must not imply a vote for tx {t}"
            );
        }
        let p7 = st.pending.iter().find(|p| p.tx_id == 7).unwrap();
        assert_eq!(p7.votes[2], Vote::Yes);
    }

    #[test]
    fn two_pc_vote_is_sampled_once_and_persists_across_windows() {
        let n = 4;
        let mut st = NodeTwoPcState::new(n);
        st.adopt_tx(3, b"tx3".to_vec(), Vote::Unknown, 0, 0);
        // Sender 1 votes NO once.
        st.record_vote(3, 1, Vote::No);
        // A later, conflicting YES for the same (tx, sender) must NOT overwrite it.
        st.record_vote(3, 1, Vote::Yes);
        let p = st.pending.iter().find(|p| p.tx_id == 3).unwrap();
        assert_eq!(
            p.votes[1],
            Vote::No,
            "a recorded NO must persist; votes are sampled once"
        );
    }

    #[test]
    fn two_pc_deferred_vote_survives_until_tx_data_arrives() {
        let n = 4;
        let mut st = NodeTwoPcState::new(n);
        // Vote metadata for tx 9 arrives before its data.
        let valid = vec![false, false, false, true];
        let yes = vec![false, false, false, false]; // NO for tx 9
        st.apply_vote_windows(9, 2, &valid, &yes);
        assert!(
            st.pending.iter().all(|p| p.tx_id != 9),
            "no tx fabricated for tx 9"
        );
        assert!(
            st.deferred_votes.contains(&(9, 2, Vote::No)),
            "the early vote must be cached, not discarded"
        );
        // Data arrives via recovery; deferred NO must reconcile in.
        st.adopt_tx(9, b"tx9".to_vec(), Vote::Yes, 0, 0);
        let p = st.pending.iter().find(|p| p.tx_id == 9).unwrap();
        assert_eq!(p.votes[2], Vote::No, "deferred NO must be reconciled");
        assert_eq!(p.votes[0], Vote::Yes, "adopting node's own vote present");
        assert!(st.deferred_votes.is_empty());
    }

    #[test]
    fn build_vote_windows_reports_only_self_votes_and_pads_startup() {
        let n = 5;
        let mut st = NodeTwoPcState::new(n);
        st.adopt_tx(0, b"tx0".to_vec(), Vote::Yes, 3, 0);
        st.adopt_tx(1, b"tx1".to_vec(), Vote::No, 3, 0);
        // Some other node's vote recorded in `pending` must NOT leak into this
        // node's advertised window: the window reports only own-vote history.
        st.record_vote(1, 2, Vote::Yes);

        // current_tx = 1, N = 5 -> positions 0,1 -> tx 0,1; positions 2,3,4 pad.
        let (valid, yes) = st.build_vote_windows(1, 3);
        assert_eq!(valid, vec![true, true, false, false, false]);
        assert_eq!(yes, vec![true, false, false, false, false]); // YES(tx0), NO(tx1)

        // The window is sender-owned: it reflects this node's own votes only and
        // is independent of the `self_node` argument (retained for call symmetry),
        // so a foreign node's `pending` vote never appears.
        let (valid2, yes2) = st.build_vote_windows(1, 2);
        assert_eq!(valid2, valid, "window is own-vote history, not per-arg");
        assert_eq!(yes2, yes);
    }

    #[test]
    fn packet_window_validation_flags_illegal_pair() {
        let mut pkt = TwoPcPacket {
            transaction_id: 3,
            state: TwoPcState::Prepare,
            vote_valid_window: vec![true, false, false],
            vote_yes_window: vec![true, false, false],
            nack_tx_id: 0,
            transaction_data: b"tx3".to_vec(),
            sender: 0,
            piggyback_tx: None,
            piggyback_state: None,
        };
        assert!(pkt.windows_are_valid());
        // Introduce the illegal valid=false, yes=true at position 1.
        pkt.vote_yes_window[1] = true;
        assert!(!pkt.windows_are_valid());
        // Mismatched lengths are also invalid.
        pkt.vote_yes_window.pop();
        assert!(!pkt.windows_are_valid());
    }

    /// An UNKNOWN vote (a sender that never evaluated the tx, e.g. it was offline
    /// or lossy) must NOT abort before the deadline: `check_explicit_abort` stays
    /// false while the tx is merely non-unanimous. At the deadline the bounded
    /// decision (commit iff all-YES) turns the remaining UNKNOWN into a safe abort.
    /// This exercises the SHARED production decision path
    /// (`deadline_resolutions` / `PendingTx::bounded_decision`) that both
    /// `prepare_round` and `process_round` call, not a copy of the rule.
    #[test]
    fn two_pc_unknown_waits_until_deadline_then_aborts() {
        let n = 4;
        let mut st = NodeTwoPcState::new(n);
        // tx 5 first proposed at round 2 -> its deadline is round 2 + n = 6.
        st.adopt_tx(5, b"tx5".to_vec(), Vote::Yes, 0, 2);
        // Two more senders vote YES; sender 3 never evaluates it (stays UNKNOWN).
        st.record_vote(5, 1, Vote::Yes);
        st.record_vote(5, 2, Vote::Yes);

        let p = st.pending.iter().find(|p| p.tx_id == 5).unwrap();
        assert_eq!(p.votes[3], Vote::Unknown);
        // Before the deadline: no explicit NO, so no fast abort; not yet unanimous.
        assert!(
            !st.check_explicit_abort(5),
            "an UNKNOWN must not trigger a fast abort before the deadline"
        );
        assert!(
            !st.check_unanimity(5),
            "a remaining UNKNOWN means the tx is not yet unanimous"
        );

        // Rounds strictly before the deadline resolve nothing at all.
        for round in [2u64, 3, 4, 5] {
            assert!(
                st.deadline_resolutions(round, n).is_empty(),
                "no tx may resolve before its round {} deadline (checked round {round})",
                2 + n as u64
            );
        }

        // At the real deadline (round 6) the shared rule fires and, because one
        // sender is still UNKNOWN, resolves the tx to Aborted — never Committed.
        let at_deadline = st.deadline_resolutions(2 + n as u64, n);
        assert_eq!(
            at_deadline,
            vec![(5, TwoPcState::Aborted)],
            "a remaining UNKNOWN at the real deadline must abort, never commit"
        );
    }

    /// The all-YES companion of the deadline rule: when every sender has voted
    /// YES by the deadline, the SAME shared production path commits it.
    #[test]
    fn two_pc_all_yes_commits_at_deadline_via_shared_rule() {
        let n = 3;
        let mut st = NodeTwoPcState::new(n);
        st.adopt_tx(0, b"tx0".to_vec(), Vote::Yes, 0, 0);
        st.record_vote(0, 1, Vote::Yes);
        st.record_vote(0, 2, Vote::Yes);
        // Before the deadline (round < n): nothing resolves.
        assert!(st.deadline_resolutions(n as u64 - 1, n).is_empty());
        // At the deadline: unanimous YES commits, through bounded_decision.
        assert_eq!(
            st.deadline_resolutions(n as u64, n),
            vec![(0, TwoPcState::Committed)]
        );
    }

    /// Recovery (a piggybacked final decision, or late-arriving vote metadata)
    /// must never fabricate unanimity or backfill votes that were not actually
    /// cast. Here every sender but one voted YES and the last stayed UNKNOWN;
    /// reconciling deferred data for the tx must not invent the missing YES.
    #[test]
    fn recovery_never_grants_quorum_or_unanimity() {
        let n = 4;
        let mut st = NodeTwoPcState::new(n);
        // Vote metadata for tx 8 arrives before its data: senders 1 and 2 YES.
        // Window ends at 8, N=4 -> covers 5,6,7,8; position 3 -> tx 8.
        let valid = vec![false, false, false, true];
        let yes = vec![false, false, false, true];
        st.apply_vote_windows(8, 1, &valid, &yes);
        st.apply_vote_windows(8, 2, &valid, &yes);
        assert!(
            st.pending.iter().all(|p| p.tx_id != 8),
            "no tx fabricated from vote metadata alone"
        );
        assert!(st.deferred_votes.contains(&(8, 1, Vote::Yes)));
        assert!(st.deferred_votes.contains(&(8, 2, Vote::Yes)));

        // Recovery supplies the tx data; the adopting node (0) votes YES. Only
        // the earned votes (0,1,2) may be present; sender 3 must stay UNKNOWN.
        st.adopt_tx(8, b"tx8".to_vec(), Vote::Yes, 0, 0);
        let p = st.pending.iter().find(|p| p.tx_id == 8).unwrap();
        assert_eq!(p.votes[0], Vote::Yes);
        assert_eq!(p.votes[1], Vote::Yes);
        assert_eq!(p.votes[2], Vote::Yes);
        assert_eq!(
            p.votes[3],
            Vote::Unknown,
            "recovery must not fabricate the missing sender's vote"
        );
        assert!(
            !st.check_unanimity(8),
            "recovery must never manufacture unanimity from a missing vote"
        );

        // And a recorded NO must never be erased by recovery reconciliation.
        let mut st2 = NodeTwoPcState::new(n);
        st2.record_vote(9, 2, Vote::No); // deferred NO for tx 9
        st2.adopt_tx(9, b"tx9".to_vec(), Vote::Yes, 0, 0);
        let p9 = st2.pending.iter().find(|p| p.tx_id == 9).unwrap();
        assert_eq!(
            p9.votes[2],
            Vote::No,
            "recovery must not erase a recorded NO"
        );
        assert!(
            st2.check_explicit_abort(9),
            "the recovered NO must still abort"
        );
    }

    /// B1: a sampled NO must keep being advertised in the sender-owned window
    /// AFTER the node has locally fast-aborted and finalized the tx, for as long
    /// as the tx is still inside the N-term window. This drives the production
    /// own-vote history + `build_vote_windows`, not a re-implementation.
    #[test]
    fn own_no_survives_fast_abort_finalization_while_in_window() {
        let n = 4;
        let mut st = NodeTwoPcState::new(n);
        // This node evaluates tx 5 and samples its own vote = NO.
        st.adopt_tx(5, b"tx5".to_vec(), Vote::No, 0, 0);
        // Its own NO fast-aborts and finalizes tx 5 (drops it from `pending`).
        st.finalize_tx(5, TwoPcState::Aborted);
        assert!(
            st.pending.iter().all(|p| p.tx_id != 5),
            "fast-abort must remove the tx from pending"
        );
        // While tx 5 is still inside the window ending at 5 (covers 2..=5), the
        // node must still advertise its own NO from own-vote history.
        let (valid, yes) = st.build_vote_windows(5, 0);
        // Window ends at 5, N=4 -> positions map to tx 2,3,4,5; tx 5 is pos 3.
        assert_eq!(valid, vec![false, false, false, true], "own NO still valid");
        assert_eq!(yes, vec![false, false, false, false], "NO -> yes bit false");
    }

    /// B1: a sampled YES must likewise survive finalization while the tx remains
    /// inside the window.
    #[test]
    fn own_yes_survives_finalization_while_in_window() {
        let n = 4;
        let mut st = NodeTwoPcState::new(n);
        st.adopt_tx(6, b"tx6".to_vec(), Vote::Yes, 0, 0);
        // Finalize (e.g. learned Committed); own YES must not be erased.
        st.finalize_tx(6, TwoPcState::Committed);
        let (valid, yes) = st.build_vote_windows(6, 0);
        // Window ends at 6, N=4 -> tx 3,4,5,6; tx 6 is position 3.
        assert_eq!(
            valid,
            vec![false, false, false, true],
            "own YES still valid"
        );
        assert_eq!(yes, vec![false, false, false, true], "YES -> yes bit true");
    }

    /// B1: learning an Aborted final outcome through recovery must NOT fabricate a
    /// local NO for a node that never evaluated the transaction. A `finalize_tx`
    /// with no prior `adopt_tx`/own vote leaves the own-vote history empty, so the
    /// window stays UNKNOWN for that tx.
    #[test]
    fn learned_aborted_outcome_does_not_fabricate_local_no() {
        let n = 4;
        let mut st = NodeTwoPcState::new(n);
        // Node never evaluated tx 7; it only LEARNS the Aborted outcome (e.g. via
        // a piggybacked decision or NACK recovery), which is not an own vote.
        st.finalize_tx(7, TwoPcState::Aborted);
        let (valid, yes) = st.build_vote_windows(7, 0);
        // Window ends at 7, N=4 -> tx 4,5,6,7; tx 7 is position 3. Must be UNKNOWN.
        assert_eq!(
            valid,
            vec![false, false, false, false],
            "a learned outcome must not become an own vote"
        );
        assert_eq!(yes, vec![false, false, false, false]);
        // And the own-vote history genuinely holds nothing for tx 7.
        assert_eq!(st.own_vote(7), Vote::Unknown);
    }

    /// B1: pruning removes an own vote ONLY after the tx has left every future
    /// N-term window. `prune_own_votes(current_tx)` keeps everything >=
    /// window_start(current_tx, N) and drops only strictly older entries.
    #[test]
    fn prune_removes_own_vote_only_after_tx_leaves_window() {
        let n = 4;
        let mut st = NodeTwoPcState::new(n);
        // Own votes sampled for tx 2 and tx 3.
        st.adopt_tx(2, b"tx2".to_vec(), Vote::Yes, 0, 0);
        st.adopt_tx(3, b"tx3".to_vec(), Vote::No, 0, 0);

        // Next advertised window ends at tx 5 -> window_start = 5-(4-1) = 2, so
        // tx 2 is still the oldest reportable position. Nothing may be pruned.
        st.prune_own_votes(5);
        assert_eq!(st.own_vote(2), Vote::Yes, "tx 2 still in window -> kept");
        assert_eq!(st.own_vote(3), Vote::No);

        // Advance so the next window ends at tx 6 -> window_start = 3. tx 2 has
        // now permanently left every future window and must be pruned; tx 3 stays.
        st.prune_own_votes(6);
        assert_eq!(
            st.own_vote(2),
            Vote::Unknown,
            "tx 2 left the window -> own vote pruned"
        );
        assert_eq!(st.own_vote(3), Vote::No, "tx 3 still reportable -> kept");
    }
}
