package worker;

import common.*;
import core.RayTracer;
import core.BVH;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class TileRenderer {

    private final SceneConfig scene;
    private final int nThreads;

    public TileRenderer(SceneConfig scene, int nThreads) {
        this.scene = scene;
        this.nThreads = nThreads;
    }

    public byte[] render(Tile tile) throws InterruptedException, ExecutionException {
        int width = scene.width;
        int rowCount = tile.rowCount();
        byte[] buffer = new byte[width * rowCount * 4];

        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        List<Callable<Void>> tasks = new ArrayList<>();

        int rowsPerThread = Math.max(1, rowCount / nThreads);

        for (int t = 0; t < nThreads; t++) {
            final int threadStart = tile.startRow + t * rowsPerThread;
            final int threadEnd = (t == nThreads - 1)
                    ? tile.endRow
                    : threadStart + rowsPerThread - 1;
            if (threadStart > tile.endRow) break;

            tasks.add(() -> {
                BVH bvh = new BVH(scene.objects);
                RayTracer tracer = new RayTracer(scene, bvh);

                for (int row = threadStart; row <= threadEnd; row++) {
                    for (int col = 0; col < width; col++) {
                        Ray ray = scene.camera.getRay(col, row, width, scene.height);
                        Vec3 color = tracer.traceRay(ray, scene.maxDepth);

                        int localRow = row - tile.startRow;
                        int idx = (localRow * width + col) * 4;
                        buffer[idx]     = (byte) color.toRGB_R();
                        buffer[idx + 1] = (byte) color.toRGB_G();
                        buffer[idx + 2] = (byte) color.toRGB_B();
                        buffer[idx + 3] = (byte) 255;
                    }
                }
                return null;
            });
        }

        pool.invokeAll(tasks);
        pool.shutdown();
        return buffer;
    }
}