use crate::config::CeConfig;
use crate::run::{make_sim_config, run_experiment, RunSummary};
use crate::sweep_config::SweepConfig;
use indicatif::{MultiProgress, ProgressBar, ProgressStyle};
use rayon::prelude::*;
use std::fs;
use std::path::Path;

pub fn run_sweep(sweep_cfg: SweepConfig) {
    let out_dir = Path::new(&sweep_cfg.output_dir);
    fs::create_dir_all(out_dir).unwrap();
    let csv_path = out_dir.join("sweep_summary.csv");

    let mut csv = fs::File::create(&csv_path).unwrap();
    use std::io::Write;
    writeln!(
        csv,
        "seed,nodes,topology,loss_rate,flood_repeats,protocol,phy,diameter,edges,proposals,committed,aborted,timed_out,avg_latency,listen,flood,sleep,nacks,piggybacks,ce_timeouts,end_slot"
    ).unwrap();

    let ce_cfg = CeConfig {
        listen_timeout: sweep_cfg.listen_timeout,
        max_round_slots: sweep_cfg.max_round_slots,
    };

    let mut count = 0;
    let mut tasks = Vec::new();

    for &seed in &sweep_cfg.seeds {
        for &nodes in &sweep_cfg.num_nodes {
            for topo in &sweep_cfg.topologies {
                for &loss in &sweep_cfg.loss_rates {
                    for &repeats in &sweep_cfg.flood_repeats {
                        for proto in &sweep_cfg.ci_protocols {
                            let cfg = make_sim_config(
                                seed,
                                "ci",
                                proto,
                                nodes,
                                topo,
                                loss,
                                repeats,
                                &ce_cfg,
                                sweep_cfg.num_proposals,
                                sweep_cfg.snapshot_interval,
                                sweep_cfg.max_slots,
                                sweep_cfg.abort_probability,
                            );
                            tasks.push(cfg);
                        }
                    }

                    for proto in &sweep_cfg.ce_protocols {
                        let cfg = make_sim_config(
                            seed,
                            "ce",
                            proto,
                            nodes,
                            topo,
                            loss,
                            1,
                            &ce_cfg,
                            sweep_cfg.num_proposals,
                            sweep_cfg.snapshot_interval,
                            sweep_cfg.max_slots,
                            sweep_cfg.abort_probability,
                        );
                        tasks.push(cfg);
                    }
                }
            }
        }
    }

    let m = MultiProgress::new();
    let pb = m.add(ProgressBar::new(tasks.len() as u64));
    pb.set_style(
        ProgressStyle::default_bar()
            .template("[{elapsed_precise}] {bar:40.cyan/blue} {pos}/{len} ({eta}) {msg}")
            .unwrap()
            .progress_chars("=>-"),
    );

    println!("Starting sweep of {} experiments...", tasks.len());

    let summaries: Vec<RunSummary> = tasks
        .into_par_iter()
        .map(|cfg| {
            let msg = format!(
                "{} N={} L={}",
                cfg.protocol, cfg.network.num_nodes, cfg.network.loss_rate
            );
            pb.set_message(msg);
            let res = run_experiment(cfg);
            pb.inc(1);
            res.summary
        })
        .collect();

    pb.finish_with_message("All experiments completed.");

    for s in &summaries {
        write_summary_row(&mut csv, s);
        count += 1;
    }

    println!("Sweep complete. Ran {} experiments.", count);
    println!("Summary written to: {}", csv_path.display());
}

fn write_summary_row(f: &mut std::fs::File, s: &RunSummary) {
    use std::io::Write;
    writeln!(
        f,
        "{},{},{},{},{},{},{},{},{},{},{},{},{},{:.2},{},{},{},{},{},{},{}",
        s.seed,
        s.num_nodes,
        s.topology,
        s.loss_rate,
        s.flood_repeats,
        s.protocol,
        s.phy_mode,
        s.diameter,
        s.edge_count,
        s.num_proposals,
        s.committed,
        s.aborted,
        s.timed_out,
        s.avg_latency_committed,
        s.total_listen,
        s.total_flood,
        s.total_sleep,
        s.nacks_sent,
        s.piggybacks_sent,
        s.ce_timeouts,
        s.end_slot
    )
    .unwrap();
}
