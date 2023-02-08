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
package com.android.launcher3;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.util.CellAndSpan;
import com.android.launcher3.util.GridOccupancy;

import java.util.function.Supplier;

/**
 * CellLayout that simulates a split in the middle for use in foldable devices.
 */
public class MultipageCellLayout extends CellLayout {

    private final Drawable mLeftBackground;
    private final Drawable mRightBackground;

    private View mSeam;

    public MultipageCellLayout(Context context) {
        this(context, null);
    }

    public MultipageCellLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MultipageCellLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLeftBackground = getContext().getDrawable(R.drawable.bg_celllayout);
        mLeftBackground.setCallback(this);
        mLeftBackground.setAlpha(0);

        mRightBackground = getContext().getDrawable(R.drawable.bg_celllayout);
        mRightBackground.setCallback(this);
        mRightBackground.setAlpha(0);

        DeviceProfile deviceProfile = mActivity.getDeviceProfile();

        mCountX = deviceProfile.inv.numColumns * 2;
        mCountY = deviceProfile.inv.numRows;
        mSeam = new View(getContext());
        setGridSize(mCountX, mCountY);
    }

    @Override
    ItemConfiguration closestEmptySpaceReorder(int pixelX, int pixelY, int minSpanX, int minSpanY,
            int spanX, int spanY) {
        return simulateSeam(
                () -> super.closestEmptySpaceReorder(pixelX, pixelY, minSpanX, minSpanY, spanX,
                        spanY));
    }

    @Override
    protected ItemConfiguration findReorderSolution(int pixelX, int pixelY, int minSpanX,
            int minSpanY, int spanX, int spanY, int[] direction, View dragView, boolean decX,
            ItemConfiguration solution) {
        return simulateSeam(
                () -> super.findReorderSolution(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY,
                        direction, dragView, decX, solution));
    }

    @Override
    public ItemConfiguration dropInPlaceSolution(int pixelX, int pixelY, int spanX, int spanY,
            View dragView) {
        return simulateSeam(
                () -> super.dropInPlaceSolution(pixelX, pixelY, spanX, spanY, dragView));
    }

    protected ItemConfiguration simulateSeam(Supplier<ItemConfiguration> f) {
        CellLayoutLayoutParams lp = new CellLayoutLayoutParams(mCountX / 2, 0, 1, mCountY);
        lp.canReorder = false;
        mCountX++;
        mShortcutsAndWidgets.addViewInLayout(mSeam, lp);
        GridOccupancy auxGrid = mOccupied;
        mOccupied = createGridOccupancy();
        mTmpOccupied = new GridOccupancy(mCountX, mCountY);

        ItemConfiguration res = removeSeamFromSolution(f.get());

        mCountX--;
        mShortcutsAndWidgets.removeViewInLayout(mSeam);
        mOccupied = auxGrid;
        mTmpOccupied = new GridOccupancy(mCountX, mCountY);
        return res;
    }

    private ItemConfiguration removeSeamFromSolution(ItemConfiguration solution) {
        solution.map.forEach((view, cell) -> cell.cellX = cell.cellX > mCountX / 2
                ? cell.cellX - 1 : cell.cellX);
        solution.cellX = solution.cellX > mCountX / 2 ? solution.cellX - 1 : solution.cellX;
        return solution;
    }

    GridOccupancy createGridOccupancy() {
        GridOccupancy grid = new GridOccupancy(mCountX, mCountY);
        for (int i = 0; i < mShortcutsAndWidgets.getChildCount(); i++) {
            View view = mShortcutsAndWidgets.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) view.getLayoutParams();
            int seamOffset = lp.getCellX() >= mCountX / 2 && lp.canReorder ? 1 : 0;
            grid.markCells(lp.getCellX() + seamOffset, lp.getCellY(), lp.cellHSpan, lp.cellVSpan,
                    true);
        }
        return grid;
    }

    @Override
    protected void copyCurrentStateToSolution(ItemConfiguration solution, boolean temp) {
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            int seamOffset = lp.getCellX() >= mCountX / 2 && lp.canReorder ? 1 : 0;
            CellAndSpan c = new CellAndSpan(lp.getCellX() + seamOffset, lp.getCellY(), lp.cellHSpan,
                    lp.cellVSpan);
            solution.add(child, c);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mLeftBackground.getAlpha() > 0) {
            mLeftBackground.setState(mBackground.getState());
            mLeftBackground.draw(canvas);
        }
        if (mRightBackground.getAlpha() > 0) {
            mRightBackground.setState(mBackground.getState());
            mRightBackground.draw(canvas);
        }

        super.onDraw(canvas);
    }

    @Override
    protected void updateBgAlpha() {
        mLeftBackground.setAlpha((int) (mSpringLoadedProgress * 255));
        mRightBackground.setAlpha((int) (mSpringLoadedProgress * 255));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        Rect rect = mBackground.getBounds();
        mLeftBackground.setBounds(rect.left, rect.top, rect.right / 2 - 20, rect.bottom);
        mRightBackground.setBounds(rect.right / 2 + 20, rect.top, rect.right, rect.bottom);
    }
}
