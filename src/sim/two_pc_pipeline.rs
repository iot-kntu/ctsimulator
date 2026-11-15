//! CI Pipeline Non-blocking 2PC — implements [`CiProtocol`].

use crate::config::SimConfig;
use crate::metrics::MetricsCollector;
use crate::network::NetworkGraph;
use crate::protocol::two_pc_pipeline::{
    deserialize_packet, serialize_packet, NodeTwoPcState, TwoPcPacket, TwoPcState, Vote,
};
use crate::sim::pipeline::{run_ci_pipeline, CiPipelineCore, CiProtocol};
use rand::Rng;
use std::collections::{HashMap, HashSet};

pub struct TwoPcPipelineSim {
    pub metrics: MetricsCollector,
    core: Option<CiPipelineCore>,
    num_txs: usize,
    abort_probability: f64,
}

impl TwoPcPipelineSim {
    pub fn new(config: SimConfig, graph: NetworkGraph) -> Self {
        let num_txs = config.num_proposals;
        let abort_probability = config.abort_probability;
        Self {
            metrics: MetricsCollector::new(),
            core: Some(CiPipelineCore::new(config, graph)),
            num_txs,
            abort_probability,
        }
    }

    pub fn run(&mut self) -> (MetricsCollector, u64, u64, u64) {
        let core = self.core.take().expect("already run");
        let n = core.num_nodes();
        let proto = TwoPcCi {
            states: vec![NodeTwoPcState::new(n); n],
            tx_counter: 0,
            txs_generated: 0,
            num_txs: self.num_txs,
            abort_probability: self.abort_probability,
            tx_starts: HashMap::new(),
            piggyback_tx: None,
            piggyback_state: None,
            stop: false,
            recorded: std::collections::HashSet::new(),
        };
        let core = run_ci_pipeline(proto, core);
        self.metrics = core.metrics.clone();
        let l = core.nodes.iter().map(|nd| nd.slots_listen).sum();
        let f = core.nodes.iter().map(|nd| nd.slots_flood).sum();
        let s = core.nodes.iter().map(|nd| nd.slots_sleep).sum();
        (core.metrics, l, f, s)
    }
}

struct TwoPcCi {
    states: Vec<NodeTwoPcState>,
    tx_counter: u64,
    txs_generated: usize,
    num_txs: usize,
    abort_probability: f64,
    /// Slot at which each tx was first floated by its originating initiator.
    /// Same pattern as `paxos_pipeline::PaxosCi::proposal_starts` and
    /// `tom_pipeline::TomCi::msg_starts`: recorded directly, never derived from
    /// a round index times a round length.
    tx_starts: HashMap<u64, u64>,
    piggyback_tx: Option<u64>,
    piggyback_state: Option<TwoPcState>,
    stop: bool,
    recorded: HashSet<u64>,
}

impl TwoPcCi {
    /// Draw a node's one-time ternary vote for a transaction it is seeing for
    /// the first time. The vote is sampled ONCE here and then persists in the
    /// pending state; the window builder never re-draws it.
    fn draw_vote(&self, rng: &mut impl Rng) -> Vote {
        if rng.gen::<f64>() < self.abort_probability {
            Vote::No
        } else {
            Vote::Yes
        }
    }
}

impl CiProtocol for TwoPcCi {
    fn name(&self) -> &str {
        "Non-blocking 2PC Pipeline"
    }

    fn on_start(&mut self, core: &mut CiPipelineCore) {
        if !core.config.quiet {
            println!(
                "Starting Non-blocking 2PC Pipeline (CI mode): {} nodes, {} TXs, diameter={}",
                core.num_nodes(),
                self.num_txs,
                core.graph.diameter()
            );
        }
    }

