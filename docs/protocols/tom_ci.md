# Totally Ordered Multicast on CI (`tom_pipeline`)

**Code:** `src/protocol/tom.rs`, `src/sim/tom_pipeline.rs`  
**PHY:** `flood_identical_round`  
**Config:** `protocol = "tom_pipeline"`, `phy_mode = "ci"`

---

## 1. Goal

Deliver the same sequence of messages `0..num_proposals-1` to the **application** on **every** node, in **identical order** (total order), without a central sequencer.

---

## 2. Distributed sequencing

Round-robin **is** the sequencer:

```
initiator = round_num % N
if messages remain:
  assign next_term
  publish message_data for that term
else:
  idle flood (has_message=false) for recovery / highest_seen
```

- No leader election when a node has nothing to send: its slot is skipped for **new** sequence numbers; others continue.
- Sequence IDs increase only when a node actually publishes (`next_term++`).

---

## 3. Packet (`TomPacket`)

```text
current_term: u64
has_message: bool
message_data: Vec<u8>
sender: usize
nack_term: u64                 # first missing term (0 = none)
piggyback_term: Option<u64>
piggyback_data: Vec<u8>
```

---

## 4. Middleware state (`NodeTomState`)

| Field | Role |
|-------|------|
| `last_delivered` | Last term given to app |
| `highest_seen` | Max term observed (wire) |
| `store` | Buffer of all known messages |
| `delivered` | Ordered delivery log |

**In-order delivery:**

```
next_expected = last_delivered+1 or 0
while store contains next_expected:
  deliver to app; last_delivered = next_expected
```

If term T arrives with T > next_expected: **buffer only**; do **not** deliver until gaps filled.

**Gap detection:** first missing term in `[next_expected, highest_seen]`.

---

## 5. Round loop

### Phase 1 — initiator

1. `nack_term = detect_gap()` (if any).
2. Publish new message **or** idle re-announce `current_term`.
3. Attach piggyback if previous round saw a NACK we can satisfy.

### Phase 2 — CI flood

Identical JSON flood via `RawMergeProtocol`.

### Phase 3 — participants (Sleep)

1. If `has_message`: `insert_message(term, data)`.
2. Else: raise `highest_seen` for gap detection.
3. Insert piggyback recovery data if present.
4. `try_deliver_in_order()`; first **global** delivery of a term → metrics (latency).
5. If packet NACK and we hold data → prepare piggyback for next round.

---

## 6. Recovery (gap catch-up)

```
see higher term without lower → NACK(lower)
peer piggybacks missing payload on future flood
fill store → drain buffer in order → resync pipeline
```

Logged as `[TOM Recovery] ... NACK ...` / `piggyback term ...`.

---

## 7. Termination & verification

- Stop when every node’s `delivered.len() >= num_msgs`, or graceful drain after global first-delivery of all msgs.
- `verify_total_order`: each node’s delivered terms must be contiguous `0,1,2,...` and match a reference prefix.

---

## 8. Why total order holds (under model)

1. **Injection order** fixed by RR + monotonic `next_term`.
2. **CI flood** does not reorder multi-path within a round (identical flood; one payload per round).
3. **Delivery** only advances on consecutive terms → no out-of-order app delivery even if recovery reorders reception of buffered terms.

---

## 9. Metrics

- Outcome **Committed** = first global in-order delivery of that message id.
- Latency = delivery slot − publish start slot.
