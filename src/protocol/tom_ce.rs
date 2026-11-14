//! Totally Ordered Multicast (TOM) over CE (Capture Effect / Chaos).
//!
//! One message per goal-based CE round. Simulator issues proposals in order,
//! so total order is the sequence of completed rounds (term 1,2,3,...).
//!
//! Packet: term + message_data + flags_bitmap (who holds the message).
//! Merge: OR bitmap; adopt same term; re-flood on NewInfo.
//! Recovery: CE listen-timeout re-flood (no NACK/piggyback).
//! Goal: all nodes hold the message (full bitmap) → deliver term in-order.

use crate::event::NodeId;
use crate::protocol::{MergeResult, ProposalOutcome, Protocol};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CeTomPacket {
    pub term: u64,
    pub message_data: Vec<u8>,
    /// Bit i = node i has stored this message (ready for delivery at this term).
    pub flags_bitmap: Vec<bool>,
}

pub struct TomCE {
    pub num_nodes: usize,
    pub current_term: u64,
}

impl TomCE {
    pub fn new() -> Self {
        Self {
            num_nodes: 0,
            current_term: 0,
        }
    }
}

impl Default for TomCE {
    fn default() -> Self {
        Self::new()
    }
}

impl Protocol for TomCE {
    fn name(&self) -> &str {
        "TOM_CE (Chaos)"
    }

    fn init_proposal(&mut self, initiator: NodeId, num_nodes: usize) -> Vec<u8> {
        self.num_nodes = num_nodes;
        self.current_term += 1;

        let mut bitmap = vec![false; num_nodes];
        bitmap[initiator] = true;

        let pkt = CeTomPacket {
            term: self.current_term,
            message_data: format!("msg{}", self.current_term).into_bytes(),
            flags_bitmap: bitmap,
        };
        serde_json::to_vec(&pkt).unwrap()
    }

    fn init_node_payload(&self, _node_id: NodeId) -> Vec<u8> {
        Vec::new()
    }

    fn merge(&self, local: &mut Vec<u8>, received: &[u8], node_id: NodeId) -> MergeResult {
        let rec: CeTomPacket = match serde_json::from_slice(received) {
            Ok(p) => p,
            Err(_) => return MergeResult::Redundant,
        };

        if local.is_empty() {
            let mut p = rec;
            if node_id < p.flags_bitmap.len() {
                p.flags_bitmap[node_id] = true;
            }
            *local = serde_json::to_vec(&p).unwrap();
            return MergeResult::NewInfo;
        }

        let mut loc: CeTomPacket = match serde_json::from_slice(local) {
            Ok(p) => p,
            Err(_) => return MergeResult::Redundant,
        };

        // Newer term supersedes (shouldn't happen within one CE round of sim.rs).
        if rec.term > loc.term {
            let mut p = rec;
            if node_id < p.flags_bitmap.len() {
                p.flags_bitmap[node_id] = true;
            }
            *local = serde_json::to_vec(&p).unwrap();
            return MergeResult::NewInfo;
        }
        if rec.term < loc.term {
            return MergeResult::Redundant;
        }

        // Same term: OR bitmaps; keep message_data from either side.
        let mut new_info = false;
        if loc.message_data.is_empty() && !rec.message_data.is_empty() {
            loc.message_data = rec.message_data.clone();
            new_info = true;
        }
        let n = self
            .num_nodes
            .min(loc.flags_bitmap.len())
            .min(rec.flags_bitmap.len());
        for i in 0..n {
            if rec.flags_bitmap[i] && !loc.flags_bitmap[i] {
                loc.flags_bitmap[i] = true;
                new_info = true;
            }
        }
        if node_id < loc.flags_bitmap.len() && !loc.flags_bitmap[node_id] {
            loc.flags_bitmap[node_id] = true;
            new_info = true;
        }

        if new_info {
            *local = serde_json::to_vec(&loc).unwrap();
            MergeResult::NewInfo
        } else {
            MergeResult::Redundant
        }
    }

    fn node_goal_reached(&self, payload: &[u8]) -> bool {
        // Node holds the message (delivery).
        serde_json::from_slice::<CeTomPacket>(payload)
            .map(|p| !p.message_data.is_empty())
            .unwrap_or(false)
    }

    fn network_goal_reached(&self, payloads: &[Vec<u8>]) -> bool {
        // Unified completion: ALL N nodes delivered the message.
        !payloads.is_empty() && payloads.iter().all(|p| self.node_goal_reached(p))
    }

    fn proposal_outcome(&self, payloads: &[Vec<u8>]) -> ProposalOutcome {
        if self.network_goal_reached(payloads) {
            ProposalOutcome::Committed
        } else {
            // Incomplete dissemination by deadline.
            ProposalOutcome::TimedOut
        }
    }
}
