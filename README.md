# CTSim

**A slot-based simulator for consensus over concurrent transmissions in low-power wireless networks.**

CTSim is the research simulator behind a paper on pipelined consensus over
constructive interference in low-power wireless networks (reference to be added
on publication). It models a multi-hop, time-slotted wireless network and runs
distributed-agreement protocols on top of two physical-layer abstractions:

- **CI — Constructive Interference** (Glossy/BlueFlood-style): deterministic,
  bit-identical flood rounds with predictable, bounded duration. This is what
  makes the **pipelined** execution possible.
- **CE — Capture Effect** (Chaos-style): goal-based rounds with in-network
  merge and listen-timeout recovery. This is the sequential baseline.

Each of three consensus/coordination protocols is implemented on **both**
layers, so the comparison isolates the effect of the *communication
architecture* from that of the *protocol*:

| Protocol | Agreement condition | CI (pipelined) | CE (baseline) |
|----------|---------------------|----------------|---------------|
| **Paxos** | majority quorum | `paxos_pipeline` | `paxos_ce` |
| **2PC**   | unanimity           | `2pc_pipeline`   | `2pc_ce`   |
| **TOM**   | none (pure ordering)| `tom_pipeline`   | `tom_ce`   |

The simulator is deterministic for a fixed seed and records a three-state radio
schedule (Listen / Flood / Sleep) per node per slot, from which latency,
throughput, commit rate, and energy (awake slots) are derived.

---

## Requirements

- **Rust** (stable, edition 2021, ≥ 1.74) — [`rustup`](https://rustup.rs/). Build with `cargo`.

## Build & test

```bash
cargo build --release
cargo test
```

`tests/reproducibility.rs` asserts the central property of the simulator: for a
fixed seed, every example config reproduces its run exactly — same outcomes,
same per-slot snapshots — and a different seed does not.

## Quick start

Run a single experiment from a TOML config:

```bash
cargo run --release -- examples/paxos_ci.toml
```

This prints a summary and writes CSVs to `results/`:

```
results/results_<protocol>.csv     # one row per proposal: start, end, latency, outcome
results/snapshots_<protocol>.csv   # per-slot network state (radio states + decisions)
results/topology.dot, topology.svg # the generated graph
```

Try the other five combinations in [`examples/`](examples/):

```bash
cargo run --release -- examples/2pc_ci.toml
cargo run --release -- examples/tom_ce.toml
# ... etc.
```

## Configuration

A single-run config is a TOML file (see [`examples/`](examples/) and
[`docs/simulator.md`](docs/simulator.md) for the full reference):

```toml
seed = 42                 # deterministic RNG seed
phy_mode = "ci"           # "ci" or "ce"
protocol = "paxos_pipeline"
num_proposals = 100
snapshot_interval = 1
max_slots = 100000

[network]
num_nodes = 27
topology = "random"       # full_mesh | partial_mesh | random | scale_free
                          # line | grid | ring | star | tree
loss_rate = 0.05          # i.i.d. per-link loss probability, [0.0, 1.0)

[ci]                      # required when phy_mode = "ci"
flood_repeats = 1         # re-transmissions per node per flood round (R)

[ce]                      # required when phy_mode = "ce"
listen_timeout = 5
max_round_slots = 300
```

## Parameter sweeps

The same binary runs a Cartesian-product sweep when given a sweep config
(any file whose path contains `sweep`). Results are collected into a single
summary CSV under `results/`:

```bash
cargo run --release -- sweeps/scalability.toml   # N × loss, 15 seeds
cargo run --release -- sweeps/topology.toml      # 5 topologies, 15 seeds
```

## Repository layout

```
src/                 Rust simulator
  phy/               CI and CE physical-layer engines
  protocol/          Paxos, 2PC, TOM  (CE variants + CI middleware)
  sim/               CI pipeline core + per-protocol pipeline drivers
  network.rs         topology generators + graph metrics
  metrics.rs         per-proposal / per-slot metrics + CSV export
  sweep_runner.rs    parallel parameter sweeps
tests/               reproducibility and model checks
examples/            ready-to-run single-experiment configs (6 combinations)
sweeps/              sweep definitions (scalability, topology)
profiles/graphs/     frozen adjacency files for the seeded 27-node random graphs
docs/                simulator, protocol and wire-format documentation
```

## Documentation

[`docs/`](docs/) is the packet-level specification of what the simulator
actually does — written so a reader can trace a result back to the
mechanism that produced it, or reimplement a protocol without reading the Rust.
[`docs/simulator.md`](docs/simulator.md) covers the time model (the exact
tick → act → transition order within a slot), both PHY engines, the node and
network models, and how each metric is defined; the six pages under
[`docs/protocols/`](docs/protocols/) give the packet format, local state,
round loop, and recovery rules for each protocol/PHY pairing.
[`docs/wire-format.md`](docs/wire-format.md) specifies the packet lengths used
when slot counts are converted to wall-clock time.

## Citation

If you use CTSim in your research, please cite it — see
[`CITATION.cff`](CITATION.cff). Author and paper details are added on
publication.

## License

Released under the GNU General Public License v3.0 — see [`LICENSE`](LICENSE).
