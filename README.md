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
                      └──────┬─────────┬──────────┘
               TASK_ASSIGN   │         │  TASK_ASSIGN
                   ┌─────────┘         └─────────┐
                   ▼                              ▼
       ┌───────────────────┐        ┌───────────────────┐
       │   Worker A (JVM1) │        │   Worker C (JVM3) │
       │   ExecutorService │        │   ExecutorService │
       └─────────┬─────────┘        └─────────┬─────────┘
    RESULT_RETURN│    ┌───────────────────┐    │
                 └───▶│   Worker B (JVM2) │◀───┘
                      │   ExecutorService │
                      └───────────────────┘
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
- Java 11+
- Python 3 + matplotlib + numpy (for graphs only)

### Compile
```powershell
javac -d out (Get-ChildItem -Path src -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName)
```

### Sequential Baseline
```powershell
java -cp out core.SequentialRunner 1920 1080 5        # brute force
java -cp out core.SequentialRunner 1920 1080 5 bvh    # with BVH
```

### Distributed Parallel (start master first, then workers)
```powershell
# Window 1 — Master (3 workers, 8 threads each, 1920x1080)
java -cp out master.MasterNode 3 1920 1080 5 8

# Windows 2, 3, 4 — Workers
java -cp out worker.WorkerNode localhost 5001
```

### Correctness Verification
```powershell
java -cp out core.CorrectnessChecker output\seq_960x540_d5.png output\par_960x540_d5.png
```

### Full Benchmark Suite
```powershell
.\benchmark_sequential.ps1          # sequential baselines (all 3 sizes)
.\benchmark_parallel.ps1            # 11 parallel configurations (auto-launches workers)
python analyze_results.py           # compute S(p), E(p), generate graphs
```

---

## Performance Results

### Sequential Baseline

| Resolution | Time (ms) | BVH (ms) | BVH Speedup |
|-----------|-----------|----------|-------------|
| 480×270   | 656       | 574      | 1.14×       |
| 960×540   | 1,746     | 1,462    | 1.19×       |
| 1920×1080 | 6,221     | 5,285    | 1.18×       |

### Parallel Speedup (1920×1080)

| Config | T_par (ms) | S(p) | E(p) |
|--------|-----------|------|------|
| 3w × 1t | 2,687 | 2.32× | 0.772 |
| 3w × 2t | 2,268 | 2.74× | 0.457 |
| 1w × 8t | 2,160 | 2.88× | 0.360 |
| 3w × 4t | 2,069 | 3.01× | 0.251 |
| 2w × 8t | 1,885 | 3.30× | 0.206 |
| **3w × 8t** | **1,638** | **3.80×** | **0.158** |

### Amdahl's Law

Measured parallel fraction **f = 0.769** (sequential fraction = 23.1%).
Theoretical max speedup at 1920×1080: **S_max = 4.33×**.
Achieved speedup of 3.80× is 87.8% of the theoretical maximum.

---

## Work Division

| Component | Owner |
|-----------|-------|
| `common/`, `core/RayTracer.java`, `core/BVH.java` | Amna Akhtar Nawabi (462939) |
| `core/SequentialRunner.java`, `core/CorrectnessChecker.java` | Amna Akhtar Nawabi (462939) |
| `benchmark_*.ps1`, `analyze_results.py`, project report | Amna Akhtar Nawabi (462939) |
| `worker/WorkerNode.java`, `worker/TileRenderer.java` | Sana Khan Khitran (464597) |
| `master/MasterNode.java`, `master/WorkPartitioner.java`, `master/ResultAggregator.java` | Attiqa Bano (473781) |

---

## Team

| Member | Roll No. | Role |
|--------|----------|------|
| Amna Akhtar Nawabi | 462939 | Ray Tracer Core, BVH, Sequential Baseline, Benchmarking & Analysis, Report |
| Sana Khan Khitran | 464597 | Worker Node, Intra-node Multi-threading |
| Attiqa Bano | 473781 | Master Node, Work Partitioning, Result Aggregation |

**Course:** CS-347 Parallel & Distributed Computing · **Instructor:** Dr. Fahad Ahmed Satti · **Due:** 15 May 2026
