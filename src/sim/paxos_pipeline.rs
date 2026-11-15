//! CI Pipeline Paxos — implements [`CiProtocol`] over identical floods.

use crate::config::SimConfig;
use crate::metrics::MetricsCollector;
use crate::network::NetworkGraph;
use crate::protocol::paxos::{deserialize_packet, serialize_packet, NodePaxosState, PaxosPacket};
use crate::sim::pipeline::{run_ci_pipeline, CiPipelineCore, CiProtocol};
use std::collections::{HashMap, HashSet};

/// Public façade used by `main` (metrics after `run`).
pub struct PaxosPipelineSim {
    pub metrics: MetricsCollector,
    core: Option<CiPipelineCore>,
    num_proposals: usize,
}

impl PaxosPipelineSim {
    pub fn new(config: SimConfig, graph: NetworkGraph) -> Self {
        let num_proposals = config.num_proposals;
        Self {
            metrics: MetricsCollector::new(),
            core: Some(CiPipelineCore::new(config, graph)),
            num_proposals,
        }
    }

    pub fn run(&mut self) -> (MetricsCollector, u64, u64, u64) {
        let core = self.core.take().expect("already run");
        let n = core.num_nodes();
        let proto = PaxosCi {
            states: vec![NodePaxosState::new(); n],
            term_counter: 0,
            proposals_generated: 0,
            num_proposals: self.num_proposals,
            proposal_starts: HashMap::new(),
            globally_committed: HashSet::new(),
            piggyback_term: None,
            piggyback_data: Vec::new(),
            stop: false,
        };
        let core = run_ci_pipeline(proto, core);
        self.metrics = core.metrics.clone();
        let l = core.nodes.iter().map(|nd| nd.slots_listen).sum();
        let f = core.nodes.iter().map(|nd| nd.slots_flood).sum();
        let s = core.nodes.iter().map(|nd| nd.slots_sleep).sum();
        (core.metrics, l, f, s)
    }
}

struct PaxosCi {
    states: Vec<NodePaxosState>,
    term_counter: u64,
    proposals_generated: usize,
    num_proposals: usize,
    proposal_starts: HashMap<u64, u64>,
    globally_committed: HashSet<u64>,
    piggyback_term: Option<u64>,
    piggyback_data: Vec<u8>,
    stop: bool,
}

impl CiProtocol for PaxosCi {
    fn name(&self) -> &str {
        "Pipeline Paxos"
    }

    fn on_start(&mut self, core: &mut CiPipelineCore) {
        if !core.config.quiet {
            println!(
                "Starting Pipeline Paxos (CI mode): {} nodes, {} proposals, diameter={}",
                core.num_nodes(),
                self.num_proposals,
                core.graph.diameter()
            );
        }
    }

    fn prepare_round(&mut self, core: &mut CiPipelineCore) {
        let initiator = core.initiator();
        let num_nodes = core.num_nodes();
        let state = &mut self.states[initiator];

        let nack_term = state.detect_gap(self.term_counter).unwrap_or(0);
        if nack_term > 0 {
            core.metrics.nacks_sent += 1;
            if !core.config.quiet {
                println!(
                    "[CI Recovery] Slot {} | Node {} sending NACK for missing term {}",
                    core.current_slot, initiator, nack_term
                );
            }
        }

        let (current_term, proposal_data) = if self.proposals_generated < self.num_proposals {
            let d = format!("tx{}", self.term_counter).into_bytes();
            let t = self.term_counter;
            state.adopt_proposal(t, d.clone(), num_nodes, initiator);
            self.term_counter += 1;
            self.proposals_generated += 1;
            self.proposal_starts.insert(t, core.current_slot);
            (t, d)
        } else {
            (self.term_counter.saturating_sub(1), Vec::new())
        };

        let mut highest_quorum = None;
        for p in &state.pending {
            if state.check_quorum(p.term) && highest_quorum.unwrap_or(0) <= p.term {
                highest_quorum = Some(p.term);
            }
        }
        if let Some(t) = highest_quorum {
            let newly = state.commit_up_to(t);
            for ct in newly {
                if self.globally_committed.insert(ct) {
                    let start = self.proposal_starts.get(&ct).copied().unwrap_or(0);
                    core.metrics.record_proposal(
                        ct as usize,
                        start,
                        core.current_slot,
                        crate::protocol::ProposalOutcome::Committed,
                    );
                }
            }
        }

        let ack_window = state.build_ack_window(current_term, num_nodes, initiator);

        let pkt = PaxosPacket {
            current_term,
            last_accepted_term: state.last_accepted,
            ack_window,
            nack_term,
            sender: initiator,
            proposal_data,
            piggyback_term: self.piggyback_term,
            piggyback_data: self.piggyback_data.clone(),
        };
        core.nodes[initiator].payload = serialize_packet(&pkt);
    }

