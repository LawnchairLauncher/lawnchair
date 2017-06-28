/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.config.FeatureFlags;

import java.util.Arrays;

/**
 * Calculates the next item that a {@link KeyEvent} should change the focus to.
 *<p>
 * Note, this utility class calculates everything regards to icon index and its (x,y) coordinates.
 * Currently supports:
 * <ul>
 *  <li> full matrix of cells that are 1x1
 *  <li> sparse matrix of cells that are 1x1
 *     [ 1][  ][ 2][  ]
 *     [  ][  ][ 3][  ]
 *     [  ][ 4][  ][  ]
 *     [  ][ 5][ 6][ 7]
 * </ul>
 * *<p>
 * For testing, one can use a BT keyboard, or use following adb command.
 * ex. $ adb shell input keyevent 20 // KEYCODE_DPAD_LEFT
 */
public class FocusLogic {

    private static final String TAG = "FocusLogic";
    private static final boolean DEBUG = false;

    /** Item and page index related constant used by {@link #handleKeyEvent}. */
    public static final int NOOP = -1;

    public static final int PREVIOUS_PAGE_RIGHT_COLUMN  = -2;
    public static final int PREVIOUS_PAGE_FIRST_ITEM    = -3;
    public static final int PREVIOUS_PAGE_LAST_ITEM     = -4;
    public static final int PREVIOUS_PAGE_LEFT_COLUMN   = -5;

    public static final int CURRENT_PAGE_FIRST_ITEM     = -6;
    public static final int CURRENT_PAGE_LAST_ITEM      = -7;

    public static final int NEXT_PAGE_FIRST_ITEM        = -8;
    public static final int NEXT_PAGE_LEFT_COLUMN       = -9;
    public static final int NEXT_PAGE_RIGHT_COLUMN      = -10;

    public static final int ALL_APPS_COLUMN = -11;

    // Matrix related constant.
    public static final int EMPTY = -1;
    public static final int PIVOT = 100;

    /**
     * Returns true only if this utility class handles the key code.
     */
    public static boolean shouldConsume(int keyCode) {
        return (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_MOVE_HOME || keyCode == KeyEvent.KEYCODE_MOVE_END ||
                keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN);
    }

