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
package com.android.launcher3.celllayout;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.ViewDebug;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

/**
 * Represents the logic of where a view is in a CellLayout and its size
 */
public class CellLayoutLayoutParams extends ViewGroup.MarginLayoutParams {

    public int screenId = -1;

    /**
     * Horizontal location of the item in the grid.
     */
    @ViewDebug.ExportedProperty
    public int cellX;

    /**
     * Vertical location of the item in the grid.
     */
    @ViewDebug.ExportedProperty
    public int cellY;

    /**
     * Temporary horizontal location of the item in the grid during reorder
     */
    public int tmpCellX;

    /**
     * Temporary vertical location of the item in the grid during reorder
     */
    public int tmpCellY;

    /**
     * Indicates that the temporary coordinates should be used to layout the items
     */
    public boolean useTmpCoords;

    /**
     * Number of cells spanned horizontally by the item.
     */
    @ViewDebug.ExportedProperty
    public int cellHSpan;

    /**
     * Number of cells spanned vertically by the item.
     */
    @ViewDebug.ExportedProperty
    public int cellVSpan;

    /**
     * Indicates whether the item will set its x, y, width and height parameters freely,
     * or whether these will be computed based on cellX, cellY, cellHSpan and cellVSpan.
     */
    public boolean isLockedToGrid = true;

    /**
     * Indicates whether this item can be reordered. Always true except in the case of the
     * the AllApps button and QSB place holder.
     */
    public boolean canReorder = true;

    // X coordinate of the view in the layout.
    @ViewDebug.ExportedProperty
    public int x;
    // Y coordinate of the view in the layout.
    @ViewDebug.ExportedProperty
    public int y;

    public boolean dropped;

    public CellLayoutLayoutParams(Context c, AttributeSet attrs) {
        super(c, attrs);
        cellHSpan = 1;
        cellVSpan = 1;
    }

    public CellLayoutLayoutParams(ViewGroup.LayoutParams source) {
        super(source);
        cellHSpan = 1;
        cellVSpan = 1;
    }

    public CellLayoutLayoutParams(CellLayoutLayoutParams source) {
        super(source);
        this.cellX = source.cellX;
        this.cellY = source.cellY;
        this.cellHSpan = source.cellHSpan;
        this.cellVSpan = source.cellVSpan;
        this.screenId = source.screenId;
        this.tmpCellX = source.tmpCellX;
        this.tmpCellY = source.tmpCellY;
        this.useTmpCoords = source.useTmpCoords;
    }

    public CellLayoutLayoutParams(int cellX, int cellY, int cellHSpan, int cellVSpan,
            int screenId) {
        super(CellLayoutLayoutParams.MATCH_PARENT, CellLayoutLayoutParams.MATCH_PARENT);
        this.cellX = cellX;
        this.cellY = cellY;
        this.cellHSpan = cellHSpan;
        this.cellVSpan = cellVSpan;
        this.screenId = screenId;
    }

    /**
     * Updates the {@link CellLayoutLayoutParams} with the right measures using their
     * full/invariant device profile sizes.
     */
    public void setup(int cellWidth, int cellHeight, boolean invertHorizontally, int colCount,
            int rowCount, Point borderSpace, @Nullable Rect inset) {
        setup(cellWidth, cellHeight, invertHorizontally, colCount, rowCount, 1.0f, 1.0f,
                borderSpace, inset);
    }

    /**
     * Use this method, as opposed to {@link #setup(int, int, boolean, int, int, Point, Rect)},
     * if the view needs to be scaled.
     *
     * ie. In multi-window mode, we setup widgets so that they are measured and laid out
     * using their full/invariant device profile sizes.
     */
    public void setup(int cellWidth, int cellHeight, boolean invertHorizontally, int colCount,
            int rowCount, float cellScaleX, float cellScaleY, Point borderSpace,
            @Nullable Rect inset) {
        if (isLockedToGrid) {
            final int myCellHSpan = cellHSpan;
            final int myCellVSpan = cellVSpan;
            int myCellX = useTmpCoords ? tmpCellX : cellX;
            int myCellY = useTmpCoords ? tmpCellY : cellY;

            if (invertHorizontally) {
                myCellX = colCount - myCellX - cellHSpan;
            }

            int hBorderSpacing = (myCellHSpan - 1) * borderSpace.x;
            int vBorderSpacing = (myCellVSpan - 1) * borderSpace.y;

            float myCellWidth = ((myCellHSpan * cellWidth) + hBorderSpacing) / cellScaleX;
            float myCellHeight = ((myCellVSpan * cellHeight) + vBorderSpacing) / cellScaleY;

            width = Math.round(myCellWidth) - leftMargin - rightMargin;
            height = Math.round(myCellHeight) - topMargin - bottomMargin;
            x = leftMargin + (myCellX * cellWidth) + (myCellX * borderSpace.x);
            y = topMargin + (myCellY * cellHeight) + (myCellY * borderSpace.y);

            if (inset != null) {
                x -= inset.left;
                y -= inset.top;
                width += inset.left + inset.right;
                height += inset.top + inset.bottom;
            }
        }
    }

    /**
     * Sets the position to the provided point
     */
    public void setCellXY(Point point) {
        cellX = point.x;
        cellY = point.y;
    }

    /**
     * @return the string representation of the position of the {@link CellLayoutLayoutParams}
     */
    public String toString() {
        return "(" + this.cellX + ", " + this.cellY + ")";
    }
}
