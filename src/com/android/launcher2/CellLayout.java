/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.launcher.R;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LayoutAnimationController;

import java.util.Arrays;

public class CellLayout extends ViewGroup implements Dimmable {
    static final String TAG = "CellLayout";

    private int mCellWidth;
    private int mCellHeight;

    private int mLeftPadding;
    private int mRightPadding;
    private int mTopPadding;
    private int mBottomPadding;

    private int mCountX;
    private int mCountY;

    private int mWidthGap;
    private int mHeightGap;

    private final Rect mRect = new Rect();
    private final RectF mRectF = new RectF();
    private final CellInfo mCellInfo = new CellInfo();

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private final int[] mTmpCellXY = new int[2];
    private final int[] mTmpPoint = new int[2];
    private final PointF mTmpPointF = new PointF();

    boolean[][] mOccupied;

    private OnTouchListener mInterceptTouchListener;

    private float mBackgroundAlpha;
    private final Rect mBackgroundLayoutRect = new Rect();

    private Drawable mBackground;
    private Drawable mBackgroundMini;
    private Drawable mBackgroundMiniHover;
    // If we're actively dragging something over this screen and it's small, mHover is true
    private boolean mHover = false;

    private final Point mDragCenter = new Point();

    private Drawable mDragRectDrawable;

    // These arrays are used to implement the drag visualization on x-large screens.
    // They are used as circular arrays, indexed by mDragRectCurrent.
    private Rect[] mDragRects = new Rect[8];
    private int[] mDragRectAlphas = new int[mDragRects.length];
    private InterruptibleInOutAnimator[] mDragRectAnims =
        new InterruptibleInOutAnimator[mDragRects.length];

    // Used as an index into the above 3 arrays; indicates which is the most current value.
    private int mDragRectCurrent = 0;

    private Drawable mCrosshairsDrawable = null;
    private ValueAnimator mCrosshairsAnimator = null;
    private float mCrosshairsVisibility = 0.0f;

    // When a drag operation is in progress, holds the nearest cell to the touch point
    private final int[] mDragCell = new int[2];

    private final WallpaperManager mWallpaperManager;

    public CellLayout(Context context) {
        this(context, null);
    }

