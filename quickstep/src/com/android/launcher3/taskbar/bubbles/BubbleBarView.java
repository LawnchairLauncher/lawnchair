/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.taskbar.bubbles;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.R;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

/**
 * The view that holds all the bubble views. Modifying this view should happen through
 * {@link BubbleBarViewController}. Updates to the bubbles themselves (adds, removes, updates,
 * selection) should happen through {@link BubbleBarController} which is the source of truth
 * for state information about the bubbles.
 * <p>
 * The bubble bar has a couple of visual states:
 * - stashed as a handle
 * - unstashed but collapsed, in this state the bar is showing but the bubbles are stacked within it
 * - unstashed and expanded, in this state the bar is showing and the bubbles are shown in a row
 * with one of the bubbles being selected. Additionally, WMShell will display the expanded bubble
 * view above the bar.
 * <p>
 * The bubble bar has some behavior related to taskbar:
 * - When taskbar is unstashed, bubble bar will also become unstashed (but in its "collapsed"
 * state)
 * - When taskbar is stashed, bubble bar will also become stashed (unless bubble bar is in its
 * "expanded" state)
 * - When bubble bar is in its "expanded" state, taskbar becomes stashed
 * <p>
 * If there are no bubbles, the bubble bar and bubble stashed handle are not shown. Additionally
 * the bubble bar and stashed handle are not shown on lockscreen.
 * <p>
 * When taskbar is in persistent or 3 button nav mode, the bubble bar is not available, and instead
 * the bubbles are shown fully by WMShell in their floating mode.
 */
public class BubbleBarView extends FrameLayout {

    private static final String TAG = BubbleBarView.class.getSimpleName();

    // TODO: (b/273594744) calculate the amount of space we have and base the max on that
    //  if it's smaller than 5.
    private static final int MAX_BUBBLES = 5;
    private static final int ARROW_POSITION_ANIMATION_DURATION_MS = 200;
    private static final int WIDTH_ANIMATION_DURATION_MS = 200;

    private final BubbleBarBackground mBubbleBarBackground;

    // The current bounds of all the bubble bar.
    private final Rect mBubbleBarBounds = new Rect();
    // The amount the bubbles overlap when they are stacked in the bubble bar
    private final float mIconOverlapAmount;
    // The spacing between the bubbles when they are expanded in the bubble bar
    private final float mIconSpacing;
    // The size of a bubble in the bar
    private final float mIconSize;
    // The elevation of the bubbles within the bar
    private final float mBubbleElevation;

    // Whether the bar is expanded (i.e. the bubble activity is being displayed).
    private boolean mIsBarExpanded = false;
    // The currently selected bubble view.
    private BubbleView mSelectedBubbleView;
    // The click listener when the bubble bar is collapsed.
    private View.OnClickListener mOnClickListener;

    private final Rect mTempRect = new Rect();

    // An animator that represents the expansion state of the bubble bar, where 0 corresponds to the
    // collapsed state and 1 to the fully expanded state.
    private final ValueAnimator mWidthAnimator = ValueAnimator.ofFloat(0, 1);

    // We don't reorder the bubbles when they are expanded as it could be jarring for the user
    // this runnable will be populated with any reordering of the bubbles that should be applied
    // once they are collapsed.
    @Nullable
    private Runnable mReorderRunnable;

    public BubbleBarView(Context context) {
        this(context, null);
    }

    public BubbleBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BubbleBarView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TaskbarActivityContext activityContext = ActivityContext.lookupContext(context);