    fn process_round(&mut self, core: &mut CiPipelineCore) {
        let num_nodes = core.num_nodes();
        let num_proposals = self.num_proposals;
        self.piggyback_term = None;
        self.piggyback_data.clear();

        let mut all_committed = true;

        for i in 0..num_nodes {
            if !core.participated(i) {
                if self.states[i].log.len() < num_proposals {
                    all_committed = false;
                }
                continue;
            }

            let state = &mut self.states[i];
            let Some(received_pkt) = deserialize_packet(&core.nodes[i].payload) else {
                all_committed = false;
                continue;
            };
            // Reject a malformed packet wholesale BEFORE any state mutation:
            // the ACK window must be exactly N long and sender < N. A short/long/
            // out-of-range packet adopts no proposal, applies no ACK, drives no
            // recovery, commits nothing, and mutates no metric.
            if !received_pkt.is_well_formed(num_nodes) {
                all_committed = false;
                continue;
            }

            if let Some(lat) = received_pkt.last_accepted_term {
                let newly = state.commit_up_to(lat);
                for ct in newly {
                    if self.globally_committed.insert(ct) {
                        let start = self.proposal_starts.get(&ct).copied().unwrap_or(0);
                        core.metrics.record_proposal(
                            ct as usize,
                            start,
                            core.current_slot,
                            crate::protocol::ProposalOutcome::Committed,
                        );
                    }
                }
            }

            if state.highest_seen_term.unwrap_or(0) < received_pkt.current_term {
                state.highest_seen_term = Some(received_pkt.current_term);
            }

            if !received_pkt.proposal_data.is_empty() {
                let term = received_pkt.current_term;
                state.adopt_proposal(term, received_pkt.proposal_data.clone(), num_nodes, i);
            }

            // Apply the sender-owned ACK window: each true position credits the
            // packet's sender for exactly the mapped term (term-indexed), and our
            // own vote for the current term.
            state.apply_ack_window(
                received_pkt.current_term,
                num_nodes,
                received_pkt.sender,
                &received_pkt.ack_window,
            );
            if let Some(p) = state
                .pending
                .iter_mut()
                .find(|p| p.term == received_pkt.current_term)
            {
                if i < p.bitmap.len() {
                    p.bitmap[i] = true;
                }
            }

            // NACK recovery: any node that holds the term (log or pending) can piggyback.
            if received_pkt.nack_term > 0 {
                let data = state
                    .log
                    .iter()
                    .find(|(t, _)| *t == received_pkt.nack_term)
                    .map(|(_, d)| d.clone())
                    .or_else(|| {
                        state
                            .pending
                            .iter()
                            .find(|p| p.term == received_pkt.nack_term)
                            .map(|p| p.data.clone())
                    });
                if let Some(data) = data {
                    self.piggyback_term = Some(received_pkt.nack_term);
                    self.piggyback_data = data;
                    core.metrics.piggybacks_sent += 1;
                    if !core.config.quiet {
                        println!(
                            "[CI Recovery] Slot {} | Node {} preparing piggyback data for term {}",
                            core.current_slot, i, received_pkt.nack_term
                        );
                    }
                }
            }

            if let Some(pt) = received_pkt.piggyback_term {
                // Recovered proposal data: adopt with only the receiver's own
                // vote, plus any ACKs that were deferred while the data was
                // missing. adopt_proposal drains deferred_votes for this term.
                state.adopt_proposal(pt, received_pkt.piggyback_data.clone(), num_nodes, i);
            }

            if state.log.len() < num_proposals {
                all_committed = false;
            } else {
                for t in 0..num_proposals {
                    if !state.log.iter().any(|(lt, _)| *lt == t as u64) {
                        all_committed = false;
                        break;
                    }
                }
            }
        }

        // Unified progress: how many proposals EVERY node has finalized (all-N informed).
        let min_done = self.states.iter().map(|s| s.log.len()).min().unwrap_or(0);
        for i in 0..num_nodes {
            core.nodes[i].goal_reached = self.states[i].log.len() == num_proposals;
            core.nodes[i].progress_count = min_done;
        }

        if all_committed && self.proposals_generated >= num_proposals {
            if !core.config.quiet {
                println!(
                    "All nodes successfully committed {} proposals at round {}",
                    num_proposals, core.round_num
                );
            }
            self.stop = true;
            return;
        }

        if self.globally_committed.len() == num_proposals {
            let max_drain = core.graph.diameter() * 5;
            if core.round_num as usize > num_proposals + max_drain {
                if !core.config.quiet {
                    println!(
                        "Graceful termination at round {} (all proposals globally committed, stragglers drained).",
                        core.round_num
                    );
                }
                self.stop = true;
                return;
            }
        }

        if core.round_num as usize > num_proposals + 1000 {
            if !core.config.quiet {
                println!("Timeout reached due to infinite idle rounds.");
            }
            self.stop = true;
        }
    }

