/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;

import com.android.launcher.R;

/**
 * An abstraction of the original CellLayout which supports laying out items
 * which span multiple cells into a grid-like layout.  Also supports dimming
 * to give a preview of its contents.
 */
public class PagedViewCellLayout extends ViewGroup implements Page {
    static final String TAG = "PagedViewCellLayout";

    private int mCellCountX;
    private int mCellCountY;
    private int mCellWidth;
    private int mCellHeight;
    private int mWidthGap;
    private int mHeightGap;
    protected PagedViewCellLayoutChildren mChildren;
    private PagedViewCellLayoutChildren mHolographicChildren;
    private boolean mAllowHardwareLayerCreation = false;
    private boolean mCreateHardwareLayersIfAllowed = false;

    public PagedViewCellLayout(Context context) {
        this(context, null);
    }

    public PagedViewCellLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewCellLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setAlwaysDrawnWithCacheEnabled(false);

        // setup default cell parameters
        Resources resources = context.getResources();
        mCellWidth = resources.getDimensionPixelSize(R.dimen.apps_customize_cell_width);
        mCellHeight = resources.getDimensionPixelSize(R.dimen.apps_customize_cell_height);
        mCellCountX = LauncherModel.getCellCountX();
        mCellCountY = LauncherModel.getCellCountY();
        mWidthGap = mHeightGap = -1;

        mChildren = new PagedViewCellLayoutChildren(context);
        mChildren.setCellDimensions(mCellWidth, mCellHeight);
        mChildren.setGap(mWidthGap, mHeightGap);

        addView(mChildren);
        mHolographicChildren = new PagedViewCellLayoutChildren(context);
        mHolographicChildren.setAlpha(0f);
        mHolographicChildren.setCellDimensions(mCellWidth, mCellHeight);
        mHolographicChildren.setGap(mWidthGap, mHeightGap);

