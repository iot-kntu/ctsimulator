//! Totally Ordered Multicast (TOM) over CI Pipeline.
//!
//! - Round-robin = distributed sequencer (no central sequencer).
//! - Term/Sequence ID increases only when a node publishes.
//! - In-order delivery: deliver term only if == last_delivered+1; else buffer + NACK.
//! - Gap recovery: piggyback missing term data on later floods.

use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

/// CI flood packet for TOM.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TomPacket {
    /// Sequence ID of the primary payload this round (or last known if idle).
    pub current_term: u64,
    /// True if this round carries a new application message for current_term.
    pub has_message: bool,
    /// Application payload for current_term (empty if idle / no publish).
    pub message_data: Vec<u8>,
    /// Sender (round-robin initiator).
    pub sender: usize,
    /// First missing term at sender (0 = none).
    pub nack_term: u64,
    /// Piggybacked recovery of a missing term.
    pub piggyback_term: Option<u64>,
    pub piggyback_data: Vec<u8>,
}

/// Per-node TOM middleware state.
#[derive(Debug, Clone)]
pub struct NodeTomState {
    /// Last term delivered to the application layer.
    pub last_delivered: Option<u64>,
    /// Highest term observed on the wire (not necessarily delivered).
    pub highest_seen: Option<u64>,
    /// Store of all known messages (for delivery + piggyback help).
    pub store: BTreeMap<u64, Vec<u8>>,
    /// Delivered log: (term, data) in order.
    pub delivered: Vec<(u64, Vec<u8>)>,
}

impl Default for NodeTomState {
    fn default() -> Self {
        Self::new()
    }
}

impl NodeTomState {
    pub fn new() -> Self {
        Self {
            last_delivered: None,
            highest_seen: None,
            store: BTreeMap::new(),
            delivered: Vec::new(),
        }
    }

    pub fn next_expected(&self) -> u64 {
        self.last_delivered.map(|t| t + 1).unwrap_or(0)
    }

    /// Insert message into store. Returns true if new.
    pub fn insert_message(&mut self, term: u64, data: Vec<u8>) -> bool {
        if self.store.contains_key(&term) {
            return false;
        }
        self.store.insert(term, data);
        match self.highest_seen {
            Some(h) if h >= term => {}
            _ => self.highest_seen = Some(term),
        }
        true
    }

    /// Deliver all consecutive terms from next_expected. Returns count delivered.
    pub fn try_deliver_in_order(&mut self) -> usize {
        let mut count = 0;
        loop {
            let next = self.next_expected();
            if let Some(data) = self.store.get(&next).cloned() {
                self.delivered.push((next, data));
                self.last_delivered = Some(next);
                count += 1;
            } else {
                break;
            }
        }
        count
    }

    /// First gap relative to highest_seen (or next_expected).
    pub fn detect_gap(&self) -> Option<u64> {
        let up_to = self.highest_seen?;
        let start = self.next_expected();
        (start..=up_to).find(|t| !self.store.contains_key(t))
    }

    pub fn get_message(&self, term: u64) -> Option<&Vec<u8>> {
        self.store.get(&term)
    }
}

/// Serialize a TomPacket to bytes.
///
/// NOT a wire format — identical caveat to the 2PC and Paxos serialisers. The JSON
/// bytes are an internal carrier; no metric, energy term or timing term in this
/// slot-level model reads their length. Millisecond conversions use the specified
/// bit-packed encoding of `docs/wire-format.md`, bounded by the only
/// enforced packet-payload budget in this repository, the 251-octet BLE LE Data
/// PDU payload; no IEEE 802.15.4 one-frame-fit claim is made (a 127-octet PSDU
/// also carries MAC header/trailer and any security overhead). The guard tests
/// are `tests/validation.rs::{specified_wire_packet_fits_in_one_ll_data_pdu,
/// specified_wire_packets_fit_the_enforced_ble_budget}`.
pub fn serialize_packet(pkt: &TomPacket) -> Vec<u8> {
    serde_json::to_vec(pkt).unwrap_or_default()
}

pub fn deserialize_packet(data: &[u8]) -> Option<TomPacket> {
    serde_json::from_slice(data).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    /// TOM carries no voting/ACK bitmap and must stay behaviorally unchanged by
    /// the CI vote-window redesign. This pins the packet shape (a construction
    /// that names every field would fail to compile if a vote window were added)
    /// and the in-order delivery / gap behavior that TOM actually implements.
    #[test]
    fn tom_behavior_is_unchanged() {
        // 1. Packet shape: exactly the fields TOM had before this PR — no
        //    ack_window, vote_valid_window, vote_yes_window, or flags_bitmap.
        //    Adding any such field would break this exhaustive struct literal.
        let pkt = TomPacket {
            current_term: 3,
            has_message: true,
            message_data: b"msg3".to_vec(),
            sender: 1,
            nack_term: 0,
            piggyback_term: None,
            piggyback_data: Vec::new(),
        };
        let back = deserialize_packet(&serialize_packet(&pkt)).unwrap();
        assert_eq!(back.current_term, 3);
        assert!(back.has_message);
        assert_eq!(back.message_data, b"msg3");
        assert_eq!(back.sender, 1);
        assert_eq!(back.nack_term, 0);
        assert!(back.piggyback_term.is_none());

        // 2. In-order delivery is unchanged: an out-of-order arrival buffers and
        //    is delivered only once the gap is filled — no vote/quorum logic.
        let mut st = NodeTomState::new();
        assert_eq!(st.next_expected(), 0);
        assert!(st.insert_message(0, b"m0".to_vec()));
        assert!(st.insert_message(2, b"m2".to_vec())); // arrives before term 1
        assert_eq!(st.try_deliver_in_order(), 1, "only term 0 is deliverable");
        assert_eq!(st.next_expected(), 1);
        assert_eq!(st.detect_gap(), Some(1), "term 1 is the outstanding gap");
        // Recovery fills the gap; 1 and the buffered 2 now deliver in order.
        assert!(st.insert_message(1, b"m1".to_vec()));
        assert_eq!(st.try_deliver_in_order(), 2);
        assert_eq!(st.next_expected(), 3);
        assert_eq!(st.detect_gap(), None);
        assert_eq!(
            st.delivered,
            vec![
                (0, b"m0".to_vec()),
                (1, b"m1".to_vec()),
                (2, b"m2".to_vec())
            ]
        );
    }
}
