//! Paxos over CE (Capture Effect / Chaos) — In-network Merging.
//!
//! Unlike CI where payloads must remain identical and require pipelining,
//! CE allows nodes to dynamically merge votes (bitmaps) mid-round using a
//! logical OR operator. A single goal-based round runs until quorum is reached.

use crate::event::NodeId;
use crate::protocol::{MergeResult, ProposalOutcome, Protocol};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CePaxosPacket {
    pub term: u64,
    pub proposal_data: Vec<u8>,
    pub flags_bitmap: Vec<bool>,
}

pub struct PaxosCE {
    pub num_nodes: usize,
    pub current_term: u64,
}

impl Default for PaxosCE {
    fn default() -> Self {
        Self::new()
    }
}

impl PaxosCE {
    pub fn new() -> Self {
        Self {
            num_nodes: 0,
            current_term: 0,
        }
    }

    fn has_quorum(&self, bitmap: &[bool]) -> bool {
        let votes = bitmap.iter().filter(|&&b| b).count();
        let majority = self.num_nodes / 2 + 1;
        votes >= majority
    }
}

impl Protocol for PaxosCE {
    fn name(&self) -> &str {
        "Paxos_CE (Chaos)"
    }

    fn init_proposal(&mut self, initiator: NodeId, num_nodes: usize) -> Vec<u8> {
        self.num_nodes = num_nodes;
        self.current_term += 1;

        let mut bitmap = vec![false; num_nodes];
        bitmap[initiator] = true; // Initiator votes for its own proposal

        let pkt = CePaxosPacket {
            term: self.current_term,
            proposal_data: format!("tx{}", self.current_term).into_bytes(),
            flags_bitmap: bitmap,
        };

        serde_json::to_vec(&pkt).unwrap()
    }

    fn init_node_payload(&self, _node_id: NodeId) -> Vec<u8> {
        Vec::new() // Empty at start. Will be filled on first reception.
    }

    fn merge(&self, local: &mut Vec<u8>, received: &[u8], node_id: NodeId) -> MergeResult {
        let rec_pkt: CePaxosPacket = match serde_json::from_slice(received) {
            Ok(p) => p,
            Err(_) => return MergeResult::Redundant, // invalid packet
        };

        if local.is_empty() {
            // First time hearing about any proposal. Adopt it entirely, but ALSO
            // we must add OUR vote to it because we are receiving and adopting it.
            let mut new_pkt = rec_pkt.clone();
            new_pkt.flags_bitmap[node_id] = true;
            *local = serde_json::to_vec(&new_pkt).unwrap();
            return MergeResult::NewInfo;
        }

        let mut loc_pkt: CePaxosPacket = match serde_json::from_slice(local) {
            Ok(p) => p,
            Err(_) => return MergeResult::Redundant,
        };

        if rec_pkt.term > loc_pkt.term {
            // Newer term overrides older state
            *local = received.to_vec();
            return MergeResult::NewInfo;
        } else if rec_pkt.term < loc_pkt.term {
            // Outdated packet
            return MergeResult::Redundant;
        }

        // Terms are equal. Perform Chaos MERGE (Logical OR on bitmaps).
        let mut new_info = false;
        for i in 0..self.num_nodes {
            if rec_pkt.flags_bitmap[i] && !loc_pkt.flags_bitmap[i] {
                loc_pkt.flags_bitmap[i] = true;
                new_info = true;
            }
        }

        // Also ensure our own bit is set!
        if !loc_pkt.flags_bitmap[node_id] {
            loc_pkt.flags_bitmap[node_id] = true;
            new_info = true;
        }

        if new_info {
            *local = serde_json::to_vec(&loc_pkt).unwrap();
            MergeResult::NewInfo
        } else {
            MergeResult::Redundant
        }
    }

    fn node_goal_reached(&self, payload: &[u8]) -> bool {
        if let Ok(pkt) = serde_json::from_slice::<CePaxosPacket>(payload) {
            self.has_quorum(&pkt.flags_bitmap)
        } else {
            false
        }
    }

    fn network_goal_reached(&self, payloads: &[Vec<u8>]) -> bool {
        // Unified completion: EVERY node holds a quorum bitmap (decision known network-wide).
        // Same "all-N informed" bar as TOM delivery and 2PC final-phase dissemination.
        !payloads.is_empty() && payloads.iter().all(|p| self.node_goal_reached(p))
    }

    fn proposal_outcome(&self, payloads: &[Vec<u8>]) -> ProposalOutcome {
        // Safety of Paxos: majority of nodes with quorum is enough to commit.
        // Round may still run until network_goal (all informed) for fair latency.
        let reached_count = payloads
            .iter()
            .filter(|p| self.node_goal_reached(p))
            .count();
        let majority = self.num_nodes / 2 + 1;

        if reached_count >= majority {
            ProposalOutcome::Committed
        } else {
            ProposalOutcome::TimedOut
        }
    }
}