        addView(mHolographicChildren);
    }

    public void allowHardwareLayerCreation() {
        // This is called after the first time we launch into All Apps. Before that point,
        // there's no need for hardware layers here since there's a hardware layer set on the
        // parent, AllAppsTabbed, during the AllApps transition -- creating hardware layers here
        // before the animation is done slows down the animation
        if (!mAllowHardwareLayerCreation) {
            mAllowHardwareLayerCreation = true;
            if (mCreateHardwareLayersIfAllowed) {
                createHardwareLayers();
            }
        }
    }

    @Override
    public void setAlpha(float alpha) {
        mChildren.setAlpha(alpha);
        mHolographicChildren.setAlpha(1.0f - alpha);
    }

    void destroyHardwareLayers() {
        // called when a page is no longer visible (triggered by loadAssociatedPages ->
        // removeAllViewsOnPage)
        mCreateHardwareLayersIfAllowed = false;
        if (mAllowHardwareLayerCreation) {
            mChildren.destroyHardwareLayer();
            mHolographicChildren.destroyHardwareLayer();
        }
    }
    void createHardwareLayers() {
        // called when a page is visible (triggered by loadAssociatedPages -> syncPageItems)
        mCreateHardwareLayersIfAllowed = true;
        if (mAllowHardwareLayerCreation) {
            mChildren.createHardwareLayer();
            mHolographicChildren.createHardwareLayer();
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        // Cancel long press for all children
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            child.cancelLongPress();
        }
    }

    /** Syncs the holographic icon views to the child icon views */
    public void reloadHolographicIcons(boolean createHolographicOutlines) {
        if (createHolographicOutlines) {
            mChildren.loadHolographicOutlines();
        } else {
            mChildren.clearHolographicOutlines();
        }
    }

    public boolean addViewToCellLayout(View child, int index, int childId,
            PagedViewCellLayout.LayoutParams params) {
        final PagedViewCellLayout.LayoutParams lp = params;

        // Generate an id for each view, this assumes we have at most 256x256 cells
        // per workspace screen
        if (lp.cellX >= 0 && lp.cellX <= (mCellCountX - 1) &&
                lp.cellY >= 0 && (lp.cellY <= mCellCountY - 1)) {
            // If the horizontal or vertical span is set to -1, it is taken to
            // mean that it spans the extent of the CellLayout
            if (lp.cellHSpan < 0) lp.cellHSpan = mCellCountX;
            if (lp.cellVSpan < 0) lp.cellVSpan = mCellCountY;

            child.setId(childId);
            mChildren.addView(child, index, lp);

            if (child instanceof PagedViewIcon) {
                PagedViewIcon pagedViewIcon = (PagedViewIcon) child;
                if (mAllowHardwareLayerCreation) {
                    pagedViewIcon.disableCache();
                }
                mHolographicChildren.addView(pagedViewIcon.getHolographicOutlineView(),
                        index, lp);
            }
            return true;
        }
        return false;
    }

    @Override
    public void removeAllViewsOnPage() {
        mChildren.removeAllViews();
        mHolographicChildren.removeAllViews();
        destroyHardwareLayers();
    }

    @Override
    public void removeViewOnPageAt(int index) {
        mChildren.removeViewAt(index);
        mHolographicChildren.removeViewAt(index);
    }

    @Override
    public int getPageChildCount() {
        return mChildren.getChildCount();
    }

    @Override
    public View getChildOnPageAt(int i) {
        return mChildren.getChildAt(i);
    }

    @Override
    public int indexOfChildOnPage(View v) {
        return mChildren.indexOfChild(v);
    }

    public int getCellCountX() {
        return mCellCountX;
    }

    public int getCellCountY() {
        return mCellCountY;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // TODO: currently ignoring padding

        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);

        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSpecSize =  MeasureSpec.getSize(heightMeasureSpec);

        if (widthSpecMode == MeasureSpec.UNSPECIFIED || heightSpecMode == MeasureSpec.UNSPECIFIED) {
            throw new RuntimeException("CellLayout cannot have UNSPECIFIED dimensions");
        }

        final int cellWidth = mCellWidth;
        final int cellHeight = mCellHeight;

        int numWidthGaps = mCellCountX - 1;
        int numHeightGaps = mCellCountY - 1;

        int vSpaceLeft = heightSpecSize - mPaddingTop
                - mPaddingBottom - (cellHeight * mCellCountY);
        int heightGap = vSpaceLeft / numHeightGaps;

        int hSpaceLeft = widthSpecSize - mPaddingLeft
                - mPaddingRight - (cellWidth * mCellCountX);
        int widthGap = hSpaceLeft / numWidthGaps;

        // center it around the min gaps
        int minGap = Math.min(widthGap, heightGap);
        /*
        if (minGap < heightGap) {
            // vertical space has shrunken, so change padding accordingly
            paddingTop += ((heightGap - minGap) * (mCellCountY - 1)) / 2;
        } else if (minGap < widthGap) {
            // horizontal space has shrunken, so change padding accordingly
            paddingLeft += ((widthGap - minGap) * (mCellCountX - 1)) / 2;
        }
        */
        if (mWidthGap > -1 && mHeightGap > -1) {
            widthGap = mWidthGap;
            heightGap = mHeightGap;
        } else {
            widthGap = heightGap = minGap;
        }

        int newWidth = mPaddingLeft + mPaddingRight + (mCellCountX * cellWidth) +
            ((mCellCountX - 1) * widthGap);
        int newHeight = mPaddingTop + mPaddingBottom + (mCellCountY * cellHeight) +
            ((mCellCountY - 1) * heightGap);

        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            int childWidthMeasureSpec =
                MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY);
            int childheightMeasureSpec =
                MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY);
            child.measure(childWidthMeasureSpec, childheightMeasureSpec);
        }

        setMeasuredDimension(newWidth, newHeight);
    }

    int getContentWidth() {
        if (LauncherApplication.isScreenLarge()) {
            // Return the distance from the left edge of the content of the leftmost icon to
            // the right edge of the content of the rightmost icon

            // icons are centered within cells, find out how much padding that accounts for
            return getWidthBeforeFirstLayout() - (mCellWidth - Utilities.getIconContentSize());
        } else {
            return getWidthBeforeFirstLayout() + mPaddingLeft + mPaddingRight;
        }
    }

    int getContentHeight() {
        return mCellCountY * mCellHeight + (mCellCountY - 1) * Math.max(0, mHeightGap);
    }

    int getWidthBeforeFirstLayout() {
        return mCellCountX * mCellWidth + (mCellCountX - 1) * Math.max(0, mWidthGap);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (LauncherApplication.isScreenLarge()) {
                child.layout(0, 0, r - l, b - t);
            } else {
                child.layout(mPaddingLeft, mPaddingTop, getMeasuredWidth() - mPaddingRight,
                        getMeasuredHeight() - mPaddingBottom);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event) || true;
    }

    public void enableCenteredContent(boolean enabled) {
        mChildren.enableCenteredContent(enabled);
        mHolographicChildren.enableCenteredContent(enabled);
    }

    @Override
    protected void setChildrenDrawingCacheEnabled(boolean enabled) {
        mChildren.setChildrenDrawingCacheEnabled(enabled);
        mHolographicChildren.setChildrenDrawingCacheEnabled(enabled);
    }

    public void setCellCount(int xCount, int yCount) {
        mCellCountX = xCount;
        mCellCountY = yCount;
        requestLayout();
    }

    public void setGap(int widthGap, int heightGap) {
        mWidthGap = widthGap;
        mHeightGap = heightGap;
        mChildren.setGap(widthGap, heightGap);
        mHolographicChildren.setGap(widthGap, heightGap);
    }

    public int[] getCellCountForDimensions(int width, int height) {
        // Always assume we're working with the smallest span to make sure we
        // reserve enough space in both orientations
        int smallerSize = Math.min(mCellWidth, mCellHeight);

        // Always round up to next largest cell
        int spanX = (width + smallerSize) / smallerSize;
        int spanY = (height + smallerSize) / smallerSize;

        return new int[] { spanX, spanY };
    }

    /**
     * Start dragging the specified child
     *
     * @param child The child that is being dragged
     */
    void onDragChild(View child) {
        PagedViewCellLayout.LayoutParams lp = (PagedViewCellLayout.LayoutParams) child.getLayoutParams();
        lp.isDragging = true;
    }

    /**
     * Estimates the number of cells that the specified width would take up.
     */
    public int estimateCellHSpan(int width) {
        // TODO: we need to take widthGap into effect
        return (width + mCellWidth) / mCellWidth;
    }

    /**
     * Estimates the number of cells that the specified height would take up.
     */
    public int estimateCellVSpan(int height) {
        // TODO: we need to take heightGap into effect
        return (height + mCellHeight) / mCellHeight;
    }

    /**
     * Estimates the width that the number of vSpan cells will take up.
     */
    public int estimateCellWidth(int hSpan) {
        // TODO: we need to take widthGap into effect
        return hSpan * mCellWidth;
    }

    /**
     * Estimates the height that the number of vSpan cells will take up.
     */
    public int estimateCellHeight(int vSpan) {
        // TODO: we need to take heightGap into effect
        return vSpan * mCellHeight;
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new PagedViewCellLayout.LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof PagedViewCellLayout.LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new PagedViewCellLayout.LayoutParams(p);
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
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
         * Is this item currently being dragged
         */
        public boolean isDragging;

        // a data object that you can bind to this layout params
        private Object mTag;

        // X coordinate of the view in the layout.
        @ViewDebug.ExportedProperty
        int x;
        // Y coordinate of the view in the layout.
        @ViewDebug.ExportedProperty
        int y;

        public LayoutParams() {
            super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            cellHSpan = 1;
            cellVSpan = 1;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            cellHSpan = 1;
            cellVSpan = 1;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            cellHSpan = 1;
            cellVSpan = 1;
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.cellX = source.cellX;
            this.cellY = source.cellY;
            this.cellHSpan = source.cellHSpan;
            this.cellVSpan = source.cellVSpan;
        }

        public LayoutParams(int cellX, int cellY, int cellHSpan, int cellVSpan) {
            super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            this.cellX = cellX;
            this.cellY = cellY;
            this.cellHSpan = cellHSpan;
            this.cellVSpan = cellVSpan;
        }

        public void setup(int cellWidth, int cellHeight, int widthGap, int heightGap,
                int hStartPadding, int vStartPadding) {

            final int myCellHSpan = cellHSpan;
            final int myCellVSpan = cellVSpan;
            final int myCellX = cellX;
            final int myCellY = cellY;

            width = myCellHSpan * cellWidth + ((myCellHSpan - 1) * widthGap) -
                    leftMargin - rightMargin;
            height = myCellVSpan * cellHeight + ((myCellVSpan - 1) * heightGap) -
                    topMargin - bottomMargin;

            if (LauncherApplication.isScreenLarge()) {
                x = hStartPadding + myCellX * (cellWidth + widthGap) + leftMargin;
                y = vStartPadding + myCellY * (cellHeight + heightGap) + topMargin;
            } else {
                x = myCellX * (cellWidth + widthGap) + leftMargin;
                y = myCellY * (cellHeight + heightGap) + topMargin;
            }
        }

        public Object getTag() {
            return mTag;
        }

        public void setTag(Object tag) {
            mTag = tag;
        }

        public String toString() {
            return "(" + this.cellX + ", " + this.cellY + ", " +
                this.cellHSpan + ", " + this.cellVSpan + ")";
        }
    }
}

interface Page {
    public int getPageChildCount();
    public View getChildOnPageAt(int i);
    public void removeAllViewsOnPage();
    public void removeViewOnPageAt(int i);
    public int indexOfChildOnPage(View v);
}
