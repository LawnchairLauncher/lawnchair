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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;

import com.android.launcher.R;
import com.android.launcher2.FolderIcon.FolderRingAnimator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class CellLayout extends ViewGroup {
    static final String TAG = "CellLayout";

    private int mOriginalCellWidth;
    private int mOriginalCellHeight;
    private int mCellWidth;
    private int mCellHeight;

    private int mCountX;
    private int mCountY;

    private int mOriginalWidthGap;
    private int mOriginalHeightGap;
    private int mWidthGap;
    private int mHeightGap;
    private int mMaxGap;

    private final Rect mRect = new Rect();
    private final CellInfo mCellInfo = new CellInfo();

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private final int[] mTmpXY = new int[2];
    private final int[] mTmpPoint = new int[2];
    private final PointF mTmpPointF = new PointF();
    int[] mTempLocation = new int[2];

    boolean[][] mOccupied;
    private boolean mLastDownOnOccupiedCell = false;

    private OnTouchListener mInterceptTouchListener;

    private ArrayList<FolderRingAnimator> mFolderOuterRings = new ArrayList<FolderRingAnimator>();
    private int[] mFolderLeaveBehindCell = {-1, -1};

    private int mForegroundAlpha = 0;
    private float mBackgroundAlpha;
    private float mBackgroundAlphaMultiplier = 1.0f;

    private Drawable mNormalBackground;
    private Drawable mActiveBackground;
    private Drawable mActiveGlowBackground;
    private Drawable mNormalBackgroundMini;
    private Drawable mNormalGlowBackgroundMini;
    private Drawable mActiveBackgroundMini;
    private Drawable mActiveGlowBackgroundMini;
    private Drawable mOverScrollForegroundDrawable;
    private Drawable mOverScrollLeft;
    private Drawable mOverScrollRight;
    private Rect mBackgroundRect;
    private Rect mForegroundRect;
    private Rect mGlowBackgroundRect;
    private float mGlowBackgroundScale;
    private float mGlowBackgroundAlpha;
    private int mForegroundPadding;

    private boolean mAcceptsDrops = true;
    // If we're actively dragging something over this screen, mIsDragOverlapping is true
    private boolean mIsDragOverlapping = false;
    private boolean mIsDragOccuring = false;
    private boolean mIsDefaultDropTarget = false;
    private final Point mDragCenter = new Point();

    // These arrays are used to implement the drag visualization on x-large screens.
    // They are used as circular arrays, indexed by mDragOutlineCurrent.
    private Point[] mDragOutlines = new Point[4];
    private float[] mDragOutlineAlphas = new float[mDragOutlines.length];
    private InterruptibleInOutAnimator[] mDragOutlineAnims =
            new InterruptibleInOutAnimator[mDragOutlines.length];

    // Used as an index into the above 3 arrays; indicates which is the most current value.
    private int mDragOutlineCurrent = 0;
    private final Paint mDragOutlinePaint = new Paint();

    private BubbleTextView mPressedOrFocusedIcon;

    private Drawable mCrosshairsDrawable = null;
    private InterruptibleInOutAnimator mCrosshairsAnimator = null;
    private float mCrosshairsVisibility = 0.0f;

    private HashMap<CellLayout.LayoutParams, ObjectAnimator> mReorderAnimators = new
            HashMap<CellLayout.LayoutParams, ObjectAnimator>();

    // When a drag operation is in progress, holds the nearest cell to the touch point
    private final int[] mDragCell = new int[2];

    private boolean mDragging = false;

    private TimeInterpolator mEaseOutInterpolator;
    private CellLayoutChildren mChildren;

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

        mOriginalCellWidth =
            mCellWidth = a.getDimensionPixelSize(R.styleable.CellLayout_cellWidth, 10);
        mOriginalCellHeight =
            mCellHeight = a.getDimensionPixelSize(R.styleable.CellLayout_cellHeight, 10);
        mWidthGap = mOriginalWidthGap = a.getDimensionPixelSize(R.styleable.CellLayout_widthGap, 0);
        mHeightGap = mOriginalHeightGap = a.getDimensionPixelSize(R.styleable.CellLayout_heightGap, 0);
        mMaxGap = a.getDimensionPixelSize(R.styleable.CellLayout_maxGap, 0);
        mCountX = LauncherModel.getCellCountX();
        mCountY = LauncherModel.getCellCountY();
        mOccupied = new boolean[mCountX][mCountY];

        a.recycle();

        setAlwaysDrawnWithCacheEnabled(false);

        final Resources res = getResources();

        mNormalBackground = res.getDrawable(R.drawable.homescreen_blue_normal_holo);
        mActiveBackground = res.getDrawable(R.drawable.homescreen_blue_strong_holo);
        mActiveGlowBackground = res.getDrawable(R.drawable.homescreen_blue_strong_holo);

        mNormalBackgroundMini = res.getDrawable(R.drawable.homescreen_small_blue);
        mNormalGlowBackgroundMini = res.getDrawable(R.drawable.homescreen_small_blue_strong);
        mActiveBackgroundMini = res.getDrawable(R.drawable.homescreen_small_blue_strong);
        mActiveGlowBackgroundMini = res.getDrawable(R.drawable.homescreen_small_blue_strong);
        mOverScrollLeft = res.getDrawable(R.drawable.overscroll_glow_left);
        mOverScrollRight = res.getDrawable(R.drawable.overscroll_glow_right);
        mForegroundPadding =
                res.getDimensionPixelSize(R.dimen.workspace_overscroll_drawable_padding);

        mNormalBackground.setFilterBitmap(true);
        mActiveBackground.setFilterBitmap(true);
        mActiveGlowBackground.setFilterBitmap(true);
        mNormalBackgroundMini.setFilterBitmap(true);
        mNormalGlowBackgroundMini.setFilterBitmap(true);
        mActiveBackgroundMini.setFilterBitmap(true);
        mActiveGlowBackgroundMini.setFilterBitmap(true);

        // Initialize the data structures used for the drag visualization.

        mCrosshairsDrawable = res.getDrawable(R.drawable.gardening_crosshairs);
        mEaseOutInterpolator = new DecelerateInterpolator(2.5f); // Quint ease out

        // Set up the animation for fading the crosshairs in and out
        int animDuration = res.getInteger(R.integer.config_crosshairsFadeInTime);
        mCrosshairsAnimator = new InterruptibleInOutAnimator(animDuration, 0.0f, 1.0f);
        mCrosshairsAnimator.getAnimator().addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                mCrosshairsVisibility = ((Float) animation.getAnimatedValue()).floatValue();
                invalidate();
            }
        });
        mCrosshairsAnimator.getAnimator().setInterpolator(mEaseOutInterpolator);

        mDragCell[0] = mDragCell[1] = -1;
        for (int i = 0; i < mDragOutlines.length; i++) {
            mDragOutlines[i] = new Point(-1, -1);
        }

        // When dragging things around the home screens, we show a green outline of
        // where the item will land. The outlines gradually fade out, leaving a trail
        // behind the drag path.
        // Set up all the animations that are used to implement this fading.
        final int duration = res.getInteger(R.integer.config_dragOutlineFadeTime);
        final float fromAlphaValue = 0;
        final float toAlphaValue = (float)res.getInteger(R.integer.config_dragOutlineMaxAlpha);

        Arrays.fill(mDragOutlineAlphas, fromAlphaValue);

        for (int i = 0; i < mDragOutlineAnims.length; i++) {
            final InterruptibleInOutAnimator anim =
                new InterruptibleInOutAnimator(duration, fromAlphaValue, toAlphaValue);
            anim.getAnimator().setInterpolator(mEaseOutInterpolator);
            final int thisIndex = i;
            anim.getAnimator().addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    final Bitmap outline = (Bitmap)anim.getTag();

                    // If an animation is started and then stopped very quickly, we can still
                    // get spurious updates we've cleared the tag. Guard against this.
                    if (outline == null) {
                        if (false) {
                            Object val = animation.getAnimatedValue();
                            Log.d(TAG, "anim " + thisIndex + " update: " + val +
                                     ", isStopped " + anim.isStopped());
                        }
                        // Try to prevent it from continuing to run
                        animation.cancel();
                    } else {
                        mDragOutlineAlphas[thisIndex] = (Float) animation.getAnimatedValue();
                        final int left = mDragOutlines[thisIndex].x;
                        final int top = mDragOutlines[thisIndex].y;
                        CellLayout.this.invalidate(left, top,
                                left + outline.getWidth(), top + outline.getHeight());
                    }
                }
            });
            // The animation holds a reference to the drag outline bitmap as long is it's
            // running. This way the bitmap can be GCed when the animations are complete.
            anim.getAnimator().addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if ((Float) ((ValueAnimator) animation).getAnimatedValue() == 0f) {
                        anim.setTag(null);
                    }
                }
            });
            mDragOutlineAnims[i] = anim;
        }

        mBackgroundRect = new Rect();
        mForegroundRect = new Rect();
        mGlowBackgroundRect = new Rect();
        setHoverScale(1.0f);
        setHoverAlpha(1.0f);

        mChildren = new CellLayoutChildren(context);
        mChildren.setCellDimensions(mCellWidth, mCellHeight, mWidthGap, mHeightGap);
        addView(mChildren);
    }

    static int widthInPortrait(Resources r, int numCells) {
        // We use this method from Workspace to figure out how many rows/columns Launcher should
        // have. We ignore the left/right padding on CellLayout because it turns out in our design
        // the padding extends outside the visible screen size, but it looked fine anyway.
        int cellWidth = r.getDimensionPixelSize(R.dimen.workspace_cell_width);
        int minGap = Math.min(r.getDimensionPixelSize(R.dimen.workspace_width_gap),
                r.getDimensionPixelSize(R.dimen.workspace_height_gap));

        return  minGap * (numCells - 1) + cellWidth * numCells;
    }

    static int heightInLandscape(Resources r, int numCells) {
        // We use this method from Workspace to figure out how many rows/columns Launcher should
        // have. We ignore the left/right padding on CellLayout because it turns out in our design
        // the padding extends outside the visible screen size, but it looked fine anyway.
        int cellHeight = r.getDimensionPixelSize(R.dimen.workspace_cell_height);
        int minGap = Math.min(r.getDimensionPixelSize(R.dimen.workspace_width_gap),
                r.getDimensionPixelSize(R.dimen.workspace_height_gap));

        return minGap * (numCells - 1) + cellHeight * numCells;
    }

    public void enableHardwareLayers() {
        mChildren.enableHardwareLayers();
    }

    public void setGridSize(int x, int y) {
        mCountX = x;
        mCountY = y;
        mOccupied = new boolean[mCountX][mCountY];
        requestLayout();
    }

    private void invalidateBubbleTextView(BubbleTextView icon) {
        final int padding = icon.getPressedOrFocusedBackgroundPadding();
        invalidate(icon.getLeft() + getPaddingLeft() - padding,
                icon.getTop() + getPaddingTop() - padding,
                icon.getRight() + getPaddingLeft() + padding,
                icon.getBottom() + getPaddingTop() + padding);
    }

    void setOverScrollAmount(float r, boolean left) {
        if (left && mOverScrollForegroundDrawable != mOverScrollLeft) {
            mOverScrollForegroundDrawable = mOverScrollLeft;
        } else if (!left && mOverScrollForegroundDrawable != mOverScrollRight) {
            mOverScrollForegroundDrawable = mOverScrollRight;
        }

        mForegroundAlpha = (int) Math.round((r * 255));
        mOverScrollForegroundDrawable.setAlpha(mForegroundAlpha);
        invalidate();
    }

    void setPressedOrFocusedIcon(BubbleTextView icon) {
        // We draw the pressed or focused BubbleTextView's background in CellLayout because it
        // requires an expanded clip rect (due to the glow's blur radius)
        BubbleTextView oldIcon = mPressedOrFocusedIcon;
        mPressedOrFocusedIcon = icon;
        if (oldIcon != null) {
            invalidateBubbleTextView(oldIcon);
        }
        if (mPressedOrFocusedIcon != null) {
            invalidateBubbleTextView(mPressedOrFocusedIcon);
        }
    }

    public CellLayoutChildren getChildrenLayout() {
        if (getChildCount() > 0) {
            return (CellLayoutChildren) getChildAt(0);
        }
        return null;
    }

    public void setIsDefaultDropTarget(boolean isDefaultDropTarget) {
        if (mIsDefaultDropTarget != isDefaultDropTarget) {
            mIsDefaultDropTarget = isDefaultDropTarget;
            invalidate();
        }
    }

    void setIsDragOccuring(boolean isDragOccuring) {
        if (mIsDragOccuring != isDragOccuring) {
            mIsDragOccuring = isDragOccuring;
            invalidate();
        }
    }

    void setIsDragOverlapping(boolean isDragOverlapping) {
        if (mIsDragOverlapping != isDragOverlapping) {
            mIsDragOverlapping = isDragOverlapping;
            invalidate();
        }
    }

    boolean getIsDragOverlapping() {
        return mIsDragOverlapping;
    }

    private void updateGlowRect() {
        float marginFraction = (mGlowBackgroundScale - 1.0f) / 2.0f;
        int marginX = (int) (marginFraction * (mBackgroundRect.right - mBackgroundRect.left));
        int marginY = (int) (marginFraction * (mBackgroundRect.bottom - mBackgroundRect.top));
        mGlowBackgroundRect.set(mBackgroundRect.left - marginX, mBackgroundRect.top - marginY,
                mBackgroundRect.right + marginX, mBackgroundRect.bottom + marginY);
        invalidate();
    }

    public void setHoverScale(float scaleFactor) {
        if (scaleFactor != mGlowBackgroundScale) {
            mGlowBackgroundScale = scaleFactor;
            updateGlowRect();
            if (getParent() != null) {
                ((View) getParent()).invalidate();
            }
        }
    }

    public float getHoverScale() {
        return mGlowBackgroundScale;
    }

    public float getHoverAlpha() {
        return mGlowBackgroundAlpha;
    }

    public void setHoverAlpha(float alpha) {
        mGlowBackgroundAlpha = alpha;
        invalidate();
    }

    void animateDrop() {
        Resources res = getResources();
        float onDropScale = res.getInteger(R.integer.config_screenOnDropScalePercent) / 100.0f;
        ObjectAnimator scaleUp = ObjectAnimator.ofFloat(this, "hoverScale", onDropScale);
        scaleUp.setDuration(res.getInteger(R.integer.config_screenOnDropScaleUpDuration));
        ObjectAnimator scaleDown = ObjectAnimator.ofFloat(this, "hoverScale", 1.0f);
        scaleDown.setDuration(res.getInteger(R.integer.config_screenOnDropScaleDownDuration));
        ObjectAnimator alphaFadeOut = ObjectAnimator.ofFloat(this, "hoverAlpha", 0.0f);

        alphaFadeOut.setStartDelay(res.getInteger(R.integer.config_screenOnDropAlphaFadeDelay));
        alphaFadeOut.setDuration(res.getInteger(R.integer.config_screenOnDropAlphaFadeDuration));

        AnimatorSet bouncer = new AnimatorSet();
        bouncer.play(scaleUp).before(scaleDown);
        bouncer.play(scaleUp).with(alphaFadeOut);
        bouncer.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                setIsDragOverlapping(true);
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                setIsDragOverlapping(false);
                setHoverScale(1.0f);
                setHoverAlpha(1.0f);
            }
        });
        bouncer.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // When we're large, we are either drawn in a "hover" state (ie when dragging an item to
        // a neighboring page) or with just a normal background (if backgroundAlpha > 0.0f)
        // When we're small, we are either drawn normally or in the "accepts drops" state (during
        // a drag). However, we also drag the mini hover background *over* one of those two
        // backgrounds
        if (mBackgroundAlpha > 0.0f) {
            Drawable bg;
            boolean mini = getScaleX() < 0.5f;

            if (mIsDragOverlapping) {
                // In the mini case, we draw the active_glow bg *over* the active background
                bg = mini ? mActiveBackgroundMini : mActiveGlowBackground;
            } else if (mIsDragOccuring && mAcceptsDrops) {
                bg = mini ? mActiveBackgroundMini : mActiveBackground;
            } else if (mIsDefaultDropTarget && mini) {
                bg = mNormalGlowBackgroundMini;
            } else {
                bg = mini ? mNormalBackgroundMini : mNormalBackground;
            }

            bg.setAlpha((int) (mBackgroundAlpha * mBackgroundAlphaMultiplier * 255));
            bg.setBounds(mBackgroundRect);
            bg.draw(canvas);

            if (mini && mIsDragOverlapping) {
                boolean modifiedClipRect = false;
                if (mGlowBackgroundScale > 1.0f) {
                    // If the hover background's scale is greater than 1, we'll be drawing outside
                    // the bounds of this CellLayout. Get around that by temporarily increasing the
                    // size of the clip rect
                    float marginFraction = (mGlowBackgroundScale - 1.0f) / 2.0f;
                    Rect clipRect = canvas.getClipBounds();
                    int marginX = (int) (marginFraction * (clipRect.right - clipRect.left));
                    int marginY = (int) (marginFraction * (clipRect.bottom - clipRect.top));
                    canvas.save(Canvas.CLIP_SAVE_FLAG);
                    canvas.clipRect(-marginX, -marginY,
                            getWidth() + marginX, getHeight() + marginY, Region.Op.REPLACE);
                    modifiedClipRect = true;
                }

                mActiveGlowBackgroundMini.setAlpha(
                        (int) (mBackgroundAlpha * mGlowBackgroundAlpha * 255));
                mActiveGlowBackgroundMini.setBounds(mGlowBackgroundRect);
                mActiveGlowBackgroundMini.draw(canvas);
                if (modifiedClipRect) {
                    canvas.restore();
                }
            }
        }

        if (mCrosshairsVisibility > 0.0f) {
            final int countX = mCountX;
            final int countY = mCountY;

            final float MAX_ALPHA = 0.4f;
            final int MAX_VISIBLE_DISTANCE = 600;
            final float DISTANCE_MULTIPLIER = 0.002f;

            final Drawable d = mCrosshairsDrawable;
            final int width = d.getIntrinsicWidth();
            final int height = d.getIntrinsicHeight();

            int x = getPaddingLeft() - (mWidthGap / 2) - (width / 2);
            for (int col = 0; col <= countX; col++) {
                int y = getPaddingTop() - (mHeightGap / 2) - (height / 2);
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
        }

        final Paint paint = mDragOutlinePaint;
        for (int i = 0; i < mDragOutlines.length; i++) {
            final float alpha = mDragOutlineAlphas[i];
            if (alpha > 0) {
                final Point p = mDragOutlines[i];
                final Bitmap b = (Bitmap) mDragOutlineAnims[i].getTag();
                paint.setAlpha((int)(alpha + .5f));
                canvas.drawBitmap(b, p.x, p.y, paint);
            }
        }

        // We draw the pressed or focused BubbleTextView's background in CellLayout because it
        // requires an expanded clip rect (due to the glow's blur radius)
        if (mPressedOrFocusedIcon != null) {
            final int padding = mPressedOrFocusedIcon.getPressedOrFocusedBackgroundPadding();
            final Bitmap b = mPressedOrFocusedIcon.getPressedOrFocusedBackground();
            if (b != null) {
                canvas.drawBitmap(b,
                        mPressedOrFocusedIcon.getLeft() + getPaddingLeft() - padding,
                        mPressedOrFocusedIcon.getTop() + getPaddingTop() - padding,
                        null);
            }
        }

        // The folder outer / inner ring image(s)
        for (int i = 0; i < mFolderOuterRings.size(); i++) {
            FolderRingAnimator fra = mFolderOuterRings.get(i);

            // Draw outer ring
            Drawable d = FolderRingAnimator.sSharedOuterRingDrawable;
            int width = (int) fra.getOuterRingSize();
            int height = width;
            cellToPoint(fra.mCellX, fra.mCellY, mTempLocation);

            int centerX = mTempLocation[0] + mCellWidth / 2;
            int centerY = mTempLocation[1] + FolderRingAnimator.sPreviewSize / 2;

            canvas.save();
            canvas.translate(centerX - width / 2, centerY - height / 2);
            d.setBounds(0, 0, width, height);
            d.draw(canvas);
            canvas.restore();

            // Draw inner ring
            d = FolderRingAnimator.sSharedInnerRingDrawable;
            width = (int) fra.getInnerRingSize();
            height = width;
            cellToPoint(fra.mCellX, fra.mCellY, mTempLocation);

            centerX = mTempLocation[0] + mCellWidth / 2;
            centerY = mTempLocation[1] + FolderRingAnimator.sPreviewSize / 2;
            canvas.save();
            canvas.translate(centerX - width / 2, centerY - width / 2);
            d.setBounds(0, 0, width, height);
            d.draw(canvas);
            canvas.restore();
        }

        if (mFolderLeaveBehindCell[0] >= 0 && mFolderLeaveBehindCell[1] >= 0) {
            Drawable d = FolderIcon.sSharedFolderLeaveBehind;
            int width = d.getIntrinsicWidth();
            int height = d.getIntrinsicHeight();

            cellToPoint(mFolderLeaveBehindCell[0], mFolderLeaveBehindCell[1], mTempLocation);
            int centerX = mTempLocation[0] + mCellWidth / 2;
            int centerY = mTempLocation[1] + FolderRingAnimator.sPreviewSize / 2;

            canvas.save();
            canvas.translate(centerX - width / 2, centerY - width / 2);
            d.setBounds(0, 0, width, height);
            d.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mForegroundAlpha > 0) {
            mOverScrollForegroundDrawable.setBounds(mForegroundRect);
            Paint p = ((NinePatchDrawable) mOverScrollForegroundDrawable).getPaint();
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            mOverScrollForegroundDrawable.draw(canvas);
            p.setXfermode(null);
        }
    }

    public void showFolderAccept(FolderRingAnimator fra) {
        mFolderOuterRings.add(fra);
    }

    public void hideFolderAccept(FolderRingAnimator fra) {
        if (mFolderOuterRings.contains(fra)) {
            mFolderOuterRings.remove(fra);
        }
        invalidate();
    }

    public void setFolderLeaveBehindCell(int x, int y) {
        mFolderLeaveBehindCell[0] = x;
        mFolderLeaveBehindCell[1] = y;
        invalidate();
    }

    public void clearFolderLeaveBehind() {
        mFolderLeaveBehindCell[0] = -1;
        mFolderLeaveBehindCell[1] = -1;
        invalidate();
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
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

    public boolean addViewToCellLayout(
            View child, int index, int childId, LayoutParams params, boolean markCells) {
        final LayoutParams lp = params;

        // Generate an id for each view, this assumes we have at most 256x256 cells
        // per workspace screen
        if (lp.cellX >= 0 && lp.cellX <= mCountX - 1 && lp.cellY >= 0 && lp.cellY <= mCountY - 1) {
            // If the horizontal or vertical span is set to -1, it is taken to
            // mean that it spans the extent of the CellLayout
            if (lp.cellHSpan < 0) lp.cellHSpan = mCountX;
            if (lp.cellVSpan < 0) lp.cellVSpan = mCountY;

            child.setId(childId);

            mChildren.addView(child, index, lp);

            if (markCells) markCellsAsOccupiedForView(child);

            return true;
        }
        return false;
    }

    public void setAcceptsDrops(boolean acceptsDrops) {
        if (mAcceptsDrops != acceptsDrops) {
            mAcceptsDrops = acceptsDrops;
            invalidate();
        }
    }

    @Override
    public void removeAllViews() {
        clearOccupiedCells();
        mChildren.removeAllViews();
    }

    @Override
    public void removeAllViewsInLayout() {
        if (mChildren.getChildCount() > 0) {
            clearOccupiedCells();
            mChildren.removeAllViewsInLayout();
        }
    }

    public void removeViewWithoutMarkingCells(View view) {
        mChildren.removeView(view);
    }

    @Override
    public void removeView(View view) {
        markCellsAsUnoccupiedForView(view);
        mChildren.removeView(view);
    }

    @Override
    public void removeViewAt(int index) {
        markCellsAsUnoccupiedForView(mChildren.getChildAt(index));
        mChildren.removeViewAt(index);
    }

    @Override
    public void removeViewInLayout(View view) {
        markCellsAsUnoccupiedForView(view);
        mChildren.removeViewInLayout(view);
    }

    @Override
    public void removeViews(int start, int count) {
        for (int i = start; i < start + count; i++) {
            markCellsAsUnoccupiedForView(mChildren.getChildAt(i));
        }
        mChildren.removeViews(start, count);
    }

    @Override
    public void removeViewsInLayout(int start, int count) {
        for (int i = start; i < start + count; i++) {
            markCellsAsUnoccupiedForView(mChildren.getChildAt(i));
        }
        mChildren.removeViewsInLayout(start, count);
    }

    public void drawChildren(Canvas canvas) {
        mChildren.draw(canvas);
    }

    void buildChildrenLayer() {
        mChildren.buildLayer();
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
        final int count = mChildren.getChildCount();

        boolean found = false;
        for (int i = count - 1; i >= 0; i--) {
            final View child = mChildren.getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if ((child.getVisibility() == VISIBLE || child.getAnimation() != null) &&
                    lp.isLockedToGrid) {
                child.getHitRect(frame);

                // The child hit rect is relative to the CellLayoutChildren parent, so we need to
                // offset that by this CellLayout's padding to test an (x,y) point that is relative
                // to this view.
                frame.offset(mPaddingLeft, mPaddingTop);

                if (frame.contains(x, y)) {
                    cellInfo.cell = child;
                    cellInfo.cellX = lp.cellX;
                    cellInfo.cellY = lp.cellY;
                    cellInfo.spanX = lp.cellHSpan;
                    cellInfo.spanY = lp.cellVSpan;
                    found = true;
                    break;
                }
            }
        }

        mLastDownOnOccupiedCell = found;

        if (!found) {
            final int cellXY[] = mTmpXY;
            pointToCellExact(x, y, cellXY);

            cellInfo.cell = null;
            cellInfo.cellX = cellXY[0];
            cellInfo.cellY = cellXY[1];
            cellInfo.spanX = 1;
            cellInfo.spanY = 1;
        }
        setTag(cellInfo);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // First we clear the tag to ensure that on every touch down we start with a fresh slate,
        // even in the case where we return early. Not clearing here was causing bugs whereby on
        // long-press we'd end up picking up an item from a previous drag operation.
        final int action = ev.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            clearTagCellInfo();
        }

        if (mInterceptTouchListener != null && mInterceptTouchListener.onTouch(this, ev)) {
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            setTagToCellInfoForPoint((int) ev.getX(), (int) ev.getY());
        }
        return false;
    }

    private void clearTagCellInfo() {
        final CellInfo cellInfo = mCellInfo;
        cellInfo.cell = null;
        cellInfo.cellX = -1;
        cellInfo.cellY = -1;
        cellInfo.spanX = 0;
        cellInfo.spanY = 0;
        setTag(cellInfo);
    }

    public CellInfo getTag() {
        return (CellInfo) super.getTag();
    }

    /**
     * Given a point, return the cell that strictly encloses that point
     * @param x X coordinate of the point
     * @param y Y coordinate of the point
     * @param result Array of 2 ints to hold the x and y coordinate of the cell
     */
    void pointToCellExact(int x, int y, int[] result) {
        final int hStartPadding = getPaddingLeft();
        final int vStartPadding = getPaddingTop();

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
        final int hStartPadding = getPaddingLeft();
        final int vStartPadding = getPaddingTop();

        result[0] = hStartPadding + cellX * (mCellWidth + mWidthGap);
        result[1] = vStartPadding + cellY * (mCellHeight + mHeightGap);
    }

    /**
     * Given a cell coordinate, return the point that represents the upper left corner of that cell
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void cellToCenterPoint(int cellX, int cellY, int[] result) {
        final int hStartPadding = getPaddingLeft();
        final int vStartPadding = getPaddingTop();

        result[0] = hStartPadding + cellX * (mCellWidth + mWidthGap) + mCellWidth / 2;
        result[1] = vStartPadding + cellY * (mCellHeight + mHeightGap) + mCellHeight / 2;
    }

    int getCellWidth() {
        return mCellWidth;
    }

    int getCellHeight() {
        return mCellHeight;
    }

    int getWidthGap() {
        return mWidthGap;
    }

    int getHeightGap() {
        return mHeightGap;
    }

    Rect getContentRect(Rect r) {
        if (r == null) {
            r = new Rect();
        }
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int right = left + getWidth() - mPaddingLeft - mPaddingRight;
        int bottom = top + getHeight() - mPaddingTop - mPaddingBottom;
        r.set(left, top, right, bottom);
        return r;
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

        int numWidthGaps = mCountX - 1;
        int numHeightGaps = mCountY - 1;

        if (mOriginalWidthGap < 0 || mOriginalHeightGap < 0) {
            int hSpace = widthSpecSize - mPaddingLeft - mPaddingRight;
            int vSpace = heightSpecSize - mPaddingTop - mPaddingBottom;
            int hFreeSpace = hSpace - (mCountX * mOriginalCellWidth);
            int vFreeSpace = vSpace - (mCountY * mOriginalCellHeight);
            mWidthGap = Math.min(mMaxGap, numWidthGaps > 0 ? (hFreeSpace / numWidthGaps) : 0);
            mHeightGap = Math.min(mMaxGap,numHeightGaps > 0 ? (vFreeSpace / numHeightGaps) : 0);
            mChildren.setCellDimensions(mCellWidth, mCellHeight, mWidthGap, mHeightGap);
        } else {
            mWidthGap = mOriginalWidthGap;
            mHeightGap = mOriginalHeightGap;
        }

        // Initial values correspond to widthSpecMode == MeasureSpec.EXACTLY
        int newWidth = widthSpecSize;
        int newHeight = heightSpecSize;
        if (widthSpecMode == MeasureSpec.AT_MOST) {
            newWidth = mPaddingLeft + mPaddingRight + (mCountX * mCellWidth) +
                ((mCountX - 1) * mWidthGap);
            newHeight = mPaddingTop + mPaddingBottom + (mCountY * mCellHeight) +
                ((mCountY - 1) * mHeightGap);
            setMeasuredDimension(newWidth, newHeight);
        }

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(newWidth - mPaddingLeft -
                    mPaddingRight, MeasureSpec.EXACTLY);
            int childheightMeasureSpec = MeasureSpec.makeMeasureSpec(newHeight - mPaddingTop -
                    mPaddingBottom, MeasureSpec.EXACTLY);
            child.measure(childWidthMeasureSpec, childheightMeasureSpec);
        }
        setMeasuredDimension(newWidth, newHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            child.layout(mPaddingLeft, mPaddingTop,
                    r - l - mPaddingRight, b - t - mPaddingBottom);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBackgroundRect.set(0, 0, w, h);
        mForegroundRect.set(mForegroundPadding, mForegroundPadding,
                w - 2 * mForegroundPadding, h - 2 * mForegroundPadding);
        updateGlowRect();
    }

    @Override
    protected void setChildrenDrawingCacheEnabled(boolean enabled) {
        mChildren.setChildrenDrawingCacheEnabled(enabled);
    }

    @Override
    protected void setChildrenDrawnWithCacheEnabled(boolean enabled) {
        mChildren.setChildrenDrawnWithCacheEnabled(enabled);
    }

    public float getBackgroundAlpha() {
        return mBackgroundAlpha;
    }

    public void setFastBackgroundAlpha(float alpha) {
        mBackgroundAlpha = alpha;
    }

    public void setBackgroundAlphaMultiplier(float multiplier) {
        mBackgroundAlphaMultiplier = multiplier;
    }

    public float getBackgroundAlphaMultiplier() {
        return mBackgroundAlphaMultiplier;
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

    public void setFastAlpha(float alpha) {
        setFastChildrenAlpha(alpha);
        super.setFastAlpha(alpha);
    }

    private void setChildrenAlpha(float alpha) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).setAlpha(alpha);
        }
    }

    private void setFastChildrenAlpha(float alpha) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).setFastAlpha(alpha);
        }
    }

    public View getChildAt(int x, int y) {
        return mChildren.getChildAt(x, y);
    }

    public boolean animateChildToPosition(final View child, int cellX, int cellY, int duration,
            int delay) {
        CellLayoutChildren clc = getChildrenLayout();
        if (clc.indexOfChild(child) != -1 && !mOccupied[cellX][cellY]) {
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final ItemInfo info = (ItemInfo) child.getTag();

            // We cancel any existing animations
            if (mReorderAnimators.containsKey(lp)) {
                mReorderAnimators.get(lp).cancel();
                mReorderAnimators.remove(lp);
            }

            int oldX = lp.x;
            int oldY = lp.y;
            mOccupied[lp.cellX][lp.cellY] = false;
            mOccupied[cellX][cellY] = true;

            lp.isLockedToGrid = true;
            lp.cellX = info.cellX = cellX;
            lp.cellY = info.cellY = cellY;
            clc.setupLp(lp);
            lp.isLockedToGrid = false;
            int newX = lp.x;
            int newY = lp.y;

            lp.x = oldX;
            lp.y = oldY;
            child.requestLayout();

            PropertyValuesHolder x = PropertyValuesHolder.ofInt("x", oldX, newX);
            PropertyValuesHolder y = PropertyValuesHolder.ofInt("y", oldY, newY);
            ObjectAnimator oa = ObjectAnimator.ofPropertyValuesHolder(lp, x, y);
            oa.setDuration(duration);
            mReorderAnimators.put(lp, oa);
            oa.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    child.requestLayout();
                }
            });
            oa.addListener(new AnimatorListenerAdapter() {
                boolean cancelled = false;
                public void onAnimationEnd(Animator animation) {
                    // If the animation was cancelled, it means that another animation
                    // has interrupted this one, and we don't want to lock the item into
                    // place just yet.
                    if (!cancelled) {
                        lp.isLockedToGrid = true;
                    }
                    if (mReorderAnimators.containsKey(lp)) {
                        mReorderAnimators.remove(lp);
                    }
                }
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }
            });
            oa.setStartDelay(delay);
            oa.start();
            return true;
        }
        return false;
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

    void visualizeDropLocation(View v, Bitmap dragOutline, int originX, int originY,
            int spanX, int spanY, Point dragOffset, Rect dragRegion) {

        final int oldDragCellX = mDragCell[0];
        final int oldDragCellY = mDragCell[1];
        final int[] nearest = findNearestVacantArea(originX, originY, spanX, spanY, v, mDragCell);
        if (v != null && dragOffset == null) {
            mDragCenter.set(originX + (v.getWidth() / 2), originY + (v.getHeight() / 2));
        } else {
            mDragCenter.set(originX, originY);
        }

        if (dragOutline == null && v == null) {
            if (mCrosshairsDrawable != null) {
                invalidate();
            }
            return;
        }

        if (nearest != null && (nearest[0] != oldDragCellX || nearest[1] != oldDragCellY)) {
            // Find the top left corner of the rect the object will occupy
            final int[] topLeft = mTmpPoint;
            cellToPoint(nearest[0], nearest[1], topLeft);

            int left = topLeft[0];
            int top = topLeft[1];

            if (v != null && dragOffset == null) {
                // When drawing the drag outline, it did not account for margin offsets
                // added by the view's parent.
                MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
                left += lp.leftMargin;
                top += lp.topMargin;

                // Offsets due to the size difference between the View and the dragOutline.
                // There is a size difference to account for the outer blur, which may lie
                // outside the bounds of the view.
                top += (v.getHeight() - dragOutline.getHeight()) / 2;
                // We center about the x axis
                left += ((mCellWidth * spanX) + ((spanX - 1) * mWidthGap)
                        - dragOutline.getWidth()) / 2;
            } else {
                if (dragOffset != null && dragRegion != null) {
                    // Center the drag region *horizontally* in the cell and apply a drag
                    // outline offset
                    left += dragOffset.x + ((mCellWidth * spanX) + ((spanX - 1) * mWidthGap)
                             - dragRegion.width()) / 2;
                    top += dragOffset.y;
                } else {
                    // Center the drag outline in the cell
                    left += ((mCellWidth * spanX) + ((spanX - 1) * mWidthGap)
                            - dragOutline.getWidth()) / 2;
                    top += ((mCellHeight * spanY) + ((spanY - 1) * mHeightGap)
                            - dragOutline.getHeight()) / 2;
                }
            }

            final int oldIndex = mDragOutlineCurrent;
            mDragOutlineAnims[oldIndex].animateOut();
            mDragOutlineCurrent = (oldIndex + 1) % mDragOutlines.length;

            mDragOutlines[mDragOutlineCurrent].set(left, top);
            mDragOutlineAnims[mDragOutlineCurrent].setTag(dragOutline);
            mDragOutlineAnims[mDragOutlineCurrent].animateIn();
        }

        // If we are drawing crosshairs, the entire CellLayout needs to be invalidated
        if (mCrosshairsDrawable != null) {
            invalidate();
        }
    }

    public void clearDragOutlines() {
        final int oldIndex = mDragOutlineCurrent;
        mDragOutlineAnims[oldIndex].animateOut();
        mDragCell[0] = -1;
        mDragCell[1] = -1;
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
     * @param ignoreOccupied If true, the result can be an occupied cell
     * @param result Array in which to place the result, or null (in which case a new array will
     *        be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    int[] findNearestArea(int pixelX, int pixelY, int spanX, int spanY, View ignoreView,
            boolean ignoreOccupied, int[] result) {
        // mark space take by ignoreView as available (method checks if ignoreView is null)
        markCellsAsUnoccupiedForView(ignoreView);

        // For items with a spanX / spanY > 1, the passed in point (pixelX, pixelY) corresponds
        // to the center of the item, but we are searching based on the top-left cell, so
        // we translate the point over to correspond to the top-left.
        pixelX -= (mCellWidth + mWidthGap) * (spanX - 1) / 2f;
        pixelY -= (mCellHeight + mHeightGap) * (spanY - 1) / 2f;

        // Keep track of best-scoring drop area
        final int[] bestXY = result != null ? result : new int[2];
        double bestDistance = Double.MAX_VALUE;

        final int countX = mCountX;
        final int countY = mCountY;
        final boolean[][] occupied = mOccupied;

        for (int y = 0; y < countY - (spanY - 1); y++) {
            inner:
            for (int x = 0; x < countX - (spanX - 1); x++) {
                if (ignoreOccupied) {
                    for (int i = 0; i < spanX; i++) {
                        for (int j = 0; j < spanY; j++) {
                            if (occupied[x + i][y + j]) {
                                // small optimization: we can skip to after the column we
                                // just found an occupied cell
                                x += i;
                                continue inner;
                            }
                        }
                    }
                }
                final int[] cellXY = mTmpXY;
                cellToCenterPoint(x, y, cellXY);

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

        // Return -1, -1 if no suitable location found
        if (bestDistance == Double.MAX_VALUE) {
            bestXY[0] = -1;
            bestXY[1] = -1;
        }
        return bestXY;
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
        return findNearestArea(pixelX, pixelY, spanX, spanY, ignoreView, true, result);
    }

    /**
     * Find a starting cell position that will fit the given bounds nearest the requested
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
    int[] findNearestArea(
            int pixelX, int pixelY, int spanX, int spanY, int[] result) {
        return findNearestArea(pixelX, pixelY, spanX, spanY, null, false, result);
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

            for (int y = startY; y < endY && !foundCell; y++) {
                inner:
                for (int x = startX; x < endX; x++) {
                    for (int i = 0; i < spanX; i++) {
                        for (int j = 0; j < spanY; j++) {
                            if (mOccupied[x + i][y + j]) {
                                // small optimization: we can skip to after the column we just found
                                // an occupied cell
                                x += i;
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
     * A drag event has begun over this layout.
     * It may have begun over this layout (in which case onDragChild is called first),
     * or it may have begun on another layout.
     */
    void onDragEnter() {
        if (!mDragging) {
            // Fade in the drag indicators
            if (mCrosshairsAnimator != null) {
                mCrosshairsAnimator.animateIn();
            }
        }
        mDragging = true;
    }

    /**
     * Called when drag has left this CellLayout or has been completed (successfully or not)
     */
    void onDragExit() {
        // This can actually be called when we aren't in a drag, e.g. when adding a new
        // item to this layout via the customize drawer.
        // Guard against that case.
        if (mDragging) {
            mDragging = false;

            // Fade out the drag indicators
            if (mCrosshairsAnimator != null) {
                mCrosshairsAnimator.animateOut();
            }
        }

        // Invalidate the drag data
        mDragCell[0] = -1;
        mDragCell[1] = -1;
        mDragOutlineAnims[mDragOutlineCurrent].animateOut();
        mDragOutlineCurrent = (mDragOutlineCurrent + 1) % mDragOutlineAnims.length;

        setIsDragOverlapping(false);
    }

    /**
     * Mark a child as having been dropped.
     * At the beginning of the drag operation, the child may have been on another
     * screen, but it is re-parented before this method is called.
     *
     * @param child The child that is being dropped
     */
    void onDropChild(View child) {
        if (child != null) {
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.dropped = true;
            child.requestLayout();
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

        final int hStartPadding = getPaddingLeft();
        final int vStartPadding = getPaddingTop();

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
        return rectToCell(getResources(), width, height, result);
    }

    public static int[] rectToCell(Resources resources, int width, int height, int[] result) {
        // Always assume we're working with the smallest span to make sure we
        // reserve enough space in both orientations.
        int actualWidth = resources.getDimensionPixelSize(R.dimen.workspace_cell_width);
        int actualHeight = resources.getDimensionPixelSize(R.dimen.workspace_cell_height);
        int smallerSize = Math.min(actualWidth, actualHeight);

        // Always round up to next largest cell
        int spanX = (int) Math.ceil(width / (float) smallerSize);
        int spanY = (int) Math.ceil(height / (float) smallerSize);

        if (result == null) {
            return new int[] { spanX, spanY };
        }
        result[0] = spanX;
        result[1] = spanY;
        return result;
    }

    public int[] cellSpansToSize(int hSpans, int vSpans) {
        int[] size = new int[2];
        size[0] = hSpans * mCellWidth + (hSpans - 1) * mWidthGap;
        size[1] = vSpans * mCellHeight + (vSpans - 1) * mHeightGap;
        return size;
    }

    /**
     * Calculate the grid spans needed to fit given item
     */
    public void calculateSpans(ItemInfo info) {
        final int minWidth;
        final int minHeight;

        if (info instanceof LauncherAppWidgetInfo) {
            minWidth = ((LauncherAppWidgetInfo) info).minWidth;
            minHeight = ((LauncherAppWidgetInfo) info).minHeight;
        } else if (info instanceof PendingAddWidgetInfo) {
            minWidth = ((PendingAddWidgetInfo) info).minWidth;
            minHeight = ((PendingAddWidgetInfo) info).minHeight;
        } else {
            // It's not a widget, so it must be 1x1
            info.spanX = info.spanY = 1;
            return;
        }
        int[] spans = rectToCell(minWidth, minHeight, null);
        info.spanX = spans[0];
        info.spanY = spans[1];
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

        for (int y = 0; y < yCount; y++) {
            for (int x = 0; x < xCount; x++) {
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

    private void clearOccupiedCells() {
        for (int x = 0; x < mCountX; x++) {
            for (int y = 0; y < mCountY; y++) {
                mOccupied[x][y] = false;
            }
        }
    }

    /**
     * Given a view, determines how much that view can be expanded in all directions, in terms of
     * whether or not there are other items occupying adjacent cells. Used by the
     * AppWidgetResizeFrame to determine how the widget can be resized.
     */
    public void getExpandabilityArrayForView(View view, int[] expandability) {
        final LayoutParams lp = (LayoutParams) view.getLayoutParams();
        boolean flag;

        expandability[AppWidgetResizeFrame.LEFT] = 0;
        for (int x = lp.cellX - 1; x >= 0; x--) {
            flag = false;
            for (int y = lp.cellY; y < lp.cellY + lp.cellVSpan; y++) {
                if (mOccupied[x][y]) flag = true;
            }
            if (flag) break;
            expandability[AppWidgetResizeFrame.LEFT]++;
        }

        expandability[AppWidgetResizeFrame.TOP] = 0;
        for (int y = lp.cellY - 1; y >= 0; y--) {
            flag = false;
            for (int x = lp.cellX; x < lp.cellX + lp.cellHSpan; x++) {
                if (mOccupied[x][y]) flag = true;
            }
            if (flag) break;
            expandability[AppWidgetResizeFrame.TOP]++;
        }

        expandability[AppWidgetResizeFrame.RIGHT] = 0;
        for (int x = lp.cellX + lp.cellHSpan; x < mCountX; x++) {
            flag = false;
            for (int y = lp.cellY; y < lp.cellY + lp.cellVSpan; y++) {
                if (mOccupied[x][y]) flag = true;
            }
            if (flag) break;
            expandability[AppWidgetResizeFrame.RIGHT]++;
        }

        expandability[AppWidgetResizeFrame.BOTTOM] = 0;
        for (int y = lp.cellY + lp.cellVSpan; y < mCountY; y++) {
            flag = false;
            for (int x = lp.cellX; x < lp.cellX + lp.cellHSpan; x++) {
                if (mOccupied[x][y]) flag = true;
            }
            if (flag) break;
            expandability[AppWidgetResizeFrame.BOTTOM]++;
        }
    }

    public void onMove(View view, int newCellX, int newCellY) {
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        markCellsAsUnoccupiedForView(view);
        markCellsForView(newCellX, newCellY, lp.cellHSpan, lp.cellVSpan, true);
    }

    public void markCellsAsOccupiedForView(View view) {
        if (view == null || view.getParent() != mChildren) return;
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        markCellsForView(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, true);
    }

    public void markCellsAsUnoccupiedForView(View view) {
        if (view == null || view.getParent() != mChildren) return;
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

    public int getDesiredWidth() {
        return mPaddingLeft + mPaddingRight + (mCountX * mCellWidth) +
                (Math.max((mCountX - 1), 0) * mWidthGap);
    }

    public int getDesiredHeight()  {
        return mPaddingTop + mPaddingBottom + (mCountY * mCellHeight) +
                (Math.max((mCountY - 1), 0) * mHeightGap);
    }

    public boolean isOccupied(int x, int y) {
        if (x < mCountX && y < mCountY) {
            return mOccupied[x][y];
        } else {
            throw new RuntimeException("Position exceeds the bound of this CellLayout");
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
         * Indicates whether the item will set its x, y, width and height parameters freely,
         * or whether these will be computed based on cellX, cellY, cellHSpan and cellVSpan.
         */
        public boolean isLockedToGrid = true;

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

        public void setup(int cellWidth, int cellHeight, int widthGap, int heightGap) {
            if (isLockedToGrid) {
                final int myCellHSpan = cellHSpan;
                final int myCellVSpan = cellVSpan;
                final int myCellX = cellX;
                final int myCellY = cellY;

                width = myCellHSpan * cellWidth + ((myCellHSpan - 1) * widthGap) -
                        leftMargin - rightMargin;
                height = myCellVSpan * cellHeight + ((myCellVSpan - 1) * heightGap) -
                        topMargin - bottomMargin;
                x = myCellX * (cellWidth + widthGap) + leftMargin;
                y = myCellY * (cellHeight + heightGap) + topMargin;
            }
        }

        public String toString() {
            return "(" + this.cellX + ", " + this.cellY + ")";
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getWidth() {
            return width;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getHeight() {
            return height;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getX() {
            return x;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getY() {
            return y;
        }
    }

    // This class stores info for two purposes:
    // 1. When dragging items (mDragInfo in Workspace), we store the View, its cellX & cellY,
    //    its spanX, spanY, and the screen it is on
    // 2. When long clicking on an empty cell in a CellLayout, we save information about the
    //    cellX and cellY coordinates and which page was clicked. We then set this as a tag on
    //    the CellLayout that was long clicked
    static final class CellInfo {
        View cell;
        int cellX = -1;
        int cellY = -1;
        int spanX;
        int spanY;
        int screen;
        long container;

        @Override
        public String toString() {
            return "Cell[view=" + (cell == null ? "null" : cell.getClass())
                    + ", x=" + cellX + ", y=" + cellY + "]";
        }
    }

    public boolean lastDownOnOccupiedCell() {
        return mLastDownOnOccupiedCell;
    }
}
