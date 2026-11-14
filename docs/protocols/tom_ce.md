# Totally Ordered Multicast on CE (`tom_ce`)

**Code:** `src/protocol/tom_ce.rs`  
**PHY:** `run_ce_round`  
**Orchestrator:** `Simulator`  
**Config:** `protocol = "tom_ce"`, `phy_mode = "ce"`

---

## 1. Goal

Deliver messages in the **same global order** on all nodes. On CE, order is induced by the **simulator**: one message per sequential CE round (term 1, 2, 3, …). Within a round, the problem reduces to **reliable dissemination** of that single message to all nodes.

---

## 2. Packet (`CeTomPacket`)

```text
term: u64
message_data: Vec<u8>
flags_bitmap: Vec<bool>   # bit i = node i holds this message
```

No NACK / piggyback (CE listen-timeout recovery).

---

## 3. Protocol logic

### `init_proposal`

- `current_term += 1`
- message `b"msg{term}"`
- initiator bit = 1

### `merge`

- Adopt empty local / higher term; set own hold-bit.
- Same term: OR bitmaps; copy message_data if missing; sticky self-bit.
- **NewInfo** → re-flood under CE.

### Goals

- `node_goal_reached`: full bitmap **and** non-empty data (node holds message and knows all peers do in its view — practically full bitmap).
- `network_goal_reached`: **every** node has full bitmap (all hold).
- `proposal_outcome`: full network goal → **Committed**, else **TimedOut**.

---

## 4. Total order argument

```
Simulator for-loop:
  proposal 0 → CE round until all hold msg₁
  proposal 1 → CE round until all hold msg₂
  ...
```

No node starts term k+1 until term k’s CE round finished (or timed out).  
Therefore application order = round order = term order, **if** each round commits. TimedOut rounds break completeness (message not fully disseminated).

Unlike CI TOM, there is **no** multi-message buffer + gap NACK across terms inside one flood; ordering is **externalized** to the orchestrator.

---

## 5. CE PHY coupling

| Event | Effect |
|-------|--------|
| Capture + NewInfo | OR more hold-bits; Flood next slot |
| Redundant | Stay Listen |
| Listen-timeout | Random re-flood of local packet |
| All full bitmaps | Round ends early |
| Deadline | TimedOut |

---

## 6. Recovery

Only Chaos-style:

- In-network OR of who-holds bits.
- Random re-flood on stall.
- No sequence-gap NACK (single term per round).

---

## 7. vs CI TOM

| | CI `tom_pipeline` | CE `tom_ce` |
|--|-------------------|-------------|
| Sequencer | RR publish terms | Simulator sequential rounds |
| Multi-msg pipeline | Yes | No (one msg / round) |
| Gap recovery | NACK + piggyback | Timeout re-flood only |
| Delivery middleware | In-order buffer | Round completion = deliver |
| Sleep energy | High | ~0 |

---

## 8. Metrics

- One CSV row per message/term.
- Latency = end of CE round − start of that proposal’s round.
- `CE_Timeouts` count recovery re-floods during dissemination.
