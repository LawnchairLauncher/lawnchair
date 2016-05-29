package com.android.launcher3.util;

import android.graphics.Rect;

import com.android.launcher3.ItemInfo;

/**
 * Utility object to manage the occupancy in a grid.
 */
public class GridOccupancy {

    private final int mCountX;
    private final int mCountY;

    public final boolean[][] cells;

    public GridOccupancy(int countX, int countY) {
        mCountX = countX;
        mCountY = countY;
        cells = new boolean[countX][countY];
    }

    /**
     * Find the first vacant cell, if there is one.
     *
     * @param vacantOut Holds the x and y coordinate of the vacant cell
     * @param spanX Horizontal cell span.
     * @param spanY Vertical cell span.
     *
     * @return true if a vacant cell was found
     */
    public boolean findVacantCell(int[] vacantOut, int spanX, int spanY) {
        for (int y = 0; (y + spanY) <= mCountY; y++) {
            for (int x = 0; (x + spanX) <= mCountX; x++) {
                boolean available = !cells[x][y];
                out:
                for (int i = x; i < x + spanX; i++) {
                    for (int j = y; j < y + spanY; j++) {
                        available = available && !cells[i][j];
                        if (!available) break out;
                    }
                }
                if (available) {
                    vacantOut[0] = x;
                    vacantOut[1] = y;
                    return true;
                }
            }
        }
        return false;
    }

    public void copyTo(GridOccupancy dest) {
        for (int i = 0; i < mCountX; i++) {
            for (int j = 0; j < mCountY; j++) {
                dest.cells[i][j] = cells[i][j];
            }
        }
    }

    public boolean isRegionVacant(int x, int y, int spanX, int spanY) {
        int x2 = x + spanX - 1;
        int y2 = y + spanY - 1;
        if (x < 0 || y < 0 || x2 >= mCountX || y2 >= mCountY) {
            return false;
        }
        for (int i = x; i <= x2; i++) {
            for (int j = y; j <= y2; j++) {
                if (cells[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    public void markCells(int cellX, int cellY, int spanX, int spanY, boolean value) {
        if (cellX < 0 || cellY < 0) return;
        for (int x = cellX; x < cellX + spanX && x < mCountX; x++) {
            for (int y = cellY; y < cellY + spanY && y < mCountY; y++) {
                cells[x][y] = value;
            }
        }
    }

    public void markCells(Rect r, boolean value) {
        markCells(r.left, r.top, r.width(), r.height(), value);
    }

    public void markCells(CellAndSpan cell, boolean value) {
        markCells(cell.cellX, cell.cellY, cell.spanX, cell.spanY, value);
    }

    public void markCells(ItemInfo item, boolean value) {
        markCells(item.cellX, item.cellY, item.spanX, item.spanY, value);
    }

    public void clear() {
        markCells(0, 0, mCountX, mCountY, false);
    }
}
