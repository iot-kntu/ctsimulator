//! Protocol trait — the pluggable application layer.
//!
//! Protocols define how payloads are created, merged, and when consensus
//! (the "goal") is reached. The simulator calls into these trait methods
//! without knowing the protocol internals.

pub mod paxos;
pub mod paxos_ce;
pub mod tom;
pub mod tom_ce;
pub mod two_pc_ce;
pub mod two_pc_pipeline;
pub mod vote_window;

use crate::event::NodeId;

/// Outcome of merging a received payload into a node's state.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MergeResult {
    /// New information was incorporated — the node should re-flood.
    NewInfo,
    /// The received payload was redundant — no state change.
    Redundant,
}

/// Outcome of a single proposal after the simulation.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProposalOutcome {
    Committed,
    Aborted,
    TimedOut,
}

/// A pluggable consensus / coordination protocol.
///
/// Implementors manage their own internal state per proposal. The simulator
/// treats payloads as opaque `Vec<u8>` and delegates all semantic logic here.
pub trait Protocol {
    /// Human-readable name for logging.
    fn name(&self) -> &str;

    /// Reset protocol state for a new proposal. Returns the initial payload
    /// that the initiator should flood.
    fn init_proposal(&mut self, initiator: NodeId, num_nodes: usize) -> Vec<u8>;

    /// Create the initial (empty) payload for a non-initiator node.
    fn init_node_payload(&self, node_id: NodeId) -> Vec<u8>;

    /// Merge `received` payload into `local` payload (CE-style).
    /// Returns whether new information was incorporated.
    fn merge(&self, local: &mut Vec<u8>, received: &[u8], node_id: NodeId) -> MergeResult;

    /// Check if a single node's payload indicates it has reached the goal.
    fn node_goal_reached(&self, payload: &[u8]) -> bool;

    /// Optional: Can this node safely go to Sleep (stop participating entirely) for the rest of the round?
    fn node_can_sleep(&self, _payload: &[u8]) -> bool {
        false
    }

    /// Check if the network-wide goal is met (e.g., all nodes committed).
    /// `payloads` is indexed by NodeId.
    fn network_goal_reached(&self, payloads: &[Vec<u8>]) -> bool;

    /// Determine the outcome of the proposal from final node payloads.
    fn proposal_outcome(&self, payloads: &[Vec<u8>]) -> ProposalOutcome;
}
