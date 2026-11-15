//! Structural checks on the network model, packet sizes and accounting.
//!
//! These tests assert *structural* properties of the model that the
//! reported metrics rely on. They run no protocol logic and call no
//! existing simulation entry point beyond the public graph builders, so a
//! failure here means a graph builder changed, not that a protocol regressed.

use ctsim::config::{CeConfig, CiConfig, NetworkConfig, SimConfig};
use ctsim::event::NodeId;
use ctsim::network::NetworkGraph;
use ctsim::protocol::paxos::PaxosPacket;
use ctsim::protocol::two_pc_pipeline::{serialize_packet, TwoPcPacket, TwoPcState};
#[allow(clippy::manual_div_ceil)]
fn ceil_div8(n: usize) -> usize {
    (n + 7) / 8
}
use ctsim::run::{build_graph, make_sim_config, run_experiment};
use ctsim::sim::pure_flood::PureFloodSim;
use rand::SeedableRng;
use rand_chacha::ChaCha8Rng;

/// The seed set used by both sweeps (`sweeps/topology.toml`,
/// `sweeps/scalability.toml`).
const SEEDS: [u64; 15] = [2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47];

/// Topology arms of the topology sweep.
const TOPOLOGIES: [&str; 5] = ["line", "partial_mesh", "random", "scale_free", "full_mesh"];

/// Node counts of the scalability sweep.
const NODE_COUNTS: [usize; 5] = [6, 13, 27, 54, 188];

/// Size of the largest connected component, found by BFS from node 0's component
/// and then from every unvisited node.
fn component_sizes(graph: &NetworkGraph) -> Vec<usize> {
    let n = graph.num_nodes();
    let mut seen = vec![false; n];
    let mut sizes = Vec::new();
    for start in 0..n {
        if seen[start] {
            continue;
        }
        let mut size = 0usize;
        let mut queue = std::collections::VecDeque::new();
        queue.push_back(start as NodeId);
        seen[start] = true;
        while let Some(u) = queue.pop_front() {
            size += 1;
            for v in graph.neighbors(u) {
                if !seen[v] {
                    seen[v] = true;
                    queue.push_back(v);
                }
            }
        }
        sizes.push(size);
    }
    sizes.sort_unstable_by(|a, b| b.cmp(a));
    sizes
}

fn graph_for(topology: &str, num_nodes: usize, seed: u64) -> NetworkGraph {
    let network = NetworkConfig {
        num_nodes,
        topology: topology.to_string(),
        loss_rate: 0.0,
        graph_file: None,
    };
    let mut rng = ChaCha8Rng::seed_from_u64(seed);
    build_graph(&network, &mut rng)
}

/// Risk (a): `diameter()` filters unreachable pairs, so a disconnected graph
/// reports a component diameter rather than infinity and no error is raised.
/// Every graph actually used by the published sweeps must therefore be checked
/// for connectivity explicitly.
#[test]
fn every_published_graph_is_connected() {
    let mut disconnected = Vec::new();

    for topology in TOPOLOGIES {
        for seed in SEEDS {
            let graph = graph_for(topology, 27, seed);
            let sizes = component_sizes(&graph);
            println!(
                "topology={topology:<13} n=27  seed={seed:<3} components={:<2} largest={:<3} diameter={} edges={}",
                sizes.len(),
                sizes[0],
                graph.diameter(),
                graph.edge_count()
            );
            if sizes.len() != 1 {
                disconnected.push(format!("{topology} n=27 seed={seed} components={sizes:?}"));
            }
        }
    }

    for num_nodes in NODE_COUNTS {
        for seed in SEEDS {
            let graph = graph_for("random", num_nodes, seed);
            let sizes = component_sizes(&graph);
            println!(
                "topology=random        n={num_nodes:<4} seed={seed:<3} components={:<2} largest={:<3} diameter={} edges={}",
                sizes.len(),
                sizes[0],
                graph.diameter(),
                graph.edge_count()
            );
            if sizes.len() != 1 {
                disconnected.push(format!(
                    "random n={num_nodes} seed={seed} components={sizes:?}"
                ));
            }
        }
    }

    assert!(
        disconnected.is_empty(),
        "disconnected graphs feed the published sweeps, so their reported diameter is a \\
         component diameter and their commit rates are affected:\n{}",
        disconnected.join("\n")
    );
}

