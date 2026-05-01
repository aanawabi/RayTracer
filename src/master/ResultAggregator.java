package master;

import common.Tile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ResultAggregator {

    private final int   width;
    private final int   height;
    private final int[] masterBuffer; // packed RGB, one int per pixel

    public ResultAggregator(int width, int height) {
        this.width        = width;
        this.height       = height;
        this.masterBuffer = new int[width * height];
    }

    /**
     * @param tile 
     * @param pixels
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

    /**
     * @param path
     */
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