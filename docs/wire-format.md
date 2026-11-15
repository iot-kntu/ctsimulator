# Wire format and slot timing

CTSim is a slot-level model. Protocol packets are carried internally as JSON
(`serialize_packet`), but no metric, energy term or timing term reads that byte
length. When slot counts are converted to wall-clock time, the packet length is
taken from the bit-packed wire encoding specified here.

## Packet length

| Protocol | Payload length `L_wire(N)` in octets | Fixed part |
|----------|--------------------------------------|------------|
| 2PC      | `2 * ceil(N/8) + 22` | transaction_id u32, nack_tx_id u32, piggyback_tx u32, transaction data (8), sender u8, state/piggyback-state octet |
| Paxos    | `ceil(N/8) + 34` | current_term u32, last_accepted_term u32, nack_term u32, piggyback_term u32, proposal data (8), piggyback data (8), sender u8, presence-flags octet |

2PC carries two N-bit term-indexed vote windows (valid / YES); Paxos carries one
N-bit ACK window. The Paxos length is the maximum simultaneous shape, in which a
fresh proposal and a recovery piggyback travel in the same flood.

Examples: 2PC `L_wire(27) = 30`, `L_wire(188) = 70`; Paxos `L_wire(27) = 38`,
`L_wire(188) = 58`.

## Packet budget

Every packet must fit one Bluetooth 5 LE Data PDU payload (251 octets with Data
Length Extension); otherwise the link layer would fragment it and one flood would
no longer occupy one slot. `tests/validation.rs` checks this for `1 <= N <= 255`.
No IEEE 802.15.4 single-frame claim is made.

## Air time

On the BLE 1 Mbit/s PHY the air time of a packet with payload `L` octets is
`T_air(L) = 44.0 + 4.0 * L` microseconds. Slot duration is `T_air` plus the
guard and turnaround times of the target platform; the simulator itself counts
slots and is independent of this conversion.
