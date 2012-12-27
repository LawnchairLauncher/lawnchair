/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.cyanogenmod.trebuchet;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.cyanogenmod.trebuchet.preference.PreferencesProvider;

import java.util.Arrays;

public class Hotseat extends PagedView {
    private int mCellCount;

    private boolean mTransposeLayoutWithOrientation;
    private boolean mIsLandscape;

    private float[] mTempCellLayoutCenterCoordinates = new float[2];
    private Matrix mTempInverseMatrix = new Matrix();

    private static final int DEFAULT_CELL_COUNT = 5;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mFadeInAdjacentScreens = false;
        mHandleScrollIndicator = true;

        int hotseatPages = PreferencesProvider.Interface.Dock.getNumberPages();
        int defaultPage = PreferencesProvider.Interface.Dock.getDefaultPage(hotseatPages / 2);


        mCurrentPage = defaultPage;

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Hotseat, defStyle, 0);
        mTransposeLayoutWithOrientation =
                context.getResources().getBoolean(R.bool.hotseat_transpose_layout_with_orientation);
        mIsLandscape = context.getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;
        mCellCount = a.getInt(R.styleable.Hotseat_cellCount, DEFAULT_CELL_COUNT);
        int cellCount = PreferencesProvider.Interface.Dock.getNumberIcons(0);
        if (cellCount > 0) {
            mCellCount = cellCount;
        }

        mVertical = hasVerticalHotseat();


        float childrenScale = PreferencesProvider.Interface.Dock.getIconScale(
                getResources().getInteger(R.integer.hotseat_item_scale_percentage)) / 100f;

        LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        for (int i = 0; i < hotseatPages; i++) {
            CellLayout cl = (CellLayout) inflater.inflate(R.layout.hotseat_page, null);
            cl.setChildrenScale(childrenScale);
            cl.setGridSize((!hasVerticalHotseat() ? mCellCount : 1), (hasVerticalHotseat() ? mCellCount : 1));
            addView(cl);
        }

        // No data needed
        setDataIsReady();

        setOnKeyListener(new HotseatIconKeyEventListener());
    }

    public boolean hasPage(View view) {
        for (int i = 0; i < getChildCount(); i++) {
            if (view == getChildAt(i)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVerticalHotseat() {
        return (mIsLandscape && mTransposeLayoutWithOrientation);
    }

    /* Get the orientation invariant order of the item in the hotseat for persistence. */
    int getOrderInHotseat(int x, int y) {
        return hasVerticalHotseat() ? (mCellCount - y - 1) : x;
    }
    /* Get the orientation specific coordinates given an invariant order in the hotseat. */
    int getCellXFromOrder(int rank) {
        return hasVerticalHotseat() ? 0 : rank;
    }
    int getCellYFromOrder(int rank) {
        return hasVerticalHotseat() ? (mCellCount - rank - 1) : 0;
    }
    int getScreenFromOrder(int screen) {
        return hasVerticalHotseat() ? (getChildCount() - screen - 1) : screen;
    }

    /*
     *
     * Convert the 2D coordinate xy from the parent View's coordinate space to this CellLayout's
     * coordinate space. The argument xy is modified with the return result.
     *
     * if cachedInverseMatrix is not null, this method will just use that matrix instead of
     * computing it itself; we use this to avoid redundant matrix inversions in
     * findMatchingPageForDragOver
     *
     */
    void mapPointFromSelfToChild(View v, float[] xy, Matrix cachedInverseMatrix) {
        if (cachedInverseMatrix == null) {
            v.getMatrix().invert(mTempInverseMatrix);
            cachedInverseMatrix = mTempInverseMatrix;
        }
        int scrollX = getScrollX();
        if (mNextPage != INVALID_PAGE) {
            scrollX = mScroller.getFinalX();
        }
        xy[0] = xy[0] + scrollX - v.getLeft();
        xy[1] = xy[1] + getScrollY() - v.getTop();
        cachedInverseMatrix.mapPoints(xy);
    }

    /**
     * Convert the 2D coordinate xy from this CellLayout's coordinate space to
     * the parent View's coordinate space. The argument xy is modified with the return result.
     */
    void mapPointFromChildToSelf(View v, float[] xy) {
        v.getMatrix().mapPoints(xy);
        int scrollX = getScrollX();
        if (mNextPage != INVALID_PAGE) {
            scrollX = mScroller.getFinalX();
        }
        xy[0] -= (scrollX - v.getLeft());
        xy[1] -= (getScrollY() - v.getTop());
    }

    /**
     * This method returns the CellLayout that is currently being dragged to. In order to drag
     * to a CellLayout, either the touch point must be directly over the CellLayout, or as a second
     * strategy, we see if the dragView is overlapping any CellLayout and choose the closest one
     *
     * Return null if no CellLayout is currently being dragged over
     */
    CellLayout findMatchingPageForDragOver(float originX, float originY, boolean exact) {
        // We loop through all the screens (ie CellLayouts) and see which ones overlap
        // with the item being dragged and then choose the one that's closest to the touch point
        final int screenCount = getChildCount();
        CellLayout bestMatchingScreen = null;
        float smallestDistSoFar = Float.MAX_VALUE;

        for (int i = 0; i < screenCount; i++) {
            CellLayout cl = (CellLayout) getChildAt(i);

            final float[] touchXy = {originX, originY};
            // Transform the touch coordinates to the CellLayout's local coordinates
            // If the touch point is within the bounds of the cell layout, we can return immediately
            cl.getMatrix().invert(mTempInverseMatrix);
            mapPointFromSelfToChild(cl, touchXy, mTempInverseMatrix);

            if (touchXy[0] >= 0 && touchXy[0] <= cl.getWidth() &&
                    touchXy[1] >= 0 && touchXy[1] <= cl.getHeight()) {
                return cl;
            }

            if (!exact) {
                // Get the center of the cell layout in screen coordinates
                final float[] cellLayoutCenter = mTempCellLayoutCenterCoordinates;
                cellLayoutCenter[0] = cl.getWidth()/2;
                cellLayoutCenter[1] = cl.getHeight()/2;
                mapPointFromChildToSelf(cl, cellLayoutCenter);

                touchXy[0] = originX;
                touchXy[1] = originY;

                // Calculate the distance between the center of the CellLayout
                // and the touch point
                float dist = Workspace.squaredDistance(touchXy, cellLayoutCenter);

                if (dist < smallestDistSoFar) {
                    smallestDistSoFar = dist;
                    bestMatchingScreen = cl;
                }
            }
        }
        return bestMatchingScreen;
    }

    public void setChildrenOutlineAlpha(float alpha) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getChildAt(i);
            cl.setBackgroundAlpha(alpha);
        }
    }

    /**
     * Return the current {@link CellLayout}, correctly picking the destination
     * screen while a scroll is in progress.
     */
    public CellLayout getCurrentDropLayout() {
        return (CellLayout) getChildAt(getNextPage());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        resetLayout();
    }

    void resetLayout() {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            cl.removeAllViewsInLayout();
        }
    }

    @Override
    public void syncPages() {
    }

    @Override
    public void syncPageItems(int page, boolean immediate) {
    }

    @Override
    protected void loadAssociatedPages(int page) {
    }
    @Override
    protected void loadAssociatedPages(int page, boolean immediateAndOnly) {
    }
}
