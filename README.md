# ParallelRayTracer

> **Distributed Ray Tracing Engine — CS-347 Parallel & Distributed Computing**
> National University of Sciences & Technology (NUST) · SEECS · Spring 2026

A fully distributed, multi-threaded ray tracer built in Java. The system renders photorealistic 3D scenes by distributing pixel computation across multiple independent JVM worker processes coordinated by a master node over TCP sockets.

---

## Overview

- Renders a 3D scene with Phong shading, shadow rays, and recursive reflections (depth 5)
- Distributes image rows across **3 independent worker JVM processes** via TCP sockets
- Uses **Java ExecutorService thread pools** inside each worker for intra-node parallelism
- Includes a **BVH (Bounding Volume Hierarchy)** optimization for O(log n) ray-object intersection
- Measures speedup, efficiency, and validates against **Amdahl's Law**
- Automated benchmarking scripts and Python analysis generate all performance graphs

---

## Architecture

```
                      ┌─────────────────────────┐
                      │       Master Process      │
                      │  WorkPartitioner          │
                      │  ResultAggregator         │
                      └──────┬────┬────┬──────────┘
               TASK_ASSIGN   │    │    │   TASK_ASSIGN
                   ┌─────────┘    │    └─────────┐
                   ▼              ▼              ▼
       ┌───────────────┐  ┌───────────────┐  ┌───────────────┐
       │ Worker A JVM1 │  │ Worker B JVM2 │  │ Worker C JVM3 │
       │ ExecutorSvc   │  │ ExecutorSvc   │  │ ExecutorSvc   │
       └───────┬───────┘  └───────┬───────┘  └───────┬───────┘
               │   RESULT_RETURN  │   RESULT_RETURN   │
               └──────────────────▶ Master ◀──────────┘

All communication: TCP sockets · No inter-worker communication
```

### Message Protocol

| Message | Direction | Payload |
|---------|-----------|---------|
| `CONNECT (0x00)` | Worker → Master | Handshake |
| `TASK_ASSIGN (0x01)` | Master → Worker | SceneConfig + Tile + nThreads |
| `RESULT_RETURN (0x02)` | Worker → Master | Tile + pixel buffer |
| `SHUTDOWN (0x03)` | Master → Worker | — |

**Coordination:** Barrier synchronization — master dispatches all tiles upfront, waits for all results, then assembles the final image.

---

## Project Structure

```
ParallelRayTracer/
├── src/
│   ├── common/          Vec3, Ray, Camera, Sphere, Plane, Light, SceneConfig, Tile, MessageProtocol
│   ├── core/            RayTracer, BVH, SequentialRunner, CorrectnessChecker
│   ├── worker/          WorkerNode, TileRenderer
│   └── master/          MasterNode, WorkPartitioner, ResultAggregator
├── results/
│   ├── sequential_benchmark.csv
│   ├── parallel_benchmark.csv
│   ├── speedup_analysis.csv
│   └── graphs/          6 performance graphs (PNG)
├── output/              Rendered PNG images
├── benchmark_sequential.ps1
├── benchmark_parallel.ps1
└── analyze_results.py
```

---

## How to Run

### Prerequisites
- Java 17+
- Python 3 + matplotlib + numpy + pandas (for graphs only)

### Compile
```powershell
.\compile.bat
```
Or manually in PowerShell:
```powershell
javac -d out (Get-ChildItem -Path src -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName)
```

---

### Sequential Baseline
```powershell
java -cp out core.SequentialRunner 1920 1080 5        # brute force
java -cp out core.SequentialRunner 1920 1080 5 bvh    # with BVH acceleration
```

---

### Distributed Parallel

> **Important:** Start the master first, then launch all worker terminals within 2 minutes.

Open 4 separate terminals:

```powershell
# Terminal 1 — Master
java -cp out master.MasterNode 3 1920 1080 5 8

# Terminal 2 — Worker
java -cp out worker.WorkerNode localhost 5001

# Terminal 3 — Worker
java -cp out worker.WorkerNode localhost 5001

# Terminal 4 — Worker
java -cp out worker.WorkerNode localhost 5001
```

The master will print `worker 0 connected`, `worker 1 connected`, `worker 2 connected` and begin rendering automatically once all workers are connected.

---

### Configuring Worker Count

Change the **first argument** of `MasterNode` to set the number of workers. Launch exactly that many worker terminals.

