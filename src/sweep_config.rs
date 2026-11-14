use serde::Deserialize;
use std::fs;
use std::path::Path;

#[derive(Debug, Deserialize)]
pub struct SweepConfig {
    pub output_dir: String,
    pub seeds: Vec<u64>,
    pub num_nodes: Vec<usize>,
    pub topologies: Vec<String>,
    pub loss_rates: Vec<f64>,
    pub flood_repeats: Vec<u32>,
    pub listen_timeout: u64,
    pub max_round_slots: u64,
    pub ci_protocols: Vec<String>,
    pub ce_protocols: Vec<String>,
    pub num_proposals: usize,
    pub snapshot_interval: u64,
    pub max_slots: u64,
    pub abort_probability: f64,
}

impl SweepConfig {
    pub fn from_file(path: &Path) -> Result<Self, Box<dyn std::error::Error>> {
        let text = fs::read_to_string(path)?;
        let cfg: SweepConfig = toml::from_str(&text)?;
        Ok(cfg)
    }
}
