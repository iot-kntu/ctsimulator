# Non-blocking 2PC on CI (`2pc_pipeline`)

**Code:** `src/protocol/two_pc_pipeline.rs`, `src/sim/two_pc_pipeline.rs`  
**PHY:** `flood_identical_round`  
**Config:** `protocol = "2pc_pipeline"`, `phy_mode = "ci"`  
**Related config:** top-level `abort_probability` (app-level NO)

---

## 1. Goal

**Unanimous** commit (all N YES) for each transaction, with:

- **Bounded voting:** decision after one full RR cycle (`num_nodes` rounds of life).
- **Non-blocking:** any node that sees all-YES (or an explicit NO) can finalize without waiting for a single coordinator.
- **Fast abort:** an explicit NO aborts immediately; any remaining UNKNOWN at the deadline aborts safely.

---

## 2. Packet (`TwoPcPacket`)

```text
transaction_id: u64
state: Prepare | Committed | Aborted   # wire state for current flood focus
vote_valid_window: Vec<bool>           # sender-owned, TERM-indexed (N bits)
vote_yes_window:   Vec<bool>           # companion window (N bits)
nack_tx_id: u64                        # gap recovery for finalized log
sender: usize
transaction_data: Vec<u8>
piggyback_tx / piggyback_state         # recovery of final decision
```

### 2.1 Ternary vote window

In CI only the round initiator creates bytes; relays retransmit the frame
unchanged. A node-indexed current-term bitmap would therefore only ever carry the
initiator's own bit (the `sender` field already identifies that node), and a
cumulative merge would widen a positive bit from a later transaction onto every
earlier pending one — invalid for independent 2PC votes.

The packet therefore carries **two** `N`-bit, term-indexed, **sender-owned** windows
over the most recent transactions (see `src/protocol/vote_window.rs`). Let `N`
be the node count and `T` the packet's `transaction_id`:

```text
window_start = T.saturating_sub(N - 1)
position j maps to transaction  window_start + j
```

- `vote_valid_window[j]`: the sender has **evaluated** the transaction mapped to
  position `j`.
- `vote_yes_window[j]`: meaningful only when `valid=true`; `true`=YES, `false`=NO.

**Truth table** — the only legal encodings:

| valid | yes | meaning |
|:-----:|:---:|---------|
| false | false | **UNKNOWN** (not yet evaluated) |
| true  | true  | **YES** |
| true  | false | **NO** |
| false | true  | **invalid** — never emitted; rejected in validation/tests |

There is no separate abort flag: a NO lives inside the window as
`(valid=true, yes=false)`, so it can never widen to any other transaction.

During startup (`T < N-1`), positions mapping above `T` are **padding** and must
carry `valid=false`; they are never interpreted as votes.

---

## 3. Local state

- `PendingTx`: `tx_id`, `data`, **`votes: Vec<Vote>`** (per-sender ternary
  UNKNOWN/YES/NO, node-indexed), `start_round`.
- `log`: finalized `(tx_id, Committed|Aborted)`.
- `own_votes`: **persistent** `(tx_id, Vote)` history of this node's OWN
  evaluated votes.
- `deferred_votes`: `(tx_id, voter, Vote)` heard before the transaction data
  arrived.

Per-sender votes are kept **per node**, so a NO is never collapsed into a global
boolean that loses which sender voted NO.

### 3.1 Persistent own-vote history (`own_votes`)

A node's advertised windows report its OWN votes from `own_votes`, **not** from
`pending`. This matters because `finalize_tx` removes a transaction from
`pending` the moment it is locally decided (fast-abort on an own NO, or a
learned/piggybacked outcome). If the window were built from `pending`, a node
would stop advertising its own vote for a transaction still inside the sliding
`N`-term window as soon as it finalized locally — silently dropping information
its neighbours still need.

- `record_own_vote(tx, vote)`: records this node's own YES/NO **exactly once**
  (sample-once; UNKNOWN is never stored). Called from `adopt_tx`, independent of
  `pending`/`log`, so it survives finalization.
- `own_vote(tx)`: this node's own recorded vote, or UNKNOWN if it never
  evaluated the transaction. **A learned final outcome is not an own vote**:
  `finalize_tx` alone (e.g. learning `Aborted` through recovery/piggyback) never
  writes `own_votes`, so a node that never evaluated a transaction never
  fabricates a local NO/YES for it — its window position stays UNKNOWN.
- **Persistence after finalization**: a sampled YES/NO keeps being advertised
  across every window in which the transaction still falls, even after the node
  has locally fast-aborted and finalized it.