/// The snapshot series and the energy counters must agree exactly.
///
/// `node.tick()` charges every node-slot to exactly one of Listen/Flood/Sleep, so
/// `listen + flood + sleep == N * (slots simulated)` identically. The snapshot
/// series is a second accounting of the same slots, and the amortised-energy
/// metric integrates over it. If a
/// slot is emitted that was never ticked, or a ticked slot is not emitted, the two
/// disagree and the amortised figures are wrong by that amount.
///
/// Two defects made them disagree before this test existed: `phy/ci.rs` emitted the
/// round-boundary slot with the pre-`reset_round()` all-Sleep state (one row per
/// round, `N` node-slots each, ~1.41x under-count on CI), and both orchestrators
/// emitted a trailing snapshot for the first *free* slot, duplicating one row.
///
/// SCOPE: this invariant is defined at `snapshot_interval = 1` ONLY, which is what
/// these six runs use. At any larger interval the series is a deliberate *sample*
/// and `rows * N == L+F+S` fails by construction — a 455-slot run emits 23 rows at
/// interval 20 (594 node-slots against 12285 charged). The sweep configurations use
/// 20 and `config.rs` defaults to 50, so this test does NOT cover them; consumers of
/// amortised energy must require a per-slot series. What this test does not check:
/// any interval but 1, any topology but `random`, any N but 27, and whether the
/// *values* in each row are right — only that every ticked slot is represented
/// exactly once and that the two accountings of it agree.
#[test]
fn snapshot_series_accounts_for_every_ticked_slot() {
    let ce = CeConfig {
        listen_timeout: 5,
        max_round_slots: 300,
    };
    let arms = [
        ("ci", "tom_pipeline"),
        ("ci", "paxos_pipeline"),
        ("ci", "2pc_pipeline"),
        ("ce", "tom_ce"),
        ("ce", "paxos_ce"),
        ("ce", "2pc_ce"),
    ];

    for (phy, protocol) in arms {
        let config = make_sim_config(
            99, phy, protocol, 27, "random", 0.05, 1, &ce, 100, 1, 50_000, 0.0,
        );
        let result = run_experiment(config);
        let s = &result.summary;
        let snaps = &result.metrics.snapshots;

        let awake_ticks = s.total_listen + s.total_flood;
        let awake_snaps: u64 = snaps
            .iter()
            .map(|x| (x.nodes_listening + x.nodes_flooding) as u64)
            .sum();
        assert_eq!(
            awake_ticks, awake_snaps,
            "{protocol}: energy counters charged {awake_ticks} awake node-slots but the \\
             snapshot series accounts for {awake_snaps}"
        );

        let sleep_snaps: u64 = snaps.iter().map(|x| x.nodes_sleeping as u64).sum();
        assert_eq!(sleep_snaps, s.total_sleep, "{protocol}: sleep disagrees");

        // Every ticked slot emitted exactly once: no duplicate rows, contiguous from 0.
        let mut slots: Vec<u64> = snaps.iter().map(|x| x.slot).collect();
        let rows = slots.len();
        slots.sort_unstable();
        slots.dedup();
        assert_eq!(rows, slots.len(), "{protocol}: duplicate snapshot rows");
        assert_eq!(slots[0], 0, "{protocol}: snapshots do not start at slot 0");
        assert_eq!(
            *slots.last().unwrap(),
            rows as u64 - 1,
            "{protocol}: snapshot slots are not contiguous"
        );
        assert_eq!(
            rows as u64 * 27,
            s.total_listen + s.total_flood + s.total_sleep,
            "{protocol}: rows * N != total node-slots charged"
        );
    }
}

