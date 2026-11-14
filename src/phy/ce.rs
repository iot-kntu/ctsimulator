//! CE (Capture Effect) engine.
//!
//! Rounds are goal-based: they continue until the protocol's network-wide goal
//! is met or `max_round_slots` is exceeded. Nodes merge novel payloads and
//! re-flood; stalled nodes use a listen-timeout to randomly re-flood.
//!
//! Slot rule: state fixed at slot start → action from that state → transitions for next slot.

use crate::config::CeConfig;
use crate::event::{NodeId, Slot};
use crate::network::NetworkGraph;
use crate::node::{Node, NodeState};
use crate::protocol::{MergeResult, Protocol};
use rand::Rng;
use rand_chacha::ChaCha8Rng;

/// Run one CE round. Returns (slot_after_round, goal_reached).
// ponytail: plain argument list rather than a params struct — these are the two
// internal call sites of the CE round; bundling them would only move the noise.
#[allow(clippy::too_many_arguments)]
pub fn run_ce_round(
    round_start: Slot,
    initiator: NodeId,
    nodes: &mut [Node],
    graph: &NetworkGraph,
    ce_cfg: &CeConfig,
    protocol: &dyn Protocol,
    rng: &mut ChaCha8Rng,
    loss_rate: f64,
    metrics: &mut crate::metrics::MetricsCollector,
    next_snapshot: &mut u64,
    snapshot_interval: u64,
) -> (Slot, bool) {
    let deadline = round_start + ce_cfg.max_round_slots;

    for node in nodes.iter_mut() {
        node.reset_round();
        node.last_progress_slot = round_start;
    }

    nodes[initiator].state = NodeState::Flood;

    let mut slot = round_start;
    while slot < deadline {
        // 1. Tick + snapshot from CURRENT state (start of slot).
        for node in nodes.iter_mut() {
            node.tick();
        }

        while *next_snapshot <= slot {
            metrics.take_snapshot(*next_snapshot, nodes);
            *next_snapshot += snapshot_interval;
        }

        // Goal from state established by previous transitions.
        // Return `slot + 1` so the next CE round starts on a fresh slot index.
        // (Returning `slot` would make the next round re-tick the same absolute
        // slot number and double-count energy: L+F+S = N*(end_slot + num_proposals).)
        let payloads: Vec<Vec<u8>> = nodes.iter().map(|n| n.payload.clone()).collect();
        if protocol.network_goal_reached(&payloads) {
            return (slot + 1, true);
        }

        // 2. Action phase — plan next-slot states (default: Listen, unless sleeping).
        let mut next_states = vec![NodeState::Listen; nodes.len()];
        for i in 0..nodes.len() {
            if nodes[i].state == NodeState::Sleep {
                next_states[i] = NodeState::Sleep;
            }
        }
        let mut progress_updates = vec![false; nodes.len()];

        let mut transmissions: Vec<(NodeId, Vec<u8>)> = Vec::new();
        for node in nodes.iter() {
            if node.state == NodeState::Flood {
                transmissions.push((node.id, node.payload.clone()));
            }
        }

        // Listen-timeout recovery (for next slot).
        for i in 0..nodes.len() {
            if nodes[i].state == NodeState::Listen
                && (slot - nodes[i].last_progress_slot) >= ce_cfg.listen_timeout
                && rng.gen_bool(0.5)
            {
                next_states[i] = NodeState::Flood;
                progress_updates[i] = true;
                metrics.ce_timeouts += 1;
                // Avoid printing during sweep to keep progress bar clean
                // (quiet flag not easily passed to PHY without changing signature, so just omit or use macro if needed)
                // For now, removing the print since metrics tracks the count.
            }
        }

        // Capture: at most one successful RX per Listen target this slot.
        for target in 0..nodes.len() {
            if nodes[target].state != NodeState::Listen {
                continue;
            }
            if next_states[target] == NodeState::Flood {
                continue; // already recovery-flooding
            }

            let mut flooding_nbrs: Vec<NodeId> = transmissions
                .iter()
                .filter(|(src, _)| graph.neighbors(*src).contains(&target))
                .map(|(src, _)| *src)
                .collect();
            flooding_nbrs.sort_unstable();

            if flooding_nbrs.is_empty() {
                continue;
            }

            for i in (1..flooding_nbrs.len()).rev() {
                let j = rng.gen_range(0..=i);
                flooding_nbrs.swap(i, j);
            }

            for &src in &flooding_nbrs {
                // PDR instrumentation, write-only. NOTE the denominator:
                // this loop `break`s after the first transmission that survives the
                // loss draw, so `rx_attempts` counts only the transmissions actually
                // examined under capture — not every concurrent neighbour, which is
                // what `phy/ci.rs` counts. The two are different quantities.
                metrics.rx_attempts += 1;
                if loss_rate > 0.0 && rng.gen::<f64>() < loss_rate {
                    continue;
                }
                metrics.rx_success += 1;

                let src_payload = transmissions
                    .iter()
                    .find(|(s, _)| *s == src)
                    .unwrap()
                    .1
                    .clone();
                let result = protocol.merge(&mut nodes[target].payload, &src_payload, target);

                if result == MergeResult::NewInfo {
                    next_states[target] = NodeState::Flood;
                    progress_updates[target] = true;
                    // Goal flag only — progress_count is advanced once per completed
                    // proposal by the CE orchestrator (sim.rs), not on every NewInfo.
                    nodes[target].goal_reached = protocol.node_goal_reached(&nodes[target].payload);
                }
                break;
            }
        }

        // 3. Apply transitions for NEXT slot.
        for i in 0..nodes.len() {
            if protocol.node_can_sleep(&nodes[i].payload) {
                // Must flood the completing state at least once before sleeping,
                // otherwise peers never learn the full dissemination bitmap.
                if nodes[i].state == NodeState::Flood {
                    next_states[i] = NodeState::Sleep;
                } else {
                    next_states[i] = NodeState::Flood;
                }
            }
            nodes[i].state = next_states[i];
            if progress_updates[i] {
                nodes[i].last_progress_slot = slot;
            }
        }

        slot += 1;
    }

    (slot, false)
}