        mIconOverlapAmount = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_overlap);
        mIconSpacing = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_spacing);
        mIconSize = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_size);
        mBubbleElevation = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_elevation);
        setClipToPadding(false);

        mBubbleBarBackground = new BubbleBarBackground(activityContext,
                getResources().getDimensionPixelSize(R.dimen.bubblebar_size));
        setBackgroundDrawable(mBubbleBarBackground);

        mWidthAnimator.setDuration(WIDTH_ANIMATION_DURATION_MS);
        mWidthAnimator.addUpdateListener(animation -> {
            updateChildrenRenderNodeProperties();
            invalidate();
        });
        mWidthAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mBubbleBarBackground.showArrow(mIsBarExpanded);
                if (!mIsBarExpanded && mReorderRunnable != null) {
                    mReorderRunnable.run();
                    mReorderRunnable = null;
                }
                updateWidth();
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }

            @Override
            public void onAnimationStart(Animator animation) {
                mBubbleBarBackground.showArrow(true);
            }
        });
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mBubbleBarBounds.left = left;
        mBubbleBarBounds.top = top;
        mBubbleBarBounds.right = right;
        mBubbleBarBounds.bottom = bottom;

        // The bubble bar handle is aligned to the bottom edge of the screen so scale towards that.
        setPivotX(getWidth());
        setPivotY(getHeight());

        // Position the views
        updateChildrenRenderNodeProperties();
    }

    /**
     * Returns the bounds of the bubble bar.
     */
    public Rect getBubbleBarBounds() {
        return mBubbleBarBounds;
    }

    // TODO: (b/280605790) animate it
    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() + 1 > MAX_BUBBLES) {
            removeViewInLayout(getChildAt(getChildCount() - 1));
        }
        super.addView(child, index, params);
        updateWidth();
    }

    // TODO: (b/283309949) animate it
    @Override
    public void removeView(View view) {
        super.removeView(view);
        updateWidth();
    }

    private void updateWidth() {
        LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.width = (int) (mIsBarExpanded ? expandedWidth() : collapsedWidth());
        setLayoutParams(lp);
    }

    /**
     * Updates the z order, positions, and badge visibility of the bubble views in the bar based
     * on the expanded state.
     */
    private void updateChildrenRenderNodeProperties() {
        final float widthState = (float) mWidthAnimator.getAnimatedValue();
        final float currentWidth = getWidth();
        final float expandedWidth = expandedWidth();
        final float collapsedWidth = collapsedWidth();
        int bubbleCount = getChildCount();
        final float ty = (mBubbleBarBounds.height() - mIconSize) / 2f;
        for (int i = 0; i < bubbleCount; i++) {
            BubbleView bv = (BubbleView) getChildAt(i);
            bv.setTranslationY(ty);

            // the position of the bubble when the bar is fully expanded
            final float expandedX = i * (mIconSize + mIconSpacing);
            // the position of the bubble when the bar is fully collapsed
            final float collapsedX = i * mIconOverlapAmount;

            if (mIsBarExpanded) {
                // where the bubble will end up when the animation ends
                final float targetX = currentWidth - expandedWidth + expandedX;
                bv.setTranslationX(widthState * (targetX - collapsedX) + collapsedX);
                // if we're fully expanded, set the z level to 0
                if (widthState == 1f) {
                    bv.setZ(0);
                }
                bv.showBadge();
            } else {
                final float targetX = currentWidth - collapsedWidth + collapsedX;
                bv.setTranslationX(widthState * (expandedX - targetX) + targetX);
                bv.setZ((MAX_BUBBLES * mBubbleElevation) - i);
                if (i > 0) {
                    bv.hideBadge();
                } else {
                    bv.showBadge();
                }
            }
        }

        // update the arrow position
        final float collapsedArrowPosition = arrowPositionForSelectedWhenCollapsed();
        final float expandedArrowPosition = arrowPositionForSelectedWhenExpanded();
        final float interpolatedWidth =
                widthState * (expandedWidth - collapsedWidth) + collapsedWidth;
        if (mIsBarExpanded) {
            // when the bar is expanding, the selected bubble is always the first, so the arrow
            // always shifts with the interpolated width.
            final float arrowPosition = currentWidth - interpolatedWidth + collapsedArrowPosition;
            mBubbleBarBackground.setArrowPosition(arrowPosition);
        } else {
            final float targetPosition = currentWidth - collapsedWidth + collapsedArrowPosition;
            final float arrowPosition =
                    targetPosition + widthState * (expandedArrowPosition - targetPosition);
            mBubbleBarBackground.setArrowPosition(arrowPosition);
        }

        mBubbleBarBackground.setWidth(interpolatedWidth);
    }

    /**
     * Reorders the views to match the provided list.
     */
    public void reorder(List<BubbleView> viewOrder) {
        if (isExpanded() || mWidthAnimator.isRunning()) {
            mReorderRunnable = () -> doReorder(viewOrder);
        } else {
            doReorder(viewOrder);
        }
    }

    // TODO: (b/273592694) animate it
    private void doReorder(List<BubbleView> viewOrder) {
        if (!isExpanded()) {
            for (int i = 0; i < viewOrder.size(); i++) {
                View child = viewOrder.get(i);
                if (child != null) {
                    removeViewInLayout(child);
                    addViewInLayout(child, i, child.getLayoutParams());
                }
            }
            updateChildrenRenderNodeProperties();
        }
    }

    /**
     * Sets which bubble view should be shown as selected.
     */
    public void setSelectedBubble(BubbleView view) {
        mSelectedBubbleView = view;
        updateArrowForSelected(/* shouldAnimate= */ true);
    }

    /**
     * Update the arrow position to match the selected bubble.
     *
     * @param shouldAnimate whether or not to animate the arrow. If the bar was just expanded, this
     *                      should be set to {@code false}. Otherwise set this to {@code true}.
     */
    private void updateArrowForSelected(boolean shouldAnimate) {
        if (mSelectedBubbleView == null) {
            Log.w(TAG, "trying to update selection arrow without a selected view!");
            return;
        }
        final int index = indexOfChild(mSelectedBubbleView);
        // Find the center of the bubble when it's expanded, set the arrow position to it.
        final float tx = getPaddingStart() + index * (mIconSize + mIconSpacing) + mIconSize / 2f;

        if (shouldAnimate) {
            final float currentArrowPosition = mBubbleBarBackground.getArrowPositionX();
            ValueAnimator animator = ValueAnimator.ofFloat(currentArrowPosition, tx);
            animator.setDuration(ARROW_POSITION_ANIMATION_DURATION_MS);
            animator.addUpdateListener(animation -> {
                float x = (float) animation.getAnimatedValue();
                mBubbleBarBackground.setArrowPosition(x);
                invalidate();
            });
            animator.start();
        } else {
            mBubbleBarBackground.setArrowPosition(tx);
            invalidate();
        }
    }

    private float arrowPositionForSelectedWhenExpanded() {
        final int index = indexOfChild(mSelectedBubbleView);
        return getPaddingStart() + index * (mIconSize + mIconSpacing) + mIconSize / 2f;
    }

    private float arrowPositionForSelectedWhenCollapsed() {
        final int index = indexOfChild(mSelectedBubbleView);
        return getPaddingStart() + index * (mIconOverlapAmount) + mIconSize / 2f;
    }

    @Override
    public void setOnClickListener(View.OnClickListener listener) {
        mOnClickListener = listener;
        setOrUnsetClickListener();
    }

    /**
     * The click listener used for the bubble view gets added / removed depending on whether
     * the bar is expanded or collapsed, this updates whether the listener is set based on state.
     */
    private void setOrUnsetClickListener() {
        super.setOnClickListener(mIsBarExpanded ? null : mOnClickListener);
    }

    /**
     * Sets whether the bubble bar is expanded or collapsed.
     */
    public void setExpanded(boolean isBarExpanded) {
        if (mIsBarExpanded != isBarExpanded) {
            mIsBarExpanded = isBarExpanded;
            updateArrowForSelected(/* shouldAnimate= */ false);
            setOrUnsetClickListener();
            if (isBarExpanded) {
                mWidthAnimator.start();
            } else {
                mWidthAnimator.reverse();
            }
        }
    }

    /**
     * Returns whether the bubble bar is expanded.
     */
    public boolean isExpanded() {
        return mIsBarExpanded;
    }

    private float expandedWidth() {
        final int childCount = getChildCount();
        final int horizontalPadding = getPaddingStart() + getPaddingEnd();
        return childCount * (mIconSize + mIconSpacing) + horizontalPadding;
    }

    private float collapsedWidth() {
        final int childCount = getChildCount();
        final int horizontalPadding = getPaddingStart() + getPaddingEnd();
        return mIconSize + ((childCount - 1) * mIconOverlapAmount) + horizontalPadding;
    }

    /**
     * Returns whether the given MotionEvent, *in screen coordinates*, is within bubble bar
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        if (getVisibility() == View.VISIBLE) {
            getBoundsOnScreen(mTempRect);
            return mTempRect.contains((int) ev.getX(), (int) ev.getY());
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mIsBarExpanded) {
            // When the bar is collapsed, all taps on it should expand it.
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }
}