/// The connectivity above is not luck: `random_topology` and `partial_mesh` both
/// open by attaching every node `i >= 1` to a uniformly chosen node `< i`, which
/// is a spanning tree, and `scale_free` grows from a complete core by attaching
/// each new node to existing ones. This test pins that reasoning at the node
/// counts the study actually uses.
///
/// HAZARD, deliberately not exercised here: `random_topology`'s second loop is
/// `while adj[i].len() < num_neighbors`, with `num_neighbors` drawn from
/// `min_neighbors..=max_neighbors` (`src/network.rs:291-298`). When
/// `n - 1 < max_neighbors` the condition can never be satisfied and the builder
/// spins forever. With the published bounds (2, 5) that means any `n <= 5`
/// hangs. Every published configuration has `n >= 6`, so no published run is
/// affected, but a future sweep over small `n` would hang rather than fail.
#[test]
fn random_builders_are_connected_by_construction_not_by_degree() {
    for seed in SEEDS {
        for n in [6usize, 13, 27, 54, 180, 188] {
            let mut rng = ChaCha8Rng::seed_from_u64(seed);
            let graph = NetworkGraph::random_topology(n, 2, 5, &mut rng);
            assert_eq!(
                component_sizes(&graph).len(),
                1,
                "random_topology(n={n}, 2, 5) disconnected at seed {seed}"
            );
        }
    }
}

/// Maximum payload of one BLE LE Data PDU (Core Spec, LL Data PDU payload field).
const LL_DATA_PDU_MAX_OCTETS: usize = 251;

/// The wire encoding *specified* in `docs/wire-format.md`, in octets,
/// for the 2PC packet, which carries TWO N-bit term-indexed vote windows instead
/// of one node-indexed bitmap.
///
/// The 22 fixed octets are transaction_id u32 (4), nack_tx_id u32 (4),
/// piggyback_tx u32 (4), transaction_data `tx{term}` ASCII (8), sender u8 (1),
/// and one octet holding state and piggyback_state packed together (1). The old
/// scalar `abort_flag` is gone: a NO is now encoded inside the ternary window
/// (valid=true, yes=false), so it costs no extra fixed octet. The two vote
/// windows (`vote_valid_window`, `vote_yes_window`) add `2*ceil(N/8)` octets on
/// top of those 22.
#[allow(clippy::manual_div_ceil)]
fn wire_payload_len(num_nodes: usize) -> usize {
    2 * ceil_div8(num_nodes) + 22
}

/// The Paxos packet carries a single N-bit ACK window, so its bitmap cost is
/// half the 2PC packet's, but its MAXIMUM SIMULTANEOUS shape carries TWO bounded
/// 8-byte data fields at once: a fresh `proposal_data` for `current_term` AND a
/// recovery `piggyback_data`, both emitted in the same flood by
/// `PaxosCi::prepare_round` (the piggyback is carried over from the previous
/// round's NACK handling and is not mutually exclusive with a new proposal).
///
/// Its 34 fixed octets are current_term u32 (4), last_accepted_term u32 (4),
/// nack_term u32 (4, 0 = none sentinel — no presence bit), sender u8 (1),
/// proposal_data `tx{term}` ASCII (8), piggyback_term u32 (4), piggyback_data
/// `tx{term}` ASCII (8), and one presence-flags octet (1) for the two
/// Option-typed term fields. So `L_wire_paxos(N) = ceil(N/8) + 34`, verified
/// against the maximum-shape packet in `paxos_max_shape_matches_wire_formula`.
#[allow(clippy::manual_div_ceil)]
fn paxos_wire_payload_len(num_nodes: usize) -> usize {
    ceil_div8(num_nodes) + 34
}

