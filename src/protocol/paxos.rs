//! Paxos over CI Pipeline — pipelined consensus with round-robin flooding.
//!
//! Packet structure (unified flood payload):
//!
//! ```text
//! ┌─────────── Header ───────────┐  ┌───── Payload ──────────────┐
//! │ current_term: u64            │  │ proposal_data: Vec<u8>     │
//! │ last_accepted_term: Option   │  │ piggyback_term: Option<u64>│
//! │ ack_window: Vec<bool>        │  │ piggyback_data: Vec<u8>    │
//! │ nack_term: u64 (0=none)      │  └────────────────────────────┘
//! │ sender: usize                │
//! └──────────────────────────────┘
//! ```
//!
//! No explicit Phase field. Commit is implicit via last_accepted_term:
//!   - Initiator sets current_term=T, broadcasts proposal_data
//!   - `ack_window` is the SENDER's own view of the N most recent terms: bit j
//!     means the sender has seen and affirmatively voted for the term mapped to
//!     position j (see [`crate::protocol::vote_window`]).
//!   - A receiver uses `sender` to record only that sender's vote, only for the
//!     term each position maps to. Quorum is still counted locally per node.
//!   - When the initiator's turn comes back, it checks the local per-term
//!     bitmap for quorum
//!   - If majority reached: last_accepted_term = T in next packet
//!   - All nodes seeing last_accepted_term=T know T and everything before is committed

use crate::protocol::vote_window::{is_padding, mapped_term};
use serde::{Deserialize, Serialize};

/// The unified CI packet: header + payload. No phase field.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PaxosPacket {
    // ── Header ─────────────────────────────────────────
    /// Term of the NEW proposal being broadcast this round.
    pub current_term: u64,
    /// Highest term known to be globally accepted (quorum reached).
    /// Implicitly commits this and all prior terms.
    pub last_accepted_term: Option<u64>,
    /// Sender-owned sliding ACK window of `N` logical bits. Position `j` maps to
    /// term `window_start(current_term, N) + j`; bit `true` means the packet's
    /// `sender` has seen and affirmatively voted for that mapped term. Startup
    /// padding positions (mapped term > current_term) are always false.
    pub ack_window: Vec<bool>,
    /// NACK: if > 0, sender is missing data for this term.
    pub nack_term: u64,
    /// The sender's node ID.
    pub sender: usize,

    // ── Payload ────────────────────────────────────────
    /// Proposal data for current_term.
    pub proposal_data: Vec<u8>,
    /// Piggybacked recovery: term of NACK'd proposal being retransmitted.
    pub piggyback_term: Option<u64>,
    /// Piggybacked recovery data.
    pub piggyback_data: Vec<u8>,
}

impl PaxosPacket {
    /// Full packet-shape validation against the runtime node count `n`. The
    /// sender-owned ACK window has exactly `n` logical positions and the node id
    /// lives in `0..n`, so a well-formed packet must have `ack_window.len() == n`
    /// exactly (no short window silently padded, no long window truncated) and
    /// `sender < n`. A packet failing either check is rejected wholesale before
    /// proposal adoption, ACK application, recovery, commit, or metric mutation.
    pub fn is_well_formed(&self, n: usize) -> bool {
        self.ack_window.len() == n && self.sender < n
    }
}

/// Per-node local state for the Paxos pipeline.
#[derive(Debug, Clone)]
pub struct NodePaxosState {
    /// Highest term this node has seen.
    pub highest_seen_term: Option<u64>,
    /// Highest term this node knows is globally accepted.
    pub last_accepted: Option<u64>,
    /// Log of committed proposals: term → data.
    pub log: Vec<(u64, Vec<u8>)>,
    /// Pending proposals awaiting quorum: term → (data, bitmap).
    pub pending: Vec<PendingProposal>,
    /// ACKs received for terms whose proposal data has not arrived yet, kept as
    /// `(term, voter)` pairs. Applied verbatim once the proposal appears (via a
    /// fresh flood or NACK recovery), so an early ACK is never silently
    /// discarded and never fabricates proposal data.
    pub deferred_votes: Vec<(u64, usize)>,
}

