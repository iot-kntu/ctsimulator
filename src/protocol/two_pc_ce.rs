//! Non-blocking Two-Phase Commit (2PC) over CE (Capture Effect / Chaos).
//!
//! Phase 1 (Prepare): OR vote bitmap until Unanimity YES (or escalate to Abort).
//! Phase 2 (Commit/Abort): reset bitmap; disseminate final decision until all
//! nodes hold Committed/Aborted. A node that sees a full dissemination bitmap
//! may sleep (after flooding that state once — enforced in phy/ce.rs).

use crate::event::NodeId;
use crate::protocol::{MergeResult, ProposalOutcome, Protocol};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TwoPcCePhase {
    Prepare,
    Committed,
    Aborted,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CeTwoPcPacket {
    pub term: u64,
    pub phase: TwoPcCePhase,
    pub proposal_data: Vec<u8>,
    /// Prepare: YES votes. Commit/Abort: who has received the final decision.
    pub flags_bitmap: Vec<bool>,
}

pub struct TwoPcCE {
    pub num_nodes: usize,
    pub current_term: u64,
    pub abort_probability: f64,
    pub seed: u64,
}

impl TwoPcCE {
    pub fn new(abort_probability: f64, seed: u64) -> Self {
        Self {
            num_nodes: 0,
            current_term: 0,
            abort_probability,
            seed,
        }
    }

    fn has_unanimity(&self, bitmap: &[bool]) -> bool {
        !bitmap.is_empty() && bitmap.len() >= self.num_nodes && bitmap.iter().all(|&b| b)
    }

    fn should_abort_app(&self, term: u64, node_id: NodeId) -> bool {
        if self.abort_probability <= 0.0 {
            return false;
        }
        let h = term
            .wrapping_mul(1_000_003)
            .wrapping_add(node_id as u64 + 1)
            .wrapping_mul(self.seed.wrapping_add(0x9e37_79b9_7f4a_7c15));
        (h % 10_000) as f64 / 10_000.0 < self.abort_probability
    }

    fn ensure_bitmap_len(&self, pkt: &mut CeTwoPcPacket) {
        if pkt.flags_bitmap.len() < self.num_nodes {
            pkt.flags_bitmap.resize(self.num_nodes, false);
        }
    }

    /// Escalate Prepare → Committed and reset dissemination bitmap (own bit set).
    fn escalate_to_commit(&self, pkt: &mut CeTwoPcPacket, node_id: NodeId) {
        pkt.phase = TwoPcCePhase::Committed;
        pkt.flags_bitmap = vec![false; self.num_nodes];
        if node_id < self.num_nodes {
            pkt.flags_bitmap[node_id] = true;
        }
    }

    fn escalate_to_abort(&self, pkt: &mut CeTwoPcPacket, node_id: NodeId) {
        pkt.phase = TwoPcCePhase::Aborted;
        pkt.flags_bitmap = vec![false; self.num_nodes];
        if node_id < self.num_nodes {
            pkt.flags_bitmap[node_id] = true;
        }
    }

    fn adopt_with_local_vote(&self, mut pkt: CeTwoPcPacket, node_id: NodeId) -> CeTwoPcPacket {
        self.ensure_bitmap_len(&mut pkt);
        match pkt.phase {
            TwoPcCePhase::Prepare => {
                if self.should_abort_app(pkt.term, node_id) {
                    self.escalate_to_abort(&mut pkt, node_id);
                } else {
                    if node_id < pkt.flags_bitmap.len() {
                        pkt.flags_bitmap[node_id] = true;
                    }
                    if self.has_unanimity(&pkt.flags_bitmap) {
                        self.escalate_to_commit(&mut pkt, node_id);
                    }
                }
            }
            TwoPcCePhase::Committed | TwoPcCePhase::Aborted => {
                if node_id < pkt.flags_bitmap.len() {
                    pkt.flags_bitmap[node_id] = true;
                }
            }
        }
        pkt
    }

    fn is_final(phase: TwoPcCePhase) -> bool {
        matches!(phase, TwoPcCePhase::Committed | TwoPcCePhase::Aborted)
    }
}

impl Protocol for TwoPcCE {
    fn name(&self) -> &str {
        "2PC_CE (Chaos)"
    }

    fn init_proposal(&mut self, initiator: NodeId, num_nodes: usize) -> Vec<u8> {
        self.num_nodes = num_nodes;
        self.current_term += 1;

        let mut pkt = CeTwoPcPacket {
            term: self.current_term,
            phase: TwoPcCePhase::Prepare,
            proposal_data: format!("tx{}", self.current_term).into_bytes(),
            flags_bitmap: vec![false; num_nodes],
        };

        if self.should_abort_app(self.current_term, initiator) {
            self.escalate_to_abort(&mut pkt, initiator);
        } else {
            pkt.flags_bitmap[initiator] = true;
            // n=1 edge case
            if self.has_unanimity(&pkt.flags_bitmap) {
                self.escalate_to_commit(&mut pkt, initiator);
            }
        }

        serde_json::to_vec(&pkt).unwrap()
    }

    fn init_node_payload(&self, _node_id: NodeId) -> Vec<u8> {
        Vec::new()
    }

    fn merge(&self, local: &mut Vec<u8>, received: &[u8], node_id: NodeId) -> MergeResult {
        let rec_pkt: CeTwoPcPacket = match serde_json::from_slice(received) {
            Ok(p) => p,
            Err(_) => return MergeResult::Redundant,
        };

        if local.is_empty() {
            let new_pkt = self.adopt_with_local_vote(rec_pkt, node_id);
            *local = serde_json::to_vec(&new_pkt).unwrap();
            return MergeResult::NewInfo;
        }

        let mut loc_pkt: CeTwoPcPacket = match serde_json::from_slice(local) {
            Ok(p) => p,
            Err(_) => return MergeResult::Redundant,
        };

        if rec_pkt.term > loc_pkt.term {
            let new_pkt = self.adopt_with_local_vote(rec_pkt, node_id);
            *local = serde_json::to_vec(&new_pkt).unwrap();
            return MergeResult::NewInfo;
        }
        if rec_pkt.term < loc_pkt.term {
            return MergeResult::Redundant;
        }

        self.ensure_bitmap_len(&mut loc_pkt);
        let mut rec = rec_pkt;
        self.ensure_bitmap_len(&mut rec);

        let mut new_info = false;

        // Phase escalation: Abort > Commit > Prepare
        if rec.phase == TwoPcCePhase::Aborted && loc_pkt.phase != TwoPcCePhase::Aborted {
            loc_pkt.phase = TwoPcCePhase::Aborted;
            loc_pkt.flags_bitmap = rec.flags_bitmap.clone();
            new_info = true;
        } else if rec.phase == TwoPcCePhase::Committed && loc_pkt.phase == TwoPcCePhase::Prepare {
            loc_pkt.phase = TwoPcCePhase::Committed;
            loc_pkt.flags_bitmap = rec.flags_bitmap.clone();
            new_info = true;
        }

        // Same phase: OR dissemination / vote bits
        if rec.phase == loc_pkt.phase {
            let n = self
                .num_nodes
                .min(loc_pkt.flags_bitmap.len())
                .min(rec.flags_bitmap.len());
            for i in 0..n {
                if rec.flags_bitmap[i] && !loc_pkt.flags_bitmap[i] {
                    loc_pkt.flags_bitmap[i] = true;
                    new_info = true;
                }
            }
        }

        // Local YES / ack for current phase
        if node_id < loc_pkt.flags_bitmap.len() && !loc_pkt.flags_bitmap[node_id] {
            if loc_pkt.phase == TwoPcCePhase::Prepare {
                if self.should_abort_app(loc_pkt.term, node_id) {
                    self.escalate_to_abort(&mut loc_pkt, node_id);
                    new_info = true;
                } else {
                    loc_pkt.flags_bitmap[node_id] = true;
                    new_info = true;
                }
            } else {
                loc_pkt.flags_bitmap[node_id] = true;
                new_info = true;
            }
        }

        // Prepare → Commit when votes complete
        if loc_pkt.phase == TwoPcCePhase::Prepare && self.has_unanimity(&loc_pkt.flags_bitmap) {
            self.escalate_to_commit(&mut loc_pkt, node_id);
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
        // Decision held (phase 2 entered). Progress metric for snapshots.
        serde_json::from_slice::<CeTwoPcPacket>(payload)
            .map(|p| Self::is_final(p.phase))
            .unwrap_or(false)
    }

    fn node_can_sleep(&self, payload: &[u8]) -> bool {
        // Sleep only after final phase AND full dissemination bitmap known locally.
        serde_json::from_slice::<CeTwoPcPacket>(payload)
            .map(|p| Self::is_final(p.phase) && self.has_unanimity(&p.flags_bitmap))
            .unwrap_or(false)
    }

    fn network_goal_reached(&self, payloads: &[Vec<u8>]) -> bool {
        // Unified completion: ALL N nodes hold the same final phase (Commit or Abort).
        if payloads.is_empty() || self.num_nodes == 0 {
            return false;
        }
        let mut n_commit = 0usize;
        let mut n_abort = 0usize;
        for p in payloads {
            match serde_json::from_slice::<CeTwoPcPacket>(p) {
                Ok(pkt) if pkt.phase == TwoPcCePhase::Committed => n_commit += 1,
                Ok(pkt) if pkt.phase == TwoPcCePhase::Aborted => n_abort += 1,
                _ => return false,
            }
        }
        n_commit == payloads.len() || n_abort == payloads.len()
    }

    fn proposal_outcome(&self, payloads: &[Vec<u8>]) -> ProposalOutcome {
        let parsed: Vec<Option<CeTwoPcPacket>> = payloads
            .iter()
            .map(|p| serde_json::from_slice::<CeTwoPcPacket>(p).ok())
            .collect();

        let any_abort = parsed
            .iter()
            .any(|p| matches!(p, Some(pkt) if pkt.phase == TwoPcCePhase::Aborted));
        if any_abort {
            return ProposalOutcome::Aborted;
        }

        let all_committed = parsed
            .iter()
            .all(|p| matches!(p, Some(pkt) if pkt.phase == TwoPcCePhase::Committed));
        if all_committed && !parsed.is_empty() {
            ProposalOutcome::Committed
        } else {
            // Incomplete by deadline → Abort (2PC safety)
            ProposalOutcome::Aborted
        }
    }
}
