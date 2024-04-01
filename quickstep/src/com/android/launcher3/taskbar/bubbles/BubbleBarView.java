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

import static com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.LayoutDirection;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.SpringForce;

import com.android.launcher3.R;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.wm.shell.common.bubbles.BubbleBarLocation;

import java.util.List;
import java.util.function.Consumer;

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

    private static final long FADE_OUT_ANIM_ALPHA_DURATION_MS = 50L;
    private static final long FADE_OUT_ANIM_ALPHA_DELAY_MS = 50L;
    private static final long FADE_OUT_ANIM_POSITION_DURATION_MS = 100L;
    // During fade out animation we shift the bubble bar 1/80th of the screen width
    private static final float FADE_OUT_ANIM_POSITION_SHIFT = 1 / 80f;

    private static final long FADE_IN_ANIM_ALPHA_DURATION_MS = 100L;
    // Use STIFFNESS_MEDIUMLOW which is not defined in the API constants
    private static final float FADE_IN_ANIM_POSITION_SPRING_STIFFNESS = 400f;
    // During fade in animation we shift the bubble bar 1/60th of the screen width
    private static final float FADE_IN_ANIM_POSITION_SHIFT = 1 / 60f;

    private final BubbleBarBackground mBubbleBarBackground;

    /**
     * The current bounds of all the bubble bar. Note that these bounds may not account for
     * translation. The bounds should be retrieved using {@link #getBubbleBarBounds()} which
     * updates the bounds and accounts for translation.
     */
    private final Rect mBubbleBarBounds = new Rect();
    // The amount the bubbles overlap when they are stacked in the bubble bar
    private final float mIconOverlapAmount;
    // The spacing between the bubbles when they are expanded in the bubble bar
    private final float mIconSpacing;
    // The size of a bubble in the bar
    private final float mIconSize;
    // The elevation of the bubbles within the bar
    private final float mBubbleElevation;
    private final int mPointerSize;

    // Whether the bar is expanded (i.e. the bubble activity is being displayed).
    private boolean mIsBarExpanded = false;
    // The currently selected bubble view.
    private BubbleView mSelectedBubbleView;
    private BubbleBarLocation mBubbleBarLocation = BubbleBarLocation.DEFAULT;
    // The click listener when the bubble bar is collapsed.
    private View.OnClickListener mOnClickListener;

    private final Rect mTempRect = new Rect();
    private float mRelativePivotX = 1f;
    private float mRelativePivotY = 1f;

    // An animator that represents the expansion state of the bubble bar, where 0 corresponds to the
    // collapsed state and 1 to the fully expanded state.
    private final ValueAnimator mWidthAnimator = ValueAnimator.ofFloat(0, 1);

    @Nullable
    private Animator mBubbleBarLocationAnimator = null;

    // We don't reorder the bubbles when they are expanded as it could be jarring for the user
    // this runnable will be populated with any reordering of the bubbles that should be applied
    // once they are collapsed.
    @Nullable
    private Runnable mReorderRunnable;

    @Nullable
    private Consumer<String> mUpdateSelectedBubbleAfterCollapse;

    @Nullable
    private BubbleView mDraggedBubbleView;

    private int mPreviousLayoutDirection = LayoutDirection.UNDEFINED;

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
        setAlpha(0);
        setVisibility(INVISIBLE);
        mIconOverlapAmount = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_overlap);
        mIconSpacing = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_spacing);
        mIconSize = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_size);
        mBubbleElevation = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_elevation);
        mPointerSize = getResources().getDimensionPixelSize(R.dimen.bubblebar_pointer_size);

        setClipToPadding(false);

        mBubbleBarBackground = new BubbleBarBackground(context,
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
                // If the bar was just collapsed and the overflow was the last bubble that was
                // selected, set the first bubble as selected.
                if (!mIsBarExpanded && mUpdateSelectedBubbleAfterCollapse != null
                        && mSelectedBubbleView.getBubble() instanceof BubbleBarOverflow) {
                    BubbleView firstBubble = (BubbleView) getChildAt(0);
                    mUpdateSelectedBubbleAfterCollapse.accept(firstBubble.getBubble().getKey());
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
        mBubbleBarBounds.top = top + mPointerSize;
        mBubbleBarBounds.right = right;
        mBubbleBarBounds.bottom = bottom;

        // The bubble bar handle is aligned according to the relative pivot,
        // by default it's aligned to the bottom edge of the screen so scale towards that
        setPivotX(mRelativePivotX * getWidth());
        setPivotY(mRelativePivotY * getHeight());

        // Position the views
        updateChildrenRenderNodeProperties();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        if (mBubbleBarLocation == BubbleBarLocation.DEFAULT
                && mPreviousLayoutDirection != layoutDirection) {
            Log.d(TAG, "BubbleBar RTL properties changed, new layoutDirection=" + layoutDirection
                    + " previous layoutDirection=" + mPreviousLayoutDirection);
            mPreviousLayoutDirection = layoutDirection;
            onBubbleBarLocationChanged();
        }
    }

    private void onBubbleBarLocationChanged() {
        final boolean onLeft = mBubbleBarLocation.isOnLeft(isLayoutRtl());
        mBubbleBarBackground.setAnchorLeft(onLeft);
        mRelativePivotX = onLeft ? 0f : 1f;
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams instanceof LayoutParams lp) {
            lp.gravity = Gravity.BOTTOM | (onLeft ? Gravity.LEFT : Gravity.RIGHT);
            setLayoutParams(lp);
        }
        invalidate();
    }

    /**
     * @return current {@link BubbleBarLocation}
     */
    public BubbleBarLocation getBubbleBarLocation() {
        return mBubbleBarLocation;
    }

    /**
     * Update {@link BubbleBarLocation}
     */
    public void setBubbleBarLocation(BubbleBarLocation bubbleBarLocation, boolean animate) {
        if (animate) {
            animateToBubbleBarLocation(bubbleBarLocation);
        } else {
            setBubbleBarLocationInternal(bubbleBarLocation);
        }
    }

    private void setBubbleBarLocationInternal(BubbleBarLocation bubbleBarLocation) {
        if (bubbleBarLocation != mBubbleBarLocation) {
            mBubbleBarLocation = bubbleBarLocation;
            onBubbleBarLocationChanged();
            invalidate();
        }
    }

    private void animateToBubbleBarLocation(BubbleBarLocation bubbleBarLocation) {
        if (bubbleBarLocation == mBubbleBarLocation) {
            // nothing to do, already at expected location
            return;
        }
        if (mBubbleBarLocationAnimator != null && mBubbleBarLocationAnimator.isRunning()) {
            mBubbleBarLocationAnimator.cancel();
        }

        // Location animation uses two separate animators.
        // First animator hides the bar.
        // After it completes, location update is sent to layout the bar in the new location.
        // Second animator is started to show the bar.
        mBubbleBarLocationAnimator = getLocationUpdateFadeOutAnimator();
        mBubbleBarLocationAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Bubble bar is not visible, update the location
                setBubbleBarLocationInternal(bubbleBarLocation);
                // Animate it in
                mBubbleBarLocationAnimator = getLocationUpdateFadeInAnimator();
                mBubbleBarLocationAnimator.start();
            }
        });
        mBubbleBarLocationAnimator.start();
    }

    private AnimatorSet getLocationUpdateFadeOutAnimator() {
        final float shift =
                getResources().getDisplayMetrics().widthPixels * FADE_OUT_ANIM_POSITION_SHIFT;
        final float tx = mBubbleBarLocation.isOnLeft(isLayoutRtl()) ? shift : -shift;

        ObjectAnimator positionAnim = ObjectAnimator.ofFloat(this, TRANSLATION_X, tx)
                .setDuration(FADE_OUT_ANIM_POSITION_DURATION_MS);
        positionAnim.setInterpolator(EMPHASIZED_ACCELERATE);

        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(this, ALPHA, 0f)
                .setDuration(FADE_OUT_ANIM_ALPHA_DURATION_MS);
        alphaAnim.setStartDelay(FADE_OUT_ANIM_ALPHA_DELAY_MS);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(positionAnim, alphaAnim);
        return animatorSet;
    }

    private Animator getLocationUpdateFadeInAnimator() {
        final float shift =
                getResources().getDisplayMetrics().widthPixels * FADE_IN_ANIM_POSITION_SHIFT;
        final float startTx = mBubbleBarLocation.isOnLeft(isLayoutRtl()) ? shift : -shift;

        ValueAnimator positionAnim = new SpringAnimationBuilder(getContext())
                .setStartValue(startTx)
                .setEndValue(0)
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                .setStiffness(FADE_IN_ANIM_POSITION_SPRING_STIFFNESS)
                .build(this, VIEW_TRANSLATE_X);

        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(this, ALPHA, 1f)
                .setDuration(FADE_IN_ANIM_ALPHA_DURATION_MS);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(positionAnim, alphaAnim);
        return animatorSet;
    }

    /**
     * Updates the bounds with translation that may have been applied and returns the result.
     */
    public Rect getBubbleBarBounds() {
        mBubbleBarBounds.top = getTop() + (int) getTranslationY() + mPointerSize;
        mBubbleBarBounds.bottom = getBottom() + (int) getTranslationY();
        return mBubbleBarBounds;
    }

    /**
     * Set bubble bar relative pivot value for X and Y, applied as a fraction of view width/height
     * respectively. If the value is not in range of 0 to 1 it will be normalized.
     * @param x relative X pivot value in range 0..1
     * @param y relative Y pivot value in range 0..1
     */
    public void setRelativePivot(float x, float y) {
        mRelativePivotX = Float.max(Float.min(x, 1), 0);
        mRelativePivotY = Float.max(Float.min(y, 1), 0);
        requestLayout();
    }

    /**
     * Get current relative pivot for X axis
     */
    public float getRelativePivotX() {
        return mRelativePivotX;
    }

    /**
     * Get current relative pivot for Y axis
     */
    public float getRelativePivotY() {
        return mRelativePivotY;
    }

    /** Prepares for animating a bubble while being stashed. */
    public void prepareForAnimatingBubbleWhileStashed(String bubbleKey) {
        // we're about to animate the new bubble in. the new bubble has already been added to this
        // view, but we're currently stashed, so before we can start the animation we need make
        // everything else in the bubble bar invisible, except for the bubble that's being animated.
        setBackground(null);
        for (int i = 0; i < getChildCount(); i++) {
            final BubbleView view = (BubbleView) getChildAt(i);
            final String key = view.getBubble().getKey();
            if (!bubbleKey.equals(key)) {
                view.setVisibility(INVISIBLE);
            }
        }
        setVisibility(VISIBLE);
        setAlpha(1);
        setTranslationY(0);
        setScaleX(1);
        setScaleY(1);
    }

    /** Resets the state after the bubble animation completed. */
    public void onAnimatingBubbleCompleted() {
        setBackground(mBubbleBarBackground);
        for (int i = 0; i < getChildCount(); i++) {
            final BubbleView view = (BubbleView) getChildAt(i);
            view.setVisibility(VISIBLE);
            view.setAlpha(1f);
        }
    }

    // TODO: (b/280605790) animate it
    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() + 1 > MAX_BUBBLES) {
            // the last child view is the overflow bubble and we shouldn't remove that. remove the
            // second to last child view.
            removeViewInLayout(getChildAt(getChildCount() - 2));
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

    /** @return the horizontal margin between the bubble bar and the edge of the screen. */
    int getHorizontalMargin() {
        LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        return lp.getMarginEnd();
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
        final boolean animate = getVisibility() == VISIBLE;
        final boolean onLeft = mBubbleBarLocation.isOnLeft(isLayoutRtl());
        for (int i = 0; i < bubbleCount; i++) {
            BubbleView bv = (BubbleView) getChildAt(i);
            bv.setTranslationY(ty);

            // the position of the bubble when the bar is fully expanded
            final float expandedX;
            // the position of the bubble when the bar is fully collapsed
            final float collapsedX;
            if (onLeft) {
                // If bar is on the left, bubbles are ordered right to left
                expandedX = (bubbleCount - i - 1) * (mIconSize + mIconSpacing);
                // Shift the first bubble only if there are more bubbles in addition to overflow
                collapsedX = i == 0 && bubbleCount > 2 ? mIconOverlapAmount : 0;
            } else {
                // Bubbles ordered left to right, don't move the first bubble
                expandedX = i * (mIconSize + mIconSpacing);
                collapsedX = i == 0 ? 0 : mIconOverlapAmount;
            }

            if (mIsBarExpanded) {
                // If bar is on the right, account for bubble bar expanding and shifting left
                final float expandedBarShift = onLeft ? 0 : currentWidth - expandedWidth;
                // where the bubble will end up when the animation ends
                final float targetX = expandedX + expandedBarShift;
                bv.setTranslationX(widthState * (targetX - collapsedX) + collapsedX);
                // if we're fully expanded, set the z level to 0 or to bubble elevation if dragged
                if (widthState == 1f) {
                    bv.setZ(bv == mDraggedBubbleView ? mBubbleElevation : 0);
                }
                // When we're expanded, we're not stacked so we're not behind the stack
                bv.setBehindStack(false, animate);
                bv.setAlpha(1);
            } else {
                // If bar is on the right, account for bubble bar expanding and shifting left
                final float collapsedBarShift = onLeft ? 0 : currentWidth - collapsedWidth;
                final float targetX = collapsedX + collapsedBarShift;
                bv.setTranslationX(widthState * (expandedX - targetX) + targetX);
                bv.setZ((MAX_BUBBLES * mBubbleElevation) - i);
                // If we're not the first bubble we're behind the stack
                bv.setBehindStack(i > 0, animate);
                // If we're fully collapsed, hide all bubbles except for the first 2. If there are
                // only 2 bubbles, hide the second bubble as well because it's the overflow.
                if (widthState == 0) {
                    if (i > 1) {
                        bv.setAlpha(0);
                    } else if (i == 1 && bubbleCount == 2) {
                        bv.setAlpha(0);
                    }
                }
            }
        }

        // update the arrow position
        final float collapsedArrowPosition = arrowPositionForSelectedWhenCollapsed();
        final float expandedArrowPosition = arrowPositionForSelectedWhenExpanded();
        final float interpolatedWidth =
                widthState * (expandedWidth - collapsedWidth) + collapsedWidth;
        final float arrowPosition;
        if (onLeft) {
            float interpolatedShift = (expandedArrowPosition - collapsedArrowPosition) * widthState;
            arrowPosition = collapsedArrowPosition + interpolatedShift;
        } else {
            if (mIsBarExpanded) {
                // when the bar is expanding, the selected bubble is always the first, so the arrow
                // always shifts with the interpolated width.
                arrowPosition = currentWidth - interpolatedWidth + collapsedArrowPosition;
            } else {
                final float targetPosition = currentWidth - collapsedWidth + collapsedArrowPosition;
                arrowPosition =
                        targetPosition + widthState * (expandedArrowPosition - targetPosition);
            }
        }
        mBubbleBarBackground.setArrowPosition(arrowPosition);
        mBubbleBarBackground.setArrowAlpha((int) (255 * widthState));
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
                // this child view may have already been removed so verify that it still exists
                // before reordering it, otherwise it will be re-added.
                int indexOfChild = indexOfChild(child);
                if (child != null && indexOfChild >= 0) {
                    removeViewInLayout(child);
                    addViewInLayout(child, i, child.getLayoutParams());
                }
            }
            updateChildrenRenderNodeProperties();
        }
    }

    public void setUpdateSelectedBubbleAfterCollapse(
            Consumer<String> updateSelectedBubbleAfterCollapse) {
        mUpdateSelectedBubbleAfterCollapse = updateSelectedBubbleAfterCollapse;
    }

    /**
     * Sets which bubble view should be shown as selected.
     */
    public void setSelectedBubble(BubbleView view) {
        mSelectedBubbleView = view;
        updateArrowForSelected(/* shouldAnimate= */ true);
    }

    /**
     * Sets the dragged bubble view to correctly apply Z order. Dragged view should appear on top
     */
    public void setDraggedBubble(@Nullable BubbleView view) {
        mDraggedBubbleView = view;
        requestLayout();
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
        // Find the center of the bubble when it's expanded, set the arrow position to it.
        final float tx = arrowPositionForSelectedWhenExpanded();
        final float currentArrowPosition = mBubbleBarBackground.getArrowPositionX();
        if (shouldAnimate && currentArrowPosition > expandedWidth()) {
            Log.d(TAG, "arrow out of bounds of expanded view, skip animation");
            shouldAnimate = false;
        }
        if (shouldAnimate) {
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
        final int bubblePosition;
        if (mBubbleBarLocation.isOnLeft(isLayoutRtl())) {
            // Bubble positions are reversed. First bubble is on the right.
            bubblePosition = getChildCount() - index - 1;
        } else {
            bubblePosition = index;
        }
        return getPaddingStart() + bubblePosition * (mIconSize + mIconSpacing) + mIconSize / 2f;
    }

    private float arrowPositionForSelectedWhenCollapsed() {
        final int index = indexOfChild(mSelectedBubbleView);
        final int bubblePosition;
        if (mBubbleBarLocation.isOnLeft(isLayoutRtl())) {
            // Bubble positions are reversed. First bubble may be shifted, if there are more
            // bubbles than the current bubble and overflow.
            bubblePosition = index == 0 && getChildCount() > 2 ? 1 : 0;
        } else {
            bubblePosition = index;
        }
        return getPaddingStart() + bubblePosition * (mIconOverlapAmount) + mIconSize / 2f;
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

    /**
     * Get width of the bubble bar as if it would be expanded.
     *
     * @return width of the bubble bar in its expanded state, regardless of current width
     */
    public float expandedWidth() {
        final int childCount = getChildCount();
        final int horizontalPadding = getPaddingStart() + getPaddingEnd();
        return childCount * (mIconSize + mIconSpacing) + horizontalPadding;
    }

    private float collapsedWidth() {
        final int childCount = getChildCount();
        final int horizontalPadding = getPaddingStart() + getPaddingEnd();
        // If there are more than 2 bubbles, the first 2 should be visible when collapsed.
        // Otherwise just the first bubble should be visible because we don't show the overflow.
        return childCount > 2
                ? mIconSize + mIconOverlapAmount + horizontalPadding
                : mIconSize + horizontalPadding;
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
