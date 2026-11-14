//! Core time types for the slot-based simulator.
//!
//! The engine advances time by integer slots (not a classic event-queue DES).
//! `NodeId` / `Slot` are shared aliases used across PHY and orchestrators.

/// Unique node identifier.
pub type NodeId = usize;

/// Slot (tick) — the fundamental time unit.
pub type Slot = u64;
