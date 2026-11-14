//! Core simulator — CE (goal-based) orchestrator + CI pipeline modules.
//!
//! CI protocols use dedicated pipeline sims (`paxos_pipeline`, `two_pc_pipeline`,
//! `tom_pipeline`) built on shared [`pipeline`] (`CiProtocol` + `flood_identical_round`).
//! This `Simulator` runs **CE-only** Protocol-trait workloads.

pub mod paxos_pipeline;
pub mod pipeline;
pub mod pure_flood;
pub mod tom_pipeline;
pub mod two_pc_pipeline;

use crate::config::SimConfig;
use crate::metrics::MetricsCollector;
use crate::network::NetworkGraph;
use crate::node::Node;
use crate::phy::ce;
use crate::protocol::Protocol;
use rand::SeedableRng;
use rand_chacha::ChaCha8Rng;

/// CE-mode simulator (one goal-based round per proposal).
pub struct Simulator {
    pub config: SimConfig,
    pub graph: NetworkGraph,
    pub nodes: Vec<Node>,
    pub metrics: MetricsCollector,
    pub rng: ChaCha8Rng,
    pub current_slot: u64,
}

impl Simulator {
    pub fn new(config: SimConfig, graph: NetworkGraph) -> Self {
        let n = graph.num_nodes();
        let nodes: Vec<Node> = (0..n).map(Node::new).collect();
        let rng = ChaCha8Rng::seed_from_u64(config.seed);

        if config.phy_mode != "ce" {
            panic!(
                "Simulator is CE-only (got phy_mode={}). Use paxos_pipeline / 2pc_pipeline / tom_pipeline for CI.",
                config.phy_mode
            );
        }
        if config.ce.is_none() {
            panic!("CE config section [ce] is required");
        }

        Self {
            config,
            graph,
            nodes,
            metrics: MetricsCollector::new(),
            rng,
            current_slot: 0,
        }
    }

    /// Run sequential CE rounds for each proposal via the Protocol trait.
    pub fn run(&mut self, protocol: &mut dyn Protocol) {
        let num_proposals = self.config.num_proposals;
        let snapshot_interval = self.config.snapshot_interval;
        let max_slots = self.config.max_slots;
        let loss_rate = self.config.network.loss_rate;
        let num_nodes = self.graph.num_nodes();
        let ce_cfg = self.config.ce.as_ref().unwrap().clone();

        // println!(
        //     "Starting simulation: CE mode, {} nodes, {} proposals, diameter={}",
        //     num_nodes,
        //     num_proposals,
        //     self.graph.diameter(),
        // );

        let mut next_snapshot = 0;

        for proposal_id in 0..num_proposals {
            if self.current_slot >= max_slots {
                for remaining in proposal_id..num_proposals {
                    self.metrics.record_proposal(
                        remaining,
                        self.current_slot,
                        self.current_slot,
                        crate::protocol::ProposalOutcome::TimedOut,
                    );
                }
                break;
            }

            let initiator = proposal_id % num_nodes;
            let round_start = self.current_slot;

            let init_payload = protocol.init_proposal(initiator, num_nodes);
            for node in self.nodes.iter_mut() {
                node.goal_reached = false;
                node.payload = protocol.init_node_payload(node.id);
            }
            self.nodes[initiator].payload = init_payload;

            let (end_slot, _goal_reached) = ce::run_ce_round(
                round_start,
                initiator,
                &mut self.nodes,
                &self.graph,
                &ce_cfg,
                protocol,
                &mut self.rng,
                loss_rate,
                &mut self.metrics,
                &mut next_snapshot,
                snapshot_interval,
            );
            self.current_slot = end_slot;

            let payloads: Vec<Vec<u8>> = self.nodes.iter().map(|n| n.payload.clone()).collect();
            let outcome = protocol.proposal_outcome(&payloads);
            self.metrics
                .record_proposal(proposal_id, round_start, self.current_slot, outcome);

            // Snapshot progress: completed proposals so far (0-based id → count).
            // Only advance on terminal outcomes; leave count unchanged on TimedOut.
            if matches!(
                outcome,
                crate::protocol::ProposalOutcome::Committed
                    | crate::protocol::ProposalOutcome::Aborted
            ) {
                let done = proposal_id + 1;
                for node in self.nodes.iter_mut() {
                    node.progress_count = done;
                }
            }

            // No snapshot here. `current_slot` is the first FREE slot: the round that
            // just returned never ticked it, and the NEXT round starts there, calls
            // reset_round() and ticks it — emitting it from its own loop with the state
            // the energy counters charged. Emitting it here recorded the pre-reset state
            // instead, and after the final proposal it produced a row for a slot that was
            // never ticked at all (plus a duplicate of it, from the trailing snapshot
            // below: 457 rows for 456 distinct slots).
        }

        // ponytail: every ticked slot is now emitted exactly once, by the round loop
        // that ticked it. Snapshot rows and tick counters agree by construction; the
        // amortised-energy integral over rows equals listen+flood exactly.
        self.metrics.print_summary(&self.nodes, self.config.quiet);
    }
}
