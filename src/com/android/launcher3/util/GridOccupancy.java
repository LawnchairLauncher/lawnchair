package com.android.launcher3.util;

import android.graphics.Rect;

import com.android.launcher3.model.data.ItemInfo;

/**
 * Utility object to manage the occupancy in a grid.
 */
public class GridOccupancy extends AbsGridOccupancy {

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
        return super.findVacantCell(vacantOut, cells, mCountX, mCountY, spanX, spanY);
    }

    public boolean findLastVacantCell(int[] vacantOut, int spanX, int spanY) {
        int lastX = -1, lastY = -1;
        boolean available = false;
        for (int y = mCountY - 1; y >= 0; y--) {
            for (int x = mCountX - 1; x >= 0; x--) {
                available = !cells[x][y];
                if (available) {
                    lastX = x;
                    lastY = y;
                } else {
                    if (lastX == -1||lastY == -1) {
                        // if this page is full
                        return false;
                    }
                    vacantOut[0] = lastX;
                    vacantOut[1] = lastY;
                    return true;
                }
            }
        }

        if (available) {
            // if this page is empty
            vacantOut[0] = 0;
            vacantOut[1] = 0;
            return true;
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

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("Grid: \n");
        for (int y = 0; y < mCountY; y++) {
            for (int x = 0; x < mCountX; x++) {
                s.append(cells[x][y] ? 1 : 0).append(" ");
            }
            s.append("\n");
        }
        return s.toString();
    }
}
