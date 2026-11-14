# Paxos on CE / Chaos (`paxos_ce`)

**Code:** `src/protocol/paxos_ce.rs`  
**PHY:** `run_ce_round`  
**Orchestrator:** generic `Simulator` (`sim.rs`)  
**Config:** `protocol = "paxos_ce"`, `phy_mode = "ce"`

---

## 1. Goal

Commit **one proposal per CE round** when a **majority** of nodes hold the proposal and have set their vote bit. No pipeline across rounds; no NACK/piggyback headers.

---

## 2. Packet (`CePaxosPacket`)

```text
term: u64
proposal_data: Vec<u8>
flags_bitmap: Vec<bool>   # bit i = node i voted YES for this term
```

Lean Chaos-style aggregation packet.

---

## 3. Protocol methods

### `init_proposal(initiator, n)`

- `current_term += 1`
- bitmap all false; `bitmap[initiator] = true`
- payload = JSON of packet with `proposal_data = b"tx{term}"`

### `init_node_payload`

- empty `Vec` (filled on first merge)

### `merge(local, received, node_id)`

1. Deserialize received.
2. If local empty: adopt packet, set `flags_bitmap[node_id]=true` → **NewInfo**.
3. If `rec.term > loc.term`: adopt newer, set own bit → **NewInfo**.
4. If `rec.term < loc.term`: **Redundant**.
5. Same term: **OR** bitmaps; ensure own bit set; if any new bit → **NewInfo** else **Redundant**.

### Goals

- `node_goal_reached`: majority bits set in local bitmap (`N/2+1`).
- `network_goal_reached`: **all N** nodes have local quorum (unified all-informed bar for fair latency).
- `proposal_outcome`: if at least **majority** of nodes reached local goal → **Committed**, else **TimedOut** (Paxos safety; may commit before round fully ends if deadline hits).

---

## 4. Interaction with CE PHY

Per slot inside `run_ce_round`:

1. If **every** node has local quorum → round ends early (all-N informed).
2. Flood nodes TX their **current** (possibly different) payloads.
3. Listeners capture **one** neighbor TX (random among concurrent), run `merge`.
4. **NewInfo** → next state Flood (re-broadcast merged bitmap).
5. Redundant → stay Listen (unless listen-timeout recovery).
6. Listen-timeout: random re-flood of local state (Chaos recovery).

**Difference vs CI Paxos:** votes accumulate **inside one round** via OR; no RR multi-round pipeline required for a single proposal.

---

## 5. Round lifecycle in `Simulator`

```
for proposal_id in 0..num_proposals:
  initiator = proposal_id % N
  clear payloads; initiator gets init_proposal
  run_ce_round until goal or max_round_slots
  record proposal_outcome
```

Total order of proposals = order of sequential CE rounds (one term each).

---

## 6. Recovery

| Mechanism | Role |
|-----------|------|
| In-network OR | Late nodes pick up denser bitmaps from neighbors |
| Listen-timeout re-flood | Breaks all-Listen deadlock under loss |
| max_round_slots | Hard abort of round → TimedOut if no majority |

No NACK field.

---

## 7. Comparison to `paxos_pipeline`

| | CI Pipeline | CE Chaos |
|--|-------------|----------|
| Votes | Across RR rounds | Same CE round |
| Commit signal | `last_accepted_term` | Majority bitmap locally |
| Recovery | NACK + piggyback | Timeout re-flood |
| Energy Sleep | High (CI sleep after flood) | Sleep≈0 (Listen/Flood) |
