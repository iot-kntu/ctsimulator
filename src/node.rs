//! Node state machine for the simulation.

use crate::event::NodeId;

/// Primary radio state of a node within a round.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NodeState {
    /// Listening for incoming packets.
    Listen,
    /// Actively flooding (transmitting).
    Flood,
    /// Done for this round; radio off.
    Sleep,
}

/// A simulated wireless node.
#[derive(Debug, Clone)]
pub struct Node {
    pub id: NodeId,
    pub state: NodeState,

    // ── CI bookkeeping ─────────────────────────────
    /// Remaining flood-repeat transmissions in this CI round.
    pub flood_remaining: u32,

    // ── CE bookkeeping ─────────────────────────────
    /// Slot at which the node last saw progress (new information).
    pub last_progress_slot: u64,

    // ── Protocol state (opaque) ────────────────────
    /// The node's current protocol-level payload (serialised).
    pub payload: Vec<u8>,
    /// Whether this node has reached the protocol's goal condition.
    pub goal_reached: bool,
    /// Number of protocol proposals/decisions finalized so far by this node.
    pub progress_count: usize,

    // ── Metrics accumulators ───────────────────────
    pub slots_listen: u64,
    pub slots_flood: u64,
    pub slots_sleep: u64,
}

impl Node {
    pub fn new(id: NodeId) -> Self {
        Self {
            id,
            state: NodeState::Listen,
            flood_remaining: 0,
            last_progress_slot: 0,
            payload: Vec::new(),
            goal_reached: false,
            progress_count: 0,
            slots_listen: 0,
            slots_flood: 0,
            slots_sleep: 0,
        }
    }

    /// Reset transient per-round state; keep accumulated metrics and protocol payload.
    pub fn reset_round(&mut self) {
        self.state = NodeState::Listen;
        self.flood_remaining = 0;
        self.last_progress_slot = 0;
    }

    /// Tick one slot: increment the state-duration counter for the current state.
    pub fn tick(&mut self) {
        match self.state {
            NodeState::Listen => self.slots_listen += 1,
            NodeState::Flood => self.slots_flood += 1,
            NodeState::Sleep => self.slots_sleep += 1,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_node_starts_listening() {
        let n = Node::new(0);
        assert_eq!(n.state, NodeState::Listen);
    }

    #[test]
    fn tick_increments_correct_counter() {
        let mut n = Node::new(0);
        n.tick();
        assert_eq!(n.slots_listen, 1);
        n.state = NodeState::Flood;
        n.tick();
        assert_eq!(n.slots_flood, 1);
        n.state = NodeState::Sleep;
        n.tick();
        assert_eq!(n.slots_sleep, 1);
    }
}