#[derive(Debug, Clone)]
pub struct PendingProposal {
    pub term: u64,
    pub data: Vec<u8>,
    /// Local, node-indexed voter set for this term. `bitmap[k] == true` means
    /// node `k` has voted for this term. Quorum is counted here.
    pub bitmap: Vec<bool>,
}

impl Default for NodePaxosState {
    fn default() -> Self {
        Self::new()
    }
}

impl NodePaxosState {
    pub fn new() -> Self {
        Self {
            highest_seen_term: None,
            last_accepted: None,
            log: Vec::new(),
            pending: Vec::new(),
            deferred_votes: Vec::new(),
        }
    }

    /// Whether `node` has voted for `term` in this node's local view.
    pub fn self_voted(&self, term: u64, node: usize) -> bool {
        self.pending
            .iter()
            .find(|p| p.term == term)
            .and_then(|p| p.bitmap.get(node).copied())
            .unwrap_or(false)
    }

    /// Build the sender-owned ACK window ending at `current_term`: bit `j` is the
    /// sender's own vote for the term mapped to position `j`, padding excluded.
    pub fn build_ack_window(&self, current_term: u64, n: usize, sender: usize) -> Vec<bool> {
        (0..n)
            .map(|j| {
                !is_padding(current_term, n, j)
                    && self.self_voted(mapped_term(current_term, n, j), sender)
            })
            .collect()
    }

    /// Apply a received ACK window: for every true, non-padding position, record
    /// the SENDER's vote for exactly the mapped term. If the mapped term is not
    /// yet known (no proposal data, not committed), stash the vote in
    /// `deferred_votes` instead of dropping it.
    pub fn apply_ack_window(
        &mut self,
        current_term: u64,
        n: usize,
        sender: usize,
        window: &[bool],
    ) {
        for (j, &acked) in window.iter().enumerate().take(n) {
            if !acked || is_padding(current_term, n, j) {
                continue;
            }
            let mt = mapped_term(current_term, n, j);
            if let Some(p) = self.pending.iter_mut().find(|p| p.term == mt) {
                if sender < p.bitmap.len() {
                    p.bitmap[sender] = true;
                }
            } else if !self.log.iter().any(|(lt, _)| *lt == mt) {
                self.defer_vote(mt, sender);
            }
        }
    }

    /// Record a vote for a term whose proposal has not yet arrived (dedup).
    pub fn defer_vote(&mut self, term: u64, voter: usize) {
        if !self.deferred_votes.contains(&(term, voter)) {
            self.deferred_votes.push((term, voter));
        }
    }

    /// Drain any deferred votes for `term` into `bitmap` (used when the proposal
    /// finally materialises via flood or piggyback recovery).
    fn drain_deferred_into(&mut self, term: u64, bitmap: &mut [bool]) {
        self.deferred_votes.retain(|&(t, v)| {
            if t == term {
                if v < bitmap.len() {
                    bitmap[v] = true;
                }
                false
            } else {
                true
            }
        });
    }

    /// Insert a proposal into `pending` if it is not already known, seeding its
    /// bitmap with `self_node`'s vote plus any deferred votes for that term.
    pub fn adopt_proposal(&mut self, term: u64, data: Vec<u8>, num_nodes: usize, self_node: usize) {
        if self.pending.iter().any(|p| p.term == term) || self.log.iter().any(|(t, _)| *t == term) {
            return;
        }
        let mut bitmap = vec![false; num_nodes];
        if self_node < bitmap.len() {
            bitmap[self_node] = true;
        }
        self.drain_deferred_into(term, &mut bitmap);
        self.pending.push(PendingProposal { term, data, bitmap });
    }