    fn prepare_round(&mut self, core: &mut CiPipelineCore) {
        let initiator = core.initiator();
        let num_nodes = core.num_nodes();
        let round_num = core.round_num;

        let nack_tx_id = {
            let gap = self.states[initiator]
                .detect_gap(self.tx_counter)
                .unwrap_or(0);
            if gap > 0 {
                core.metrics.nacks_sent += 1;
            }
            gap
        };

        let (current_tx, tx_data, tx_state) = if self.txs_generated < self.num_txs {
            let d = format!("tx{}", self.tx_counter).into_bytes();
            let t = self.tx_counter;
            let self_vote = self.draw_vote(&mut core.rng);
            self.states[initiator].adopt_tx(t, d.clone(), self_vote, initiator, round_num);
            self.tx_counter += 1;
            self.txs_generated += 1;
            self.tx_starts.insert(t, core.current_slot);
            (t, d, TwoPcState::Prepare)
        } else {
            (
                self.tx_counter.saturating_sub(1),
                Vec::new(),
                TwoPcState::Prepare,
            )
        };

        // Bounded decision at the deadline: commit iff all N senders YES,
        // otherwise abort (this is what makes a remaining UNKNOWN abort safely).
        // Single production rule via `deadline_resolutions` — shared with the
        // receiver path in `process_round`, so the decision cannot drift.
        let state = &mut self.states[initiator];
        let newly_resolved = state.deadline_resolutions(round_num, num_nodes);
        for (t, s) in newly_resolved {
            state.finalize_tx(t, s);
            if self.recorded.insert(t) {
                let outcome = match s {
                    TwoPcState::Committed => crate::protocol::ProposalOutcome::Committed,
                    TwoPcState::Aborted => crate::protocol::ProposalOutcome::Aborted,
                    _ => unreachable!(),
                };
                let start_slot = self.tx_starts.get(&t).copied().unwrap_or(0);
                core.metrics
                    .record_proposal(t as usize, start_slot, core.current_slot, outcome);
            }
        }

        // Prune own-vote history that has permanently left every future N-term
        // window BEFORE building this round's windows: keep the oldest still
        // reportable vote (>= window_start(current_tx, N)) and drop only entries
        // strictly below it. During drain rounds current_tx stops advancing, so
        // the final N reportable votes are retained.
        self.states[initiator].prune_own_votes(current_tx);

        let (vote_valid_window, vote_yes_window) =
            self.states[initiator].build_vote_windows(current_tx, initiator);

        let pkt = TwoPcPacket {
            transaction_id: current_tx,
            state: tx_state,
            vote_valid_window,
            vote_yes_window,
            nack_tx_id,
            sender: initiator,
            transaction_data: tx_data,
            piggyback_tx: self.piggyback_tx,
            piggyback_state: self.piggyback_state,
        };
        core.nodes[initiator].payload = serialize_packet(&pkt);
    }

