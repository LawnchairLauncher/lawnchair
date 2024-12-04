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
import com.android.launcher3.celllayout.ItemConfiguration;
import com.android.launcher3.celllayout.MulticellReorderAlgorithm;
import com.android.launcher3.util.CellAndSpan;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.MultiTranslateDelegate;

/**
 * CellLayout that simulates a split in the middle for use in foldable devices.
 */
public class MultipageCellLayout extends CellLayout {

    private final Drawable mLeftBackground;
    private final Drawable mRightBackground;

    private boolean mSeamWasAdded = false;

    public MultipageCellLayout(Context context) {
        this(context, null);
    }

    public MultipageCellLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @Override
    protected int[] findNearestArea(int relativeXPos, int relativeYPos, int minSpanX, int minSpanY,
            int spanX, int spanY, boolean ignoreOccupied, int[] result, int[] resultSpan) {
        return createReorderAlgorithm().simulateSeam(
                () -> super.findNearestArea(relativeXPos, relativeYPos, minSpanX, minSpanY, spanX,
                        spanY, ignoreOccupied, result, resultSpan));
    }

    @Override
    public boolean isNearestDropLocationOccupied(int pixelX, int pixelY, int spanX, int spanY,
            View dragView, int[] result) {
        return createReorderAlgorithm().simulateSeam(
                () -> super.isNearestDropLocationOccupied(pixelX, pixelY, spanX, spanY, dragView,
                        result));
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
        setGridSize(mCountX, mCountY);
    }

    @Override
    boolean createAreaForResize(int cellX, int cellY, int spanX, int spanY, View dragView,
            int[] direction, boolean commit) {
        // Add seam to x position
        if (cellX >= mCountX / 2) {
            cellX++;
        }
        int finalCellX = cellX;
        return createReorderAlgorithm().simulateSeam(
                () -> super.createAreaForResize(finalCellX, cellY, spanX, spanY, dragView,
                        direction, commit));
    }

    @Override
    int[] performReorder(int pixelX, int pixelY, int minSpanX, int minSpanY, int spanX, int spanY,
            View dragView, int[] result, int[] resultSpan, int mode) {
        if (pixelX >= getWidth() / 2) {
            pixelX += getCellWidth();
        }
        return super.performReorder(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY, dragView,
                result, resultSpan, mode);
    }

    @Override
    public MulticellReorderAlgorithm createReorderAlgorithm() {
        return new MulticellReorderAlgorithm(this);
    }

    @Override
    public void copyCurrentStateToSolution(ItemConfiguration solution) {
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
    public int getUnusedHorizontalSpace() {
        return (int) Math.ceil(
                (getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - (mCountX * mCellWidth)
                        - ((mCountX - 1) * mBorderSpace.x)) / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float animatedWorkspaceMargin = mSpaceBetweenCellLayoutsPx * mSpringLoadedProgress;
        if (mLeftBackground.getAlpha() > 0) {
            canvas.save();
            canvas.translate(-animatedWorkspaceMargin, 0);
            mLeftBackground.setState(mBackground.getState());
            mLeftBackground.draw(canvas);
            canvas.restore();
        }
        if (mRightBackground.getAlpha() > 0) {
            canvas.save();
            canvas.translate(animatedWorkspaceMargin, 0);
            mRightBackground.setState(mBackground.getState());
            mRightBackground.draw(canvas);
            canvas.restore();
        }
        super.onDraw(canvas);
    }

    private void updateMarginBetweenCellLayouts() {
        for (int i = 0; i < mShortcutsAndWidgets.getChildCount(); i++) {
            View workspaceItem = mShortcutsAndWidgets.getChildAt(i);
            if (!(workspaceItem instanceof Reorderable)) {
                continue;
            }
            CellLayoutLayoutParams params =
                    (CellLayoutLayoutParams) workspaceItem.getLayoutParams();
            ((Reorderable) workspaceItem).getTranslateDelegate().setTranslation(
                    MultiTranslateDelegate.INDEX_CELLAYOUT_MULTIPAGE_SPACING,
                    getMarginForGivenCellParams(params),
                    0
            );

        }
    }

    @Override
    protected float getMarginForGivenCellParams(CellLayoutLayoutParams params) {
        float margin = mSpaceBetweenCellLayoutsPx * mSpringLoadedProgress;
        return params.getCellX() >= mCountX / 2 ? margin : -margin;
    }

    @Override
    public void setSpringLoadedProgress(float progress) {
        super.setSpringLoadedProgress(progress);
        updateMarginBetweenCellLayouts();
    }

    @Override
    public void setSpaceBetweenCellLayoutsPx(int spaceBetweenCellLayoutsPx) {
        super.setSpaceBetweenCellLayoutsPx(spaceBetweenCellLayoutsPx);
        updateMarginBetweenCellLayouts();
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
        int middlePointInPixels = rect.centerX();
        mLeftBackground.setBounds(rect.left, rect.top, middlePointInPixels, rect.bottom);
        mRightBackground.setBounds(middlePointInPixels, rect.top, rect.right, rect.bottom);
    }

    public void setCountX(int countX) {
        mCountX = countX;
    }

    public void setCountY(int countY) {
        mCountY = countY;
    }

    public void setOccupied(GridOccupancy occupied) {
        mOccupied = occupied;
    }

    public boolean isSeamWasAdded() {
        return mSeamWasAdded;
    }

    public void setSeamWasAdded(boolean seamWasAdded) {
        mSeamWasAdded = seamWasAdded;
    }
}