- **Production pruning** (`prune_own_votes(current_tx)`): called in the real
  initiator packet-build path (`prepare_round`) **before** `build_vote_windows`.
  It keeps every entry `>= window_start(current_tx, N)` (the oldest still
  reportable transaction) and drops only entries strictly below it — so a vote
  is pruned only once the transaction has permanently left every future
  `N`-term window. During drain rounds `current_tx` stops advancing, so the
  final `N` reportable votes are retained.

- `check_unanimity(tx)`: every sender's vote is YES (no UNKNOWN, no NO).
- `check_explicit_abort(tx)`: some sender's vote is NO.
- `record_vote(tx, voter, vote)`: sets **only** `voter`'s vote for **exactly**
  `tx`. A node evaluates a transaction at most once — a recorded YES/NO is never
  overwritten by a later conflicting value (sample-once). If the tx is unknown,
  the vote is cached in `deferred_votes`.
- `apply_vote_windows(current_tx, sender, valid, yes)`: for each valid,
  non-padding position, record the **sender's** vote for exactly the mapped
  transaction.
- `build_vote_windows(current_tx, self_node)`: emit only `self_node`'s own
  evaluated votes over the window **from `own_votes`**; padding stays UNKNOWN.
- `adopt_tx` / `finalize_tx` / `detect_gap` for insertion, log holes, piggyback.

Votes are never merged cumulatively: a vote for transaction `T` must never
imply a vote for any transaction below `T`.

---

## 4. Round loop

```
initiator = round_num % N
Phase 1: build TwoPcPacket
Phase 2: CI flood (identical)
Phase 3: Sleep participants update pending / finalize
```

### Phase 1 — initiator

1. NACK missing finalized TX if any.
2. If still generating TXs:
   - Draw this node's one-time vote once (`abort_probability` → NO, else YES).
   - `adopt_tx` with `start_round = round_num`.
3. **Bounded decision** for each pending with `round_num >= start_round + N`:
   - all-YES → **Committed**
   - else (any NO, or any remaining UNKNOWN) → **Aborted**
4. Build `vote_valid_window` / `vote_yes_window` from the initiator's own votes
   and flood the packet for `current_tx` (+ piggyback if recovering).

### Phase 3 — receivers

1. First sight of TX (non-empty `transaction_data`, not already known): draw this
   node's own vote **once** (`abort_probability`); `adopt_tx`.
2. Reject a malformed ternary encoding (`valid=false, yes=true`, or mismatched
   window lengths) — the packet is skipped.
3. `apply_vote_windows` — credit the packet's sender for exactly the mapped
   transactions, never earlier or later ones.
4. **Fast abort:** any pending tx with a recorded NO finalizes **Aborted**
   immediately — and only that transaction.
5. **Bounded finalize** (overhearing-based non-blocking): at the deadline commit
   only if all N senders are YES; any remaining UNKNOWN aborts.
6. Piggyback final decisions for NACK'd TX ids.

---

## 5. Why "non-blocking"

Classic 2PC blocks if the coordinator dies after collecting votes. Here:

- The per-sender vote state is flooded network-wide (CI overhearing) as the two
  term-indexed windows.
- After **exactly N RR rounds** of lifetime, **every** node independently
  evaluates all-YES vs not.
- No extra PreCommit phase (3PC); determinism of RR replaces dynamic timeouts for
  the voting window.

---

## 6. Abort conditions

| Case | Outcome |
|------|---------|
| Explicit NO (`valid=true, yes=false`) for the tx | **Aborted** (fast, that tx only) |
| Bound elapsed, not all-YES (loss / offline / remaining UNKNOWN) | **Aborted** |
| Bound elapsed, all-YES | **Committed** |

An UNKNOWN never aborts *before* the deadline; it only causes a safe abort at the
deadline because commit requires unanimity.

---

## 7. Recovery

- **NACK** on a missing **finalized** log entry — a request for the outcome, not
  a negative vote.
- **Piggyback** of `(tx_id, Committed|Aborted)`.
- **Deferred votes:** vote metadata heard before the transaction data is cached
  and reconciled once the data arrives via flood or recovery — never fabricating
  the payload, never erasing a recorded NO, never manufacturing unanimity.
- CI identical-flood + RR continues regardless of coordinator failure (next
  initiator continues the pipeline).

---

## 8. CI PHY coupling

Same as other pipelines: one identical JSON blob per flood (an internal carrier,
not the wire size); middleware only between rounds; Sleep ⇒ participated
successfully. The specified bit-packed wire size is
`L_wire(N) = 2*ceil(N/8) + 22` octets (two windows) — see
[`docs/wire-format.md`](../wire-format.md) and the guards in `tests/validation.rs`.
