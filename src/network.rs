//! Network graph: adjacency representation and topology builders.

use crate::event::NodeId;
use std::collections::HashSet;

/// Undirected network graph stored as adjacency sets.
#[derive(Debug, Clone)]
pub struct NetworkGraph {
    /// `adj[i]` = set of node IDs that node `i` can hear.
    adj: Vec<HashSet<NodeId>>,
}

impl NetworkGraph {
    /// Number of nodes in the graph.
    pub fn num_nodes(&self) -> usize {
        self.adj.len()
    }

    /// Neighbours of a node (sorted NodeIds — deterministic iteration for seeded RNG).
    pub fn neighbors(&self, id: NodeId) -> Vec<NodeId> {
        let mut v: Vec<NodeId> = self.adj[id].iter().copied().collect();
        v.sort_unstable();
        v
    }

    /// BFS shortest-path diameter of the graph.
    pub fn diameter(&self) -> usize {
        let n = self.num_nodes();
        let mut max_dist = 0;
        for start in 0..n {
            let mut dist = vec![usize::MAX; n];
            dist[start] = 0;
            let mut queue = std::collections::VecDeque::new();
            queue.push_back(start);
            while let Some(u) = queue.pop_front() {
                for &v in &self.adj[u] {
                    if dist[v] == usize::MAX {
                        dist[v] = dist[u] + 1;
                        queue.push_back(v);
                    }
                }
            }
            let d = dist
                .iter()
                .filter(|&&x| x != usize::MAX)
                .copied()
                .max()
                .unwrap_or(0);
            max_dist = max_dist.max(d);
        }
        max_dist
    }

    /// Number of undirected edges.
    pub fn edge_count(&self) -> usize {
        self.adj.iter().map(|s| s.len()).sum::<usize>() / 2
    }

    /// GraphViz DOT (undirected). Open with `dot -Tpng` or any GraphViz viewer.
    pub fn to_dot(&self, name: &str) -> String {
        let mut out = format!("graph {} {{\n", sanitize_dot_id(name));
        out.push_str("  graph [overlap=false, splines=true];\n");
        out.push_str("  node [shape=circle, style=filled, fillcolor=\"#4a90d9\", fontcolor=white, fontsize=12];\n");
        out.push_str("  edge [color=\"#555555\"];\n");
        let n = self.num_nodes();
        for i in 0..n {
            out.push_str(&format!("  {i};\n"));
        }
        for i in 0..n {
            for j in self.neighbors(i) {
                if j > i {
                    out.push_str(&format!("  {i} -- {j};\n"));
                }
            }
        }
        out.push_str("}\n");
        out
    }

    /// Self-contained SVG (circular layout). No external tools required.
    pub fn to_svg(&self, size: f64) -> String {
        let n = self.num_nodes().max(1);
        let cx = size / 2.0;
        let cy = size / 2.0;
        let r = size * 0.38;
        let node_r = (size / (n as f64).sqrt() * 0.12).clamp(8.0, 22.0);

        let mut positions = Vec::with_capacity(n);
        for i in 0..n {
            let angle =
                std::f64::consts::TAU * (i as f64) / (n as f64) - std::f64::consts::FRAC_PI_2;
            positions.push((cx + r * angle.cos(), cy + r * angle.sin()));
        }

        let mut out = format!(
            r##"<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">
  <rect width="100%" height="100%" fill="#0f1115"/>
  <text x="12" y="20" fill="#aaaaaa" font-family="monospace" font-size="12">n={n} edges={} diameter={}</text>
"##,
            self.edge_count(),
            self.diameter()
        );

        for i in 0..n {
            for j in self.neighbors(i) {
                if j > i {
                    let (x1, y1) = positions[i];
                    let (x2, y2) = positions[j];
                    out.push_str(&format!(
                        r##"  <line x1="{x1:.1}" y1="{y1:.1}" x2="{x2:.1}" y2="{y2:.1}" stroke="#5a5a6e" stroke-width="1.5"/>
"##
                    ));
                }
            }
        }

        for (i, &(x, y)) in positions.iter().enumerate() {
            out.push_str(&format!(
                r##"  <circle cx="{x:.1}" cy="{y:.1}" r="{node_r:.1}" fill="#3b82f6" stroke="#93c5fd" stroke-width="1.5"/>
  <text x="{x:.1}" y="{ty:.1}" text-anchor="middle" fill="#ffffff" font-family="sans-serif" font-size="{fs:.0}" font-weight="600">{i}</text>
"##,
                ty = y + node_r * 0.35,
                fs = (node_r * 0.9).clamp(9.0, 14.0),
            ));
        }
        out.push_str("</svg>\n");
        out
    }

