//! Metrics collection and export.

use crate::event::Slot;
use crate::node::Node;
use crate::protocol::ProposalOutcome;
use std::io::Write;

/// A snapshot of network state at a point in time.
#[derive(Debug, Clone)]
pub struct Snapshot {
    pub slot: Slot,
    /// Network-wide completed decisions under the unified bar:
    /// "how many proposals have been finalized on ALL nodes" (all-N informed).
    pub nodes_goal_reached: usize,
    pub nodes_listening: usize,
    pub nodes_flooding: usize,
    pub nodes_sleeping: usize,
}

/// Per-proposal result record.
#[derive(Debug, Clone)]
pub struct ProposalRecord {
    pub proposal_id: usize,
    pub start_slot: Slot,
    pub end_slot: Slot,
    pub latency: u64,
    pub outcome: ProposalOutcome,
}

/// Collects all simulation metrics.
#[derive(Debug, Default, Clone)]
pub struct MetricsCollector {
    pub snapshots: Vec<Snapshot>,
    pub proposals: Vec<ProposalRecord>,
    pub nacks_sent: u64,
    pub piggybacks_sent: u64,
    pub ce_timeouts: u64,
    pub total_listen: u64,
    pub total_flood: u64,
    pub total_sleep: u64,

    // ── PDR instrumentation ──────────────────────────────────────
    //
    // WRITE-ONLY by construction: no engine reads these, no control flow branches
    // on them, and they consume no RNG draw. Every published sweep must therefore
    // stay byte-identical after this patch; that is a measured gate, not a claim.
    //
    // The two PHY engines count DIFFERENT denominators, so their link PDRs must
    // never be pooled:
    //   * CI (`phy/ci.rs`): one reception opportunity per (transmitter, Listen
    //     neighbour) pair per slot — every concurrent transmission is counted.
    //   * CE (`phy/ce.rs`): opportunities are enumerated per Listen target in
    //     capture order and the loop STOPS at the first surviving transmission,
    //     so the denominator is truncated by design (capture semantics).
    /// Reception opportunities offered to Listen nodes.
    pub rx_attempts: u64,
    /// Opportunities the loss draw did not drop.
    pub rx_success: u64,
    /// Completed CI flood rounds. Stays 0 on the CE path, which has no flood round.
    pub flood_rounds: u64,
    /// Sum of N over completed CI flood rounds (the coverage denominator).
    pub flood_nodes_total: u64,
    /// Sum over completed CI flood rounds of the number of nodes holding the
    /// round's flooded payload when the round ended.
    pub flood_nodes_covered: u64,
}

impl MetricsCollector {
    pub fn new() -> Self {
        Self::default()
    }

    /// Link-level packet delivery ratio: successful receptions over reception
    /// opportunities. `None` when no opportunity was recorded.
    ///
    /// See the field comments: the CI and CE denominators are not comparable.
    pub fn link_pdr(&self) -> Option<f64> {
        if self.rx_attempts == 0 {
            None
        } else {
            Some(self.rx_success as f64 / self.rx_attempts as f64)
        }
    }

    /// End-to-end flood coverage: fraction of node-rounds in which a node held the
    /// flooded payload when the CI round ended. `None` on the CE path.
    ///
    /// CAVEAT: coverage is measured by comparing each node's payload against the
    /// round payload, so a round whose payload is empty counts every
    /// empty-payload node as covered. That case does not arise in the pipelines
    /// (the initiator always floods a non-empty protocol packet), but a future
    /// caller that floods nothing would read 1.0 here.
    pub fn flood_coverage(&self) -> Option<f64> {
        if self.flood_nodes_total == 0 {
            None
        } else {
            Some(self.flood_nodes_covered as f64 / self.flood_nodes_total as f64)
        }
    }

    /// Take a snapshot from current node states.
    pub fn take_snapshot(&mut self, slot: Slot, nodes: &[Node]) {
        let mut s = Snapshot {
            slot,
            nodes_goal_reached: 0,
            nodes_listening: 0,
            nodes_flooding: 0,
            nodes_sleeping: 0,
        };
        for n in nodes {
            // progress_count is already the network-unified "how many decisions every
            // node has finalized" (min across nodes on CI; post-outcome count on CE).
            // Use max so a single snapshot value = that count (all nodes share the same
            // value when set correctly); min is safer if stragglers lag mid-update.
            s.nodes_goal_reached = s.nodes_goal_reached.max(n.progress_count);
            match n.state {
                crate::node::NodeState::Listen => s.nodes_listening += 1,
                crate::node::NodeState::Flood => s.nodes_flooding += 1,
                crate::node::NodeState::Sleep => s.nodes_sleeping += 1,
            }
        }
        self.snapshots.push(s);
    }

