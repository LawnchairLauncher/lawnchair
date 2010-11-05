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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;

/**
 * An abstraction of the original CellLayout which supports laying out items
 * which span multiple cells into a grid-like layout.  Also supports dimming
 * to give a preview of its contents.
 */
public class PagedViewCellLayout extends ViewGroup {
    static final String TAG = "PagedViewCellLayout";

    private float mHolographicAlpha;

    private boolean mCenterContent;

    private int mCellCountX;
    private int mCellCountY;
    private int mCellWidth;
    private int mCellHeight;
    private int mWidthGap;
    private int mHeightGap;
    private static int sDefaultCellDimensions = 96;

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
        mCellWidth = mCellHeight = sDefaultCellDimensions;
        mCellCountX = LauncherModel.getCellCountX();
        mCellCountY = LauncherModel.getCellCountY();
        mHolographicAlpha = 0.0f;
        mWidthGap = mHeightGap = -1;
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        return true;
    }

    @Override
    public void setAlpha(float alpha) {
        mHolographicAlpha = 1.0f - alpha;
        setChildrenAlpha(alpha);
        super.setAlpha(alpha);
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

            // We might be in the middle or end of shrinking/fading to a dimmed view
            // Make sure this view's alpha is set the same as all the rest of the views
            child.setAlpha(1.0f - mHolographicAlpha);

            addView(child, index, lp);
            return true;
        }
        return false;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (child != null) {
            Rect r = new Rect();
            child.getDrawingRect(r);
            requestRectangleOnScreen(r);
        }
    }

    @Override
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
        int paddingLeft = mPaddingLeft;
        int paddingTop = mPaddingTop;
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
            PagedViewCellLayout.LayoutParams lp =
                (PagedViewCellLayout.LayoutParams) child.getLayoutParams();
            lp.setup(cellWidth, cellHeight, widthGap, heightGap,
                    paddingLeft, paddingTop);

            int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(lp.width,
                    MeasureSpec.EXACTLY);
            int childheightMeasureSpec = MeasureSpec.makeMeasureSpec(lp.height,
                    MeasureSpec.EXACTLY);

            child.measure(childWidthMeasureSpec, childheightMeasureSpec);
        }

        setMeasuredDimension(newWidth, newHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();

        int offsetX = 0;
        if (mCenterContent) {
            // determine the max width of all the rows and center accordingly
            int maxRowWidth = 0;
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    PagedViewCellLayout.LayoutParams lp =
                        (PagedViewCellLayout.LayoutParams) child.getLayoutParams();
                    maxRowWidth = Math.max(maxRowWidth, lp.x + lp.width);
                }
            }
            offsetX = (getMeasuredWidth() / 2) - (maxRowWidth / 2);
        }

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                PagedViewCellLayout.LayoutParams lp =
                    (PagedViewCellLayout.LayoutParams) child.getLayoutParams();

                int childLeft = offsetX + lp.x;
                int childTop = lp.y;
                child.layout(childLeft, childTop, childLeft + lp.width, childTop + lp.height);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event) || true;
    }

    public void enableCenteredContent(boolean enabled) {
        mCenterContent = enabled;
    }

    @Override
    protected void setChildrenDrawingCacheEnabled(boolean enabled) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View view = getChildAt(i);
            view.setDrawingCacheEnabled(enabled);
            // Update the drawing caches
            if (!view.isHardwareAccelerated()) {
                view.buildDrawingCache(true);
            }
        }
    }

    public void setCellCount(int xCount, int yCount) {
        mCellCountX = xCount;
        mCellCountY = yCount;
        requestLayout();
    }

    public void setGap(int widthGap, int heightGap) {
        mWidthGap = widthGap;
        mHeightGap = heightGap;
    }

    public void setCellDimensions(int width, int height) {
        mCellWidth = width;
        mCellHeight = height;
        requestLayout();
    }

    public int getDefaultCellDimensions() {
        return sDefaultCellDimensions;
    }

    private void setChildrenAlpha(float alpha) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).setAlpha(alpha);
        }
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

            x = hStartPadding + myCellX * (cellWidth + widthGap) + leftMargin;
            y = vStartPadding + myCellY * (cellHeight + heightGap) + topMargin;
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
