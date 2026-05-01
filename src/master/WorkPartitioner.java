package master;

import common.Tile;
import java.util.ArrayList;
import java.util.List;

public class WorkPartitioner {
    /**
     * @param height
     * @param nWorkers
     * @return
     */
    public static List<Tile> partition(int height, int nWorkers) {
        List<Tile> tiles = new ArrayList<>();
        int rowsPerWorker = height / nWorkers;

        for (int i = 0; i < nWorkers; i++) {
            int start = i * rowsPerWorker;
            int end   = (i == nWorkers - 1)
                        ? height - 1                  
                        : start + rowsPerWorker - 1;
            tiles.add(new Tile(i, start, end));
        }
        return tiles;
    }
}