# CTSim — How the Simulator Works

**CTSim** is a slot-based simulator for **synchronous-transmission (ST)** low-power wireless protocols. Time advances one integer slot at a time (there is no event queue). It evaluates **Paxos**, **2PC**, and **TOM** over two PHY abstractions:

| PHY | Name in code | Inspired by | Round model |
|-----|--------------|-------------|-------------|
| **CI** | `PhyMode::CI` | Glossy / A2 / BlueFlood | Fixed-length **flood rounds** |
| **CE** | `PhyMode::CE` | Chaos | **Goal-based** rounds with in-network merge |

Source layout:

```
src/
  config.rs          # TOML → SimConfig
  event.rs           # NodeId, Slot (slot-based time types; no event queue)
  network.rs         # topologies + diameter()
  node.rs            # Listen / Flood / Sleep + metrics counters
  phy/ci.rs          # flood_identical_round (no Protocol)
  phy/ce.rs          # run_ce_round
  protocol/          # packet formats + CE Protocol trait impls
  sim.rs             # CE-only Simulator
  sim/pipeline.rs    # CiPipelineCore + CiProtocol + run_ci_pipeline
  sim/paxos_pipeline.rs
  sim/two_pc_pipeline.rs
  sim/tom_pipeline.rs
  metrics.rs
  main.rs            # protocol dispatch
```

---

## 1. Time model

### 1.1 Slot (tick)

- Time is discrete: `Slot = u64`.
- **Within a slot, node state is fixed.**
- Order of operations **every slot** (both CI and CE):

1. **Tick / snapshot** — record `Listen` / `Flood` / `Sleep` counters from **current** state; optional metrics snapshot.
2. **Action** — transmit / receive according to current state (no mid-slot state flip for the actor).
3. **Transition** — compute next-slot states; apply at end of slot.

**Invariant:** never change a node’s radio state in the middle of a slot and then re-use that new state for RX/TX in the same slot.

### 1.2 Round

- A **round** is a contiguous range of slots used for one flood (CI) or one goal-oriented dissemination (CE).
- CI: round length fixed:  
  `round_slots = max(3, (diameter + 2) * flood_repeats)` if not set in TOML.
- CE: round ends when `protocol.network_goal_reached` **or** `max_round_slots` exceeded.

### 1.3 Randomness

- Seeded `ChaCha8Rng` from `config.seed`.
- Used for: link loss, CE capture winner shuffle, CE listen-timeout re-flood (p=0.5), 2PC app-level abort (where configured).

---

## 2. Node model (`src/node.rs`)

```text
NodeState ∈ { Listen, Flood, Sleep }
```

| Field | Role |
|-------|------|
| `state` | Radio state for this slot |
| `flood_remaining` | CI only: remaining TX slots this flood participation |
| `last_progress_slot` | CE only: last slot with NewInfo / recovery |
| `payload` | Opaque protocol bytes (`Vec<u8>`, usually JSON) |
| `goal_reached` | Protocol flag for metrics snapshots |
| `slots_listen / flood / sleep` | Energy profile counters |

`reset_round()`: state → `Listen`, clear flood counters; **keeps** payload unless the orchestrator rewrites it.

`tick()`: +1 to the counter matching **current** state (called at **start** of slot).

---

## 3. Network (`src/network.rs`)

Undirected adjacency list. `diameter()` = BFS max eccentricity.

Topologies (TOML `network.topology`):

| Name | Notes |
|------|--------|
| `full_mesh` | diameter 1 |
| `line` | diameter N−1 |
| `star` | diameter 2 |
| `grid` | √N × √N |
| `ring` | cycle |
| `tree` | branching factor 3 |
| `scale_free` | Barabási–Albert, m=2 |
| `random` | min/max degree 2–5 |
| `partial_mesh` | spanning tree + edge prob 0.3 |
| file | optional `graph_file` adjacency list |

**Link loss:** each neighbor RX attempt succeeds with probability `1 − loss_rate` (independent Bernoulli).

---

## 4. CI engine (`src/phy/ci.rs` → `flood_identical_round`)

### 4.1 API

```rust
pub fn flood_identical_round(
    round_start, initiator, nodes, graph, ci_cfg,
    rng, loss_rate, metrics, next_snapshot, snapshot_interval,
) -> Slot
```

No `Protocol` argument. Pipelines call this only after writing initiator `payload`.

### 4.2 Preconditions

- Initiator’s `payload` already holds the **exact** bytes to flood.
- All nodes `reset_round()` → `Listen`.
- Initiator → `Flood`, `flood_remaining = flood_repeats`.