    fn should_stop(&self, _core: &CiPipelineCore) -> bool {
        self.stop
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::{CiConfig, NetworkConfig, SimConfig};
    use crate::network::NetworkGraph;
    use crate::node::NodeState;

    /// Regression test for issue 257: the piggyback-recovery insertion at
    /// lines 240-252 must NOT create a PendingProposal with an all-ones
    /// bitmap.
    ///
    /// Strategy: construct a minimal PaxosCi + CiPipelineCore, inject a
    /// serialized PaxosPacket carrying piggyback_term = Some(7) into one
    /// node's payload, call process_round, then inspect the bitmap of the
    /// PendingProposal that was created for term 7.
    ///
    /// With the old bug (bitmap: vec![true; num_nodes]), check_quorum
    /// returns true and the vote count equals num_nodes. This test catches
    /// both.
    #[test]
    fn piggyback_bitmap_does_not_grant_instant_quorum() {
        let num_nodes = 5;

        // Build a full-mesh graph so every node participates in every
        // flood round.
        let graph = NetworkGraph::full_mesh(num_nodes);

        let config = SimConfig {
            seed: 1,
            phy_mode: "ci".to_string(),
            protocol: "paxos_pipeline".to_string(),
            network: NetworkConfig {
                num_nodes,
                topology: "full_mesh".to_string(),
                loss_rate: 0.0,
                graph_file: None,
            },
            ci: Some(CiConfig {
                flood_repeats: 1,
                round_slots: Some(10),
            }),
            ce: None,
            num_proposals: 10,
            snapshot_interval: 50,
            max_slots: 100_000,
            abort_probability: 0.0,
            quiet: true,
        };

        let mut core = CiPipelineCore::new(config, graph);

        let mut proto = PaxosCi {
            states: vec![NodePaxosState::new(); num_nodes],
            term_counter: 0,
            proposals_generated: 0,
            num_proposals: 10,
            proposal_starts: HashMap::new(),
            globally_committed: HashSet::new(),
            piggyback_term: None,
            piggyback_data: Vec::new(),
            stop: false,
        };

        // Craft a PaxosPacket that carries a piggybacked term 7.
        // Node 2 will receive this packet.
        let receiving_node: usize = 2;
        let piggyback_term: u64 = 7;
        let pkt = PaxosPacket {
            current_term: 0,
            last_accepted_term: None,
            ack_window: vec![false; num_nodes],
            nack_term: 0,
            sender: 0,
            proposal_data: Vec::new(),
            piggyback_term: Some(piggyback_term),
            piggyback_data: b"tx7".to_vec(),
        };

        // Place the crafted packet into the receiving node's payload and
        // mark it as having participated (Sleep = finished flood round).
        core.nodes[receiving_node].payload = serialize_packet(&pkt);
        core.nodes[receiving_node].state = NodeState::Sleep;

        // Run process_round -- this executes the piggyback-insertion path.
        proto.process_round(&mut core);

        // Inspect the PendingProposal created for the piggybacked term.
        let state = &proto.states[receiving_node];
        let pp = state
            .pending
            .iter()
            .find(|p| p.term == piggyback_term)
            .expect("piggyback term 7 must appear in pending");

        // REGRESSION ASSERTION 1: bitmap must NOT be all-true.
        let vote_count: usize = pp.bitmap.iter().filter(|&&b| b).count();
        assert_eq!(
            vote_count, 1,
            "piggyback bitmap must have exactly 1 vote (the receiver's), \
             got {vote_count} out of {num_nodes}"
        );
        assert!(
            pp.bitmap[receiving_node],
            "the set bit must be the receiving node's (index {receiving_node})"
        );

        // REGRESSION ASSERTION 2: check_quorum must return false.
        assert!(
            !state.check_quorum(piggyback_term),
            "piggyback proposal must NOT have quorum with 1/{num_nodes} \
             votes (majority = {})",
            num_nodes / 2 + 1
        );
    }

    fn paxos_core(n: usize) -> CiPipelineCore {
        let config = SimConfig {
            seed: 1,
            phy_mode: "ci".to_string(),
            protocol: "paxos_pipeline".to_string(),
            network: NetworkConfig {
                num_nodes: n,
                topology: "full_mesh".to_string(),
                loss_rate: 0.0,
                graph_file: None,
            },
            ci: Some(CiConfig {
                flood_repeats: 1,
                round_slots: Some(10),
            }),
            ce: None,
            num_proposals: 10,
            snapshot_interval: 50,
            max_slots: 100_000,
            abort_probability: 0.0,
            quiet: true,
        };
        CiPipelineCore::new(config, NetworkGraph::full_mesh(n))
    }

    fn paxos_proto(n: usize) -> PaxosCi {
        PaxosCi {
            states: vec![NodePaxosState::new(); n],
            term_counter: 0,
            proposals_generated: 0,
            num_proposals: 10,
            proposal_starts: HashMap::new(),
            globally_committed: HashSet::new(),
            piggyback_term: None,
            piggyback_data: Vec::new(),
            stop: false,
        }
    }

    /// B2 (Paxos), real simulator packet-processing path: a malformed ACK-window
    /// packet must be rejected wholesale before ANY state mutation — no proposal
    /// adoption, no ACK applied, no deferred vote, no recovery scheduled, no
    /// commit, no metric change.
    fn assert_paxos_malformed_pkt_is_rejected(pkt: PaxosPacket) {
        let n = 5;
        let receiving_node = 2;
        let mut core = paxos_core(n);
        core.round_num = 0;
        let mut proto = paxos_proto(n);

        core.nodes[receiving_node].payload = serialize_packet(&pkt);
        core.nodes[receiving_node].state = NodeState::Sleep;

        proto.process_round(&mut core);

        let st = &proto.states[receiving_node];
        assert!(
            st.pending.is_empty(),
            "no proposal adopted from a malformed packet"
        );
        assert!(st.log.is_empty(), "no commit from a malformed packet");
        assert!(
            st.deferred_votes.is_empty(),
            "no deferred ACK from a malformed packet"
        );
        assert_eq!(core.metrics.proposals.len(), 0, "no metric mutation");
        assert_eq!(core.metrics.piggybacks_sent, 0, "no recovery scheduled");
        assert_eq!(proto.piggyback_term, None, "no piggyback scheduled");
    }

    #[test]
    fn paxos_sim_rejects_short_ack_window() {
        let n = 5;
        let mut pkt = base_paxos_pkt(n);
        pkt.ack_window = vec![false; n - 1];
        assert_paxos_malformed_pkt_is_rejected(pkt);
    }

    #[test]
    fn paxos_sim_rejects_long_ack_window() {
        let n = 5;
        let mut pkt = base_paxos_pkt(n);
        pkt.ack_window = vec![false; n + 1];
        assert_paxos_malformed_pkt_is_rejected(pkt);
    }

    #[test]
    fn paxos_sim_rejects_out_of_range_sender() {
        let n = 5;
        let mut pkt = base_paxos_pkt(n);
        pkt.sender = n; // must be < n
        assert_paxos_malformed_pkt_is_rejected(pkt);
    }

    fn base_paxos_pkt(n: usize) -> PaxosPacket {
        PaxosPacket {
            current_term: 3,
            last_accepted_term: None,
            ack_window: vec![false; n],
            nack_term: 0,
            sender: 1,
            proposal_data: b"tx3".to_vec(),
            piggyback_term: None,
            piggyback_data: Vec::new(),
        }
    }

    /// B2/recovery (Paxos): a real NACK -> piggyback -> recovered-proposal flow
    /// through `process_round`. A node missing term 4's proposal, but holding a
    /// deferred ACK for it, hears a piggyback carrying term 4's data. Recovery
    /// must adopt the proposal with ONLY the earned votes (the receiver's own
    /// vote + the deferred ACK), never fabricating quorum, and drain the deferred
    /// vote. Exercises the actual packet path, not just `detect_gap`/`adopt_*`.
    #[test]
    fn paxos_sim_recovery_preserves_deferred_vote_without_fabricating_quorum() {
        let n = 5;
        let receiving_node = 2;
        let mut core = paxos_core(n);
        core.round_num = 0;
        let mut proto = paxos_proto(n);

        // Pre-state: node 2 committed terms 0..=2 and is missing term 4's data,
        // but has a deferred ACK from sender 3 for term 4.
        {
            let st = &mut proto.states[receiving_node];
            for t in 0..=2u64 {
                st.adopt_proposal(t, format!("tx{t}").into_bytes(), n, receiving_node);
                st.commit_term(t);
            }
            st.defer_vote(4, 3);
            assert!(st.pending.iter().all(|p| p.term != 4));
        }

        // A well-formed packet arrives carrying term 4's proposal as a piggyback
        // (recovery), plus an ACK window (sender 1 acks its own current term 4).
        let mut ack = vec![false; n];
        ack[n - 1] = true; // window ends at current_term 4, position n-1 -> term 4
        let pkt = PaxosPacket {
            current_term: 4,
            last_accepted_term: None,
            ack_window: ack,
            nack_term: 0,
            sender: 1,
            proposal_data: Vec::new(),
            piggyback_term: Some(4),
            piggyback_data: b"tx4".to_vec(),
        };
        core.nodes[receiving_node].payload = serialize_packet(&pkt);
        core.nodes[receiving_node].state = NodeState::Sleep;

        proto.process_round(&mut core);

        let st = &proto.states[receiving_node];
        let p4 = st
            .pending
            .iter()
            .find(|p| p.term == 4)
            .expect("recovered term 4 must be adopted via piggyback");
        // Earned votes only: receiver's own (node 2), deferred sender 3, and the
        // in-packet sender 1 ACK for term 4. Nodes 0 and 4 must stay unvoted.
        assert!(p4.bitmap[receiving_node], "receiver's own vote present");
        assert!(
            p4.bitmap[3],
            "deferred ACK from sender 3 preserved through recovery"
        );
        assert!(p4.bitmap[1], "in-packet sender-1 ACK for term 4 applied");
        assert!(!p4.bitmap[0], "recovery must not fabricate node 0's vote");
        assert!(!p4.bitmap[4], "recovery must not fabricate node 4's vote");
        assert!(
            st.deferred_votes.iter().all(|&(t, _)| t != 4),
            "the deferred vote for term 4 must be drained once applied"
        );
    }
}