    /// Record a completed proposal.
    pub fn record_proposal(&mut self, id: usize, start: Slot, end: Slot, outcome: ProposalOutcome) {
        self.proposals.push(ProposalRecord {
            proposal_id: id,
            start_slot: start,
            end_slot: end,
            latency: end.saturating_sub(start),
            outcome,
        });
    }

    /// Print summary to stdout.
    pub fn print_summary(&self, nodes: &[crate::node::Node], quiet: bool) {
        if quiet {
            return;
        }
        let total = self.proposals.len();
        let committed = self
            .proposals
            .iter()
            .filter(|p| p.outcome == ProposalOutcome::Committed)
            .count();
        let aborted = self
            .proposals
            .iter()
            .filter(|p| p.outcome == ProposalOutcome::Aborted)
            .count();
        let timed_out = total - committed - aborted;

        let avg_latency = if committed > 0 {
            self.proposals
                .iter()
                .filter(|p| p.outcome == ProposalOutcome::Committed)
                .map(|p| p.latency)
                .sum::<u64>() as f64
                / committed as f64
        } else {
            0.0
        };

        let total_listen: u64 = nodes.iter().map(|n| n.slots_listen).sum();
        let total_flood: u64 = nodes.iter().map(|n| n.slots_flood).sum();
        let total_sleep: u64 = nodes.iter().map(|n| n.slots_sleep).sum();

        println!("═══ Simulation Results ═══");
        println!("Proposals: {total} total, {committed} committed, {aborted} aborted, {timed_out} timed out");
        println!("Avg latency (committed): {avg_latency:.1} slots");
        println!("Energy profile: Listen={total_listen} Flood={total_flood} Sleep={total_sleep}");
        println!(
            "Recovery Events: NACKs={} Piggybacks={} CE_Timeouts={}",
            self.nacks_sent, self.piggybacks_sent, self.ce_timeouts
        );
        // stdout only — the sweeps run with `quiet` and export no PDR column, which
        // is what keeps their CSV output byte-identical.
        if let Some(pdr) = self.link_pdr() {
            println!(
                "Link PDR: {:.6} ({}/{} reception opportunities; CI and CE denominators differ)",
                pdr, self.rx_success, self.rx_attempts
            );
        }
        if let Some(coverage) = self.flood_coverage() {
            println!(
                "Flood coverage: {:.6} ({}/{} node-rounds over {} CI flood rounds)",
                coverage, self.flood_nodes_covered, self.flood_nodes_total, self.flood_rounds
            );
        }
    }

    /// Export proposal records to CSV.
    pub fn export_csv(&self, path: &std::path::Path) -> std::io::Result<()> {
        let mut f = std::fs::File::create(path)?;
        writeln!(f, "proposal_id,start_slot,end_slot,latency,outcome")?;
        for p in &self.proposals {
            let outcome_str = match p.outcome {
                ProposalOutcome::Committed => "committed",
                ProposalOutcome::Aborted => "aborted",
                ProposalOutcome::TimedOut => "timed_out",
            };
            writeln!(
                f,
                "{},{},{},{},{}",
                p.proposal_id, p.start_slot, p.end_slot, p.latency, outcome_str
            )?;
        }
        Ok(())
    }

    /// Export snapshots to CSV.
    pub fn export_snapshots_csv(&self, path: &std::path::Path) -> std::io::Result<()> {
        let mut f = std::fs::File::create(path)?;
        writeln!(
            f,
            "slot,progress_count,nodes_listening,nodes_flooding,nodes_sleeping"
        )?;
        for s in &self.snapshots {
            writeln!(
                f,
                "{},{},{},{},{}",
                s.slot, s.nodes_goal_reached, s.nodes_listening, s.nodes_flooding, s.nodes_sleeping
            )?;
        }
        Ok(())
    }
}
