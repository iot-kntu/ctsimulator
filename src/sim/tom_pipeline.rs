//! CI Pipeline TOM — implements [`CiProtocol`].

use crate::config::SimConfig;
use crate::metrics::MetricsCollector;
use crate::network::NetworkGraph;
use crate::protocol::tom::{deserialize_packet, serialize_packet, NodeTomState, TomPacket};
use crate::sim::pipeline::{run_ci_pipeline, CiPipelineCore, CiProtocol};
use std::collections::{HashMap, HashSet};

pub struct TomPipelineSim {
    pub metrics: MetricsCollector,
    core: Option<CiPipelineCore>,
    num_msgs: usize,
}

impl TomPipelineSim {
    pub fn new(config: SimConfig, graph: NetworkGraph) -> Self {
        let num_msgs = config.num_proposals;
        Self {
            metrics: MetricsCollector::new(),
            core: Some(CiPipelineCore::new(config, graph)),
            num_msgs,
        }
    }

    pub fn run(&mut self) -> (MetricsCollector, u64, u64, u64) {
        let core = self.core.take().expect("already run");
        let n = core.num_nodes();
        let proto = TomCi {
            states: vec![NodeTomState::new(); n],
            next_term: 0,
            msgs_published: 0,
            num_msgs: self.num_msgs,
            msg_starts: HashMap::new(),
            msg_delivered_global: HashSet::new(),
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

struct TomCi {
    states: Vec<NodeTomState>,
    next_term: u64,
    msgs_published: usize,
    num_msgs: usize,
    msg_starts: HashMap<u64, u64>,
    msg_delivered_global: HashSet<u64>,
    piggyback_term: Option<u64>,
    piggyback_data: Vec<u8>,
    stop: bool,
}

impl CiProtocol for TomCi {
    fn name(&self) -> &str {
        "TOM Pipeline"
    }

    fn on_start(&mut self, _core: &mut CiPipelineCore) {
        // Output from protocol logic is already silenced by config.quiet.
    }

    fn prepare_round(&mut self, core: &mut CiPipelineCore) {
        let initiator = core.initiator();

        let nack_term = self.states[initiator].detect_gap().unwrap_or(0);
        if nack_term > 0 {
            core.metrics.nacks_sent += 1;
            if !core.config.quiet {
                println!(
                    "[TOM Recovery] Slot {} | Node {} NACK missing term {}",
                    core.current_slot, initiator, nack_term
                );
            }
        }

        let (current_term, has_message, message_data) = if self.msgs_published < self.num_msgs {
            let t = self.next_term;
            let d = format!("msg{}", t).into_bytes();
            self.states[initiator].insert_message(t, d.clone());
            self.states[initiator].try_deliver_in_order();
            self.msg_starts.insert(t, core.current_slot);
            self.next_term += 1;
            self.msgs_published += 1;
            (t, true, d)
        } else {
            // Repair mode: re-flood earliest undelivered term (high-loss recovery).
            // Nodes that never saw a higher term never NACK — proactive retransmit needed.
            let need = self
                .states
                .iter()
                .map(|s| s.next_expected())
                .min()
                .unwrap_or(0);
            if (need as usize) < self.num_msgs {
                let data = self.states[initiator]
                    .get_message(need)
                    .cloned()
                    .or_else(|| {
                        self.states
                            .iter()
                            .find_map(|s| s.get_message(need).cloned())
                    });
                if let Some(d) = data {
                    self.states[initiator].insert_message(need, d.clone());
                    if !core.config.quiet {
                        println!(
                            "[TOM Recovery] Slot {} | Node {} retransmit term {}",
                            core.current_slot, initiator, need
                        );
                    }
                    (need, true, d)
                } else {
                    (self.next_term.saturating_sub(1), false, Vec::new())
                }
            } else {
                (self.next_term.saturating_sub(1), false, Vec::new())
            }
        };

        // Piggyback recovery for explicit NACKs (gap after higher term seen).
        let (pb_term, pb_data) = if let Some(pt) = self.piggyback_term.take() {
            let data = self.states[initiator].get_message(pt).cloned().or_else(|| {
                if !self.piggyback_data.is_empty() {
                    Some(std::mem::take(&mut self.piggyback_data))
                } else {
                    None
                }
            });
            if let Some(data) = data {
                self.states[initiator].insert_message(pt, data.clone());
                core.metrics.piggybacks_sent += 1;
                if !core.config.quiet {
                    println!(
                        "[TOM Recovery] Slot {} | Node {} piggyback term {}",
                        core.current_slot, initiator, pt
                    );
                }
                self.piggyback_data.clear();
                (Some(pt), data)
            } else {
                // Keep gap pending for a later initiator that holds the payload.
                self.piggyback_term = Some(pt);
                (None, Vec::new())
            }
        } else {
            self.piggyback_data.clear();
            (None, Vec::new())
        };

        let pkt = TomPacket {
            current_term,
            has_message,
            message_data,
            sender: initiator,
            nack_term,
            piggyback_term: pb_term,
            piggyback_data: pb_data,
        };
        core.nodes[initiator].payload = serialize_packet(&pkt);
    }

    fn process_round(&mut self, core: &mut CiPipelineCore) {
        let num_nodes = core.num_nodes();
        let num_msgs = self.num_msgs;
        // Do not clear pending piggyback here — prepare_round consumes it.
        // Collect NACK/piggyback requests for the *next* flood initiator.

        for i in 0..num_nodes {
            if !core.participated(i) {
                continue;
            }
            let Some(received) = deserialize_packet(&core.nodes[i].payload) else {
                continue;
            };

            if received.has_message {
                self.states[i].insert_message(received.current_term, received.message_data.clone());
            } else {
                match self.states[i].highest_seen {
                    Some(h) if h >= received.current_term => {}
                    _ => self.states[i].highest_seen = Some(received.current_term),
                }
            }

            if let Some(pt) = received.piggyback_term {
                if !received.piggyback_data.is_empty() {
                    self.states[i].insert_message(pt, received.piggyback_data.clone());
                }
            }

            let delivered_now = self.states[i].try_deliver_in_order();
            if delivered_now > 0 {
                let start_idx = self.states[i].delivered.len() - delivered_now;
                for (term, _) in &self.states[i].delivered[start_idx..] {
                    if self.msg_delivered_global.insert(*term) {
                        let start = self.msg_starts.get(term).copied().unwrap_or(0);
                        core.metrics.record_proposal(
                            *term as usize,
                            start,
                            core.current_slot,
                            crate::protocol::ProposalOutcome::Committed,
                        );
                    }
                }
            }

            // If this flood carried a NACK, any node that has the data can supply piggyback.
            if received.nack_term > 0 {
                if let Some(data) = self.states[i].get_message(received.nack_term).cloned() {
                    self.piggyback_term = Some(received.nack_term);
                    self.piggyback_data = data;
                } else if self.piggyback_term.is_none() {
                    // Remember the gap even if this node lacks data (next holders may help).
                    self.piggyback_term = Some(received.nack_term);
                }
            }
        }

        // Unified progress: how many messages EVERY node has delivered in-order.
        let min_done = self
            .states
            .iter()
            .map(|s| s.delivered.len())
            .min()
            .unwrap_or(0);
        for i in 0..num_nodes {
            core.nodes[i].goal_reached = self.states[i].delivered.len() >= num_msgs;
            core.nodes[i].progress_count = min_done;
        }

        let all_delivered = self.states.iter().all(|s| s.delivered.len() >= num_msgs);
        if all_delivered && self.msgs_published >= num_msgs {
            if !core.config.quiet {
                println!(
                    "All nodes delivered {} messages in-order at round {}",
                    num_msgs, core.round_num
                );
            }
            self.verify_total_order(core.config.quiet);
            self.stop = true;
            return;
        }

        if self.msg_delivered_global.len() >= num_msgs {
            let max_drain = core.graph.diameter() * 10 + num_msgs * 2;
            if core.round_num as usize > num_msgs + max_drain {
                if !core.config.quiet {
                    println!(
                        "Graceful TOM termination at round {} (global delivery done).",
                        core.round_num
                    );
                }
                self.verify_total_order(core.config.quiet);
                self.stop = true;
                return;
            }
        }

        // Allow long repair under high loss (retransmit rounds after publish).
        if core.round_num as usize > num_msgs * 20 + 500 {
            if !core.config.quiet {
                println!("Timeout: TOM repair exceeded budget.");
            }
            self.stop = true;
        }
    }

    fn should_stop(&self, _core: &CiPipelineCore) -> bool {
        self.stop
    }
}

impl TomCi {
    fn verify_total_order(&self, quiet: bool) {
        let num_msgs = self.num_msgs;
        let mut ref_seq: Option<Vec<u64>> = None;
        let mut ok = true;
        for (i, st) in self.states.iter().enumerate() {
            let seq: Vec<u64> = st.delivered.iter().map(|(t, _)| *t).collect();
            for (k, t) in seq.iter().enumerate() {
                if *t != k as u64 {
                    if !quiet {
                        println!(
                            "[TOM ORDER FAIL] Node {}: gap/disorder at index {} got term {}",
                            i, k, t
                        );
                    }
                    ok = false;
                    break;
                }
            }
            if let Some(ref r) = ref_seq {
                if seq.len() >= num_msgs && r.len() >= num_msgs && seq[..num_msgs] != r[..num_msgs]
                {
                    if !quiet {
                        println!(
                            "[TOM ORDER FAIL] Node {} sequence differs from reference",
                            i
                        );
                    }
                    ok = false;
                }
            } else if seq.len() >= num_msgs {
                ref_seq = Some(seq);
            }
        }
        if ok && !quiet {
            println!(
                "TOM total-order check: OK ({} nodes, {} messages)",
                self.states.len(),
                num_msgs
            );
        }
    }
}
