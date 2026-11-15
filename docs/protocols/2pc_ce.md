# 2PC on CE / Chaos (`2pc_ce`)

**Code:** `src/protocol/two_pc_ce.rs`  
**PHY:** `run_ce_round`  
**Orchestrator:** `Simulator`  
**Config:** `protocol = "2pc_ce"`, `phy_mode = "ce"`, `abort_probability`

---

## 1. Goal

One transaction per CE round in **two phases**:

| Phase | Name | What happens |
|-------|------|-------------|
| 1 | **Prepare (Vote)** | All N nodes must vote YES (unanimity) in bitmap; **any** NO escalates to Abort |
| 2 | **Commit / Abort (Disseminate)** | Reset bitmap; disseminate the final decision to all nodes |

Round ends when **every** node holds the same final phase (Commit ∨ Abort).

---

## 2. Packet (`CeTwoPcPacket`)

```rust
struct CeTwoPcPacket {
    term: u64,
    phase: TwoPcCePhase,        // Prepare | Committed | Aborted
    proposal_data: Vec<u8>,
    flags_bitmap: Vec<bool>,     // Phase 1: YES votes; Phase 2: who heard the decision
}
```

No NACK / piggyback (CE recovery is timeout re-flood).

---

## 3. State machine

```
                      ┌─────────────────┐
                      │    Prepare       │
                      │ (OR YES votes)   │
                      └──────┬──────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
    ┌─────────────────┐          ┌─────────────────┐
    │    Committed     │          │    Aborted       │
    │ (disseminate OK) │          │ (inform others)  │
    └─────────────────┘          └─────────────────┘
              │                             │
              └────────── goal ─────────────┘
```

**Escalation rules:**
- **Prepare → Committed:** node's local bitmap has all N bits (unanimity vote achieved).
- **Prepare → Aborted:** app-level `should_abort_app` returns true for this `(term, node_id)`.
- **Committed/Aborted → Sleep:** full dissemination bitmap → flood once more → sleep (enforced in `ce.rs`).

---

## 4. App-level NO

Deterministic per `(term, node_id, seed)` hash vs `abort_probability`:

- Hash function: `term.wrapping_mul(1_000_003).wrapping_add(node_id + 1).wrapping_mul(seed + 0x9e37_79b9_7f4a_7c15)`
- If `(hash % 10_000) / 10_000 < abort_probability` → Abort.

---

## 5. Merge rules

| Case | Action |
|------|--------|
| Local empty | `adopt_with_local_vote`: decide YES/NO, own bitmap set |
| Higher term | Adopt + local decision |
| Lower term | Redundant |
| Same term, same phase | OR bitmaps. In Prepare, if full → escalate to Committed |
| Same term, received Aborted | Escalate local to Aborted, adopt received bitmap |
| Same term, received Committed | Escalate local from Prepare to Committed, adopt received bitmap |

**Key:** local vote always set at the end → ensures full dissemination bitmap eventually.

---

## 6. Node sleep policy (`ce.rs`)

After `node_can_sleep` triggers (final phase + full dissemination bitmap):

- If node **already flooding** → sleep next slot (last flood carries final state).
- If node **listening** → switch to Flood one slot, then sleep.

This guarantees one final broadcast of the completed state to any stragglers.

---

## 7. Goals & outcome

- `node_goal_reached`: phase is Committed ∨ Aborted (decision known).
- `node_can_sleep`: phase is final **AND** full dissemination bitmap locally.
- `network_goal_reached`: **all** nodes in same final phase (Commit ∨ Abort).
- `proposal_outcome`:
  - Any Aborted phase → **Aborted**
  - All Committed → **Committed**
  - Incomplete by deadline → **Aborted** (2PC safety)

---

## 8. CE PHY coupling

Same as other CE protocols:

- Concurrent different payloads OK (capture effect).
- OR aggregation mid-round.
- Listen-timeout random re-flood spreads phase 1 votes or phase 2 final state.
- Round ends early when all nodes decide; else `max_round_slots`.

---

## 9. vs CI 2PC

| | CI pipeline | CE Chaos |
|--|-------------|----------|
| Phases | Implicit in slot timeline (RR) | Explicit packet state (`Prepare → Commit`) |
| Voting window | Exactly N RR rounds | Until goal / deadline |
| Aggregation | Between floods (next round) | In-network OR (same round) |
| Explicit NO | `(valid=true, yes=false)` in the vote window | Phase escalation to Aborted + dissemination |
| Recovery | NACK / piggyback | Listen-timeout re-flood |