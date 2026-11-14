//! Pure-flood calibration harness.
//!
//! Runs repeated one-shot, single-source CI floods with no consensus middleware and
//! reports the fraction of receivers that actually got the flooded payload. This exists
//! only to calibrate `network.loss_rate` against a published flooding result. No
//! published protocol path calls this module, so it cannot perturb any committed sweep.
//!
//! Coverage accounting is provenance-safe within a flood: every node payload is cleared
//! before the flood and the initiator payload is unique per flood, so a node can only
//! match by having received bytes originating in that flood. The initiator itself is
//! excluded from both numerator and denominator, because a coverage figure that counts
//! the source has an artificial floor of `1/N`.

use crate::config::SimConfig;
use crate::network::NetworkGraph;
use crate::sim::pipeline::CiPipelineCore;

/// Outcome of one pure-flood calibration run.
#[derive(Debug, Clone)]
pub struct PureFloodReport {
    pub num_nodes: usize,
    pub loss_rate: f64,
    pub seed: u64,
    pub floods: usize,
    pub receivers_covered: usize,
    pub receiver_opportunities: usize,
    pub coverage: f64,
    pub rx_attempts: u64,
    pub rx_success: u64,
    pub slots: u64,
}

/// Driver for the pure-flood harness.
pub struct PureFloodSim {
    core: Option<CiPipelineCore>,
    num_floods: usize,
    quiet: bool,
    seed: u64,
}

impl PureFloodSim {
    pub fn new(config: SimConfig, graph: NetworkGraph) -> Self {
        let num_floods = config.num_proposals;
        let quiet = config.quiet;
        let seed = config.seed;
        Self {
            core: Some(CiPipelineCore::new(config, graph)),
            num_floods,
            quiet,
            seed,
        }
    }

    /// Run `num_proposals` independent one-shot floods, rotating the initiator.
    pub fn run(&mut self) -> PureFloodReport {
        let mut core = self.core.take().expect("PureFloodSim::run called twice");
        let n = core.num_nodes();
        assert!(n >= 2, "pure_flood requires at least two nodes");
        let max_slots = core.config.max_slots;
        let loss_rate = core.loss_rate();

        let mut receivers_covered: usize = 0;
        let mut receiver_opportunities: usize = 0;
        let mut floods_done: usize = 0;

        for f in 0..self.num_floods {
            if core.current_slot >= max_slots {
                break;
            }

            // Unique payload per flood, so a match cannot come from an earlier flood.
            let payload = format!("pure-flood-{f}").into_bytes();

            // Clear every payload and reset per-round state, then seed the initiator.
            for node in core.nodes.iter_mut() {
                node.payload.clear();
                node.reset_round();
            }
            let initiator = core.initiator();
            core.nodes[initiator].payload = payload.clone();

            core.flood_identical();

            receivers_covered += core
                .nodes
                .iter()
                .filter(|nd| nd.id != initiator && nd.payload == payload)
                .count();
            receiver_opportunities += n - 1;
            floods_done += 1;
            core.round_num += 1;
        }

        let coverage = if receiver_opportunities == 0 {
            0.0
        } else {
            receivers_covered as f64 / receiver_opportunities as f64
        };

        let report = PureFloodReport {
            num_nodes: n,
            loss_rate,
            seed: self.seed,
            floods: floods_done,
            receivers_covered,
            receiver_opportunities,
            coverage,
            rx_attempts: core.metrics.rx_attempts,
            rx_success: core.metrics.rx_success,
            slots: core.current_slot,
        };

        if !self.quiet {
            println!(
                "PURE_FLOOD n={} loss={:.6} seed={} floods={} covered={} opportunities={} coverage={:.6} rx_attempts={} rx_success={} slots={}",
                report.num_nodes,
                report.loss_rate,
                report.seed,
                report.floods,
                report.receivers_covered,
                report.receiver_opportunities,
                report.coverage,
                report.rx_attempts,
                report.rx_success,
                report.slots
            );
        }

        report
    }
}