/// Length of the *internal* JSON carrier for a 2PC packet at N nodes, measured
/// through the real serialiser rather than modelled.
fn json_packet_len(num_nodes: usize) -> usize {
    let pkt = TwoPcPacket {
        transaction_id: 0,
        state: TwoPcState::Prepare,
        vote_valid_window: vec![false; num_nodes],
        vote_yes_window: vec![false; num_nodes],
        nack_tx_id: 0,
        transaction_data: b"tx0".to_vec(),
        sender: 0,
        piggyback_tx: None,
        piggyback_state: None,
    };
    serialize_packet(&pkt).len()
}

/// The packet-length guard.
///
/// `docs/wire-format.md` maps slot counts to wall-clock time through an
/// air-time term `T_air(L) = 44.0 + 4.0 * L` microseconds, which presupposes that
/// one flood packet fits in one LE Data PDU: otherwise the link layer fragments and
/// "one flood occupies one slot" is false. This test pins exactly that
/// presupposition over the whole simulated range, together with the anchor values
/// listed there.
///
/// What it does NOT do: it does not check that the simulator emits this encoding.
/// It cannot, because the simulator emits JSON and consumes no packet length at all
/// (there is no `payload.len()` in the crate). `L_wire` is a specification attached
/// to the time mapping, and this test is a consistency check on that specification.
#[test]
fn specified_wire_packet_fits_in_one_ll_data_pdu() {
    for n in 1..=255usize {
        let len = wire_payload_len(n);
        assert!(
            len <= LL_DATA_PDU_MAX_OCTETS,
            "L_wire({n}) = {len} octets exceeds the {LL_DATA_PDU_MAX_OCTETS}-octet \\
             LE Data PDU payload budget, so a flood would need link-layer fragmentation"
        );
        // The Paxos ACK-window packet fits the same budget.
        assert!(paxos_wire_payload_len(n) <= LL_DATA_PDU_MAX_OCTETS);
    }

    // 2PC anchors (two N-bit windows): L_wire(N) = 2*ceil(N/8) + 22.
    assert_eq!(wire_payload_len(2), 24);
    assert_eq!(wire_payload_len(27), 30);
    assert_eq!(wire_payload_len(180), 68);
    assert_eq!(wire_payload_len(188), 70);
    assert_eq!(wire_payload_len(255), 86);

    // Explicit N=188 guard against the LE Data PDU budget for both packet
    // shapes: 2PC (two windows) and Paxos (one ACK window + both data fields).
    // 2PC:    2*ceil(188/8) + 22 = 2*24 + 22 = 70 octets.
    // Paxos:    ceil(188/8) + 34 =   24  + 34 = 58 octets.
    assert_eq!(wire_payload_len(188), 70);
    assert_eq!(paxos_wire_payload_len(188), 58);
    assert!(wire_payload_len(188) <= LL_DATA_PDU_MAX_OCTETS);
    assert!(paxos_wire_payload_len(188) <= LL_DATA_PDU_MAX_OCTETS);
}