### 4.3 Identical payload law

```text
round_payload := clone(nodes[initiator].payload)
```

Every successful reception in this round is of **that same** blob. End of round: participants in `Sleep` (except initiator) get `payload = round_payload.clone()`.

### 4.4 Per-slot algorithm

```
for slot in [round_start, round_end):
  1. tick() all nodes; take snapshots if due
  2. Action:
       for each Flood node src:
         for each Listen neighbor nbr:
           if not lost: mark received_this_slot[nbr] = true
  3. Transition (for next slot):
       Flood: flood_remaining -= 1; if 0 → Sleep
       Listen && received: → Flood, flood_remaining = flood_repeats
  4. slot += 1
```

**Propagation timing example** (`flood_repeats = 1`, full mesh):

| Slot | Initiator | Others |
|------|-----------|--------|
| 0 start | Flood | Listen |
| 0 action | TX | RX |
| 0 end | Sleep | Flood |
| 1 start | Sleep | Flood |
| 1 action | — | TX |
| 1 end | Sleep | Sleep |

### 4.5 Snapshots

`next_snapshot` starts at **0** so slot 0 is logged: initiator Flood, others Listen.

### 4.6 Shared CI pipeline (`sim/pipeline.rs`)

```text
trait CiProtocol {
  prepare_round(core)   // write initiator payload
  process_round(core)   // middleware after flood
  should_stop(core)
}

run_ci_pipeline(protocol, core):
  loop:
    prepare_round
    core.flood_identical()   // → flood_identical_round
    process_round
    if should_stop: break
```

Paxos / 2PC / TOM CI each implement `CiProtocol`.

---

## 5. CE engine (`src/phy/ce.rs` → `run_ce_round`)

### 5.1 Preconditions

- Initiator payload = `protocol.init_proposal(...)`.
- Other nodes empty / `init_node_payload`.
- Initiator `Flood`; others `Listen`.

### 5.2 Goal check

At **start** of each slot (after tick/snapshot):

```
if protocol.network_goal_reached(all_payloads): return (slot, true)
```

### 5.3 Per-slot algorithm

```
next_states := all Listen
transmissions := payloads of nodes currently Flood

// Recovery: listen-timeout
for Listen nodes with (slot - last_progress) >= listen_timeout:
  with p=0.5: next_states[i] = Flood; mark progress; ce_timeouts++

// Capture RX (only current Listen, not already recovery-Flood)
for each Listen target:
  candidates = flooding neighbors of target
  shuffle candidates (random capture)
  for src in candidates:
    if link loss: continue
    result = protocol.merge(target.payload, src.payload, target)
    if NewInfo:
      next_states[target] = Flood
      last_progress update next
    break  // at most one capture per slot

// Apply transitions for next slot
state[i] = next_states[i]
// Note: Flood → Listen default unless NewInfo/recovery set Flood
```

**Important:** a node that **Flooded** this slot goes to **Listen** next slot unless recovery/NewInfo schedules Flood again. One-shot flood after merge (Chaos-style), not CI multi-repeat.

### 5.4 Recovery

No NACK headers in CE PHY. Progress stalls → **random re-flood** of local payload after `listen_timeout` slots without progress.

### 5.5 Deadline

If goal never met: return `(slot, false)` at `round_start + max_round_slots`.

---

## 6. Protocol trait (`src/protocol/mod.rs`)

Used by **generic** `Simulator` (CE protocols and any Protocol-driven path):

| Method | Meaning |
|--------|---------|
| `init_proposal(initiator, n)` | Start new proposal; return initiator payload |
| `init_node_payload(id)` | Non-initiator initial buffer |
| `merge(local, received, node_id)` | CE in-network update → `NewInfo` / `Redundant` |
| `node_goal_reached(payload)` | Local done? |
| `network_goal_reached(payloads)` | Global done? |
| `proposal_outcome(payloads)` | Committed / Aborted / TimedOut |

**CI pipelines** use `CiProtocol` + `flood_identical_round` (no CE `Protocol` trait). Semantics live in `sim/*_pipeline.rs` + `protocol/{paxos,two_pc_pipeline,tom}.rs`.

---

## 7. Orchestrators

### 7.1 Generic `Simulator` (`sim.rs`) — **CE only**

```
// panics if phy_mode != "ce"
for proposal_id in 0..num_proposals:
  initiator = proposal_id % N
  init payloads
  run_ce_round(...)   # until goal or deadline
  record metrics via proposal_outcome
```

Used by: `paxos_ce`, `2pc_ce`, `tom_ce`.