    public static int handleKeyEvent(int keyCode, int [][] map, int iconIdx, int pageIndex,
            int pageCount, boolean isRtl) {

        int cntX = map == null ? -1 : map.length;
        int cntY = map == null ? -1 : map[0].length;

        if (DEBUG) {
            Log.v(TAG, String.format(
                    "handleKeyEvent START: cntX=%d, cntY=%d, iconIdx=%d, pageIdx=%d, pageCnt=%d",
                    cntX, cntY, iconIdx, pageIndex, pageCount));
        }

        int newIndex = NOOP;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                newIndex = handleDpadHorizontal(iconIdx, cntX, cntY, map, -1 /*increment*/, isRtl);
                if (!isRtl && newIndex == NOOP && pageIndex > 0) {
                    newIndex = PREVIOUS_PAGE_RIGHT_COLUMN;
                } else if (isRtl && newIndex == NOOP && pageIndex < pageCount - 1) {
                    newIndex = NEXT_PAGE_RIGHT_COLUMN;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                newIndex = handleDpadHorizontal(iconIdx, cntX, cntY, map, 1 /*increment*/, isRtl);
                if (!isRtl && newIndex == NOOP && pageIndex < pageCount - 1) {
                    newIndex = NEXT_PAGE_LEFT_COLUMN;
                } else if (isRtl && newIndex == NOOP && pageIndex > 0) {
                    newIndex = PREVIOUS_PAGE_LEFT_COLUMN;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                newIndex = handleDpadVertical(iconIdx, cntX, cntY, map, 1  /*increment*/);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                newIndex = handleDpadVertical(iconIdx, cntX, cntY, map, -1  /*increment*/);
                break;
            case KeyEvent.KEYCODE_MOVE_HOME:
                newIndex = handleMoveHome();
                break;
            case KeyEvent.KEYCODE_MOVE_END:
                newIndex = handleMoveEnd();
                break;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                newIndex = handlePageDown(pageIndex, pageCount);
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
                newIndex = handlePageUp(pageIndex);
                break;
            default:
                break;
        }

        if (DEBUG) {
            Log.v(TAG, String.format("handleKeyEvent FINISH: index [%d -> %s]",
                    iconIdx, getStringIndex(newIndex)));
        }
        return newIndex;
    }

    /**
     * Returns a matrix of size (m x n) that has been initialized with {@link #EMPTY}.
     *
     * @param m                 number of columns in the matrix
     * @param n                 number of rows in the matrix
     */
    // TODO: get rid of dynamic matrix creation.
    private static int[][] createFullMatrix(int m, int n) {
        int[][] matrix = new int [m][n];

        for (int i=0; i < m;i++) {
            Arrays.fill(matrix[i], EMPTY);
        }
        return matrix;
    }

    /**
     * Returns a matrix of size same as the {@link CellLayout} dimension that is initialized with the
     * index of the child view.
     */
    // TODO: get rid of the dynamic matrix creation
    public static int[][] createSparseMatrix(CellLayout layout) {
        ShortcutAndWidgetContainer parent = layout.getShortcutsAndWidgets();
        final int m = layout.getCountX();
        final int n = layout.getCountY();
        final boolean invert = parent.invertLayoutHorizontally();

        int[][] matrix = createFullMatrix(m, n);

        // Iterate thru the children.
        for (int i = 0; i < parent.getChildCount(); i++ ) {
            View cell = parent.getChildAt(i);
            if (!cell.isFocusable()) {
                continue;
            }
            int cx = ((CellLayout.LayoutParams) cell.getLayoutParams()).cellX;
            int cy = ((CellLayout.LayoutParams) cell.getLayoutParams()).cellY;
            matrix[invert ? (m - cx - 1) : cx][cy] = i;
        }
        if (DEBUG) {
            printMatrix(matrix);
        }
        return matrix;
    }

    /**
     * Creates a sparse matrix that merges the icon and hotseat view group using the cell layout.
     * The size of the returning matrix is [icon column count x (icon + hotseat row count)]
     * in portrait orientation. In landscape, [(icon + hotseat) column count x (icon row count)]
     */
    // TODO: get rid of the dynamic matrix creation
    public static int[][] createSparseMatrixWithHotseat(
            CellLayout iconLayout, CellLayout hotseatLayout, DeviceProfile dp) {

        ViewGroup iconParent = iconLayout.getShortcutsAndWidgets();
        ViewGroup hotseatParent = hotseatLayout.getShortcutsAndWidgets();

        boolean isHotseatHorizontal = !dp.isVerticalBarLayout();
        boolean moreIconsInHotseatThanWorkspace = !FeatureFlags.NO_ALL_APPS_ICON &&
                (isHotseatHorizontal
                        ? hotseatLayout.getCountX() > iconLayout.getCountX()
                        : hotseatLayout.getCountY() > iconLayout.getCountY());

        int m, n;
        if (isHotseatHorizontal) {
            m = hotseatLayout.getCountX();
            n = iconLayout.getCountY() + hotseatLayout.getCountY();
        } else {
            m = iconLayout.getCountX() + hotseatLayout.getCountX();
            n = hotseatLayout.getCountY();
        }
        int[][] matrix = createFullMatrix(m, n);
        if (moreIconsInHotseatThanWorkspace) {
            int allappsiconRank = dp.inv.getAllAppsButtonRank();
            if (isHotseatHorizontal) {
                for (int j = 0; j < n; j++) {
                    matrix[allappsiconRank][j] = ALL_APPS_COLUMN;
                }
            } else {
                for (int j = 0; j < m; j++) {
                    matrix[j][allappsiconRank] = ALL_APPS_COLUMN;
                }
            }
        }
        // Iterate thru the children of the workspace.
        for (int i = 0; i < iconParent.getChildCount(); i++) {
            View cell = iconParent.getChildAt(i);
            if (!cell.isFocusable()) {
                continue;
            }
            int cx = ((CellLayout.LayoutParams) cell.getLayoutParams()).cellX;
            int cy = ((CellLayout.LayoutParams) cell.getLayoutParams()).cellY;
            if (moreIconsInHotseatThanWorkspace) {
                int allappsiconRank = dp.inv.getAllAppsButtonRank();
                if (isHotseatHorizontal && cx >= allappsiconRank) {
                    // Add 1 to account for the All Apps button.
                    cx++;
                }
                if (!isHotseatHorizontal && cy >= allappsiconRank) {
                    // Add 1 to account for the All Apps button.
                    cy++;
                }
            }
            matrix[cx][cy] = i;
        }

        // Iterate thru the children of the hotseat.
        for (int i = hotseatParent.getChildCount() - 1; i >= 0; i--) {
            if (isHotseatHorizontal) {
                int cx = ((CellLayout.LayoutParams)
                        hotseatParent.getChildAt(i).getLayoutParams()).cellX;
                matrix[cx][iconLayout.getCountY()] = iconParent.getChildCount() + i;
            } else {
                int cy = ((CellLayout.LayoutParams)
                        hotseatParent.getChildAt(i).getLayoutParams()).cellY;
                matrix[iconLayout.getCountX()][cy] = iconParent.getChildCount() + i;
            }
        }
        if (DEBUG) {
            printMatrix(matrix);
        }
        return matrix;
    }

    /**
     * Creates a sparse matrix that merges the icon of previous/next page and last column of
     * current page. When left key is triggered on the leftmost column, sparse matrix is created
     * that combines previous page matrix and an extra column on the right. Likewise, when right
     * key is triggered on the rightmost column, sparse matrix is created that combines this column
     * on the 0th column and the next page matrix.
     *
     * @param pivotX    x coordinate of the focused item in the current page
     * @param pivotY    y coordinate of the focused item in the current page
     */
    // TODO: get rid of the dynamic matrix creation
    public static int[][] createSparseMatrixWithPivotColumn(CellLayout iconLayout,
            int pivotX, int pivotY) {

        ViewGroup iconParent = iconLayout.getShortcutsAndWidgets();

        int[][] matrix = createFullMatrix(iconLayout.getCountX() + 1, iconLayout.getCountY());

        // Iterate thru the children of the top parent.
        for (int i = 0; i < iconParent.getChildCount(); i++) {
            View cell = iconParent.getChildAt(i);
            if (!cell.isFocusable()) {
                continue;
            }
            int cx = ((CellLayout.LayoutParams) cell.getLayoutParams()).cellX;
            int cy = ((CellLayout.LayoutParams) cell.getLayoutParams()).cellY;
            if (pivotX < 0) {
                matrix[cx - pivotX][cy] = i;
            } else {
                matrix[cx][cy] = i;
            }
        }

        if (pivotX < 0) {
            matrix[0][pivotY] = PIVOT;
        } else {
            matrix[pivotX][pivotY] = PIVOT;
        }
        if (DEBUG) {
            printMatrix(matrix);
        }
        return matrix;
    }

    //
    // key event handling methods.
    //

    /**
     * Calculates icon that has is closest to the horizontal axis in reference to the cur icon.
     *
     * Example of the check order for KEYCODE_DPAD_RIGHT:
     * [  ][  ][13][14][15]
     * [  ][ 6][ 8][10][12]
     * [ X][ 1][ 2][ 3][ 4]
     * [  ][ 5][ 7][ 9][11]
     */
    // TODO: add unit tests to verify all permutation.
    private static int handleDpadHorizontal(int iconIdx, int cntX, int cntY,
            int[][] matrix, int increment, boolean isRtl) {
        if(matrix == null) {
            throw new IllegalStateException("Dpad navigation requires a matrix.");
        }
        int newIconIndex = NOOP;

        int xPos = -1;
        int yPos = -1;
        // Figure out the location of the icon.
        for (int i = 0; i < cntX; i++) {
            for (int j = 0; j < cntY; j++) {
                if (matrix[i][j] == iconIdx) {
                    xPos = i;
                    yPos = j;
                }
            }
        }
        if (DEBUG) {
            Log.v(TAG, String.format("\thandleDpadHorizontal: \t[x, y]=[%d, %d] iconIndex=%d",
                    xPos, yPos, iconIdx));
        }

        // Rule1: check first in the horizontal direction
        for (int x = xPos + increment; 0 <= x && x < cntX; x += increment) {
            if ((newIconIndex = inspectMatrix(x, yPos, cntX, cntY, matrix)) != NOOP
                    && newIconIndex != ALL_APPS_COLUMN) {
                return newIconIndex;
            }
        }

        // Rule2: check (x1-n, yPos + increment),   (x1-n, yPos - increment)
        //              (x2-n, yPos + 2*increment), (x2-n, yPos - 2*increment)
        int nextYPos1;
        int nextYPos2;
        boolean haveCrossedAllAppsColumn1 = false;
        boolean haveCrossedAllAppsColumn2 = false;
        int x = -1;
        for (int coeff = 1; coeff < cntY; coeff++) {
            nextYPos1 = yPos + coeff * increment;
            nextYPos2 = yPos - coeff * increment;
            x = xPos + increment * coeff;
            if (inspectMatrix(x, nextYPos1, cntX, cntY, matrix) == ALL_APPS_COLUMN) {
                haveCrossedAllAppsColumn1 = true;
            }
            if (inspectMatrix(x, nextYPos2, cntX, cntY, matrix) == ALL_APPS_COLUMN) {
                haveCrossedAllAppsColumn2 = true;
            }
            for (; 0 <= x && x < cntX; x += increment) {
                int offset1 = haveCrossedAllAppsColumn1 && x < cntX - 1 ? increment : 0;
                newIconIndex = inspectMatrix(x, nextYPos1 + offset1, cntX, cntY, matrix);
                if (newIconIndex != NOOP) {
                    return newIconIndex;
                }
                int offset2 = haveCrossedAllAppsColumn2 && x < cntX - 1 ? -increment : 0;
                newIconIndex = inspectMatrix(x, nextYPos2 + offset2, cntX, cntY, matrix);
                if (newIconIndex != NOOP) {
                    return newIconIndex;
                }
            }
        }

        // Rule3: if switching between pages, do a brute-force search to find an item that was
        //        missed by rules 1 and 2 (such as when going from a bottom right icon to top left)
        if (iconIdx == PIVOT) {
            if (isRtl) {
                return increment < 0 ? NEXT_PAGE_FIRST_ITEM : PREVIOUS_PAGE_LAST_ITEM;
            }
            return increment < 0 ? PREVIOUS_PAGE_LAST_ITEM : NEXT_PAGE_FIRST_ITEM;
        }
        return newIconIndex;
    }

    /**
     * Calculates icon that is closest to the vertical axis in reference to the current icon.
     *
     * Example of the check order for KEYCODE_DPAD_DOWN:
     * [  ][  ][  ][ X][  ][  ][  ]
     * [  ][  ][ 5][ 1][ 4][  ][  ]
     * [  ][10][ 7][ 2][ 6][ 9][  ]
     * [14][12][ 9][ 3][ 8][11][13]
     */
    // TODO: add unit tests to verify all permutation.
    private static int handleDpadVertical(int iconIndex, int cntX, int cntY,
            int [][] matrix, int increment) {
        int newIconIndex = NOOP;
        if(matrix == null) {
            throw new IllegalStateException("Dpad navigation requires a matrix.");
        }

        int xPos = -1;
        int yPos = -1;
        // Figure out the location of the icon.
        for (int i = 0; i< cntX; i++) {
            for (int j = 0; j < cntY; j++) {
                if (matrix[i][j] == iconIndex) {
                    xPos = i;
                    yPos = j;
                }
            }
        }

        if (DEBUG) {
            Log.v(TAG, String.format("\thandleDpadVertical: \t[x, y]=[%d, %d] iconIndex=%d",
                    xPos, yPos, iconIndex));
        }

        // Rule1: check first in the dpad direction
        for (int y = yPos + increment; 0 <= y && y <cntY && 0 <= y; y += increment) {
            if ((newIconIndex = inspectMatrix(xPos, y, cntX, cntY, matrix)) != NOOP
                    && newIconIndex != ALL_APPS_COLUMN) {
                return newIconIndex;
            }
        }

        // Rule2: check (xPos + increment, y_(1-n)),   (xPos - increment, y_(1-n))
        //              (xPos + 2*increment, y_(2-n))), (xPos - 2*increment, y_(2-n))
        int nextXPos1;
        int nextXPos2;
        boolean haveCrossedAllAppsColumn1 = false;
        boolean haveCrossedAllAppsColumn2 = false;
        int y = -1;
        for (int coeff = 1; coeff < cntX; coeff++) {
            nextXPos1 = xPos + coeff * increment;
            nextXPos2 = xPos - coeff * increment;
            y = yPos + increment * coeff;
            if (inspectMatrix(nextXPos1, y, cntX, cntY, matrix) == ALL_APPS_COLUMN) {
                haveCrossedAllAppsColumn1 = true;
            }
            if (inspectMatrix(nextXPos2, y, cntX, cntY, matrix) == ALL_APPS_COLUMN) {
                haveCrossedAllAppsColumn2 = true;
            }
            for (; 0 <= y && y < cntY; y = y + increment) {
                int offset1 = haveCrossedAllAppsColumn1 && y < cntY - 1 ? increment : 0;
                newIconIndex = inspectMatrix(nextXPos1 + offset1, y, cntX, cntY, matrix);
                if (newIconIndex != NOOP) {
                    return newIconIndex;
                }
                int offset2 = haveCrossedAllAppsColumn2 && y < cntY - 1 ? -increment : 0;
                newIconIndex = inspectMatrix(nextXPos2 + offset2, y, cntX, cntY, matrix);
                if (newIconIndex != NOOP) {
                    return newIconIndex;
                }
            }
        }
        return newIconIndex;
    }

    private static int handleMoveHome() {
        return CURRENT_PAGE_FIRST_ITEM;
    }

    private static int handleMoveEnd() {
        return CURRENT_PAGE_LAST_ITEM;
    }

    private static int handlePageDown(int pageIndex, int pageCount) {
        if (pageIndex < pageCount -1) {
            return NEXT_PAGE_FIRST_ITEM;
        }
        return CURRENT_PAGE_LAST_ITEM;
    }

    private static int handlePageUp(int pageIndex) {
        if (pageIndex > 0) {
            return PREVIOUS_PAGE_FIRST_ITEM;
        } else {
            return CURRENT_PAGE_FIRST_ITEM;
        }
    }

    //
    // Helper methods.
    //

    private static boolean isValid(int xPos, int yPos, int countX, int countY) {
        return (0 <= xPos && xPos < countX && 0 <= yPos && yPos < countY);
    }

    private static int inspectMatrix(int x, int y, int cntX, int cntY, int[][] matrix) {
        int newIconIndex = NOOP;
        if (isValid(x, y, cntX, cntY)) {
            if (matrix[x][y] != -1) {
                newIconIndex = matrix[x][y];
                if (DEBUG) {
                    Log.v(TAG, String.format("\t\tinspect: \t[x, y]=[%d, %d] %d",
                            x, y, matrix[x][y]));
                }
                return newIconIndex;
            }
        }
        return newIconIndex;
    }

    /**
     * Only used for debugging.
     */
    private static String getStringIndex(int index) {
        switch(index) {
            case NOOP: return "NOOP";
            case PREVIOUS_PAGE_FIRST_ITEM:  return "PREVIOUS_PAGE_FIRST";
            case PREVIOUS_PAGE_LAST_ITEM:   return "PREVIOUS_PAGE_LAST";
            case PREVIOUS_PAGE_RIGHT_COLUMN:return "PREVIOUS_PAGE_RIGHT_COLUMN";
            case CURRENT_PAGE_FIRST_ITEM:   return "CURRENT_PAGE_FIRST";
            case CURRENT_PAGE_LAST_ITEM:    return "CURRENT_PAGE_LAST";
            case NEXT_PAGE_FIRST_ITEM:      return "NEXT_PAGE_FIRST";
            case NEXT_PAGE_LEFT_COLUMN:     return "NEXT_PAGE_LEFT_COLUMN";
            case ALL_APPS_COLUMN:           return "ALL_APPS_COLUMN";
            default:
                return Integer.toString(index);
        }
    }

    /**
     * Only used for debugging.
     */
    private static void printMatrix(int[][] matrix) {
        Log.v(TAG, "\tprintMap:");
        int m = matrix.length;
        int n = matrix[0].length;

        for (int j=0; j < n; j++) {
            String colY = "\t\t";
            for (int i=0; i < m; i++) {
                colY +=  String.format("%3d",matrix[i][j]);
            }
            Log.v(TAG, colY);
        }
    }

    /**
     * @param edgeColumn the column of the new icon. either {@link #NEXT_PAGE_LEFT_COLUMN} or
     * {@link #NEXT_PAGE_RIGHT_COLUMN}
     * @return the view adjacent to {@param oldView} in the {@param nextPage} of the folder.
     */
    public static View getAdjacentChildInNextFolderPage(
            ShortcutAndWidgetContainer nextPage, View oldView, int edgeColumn) {
        final int newRow = ((CellLayout.LayoutParams) oldView.getLayoutParams()).cellY;

        int column = (edgeColumn == NEXT_PAGE_LEFT_COLUMN) ^ nextPage.invertLayoutHorizontally()
                ? 0 : (((CellLayout) nextPage.getParent()).getCountX() - 1);

        for (; column >= 0; column--) {
            for (int row = newRow; row >= 0; row--) {
                View newView = nextPage.getChildAt(column, row);
                if (newView != null) {
                    return newView;
                }
            }
        }
        return null;
    }
}
