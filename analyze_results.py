"""
analyze_results.py
------------------
Reads sequential_benchmark.csv and parallel_benchmark.csv,
computes speedup, efficiency, and Amdahl's Law comparison,
then generates all required report graphs.

Run from project root:
    python analyze_results.py

Requires: pip install matplotlib numpy pandas
"""

import os
import csv
import numpy as np
import matplotlib
matplotlib.use("Agg")          # no display needed
import matplotlib.pyplot as plt
import matplotlib.ticker as mticker

RESULTS_DIR = "results"
GRAPHS_DIR  = os.path.join(RESULTS_DIR, "graphs")
SEQ_CSV     = os.path.join(RESULTS_DIR, "sequential_benchmark.csv")
PAR_CSV     = os.path.join(RESULTS_DIR, "parallel_benchmark.csv")

os.makedirs(GRAPHS_DIR, exist_ok=True)

# ── helpers ──────────────────────────────────────────────────────────────────

def read_csv(path):
    rows = []
    with open(path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            rows.append({k: (int(v) if v.isdigit() else v)
                         for k, v in row.items()})
    return rows

def amdahl(f, p_values):
    """Theoretical max speedup for parallel fraction f and p parallel units."""
    return [1.0 / ((1 - f) + f / p) for p in p_values]

def save(fig, name):
    path = os.path.join(GRAPHS_DIR, name)
    fig.savefig(path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  Saved: {path}")

# ── load data ────────────────────────────────────────────────────────────────

seq_rows = read_csv(SEQ_CSV)
par_rows = read_csv(PAR_CSV)

# Build lookup: (width, height) → T_seq (mode='seq', most recent run)
seq_times = {}
for r in seq_rows:
    if r.get("mode") == "seq" or r.get("mode") == "0":   # handle old format
        key = (int(r["width"]), int(r["height"]))
        seq_times[key] = int(r["time_ms"])               # keep last (latest run)

# Build lookup: (width, height) → T_bvh
bvh_times = {}
for r in seq_rows:
    if r.get("mode") == "bvh":
        key = (int(r["width"]), int(r["height"]))
        bvh_times[key] = int(r["time_ms"])

print("\nSequential baselines loaded:")
for k, v in sorted(seq_times.items()):
    print(f"  {k[0]}x{k[1]}  seq={v} ms  bvh={bvh_times.get(k,'—')} ms")

# Attach speedup + efficiency to every parallel row
for r in par_rows:
    key = (int(r["width"]), int(r["height"]))
    t_seq = seq_times.get(key)
    r["t_seq"]    = t_seq
    r["workers"]  = int(r["workers"])
    r["threads"]  = int(r["threads"])
    r["time_ms"]  = int(r["time_ms"])
    r["p_total"]  = r["workers"] * r["threads"]   # total parallel units
    if t_seq:
        r["speedup"]    = t_seq / r["time_ms"]
        r["efficiency"] = r["speedup"] / r["p_total"]
    else:
        r["speedup"] = r["efficiency"] = None

# ── speedup CSV ──────────────────────────────────────────────────────────────

speedup_csv = os.path.join(RESULTS_DIR, "speedup_analysis.csv")
with open(speedup_csv, "w", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["width", "height", "workers", "threads", "p_total",
                     "t_seq_ms", "t_par_ms", "speedup", "efficiency"])
    for r in par_rows:
        if r["speedup"] is not None:
            writer.writerow([
                r["width"], r["height"], r["workers"], r["threads"],
                r["p_total"], r["t_seq"], r["time_ms"],
                f"{r['speedup']:.4f}", f"{r['efficiency']:.4f}"
            ])
print(f"\nSpeedup CSV saved: {speedup_csv}")

# ── estimate parallel fraction f from measurements ───────────────────────────
# Using Amdahl: S = 1 / ((1-f) + f/p)  =>  f = (1/S - 1) / (1/p - 1)
# Average over all runs where speedup < p (theoretical limit)

f_values = []
for r in par_rows:
    if r["speedup"] and r["p_total"] > 1:
        S = r["speedup"]
        p = r["p_total"]
        denom = (1.0 / p) - 1.0
        if denom != 0:
            f_est = ((1.0 / S) - 1.0) / denom
            if 0 < f_est < 1:
                f_values.append(f_est)

f_measured = np.mean(f_values) if f_values else 0.95
print(f"\nEstimated parallel fraction f = {f_measured:.4f}  "
      f"(sequential fraction = {1-f_measured:.4f})")
S_theoretical_max = 1 / (1 - f_measured)
print(f"Theoretical max speedup = {S_theoretical_max:.2f}×")

# ── GRAPH 1: Speedup curve (vary threads, fixed 3 workers, 1920×1080) ────────

size_lg = (1920, 1080)
t_seq_lg = seq_times.get(size_lg)

if t_seq_lg:
    data3w = sorted(
        [r for r in par_rows
         if r["workers"] == 3 and (r["width"], r["height"]) == size_lg
         and r["speedup"] is not None],
        key=lambda r: r["threads"]
    )

    if data3w:
        threads_x  = [r["threads"]  for r in data3w]
        speedup_y  = [r["speedup"]  for r in data3w]
        p_range    = np.array(sorted(set(threads_x + [1, 2, 4, 8, 16])))
        amdahl_y   = amdahl(f_measured, p_range)

        fig, ax = plt.subplots(figsize=(7, 5))
        ax.plot(threads_x, speedup_y, "o-", color="#1f77b4",
                linewidth=2, markersize=7, label="Measured speedup")
        ax.plot(p_range, amdahl_y, "--", color="#d62728",
                linewidth=1.5, label=f"Amdahl bound (f={f_measured:.2f})")
        ax.plot(p_range, p_range, ":", color="gray",
                linewidth=1, label="Linear (ideal)")
        ax.set_xlabel("Threads per worker", fontsize=12)
        ax.set_ylabel("Speedup S(t)  [vs 3w×1t baseline]", fontsize=12)
        ax.set_title("Thread Scalability — 3 Workers, 1920×1080", fontsize=13)
        ax.legend(); ax.grid(True, alpha=0.3)
        ax.xaxis.set_major_locator(mticker.FixedLocator(threads_x))
        save(fig, "graph1_thread_speedup.png")

# ── GRAPH 2: Speedup curve (vary workers, fixed 8 threads, 1920×1080) ────────

if t_seq_lg:
    dataNw = sorted(
        [r for r in par_rows
         if r["threads"] == 8 and (r["width"], r["height"]) == size_lg
         and r["speedup"] is not None],
        key=lambda r: r["workers"]
    )

    if dataNw:
        workers_x = [r["workers"]  for r in dataNw]
        speedup_y = [r["speedup"]  for r in dataNw]
        p_range   = np.array(sorted(set(workers_x + [1, 2, 3, 4])))
        amdahl_y  = amdahl(f_measured, p_range * 8)   # p_total = workers × 8

        fig, ax = plt.subplots(figsize=(7, 5))
        ax.plot(workers_x, speedup_y, "s-", color="#2ca02c",
                linewidth=2, markersize=7, label="Measured speedup")
        ax.plot(p_range, amdahl_y, "--", color="#d62728",
                linewidth=1.5, label=f"Amdahl bound (f={f_measured:.2f})")
        ax.set_xlabel("Number of worker JVMs", fontsize=12)
        ax.set_ylabel(f"Speedup S(p)  [vs T_seq={t_seq_lg} ms]", fontsize=12)
        ax.set_title("Worker Scalability — 8 Threads/Worker, 1920×1080", fontsize=13)
        ax.legend(); ax.grid(True, alpha=0.3)
        ax.xaxis.set_major_locator(mticker.FixedLocator(workers_x))
        save(fig, "graph2_worker_speedup.png")

# ── GRAPH 3: Efficiency curve ────────────────────────────────────────────────

eff_data = sorted(
    [r for r in par_rows if r["efficiency"] is not None
     and (r["width"], r["height"]) == size_lg],
    key=lambda r: r["p_total"]
)

if eff_data:
    p_total_x  = [r["p_total"]   for r in eff_data]
    eff_y      = [r["efficiency"] for r in eff_data]
    labels     = [f"{r['workers']}w×{r['threads']}t" for r in eff_data]

    fig, ax = plt.subplots(figsize=(7, 5))
    bars = ax.bar(labels, eff_y, color="#9467bd", alpha=0.8, edgecolor="black")
    ax.axhline(1.0, color="#d62728", linestyle="--", linewidth=1.5, label="Perfect efficiency")
    for bar, v in zip(bars, eff_y):
        ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.01,
                f"{v:.2f}", ha="center", va="bottom", fontsize=9)
    ax.set_xlabel("Configuration (workers × threads)", fontsize=12)
    ax.set_ylabel("Efficiency E(p) = S(p)/p", fontsize=12)
    ax.set_title("Parallel Efficiency — 1920×1080", fontsize=13)
    ax.set_ylim(0, 1.2); ax.legend(); ax.grid(True, alpha=0.3, axis="y")
    save(fig, "graph3_efficiency.png")

# ── GRAPH 4: BVH before/after bar chart ──────────────────────────────────────

sizes_sorted  = [(480, 270), (960, 540), (1920, 1080)]
labels_bvh    = [f"{w}×{h}" for w, h in sizes_sorted]
seq_vals      = [seq_times.get(s, 0) for s in sizes_sorted]
bvh_vals      = [bvh_times.get(s, 0) for s in sizes_sorted]

if any(seq_vals) and any(bvh_vals):
    x  = np.arange(len(labels_bvh))
    bw = 0.35
    fig, ax = plt.subplots(figsize=(8, 5))
    b1 = ax.bar(x - bw/2, seq_vals, bw, label="Sequential (no BVH)",
                color="#1f77b4", alpha=0.85, edgecolor="black")
    b2 = ax.bar(x + bw/2, bvh_vals, bw, label="BVH optimized",
                color="#ff7f0e", alpha=0.85, edgecolor="black")
    for bar in list(b1) + list(b2):
        h = bar.get_height()
        if h > 0:
            ax.text(bar.get_x() + bar.get_width()/2, h + 50,
                    f"{h:,}", ha="center", va="bottom", fontsize=8)
    # Annotate speedup ratios
    for i, (s, b) in enumerate(zip(seq_vals, bvh_vals)):
        if s and b:
            ax.text(x[i], max(s, b) + 200, f"{s/b:.2f}×",
                    ha="center", color="#d62728", fontsize=10, fontweight="bold")
    ax.set_xlabel("Image Resolution", fontsize=12)
    ax.set_ylabel("Render Time (ms)", fontsize=12)
    ax.set_title("BVH Optimization — Render Time Before vs After", fontsize=13)
    ax.set_xticks(x); ax.set_xticklabels(labels_bvh)
    ax.legend(); ax.grid(True, alpha=0.3, axis="y")
    save(fig, "graph4_bvh_comparison.png")

# ── GRAPH 5: Strong scaling ───────────────────────────────────────────────────

strong_data = sorted(
    [r for r in par_rows
     if r["workers"] == 3 and r["threads"] == 8
     and r["speedup"] is not None],
    key=lambda r: r["width"] * r["height"]
)

if strong_data:
    pixels_x  = [r["width"] * r["height"] for r in strong_data]
    tpar_y    = [r["time_ms"] for r in strong_data]
    tseq_y    = [r["t_seq"]   for r in strong_data]
    xlabels   = [f"{r['width']}×{r['height']}" for r in strong_data]

    fig, ax = plt.subplots(figsize=(7, 5))
    ax.plot(xlabels, tseq_y, "o--", color="#1f77b4",
            linewidth=2, markersize=7, label="Sequential")
    ax.plot(xlabels, tpar_y, "s-",  color="#2ca02c",
            linewidth=2, markersize=7, label="Parallel (3w×8t)")
    ax.set_xlabel("Image Resolution", fontsize=12)
    ax.set_ylabel("Render Time (ms)", fontsize=12)
    ax.set_title("Strong Scaling — 3 Workers × 8 Threads", fontsize=13)
    ax.legend(); ax.grid(True, alpha=0.3)
    save(fig, "graph5_strong_scaling.png")

# ── GRAPH 6: Weak scaling ─────────────────────────────────────────────────────

# Weak scaling: 1w@480x270, 2w@960x270, 4w@1920x270 — constant px/worker
weak_configs = [(1, 480, 270), (2, 960, 270), (4, 1920, 270)]
weak_data = []
for (wk, wi, hi) in weak_configs:
    matches = [r for r in par_rows
               if r["workers"] == wk and r["threads"] == 8
               and r["width"] == wi and r["height"] == hi]
    if matches:
        weak_data.append((wk, matches[-1]["time_ms"]))

if weak_data:
    wk_x   = [d[0] for d in weak_data]
    time_y = [d[1] for d in weak_data]
    ideal_y = [time_y[0]] * len(wk_x)   # ideal: constant time

    fig, ax = plt.subplots(figsize=(7, 5))
    ax.plot(wk_x, time_y,  "o-", color="#8c564b",
            linewidth=2, markersize=7, label="Measured time")
    ax.plot(wk_x, ideal_y, "--", color="#d62728",
            linewidth=1.5, label="Ideal (constant time)")
    ax.set_xlabel("Number of workers", fontsize=12)
    ax.set_ylabel("Render Time (ms)", fontsize=12)
    ax.set_title("Weak Scaling — Constant Pixels per Worker", fontsize=13)
    ax.set_xticks(wk_x)
    ax.legend(); ax.grid(True, alpha=0.3)
    save(fig, "graph6_weak_scaling.png")

# ── summary printout ──────────────────────────────────────────────────────────

print("\n=== SPEEDUP SUMMARY ===")
print(f"{'Config':<22} {'Width':>6} {'T_seq':>8} {'T_par':>8} {'S(p)':>7} {'E(p)':>7}")
print("-" * 62)
for r in sorted(par_rows, key=lambda x: (x["width"]*x["height"], x["p_total"])):
    if r["speedup"] is not None:
        cfg = f"{r['workers']}w × {r['threads']}t"
        res = f"{r['width']}×{r['height']}"
        print(f"  {cfg:<12} {res:>10} {r['t_seq']:>8} {r['time_ms']:>8} "
              f"{r['speedup']:>7.2f} {r['efficiency']:>7.3f}")

print(f"\n  Measured parallel fraction f  = {f_measured:.4f}")
print(f"  Theoretical max speedup       = {S_theoretical_max:.2f}×")
print(f"\nAll graphs saved to: {GRAPHS_DIR}/")
