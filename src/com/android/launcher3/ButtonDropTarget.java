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
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;

/**
 * Implements a DropTarget.
 */
public abstract class ButtonDropTarget extends TextView
        implements DropTarget, DragController.DragListener, OnClickListener {

    private static final int DRAG_VIEW_DROP_DURATION = 285;

    private final boolean mHideParentOnDisable;
    protected final Launcher mLauncher;

    private int mBottomDragPadding;
    protected DropTargetBar mDropTargetBar;

    /** Whether this drop target is active for the current drag */
    protected boolean mActive;
    /** Whether an accessible drag is in progress */
    private boolean mAccessibleDrag;
    /** An item must be dragged at least this many pixels before this drop target is enabled. */
    private final int mDragDistanceThreshold;

    /** The paint applied to the drag view on hover */
    protected int mHoverColor = 0;

    protected CharSequence mText;
    protected ColorStateList mOriginalTextColor;
    protected Drawable mDrawable;

    private AnimatorSet mCurrentColorAnim;
    @Thunk ColorMatrix mSrcFilter, mDstFilter, mCurrentFilter;

    public ButtonDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ButtonDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);

        Resources resources = getResources();
        mBottomDragPadding = resources.getDimensionPixelSize(R.dimen.drop_target_drag_padding);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.ButtonDropTarget, defStyle, 0);
        mHideParentOnDisable = a.getBoolean(R.styleable.ButtonDropTarget_hideParentOnDisable, false);
        a.recycle();
        mDragDistanceThreshold = resources.getDimensionPixelSize(R.dimen.drag_distanceThreshold);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mText = getText();
        mOriginalTextColor = getTextColors();
    }

    protected void setDrawable(int resId) {
        // We do not set the drawable in the xml as that inflates two drawables corresponding to
        // drawableLeft and drawableStart.
        setCompoundDrawablesRelativeWithIntrinsicBounds(resId, 0, 0, 0);
        mDrawable = getCompoundDrawablesRelative()[0];
    }

    public void setDropTargetBar(DropTargetBar dropTargetBar) {
        mDropTargetBar = dropTargetBar;
    }

    @Override
    public final void onDragEnter(DragObject d) {
        d.dragView.setColor(mHoverColor);
        animateTextColor(mHoverColor);
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
        animateTextColor(mOriginalTextColor.getDefaultColor());
    }

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

        Themes.setColorScaleOnMatrix(getTextColor(), mSrcFilter);
        Themes.setColorScaleOnMatrix(targetColor, mDstFilter);
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
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        mActive = supportsDrop(dragObject.dragSource, dragObject.dragInfo);
        mDrawable.setColorFilter(null);
        if (mCurrentColorAnim != null) {
            mCurrentColorAnim.cancel();
            mCurrentColorAnim = null;
        }
        setTextColor(mOriginalTextColor);
        (mHideParentOnDisable ? ((ViewGroup) getParent()) : this)
                .setVisibility(mActive ? View.VISIBLE : View.GONE);

        mAccessibleDrag = options.isAccessibleDrag;
        setOnClickListener(mAccessibleDrag ? this : null);
    }

    @Override
    public final boolean acceptDrop(DragObject dragObject) {
        return supportsDrop(dragObject.dragSource, dragObject.dragInfo);
    }

    protected abstract boolean supportsDrop(DragSource source, ItemInfo info);

    @Override
    public boolean isDropEnabled() {
        return mActive && (mAccessibleDrag ||
                mLauncher.getDragController().getDistanceDragged() >= mDragDistanceThreshold);
    }

    @Override
    public void onDragEnd() {
        mActive = false;
        setOnClickListener(null);
    }

    /**
     * On drop animate the dropView to the icon.
     */
    @Override
    public void onDrop(final DragObject d) {
        final DragLayer dragLayer = mLauncher.getDragLayer();
        final Rect from = new Rect();
        dragLayer.getViewRectRelativeToSelf(d.dragView, from);

        final Rect to = getIconRect(d);
        final float scale = (float) to.width() / from.width();
        mDropTargetBar.deferOnDragEnd();

        Runnable onAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                completeDrop(d);
                mDropTargetBar.onDragEnd();
                mLauncher.exitSpringLoadedDragModeDelayed(true, 0, null);
            }
        };
        dragLayer.animateView(d.dragView, from, to, scale, 1f, 1f, 0.1f, 0.1f,
                DRAG_VIEW_DROP_DURATION,
                new DecelerateInterpolator(2),
                new LinearInterpolator(), onAnimationEndRunnable,
                DragLayer.ANIMATION_END_DISAPPEAR, null);
    }

    @Override
    public void prepareAccessibilityDrop() { }

    public abstract void completeDrop(DragObject d);

    @Override
    public void getHitRectRelativeToDragLayer(android.graphics.Rect outRect) {
        super.getHitRect(outRect);
        outRect.bottom += mBottomDragPadding;

        int[] coords = new int[2];
        mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(this, coords);
        outRect.offsetTo(coords[0], coords[1]);
    }

    public Rect getIconRect(DragObject dragObject) {
        int viewWidth = dragObject.dragView.getMeasuredWidth();
        int viewHeight = dragObject.dragView.getMeasuredHeight();
        int drawableWidth = mDrawable.getIntrinsicWidth();
        int drawableHeight = mDrawable.getIntrinsicHeight();
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
    public void onClick(View v) {
        mLauncher.getAccessibilityDelegate().handleAccessibleDrop(this, null, null);
    }

    public int getTextColor() {
        return getTextColors().getDefaultColor();
    }

    /**
     * Returns True if any update was made.
     */
    public boolean updateText(boolean hide) {
        if ((hide && getText().toString().isEmpty()) || (!hide && mText.equals(getText()))) {
            return false;
        }

        setText(hide ? "" : mText);
        return true;
    }

    public boolean isTextTruncated() {
        int availableWidth = getMeasuredWidth();
        if (mHideParentOnDisable) {
            ViewGroup parent = (ViewGroup) getParent();
            availableWidth = parent.getMeasuredWidth() - parent.getPaddingLeft()
                    - parent.getPaddingRight();
        }
        availableWidth -= (getPaddingLeft() + getPaddingRight() + mDrawable.getIntrinsicWidth()
                + getCompoundDrawablePadding());
        CharSequence displayedText = TextUtils.ellipsize(mText, getPaint(), availableWidth,
                TextUtils.TruncateAt.END);
        return !mText.equals(displayedText);
    }
}