    /// Check if a pending proposal has reached majority quorum.
    pub fn check_quorum(&self, term: u64) -> bool {
        for p in &self.pending {
            if p.term == term {
                let votes: usize = p.bitmap.iter().filter(|&&b| b).count();
                let majority = p.bitmap.len() / 2 + 1;
                return votes >= majority;
            }
        }
        false
    }

    /// Move a term from pending to committed log.
    pub fn commit_term(&mut self, term: u64) {
        if self.log.iter().any(|(t, _)| *t == term) {
            return; // already committed
        }
        if let Some(pos) = self.pending.iter().position(|p| p.term == term) {
            let p = self.pending.remove(pos);
            self.log.push((term, p.data));
        }
        // Update last_accepted
        match self.last_accepted {
            Some(la) if la >= term => {}
            _ => self.last_accepted = Some(term),
        }
    }

    /// Commit all terms up to and including `up_to`. Returns a list of newly committed terms.
    pub fn commit_up_to(&mut self, up_to: u64) -> Vec<u64> {
        let mut newly_committed = Vec::new();
        for t in 0..=up_to {
            if !self.log.iter().any(|(lt, _)| *lt == t) {
                self.commit_term(t);
                newly_committed.push(t);
            }
        }
        newly_committed
    }

    /// Detect gap: first term missing from both log and pending.
    pub fn detect_gap(&self, up_to_term: u64) -> Option<u64> {
        let start = self.last_accepted.map(|t| t + 1).unwrap_or(0);
        for t in start..up_to_term {
            let in_log = self.log.iter().any(|(lt, _)| *lt == t);
            let in_pending = self.pending.iter().any(|p| p.term == t);
            if !in_log && !in_pending {
                return Some(t);
            }
        }
        None
    }
}

/// Serialize a PaxosPacket to bytes.
///
/// NOT a wire format — identical caveat to the 2PC and TOM serialisers. This JSON
/// encoding is an internal carrier for protocol state; its byte length feeds no
/// metric, no energy term and no timing term in this slot-level model. Millisecond
/// conversions use the specified bit-packed encoding of `docs/wire-format.md`.
/// The Paxos header carries ONE N-bit ACK window, and its MAXIMUM SIMULTANEOUS
/// shape carries a fresh `proposal_data` AND a recovery `piggyback_data` in the
/// same flood (`prepare_round` sets both — they are not mutually exclusive), so
/// `L_wire(N) = ceil(N/8) + 34` octets. The 34 fixed octets are current_term,
/// last_accepted_term, nack_term and piggyback_term as u32 (16), proposal_data
/// `tx{term}` ASCII (8), piggyback_data `tx{term}` ASCII (8), sender u8 (1), and
/// one presence-flags octet (1) for the two `Option` term fields; nack_term needs
/// no presence bit because it reuses 0 as its "none" sentinel. The 2PC packet
/// carries TWO windows at `2*ceil(N/8) + 22`. Both are bounded by the only
/// enforced packet-payload budget in this repository, the 251-octet BLE LE Data
/// PDU payload; no IEEE 802.15.4 one-frame-fit claim is made (a 127-octet PSDU
/// also carries MAC header/trailer and any security overhead, so a raw payload
/// comparison would not prove single-frame fit). The guard tests are
/// `tests/validation.rs::{specified_wire_packet_fits_in_one_ll_data_pdu,
/// specified_wire_packets_fit_the_enforced_ble_budget, paxos_max_shape_matches_wire_formula}`.
pub fn serialize_packet(pkt: &PaxosPacket) -> Vec<u8> {
    serde_json::to_vec(pkt).unwrap_or_default()
}

