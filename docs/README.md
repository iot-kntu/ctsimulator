# CTSim documentation

These pages are the packet-level specification of the simulator: what happens in
a slot, what each protocol puts on the wire, and how every reported metric is
computed. They exist so that a result can be traced back to the mechanism that
produced it — and so a protocol can be reimplemented without reading the Rust.
They are kept aligned with the code; where the two disagree, the code wins and
the page is a bug.

## Core

- **[How the simulator works](simulator.md)** — time model, CI/CE PHY, nodes, network, metrics, config, architecture.

## Protocols

| Protocol | CI (pipeline) | CE (Chaos) |
|----------|---------------|------------|
| **Paxos** | [paxos_ci.md](protocols/paxos_ci.md) (`paxos_pipeline`) | [paxos_ce.md](protocols/paxos_ce.md) (`paxos_ce`) |
| **2PC** | [2pc_ci.md](protocols/2pc_ci.md) (`2pc_pipeline`) | [2pc_ce.md](protocols/2pc_ce.md) (`2pc_ce`) |
| **TOM** | [tom_ci.md](protocols/tom_ci.md) (`tom_pipeline`) | [tom_ce.md](protocols/tom_ce.md) (`tom_ce`) |

## Quick run

```bash
cargo run --release -- examples/paxos_ci.toml   # paxos_pipeline (CI)
cargo run --release -- examples/paxos_ce.toml   # paxos_ce      (CE)
cargo run --release -- examples/2pc_ci.toml     # 2pc_pipeline  (CI)
cargo run --release -- examples/2pc_ce.toml     # 2pc_ce        (CE)
cargo run --release -- examples/tom_ci.toml     # tom_pipeline  (CI)
cargo run --release -- examples/tom_ce.toml     # tom_ce        (CE)
```