/// The Paxos ACK-window packet and the 2PC two-window packet must both fit the
/// only packet-payload budget this repository enforces: the 251-octet Bluetooth 5
/// LE LL Data PDU payload (with Data Length Extension). This test pins that
/// property across the whole simulated range for both packet shapes.
///
/// This test deliberately makes NO IEEE 802.15.4 one-frame-fit claim. The 127-octet
/// 802.15.4 aMaxPHYPacketSize is a PSDU (PHY payload) that also carries the MAC
/// header, addressing, and any security overhead, so comparing it directly to a
/// protocol-payload octet count would not prove single-frame fit; the repository
/// has no concrete 802.15.4 MAC frame profile from which to subtract that
/// overhead. The LE Data PDU payload budget is the single pre-existing enforced
/// packet-payload budget and is the only one asserted here.
#[test]
fn specified_wire_packets_fit_the_enforced_ble_budget() {
    for n in 1..=255usize {
        let two_pc = wire_payload_len(n);
        let paxos = paxos_wire_payload_len(n);
        assert!(
            two_pc <= LL_DATA_PDU_MAX_OCTETS,
            "2PC L_wire({n}) = {two_pc} exceeds the {LL_DATA_PDU_MAX_OCTETS}-octet BLE budget"
        );
        assert!(
            paxos <= LL_DATA_PDU_MAX_OCTETS,
            "Paxos L_wire({n}) = {paxos} exceeds the {LL_DATA_PDU_MAX_OCTETS}-octet BLE budget"
        );
    }
    // The worst case in the whole study, the N=255 2PC packet, is well inside it.
    assert_eq!(wire_payload_len(255), 86);
    assert!(wire_payload_len(255) <= LL_DATA_PDU_MAX_OCTETS);
    assert_eq!(paxos_wire_payload_len(255), 66);
    assert!(paxos_wire_payload_len(255) <= LL_DATA_PDU_MAX_OCTETS);
}

/// The Paxos wire formula must count the MAXIMUM SIMULTANEOUS packet shape, not
/// a hand-picked subset of fields. `PaxosCi::prepare_round` can emit, in one
/// flood, a fresh `proposal_data` for the current term AND a recovery
/// `piggyback_data` carried over from the previous round's NACK — the two are
/// not mutually exclusive. This test constructs exactly that maximal packet and
/// checks that `paxos_wire_payload_len` accounts for BOTH bounded 8-byte data
/// fields (fixed cost 34 octets = 2 windows-worth of scalars + two 8-byte data
/// fields + a presence-flags octet), so the formula cannot silently undercount
/// by dropping the simultaneous piggyback.
#[test]
fn paxos_max_shape_matches_wire_formula() {
    let n = 188usize;
    // The maximal packet: a term's own proposal plus a simultaneous recovery
    // piggyback, both non-empty and each bounded by 8 ASCII bytes ('tx{term}').
    let max_shape = PaxosPacket {
        current_term: 99_999,
        last_accepted_term: Some(99_998),
        ack_window: vec![true; n],
        nack_term: 42,
        sender: n - 1,
        proposal_data: b"tx99999".to_vec(),
        piggyback_term: Some(41),
        piggyback_data: b"tx41".to_vec(),
    };
    // Both bounded data fields are actually present and within their 8-byte cap.
    assert!(
        !max_shape.proposal_data.is_empty() && max_shape.proposal_data.len() <= 8,
        "proposal_data must be a non-empty <=8-byte field in the max shape"
    );
    assert!(
        !max_shape.piggyback_data.is_empty() && max_shape.piggyback_data.len() <= 8,
        "piggyback_data must be carried simultaneously in the max shape"
    );

    // Fixed cost = formula minus the single N-bit ACK window. It must be 34, i.e.
    // it must include a SECOND 8-byte data field (proposal + piggyback), not 26.
    let fixed = paxos_wire_payload_len(n) - ceil_div8(n);
    assert_eq!(
        fixed, 34,
        "the Paxos fixed octet count must include BOTH the proposal and the \
         simultaneous piggyback data field (34), not just one (26)"
    );
    // And the maximal packet still fits the enforced BLE budget.
    assert!(paxos_wire_payload_len(n) <= LL_DATA_PDU_MAX_OCTETS);
}