    /// Write DOT + SVG under `dir` (created if needed). Returns paths written.
    pub fn export_visuals(
        &self,
        dir: &std::path::Path,
        basename: &str,
    ) -> std::io::Result<(std::path::PathBuf, std::path::PathBuf)> {
        std::fs::create_dir_all(dir)?;
        let dot_path = dir.join(format!("{basename}.dot"));
        let svg_path = dir.join(format!("{basename}.svg"));
        std::fs::write(&dot_path, self.to_dot(basename))?;
        std::fs::write(&svg_path, self.to_svg(640.0))?;
        Ok((dot_path, svg_path))
    }

    // ── Topology builders ──────────────────────────────────────────

    /// Fully connected mesh: every node can hear every other node.
    pub fn full_mesh(n: usize) -> Self {
        let mut adj = vec![HashSet::new(); n];
        for (i, row) in adj.iter_mut().enumerate() {
            for j in 0..n {
                if i != j {
                    row.insert(j);
                }
            }
        }
        Self { adj }
    }

    /// Linear chain: node i <-> node i+1.
    pub fn line(n: usize) -> Self {
        let mut adj = vec![HashSet::new(); n];
        for i in 0..n.saturating_sub(1) {
            adj[i].insert(i + 1);
            adj[i + 1].insert(i);
        }
        Self { adj }
    }

    /// Star: node 0 is the hub, connected to all others.
    pub fn star(n: usize) -> Self {
        let mut adj = vec![HashSet::new(); n];
        for i in 1..n {
            adj[0].insert(i);
            adj[i].insert(0);
        }
        Self { adj }
    }

    /// 2D grid (rows × cols). `n = rows * cols` total nodes.
    pub fn grid(rows: usize, cols: usize) -> Self {
        let n = rows * cols;
        let mut adj = vec![HashSet::new(); n];
        for r in 0..rows {
            for c in 0..cols {
                let id = r * cols + c;
                if c + 1 < cols {
                    let right = r * cols + c + 1;
                    adj[id].insert(right);
                    adj[right].insert(id);
                }
                if r + 1 < rows {
                    let below = (r + 1) * cols + c;
                    adj[id].insert(below);
                    adj[below].insert(id);
                }
            }
        }
        Self { adj }
    }

    /// Load from a simple adjacency-list text file.
    /// Format: first line = N (number of nodes), then lines "u v" for each edge.
    pub fn from_file(path: &std::path::Path) -> Result<Self, Box<dyn std::error::Error>> {
        let text = std::fs::read_to_string(path)?;
        let mut lines = text.lines();
        let n: usize = lines.next().ok_or("Empty graph file")?.trim().parse()?;
        let mut adj = vec![HashSet::new(); n];
        for line in lines {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() < 2 {
                continue;
            }
            let u: usize = parts[0].parse()?;
            let v: usize = parts[1].parse()?;
            if u < n && v < n {
                adj[u].insert(v);
                adj[v].insert(u);
            }
        }
        Ok(Self { adj })
    }

    /// Scale-free topology (Barabási-Albert model).
    /// `m` is the number of edges to attach from a new node to existing nodes.
    pub fn scale_free(n: usize, m: usize, rng: &mut rand_chacha::ChaCha8Rng) -> Self {
        use rand::seq::SliceRandom;
        let mut adj = vec![HashSet::new(); n];
        if n <= m {
            return Self::full_mesh(n);
        }

        let mut repeated_nodes = Vec::new();
        // Initial complete graph of size m
        for (i, row) in adj.iter_mut().take(m).enumerate() {
            for j in 0..m {
                if i != j {
                    row.insert(j);
                }
            }
            for _ in 0..(m - 1) {
                repeated_nodes.push(i);
            }
        }

        for i in m..n {
            let mut targets = HashSet::new();
            while targets.len() < m {
                let candidate = *repeated_nodes.choose(rng).unwrap();
                targets.insert(candidate);
            }
            // Sort before iterating: `repeated_nodes` is index-sampled by
            // `choose`, so its ORDER feeds preferential attachment for every
            // later node. HashSet iteration order is randomized per process
            // (SipHash with a random seed), which would make the graph -- and
            // therefore every metric -- differ between runs of the same seed.
            let mut targets: Vec<NodeId> = targets.into_iter().collect();
            targets.sort_unstable();
            for target in targets {
                adj[i].insert(target);
                adj[target].insert(i);
                repeated_nodes.push(target);
            }
            for _ in 0..m {
                repeated_nodes.push(i);
            }
        }
        Self { adj }
    }

