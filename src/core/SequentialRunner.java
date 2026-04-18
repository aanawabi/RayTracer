package core;

import common.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class SequentialRunner {

    public static void main(String[] args) throws Exception {
        int width    = args.length > 0 ? Integer.parseInt(args[0]) : 960;
        int height   = args.length > 1 ? Integer.parseInt(args[1]) : 540;
        int maxDepth = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        // Pass "bvh" as 4th arg to enable BVH mode
        boolean useBVH = args.length > 3 && args[3].equalsIgnoreCase("bvh");

        System.out.printf("Sequential ray tracer: %dx%d  depth=%d  BVH=%b%n",
                          width, height, maxDepth, useBVH);

        SceneConfig scene  = SceneConfig.buildDefaultScene(width, height, maxDepth);
        BVH         bvh    = useBVH ? new BVH(scene.objects) : null;
        RayTracer   tracer = useBVH ? new RayTracer(scene, bvh)
                                    : new RayTracer(scene);

        BufferedImage image = new BufferedImage(width, height,
                                                BufferedImage.TYPE_INT_RGB);
        long startTime = System.nanoTime();

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                Ray  ray   = scene.camera.getRay(col, row, width, height);
                Vec3 color = tracer.traceRay(ray, maxDepth);
                int rgb = (color.toRGB_R() << 16)
                        | (color.toRGB_G() <<  8)
                        |  color.toRGB_B();
                image.setRGB(col, row, rgb);
            }
            if ((row + 1) % (height / 10) == 0)
                System.out.printf("  %.0f%% done%n", 100.0*(row+1)/height);
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        System.out.printf("Render time: %d ms (%.2f s)%n",
                          elapsedMs, elapsedMs / 1000.0);

        new File("output").mkdirs();
        String mode    = useBVH ? "bvh" : "seq";
        String imgFile = String.format("output/%s_%dx%d_d%d.png",
                                       mode, width, height, maxDepth);
        ImageIO.write(image, "PNG", new File(imgFile));
        System.out.println("Image saved: " + imgFile);

        new File("results").mkdirs();
        String csvFile = "results/sequential_benchmark.csv";
        boolean newFile = !new File(csvFile).exists();
        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile, true))) {
            if (newFile) pw.println("width,height,maxDepth,mode,time_ms");
            pw.printf("%d,%d,%d,%s,%d%n",
                      width, height, maxDepth, mode, elapsedMs);
        }
        System.out.println("Benchmark saved: " + csvFile);
    }
}