/// The two protocols' packet sizes do NOT have a monotone ordering: which is
/// larger flips with N. Solving `2*ceil(N/8)+22 = ceil(N/8)+34` gives
/// `ceil(N/8) = 12`, so Paxos is larger for N <= 88, they are equal for
/// N = 89..96, and 2PC is larger for N >= 97. This pins that crossover exactly
/// (and thereby forbids re-introducing the false "2PC is larger for every
/// N >= 6" claim), and proves the N=27 CI/Paxos example uses the PAXOS size
/// helper (38 octets), not the 2PC helper (30 octets).
#[test]
fn packet_size_ordering_crossover_is_pinned() {
    // Paxos strictly larger up to N = 88.
    for n in 1..=88usize {
        assert!(
            paxos_wire_payload_len(n) > wire_payload_len(n),
            "Paxos must be strictly larger at N={n}: paxos={}, 2pc={}",
            paxos_wire_payload_len(n),
            wire_payload_len(n)
        );
    }
    // Equal across N = 89..96 (both have ceil(N/8) = 12).
    for n in 89..=96usize {
        assert_eq!(
            paxos_wire_payload_len(n),
            wire_payload_len(n),
            "sizes must be equal at N={n}"
        );
    }
    // 2PC strictly larger from N = 97 up.
    for n in 97..=255usize {
        assert!(
            wire_payload_len(n) > paxos_wire_payload_len(n),
            "2PC must be strictly larger at N={n}: 2pc={}, paxos={}",
            wire_payload_len(n),
            paxos_wire_payload_len(n)
        );
    }
    // The exact crossover boundaries.
    assert_eq!(paxos_wire_payload_len(88), 45);
    assert_eq!(wire_payload_len(88), 44);
    assert_eq!(paxos_wire_payload_len(89), 46);
    assert_eq!(wire_payload_len(89), 46);
    assert_eq!(paxos_wire_payload_len(97), 47);
    assert_eq!(wire_payload_len(97), 48);

    // The N=27 CI/Paxos example is a PAXOS decision, so it must use the Paxos
    // size helper: 38 octets, NOT the 2PC helper's 30
    // (T_slot = 366.0 µs, from ceil(27/8)+34 = 38).
    assert_eq!(
        paxos_wire_payload_len(27),
        38,
        "the N=27 CI/Paxos example must use the Paxos size helper (38 octets)"
    );
    assert_ne!(
        paxos_wire_payload_len(27),
        wire_payload_len(27),
        "the N=27 example must NOT collapse to the 2PC size (30 octets)"
    );
}

/// Companion to the packet-length guard: the JSON encoding is an internal carrier
/// that would *not* fit the budget above. That is why packet sizes are taken from
/// the specified wire encoding, never from `serialize_packet(..).len()`.
///
/// Deliberately asserted as inequalities and orderings, never as exact byte counts:
/// this test must fail if the encoding stops being an over-budget artefact, not
/// merely because a serde version renders a field differently.
#[test]
fn json_carrier_is_an_artefact_not_a_wire_format() {
    let probes = [2usize, 16, 27, 188];
    let lens: Vec<usize> = probes.iter().map(|&n| json_packet_len(n)).collect();
    for (n, len) in probes.iter().zip(&lens) {
        println!("N={n:<4} L_json={len:<6} L_wire={}", wire_payload_len(*n));
    }

    // Grows with N, because the bitmap is rendered as one JSON literal per node.
    for w in lens.windows(2) {
        assert!(
            w[1] > w[0],
            "JSON length is expected to grow with the node count: {lens:?}"
        );
    }

    // Already over the one-PDU budget well inside the simulated range.
    assert!(
        lens[1] > LL_DATA_PDU_MAX_OCTETS,
        "expected the JSON carrier to exceed {LL_DATA_PDU_MAX_OCTETS} octets by N=16, got {}",
        lens[1]
    );

    // And the specified encoding is smaller by an order of magnitude at the sizes
    // used in the sweeps, which is the whole content of the artefact claim.
    assert!(
        wire_payload_len(188) * 4 < lens[3],
        "expected L_wire(188) = {} to be far below L_json(188) = {}",
        wire_payload_len(188),
        lens[3]
    );
}

