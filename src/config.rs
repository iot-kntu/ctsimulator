//! Simulation configuration, deserializable from TOML.

use serde::Deserialize;
use std::path::Path;

/// Top-level simulation configuration.
#[derive(Debug, Clone, Deserialize)]
pub struct SimConfig {
    /// Random seed for reproducibility.
    pub seed: u64,
    /// PHY mode: "ci" or "ce".
    pub phy_mode: String,
    /// Network topology section.
    pub network: NetworkConfig,
    /// CI-specific parameters (optional, required when phy_mode = "ci").
    pub ci: Option<CiConfig>,
    /// CE-specific parameters (optional, required when phy_mode = "ce").
    pub ce: Option<CeConfig>,
    /// Protocol to run (e.g. "2pc", "paxos").
    pub protocol: String,
    /// Number of proposals (transactions) to simulate.
    #[serde(default = "default_num_proposals")]
    pub num_proposals: usize,
    /// Metrics snapshot interval (in slots).
    #[serde(default = "default_snapshot_interval")]
    pub snapshot_interval: u64,
    /// Maximum slots before the simulation is forcibly terminated.
    #[serde(default = "default_max_slots")]
    pub max_slots: u64,
    #[serde(default = "default_abort_prob")]
    pub abort_probability: f64,
    /// Disable all prints during simulation (useful for sweep).
    #[serde(default)]
    pub quiet: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct NetworkConfig {
    /// Number of nodes.
    pub num_nodes: usize,
    /// Topology type: "full_mesh", "line", "grid", "star", "random".
    pub topology: String,
    /// Probability that a transmission over a link is lost [0.0, 1.0).
    #[serde(default)]
    pub loss_rate: f64,
    /// Optional path to a custom adjacency-list graph file.
    pub graph_file: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CiConfig {
    /// Number of times a node re-floods after first reception.
    pub flood_repeats: u32,
    /// Total slots in one CI round. If None, auto-calculated as
    /// `max(3, (diameter + 2) * flood_repeats)` by `phy::ci::flood_identical_round`,
    /// which is authoritative for this value.
    pub round_slots: Option<u64>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CeConfig {
    /// Slots a node waits in Listen without progress before random re-flood.
    pub listen_timeout: u64,
    /// Maximum slots before declaring a CE round failed.
    pub max_round_slots: u64,
}

fn default_abort_prob() -> f64 {
    0.0
}

fn default_num_proposals() -> usize {
    100
}
fn default_snapshot_interval() -> u64 {
    50
}
fn default_max_slots() -> u64 {
    100_000
}

impl SimConfig {
    /// Load configuration from a TOML file.
    pub fn from_file(path: &Path) -> Result<Self, Box<dyn std::error::Error>> {
        let text = std::fs::read_to_string(path)?;
        let cfg: SimConfig = toml::from_str(&text)?;
        cfg.validate()?;
        Ok(cfg)
    }

    fn validate(&self) -> Result<(), String> {
        if self.network.num_nodes < 2 {
            return Err("network.num_nodes must be >= 2".into());
        }
        if !(0.0..1.0).contains(&self.network.loss_rate) {
            return Err("network.loss_rate must be in [0.0, 1.0)".into());
        }
        match self.phy_mode.as_str() {
            "ci" => {
                if self.ci.is_none() {
                    return Err("CI mode requires [ci] config section".into());
                }
            }
            "ce" => {
                if self.ce.is_none() {
                    return Err("CE mode requires [ce] config section".into());
                }
            }
            other => return Err(format!("Unknown phy_mode: {other}")),
        }
        Ok(())
    }
}
