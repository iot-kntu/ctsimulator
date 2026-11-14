# Pipeline Paxos on CI (`paxos_pipeline`)

**Code:** `src/protocol/paxos.rs`, `src/sim/paxos_pipeline.rs`  
**PHY:** `flood_identical_round` (identical flood)  
**Config:** `protocol = "paxos_pipeline"`, `phy_mode = "ci"`

---

## 1. Goal

Agree on a sequence of proposals (terms `0..num_proposals-1`) under **majority quorum**, with **pipelined** floods (one RR initiator per flood round), without classical Prepare/Accept **phases** in the packet.

Commit is **implicit** via `last_accepted_term`.

---

## 2. Packet (`PaxosPacket`)

```text
Header:
  current_term: u64              # term of primary data this flood (or drain term)
  last_accepted_term: Option     # highest term known committed (quorum)
  ack_window: Vec<bool>          # sender-owned, TERM-indexed ACK window (N bits)
  nack_term: u64                 # 0 = none; else missing term request
  sender: usize

Payload:
  proposal_data: Vec<u8>         # empty when draining
  piggyback_term: Option<u64>
  piggyback_data: Vec<u8>
```

No `Phase` field.

### 2.1 Term-indexed ACK window

The wire carries no node-indexed current-term bitmap. In CI only the round
initiator creates bytes and relays retransmit the frame unchanged, so such a
bitmap would only ever carry the initiator's own bit — the `sender` field
already identifies that node.

Instead, `ack_window` is a **sender-owned sliding window** over the most recent
terms (see `src/protocol/vote_window.rs`). Let `N` be the node count and `T` the
packet's `current_term`:

```text
window_start = T.saturating_sub(N - 1)
position j maps to term  window_start + j
```

- The window has exactly `N` logical positions.
- Bit `j = true` means: **this packet's `sender`** has seen and affirmatively
  voted for the term mapped to position `j`.
- During startup (`T < N-1`), positions mapping above `T` are **padding** and
  are always false; they must never be interpreted as votes.
- In steady state the window covers the `N` most recent terms including `T`.

---

## 3. Local state (`NodePaxosState`)

| Field | Meaning |
|-------|---------|
| `highest_seen_term` | Max term observed on wire |
| `last_accepted` | Max term known committed |
| `log` | Committed `(term, data)` |
| `pending` | Awaiting quorum: term, data, **node-indexed** local `bitmap` |
| `deferred_votes` | `(term, voter)` ACKs heard before the proposal data arrived |

Quorum is still **node-indexed locally**: each `PendingProposal.bitmap[k]` is
node `k`'s vote for that term, and `check_quorum` counts those bits. The wire
window is only the transport; the receiver expands it back into per-node local
votes.

Helpers:

- `build_ack_window(current_term, N, sender)` — emit only `sender`'s own votes
  for the mapped terms; padding positions stay false.
- `apply_ack_window(current_term, N, sender, window)` — for every true,
  non-padding position, set `pending[mapped_term].bitmap[sender] = true`, and
  **only** that. A later-term ACK can never backfill an earlier term unless that
  earlier term has its own true position in the window.
- `defer_vote(term, voter)` / drain-on-adopt — an ACK for a term whose proposal
  has not arrived yet is retained in `deferred_votes` and applied verbatim when
  the proposal materialises (fresh flood or NACK recovery). Never discarded, and
  never used to fabricate proposal data.
- `check_quorum(term)` — votes ≥ `N/2 + 1`.
- `commit_up_to(T)` — move all terms `0..=T` into log.
- `detect_gap(up_to)` — first missing term in `[last_accepted+1, up_to)`.

Votes are never merged cumulatively (OR-ing a later term's bit into every
earlier pending term), because that is invalid for independent per-term votes.

---

## 4. Round loop (orchestrator)

```
round_num = 0
term_counter = 0
loop:
  initiator = round_num % N
  Phase 1: initiator builds PaxosPacket
  Phase 2: flood_identical_round(initiator)  # identical bytes
  Phase 3: every node that ended Sleep processes packet
  terminate / drain checks
  round_num += 1
```