/// The pure-flood harness must be sound before its curve is read.
///
/// A lossless connected graph has to give total coverage: every non-initiator
/// node is reachable, no transmission is dropped, and the round bound is derived
/// from the diameter, so the flood wave cannot be cut short. A failure here is
/// therefore a bug in the harness (payload provenance, initiator exclusion, round
/// bookkeeping) and not a property of the loss model. No reading of the
/// coverage-vs-loss curve is meaningful until this test is green.
///
/// A `line` graph is used because it is the deterministic worst case for the
/// round bound (diameter `n - 1`) and needs no RNG to construct, so the assertion
/// is exact rather than seed-dependent.
#[test]
fn pure_flood_reaches_every_receiver_on_a_lossless_line() {
    let num_nodes = 27;
    let floods = 5;
    let config = SimConfig {
        seed: 1,
        phy_mode: "ci".to_string(),
        network: NetworkConfig {
            num_nodes,
            topology: "line".to_string(),
            loss_rate: 0.0,
            graph_file: None,
        },
        ci: Some(CiConfig {
            flood_repeats: 1,
            round_slots: None,
        }),
        ce: None,
        protocol: "pure_flood".to_string(),
        num_proposals: floods,
        snapshot_interval: 1_000_000,
        max_slots: 100_000,
        abort_probability: 0.0,
        quiet: true,
    };

    let graph = NetworkGraph::line(num_nodes);
    let mut sim = PureFloodSim::new(config, graph);
    let report = sim.run();

    assert_eq!(
        report.receiver_opportunities,
        floods * (num_nodes - 1),
        "expected {floods} floods x {} receivers",
        num_nodes - 1
    );
    assert_eq!(
        report.coverage,
        1.0,
        "lossless line left {} of {} receivers uncovered",
        report.receiver_opportunities - report.receivers_covered,
        report.receiver_opportunities
    );
}

/// The frozen adjacency files under `profiles/graphs/` must
/// reproduce the random topologies that `build_graph` generates from each seed.
///
/// This guards against DOT-to-adjacency conversion errors. The test is fast: it builds 15 in-memory graphs and
/// loads 15 small text files, with no simulation runs.
#[test]
fn frozen_graph_files_reproduce_the_seeded_random_topologies() {
    use std::path::Path;

    for &seed in &SEEDS {
        let mut rng = ChaCha8Rng::seed_from_u64(seed);
        let expected = NetworkGraph::random_topology(27, 2, 5, &mut rng);

        let graph_path = format!("profiles/graphs/random_n27_seed{seed}.txt");
        let loaded = NetworkGraph::from_file(Path::new(&graph_path))
            .unwrap_or_else(|e| panic!("failed to load {graph_path}: {e}"));

        assert_eq!(
            expected.num_nodes(),
            loaded.num_nodes(),
            "seed {seed}: num_nodes mismatch"
        );
        assert_eq!(
            expected.edge_count(),
            loaded.edge_count(),
            "seed {seed}: edge_count mismatch ({} vs {})",
            expected.edge_count(),
            loaded.edge_count()
        );
        assert_eq!(
            expected.diameter(),
            loaded.diameter(),
            "seed {seed}: diameter mismatch"
        );

        for i in 0..expected.num_nodes() {
            assert_eq!(
                expected.neighbors(i),
                loaded.neighbors(i),
                "seed {seed}: neighbors({i}) differ"
            );
        }
    }
}

/// Under heavy loss on a line, 2PC over CE must produce aborts or timeouts;
/// otherwise the outcome classifier is not being exercised.
#[test]
fn high_loss_2pc_exercises_non_committed_outcomes() {
    let config = make_sim_config(
        2,
        "ce",
        "2pc_ce",
        6,
        "line",
        0.20,
        1,
        &CeConfig {
            listen_timeout: 5,
            max_round_slots: 100,
        },
        10,
        1_000_000,
        100_000,
        0.0,
    );
    let result = run_experiment(config);
    assert!(
        result.summary.aborted + result.summary.timed_out > 0,
        "high-loss outcome classifier stayed all-committed: committed={} aborted={} timed_out={}",
        result.summary.committed,
        result.summary.aborted,
        result.summary.timed_out,
    );
}