    public CellLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CellLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // A ViewGroup usually does not draw, but CellLayout needs to draw a rectangle to show
        // the user where a dragged item will land when dropped.
        setWillNotDraw(false);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CellLayout, defStyle, 0);

        mCellWidth = a.getDimensionPixelSize(R.styleable.CellLayout_cellWidth, 10);
        mCellHeight = a.getDimensionPixelSize(R.styleable.CellLayout_cellHeight, 10);

        mLeftPadding =
            a.getDimensionPixelSize(R.styleable.CellLayout_xAxisStartPadding, 10);
        mRightPadding =
            a.getDimensionPixelSize(R.styleable.CellLayout_xAxisEndPadding, 10);
        mTopPadding =
            a.getDimensionPixelSize(R.styleable.CellLayout_yAxisStartPadding, 10);
        mBottomPadding =
            a.getDimensionPixelSize(R.styleable.CellLayout_yAxisEndPadding, 10);

        mCountX = LauncherModel.getCellCountX();
        mCountY = LauncherModel.getCellCountY();
        mOccupied = new boolean[mCountX][mCountY];

        a.recycle();

        setAlwaysDrawnWithCacheEnabled(false);

        mWallpaperManager = WallpaperManager.getInstance(context);

        if (LauncherApplication.isScreenXLarge()) {
            final Resources res = getResources();

            mBackgroundMini = res.getDrawable(R.drawable.mini_home_screen_bg);
            mBackgroundMini.setFilterBitmap(true);
            mBackground = res.getDrawable(R.drawable.home_screen_bg);
            mBackground.setFilterBitmap(true);
            mBackgroundMiniHover = res.getDrawable(R.drawable.mini_home_screen_bg_hover);
            mBackgroundMiniHover.setFilterBitmap(true);

            // Initialize the data structures used for the drag visualization.

            mDragRectDrawable = res.getDrawable(R.drawable.rounded_rect_green);
            mCrosshairsDrawable = res.getDrawable(R.drawable.gardening_crosshairs);
            Interpolator interp = new DecelerateInterpolator(2.5f); // Quint ease out

            // Set up the animation for fading the crosshairs in and out
            int animDuration = res.getInteger(R.integer.config_crosshairsFadeInTime);
            mCrosshairsAnimator = new ValueAnimator<Float>(animDuration);
            mCrosshairsAnimator.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    mCrosshairsVisibility = ((Float) animation.getAnimatedValue()).floatValue();
                    CellLayout.this.invalidate();
                }
            });
            mCrosshairsAnimator.setInterpolator(interp);

            for (int i = 0; i < mDragRects.length; i++) {
                mDragRects[i] = new Rect();
            }

            // When dragging things around the home screens, we show a green outline of
            // where the item will land. The outlines gradually fade out, leaving a trail
            // behind the drag path.
            // Set up all the animations that are used to implement this fading.
            final int duration = res.getInteger(R.integer.config_dragOutlineFadeTime);
            final int fromAlphaValue = 0;
            final int toAlphaValue = res.getInteger(R.integer.config_dragOutlineMaxAlpha);
            for (int i = 0; i < mDragRectAnims.length; i++) {
                final InterruptibleInOutAnimator anim =
                    new InterruptibleInOutAnimator(duration, fromAlphaValue, toAlphaValue);
                anim.setInterpolator(interp);
                final int thisIndex = i;
                anim.addUpdateListener(new AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mDragRectAlphas[thisIndex] = (Integer) animation.getAnimatedValue();
                        CellLayout.this.invalidate(mDragRects[thisIndex]);
                    }
                });
                mDragRectAnims[i] = anim;
            }
        }
    }

    public void setHover(boolean value) {
        if (mHover != value) {
            invalidate();
        }
        mHover = value;
    }

    private void animateCrosshairsTo(float value) {
        final ValueAnimator anim = mCrosshairsAnimator;
        long fullDuration = getResources().getInteger(R.integer.config_crosshairsFadeInTime);
        anim.setDuration(fullDuration - anim.getCurrentPlayTime());
        anim.setValues(mCrosshairsVisibility, value);
        anim.cancel();
        anim.start();
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        if (mBackgroundAlpha > 0.0f) {
            Drawable bg;
            if (mHover && getScaleX() < 0.5f) {
                bg = mBackgroundMiniHover;
            } else if (getScaleX() < 0.5f) {
                bg = mBackgroundMini;
            } else {
                bg = mBackground;
            }
            bg.setAlpha((int) (mBackgroundAlpha * 255));
            bg.draw(canvas);
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCrosshairsVisibility > 0.0f) {
            final int countX = mCountX;
            final int countY = mCountY;

            final float MAX_ALPHA = 0.4f;
            final int MAX_VISIBLE_DISTANCE = 600;
            final float DISTANCE_MULTIPLIER = 0.002f;

            final Drawable d = mCrosshairsDrawable;
            final int width = d.getIntrinsicWidth();
            final int height = d.getIntrinsicHeight();

            int x = getLeftPadding() - (mWidthGap / 2) - (width / 2);
            for (int col = 0; col <= countX; col++) {
                int y = getTopPadding() - (mHeightGap / 2) - (height / 2);
                for (int row = 0; row <= countY; row++) {
                    mTmpPointF.set(x - mDragCenter.x, y - mDragCenter.y);
                    float dist = mTmpPointF.length();
                    // Crosshairs further from the drag point are more faint
                    float alpha = Math.min(MAX_ALPHA,
                            DISTANCE_MULTIPLIER * (MAX_VISIBLE_DISTANCE - dist));
                    if (alpha > 0.0f) {
                        d.setBounds(x, y, x + width, y + height);
                        d.setAlpha((int) (alpha * 255 * mCrosshairsVisibility));
                        d.draw(canvas);
                    }
                    y += mCellHeight + mHeightGap;
                }
                x += mCellWidth + mWidthGap;
            }

            for (int i = 0; i < mDragRects.length; i++) {
                int alpha = mDragRectAlphas[i];
                if (alpha > 0) {
                    mDragRectDrawable.setAlpha(alpha);
                    mDragRectDrawable.setBounds(mDragRects[i]);
                    mDragRectDrawable.draw(canvas);
                }
            }
        }
    }

    public void setDimmableProgress(float progress) {
        for (int i = 0; i < getChildCount(); i++) {
            Dimmable d = (Dimmable) getChildAt(i);
            d.setDimmableProgress(progress);
        }
    }

    public float getDimmableProgress() {
        if (getChildCount() > 0) {
            return ((Dimmable) getChildAt(0)).getDimmableProgress();
        }
        return 0.0f;
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

    public void setOnInterceptTouchListener(View.OnTouchListener listener) {
        mInterceptTouchListener = listener;
    }

    int getCountX() {
        return mCountX;
    }

    int getCountY() {
        return mCountY;
    }

    public boolean addViewToCellLayout(View child, int index, int childId, LayoutParams params) {
        final LayoutParams lp = params;

        // Generate an id for each view, this assumes we have at most 256x256 cells
        // per workspace screen
        if (lp.cellX >= 0 && lp.cellX <= mCountX - 1 && lp.cellY >= 0 && lp.cellY <= mCountY - 1) {
            // If the horizontal or vertical span is set to -1, it is taken to
            // mean that it spans the extent of the CellLayout
            if (lp.cellHSpan < 0) lp.cellHSpan = mCountX;
            if (lp.cellVSpan < 0) lp.cellVSpan = mCountY;

            child.setId(childId);

            // We might be in the middle or end of shrinking/fading to a dimmed view
            // Make sure this view's alpha is set the same as all the rest of the views
            child.setAlpha(getAlpha());
            addView(child, index, lp);

            markCellsAsOccupiedForView(child);

            return true;
        }
        return false;
    }

    @Override
    public void removeAllViews() {
        clearOccupiedCells();
    }

    @Override
    public void removeAllViewsInLayout() {
        clearOccupiedCells();
    }

    @Override
    public void removeView(View view) {
        markCellsAsUnoccupiedForView(view);
        super.removeView(view);
    }

    @Override
    public void removeViewAt(int index) {
        markCellsAsUnoccupiedForView(getChildAt(index));
        super.removeViewAt(index);
    }

    @Override
    public void removeViewInLayout(View view) {
        markCellsAsUnoccupiedForView(view);
        super.removeViewInLayout(view);
    }

    @Override
    public void removeViews(int start, int count) {
        for (int i = start; i < start + count; i++) {
            markCellsAsUnoccupiedForView(getChildAt(i));
        }
        super.removeViews(start, count);
    }

    @Override
    public void removeViewsInLayout(int start, int count) {
        for (int i = start; i < start + count; i++) {
            markCellsAsUnoccupiedForView(getChildAt(i));
        }
        super.removeViewsInLayout(start, count);
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
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mCellInfo.screen = ((ViewGroup) getParent()).indexOfChild(this);
    }

    public void setTagToCellInfoForPoint(int touchX, int touchY) {
        final CellInfo cellInfo = mCellInfo;
        final Rect frame = mRect;
        final int x = touchX + mScrollX;
        final int y = touchY + mScrollY;
        final int count = getChildCount();

        boolean found = false;
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);

            if ((child.getVisibility()) == VISIBLE || child.getAnimation() != null) {
                child.getHitRect(frame);
                if (frame.contains(x, y)) {
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    cellInfo.cell = child;
                    cellInfo.cellX = lp.cellX;
                    cellInfo.cellY = lp.cellY;
                    cellInfo.spanX = lp.cellHSpan;
                    cellInfo.spanY = lp.cellVSpan;
                    cellInfo.valid = true;
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            final int cellXY[] = mTmpCellXY;
            pointToCellExact(x, y, cellXY);

            cellInfo.cell = null;
            cellInfo.cellX = cellXY[0];
            cellInfo.cellY = cellXY[1];
            cellInfo.spanX = 1;
            cellInfo.spanY = 1;
            cellInfo.valid = cellXY[0] >= 0 && cellXY[1] >= 0 && cellXY[0] < mCountX &&
                    cellXY[1] < mCountY && !mOccupied[cellXY[0]][cellXY[1]];
        }
        setTag(cellInfo);
    }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mInterceptTouchListener != null && mInterceptTouchListener.onTouch(this, ev)) {
            return true;
        }
        final int action = ev.getAction();
        final CellInfo cellInfo = mCellInfo;

        if (action == MotionEvent.ACTION_DOWN) {
            setTagToCellInfoForPoint((int) ev.getX(), (int) ev.getY());
        } else if (action == MotionEvent.ACTION_UP) {
            cellInfo.cell = null;
            cellInfo.cellX = -1;
            cellInfo.cellY = -1;
            cellInfo.spanX = 0;
            cellInfo.spanY = 0;
            cellInfo.valid = false;
            setTag(cellInfo);
        }

        return false;
    }

    @Override
    public CellInfo getTag() {
        return (CellInfo) super.getTag();
    }

    /**
     * Check if the row 'y' is empty from columns 'left' to 'right', inclusive.
     */
    private static boolean isRowEmpty(int y, int left, int right, boolean[][] occupied) {
        for (int x = left; x <= right; x++) {
            if (occupied[x][y]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Given a point, return the cell that strictly encloses that point
     * @param x X coordinate of the point
     * @param y Y coordinate of the point
     * @param result Array of 2 ints to hold the x and y coordinate of the cell
     */
    void pointToCellExact(int x, int y, int[] result) {
        final int hStartPadding = getLeftPadding();
        final int vStartPadding = getTopPadding();

        result[0] = (x - hStartPadding) / (mCellWidth + mWidthGap);
        result[1] = (y - vStartPadding) / (mCellHeight + mHeightGap);

        final int xAxis = mCountX;
        final int yAxis = mCountY;

        if (result[0] < 0) result[0] = 0;
        if (result[0] >= xAxis) result[0] = xAxis - 1;
        if (result[1] < 0) result[1] = 0;
        if (result[1] >= yAxis) result[1] = yAxis - 1;
    }

    /**
     * Given a point, return the cell that most closely encloses that point
     * @param x X coordinate of the point
     * @param y Y coordinate of the point
     * @param result Array of 2 ints to hold the x and y coordinate of the cell
     */
    void pointToCellRounded(int x, int y, int[] result) {
        pointToCellExact(x + (mCellWidth / 2), y + (mCellHeight / 2), result);
    }

    /**
     * Given a cell coordinate, return the point that represents the upper left corner of that cell
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void cellToPoint(int cellX, int cellY, int[] result) {
        final int hStartPadding = getLeftPadding();
        final int vStartPadding = getTopPadding();

        result[0] = hStartPadding + cellX * (mCellWidth + mWidthGap);
        result[1] = vStartPadding + cellY * (mCellHeight + mHeightGap);
    }

    int getCellWidth() {
        return mCellWidth;
    }

    int getCellHeight() {
        return mCellHeight;
    }

    int getLeftPadding() {
        return mLeftPadding;
    }

    int getTopPadding() {
        return mTopPadding;
    }

    int getRightPadding() {
        return mRightPadding;
    }

    int getBottomPadding() {
        return mBottomPadding;
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

        int numWidthGaps = mCountX - 1;
        int numHeightGaps = mCountY - 1;

        int vSpaceLeft = heightSpecSize - mTopPadding - mBottomPadding - (cellHeight * mCountY);
        mHeightGap = vSpaceLeft / numHeightGaps;

        int hSpaceLeft = widthSpecSize - mLeftPadding - mRightPadding - (cellWidth * mCountX);
        mWidthGap = hSpaceLeft / numWidthGaps;

        // center it around the min gaps
        int minGap = Math.min(mWidthGap, mHeightGap);
        mWidthGap = mHeightGap = minGap;

        int count = getChildCount();

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.setup(cellWidth, cellHeight, mWidthGap, mHeightGap,
                    mLeftPadding, mTopPadding);

            int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            int childheightMeasureSpec = MeasureSpec.makeMeasureSpec(lp.height,
                    MeasureSpec.EXACTLY);

            child.measure(childWidthMeasureSpec, childheightMeasureSpec);
        }
        if (widthSpecMode == MeasureSpec.AT_MOST) {
            int newWidth = mLeftPadding + mRightPadding + (mCountX * cellWidth) +
                ((mCountX - 1) * minGap);
            int newHeight = mTopPadding + mBottomPadding + (mCountY * cellHeight) +
                ((mCountY - 1) * minGap);
            setMeasuredDimension(newWidth, newHeight);
        } else if (widthSpecMode == MeasureSpec.EXACTLY) {
            setMeasuredDimension(widthSpecSize, heightSpecSize);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {

                CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();

                int childLeft = lp.x;
                int childTop = lp.y;
                child.layout(childLeft, childTop, childLeft + lp.width, childTop + lp.height);

                if (lp.dropped) {
                    lp.dropped = false;

                    final int[] cellXY = mTmpCellXY;
                    getLocationOnScreen(cellXY);
                    mWallpaperManager.sendWallpaperCommand(getWindowToken(), "android.home.drop",
                            cellXY[0] + childLeft + lp.width / 2,
                            cellXY[1] + childTop + lp.height / 2, 0, null);
                }
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBackgroundLayoutRect.set(0, 0, w, h);
        if (mBackground != null) {
            mBackground.setBounds(mBackgroundLayoutRect);
        }
        if (mBackgroundMiniHover != null) {
            mBackgroundMiniHover.setBounds(mBackgroundLayoutRect);
        }
        if (mBackgroundMini != null) {
            mBackgroundMini.setBounds(mBackgroundLayoutRect);
        }
    }

    @Override
    protected void setChildrenDrawingCacheEnabled(boolean enabled) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View view = getChildAt(i);
            view.setDrawingCacheEnabled(enabled);
            // Update the drawing caches
            view.buildDrawingCache(true);
        }
    }

    @Override
    protected void setChildrenDrawnWithCacheEnabled(boolean enabled) {
        super.setChildrenDrawnWithCacheEnabled(enabled);
    }

    public float getBackgroundAlpha() {
        return mBackgroundAlpha;
    }

    public void setBackgroundAlpha(float alpha) {
        mBackgroundAlpha = alpha;
        invalidate();
    }

    // Need to return true to let the view system know we know how to handle alpha-- this is
    // because when our children have an alpha of 0.0f, they are still rendering their "dimmed"
    // versions
    @Override
    protected boolean onSetAlpha(int alpha) {
        return true;
    }

    public void setAlpha(float alpha) {
        setChildrenAlpha(alpha);
        super.setAlpha(alpha);
    }

    private void setChildrenAlpha(float alpha) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).setAlpha(alpha);
        }
    }

    private boolean isVacantIgnoring(
            int originX, int originY, int spanX, int spanY, View ignoreView) {
        if (ignoreView != null) {
            markCellsAsUnoccupiedForView(ignoreView);
        }
        boolean isVacant = true;
        for (int i = 0; i < spanY; i++) {
            if (!isRowEmpty(originY + i, originX, originX + spanX - 1, mOccupied)) {
                isVacant = false;
                break;
            }
        }
        if (ignoreView != null) {
            markCellsAsOccupiedForView(ignoreView);
        }
        return isVacant;
    }

    private boolean isVacant(int originX, int originY, int spanX, int spanY) {
        return isVacantIgnoring(originX, originY, spanX, spanY, null);
    }

    public View getChildAt(int x, int y) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if ((lp.cellX <= x) && (x < lp.cellX + lp.cellHSpan) &&
                    (lp.cellY <= y) && (y < lp.cellY + lp.cellHSpan)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Estimate the size that a child with the given dimensions will take in the layout.
     */
    void estimateChildSize(int minWidth, int minHeight, int[] result) {
        // Assuming it's placed at 0, 0, find where the bottom right cell will land
        rectToCell(minWidth, minHeight, result);

        // Then figure out the rect it will occupy
        cellToRect(0, 0, result[0], result[1], mRectF);
        result[0] = (int)mRectF.width();
        result[1] = (int)mRectF.height();
    }

    /**
     * Estimate where the top left cell of the dragged item will land if it is dropped.
     *
     * @param originX The X value of the top left corner of the item
     * @param originY The Y value of the top left corner of the item
     * @param spanX The number of horizontal cells that the item spans
     * @param spanY The number of vertical cells that the item spans
     * @param result The estimated drop cell X and Y.
     */
    void estimateDropCell(int originX, int originY, int spanX, int spanY, int[] result) {
        final int countX = mCountX;
        final int countY = mCountY;

        // pointToCellRounded takes the top left of a cell but will pad that with
        // cellWidth/2 and cellHeight/2 when finding the matching cell
        pointToCellRounded(originX, originY, result);

        // If the item isn't fully on this screen, snap to the edges
        int rightOverhang = result[0] + spanX - countX;
        if (rightOverhang > 0) {
            result[0] -= rightOverhang; // Snap to right
        }
        result[0] = Math.max(0, result[0]); // Snap to left
        int bottomOverhang = result[1] + spanY - countY;
        if (bottomOverhang > 0) {
            result[1] -= bottomOverhang; // Snap to bottom
        }
        result[1] = Math.max(0, result[1]); // Snap to top
    }

    void visualizeDropLocation(View view, int originX, int originY, int spanX, int spanY) {
        final int[] nearest = findNearestVacantArea(originX, originY, spanX, spanY, view, mDragCell);
        mDragCenter.set(originX + (view.getWidth() / 2), originY + (view.getHeight() / 2));

        if (nearest != null) {
            // Find the top left corner of the rect the object will occupy
            final int[] topLeft = mTmpPoint;
            cellToPoint(nearest[0], nearest[1], topLeft);

            // Need to copy these, because the next call to cellToPoint will overwrite them
            final int left = topLeft[0];
            final int top = topLeft[1];

            final Rect dragRect = mDragRects[mDragRectCurrent];

            if (dragRect.isEmpty() || left != dragRect.left || top != dragRect.top) {
                // Now find the bottom right
                final int[] bottomRight = mTmpPoint;
                cellToPoint(nearest[0] + spanX - 1, nearest[1] + spanY - 1, bottomRight);
                bottomRight[0] += mCellWidth;
                bottomRight[1] += mCellHeight;

                final int oldIndex = mDragRectCurrent;
                mDragRectCurrent = (oldIndex + 1) % mDragRects.length;

                mDragRects[mDragRectCurrent].set(left, top, bottomRight[0], bottomRight[1]);

                mDragRectAnims[oldIndex].animateOut();
                mDragRectAnims[mDragRectCurrent].animateIn();
            }
        }
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     *
     * @param pixelX The X location at which you want to search for a vacant area.
     * @param pixelY The Y location at which you want to search for a vacant area.
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param result Array in which to place the result, or null (in which case a new array will
     *        be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    int[] findNearestVacantArea(
            int pixelX, int pixelY, int spanX, int spanY, int[] result) {
        return findNearestVacantArea(pixelX, pixelY, spanX, spanY, null, result);
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     *
     * @param pixelX The X location at which you want to search for a vacant area.
     * @param pixelY The Y location at which you want to search for a vacant area.
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param ignoreView Considers space occupied by this view as unoccupied
     * @param result Previously returned value to possibly recycle.
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    int[] findNearestVacantArea(
            int pixelX, int pixelY, int spanX, int spanY, View ignoreView, int[] result) {
        // mark space take by ignoreView as available (method checks if ignoreView is null)
        markCellsAsUnoccupiedForView(ignoreView);

        // Keep track of best-scoring drop area
        final int[] bestXY = result != null ? result : new int[2];
        double bestDistance = Double.MAX_VALUE;

        final int countX = mCountX;
        final int countY = mCountY;
        final boolean[][] occupied = mOccupied;

        for (int x = 0; x < countX - (spanX - 1); x++) {
            inner:
            for (int y = 0; y < countY - (spanY - 1); y++) {
                for (int i = 0; i < spanX; i++) {
                    for (int j = 0; j < spanY; j++) {
                        if (occupied[x + i][y + j]) {
                            // small optimization: we can skip to below the row we just found
                            // an occupied cell
                            y += j;
                            continue inner;
                        }
                    }
                }
                final int[] cellXY = mTmpCellXY;
                cellToPoint(x, y, cellXY);

                double distance = Math.sqrt(Math.pow(cellXY[0] - pixelX, 2)
                        + Math.pow(cellXY[1] - pixelY, 2));
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    bestXY[0] = x;
                    bestXY[1] = y;
                }
            }
        }
        // re-mark space taken by ignoreView as occupied
        markCellsAsOccupiedForView(ignoreView);

        // Return null if no suitable location found
        if (bestDistance < Double.MAX_VALUE) {
            return bestXY;
        } else {
            return null;
        }
    }

    boolean existsEmptyCell() {
        return findCellForSpan(null, 1, 1);
    }

    /**
     * Finds the upper-left coordinate of the first rectangle in the grid that can
     * hold a cell of the specified dimensions. If intersectX and intersectY are not -1,
     * then this method will only return coordinates for rectangles that contain the cell
     * (intersectX, intersectY)
     *
     * @param cellXY The array that will contain the position of a vacant cell if such a cell
     *               can be found.
     * @param spanX The horizontal span of the cell we want to find.
     * @param spanY The vertical span of the cell we want to find.
     *
     * @return True if a vacant cell of the specified dimension was found, false otherwise.
     */
    boolean findCellForSpan(int[] cellXY, int spanX, int spanY) {
        return findCellForSpanThatIntersectsIgnoring(cellXY, spanX, spanY, -1, -1, null);
    }

    /**
     * Like above, but ignores any cells occupied by the item "ignoreView"
     *
     * @param cellXY The array that will contain the position of a vacant cell if such a cell
     *               can be found.
     * @param spanX The horizontal span of the cell we want to find.
     * @param spanY The vertical span of the cell we want to find.
     * @param ignoreView The home screen item we should treat as not occupying any space
     * @return
     */
    boolean findCellForSpanIgnoring(int[] cellXY, int spanX, int spanY, View ignoreView) {
        return findCellForSpanThatIntersectsIgnoring(cellXY, spanX, spanY, -1, -1, ignoreView);
    }

    /**
     * Like above, but if intersectX and intersectY are not -1, then this method will try to
     * return coordinates for rectangles that contain the cell [intersectX, intersectY]
     *
     * @param spanX The horizontal span of the cell we want to find.
     * @param spanY The vertical span of the cell we want to find.
     * @param ignoreView The home screen item we should treat as not occupying any space
     * @param intersectX The X coordinate of the cell that we should try to overlap
     * @param intersectX The Y coordinate of the cell that we should try to overlap
     *
     * @return True if a vacant cell of the specified dimension was found, false otherwise.
     */
    boolean findCellForSpanThatIntersects(int[] cellXY, int spanX, int spanY,
            int intersectX, int intersectY) {
        return findCellForSpanThatIntersectsIgnoring(
                cellXY, spanX, spanY, intersectX, intersectY, null);
    }

    /**
     * The superset of the above two methods
     */
    boolean findCellForSpanThatIntersectsIgnoring(int[] cellXY, int spanX, int spanY,
            int intersectX, int intersectY, View ignoreView) {
        // mark space take by ignoreView as available (method checks if ignoreView is null)
        markCellsAsUnoccupiedForView(ignoreView);

        boolean foundCell = false;
        while (true) {
            int startX = 0;
            if (intersectX >= 0) {
                startX = Math.max(startX, intersectX - (spanX - 1));
            }
            int endX = mCountX - (spanX - 1);
            if (intersectX >= 0) {
                endX = Math.min(endX, intersectX + (spanX - 1) + (spanX == 1 ? 1 : 0));
            }
            int startY = 0;
            if (intersectY >= 0) {
                startY = Math.max(startY, intersectY - (spanY - 1));
            }
            int endY = mCountY - (spanY - 1);
            if (intersectY >= 0) {
                endY = Math.min(endY, intersectY + (spanY - 1) + (spanY == 1 ? 1 : 0));
            }

            for (int x = startX; x < endX; x++) {
                inner:
                for (int y = startY; y < endY; y++) {
                    for (int i = 0; i < spanX; i++) {
                        for (int j = 0; j < spanY; j++) {
                            if (mOccupied[x + i][y + j]) {
                                // small optimization: we can skip to below the row we just found
                                // an occupied cell
                                y += j;
                                continue inner;
                            }
                        }
                    }
                    if (cellXY != null) {
                        cellXY[0] = x;
                        cellXY[1] = y;
                    }
                    foundCell = true;
                    break;
                }
            }
            if (intersectX == -1 && intersectY == -1) {
                break;
            } else {
                // if we failed to find anything, try again but without any requirements of
                // intersecting
                intersectX = -1;
                intersectY = -1;
                continue;
            }
        }

        // re-mark space taken by ignoreView as occupied
        markCellsAsOccupiedForView(ignoreView);
        return foundCell;
    }

    /**
     * Called when drag has left this CellLayout or has been completed (successfully or not)
     */
    void onDragExit() {
        // Invalidate the drag data
        mDragCell[0] = -1;
        mDragCell[1] = -1;

        setHover(false);
        invalidate();

        // Fade out the drag indicators
        if (mCrosshairsAnimator != null) {
            animateCrosshairsTo(0.0f);
        }

        mDragRectAnims[mDragRectCurrent].animateOut();
        mDragRectCurrent = (mDragRectCurrent + 1) % mDragRects.length;
        mDragRects[mDragRectCurrent].setEmpty();
    }

    /**
     * Mark a child as having been dropped.
     * At the beginning of the drag operation, the child may have been on another
     * screen, but it is reparented before this method is called.
     *
     * @param child The child that is being dropped
     */
    void onDropChild(View child) {
        if (child != null) {
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.isDragging = false;
            lp.dropped = true;
            child.requestLayout();
        }
        onDragExit();
    }

    void onDropAborted(View child) {
        if (child != null) {
            ((LayoutParams) child.getLayoutParams()).isDragging = false;
        }
        onDragExit();
    }

    /**
     * Start dragging the specified child
     *
     * @param child The child that is being dragged
     */
    void onDragChild(View child) {
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        lp.isDragging = true;
    }

    /**
     * A drag event has begun over this layout.
     * It may have begun over this layout (in which case onDragChild is called first),
     * or it may have begun on another layout.
     */
    void onDragEnter(View dragView) {
        // Fade in the drag indicators
        if (mCrosshairsAnimator != null) {
            animateCrosshairsTo(1.0f);
        }
    }

    /**
     * Computes a bounding rectangle for a range of cells
     *
     * @param cellX X coordinate of upper left corner expressed as a cell position
     * @param cellY Y coordinate of upper left corner expressed as a cell position
     * @param cellHSpan Width in cells
     * @param cellVSpan Height in cells
     * @param resultRect Rect into which to put the results
     */
    public void cellToRect(int cellX, int cellY, int cellHSpan, int cellVSpan, RectF resultRect) {
        final int cellWidth = mCellWidth;
        final int cellHeight = mCellHeight;
        final int widthGap = mWidthGap;
        final int heightGap = mHeightGap;

        final int hStartPadding = getLeftPadding();
        final int vStartPadding = getTopPadding();

        int width = cellHSpan * cellWidth + ((cellHSpan - 1) * widthGap);
        int height = cellVSpan * cellHeight + ((cellVSpan - 1) * heightGap);

        int x = hStartPadding + cellX * (cellWidth + widthGap);
        int y = vStartPadding + cellY * (cellHeight + heightGap);

        resultRect.set(x, y, x + width, y + height);
    }

    /**
     * Computes the required horizontal and vertical cell spans to always
     * fit the given rectangle.
     *
     * @param width Width in pixels
     * @param height Height in pixels
     * @param result An array of length 2 in which to store the result (may be null).
     */
    public int[] rectToCell(int width, int height, int[] result) {
        // Always assume we're working with the smallest span to make sure we
        // reserve enough space in both orientations.
        final Resources resources = getResources();
        int actualWidth = resources.getDimensionPixelSize(R.dimen.workspace_cell_width);
        int actualHeight = resources.getDimensionPixelSize(R.dimen.workspace_cell_height);
        int smallerSize = Math.min(actualWidth, actualHeight);

        // Always round up to next largest cell
        int spanX = (width + smallerSize) / smallerSize;
        int spanY = (height + smallerSize) / smallerSize;

        if (result == null) {
            return new int[] { spanX, spanY };
        }
        result[0] = spanX;
        result[1] = spanY;
        return result;
    }

    /**
     * Find the first vacant cell, if there is one.
     *
     * @param vacant Holds the x and y coordinate of the vacant cell
     * @param spanX Horizontal cell span.
     * @param spanY Vertical cell span.
     *
     * @return True if a vacant cell was found
     */
    public boolean getVacantCell(int[] vacant, int spanX, int spanY) {

        return findVacantCell(vacant, spanX, spanY, mCountX, mCountY, mOccupied);
    }

    static boolean findVacantCell(int[] vacant, int spanX, int spanY,
            int xCount, int yCount, boolean[][] occupied) {

        for (int x = 0; x < xCount; x++) {
            for (int y = 0; y < yCount; y++) {
                boolean available = !occupied[x][y];
out:            for (int i = x; i < x + spanX - 1 && x < xCount; i++) {
                    for (int j = y; j < y + spanY - 1 && y < yCount; j++) {
                        available = available && !occupied[i][j];
                        if (!available) break out;
                    }
                }

                if (available) {
                    vacant[0] = x;
                    vacant[1] = y;
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Update the array of occupied cells (mOccupied), and return a flattened copy of the array.
     */
    boolean[] getOccupiedCellsFlattened() {
        final int xCount = mCountX;
        final int yCount = mCountY;
        final boolean[][] occupied = mOccupied;

        final boolean[] flat = new boolean[xCount * yCount];
        for (int y = 0; y < yCount; y++) {
            for (int x = 0; x < xCount; x++) {
                flat[y * xCount + x] = occupied[x][y];
            }
        }

        return flat;
    }

    private void clearOccupiedCells() {
        for (int x = 0; x < mCountX; x++) {
            for (int y = 0; y < mCountY; y++) {
                mOccupied[x][y] = false;
            }
        }
    }

    public void onMove(View view, int newCellX, int newCellY) {
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        markCellsAsUnoccupiedForView(view);
        markCellsForView(newCellX, newCellY, lp.cellHSpan, lp.cellVSpan, true);
    }

    private void markCellsAsOccupiedForView(View view) {
        if (view == null || view.getParent() != this) return;
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        markCellsForView(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, true);
    }

    private void markCellsAsUnoccupiedForView(View view) {
        if (view == null || view.getParent() != this) return;
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        markCellsForView(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, false);
    }

    private void markCellsForView(int cellX, int cellY, int spanX, int spanY, boolean value) {
        for (int x = cellX; x < cellX + spanX && x < mCountX; x++) {
            for (int y = cellY; y < cellY + spanY && y < mCountY; y++) {
                mOccupied[x][y] = value;
            }
        }
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new CellLayout.LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof CellLayout.LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new CellLayout.LayoutParams(p);
    }

    public static class CellLayoutAnimationController extends LayoutAnimationController {
        public CellLayoutAnimationController(Animation animation, float delay) {
            super(animation, delay);
        }

        @Override
        protected long getDelayForView(View view) {
            return (int) (Math.random() * 150);
        }
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

        // X coordinate of the view in the layout.
        @ViewDebug.ExportedProperty
        int x;
        // Y coordinate of the view in the layout.
        @ViewDebug.ExportedProperty
        int y;

        boolean dropped;

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

        public String toString() {
            return "(" + this.cellX + ", " + this.cellY + ")";
        }
    }

    // This class stores info for two purposes:
    // 1. When dragging items (mDragInfo in Workspace), we store the View, its cellX & cellY,
    //    its spanX, spanY, and the screen it is on
    // 2. When long clicking on an empty cell in a CellLayout, we save information about the
    //    cellX and cellY coordinates and which page was clicked. We then set this as a tag on
    //    the CellLayout that was long clicked
    static final class CellInfo implements ContextMenu.ContextMenuInfo {
        View cell;
        int cellX = -1;
        int cellY = -1;
        int spanX;
        int spanY;
        int screen;
        boolean valid;

        @Override
        public String toString() {
            return "Cell[view=" + (cell == null ? "null" : cell.getClass())
                    + ", x=" + cellX + ", y=" + cellY + "]";
        }
    }
}