    fn process_round(&mut self, core: &mut CiPipelineCore) {
        let num_nodes = core.num_nodes();
        let num_txs = self.num_txs;
        let round_num = core.round_num;
        self.piggyback_tx = None;
        self.piggyback_state = None;

        let mut all_resolved = true;

        for i in 0..num_nodes {
            if !core.participated(i) {
                if self.states[i].log.len() < num_txs {
                    all_resolved = false;
                }
                continue;
            }

            let Some(received_pkt) = deserialize_packet(&core.nodes[i].payload) else {
                all_resolved = false;
                continue;
            };
            // Reject a malformed packet wholesale BEFORE any state mutation:
            // both windows must be exactly N long, every ternary pair legal
            // (never valid=false, yes=true), and sender < N. A short/long/
            // mismatched/illegal/out-of-range packet creates no tx, vote,
            // deferred vote, outcome, quorum, or metric effect.
            if !received_pkt.is_well_formed(num_nodes) {
                all_resolved = false;
                continue;
            }

            // First sight of the transaction: draw this node's own vote ONCE.
            if !received_pkt.transaction_data.is_empty() {
                let tx_id = received_pkt.transaction_id;
                let known = self.states[i].pending.iter().any(|p| p.tx_id == tx_id)
                    || self.states[i].log.iter().any(|(lt, _)| *lt == tx_id);
                if !known {
                    let self_vote = self.draw_vote(&mut core.rng);
                    self.states[i].adopt_tx(
                        tx_id,
                        received_pkt.transaction_data.clone(),
                        self_vote,
                        i,
                        round_num,
                    );
                }
            }

            let state = &mut self.states[i];

            // Apply the sender-owned ternary vote windows: each valid position
            // credits the packet's sender for exactly the mapped transaction.
            state.apply_vote_windows(
                received_pkt.transaction_id,
                received_pkt.sender,
                &received_pkt.vote_valid_window,
                &received_pkt.vote_yes_window,
            );

            // Fast abort: any transaction with a recorded NO aborts immediately,
            // and only that transaction.
            let mut fast_aborts = Vec::new();
            for p in &state.pending {
                if p.any_no() {
                    fast_aborts.push(p.tx_id);
                }
            }
            for t in fast_aborts {
                state.finalize_tx(t, TwoPcState::Aborted);
                if self.recorded.insert(t) {
                    // Same start-slot source as the commit path above: the slot the
                    // tx was floated, not a hand-written 0.
                    let start_slot = self.tx_starts.get(&t).copied().unwrap_or(0);
                    core.metrics.record_proposal(
                        t as usize,
                        start_slot,
                        core.current_slot,
                        crate::protocol::ProposalOutcome::Aborted,
                    );
                }
            }

            if let (Some(ptx), Some(pst)) =
                (received_pkt.piggyback_tx, received_pkt.piggyback_state)
            {
                if !state.log.iter().any(|(lt, _)| *lt == ptx) {
                    state.finalize_tx(ptx, pst);
                }
            }

            if received_pkt.nack_tx_id > 0 {
                if let Some((_, log_state)) = state
                    .log
                    .iter()
                    .find(|(t, _)| *t == received_pkt.nack_tx_id)
                {
                    self.piggyback_tx = Some(received_pkt.nack_tx_id);
                    self.piggyback_state = Some(*log_state);
                    core.metrics.piggybacks_sent += 1;
                }
            }

            // Bounded finalize (overhearing-based non-blocking): at the deadline
            // commit only if all N senders are YES; any remaining UNKNOWN aborts.
            // Same single production rule as the initiator path in prepare_round.
            let newly_resolved_rx = state.deadline_resolutions(round_num, num_nodes);
            for (t, s) in newly_resolved_rx {
                state.finalize_tx(t, s);
            }

            if state.log.len() < num_txs {
                all_resolved = false;
            }
        }

        // Unified progress: how many txs EVERY node has finalized.
        let min_done = self.states.iter().map(|s| s.log.len()).min().unwrap_or(0);
        for i in 0..num_nodes {
            core.nodes[i].goal_reached = self.states[i].log.len() >= num_txs;
            core.nodes[i].progress_count = min_done;
        }

        if all_resolved && self.txs_generated >= num_txs {
            if !core.config.quiet {
                println!(
                    "All nodes successfully finalized {} transactions at round {}",
                    num_txs, core.round_num
                );
            }
            self.stop = true;
        } else if core.round_num as usize > num_txs + 1000 {
            if !core.config.quiet {
                println!("Timeout: infinite idle rounds.");
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
    use crate::config::{CiConfig, NetworkConfig};
    use crate::node::NodeState;
    use crate::protocol::two_pc_pipeline::Vote;

    fn core_with_nodes(n: usize) -> CiPipelineCore {
        let config = SimConfig {
            seed: 7,
            phy_mode: "ci".to_string(),
            protocol: "2pc_pipeline".to_string(),
            network: NetworkConfig {
                num_nodes: n,
                topology: "full_mesh".to_string(),
                loss_rate: 0.0,
                graph_file: None,
            },
            ci: Some(CiConfig {
                flood_repeats: 1,
                round_slots: None,
            }),
            ce: None,
            num_proposals: 5,
            snapshot_interval: 100,
            max_slots: 100_000,
            abort_probability: 0.0,
            quiet: true,
        };
        CiPipelineCore::new(config, NetworkGraph::full_mesh(n))
    }

    fn proto(n: usize) -> TwoPcCi {
        TwoPcCi {
            states: vec![NodeTwoPcState::new(n); n],
            tx_counter: 0,
            txs_generated: 0,
            num_txs: 5,
            abort_probability: 0.0,
            tx_starts: HashMap::new(),
            piggyback_tx: None,
            piggyback_state: None,
            stop: false,
            recorded: HashSet::new(),
        }
    }

    fn base_pkt(n: usize) -> TwoPcPacket {
        TwoPcPacket {
            transaction_id: 2,
            state: TwoPcState::Prepare,
            vote_valid_window: vec![false; n],
            vote_yes_window: vec![false; n],
            nack_tx_id: 0,
            transaction_data: b"tx2".to_vec(),
            sender: 1,
            piggyback_tx: None,
            piggyback_state: None,
        }
    }

    /// B2 (2PC), real simulator packet-processing path: a malformed packet must be
    /// rejected wholesale before ANY state mutation — no tx adoption, no local or
    /// deferred vote, no recovery scheduling, no finalization, no quorum, no
    /// metric change. Node 2 is a participating receiver (Sleep); its payload is a
    /// malformed 2PC packet. After `process_round` its state and the metrics must
    /// be exactly as before.
    fn assert_malformed_pkt_is_rejected(pkt: TwoPcPacket) {
        let n = 4;
        let mut core = core_with_nodes(n);
        core.round_num = 0; // initiator = node 0
        let mut p = proto(n);

        // Node 2 participates as a receiver and carries the malformed packet.
        core.nodes[2].state = NodeState::Sleep;
        core.nodes[2].payload = serialize_packet(&pkt);

        p.process_round(&mut core);

        let st = &p.states[2];
        assert!(
            st.pending.is_empty(),
            "no tx may be adopted from a malformed packet"
        );
        assert!(st.log.is_empty(), "no finalization from a malformed packet");
        assert!(
            st.deferred_votes.is_empty(),
            "no deferred vote from a malformed packet"
        );
        assert!(
            st.own_votes.is_empty(),
            "no own vote sampled from a malformed packet"
        );
        assert_eq!(core.metrics.proposals.len(), 0, "no metric mutation");
        assert_eq!(core.metrics.piggybacks_sent, 0, "no recovery scheduled");
        assert_eq!(
            p.piggyback_tx, None,
            "no piggyback scheduled from a malformed packet"
        );
    }

    #[test]
    fn two_pc_sim_rejects_short_windows() {
        let n = 4;
        let mut pkt = base_pkt(n);
        pkt.vote_valid_window = vec![false; n - 1];
        pkt.vote_yes_window = vec![false; n - 1];
        assert_malformed_pkt_is_rejected(pkt);
    }

    #[test]
    fn two_pc_sim_rejects_long_windows() {
        let n = 4;
        let mut pkt = base_pkt(n);
        pkt.vote_valid_window = vec![false; n + 1];
        pkt.vote_yes_window = vec![false; n + 1];
        assert_malformed_pkt_is_rejected(pkt);
    }

    #[test]
    fn two_pc_sim_rejects_mismatched_windows() {
        let n = 4;
        let mut pkt = base_pkt(n);
        pkt.vote_valid_window = vec![false; n];
        pkt.vote_yes_window = vec![false; n - 1];
        assert_malformed_pkt_is_rejected(pkt);
    }

    #[test]
    fn two_pc_sim_rejects_illegal_ternary_pair() {
        let n = 4;
        let mut pkt = base_pkt(n);
        // valid=false, yes=true at position 0 — the illegal encoding.
        pkt.vote_valid_window[0] = false;
        pkt.vote_yes_window[0] = true;
        assert_malformed_pkt_is_rejected(pkt);
    }

    #[test]
    fn two_pc_sim_rejects_out_of_range_sender() {
        let n = 4;
        let mut pkt = base_pkt(n);
        pkt.sender = n; // sender must be < n
        assert_malformed_pkt_is_rejected(pkt);
    }

    /// A WELL-FORMED packet on the same path DOES mutate state: this pins that the
    /// rejection above is specific to malformed packets, not a dead code path.
    #[test]
    fn two_pc_sim_accepts_well_formed_packet() {
        let n = 4;
        let mut core = core_with_nodes(n);
        core.round_num = 0;
        let mut p = proto(n);

        // window_start(2,4)=0 -> positions 0,1,2,3 -> tx 0,1,2,3; tx 3 is padding.
        // Put sender-1's YES on the position mapping to tx 2.
        let mut pkt = base_pkt(n);
        pkt.vote_valid_window = vec![false, false, true, false];
        pkt.vote_yes_window = vec![false, false, true, false];

        core.nodes[2].state = NodeState::Sleep;
        core.nodes[2].payload = serialize_packet(&pkt);

        p.process_round(&mut core);

        // Node 2 adopted tx 2 (it had transaction_data) and recorded sender 1's YES.
        let st = &p.states[2];
        let tx2 = st.pending.iter().find(|q| q.tx_id == 2);
        assert!(tx2.is_some(), "a well-formed packet must adopt the tx");
        assert_eq!(
            tx2.unwrap().votes[1],
            Vote::Yes,
            "sender 1's YES for tx 2 must be applied on the real path"
        );
    }

    /// Production own-vote pruning: `prepare_round` must prune the initiator's
    /// own-vote history that has left every future N-term window BEFORE building
    /// the packet, keep the oldest still-reportable vote, and emit a window with
    /// exactly N positions carrying the correct bits. This drives the REAL
    /// initiator packet-build path (there is no other production caller of
    /// `prune_own_votes`), not a direct helper call.
    #[test]
    fn prepare_round_prunes_old_own_votes_and_emits_correct_window() {
        let n = 4;
        let mut core = core_with_nodes(n);
        let mut p = proto(n);
        p.num_txs = 6; // allow the window to advance to tx 5, then drain

        // Drive the initiator (node 0 at round_num 0) through the real
        // prepare_round path. Each generating call proposes the next tx and
        // records node 0's own vote; drain rounds (after num_txs) stop advancing
        // current_tx and must retain the final N reportable votes.
        p.abort_probability = 0.0; // deterministic YES votes
        for r in 0..8u64 {
            core.round_num = r * n as u64; // keep node 0 as initiator each time
            p.prepare_round(&mut core);
        }
        // 6 proposals (tx 0..5), then 2 drain rounds. The last advertised window
        // ends at tx 5; window_start(5,4) = 2, so tx 0 and tx 1 have permanently
        // left every future window and must have been pruned; tx 2 (the oldest
        // still reportable) must remain, and the final N=4 reportable votes
        // (tx 2,3,4,5) are all retained through the drain rounds.
        let st = &p.states[0];
        assert_eq!(
            st.own_vote(0),
            Vote::Unknown,
            "tx 0 left the window -> own vote must be pruned in production"
        );
        assert_eq!(
            st.own_vote(1),
            Vote::Unknown,
            "tx 1 left the window -> own vote must be pruned in production"
        );
        assert_eq!(
            st.own_vote(2),
            Vote::Yes,
            "tx 2 is the oldest still-reportable vote and must be preserved"
        );
        for t in 2..=5u64 {
            assert_eq!(
                st.own_vote(t),
                Vote::Yes,
                "the final N reportable votes must survive drain rounds (tx {t})"
            );
        }

        // The emitted packet is well-formed: exactly N positions, each window N
        // long, and the bits are node 0's own YES votes across the current window.
        let pkt = deserialize_packet(&core.nodes[0].payload).expect("packet emitted");
        assert!(
            pkt.is_well_formed(n),
            "the production packet must have two N-length windows and legal bits"
        );
        assert_eq!(pkt.vote_valid_window.len(), n);
        assert_eq!(pkt.vote_yes_window.len(), n);
        // Window ends at tx 5, N=4 -> positions map to tx 2,3,4,5, all own YES.
        assert_eq!(pkt.vote_valid_window, vec![true, true, true, true]);
        assert_eq!(pkt.vote_yes_window, vec![true, true, true, true]);
    }
}
