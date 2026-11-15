//! Shared CI pipeline infrastructure.
//!
//! - [`CiPipelineCore`]: nodes, metrics, RNG, slot clock shared by all CI protocols.
//! - [`CiProtocol`]: middleware hooks (build flood payload / process after flood / stop).
//! - [`run_ci_pipeline`]: generic RR loop over any `CiProtocol`.

use crate::config::{CiConfig, SimConfig};
use crate::event::NodeId;
use crate::metrics::MetricsCollector;
use crate::network::NetworkGraph;
use crate::node::Node;
use crate::phy::ci;
use rand::SeedableRng;
use rand_chacha::ChaCha8Rng;

/// Shared runtime state for a CI RR pipeline.
pub struct CiPipelineCore {
    pub config: SimConfig,
    pub graph: NetworkGraph,
    pub nodes: Vec<Node>,
    pub metrics: MetricsCollector,
    pub rng: ChaCha8Rng,
    pub current_slot: u64,
    pub next_snapshot: u64,
    pub round_num: u64,
}

impl CiPipelineCore {
    pub fn new(config: SimConfig, graph: NetworkGraph) -> Self {
        let n = graph.num_nodes();
        let seed = config.seed;
        Self {
            config,
            graph,
            nodes: (0..n).map(Node::new).collect(),
            metrics: MetricsCollector::new(),
            rng: ChaCha8Rng::seed_from_u64(seed),
            current_slot: 0,
            next_snapshot: 0,
            round_num: 0,
        }
    }

    pub fn num_nodes(&self) -> usize {
        self.graph.num_nodes()
    }

    pub fn ci_cfg(&self) -> &CiConfig {
        self.config.ci.as_ref().expect("CI config required")
    }

    pub fn loss_rate(&self) -> f64 {
        self.config.network.loss_rate
    }

    pub fn snapshot_interval(&self) -> u64 {
        self.config.snapshot_interval
    }

    /// Current RR initiator for `round_num`.
    pub fn initiator(&self) -> NodeId {
        (self.round_num % self.num_nodes() as u64) as NodeId
    }

    /// Run one identical-payload CI flood with current initiator payload.
    pub fn flood_identical(&mut self) {
        let initiator = self.initiator();
        let loss = self.loss_rate();
        let snap = self.snapshot_interval();
        // Pull ci config values we need without holding a borrow across mut self.
        let flood_repeats = self.ci_cfg().flood_repeats;
        let round_slots = self.ci_cfg().round_slots;
        let ci_cfg = CiConfig {
            flood_repeats,
            round_slots,
        };

        self.current_slot = ci::flood_identical_round(
            self.current_slot,
            initiator,
            &mut self.nodes,
            &self.graph,
            &ci_cfg,
            &mut self.rng,
            loss,
            &mut self.metrics,
            &mut self.next_snapshot,
            snap,
        );
    }

    /// True if node finished the flood (participated) or is the initiator.
    pub fn participated(&self, id: NodeId) -> bool {
        id == self.initiator() || self.nodes[id].state == crate::node::NodeState::Sleep
    }
}

/// Middleware hooks for a CI identical-flood pipeline protocol.
///
/// PHY is always [`ci::flood_identical_round`]; this trait only owns
/// packet construction and post-flood state updates.
pub trait CiProtocol {
    /// Human-readable name for logs.
    fn name(&self) -> &str;

    /// Optional banner at start of `run_ci_pipeline`.
    fn on_start(&mut self, core: &mut CiPipelineCore) {
        println!(
            "Starting {} (CI pipeline): {} nodes, diameter={}",
            self.name(),
            core.num_nodes(),
            core.graph.diameter()
        );
    }

    /// Phase 1: write `core.nodes[initiator].payload` (serialized flood bytes).
    fn prepare_round(&mut self, core: &mut CiPipelineCore);

    /// Phase 3: after identical flood; process participants' payloads / local state.
    fn process_round(&mut self, core: &mut CiPipelineCore);

    /// Stop condition (checked after each round).
    fn should_stop(&self, core: &CiPipelineCore) -> bool;

    /// Finalization (metrics summary, protocol-specific verification, etc.).
    ///
    /// Invoked exactly once by [`run_ci_pipeline`]. The default implementation
    /// owns printing the metrics summary via [`core.metrics.print_summary`].
    /// Any protocol override that does not call default must either explicitly
    /// call `core.metrics.print_summary` or document why it omits it.
    fn on_finish(&mut self, core: &mut CiPipelineCore) {
        core.metrics.print_summary(&core.nodes, core.config.quiet);
    }
}

/// Generic RR pipeline: prepare → flood_identical → process until stop / max_slots.
pub fn run_ci_pipeline<P: CiProtocol>(mut protocol: P, mut core: CiPipelineCore) -> CiPipelineCore {
    protocol.on_start(&mut core);

    loop {
        if core.current_slot >= core.config.max_slots {
            if !core.config.quiet {
                println!("Timeout at slot {}", core.current_slot);
            }
            break;
        }

        protocol.prepare_round(&mut core);
        core.flood_identical();
        protocol.process_round(&mut core);

        if protocol.should_stop(&core) {
            break;
        }

        core.round_num += 1;

        if core.round_num > 1_000_000 {
            if !core.config.quiet {
                println!("Timeout: excessive pipeline rounds");
            }
            break;
        }
    }

    // No trailing snapshot: `current_slot` is the first FREE slot — nothing was ever
    // ticked for it, so a row here would be an extra sample the energy counters never
    // charged (and `take_snapshot` at the same index the loop already emitted was
    // double-counting one slot in every run). Every ticked slot is emitted, once, by
    // the round loop above.
    protocol.on_finish(&mut core);
    core
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::NetworkConfig;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::Arc;

    struct FinalizationCountingProtocol {
        finish_count: Arc<AtomicUsize>,
    }

    impl CiProtocol for FinalizationCountingProtocol {
        fn name(&self) -> &str {
            "test_counting_proto"
        }
        fn prepare_round(&mut self, _core: &mut CiPipelineCore) {}
        fn process_round(&mut self, _core: &mut CiPipelineCore) {}
        fn should_stop(&self, _core: &CiPipelineCore) -> bool {
            true
        }
        fn on_finish(&mut self, core: &mut CiPipelineCore) {
            self.finish_count.fetch_add(1, Ordering::SeqCst);
            core.metrics.print_summary(&core.nodes, core.config.quiet);
        }
    }

    #[test]
    fn finalization_invoked_exactly_once() {
        for quiet in [true, false] {
            let config = SimConfig {
                seed: 42,
                phy_mode: "ci".to_string(),
                protocol: "test_counting_proto".to_string(),
                network: NetworkConfig {
                    num_nodes: 3,
                    topology: "full_mesh".to_string(),
                    loss_rate: 0.0,
                    graph_file: None,
                },
                ci: Some(CiConfig {
                    flood_repeats: 1,
                    round_slots: None,
                }),
                ce: None,
                num_proposals: 1,
                snapshot_interval: 100,
                max_slots: 1000,
                abort_probability: 0.0,
                quiet,
            };
            let graph = NetworkGraph::full_mesh(3);
            let core = CiPipelineCore::new(config, graph);
            let finish_count = Arc::new(AtomicUsize::new(0));
            let proto = FinalizationCountingProtocol {
                finish_count: Arc::clone(&finish_count),
            };

            let _ = run_ci_pipeline(proto, core);
            assert_eq!(
                finish_count.load(Ordering::SeqCst),
                1,
                "on_finish must be called exactly once (quiet={quiet})"
            );
        }
    }
}