```powershell
# 1 worker (8 threads)
java -cp out master.MasterNode 1 1920 1080 5 8
java -cp out worker.WorkerNode localhost 5001

# 2 workers (8 threads each)
java -cp out master.MasterNode 2 1920 1080 5 8
java -cp out worker.WorkerNode localhost 5001   # Terminal 2
java -cp out worker.WorkerNode localhost 5001   # Terminal 3

# 3 workers (8 threads each) — default configuration
java -cp out master.MasterNode 3 1920 1080 5 8
java -cp out worker.WorkerNode localhost 5001   # Terminal 2
java -cp out worker.WorkerNode localhost 5001   # Terminal 3
java -cp out worker.WorkerNode localhost 5001   # Terminal 4
```

**Arguments for MasterNode:**
```
java -cp out master.MasterNode <workers> <width> <height> <depth> <threads/worker>
```

---

### Correctness Verification

```powershell
java -cp out core.CorrectnessChecker output\seq_480x270_d5.png output\par_480x270_d5.png
java -cp out core.CorrectnessChecker output\seq_960x540_d5.png output\par_960x540_d5.png
java -cp out core.CorrectnessChecker output\seq_1920x1080_d5.png output\par_1920x1080_d5.png
```

Expected output: `OK — Both images are pixel-exact — zero mismatches`

---

### Full Benchmark Suite

```powershell
.\benchmark_sequential.ps1    # collect sequential baselines (all 3 resolutions)
.\benchmark_parallel.ps1      # run all 11 parallel configurations (auto-launches workers)
python analyze_results.py     # compute S(p), E(p), generate all 6 graphs
```

---

## Performance Results

### Sequential Baseline

| Resolution | Pixels | Time (ms) | BVH (ms) | BVH Speedup |
|-----------|--------|-----------|----------|-------------|
| 480×270   | 129,600 | 656      | 574      | 1.14×       |
| 960×540   | 518,400 | 1,746    | 1,462    | 1.19×       |
| 1920×1080 | 2,073,600 | 6,221  | 5,285    | 1.18×       |

### Parallel Speedup (1920×1080, T_seq = 6,221 ms)

| Config | p (total) | T_par (ms) | S(p) | E(p) |
|--------|-----------|-----------|------|------|
| 3w × 1t | 3 | 2,687 | 2.32× | 0.772 |
| 3w × 2t | 6 | 2,268 | 2.74× | 0.457 |
| 1w × 8t | 8 | 2,160 | 2.88× | 0.360 |
| 3w × 4t | 12 | 2,069 | 3.01× | 0.251 |
| 2w × 8t | 16 | 1,885 | 3.30× | 0.206 |
| **3w × 8t** | **24** | **1,638** | **3.80×** | **0.158** |

### Amdahl's Law

- Measured parallel fraction: **f = 0.769**
- Sequential fraction: **23.1%**
- Theoretical maximum speedup at 1920×1080: **S_max = 4.33×**
- Achieved: **3.80× = 87.8% of theoretical maximum**

### Strong Scaling (3 workers × 8 threads)

| Resolution | T_seq (ms) | T_par (ms) | S(p) |
|-----------|-----------|-----------|------|
| 480×270   | 656       | 520       | 1.26× |
| 960×540   | 1,746     | 897       | 1.95× |
| 1920×1080 | 6,221     | 1,638     | 3.80× |

---

## Work Division

| Component | Owner |
|-----------|-------|
| `common/`, `core/RayTracer.java`, `core/BVH.java` | Amna Akhtar Nawabi (462939) |
| `core/SequentialRunner.java`, `core/CorrectnessChecker.java` | Amna Akhtar Nawabi (462939) |
| `benchmark_*.ps1`, `analyze_results.py`, project report | Amna Akhtar Nawabi (462939) |
| `worker/WorkerNode.java`, `worker/TileRenderer.java`, project report | Sana Khan Khitran (464597) |
| `master/MasterNode.java`, `master/WorkPartitioner.java`, `master/ResultAggregator.java` | Attiqa Bano (473781) |

---

## Repository

**GitHub:** https://github.com/aanawabi/RayTracer

---

## Team

| Member | Roll No. | Contributions |
|--------|----------|--------------|
| Amna Akhtar Nawabi | 462939 | Ray Tracer Core, BVH Optimization, Sequential Baseline, Benchmarking & Analysis, Report |
| Sana Khan Khitran | 464597 | Worker Node, Intra-node Multi-threading, System Testing & Validation, Report |
| Attiqa Bano | 473781 | Master Node, Work Partitioning, Result Aggregation |

**Course:** CS-347 Parallel & Distributed Computing · **Instructor:** Dr. Fahad Ahmed Satti · **Due:** 8th May 2026