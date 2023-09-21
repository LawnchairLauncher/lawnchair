/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.util;

/**
 * Defines method to find the next vacant cell on a grid.
 * This uses the default top-down, left-right approach and can be over-written through
 * code swaps in different launchers.
 */
public abstract class AbsGridOccupancy {
    /**
     * Find the first vacant cell, if there is one.
     *
     * @param vacantOut Holds the x and y coordinate of the vacant cell
     * @param spanX Horizontal cell span.
     * @param spanY Vertical cell span.
     *
     * @return true if a vacant cell was found
     */
    protected boolean findVacantCell(int[] vacantOut, boolean[][] cells, int countX, int countY,
            int spanX, int spanY) {
        for (int y = 0; (y + spanY) <= countY; y++) {
            for (int x = 0; (x + spanX) <= countX; x++) {
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
}
