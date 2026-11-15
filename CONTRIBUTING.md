# Contributing to CTSim

Thanks for your interest in CTSim. This is a research simulator released
alongside a paper; contributions that improve correctness, reproducibility, or
clarity are welcome.

## Development setup

```bash
cargo build --release
cargo test
```

## Before opening a pull request

CI runs exactly these four checks — run them locally first:

```bash
cargo fmt --all --check
cargo clippy --all-targets -- -D warnings
cargo test --all
cargo build --release
```

- Do **not** commit generated output (anything under `results/` or
  `sweep_results/`); it is ignored on purpose and reproducible from the configs.
- If you change simulator behavior, say which outputs it affects.
- Keep runs reproducible: never iterate a `HashSet` of neighbours where the
  order feeds the RNG (loss draws, capture-effect contention). Neighbour lists
  are returned sorted for this reason, and `tests/reproducibility.rs` will fail
  if a fixed seed stops reproducing a run.

## Reproducibility

The simulator is deterministic for a fixed seed. If you add an experiment, add
its config under `examples/` or `sweeps/` so it can be re-run from source.

## Reporting issues

Please include the config (or sweep) file, the exact command, the simulator
version/commit, and the observed vs. expected behavior.
