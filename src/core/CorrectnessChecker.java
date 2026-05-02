package core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Compares a sequential reference image against a parallel output image,
 * pixel by pixel, and reports whether they match.
 *
 * Usage:
 *   java -cp out core.CorrectnessChecker <reference.png> <parallel.png> [tolerance]
 *
 * tolerance (default 0): max allowed per-channel difference (0 = exact match).
 * Exits with code 0 on match, 1 on mismatch.
 */
public class CorrectnessChecker {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java -cp out core.CorrectnessChecker <ref.png> <par.png> [tolerance]");
            System.exit(2);
        }

        String refPath = args[0];
        String parPath = args[1];
        int    tol     = args.length > 2 ? Integer.parseInt(args[2]) : 0;

        BufferedImage ref = ImageIO.read(new File(refPath));
        BufferedImage par = ImageIO.read(new File(parPath));

        System.out.println("=== Correctness Verification ===");
        System.out.printf("  Reference : %s  (%dx%d)%n", refPath, ref.getWidth(), ref.getHeight());
        System.out.printf("  Parallel  : %s  (%dx%d)%n", parPath, par.getWidth(), par.getHeight());
        System.out.printf("  Tolerance : ±%d per channel%n%n", tol);

        if (ref.getWidth() != par.getWidth() || ref.getHeight() != par.getHeight()) {
            System.err.println("FAIL: images have different dimensions.");
            System.exit(1);
        }

        int width  = ref.getWidth();
        int height = ref.getHeight();
        long totalPixels   = (long) width * height;
        long mismatchCount = 0;
        int  maxDiff       = 0;
        long sumDiff       = 0;

        // Diff image: white background, mismatched pixels highlighted red
        BufferedImage diffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgbRef = ref.getRGB(x, y);
                int rgbPar = par.getRGB(x, y);

                int rRef = (rgbRef >> 16) & 0xFF;
                int gRef = (rgbRef >>  8) & 0xFF;
                int bRef =  rgbRef        & 0xFF;

                int rPar = (rgbPar >> 16) & 0xFF;
                int gPar = (rgbPar >>  8) & 0xFF;
                int bPar =  rgbPar        & 0xFF;

                int dr = Math.abs(rRef - rPar);
                int dg = Math.abs(gRef - gPar);
                int db = Math.abs(bRef - bPar);
                int channelMax = Math.max(dr, Math.max(dg, db));

                maxDiff  = Math.max(maxDiff, channelMax);
                sumDiff += channelMax;

                if (channelMax > tol) {
                    mismatchCount++;
                    diffImg.setRGB(x, y, 0xFF0000); // red = mismatch
                } else {
                    // Dim the matching pixel so mismatches stand out
                    int dimmed = ((rRef / 4) << 16) | ((gRef / 4) << 8) | (bRef / 4);
                    diffImg.setRGB(x, y, dimmed);
                }
            }
        }

        double meanDiff      = (double) sumDiff / totalPixels;
        double mismatchPct   = 100.0 * mismatchCount / totalPixels;

        System.out.printf("  Total pixels    : %,d%n",    totalPixels);
        System.out.printf("  Mismatched      : %,d  (%.4f%%)%n", mismatchCount, mismatchPct);
        System.out.printf("  Max channel diff: %d%n",     maxDiff);
        System.out.printf("  Mean channel diff: %.4f%n",  meanDiff);

        // Save diff image
        new File("output").mkdirs();
        String diffPath = "output/diff_" + new File(parPath).getName();
        ImageIO.write(diffImg, "PNG", new File(diffPath));
        System.out.printf("%n  Diff image saved: %s%n", diffPath);
        System.out.printf("  (Red pixels = mismatch, dark = match)%n%n");

        if (mismatchCount == 0) {
            System.out.println("RESULT: PASS — outputs are pixel-exact.");
            System.exit(0);
        } else {
            System.out.printf("RESULT: FAIL — %,d pixels differ (tolerance=%d).%n",
                              mismatchCount, tol);
            System.exit(1);
        }
    }
}