/// Deserialize a PaxosPacket from bytes.
pub fn deserialize_packet(data: &[u8]) -> Option<PaxosPacket> {
    serde_json::from_slice(data).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn packet(current_term: u64, sender: usize, ack_window: Vec<bool>) -> PaxosPacket {
        PaxosPacket {
            current_term,
            last_accepted_term: None,
            ack_window,
            nack_term: 0,
            sender,
            proposal_data: Vec::new(),
            piggyback_term: None,
            piggyback_data: Vec::new(),
        }
    }

    #[test]
    fn paxos_ack_window_is_term_indexed_not_node_indexed() {
        let n = 5;
        let mut st = NodePaxosState::new();
        // Two pending terms known locally.
        st.adopt_proposal(3, b"tx3".to_vec(), n, 0);
        st.adopt_proposal(4, b"tx4".to_vec(), n, 0);

        // Sender 2 sends an ACK window ending at term 4 with ONLY position for
        // term 4 set (window covers 0..=4). A node-indexed reading would light
        // up node index 4; a term-indexed reading lights up term 4 for sender 2.
        let mut window = vec![false; n];
        window[4] = true; // position 4 -> term 4
        st.apply_ack_window(4, n, 2, &window);

        let p3 = st.pending.iter().find(|p| p.term == 3).unwrap();
        let p4 = st.pending.iter().find(|p| p.term == 4).unwrap();
        // Sender 2 voted for term 4 only.
        assert!(p4.bitmap[2], "sender 2's vote must land on term 4");
        assert!(!p3.bitmap[2], "sender 2 must NOT be credited for term 3");
        // Node index 4 must be untouched — this is not a node-indexed bitmap.
        assert!(!p4.bitmap[4], "position 4 is term 4, not node 4");
    }

    #[test]
    fn paxos_later_ack_does_not_backfill_earlier_term() {
        let n = 4;
        let mut st = NodePaxosState::new();
        st.adopt_proposal(5, b"tx5".to_vec(), n, 0);
        st.adopt_proposal(6, b"tx6".to_vec(), n, 0);
        st.adopt_proposal(7, b"tx7".to_vec(), n, 0);

        // Sender 1 acks ONLY term 7 (last position). Window ends at 7 -> covers
        // 4,5,6,7. Only position 3 (term 7) is true.
        let window = vec![false, false, false, true];
        st.apply_ack_window(7, n, 1, &window);

        for t in [5u64, 6] {
            let p = st.pending.iter().find(|p| p.term == t).unwrap();
            assert!(
                !p.bitmap[1],
                "a later-term ACK (term 7) must not backfill term {t}"
            );
        }
        let p7 = st.pending.iter().find(|p| p.term == 7).unwrap();
        assert!(p7.bitmap[1], "sender 1's ACK must land on term 7");
    }

    #[test]
    fn paxos_deferred_ack_survives_until_proposal_arrives() {
        let n = 4;
        let mut st = NodePaxosState::new();
        // ACK for term 9 arrives with no proposal data yet.
        let window = vec![false, false, false, true]; // position 3 -> term 9
        st.apply_ack_window(9, n, 2, &window);
        assert!(
            st.pending.iter().all(|p| p.term != 9),
            "no proposal fabricated for term 9"
        );
        assert!(
            st.deferred_votes.contains(&(9, 2)),
            "the early ACK must be retained, not discarded"
        );

        // Proposal data for term 9 now arrives (e.g. via NACK recovery). Node 0
        // adopts it; the deferred sender-2 vote must be applied.
        st.adopt_proposal(9, b"tx9".to_vec(), n, 0);
        let p9 = st.pending.iter().find(|p| p.term == 9).unwrap();
        assert!(p9.bitmap[2], "deferred ACK for sender 2 must be applied");
        assert!(p9.bitmap[0], "adopting node's own vote must be present");
        assert!(
            st.deferred_votes.is_empty(),
            "deferred vote must be drained once applied"
        );
    }

    /// Full NACK -> piggyback recovery -> recovered proposal -> preserved deferred
    /// ACK path, at the state-model level. A node hears an ACK for a term whose
    /// proposal it is missing (the gap it would NACK for), then the proposal data
    /// arrives via piggyback recovery. The deferred ACK must survive and land on
    /// the recovered proposal without recovery itself granting a vote it did not
    /// earn.
    #[test]
    fn paxos_deferred_ack_survives_nack_recovery() {
        let n = 5;
        let mut st = NodePaxosState::new();

        // The node already committed terms 0..=3 but is missing term 4's proposal.
        for t in 0..=3u64 {
            st.adopt_proposal(t, format!("tx{t}").into_bytes(), n, 0);
            st.commit_term(t);
        }
        // Sender 2 floods an ACK window ending at current term 4 that affirms
        // term 4 (position 4 -> term 4). The proposal data for term 4 is absent,
        // so this ACK must be deferred, not dropped, and no proposal fabricated.
        let mut window = vec![false; n];
        window[4] = true; // position 4 -> term 4
        st.apply_ack_window(4, n, 2, &window);
        assert!(
            st.pending.iter().all(|p| p.term != 4),
            "recovery target must not be fabricated from an ACK alone"
        );
        assert!(
            st.deferred_votes.contains(&(4, 2)),
            "the ACK for the missing term must be cached for recovery"
        );

        // detect_gap identifies term 4 as the hole a NACK would request.
        assert_eq!(
            st.detect_gap(5),
            Some(4),
            "the missing term must be the detected gap that triggers a NACK"
        );

        // Piggyback recovery supplies term 4's proposal data. The receiving node
        // (id 1) adopts it; the previously deferred sender-2 ACK must reconcile in
        // alongside node 1's own vote — and nothing else.
        st.adopt_proposal(4, b"tx4".to_vec(), n, 1);
        let p4 = st.pending.iter().find(|p| p.term == 4).unwrap();
        assert!(p4.bitmap[2], "deferred sender-2 ACK must survive recovery");
        assert!(p4.bitmap[1], "adopting node's own vote must be present");
        // Recovery must not manufacture votes for other nodes.
        assert!(!p4.bitmap[0], "recovery must not grant node 0 a vote");
        assert!(!p4.bitmap[3], "recovery must not grant node 3 a vote");
        assert!(!p4.bitmap[4], "recovery must not grant node 4 a vote");
        let votes = p4.bitmap.iter().filter(|&&b| b).count();
        assert_eq!(votes, 2, "exactly the earned votes (nodes 1 and 2)");
        assert!(
            !st.check_quorum(4),
            "2/5 votes must not reach majority quorum via recovery"
        );
        assert!(
            st.deferred_votes.is_empty(),
            "deferred vote drained on adopt"
        );
    }

    #[test]
    fn build_ack_window_reflects_only_sender_own_votes_and_pads_startup() {
        let n = 5;
        let mut st = NodePaxosState::new();
        st.adopt_proposal(0, b"tx0".to_vec(), n, 3); // node 3 voted for term 0
        st.adopt_proposal(1, b"tx1".to_vec(), n, 3); // node 3 voted for term 1
                                                     // Some other node voted for term 1 too, but window is sender-owned.
        if let Some(p) = st.pending.iter_mut().find(|p| p.term == 1) {
            p.bitmap[2] = true;
        }

        // Sender = node 3, current_term = 1, N = 5 -> window_start = 0, positions
        // 0,1 -> terms 0,1 (real); positions 2,3,4 -> terms 2,3,4 (padding).
        let w = st.build_ack_window(1, n, 3);
        assert_eq!(w, vec![true, true, false, false, false]);

        // A different sender (node 2) only voted for term 1.
        let w2 = st.build_ack_window(1, n, 2);
        assert_eq!(w2, vec![false, true, false, false, false]);
    }

    #[test]
    fn ack_window_roundtrips_through_serialisation() {
        let pkt = packet(4, 1, vec![true, false, true, false]);
        let bytes = serialize_packet(&pkt);
        let back = deserialize_packet(&bytes).unwrap();
        assert_eq!(back.ack_window, pkt.ack_window);
        assert_eq!(back.sender, 1);
        assert_eq!(back.current_term, 4);
    }
}
