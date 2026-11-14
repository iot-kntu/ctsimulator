//! CTSim CLI entry point.

use ctsim::config::SimConfig;
use ctsim::run::{build_graph, run_experiment_on_graph};
use rand::SeedableRng;
use std::path::Path;

fn export_pair(metrics: &ctsim::metrics::MetricsCollector, results: &str, snapshots: &str) {
    std::fs::create_dir_all("results").unwrap_or_default();
    if let Err(e) = metrics.export_csv(Path::new(results)) {
        eprintln!("Failed to export {results}: {e}");
    }
    if let Err(e) = metrics.export_snapshots_csv(Path::new(snapshots)) {
        eprintln!("Failed to export {snapshots}: {e}");
    }
    println!("Exported: {results}, {snapshots}");
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let config_path = args
        .get(1)
        .map(|s| s.as_str())
        .unwrap_or("examples/paxos_ci.toml");

    if config_path.contains("sweep") {
        let sweep_cfg = ctsim::sweep_config::SweepConfig::from_file(Path::new(config_path))
            .unwrap_or_else(|e| panic!("Sweep config error: {e}"));
        ctsim::sweep_runner::run_sweep(sweep_cfg);
        return;
    }

    let config = SimConfig::from_file(Path::new(config_path))
        .unwrap_or_else(|e| panic!("Config error: {e}"));

    let mut rng = rand_chacha::ChaCha8Rng::seed_from_u64(config.seed);
    let graph = build_graph(&config.network, &mut rng);

    // Topology visuals (always; deterministic for fixed seed on random topologies).
    std::fs::create_dir_all("results").unwrap_or_default();
    match graph.export_visuals(Path::new("results"), "topology") {
        Ok((dot, svg)) => {
            println!(
                "Topology: n={} edges={} diameter={} → {}, {}",
                graph.num_nodes(),
                graph.edge_count(),
                graph.diameter(),
                dot.display(),
                svg.display()
            );
        }
        Err(e) => eprintln!("Failed to export topology visuals: {e}"),
    }

    let protocol = config.protocol.as_str();

    let (results_file, snapshots_file) = match protocol {
        "paxos_pipeline" => ("results/results_paxos.csv", "results/snapshots_paxos.csv"),
        "2pc_pipeline" => ("results/results_2pc.csv", "results/snapshots_2pc.csv"),
        "tom_pipeline" => ("results/results_tom.csv", "results/snapshots_tom.csv"),
        "paxos_ce" => ("results/results_paxos_ce.csv", "results/snapshots_paxos_ce.csv"),
        "2pc_ce" => ("results/results_2pc_ce.csv", "results/snapshots_2pc_ce.csv"),
        "tom_ce" => ("results/results_tom_ce.csv", "results/snapshots_tom_ce.csv"),
        "pure_flood" => {
            let mut sim = ctsim::sim::pure_flood::PureFloodSim::new(config, graph);
            sim.run();
            return;
        }
        other => panic!(
            "Unknown protocol: {other}. Available: paxos_pipeline, 2pc_pipeline, tom_pipeline, paxos_ce, 2pc_ce, tom_ce, pure_flood"
        ),
    };

    let run_result = run_experiment_on_graph(config, graph);
    export_pair(&run_result.metrics, results_file, snapshots_file);
}
