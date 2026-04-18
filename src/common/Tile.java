package common;

import java.io.Serializable;

public class Tile implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int taskId;
    public final int startRow;
    public final int endRow;   // inclusive

    public Tile(int taskId, int startRow, int endRow) {
        this.taskId   = taskId;
        this.startRow = startRow;
        this.endRow   = endRow;
    }

    public int rowCount() { return endRow - startRow + 1; }

    @Override
    public String toString() {
        return String.format("Tile[id=%d rows=%d..%d]", taskId, startRow, endRow);
    }
}