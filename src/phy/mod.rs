//! PHY mode abstraction — CI (Constructive Interference) and CE (Capture Effect).

pub mod ce;
pub mod ci;

/// Selector for the physical-layer mode.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PhyMode {
    /// Constructive Interference — deterministic flood rounds.
    CI,
    /// Capture Effect — goal-based rounds with merge + timeout recovery.
    CE,
}

impl std::str::FromStr for PhyMode {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.to_lowercase().as_str() {
            "ci" => Ok(PhyMode::CI),
            "ce" => Ok(PhyMode::CE),
            other => Err(format!("Unknown PHY mode: {other}")),
        }
    }
}
