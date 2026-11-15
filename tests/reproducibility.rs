//! Reproducibility contract: a fixed seed must reproduce a run exactly.
//!
//! Every reported result is re-run from source, so a run that is not
//! bit-reproducible would silently invalidate those numbers. The test also doubles as a
//! smoke test for all six protocol/PHY combinations shipped in `examples/`.

use ctsim::config::SimConfig;
use ctsim::run::run_experiment;
use std::path::Path;

const EXAMPLES: [&str; 6] = [
    "examples/paxos_ci.toml",
    "examples/paxos_ce.toml",
    "examples/2pc_ci.toml",
    "examples/2pc_ce.toml",
    "examples/tom_ci.toml",
    "examples/tom_ce.toml",
];

/// Load an example config, shrunk so the whole suite stays fast in debug builds.
fn load(path: &str) -> SimConfig {
    let mut cfg = SimConfig::from_file(Path::new(path)).expect("example config must parse");
    cfg.num_proposals = 10;
    cfg.quiet = true;
    cfg
}

/// Everything a figure script could read back, flattened into one comparable string.
fn fingerprint(cfg: SimConfig) -> String {
    let r = run_experiment(cfg);
    let s = &r.summary;
    let mut out = format!(
        "{} {} n={} d={} e={} committed={} aborted={} timed_out={} \
         listen={} flood={} sleep={} nacks={} piggybacks={} ce_timeouts={} end={}\n",
        s.protocol,
        s.phy_mode,
        s.num_nodes,
        s.diameter,
        s.edge_count,
        s.committed,
        s.aborted,
        s.timed_out,
        s.total_listen,
        s.total_flood,
        s.total_sleep,
        s.nacks_sent,
        s.piggybacks_sent,
        s.ce_timeouts,
        s.end_slot,
    );
    for p in &r.metrics.proposals {
        out.push_str(&format!("{p:?}\n"));
    }
    for snap in &r.metrics.snapshots {
        out.push_str(&format!("{snap:?}\n"));
    }
    out
}

#[test]
fn same_seed_reproduces_every_example_exactly() {
    for path in EXAMPLES {
        let first = fingerprint(load(path));
        let second = fingerprint(load(path));
        assert_eq!(
            first, second,
            "{path}: identical seed produced different results — the run is not reproducible"
        );
    }
}

#[test]
fn every_example_makes_progress() {
    for path in EXAMPLES {
        let cfg = load(path);
        let expected = cfg.num_proposals;
        let r = run_experiment(cfg);
        let s = &r.summary;
        assert_eq!(
            s.committed + s.aborted + s.timed_out,
            expected,
            "{path}: every proposal must reach a terminal outcome"
        );
        assert!(
            s.committed > 0,
            "{path}: no proposal committed — {} aborted, {} timed out",
            s.aborted,
            s.timed_out
        );
        assert!(
            !r.metrics.snapshots.is_empty(),
            "{path}: no per-slot snapshots were recorded"
        );
    }
}

#[test]
fn a_different_seed_changes_the_run_under_loss() {
    // Guards against a seed that is accepted but never actually threaded through
    // the RNG (which would make every "seed" produce the same trace).
    let mut a = load("examples/paxos_ci.toml");
    assert!(
        a.network.loss_rate > 0.0,
        "this test needs a lossy example config"
    );
    let mut b = a.clone();
    a.seed = 1;
    b.seed = 2;
    assert_ne!(fingerprint(a), fingerprint(b));
}
