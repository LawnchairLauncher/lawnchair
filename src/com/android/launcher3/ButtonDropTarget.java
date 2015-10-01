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

package com.android.launcher3;

import android.animation.AnimatorSet;
import android.animation.FloatArrayEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.android.launcher3.util.Thunk;

/**
 * Implements a DropTarget.
 */
public abstract class ButtonDropTarget extends TextView
        implements DropTarget, DragController.DragListener, OnClickListener {

    private static int DRAG_VIEW_DROP_DURATION = 285;

    protected Launcher mLauncher;
    private int mBottomDragPadding;
    protected SearchDropTargetBar mSearchDropTargetBar;

    /** Whether this drop target is active for the current drag */
    protected boolean mActive;

    /** The paint applied to the drag view on hover */
    protected int mHoverColor = 0;

    protected ColorStateList mOriginalTextColor;
    protected Drawable mDrawable;

    private AnimatorSet mCurrentColorAnim;
    @Thunk ColorMatrix mSrcFilter, mDstFilter, mCurrentFilter;


    public ButtonDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ButtonDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mBottomDragPadding = getResources().getDimensionPixelSize(R.dimen.drop_target_drag_padding);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mOriginalTextColor = getTextColors();

        // Remove the text in the Phone UI in landscape
        DeviceProfile grid = ((Launcher) getContext()).getDeviceProfile();
        if (grid.isVerticalBarLayout()) {
            setText("");
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    protected void setDrawable(int resId) {
        // We do not set the drawable in the xml as that inflates two drawables corresponding to
        // drawableLeft and drawableStart.
        mDrawable = getResources().getDrawable(resId);

        if (Utilities.ATLEAST_JB_MR1) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(mDrawable, null, null, null);
        } else {
            setCompoundDrawablesWithIntrinsicBounds(mDrawable, null, null, null);
        }
    }

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    public void setSearchDropTargetBar(SearchDropTargetBar searchDropTargetBar) {
        mSearchDropTargetBar = searchDropTargetBar;
    }

    @Override
    public void onFlingToDelete(DragObject d, PointF vec) { }

    @Override
    public final void onDragEnter(DragObject d) {
        d.dragView.setColor(mHoverColor);
        if (Utilities.ATLEAST_LOLLIPOP) {
            animateTextColor(mHoverColor);
        } else {
            if (mCurrentFilter == null) {
                mCurrentFilter = new ColorMatrix();
            }
            DragView.setColorScale(mHoverColor, mCurrentFilter);
            mDrawable.setColorFilter(new ColorMatrixColorFilter(mCurrentFilter));
            setTextColor(mHoverColor);
        }
        if (d.stateAnnouncer != null) {
            d.stateAnnouncer.cancel();
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    @Override
    public void onDragOver(DragObject d) {
        // Do nothing
    }

    protected void resetHoverColor() {
        if (Utilities.ATLEAST_LOLLIPOP) {
            animateTextColor(mOriginalTextColor.getDefaultColor());
        } else {
            mDrawable.setColorFilter(null);
            setTextColor(mOriginalTextColor);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void animateTextColor(int targetColor) {
        if (mCurrentColorAnim != null) {
            mCurrentColorAnim.cancel();
        }

        mCurrentColorAnim = new AnimatorSet();
        mCurrentColorAnim.setDuration(DragView.COLOR_CHANGE_DURATION);

        if (mSrcFilter == null) {
            mSrcFilter = new ColorMatrix();
            mDstFilter = new ColorMatrix();
            mCurrentFilter = new ColorMatrix();
        }

        DragView.setColorScale(getTextColor(), mSrcFilter);
        DragView.setColorScale(targetColor, mDstFilter);
        ValueAnimator anim1 = ValueAnimator.ofObject(
                new FloatArrayEvaluator(mCurrentFilter.getArray()),
                mSrcFilter.getArray(), mDstFilter.getArray());
        anim1.addUpdateListener(new AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mDrawable.setColorFilter(new ColorMatrixColorFilter(mCurrentFilter));
                invalidate();
            }
        });

        mCurrentColorAnim.play(anim1);
        mCurrentColorAnim.play(ObjectAnimator.ofArgb(this, "textColor", targetColor));
        mCurrentColorAnim.start();
    }

    @Override
    public final void onDragExit(DragObject d) {
        if (!d.dragComplete) {
            d.dragView.setColor(0);
            resetHoverColor();
        } else {
            // Restore the hover color
            d.dragView.setColor(mHoverColor);
        }
    }

	@Override
    public final void onDragStart(DragSource source, Object info, int dragAction) {
        mActive = supportsDrop(source, info);
        mDrawable.setColorFilter(null);
        if (mCurrentColorAnim != null) {
            mCurrentColorAnim.cancel();
            mCurrentColorAnim = null;
        }
        setTextColor(mOriginalTextColor);
        ((ViewGroup) getParent()).setVisibility(mActive ? View.VISIBLE : View.GONE);
    }

    @Override
    public final boolean acceptDrop(DragObject dragObject) {
        return supportsDrop(dragObject.dragSource, dragObject.dragInfo);
    }

    protected abstract boolean supportsDrop(DragSource source, Object info);

    @Override
    public boolean isDropEnabled() {
        return mActive;
    }

    @Override
    public void onDragEnd() {
        mActive = false;
    }

    /**
     * On drop animate the dropView to the icon.
     */
    @Override
    public void onDrop(final DragObject d) {
        final DragLayer dragLayer = mLauncher.getDragLayer();
        final Rect from = new Rect();
        dragLayer.getViewRectRelativeToSelf(d.dragView, from);

        int width = mDrawable.getIntrinsicWidth();
        int height = mDrawable.getIntrinsicHeight();
        final Rect to = getIconRect(d.dragView.getMeasuredWidth(), d.dragView.getMeasuredHeight(),
                width, height);
        final float scale = (float) to.width() / from.width();
        mSearchDropTargetBar.deferOnDragEnd();

        Runnable onAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                completeDrop(d);
                mSearchDropTargetBar.onDragEnd();
                mLauncher.exitSpringLoadedDragModeDelayed(true, 0, null);
            }
        };
        dragLayer.animateView(d.dragView, from, to, scale, 1f, 1f, 0.1f, 0.1f,
                DRAG_VIEW_DROP_DURATION, new DecelerateInterpolator(2),
                new LinearInterpolator(), onAnimationEndRunnable,
                DragLayer.ANIMATION_END_DISAPPEAR, null);
    }

    @Override
    public void prepareAccessibilityDrop() { }

    @Thunk abstract void completeDrop(DragObject d);

    @Override
    public void getHitRectRelativeToDragLayer(android.graphics.Rect outRect) {
        super.getHitRect(outRect);
        outRect.bottom += mBottomDragPadding;

        int[] coords = new int[2];
        mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(this, coords);
        outRect.offsetTo(coords[0], coords[1]);
    }

    protected Rect getIconRect(int viewWidth, int viewHeight, int drawableWidth, int drawableHeight) {
        DragLayer dragLayer = mLauncher.getDragLayer();

        // Find the rect to animate to (the view is center aligned)
        Rect to = new Rect();
        dragLayer.getViewRectRelativeToSelf(this, to);

        final int width = drawableWidth;
        final int height = drawableHeight;

        final int left;
        final int right;

        if (Utilities.isRtl(getResources())) {
            right = to.right - getPaddingRight();
            left = right - width;
        } else {
            left = to.left + getPaddingLeft();
            right = left + width;
        }

        final int top = to.top + (getMeasuredHeight() - height) / 2;
        final int bottom = top +  height;

        to.set(left, top, right, bottom);

        // Center the destination rect about the trash icon
        final int xOffset = (int) -(viewWidth - width) / 2;
        final int yOffset = (int) -(viewHeight - height) / 2;
        to.offset(xOffset, yOffset);

        return to;
    }

    @Override
    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
    }

    public void enableAccessibleDrag(boolean enable) {
        setOnClickListener(enable ? this : null);
    }

    @Override
    public void onClick(View v) {
        LauncherAppState.getInstance().getAccessibilityDelegate()
            .handleAccessibleDrop(this, null, null);
    }

    public int getTextColor() {
        return getTextColors().getDefaultColor();
    }
}