### Phase 1 — initiator build

1. **NACK:** `nack_term = detect_gap(term_counter)`.
2. **New proposal** if still generating:
   - term = `term_counter`, data = `b"tx{term}"`
   - `adopt_proposal` with self-bit = 1
   - record `proposal_starts[term] = current_slot`
   - `term_counter++`
3. Else **drain:** `current_term = term_counter-1`, empty `proposal_data`.
4. **Local quorum check** on initiator's pending → `commit_up_to` highest quorum term; first global commit → metrics.
5. Packet: `ack_window = build_ack_window(current_term, N, initiator)`,
   `last_accepted_term = state.last_accepted`, attach piggyback from previous round if any.

### Phase 2 — CI flood

- `RawMergeProtocol`: end-of-round copy of initiator bytes into participants' `payload`.
- PHY: fixed slots, Flood/Listen/Sleep as in [simulator.md](../simulator.md).

### Phase 3 — middleware (participants in Sleep)

For each node that participated (`state == Sleep` after round):

1. If `last_accepted_term = Some(L)` → `commit_up_to(L)`; metrics on first global commit.
2. Update `highest_seen_term`.
3. If non-empty `proposal_data` and term not known → `adopt_proposal` (seeds the
   receiver's own vote plus any deferred votes for that term).
4. `apply_ack_window(current_term, N, sender, ack_window)` — credit only the
   packet's sender for exactly the mapped terms — then OR the **receiver's own**
   vote for the current term into its local bitmap.
5. If `piggyback_term/data` present → `adopt_proposal` for the recovered term
   with only the receiver's own vote plus drained deferred ACKs. Recovery never
   grants quorum by itself.
6. If packet `nack_term > 0` and we have that term in log/pending → schedule
   **piggyback** for next initiator build. A NACK is a request for missing data,
   never a vote.

---

## 5. Commit semantics

```
Majority votes on term T (in some node's view)
  → that node may set last_accepted = T when it initiates
  → flood carries last_accepted_term
  → all receivers commit_up_to(T)  # T and all prior
```

Pipeline: proposal T and votes for older terms travel in later RR floods; each
later flood's `ack_window` re-affirms the sender's own votes over its `N`-term
window, so a vote for an older term keeps propagating until quorum forms —
without ever being applied to a term the sender did not vote for.

---

## 6. Recovery

| Mechanism | When |
|-----------|------|
| **Gap NACK** | Initiator missing term in sequence |
| **Piggyback** | Peer holds NACK'd term data → attach on next flood |
| **Deferred ACK** | ACK heard before proposal data → cached, applied on adopt |
| **Drain rounds** | After all proposals generated, empty-data floods continue so votes/commits/NACKs finish |

A NACK requests missing proposal data; it is not a negative vote and never
erases a recorded vote. Recovery must not grant quorum on its own — a
recovered proposal is adopted with only the receiver's own vote plus any
previously cached (deferred) ACKs for that exact term.

**Graceful stop:** when all terms are in `globally_committed`, allow ~`5 * diameter` extra rounds for stragglers, then exit (avoids infinite hang under loss).

---

## 7. Metrics meaning

- **Committed** = first time term enters global metrics (quorum path).
- Latency = commit slot − `proposal_starts[term]`.
- Recovery counters: `nacks_sent`, `piggybacks_sent`.

---

## 8. Example timeline (N=5, RR)

| Round | Initiator | Typical content |
|-------|-----------|-----------------|
| 0 | 0 | Propose term 0, `ack_window` affirms term 0 for sender 0 |
| 1 | 1 | Propose term 1; sender 1's window may also affirm term 0 |
| … | … | Pipeline overlap; each window re-affirms only the sender's own votes |
| k | * | `last_accepted_term` advances as per-term quorums form |

Under loss: NACK + piggyback fill holes without restarting the whole log; a
deferred ACK heard before the data lands on the term once the data arrives.
