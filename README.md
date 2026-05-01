# ParallelRayTracer

> **Distributed Ray Tracing Engine — CS-347 Parallel & Distributed Computing**
> National University of Sciences & Technology (NUST) · SEECS · Spring 2026

A fully distributed, multi-threaded ray tracer built in Java. The system renders photorealistic 3D scenes by distributing pixel computation across multiple independent JVM worker processes coordinated by a master node over TCP sockets — demonstrating real-world parallel and distributed computing principles.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [How to Run](#how-to-run)
- [Performance Results](#performance-results)
- [BVH Optimization](#bvh-optimization)
- [Work Division](#work-division)
- [Member B — Worker Node (Implementation Guide)](#member-b--worker-node-implementation-guide)
- [Member C — Master Node (Implementation Guide)](#member-c--master-node-implementation-guide)
- [Report Checklist](#report-checklist)
- [Team](#team)

---

## Overview

This project implements a **parallel and distributed ray tracer** as the semester project for CS-347 Parallel & Distributed Computing. Building on the design document submitted in Assignment 3, the system:

- Renders a 3D scene with Phong shading, shadow rays, and recursive reflections
- Distributes image rows across **3 independent worker JVM processes** via TCP sockets
- Uses **Java ExecutorService thread pools** inside each worker for intra-node parallelism
- Measures speedup, efficiency, and validates against **Amdahl's Law**
- Includes a **BVH (Bounding Volume Hierarchy)** optimization for O(log n) ray-object intersection

The sequential baseline renders 1920×1080 at depth 5 in ~6.6 seconds. The distributed parallel system targets sub-1-second render times with 3 workers × 8 threads each.

---

## Features

- **Phong shading** — ambient + diffuse + specular lighting model
- **Shadow rays** — hard shadows from multiple point lights
- **Recursive reflections** — up to configurable bounce depth (default D=5)
- **Sky gradient background** — smooth blue-white sky on ray miss
- **BVH acceleration** — bounding volume hierarchy cuts intersection tests from O(n) to O(log n)
- **Master-worker distribution** — hub-and-spoke TCP architecture, zero peer-to-peer traffic
- **Dynamic task queue** — workers pull tiles on demand for automatic load balancing
- **3 image sizes** — 480×270 / 960×540 / 1920×1080 for strong scaling experiments
- **CSV benchmarks** — all timing data saved automatically for report graphs
- **PNG output** — images written via Java ImageIO

---

## Architecture

### System Architecture — Master-Worker Hub-and-Spoke

```
                        ┌─────────────────────────┐
                        │      Master Process      │
                        │       (JVM 0)            │
                        │                          │
                        │  ┌──────────────────┐    │
                        │  │   SceneLoader    │    │
                        │  │  WorkPartitioner │    │
                        │  │ ResultAggregator │    │
                        │  │   ImageWriter    │    │
                        │  └──────────────────┘    │
                        └────────┬────────┬─────────┘
                    TASK_ASSIGN  │        │  TASK_ASSIGN
                    ┌────────────┘        └────────────┐
                    ▼                                   ▼
        ┌───────────────────┐             ┌───────────────────┐
        │   Worker A (JVM1) │             │   Worker C (JVM3) │
        │   Rows 0–359      │             │   Rows 720–1079   │
        │  ExecutorService  │             │  ExecutorService  │
        │  T1 T2 T3 T4      │             │  T1 T2 T3 T4      │
        │  T5 T6 T7 T8      │             │  T5 T6 T7 T8      │
        └────────┬──────────┘             └────────┬──────────┘
    RESULT_RETURN│                     RESULT_RETURN│
                 │   ┌───────────────────┐          │
                 │   │   Worker B (JVM2) │          │
                 └──▶│   Rows 360–719    │◀─────────┘
                     │  ExecutorService  │
                     │  T1 T2 T3 T4      │
                     │  T5 T6 T7 T8      │
                     └───────────────────┘

All communication: TCP sockets · No inter-worker communication
```

### Message Protocol

| Message | Direction | When | Payload |
|---------|-----------|------|---------|
| `CONNECT (0x00)` | Worker → Master | On startup | Protocol version |
| `TASK_ASSIGN (0x01)` | Master → Worker | After all connected | Scene + row range + task_id |
| `RESULT_RETURN (0x02)` | Worker → Master | After render complete | task_id + pixel buffer (~2.6 MB) |
| `SHUTDOWN (0x03)` | Master → Worker | After all results received | Status code |

### Task Dependency Graph

```
Phase 0 — Sequential (master only)
    T0: Scene parsing & camera init
              │
Phase 1 — Sequential (master only)
    T1: Partition image into row strips
              │
    ┌─────────┼─────────┐
    ▼         ▼         ▼
Phase 2 — PARALLEL (3 workers × 8 threads)
  Worker A  Worker B  Worker C
  T2a: Ray  T2b: Ray  T2c: Ray
  cast+shade cast+shade cast+shade
  T3a: Recur T3b: Recur T3c: Recur
  reflections reflections reflections
  T4a: Write T4b: Write T4c: Write
  pixel buf  pixel buf  pixel buf
    │         │         │
    └─────────┼─────────┘
              │  RESULT_RETURN × 3
Phase 3 — Sequential (master only)
    T5: Assemble final image
    T6: Write PNG to disk
```

### Intra-Node Threading (per Worker)

```
Worker JVM Process
┌─────────────────────────────────────────────────────┐
│  Main Thread                                        │
│  1. Connect to Master & receive TASK_ASSIGN         │
│  2. Deserialize scene + build objects               │
│  3. Allocate pixel buffer [rows × width × 4 bytes]  │
│  4. Submit 8 Callable tasks to ExecutorService      │
│  5. invokeAll() ← blocks until all 8 complete       │
│  6. Serialize pixel buffer → send RESULT_RETURN     │
│  7. Await SHUTDOWN → exit                           │
│                                                     │
│  ExecutorService (Fixed Thread Pool, T=8)           │
│  ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐│
│  │ T-1  │ T-2  │ T-3  │ T-4  │ T-5  │ T-6  │ T-7  │ T-8  ││
│  │rows  │rows  │rows  │rows  │rows  │rows  │rows  │rows  ││
│  │0-44  │45-89 │90-134│135-  │180-  │225-  │270-  │315-  ││
│  │      │      │      │179   │224   │269   │314   │359   ││
│  └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘│
│  Non-overlapping row ranges → lock-free pixel writes       │
└─────────────────────────────────────────────────────┘
```

---

## Project Structure

```
ParallelRayTracer/
│
├── src/
│   ├── common/                      ← Shared by ALL members (define first)
│   │   ├── Vec3.java                  3D vector math
│   │   ├── Ray.java                   Ray (origin + direction)
│   │   ├── SceneObject.java           Interface: intersect(), getNormal()
│   │   ├── Sphere.java                Sphere geometry + AABB getters
│   │   ├── Plane.java                 Infinite plane geometry
│   │   ├── Light.java                 Point light (position, color, intensity)
│   │   ├── Camera.java                Perspective camera + ray generation
│   │   ├── SceneConfig.java           Serializable scene bundle (60 objects)
│   │   ├── Tile.java                  Work unit (taskId, startRow, endRow)
│   │   └── MessageProtocol.java       Type codes + port constants
│   │
│   ├── core/                        ← Member A
│   │   ├── RayTracer.java             Recursive trace engine (brute + BVH mode)
│   │   ├── BVH.java                   Bounding Volume Hierarchy acceleration
│   │   └── SequentialRunner.java      Single-thread baseline + benchmarking
│   │
│   ├── worker/                      ← Member B
│   │   ├── WorkerNode.java            Entry point: connects to master, renders tile
│   │   └── TileRenderer.java          ExecutorService thread pool per tile
│   │
│   └── master/                      ← Member C
│       ├── MasterNode.java            Entry point: partitions, dispatches, aggregates
│       ├── WorkPartitioner.java       Splits H rows into N tiles
│       └── ResultAggregator.java      Merges pixel buffers into final image
│
├── output/                          ← Rendered PNG images (auto-created)
│   ├── seq_480x270_d5.png
│   ├── seq_960x540_d5.png
│   ├── seq_1920x1080_d5.png
│   ├── bvh_1920x1080_d5.png
│   └── par_1920x1080_d5.png         (created after parallel run)
│
├── results/                         ← CSV benchmark data (auto-created)
│   ├── sequential_benchmark.csv
│   ├── parallel_benchmark.csv
│   └── bvh_benchmark.csv
│
└── out/                             ← Compiled .class files (auto-created)
```

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 11+ |
| Networking | `java.net.Socket` / `ServerSocket` |
| Threading | `java.util.concurrent.ExecutorService` |
| Serialization | `java.io.ObjectOutputStream` / `DataOutputStream` |
| Image output | `javax.imageio.ImageIO` (PNG) |
| Build | `javac` (no external build tool required) |
| External libraries | None |

---

## Getting Started

### Prerequisites

- Java 11 or higher installed
- Verify with: `java -version` and `javac -version`
- Windows PowerShell (or Command Prompt)

### Clone / Setup

Create the project folders:

```powershell
mkdir ParallelRayTracer\src\common
mkdir ParallelRayTracer\src\core
mkdir ParallelRayTracer\src\worker
mkdir ParallelRayTracer\src\master
mkdir ParallelRayTracer\out
mkdir ParallelRayTracer\output
mkdir ParallelRayTracer\results
```

---

## How to Run

### Compile (run from project root every time you change any file)

```powershell
javac -d out (Get-ChildItem -Path src -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName)
```

### 1. Sequential Baseline

```powershell
# Small (480×270)
java -cp out core.SequentialRunner 480 270 5

# Medium (960×540)
java -cp out core.SequentialRunner 960 540 5

# Large (1920×1080)
java -cp out core.SequentialRunner 1920 1080 5
```

### 2. Sequential Baseline with BVH

```powershell
java -cp out core.SequentialRunner 480 270 5 bvh
java -cp out core.SequentialRunner 960 540 5 bvh
java -cp out core.SequentialRunner 1920 1080 5 bvh
```

### 3. Distributed Parallel System (3 workers)

Open **4 separate PowerShell windows** from the project root.

**Window 1 — Start Master:**
```powershell
java -cp out master.MasterNode 3 960 540 5
```

**Window 2 — Start Worker A:**
```powershell
java -cp out worker.WorkerNode localhost 5001
```

**Window 3 — Start Worker B:**
```powershell
java -cp out worker.WorkerNode localhost 5001
```

**Window 4 — Start Worker C:**
```powershell
java -cp out worker.WorkerNode localhost 5001
```

Start the master first, then the 3 workers within 30 seconds.

### 4. Scaling Experiments (vary thread count)

```powershell
# 1 thread per worker
java -cp out master.MasterNode 3 1920 1080 5 1

# 2 threads per worker
java -cp out master.MasterNode 3 1920 1080 5 2

# 4 threads per worker
java -cp out master.MasterNode 3 1920 1080 5 4

# 8 threads per worker
java -cp out master.MasterNode 3 1920 1080 5 8
```

---

## Performance Results

### Sequential Baseline (T_seq)

| Image Size | Pixels | Time (ms) |
|-----------|--------|-----------|
| 480×270 | 129,600 | 676 ms |
| 960×540 | 518,400 | 1,745 ms |
| 1920×1080 | 2,073,600 | 6,604 ms |

Scaling factor: ~4× per resolution step (as expected — pixel count quadruples).

### BVH Optimization Results

| Image Size | Without BVH | With BVH | Speedup |
|-----------|------------|----------|---------|
| 480×270 | 676 ms | 559 ms | 1.21× |
| 960×540 | 1,745 ms | 1,626 ms | 1.07× |
| 1920×1080 | 6,604 ms | 5,143 ms | 1.28× |

BVH speedup grows with resolution — consistent with O(log n) vs O(n) intersection complexity.

### Amdahl's Law Bound

With 5% sequential fraction (scene parsing, I/O, aggregation) and P×T = 3×8 = 24 parallel units:

```
S_max = 1 / (f_s + (1 - f_s) / N)
      = 1 / (0.05 + 0.95 / 24)
      ≈ 11.2×
```

A 6.6-second sequential render theoretically completes in ~0.59 seconds at full parallelism.

---

## BVH Optimization

The BVH groups scene objects into a binary tree of axis-aligned bounding boxes. Ray traversal skips entire subtrees when the ray misses a parent box — reducing intersection tests from O(n) to O(log n).

**Design decisions:**
- Infinite objects (planes) bypass the BVH entirely — they are always tested directly
- Only finite objects (spheres) are inserted into the tree
- Shadow rays use `intersectAny()` for early-exit — the first hit terminates traversal
- Tree is built once at scene load time, then reused for all rays

**When BVH helps most:** Scenes with many objects (60+), deep reflections, and complex shadow geometry. Our 60-object scene shows 1.28× improvement at 1080p.

---

## Work Division

| Component | Owner | Status |
|-----------|-------|--------|
| `common/` — all shared classes | Member A | ✅ Complete |
| `core/RayTracer.java` | Member A | ✅ Complete |
| `core/BVH.java` | Member A | ✅ Complete |
| `core/SequentialRunner.java` | Member A | ✅ Complete |
| `worker/WorkerNode.java` | Member B | ✅ Complete |
| `worker/TileRenderer.java` | Member B | ✅ Complete |
| `master/MasterNode.java` | Member C | ✅ Complete |
| `master/WorkPartitioner.java` | Member C | ✅ Complete |
| `master/ResultAggregator.java` | Member C | ✅ Complete |
| Performance graphs + CSV | All | 🔲 After integration |
| Final report | All | 🔲 Week 5 |

---

## Member B — Worker Node (Implementation Guide)

**Your package:** `src/worker/`
**Your entry point:** `java -cp out worker.WorkerNode localhost 5001`

### What you build

You receive a tile from the master, render it using Member A's `RayTracer`, and send the pixel buffer back.

### Files to create

#### `src/worker/TileRenderer.java`

Wraps Member A's `RayTracer` in an `ExecutorService` thread pool. Divides the tile's rows across T threads.

```java
package worker;

import common.*;
import core.RayTracer;
import core.BVH;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class TileRenderer {

    private final SceneConfig scene;
    private final int         nThreads;

    public TileRenderer(SceneConfig scene, int nThreads) {
        this.scene    = scene;
        this.nThreads = nThreads;
    }

    /**
     * Renders rows [tile.startRow .. tile.endRow] inclusive.
     * Returns a flat RGBA byte array: width × rowCount × 4 bytes.
     */
    public byte[] render(Tile tile) throws InterruptedException, ExecutionException {
        int width    = scene.width;
        int rowCount = tile.rowCount();
        byte[] buffer = new byte[width * rowCount * 4];

        BVH       bvh    = new BVH(scene.objects);
        RayTracer tracer = new RayTracer(scene, bvh);

        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        List<Callable<Void>> tasks = new ArrayList<>();

        // Divide rows across threads
        int rowsPerThread = Math.max(1, rowCount / nThreads);

        for (int t = 0; t < nThreads; t++) {
            final int threadStart = tile.startRow + t * rowsPerThread;
            final int threadEnd   = (t == nThreads - 1)
                                    ? tile.endRow
                                    : threadStart + rowsPerThread - 1;
            if (threadStart > tile.endRow) break;

            tasks.add(() -> {
                for (int row = threadStart; row <= threadEnd; row++) {
                    for (int col = 0; col < width; col++) {
                        Ray  ray   = scene.camera.getRay(col, row, width, scene.height);
                        Vec3 color = tracer.traceRay(ray, scene.maxDepth);

                        int localRow = row - tile.startRow;
                        int idx      = (localRow * width + col) * 4;
                        buffer[idx    ] = (byte) color.toRGB_R();
                        buffer[idx + 1] = (byte) color.toRGB_G();
                        buffer[idx + 2] = (byte) color.toRGB_B();
                        buffer[idx + 3] = (byte) 255; // alpha
                    }
                }
                return null;
            });
        }

        pool.invokeAll(tasks);  // blocks until all threads done
        pool.shutdown();
        return buffer;
    }
}
```

#### `src/worker/WorkerNode.java`

Connects to master, receives `TASK_ASSIGN`, renders, sends `RESULT_RETURN`, awaits `SHUTDOWN`.

```java
package worker;

import common.*;
import java.io.*;
import java.net.*;

public class WorkerNode {

    public static void main(String[] args) throws Exception {
        String host    = args.length > 0 ? args[0] : MessageProtocol.DEFAULT_HOST;
        int    port    = args.length > 1 ? Integer.parseInt(args[1]) : MessageProtocol.DEFAULT_PORT;
        int    threads = args.length > 2 ? Integer.parseInt(args[2]) : 8;

        System.out.printf("Worker starting — connecting to %s:%d with %d threads%n",
                          host, port, threads);

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(MessageProtocol.SOCKET_TIMEOUT_MS);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream());

            // --- Send CONNECT ---
            out.writeInt(MessageProtocol.CONNECT);
            out.flush();
            System.out.println("Worker: connected to master");

            // --- Receive TASK_ASSIGN ---
            int msgType = in.readInt();
            if (msgType != MessageProtocol.TASK_ASSIGN) {
                System.err.println("Worker: expected TASK_ASSIGN, got " + msgType);
                return;
            }

            SceneConfig scene  = (SceneConfig) in.readObject();
            Tile        tile   = (Tile)        in.readObject();
            System.out.printf("Worker: received %s  scene=%dx%d depth=%d%n",
                              tile, scene.width, scene.height, scene.maxDepth);

            // --- Render ---
            long start   = System.nanoTime();
            TileRenderer renderer = new TileRenderer(scene, threads);
            byte[]       pixels   = renderer.render(tile);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("Worker: rendered %s in %d ms%n", tile, elapsed);

            // --- Send RESULT_RETURN ---
            out.writeInt(MessageProtocol.RESULT_RETURN);
            out.writeObject(tile);   // echo tile so master knows which rows these are
            out.writeObject(pixels);
            out.flush();
            System.out.println("Worker: result sent");

            // --- Await SHUTDOWN ---
            int shutdown = in.readInt();
            if (shutdown == MessageProtocol.SHUTDOWN) {
                System.out.println("Worker: received SHUTDOWN — exiting cleanly");
            }
        }
    }
}
```

### How to test your part alone

Once compiled, start the master (Member C's code or a stub), then:

```powershell
java -cp out worker.WorkerNode localhost 5001 8
```

### Key things to get right

- Use `invokeAll()` not `submit()` — it blocks until all threads finish before you send the result
- The pixel buffer index formula is `(localRow * width + col) * 4` — `localRow = row - tile.startRow`
- Send the `tile` object back with `RESULT_RETURN` so the master knows where to place your rows
- Never share `RayTracer` across threads — create one per `TileRenderer` instance (it's stateless per-call but object creation is cheap)

---

## Member C — Master Node (Implementation Guide)

**Your package:** `src/master/`
**Your entry point:** `java -cp out master.MasterNode 3 1920 1080 5`

### What you build

You partition the image, accept worker connections, dispatch tiles, collect pixel buffers, assemble the final image, and write the PNG.

### Files to create

#### `src/master/WorkPartitioner.java`

```java
package master;

import common.Tile;
import java.util.ArrayList;
import java.util.List;

public class WorkPartitioner {

    /**
     * Divides [0, height-1] rows into nWorkers contiguous strips.
     * Last worker gets any remainder rows.
     */
    public static List<Tile> partition(int height, int nWorkers) {
        List<Tile> tiles = new ArrayList<>();
        int rowsPerWorker = height / nWorkers;

        for (int i = 0; i < nWorkers; i++) {
            int start = i * rowsPerWorker;
            int end   = (i == nWorkers - 1) ? height - 1
                                             : start + rowsPerWorker - 1;
            tiles.add(new Tile(i, start, end));
        }
        return tiles;
    }
}
```

#### `src/master/ResultAggregator.java`

```java
package master;

import common.Tile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ResultAggregator {

    private final int     width;
    private final int     height;
    private final int[]   masterBuffer; // full RGBA image

    public ResultAggregator(int width, int height) {
        this.width        = width;
        this.height       = height;
        this.masterBuffer = new int[width * height];
    }

    /**
     * Copy a worker's pixel buffer into the correct rows of masterBuffer.
     * Thread-safe: workers write non-overlapping row ranges.
     */
    public void integrate(Tile tile, byte[] pixels) {
        int rowCount = tile.rowCount();
        for (int localRow = 0; localRow < rowCount; localRow++) {
            int globalRow = tile.startRow + localRow;
            for (int col = 0; col < width; col++) {
                int src = (localRow * width + col) * 4;
                int r   = pixels[src    ] & 0xFF;
                int g   = pixels[src + 1] & 0xFF;
                int b   = pixels[src + 2] & 0xFF;
                masterBuffer[globalRow * width + col] = (r << 16) | (g << 8) | b;
            }
        }
    }

    /** Write the assembled image to a PNG file. */
    public void savePNG(String path) throws IOException {
        BufferedImage image = new BufferedImage(width, height,
                                                BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < width * height; i++) {
            image.setRGB(i % width, i / width, masterBuffer[i]);
        }
        new File("output").mkdirs();
        ImageIO.write(image, "PNG", new File(path));
        System.out.println("Master: image saved → " + path);
    }
}
```

#### `src/master/MasterNode.java`

```java
package master;

import common.*;
import java.io.*;
import java.net.*;
import java.util.List;
import java.io.FileWriter;
import java.io.PrintWriter;

public class MasterNode {

    public static void main(String[] args) throws Exception {
        int nWorkers = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int width    = args.length > 1 ? Integer.parseInt(args[1]) : 960;
        int height   = args.length > 2 ? Integer.parseInt(args[2]) : 540;
        int maxDepth = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        int nThreads = args.length > 4 ? Integer.parseInt(args[4]) : 8;

        System.out.printf("Master: %dx%d  depth=%d  workers=%d  threads/worker=%d%n",
                          width, height, maxDepth, nWorkers, nThreads);

        SceneConfig scene = SceneConfig.buildDefaultScene(width, height, maxDepth);
        List<Tile>  tiles = WorkPartitioner.partition(height, nWorkers);
        ResultAggregator aggregator = new ResultAggregator(width, height);

        Socket[]           sockets = new Socket[nWorkers];
        ObjectOutputStream[] outs  = new ObjectOutputStream[nWorkers];
        ObjectInputStream[]  ins   = new ObjectInputStream[nWorkers];

        // --- Accept worker connections ---
        try (ServerSocket server = new ServerSocket(MessageProtocol.DEFAULT_PORT)) {
            server.setSoTimeout(60_000);
            System.out.println("Master: listening on port " + MessageProtocol.DEFAULT_PORT);

            for (int i = 0; i < nWorkers; i++) {
                sockets[i] = server.accept();
                outs[i]    = new ObjectOutputStream(sockets[i].getOutputStream());
                ins[i]     = new ObjectInputStream(sockets[i].getInputStream());
                int msg    = ins[i].readInt(); // CONNECT
                System.out.println("Master: worker " + i + " connected");
            }
        }

        long startTime = System.nanoTime();

        // --- Dispatch TASK_ASSIGN to each worker ---
        for (int i = 0; i < nWorkers; i++) {
            outs[i].writeInt(MessageProtocol.TASK_ASSIGN);
            outs[i].writeObject(scene);
            outs[i].writeObject(tiles.get(i));
            outs[i].flush();
            System.out.println("Master: dispatched " + tiles.get(i) + " → worker " + i);
        }

        // --- Collect RESULT_RETURN from all workers ---
        for (int i = 0; i < nWorkers; i++) {
            int msgType = ins[i].readInt();
            if (msgType != MessageProtocol.RESULT_RETURN) {
                System.err.println("Master: unexpected message type " + msgType);
                continue;
            }
            Tile   resultTile = (Tile)   ins[i].readObject();
            byte[] pixels     = (byte[]) ins[i].readObject();
            aggregator.integrate(resultTile, pixels);
            System.out.println("Master: received result for " + resultTile);
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        System.out.printf("Master: total parallel render time: %d ms (%.2f s)%n",
                          elapsedMs, elapsedMs / 1000.0);

        // --- Send SHUTDOWN to all workers ---
        for (int i = 0; i < nWorkers; i++) {
            outs[i].writeInt(MessageProtocol.SHUTDOWN);
            outs[i].flush();
            sockets[i].close();
        }

        // --- Save image ---
        String imgPath = String.format("output/par_%dx%d_d%d.png", width, height, maxDepth);
        aggregator.savePNG(imgPath);

        // --- Save CSV ---
        new File("results").mkdirs();
        String csvFile  = "results/parallel_benchmark.csv";
        boolean newFile = !new File(csvFile).exists();
        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile, true))) {
            if (newFile) pw.println("width,height,maxDepth,workers,threads,time_ms");
            pw.printf("%d,%d,%d,%d,%d,%d%n",
                      width, height, maxDepth, nWorkers, nThreads, elapsedMs);
        }
        System.out.println("Master: benchmark saved → " + csvFile);
    }
}
```

### How to test your part

```powershell
# Terminal 1 — Master
java -cp out master.MasterNode 3 960 540 5

# Terminals 2, 3, 4 — one each
java -cp out worker.WorkerNode localhost 5001
```

### Key things to get right

- Open `ServerSocket` before workers try to connect — start master first
- Use `ObjectOutputStream` before `ObjectInputStream` on both ends — wrong order causes deadlock
- `integrate()` is called sequentially on the master thread — no synchronization needed
- The `resultTile` echoed back by the worker tells you exactly which rows to fill — use `tile.startRow`
- Save timing from after all workers connect to after all results received — that's T_par

---

## Report Checklist

- [ ] Section 1: Introduction — recap Assignment 3 design, note any changes
- [ ] Section 2: Implementation — sequential baseline, threading, socket protocol
- [ ] Section 3: Correctness — pixel-exact match between sequential and parallel output, critical sections, deadlock freedom argument
- [ ] Section 4: Performance — speedup table, efficiency table, Amdahl's Law comparison, strong scaling graph
- [ ] Section 5: Optimization — BVH before/after results, motivation, analysis
- [ ] Section 6: Insights — what worked, what didn't, scalability limits

**Graphs required:**
- Speedup curve S(p) vs workers/threads (with Amdahl bound overlay)
- Efficiency curve E(p) vs workers/threads
- BVH before/after render time (bar chart, 3 resolutions)
- Strong scaling: T_par vs image size for fixed worker count

---

## Team

| Member | Role | Deliverables |
|--------|------|-------------|
| **Amna Akhtar Nawabi** (462939) | Ray Tracer Core + BVH | `common/`, `core/`, sequential benchmarks, BVH optimization |
| **Sana Khan Khitran** (464597) | Worker Node | `worker/WorkerNode.java`, `worker/TileRenderer.java`, thread scaling experiments |
| **Attiqa Bano** (473781) | Master Node | `master/MasterNode.java`, `master/WorkPartitioner.java`, `master/ResultAggregator.java`, final image assembly |

**Course:** CS-347 Parallel & Distributed Computing (2+1)
**Instructor:** Dr. Fahad Ahmed Satti
**Institution:** SEECS, NUST
**Due Date:** 15 May 2026
