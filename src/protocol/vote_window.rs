//! Sender-owned sliding vote window shared by the CI Paxos ACK window and the
//! CI 2PC ternary vote windows.
//!
//! # Model
//!
//! Let `N` be the node count and `T` the packet's current term / transaction id.
//! Every vote window has exactly `N` logical positions. With
//!
//! ```text
//! window_start = T.saturating_sub(N - 1)
//! ```
//!
//! position `j` maps to term `window_start + j`. In steady state (`T >= N - 1`)
//! the window covers the `N` most recent terms including `T`. During startup
//! (`T < N - 1`) the low positions still map to real terms `0..=T` and the high
//! positions map *above* `T`; those are **padding** and never carry a vote.
//!
//! The window is sender-owned: it carries only the packet `sender`'s own view of
//! each mapped term. A receiver uses `sender` to apply every entry to that
//! sender's vote for the mapped term — never to any other node, and never to a
//! term other than the one the position maps to. This makes a later term's ACK
//! structurally incapable of backfilling an earlier term unless that earlier
//! term has its own true position in the window.

/// First term covered by an `n`-position window ending at term `t`.
#[inline]
pub fn window_start(t: u64, n: usize) -> u64 {
    t.saturating_sub((n as u64).saturating_sub(1))
}

/// Term mapped to window position `j` for an `n`-position window ending at `t`.
#[inline]
pub fn mapped_term(t: u64, n: usize, j: usize) -> u64 {
    window_start(t, n) + j as u64
}

/// Position `j` is startup padding when its mapped term is above `t`.
#[inline]
pub fn is_padding(t: u64, n: usize, j: usize) -> bool {
    mapped_term(t, n, j) > t
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn vote_window_mapping_and_startup_padding_are_correct() {
        // Startup: T=2, N=5 -> window_start saturates to 0, positions 0..=2 are
        // real terms 0,1,2 and positions 3,4 are padding above T.
        let (t, n) = (2u64, 5usize);
        assert_eq!(window_start(t, n), 0);
        assert_eq!(
            (0..n).map(|j| mapped_term(t, n, j)).collect::<Vec<_>>(),
            vec![0, 1, 2, 3, 4]
        );
        assert_eq!(
            (0..n).map(|j| is_padding(t, n, j)).collect::<Vec<_>>(),
            vec![false, false, false, true, true]
        );

        // A window ending at exactly N-1 is the first fully-populated one.
        let (t, n) = (4u64, 5usize);
        assert_eq!(window_start(t, n), 0);
        assert!((0..n).all(|j| !is_padding(t, n, j)));

        // Steady state: T=10, N=5 -> covers 6..=10, no padding.
        let (t, n) = (10u64, 5usize);
        assert_eq!(window_start(t, n), 6);
        assert_eq!(
            (0..n).map(|j| mapped_term(t, n, j)).collect::<Vec<_>>(),
            vec![6, 7, 8, 9, 10]
        );
        assert!((0..n).all(|j| !is_padding(t, n, j)));
    }

    #[test]
    fn vote_window_shifts_across_terms() {
        let n = 4usize;
        // Each increment of T slides the window by exactly one term; the last
        // position always maps to T itself.
        // Start at t = n so that both t and t-1 are past the startup
        // saturation boundary (window_start no longer clamps to 0).
        for t in (n as u64)..20u64 {
            assert_eq!(window_start(t, n), t - (n as u64 - 1));
            assert_eq!(mapped_term(t, n, n - 1), t);
            assert_eq!(mapped_term(t, n, 0), t - (n as u64 - 1));
            // Consecutive windows overlap on n-1 terms: position 0 of the
            // window at t is position 1 of the window at t-1.
            assert_eq!(mapped_term(t, n, 0), mapped_term(t - 1, n, 1));
        }
    }
}