    /// Random topology ensuring connectivity.
    /// min_neighbors and max_neighbors dictate the degree bounds.
    pub fn random_topology(
        n: usize,
        min_neighbors: usize,
        max_neighbors: usize,
        rng: &mut rand_chacha::ChaCha8Rng,
    ) -> Self {
        use rand::Rng;
        let mut adj = vec![HashSet::new(); n];

        // Ensure connected: attach node i to a random node < i
        for i in 1..n {
            let neighbor = rng.gen_range(0..i);
            adj[i].insert(neighbor);
            adj[neighbor].insert(i);
        }

        // Add additional random edges
        for i in 0..n {
            let num_neighbors = rng.gen_range(min_neighbors..=max_neighbors);
            while adj[i].len() < num_neighbors {
                let neighbor = rng.gen_range(0..n);
                if neighbor != i {
                    adj[i].insert(neighbor);
                    adj[neighbor].insert(i);
                }
            }
        }
        Self { adj }
    }

    /// Ring topology: a line with the ends connected.
    pub fn ring(n: usize) -> Self {
        let mut graph = Self::line(n);
        if n > 2 {
            graph.adj[0].insert(n - 1);
            graph.adj[n - 1].insert(0);
        }
        graph
    }

    /// Tree topology with a given branching factor.
    pub fn tree(n: usize, branching_factor: usize) -> Self {
        let mut adj = vec![HashSet::new(); n];
        for i in 0..n {
            for j in 1..=branching_factor {
                let child = i * branching_factor + j;
                if child < n {
                    adj[i].insert(child);
                    adj[child].insert(i);
                }
            }
        }
        Self { adj }
    }

    /// Partial mesh: randomly connect nodes with connection_probability, ensuring baseline connectivity.
    pub fn partial_mesh(n: usize, prob: f64, rng: &mut rand_chacha::ChaCha8Rng) -> Self {
        use rand::Rng;
        let mut adj = vec![HashSet::new(); n];

        // Baseline tree to ensure connectivity
        for i in 1..n {
            let neighbor = rng.gen_range(0..i);
            adj[i].insert(neighbor);
            adj[neighbor].insert(i);
        }

        // Randomly add edges based on probability
        for i in 0..n {
            for j in (i + 1)..n {
                if rng.gen::<f64>() < prob {
                    adj[i].insert(j);
                    adj[j].insert(i);
                }
            }
        }
        Self { adj }
    }
}

fn sanitize_dot_id(name: &str) -> String {
    let s: String = name
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() { c } else { '_' })
        .collect();
    if s.is_empty() || s.chars().next().unwrap().is_ascii_digit() {
        format!("g_{s}")
    } else {
        s
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scale_free_is_deterministic_for_a_fixed_seed() {
        // Regression guard: `scale_free` used to iterate a HashSet when wiring each
        // new node, and HashSet order is randomized per process. That made the graph
        // -- hence diameter, latency, and energy -- differ between runs of the same
        // seed. Rebuilding from one seed must give byte-identical structure.
        use rand::SeedableRng;

        let build = || {
            let mut rng = rand_chacha::ChaCha8Rng::seed_from_u64(11);
            NetworkGraph::scale_free(27, 2, &mut rng)
        };
        let a = build();
        let b = build();

        assert_eq!(a.diameter(), b.diameter(), "diameter is seed-unstable");
        assert_eq!(
            a.edge_count(),
            b.edge_count(),
            "edge count is seed-unstable"
        );
        for i in 0..a.num_nodes() {
            assert_eq!(
                a.neighbors(i),
                b.neighbors(i),
                "adjacency of node {i} is seed-unstable"
            );
        }
    }

    #[test]
    fn full_mesh_diameter_is_one() {
        assert_eq!(NetworkGraph::full_mesh(10).diameter(), 1);
    }

    #[test]
    fn line_diameter_is_n_minus_one() {
        assert_eq!(NetworkGraph::line(5).diameter(), 4);
    }

    #[test]
    fn star_diameter_is_two() {
        assert_eq!(NetworkGraph::star(6).diameter(), 2);
    }

    #[test]
    fn grid_3x3_diameter_is_four() {
        assert_eq!(NetworkGraph::grid(3, 3).diameter(), 4);
    }

    #[test]
    fn seeded_partial_mesh_is_deterministic() {
        use rand::SeedableRng;
        let mut a = rand_chacha::ChaCha8Rng::seed_from_u64(42);
        let mut b = rand_chacha::ChaCha8Rng::seed_from_u64(42);
        let g1 = NetworkGraph::partial_mesh(12, 0.3, &mut a);
        let g2 = NetworkGraph::partial_mesh(12, 0.3, &mut b);
        assert_eq!(g1.edge_count(), g2.edge_count());
        assert_eq!(g1.diameter(), g2.diameter());
        for i in 0..12 {
            assert_eq!(g1.neighbors(i), g2.neighbors(i));
        }
    }

    #[test]
    fn to_dot_contains_edges() {
        let g = NetworkGraph::line(3);
        let dot = g.to_dot("line3");
        assert!(dot.contains("0 -- 1"));
        assert!(dot.contains("1 -- 2"));
    }
}
