package com.android.launcher3.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link GridOccupancy}
 */
@RunWith(RobolectricTestRunner.class)
public class GridOccupancyTest {

    @Test
    public void testFindVacantCell() {
        GridOccupancy grid = initGrid(4,
                1, 1, 1, 0, 0,
                0, 0, 1, 1, 0,
                0, 0, 0, 0, 0,
                1, 1, 0, 0, 0
        );

        int[] vacant = new int[2];
        assertTrue(grid.findVacantCell(vacant, 2, 2));
        assertEquals(vacant[0], 0);
        assertEquals(vacant[1], 1);

        assertTrue(grid.findVacantCell(vacant, 3, 2));
        assertEquals(vacant[0], 2);
        assertEquals(vacant[1], 2);

        assertFalse(grid.findVacantCell(vacant, 3, 3));
    }

    @Test
    public void testIsRegionVacant() {
        GridOccupancy grid = initGrid(4,
                1, 1, 1, 0, 0,
                0, 0, 1, 1, 0,
                0, 0, 0, 0, 0,
                1, 1, 0, 0, 0
        );

        assertTrue(grid.isRegionVacant(4, 0, 1, 4));
        assertTrue(grid.isRegionVacant(0, 1, 2, 2));
        assertTrue(grid.isRegionVacant(2, 2, 3, 2));

        assertFalse(grid.isRegionVacant(3, 0, 2, 4));
        assertFalse(grid.isRegionVacant(0, 0, 2, 1));
    }

    private GridOccupancy initGrid(int rows, int... cells) {
        int cols = cells.length / rows;
        int i = 0;
        GridOccupancy grid = new GridOccupancy(cols, rows);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                grid.cells[x][y] = cells[i] != 0;
                i++;
            }
        }
        return grid;
    }
}