CI workloads use the pipeline modules below; `Simulator` handles CE only.

### 7.2 Pipeline sims (CI)

Shared pattern (`run_ci_pipeline` + `CiProtocol`):

```
loop:
  initiator = round_num % N
  prepare_round: build protocol packet on initiator
  flood_identical_round
  process_round: middleware on participants
  if should_stop: break
  round_num += 1
```

| Protocol | Module | Config string |
|----------|--------|---------------|
| Paxos CI | `sim/paxos_pipeline.rs` | `paxos_pipeline` |
| 2PC CI | `sim/two_pc_pipeline.rs` | `2pc_pipeline` |
| TOM CI | `sim/tom_pipeline.rs` | `tom_pipeline` |

---

## 8. Metrics (`src/metrics.rs`)

| Output | Content |
|--------|---------|
| `results_*.csv` | proposal_id, start_slot, end_slot, latency, outcome |
| `snapshots_*.csv` | slot, **progress_count** (unified all-N decisions), listening, flooding, sleeping |
| Console | totals, avg latency, energy Listen/Flood/Sleep, NACK/piggyback/CE_timeout counts |

**Latency:** first global commit/delivery time − recorded start slot for that proposal/message.

### 8.1 Unified progress bar (all protocols)

For fair cross-protocol progress comparisons, **one decision counts only when all N nodes hold the result locally**:

| Layer | Rule |
|-------|------|
| **CE round end** (`network_goal_reached`) | All N nodes informed of the protocol result |
| **CE progress** | After terminal outcome: `progress_count = #completed proposals` on every node |
| **CI progress** | `progress_count = min over nodes of local log/delivered length` |
| **Snapshot column** | `progress_count` = that network-wide count (not sum over nodes) |

Protocol-specific *safety* for commit still differs (Paxos majority vs 2PC unanimity vs TOM delivery), but **wall-clock progress** uses the same all-N informed bar.

| Protocol | Round complete when | Commit safety (outcome) |
|----------|---------------------|-------------------------|
| TOM CE | All N hold message | Same (delivery) |
| Paxos CE | All N hold quorum bitmap | Majority of nodes with quorum |
| 2PC CE | All N hold final phase | Unanimity vote + final phase |
| CI pipelines | (fixed slots) | Local log; progress = min log across nodes |

---

## 9. Configuration (TOML)

```toml
seed = 42
phy_mode = "ci" | "ce"
protocol = "paxos_pipeline" | "2pc_pipeline" | "tom_pipeline"
         | "paxos_ce" | "2pc_ce" | "tom_ce"
num_proposals = 10
snapshot_interval = 1
max_slots = 50000
abort_probability = 0.0   # 2PC app-level NO (top-level)
quiet = true              # Disable stdout (useful for sweep)

[network]
num_nodes = 5
topology = "line"
loss_rate = 0.2

[ci]
flood_repeats = 1
# round_slots = 12   # optional override

[ce]
listen_timeout = 5
max_round_slots = 150
```

Run:

```bash
cargo run --release -- examples/paxos_ci.toml
```

---

## 10. Mental model: layers

```text
┌─────────────────────────────────────────────┐
│ Application / Middleware (protocol state)   │
│  Paxos votes, 2PC unanimity, TOM delivery   │
├─────────────────────────────────────────────┤
│ Orchestrator                                │
│  CE: Simulator (CE-only)                    │
│  CI: paxos/2pc/tom pipeline sims            │
├─────────────────────────────────────────────┤
│ PHY (ci.rs / ce.rs)                         │
│  slots, Flood/Listen/Sleep, loss, recovery  │
├─────────────────────────────────────────────┤
│ Network graph + RNG                         │
└─────────────────────────────────────────────┘
```

**CI:** PHY floods **identical** bytes; middleware runs **between** rounds.  
**CE:** PHY allows **different** payloads; middleware `merge` runs **inside** the round on capture.

---

## 11. Related docs

| File | Content |
|------|---------|
| [protocols/paxos_ci.md](protocols/paxos_ci.md) | Pipeline Paxos on CI |
| [protocols/paxos_ce.md](protocols/paxos_ce.md) | Chaos Paxos on CE |
| [protocols/2pc_ci.md](protocols/2pc_ci.md) | Non-blocking 2PC pipeline on CI |
| [protocols/2pc_ce.md](protocols/2pc_ce.md) | Unanimity 2PC on CE |
| [protocols/tom_ci.md](protocols/tom_ci.md) | Totally Ordered Multicast on CI |
| [protocols/tom_ce.md](protocols/tom_ce.md) | TOM dissemination on CE |
