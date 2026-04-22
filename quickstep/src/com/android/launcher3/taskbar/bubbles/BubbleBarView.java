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

import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.LayoutDirection;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import com.android.app.animation.Interpolators;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.taskbar.BarsLocationAnimatorHelper;
import com.android.launcher3.taskbar.bubbles.animation.BubbleAnimator;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;

import java.io.PrintWriter;
import java.util.ArrayList;
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

    public static final long FADE_OUT_ANIM_POSITION_DURATION_MS = 100L;
    public static final long FADE_IN_ANIM_ALPHA_DURATION_MS = 100L;
    public static final long FADE_OUT_BUBBLE_BAR_DURATION_MS = 150L;
    private static final String TAG = "BubbleBarView";
    // TODO: (b/273594744) calculate the amount of space we have and base the max on that
    //  if it's smaller than 5.
    private static final int MAX_BUBBLES = 5;
    private static final int MAX_VISIBLE_BUBBLES_COLLAPSED = 2;
    private static final int ARROW_POSITION_ANIMATION_DURATION_MS = 200;
    private static final int WIDTH_ANIMATION_DURATION_MS = 400;
    private static final int SCALE_ANIMATION_DURATION_MS = 200;

    /**
     * Custom property to set alpha value for the bar view while a bubble is being dragged.
     * Skips applying alpha to the dragged bubble.
     */
    private static final FloatProperty<BubbleBarView> BUBBLE_DRAG_ALPHA =
            new FloatProperty<>("bubbleDragAlpha") {
                @Override
                public void setValue(BubbleBarView bubbleBarView, float alpha) {
                    bubbleBarView.setAlphaDuringBubbleDrag(alpha);
                }

                @Override
                public Float get(BubbleBarView bubbleBarView) {
                    return bubbleBarView.mAlphaDuringDrag;
                }
            };

    private final BubbleBarBackground mBubbleBarBackground;

    /**
     * The current bounds of all the bubble bar. Note that these bounds may not account for
     * translation. The bounds should be retrieved using {@link #getBubbleBarBounds()} which
     * updates the bounds and accounts for translation.
     */
    private final Rect mBubbleBarBounds = new Rect();
    // The amount the bubbles overlap when they are stacked in the bubble bar
    private final float mIconOverlapAmount;
    // The spacing between the bubbles when bubble bar is expanded
    private final float mExpandedBarIconsSpacing;
    // The spacing between the bubbles and the borders of the bubble bar
    private float mBubbleBarPadding;
    // The size of a bubble in the bar
    private float mIconSize;
    // The scale of bubble icons
    private float mIconScale = 1f;
    // The elevation of the bubbles within the bar
    private final float mBubbleElevation;
    private final float mDragElevation;
    private final int mPointerSize;
    // Whether the bar is expanded (i.e. the bubble activity is being displayed).
    private boolean mIsBarExpanded = false;
    // The currently selected bubble view.
    @Nullable
    private BubbleView mSelectedBubbleView;
    private BubbleBarLocation mBubbleBarLocation = BubbleBarLocation.DEFAULT;
    // The click listener when the bubble bar is collapsed.
    private View.OnClickListener mOnClickListener;

    private final Rect mTempRect = new Rect();
    private float mRelativePivotX = 1f;
    private float mRelativePivotY = 1f;

    // An animator that represents the expansion state of the bubble bar, where 0 corresponds to the
    // collapsed state and 1 to the fully expanded state.
    @Nullable
    private ValueAnimator mWidthAnimator;

    @Nullable
    private ValueAnimator mDismissAnimator = null;

    /** An animator used for animating individual bubbles in the bubble bar while expanded. */
    @Nullable
    private BubbleAnimator mBubbleAnimator = null;
    @Nullable
    private ValueAnimator mScalePaddingAnimator;

    @Nullable
    private Animator mBubbleBarLocationAnimator = null;

    // We don't reorder the bubbles when they are expanded as it could be jarring for the user
    // this runnable will be populated with any reordering of the bubbles that should be applied
    // once they are collapsed.
    @Nullable
    private Runnable mReorderRunnable;

    @Nullable
    private Consumer<String> mUpdateSelectedBubbleAfterCollapse;

    private boolean mDragging;

    @Nullable
    private BubbleView mDraggedBubbleView;
    @Nullable
    private BubbleView mDismissedByDragBubbleView;
    private float mAlphaDuringDrag = 1f;

    /** Additional translation in the y direction that is applied to each bubble */
    private float mBubbleOffsetY;

    private Controller mController;

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
        setVisibility(INVISIBLE);
        mIconOverlapAmount = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_overlap);
        mBubbleBarPadding = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_spacing);
        mIconSize = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_size);
        mExpandedBarIconsSpacing = getResources().getDimensionPixelSize(
                R.dimen.bubblebar_expanded_icon_spacing);
        mBubbleElevation = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_elevation);
        mDragElevation = getResources().getDimensionPixelSize(R.dimen.dragged_bubble_elevation);
        mPointerSize = getResources()
                .getDimensionPixelSize(R.dimen.bubblebar_pointer_visible_size);

        setClipToPadding(false);

        mBubbleBarBackground = new BubbleBarBackground(context, getBubbleBarExpandedHeight());
        setBackgroundDrawable(mBubbleBarBackground);
    }


    /**
     * Animates icon sizes and spacing between icons and bubble bar borders.
     *
     * @param newIconSize         new icon size
     * @param newBubbleBarPadding spacing between icons and bubble bar borders.
     */
    public void animateBubbleBarIconSize(float newIconSize, float newBubbleBarPadding) {
        if (!isIconSizeOrPaddingUpdated(newIconSize, newBubbleBarPadding)) {
            return;
        }
        if (mScalePaddingAnimator != null && mScalePaddingAnimator.isRunning()) {
            mScalePaddingAnimator.cancel();
        }
        ValueAnimator scalePaddingAnimator = ValueAnimator.ofFloat(0f, 1f);
        scalePaddingAnimator.setDuration(SCALE_ANIMATION_DURATION_MS);
        boolean isPaddingUpdated = isPaddingUpdated(newBubbleBarPadding);
        boolean isIconSizeUpdated = isIconSizeUpdated(newIconSize);
        float initialScale = mIconScale;
        float initialPadding = mBubbleBarPadding;
        float targetScale = newIconSize / getScaledIconSize();

        addAnimationCallBacks(scalePaddingAnimator,
                /* onStart= */ null,
                /* onEnd= */ () -> setIconSizeAndPadding(newIconSize, newBubbleBarPadding),
                /* onUpdate= */ animator -> {
                    float transitionProgress = (float) animator.getAnimatedValue();
                    if (isIconSizeUpdated) {
                        mIconScale =
                                initialScale + (targetScale - initialScale) * transitionProgress;
                    }
                    if (isPaddingUpdated) {
                        mBubbleBarPadding = initialPadding
                                + (newBubbleBarPadding - initialPadding) * transitionProgress;
                    }
                    updateBubblesLayoutProperties(mBubbleBarLocation);
                    invalidate();
                });
        scalePaddingAnimator.start();
        mScalePaddingAnimator = scalePaddingAnimator;
    }

    @Override
    public void setTranslationX(float translationX) {
        super.setTranslationX(translationX);
        if (mDraggedBubbleView != null) {
            // Apply reverse of the translation as an offset to the dragged view. This ensures
            // that the dragged bubble stays at the current location on the screen and its
            // position is not affected by the parent translation.
            mDraggedBubbleView.setOffsetX(-translationX);
        }
    }

    /**
     * Set scale for bubble bar background in x direction
     */
    public void setBackgroundScaleX(float scaleX) {
        mBubbleBarBackground.setScaleX(scaleX);
    }

    /**
     * Set scale for bubble bar background in y direction
     */
    public void setBackgroundScaleY(float scaleY) {
        mBubbleBarBackground.setScaleY(scaleY);
    }

    /**
     * Set alpha for bubble views
     */
    public void setBubbleAlpha(float alpha) {
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setAlpha(alpha);
        }
    }

    /**
     * Set alpha for bar background
     */
    public void setBackgroundAlpha(float alpha) {
        mBubbleBarBackground.setAlpha((int) (255 * alpha));
    }

    /**
     * Sets offset of each bubble view in the y direction from the base position in the bar.
     */
    public void setBubbleOffsetY(float offsetY) {
        mBubbleOffsetY = offsetY;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setTranslationY(getBubbleTranslationY());
        }
    }

    /**
     * Set the bubble icons size and spacing between the bubbles and the borders of the bubble
     * bar.
     */
    public void setIconSizeAndPaddingForPinning(float newIconSize, float newBubbleBarPadding) {
        mBubbleBarPadding = newBubbleBarPadding;
        mIconScale = newIconSize / mIconSize;
        updateBubblesLayoutProperties(mBubbleBarLocation);
        invalidate();
    }

    /**
     * Sets new icon sizes and newBubbleBarPadding between icons and bubble bar borders.
     *
     * @param newIconSize         new icon size
     * @param newBubbleBarPadding newBubbleBarPadding between icons and bubble bar borders.
     */
    public void setIconSizeAndPadding(float newIconSize, float newBubbleBarPadding) {
        // TODO(b/335457839): handle new bubble animation during the size change
        if (!isIconSizeOrPaddingUpdated(newIconSize, newBubbleBarPadding)) {
            return;
        }
        mIconScale = 1f;
        mBubbleBarPadding = newBubbleBarPadding;
        mIconSize = newIconSize;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            childView.setScaleX(mIconScale);
            childView.setScaleY(mIconScale);
            FrameLayout.LayoutParams params = (LayoutParams) childView.getLayoutParams();
            params.height = (int) mIconSize;
            params.width = (int) mIconSize;
            childView.setLayoutParams(params);
        }
        mBubbleBarBackground.setBackgroundHeight(getBubbleBarHeight());
        updateLayoutParams();
    }

    private float getScaledIconSize() {
        return mIconSize * mIconScale;
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

        if (!mDragging) {
            // Position the views when not dragging
            updateBubblesLayoutProperties(mBubbleBarLocation);
        }
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

    @Override
    public void onInitializeAccessibilityNodeInfoInternal(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfoInternal(info);
        // Always show only expand action as the menu is only for collapsed bubble bar
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_dismiss_all,
                getResources().getString(R.string.bubble_bar_action_dismiss_all)));
        if (mBubbleBarLocation.isOnLeft(isLayoutRtl())) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_move_right,
                    getResources().getString(R.string.bubble_bar_action_move_right)));
        } else {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_move_left,
                    getResources().getString(R.string.bubble_bar_action_move_left)));
        }
    }

    @Override
    public boolean performAccessibilityActionInternal(int action,
            @androidx.annotation.Nullable Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_EXPAND
                || action == AccessibilityNodeInfo.ACTION_CLICK) {
            mController.expandBubbleBar();
            return true;
        }
        if (action == R.id.action_dismiss_all) {
            mController.dismissBubbleBar();
            return true;
        }
        if (action == R.id.action_move_left) {
            mController.updateBubbleBarLocation(BubbleBarLocation.LEFT,
                    BubbleBarLocation.UpdateSource.A11Y_ACTION_BAR);
            return true;
        }
        if (action == R.id.action_move_right) {
            mController.updateBubbleBarLocation(BubbleBarLocation.RIGHT,
                    BubbleBarLocation.UpdateSource.A11Y_ACTION_BAR);
            return true;
        }
        return super.performAccessibilityActionInternal(action, arguments);
    }

    @SuppressLint("RtlHardcoded")
    private void onBubbleBarLocationChanged() {
        final boolean onLeft = mBubbleBarLocation.isOnLeft(isLayoutRtl());
        mBubbleBarBackground.setAnchorLeft(onLeft);
        mRelativePivotX = onLeft ? 0f : 1f;
        LayoutParams lp = (LayoutParams) getLayoutParams();
        lp.gravity = Gravity.BOTTOM | (onLeft ? Gravity.LEFT : Gravity.RIGHT);
        setLayoutParams(lp); // triggers a relayout
        updateBubbleAccessibilityStates();
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
    public void setBubbleBarLocation(BubbleBarLocation bubbleBarLocation) {
        resetDragAnimation();
        if (bubbleBarLocation != mBubbleBarLocation) {
            mBubbleBarLocation = bubbleBarLocation;
            onBubbleBarLocationChanged();
        }
    }

    /**
     * Set whether this view is currently being dragged
     */
    public void setIsDragging(boolean dragging) {
        if (mDragging == dragging) {
            return;
        }
        mDragging = dragging;
        mController.setIsDragging(dragging);
        if (!mDragging) {
            // Relayout after dragging to ensure that the dragged bubble is positioned correctly
            requestLayout();
        }
    }

    /**
     * Get translation for bubble bar when drag is released and it needs to animate back to the
     * resting position.
     * Resting position is based on the supplied location. If the supplied location is different
     * from the internal location that was used during bubble bar layout, translation values are
     * calculated to position the bar at the desired location.
     *
     * @param initialTranslation initial bubble bar translation at the start of drag
     * @param location           desired location of the bubble bar when drag is released
     * @return point with x and y values representing translation on x and y-axis
     */
    public PointF getBubbleBarDragReleaseTranslation(PointF initialTranslation,
            BubbleBarLocation location) {
        float dragEndTranslationX = initialTranslation.x;
        if (getBubbleBarLocation().isOnLeft(isLayoutRtl()) != location.isOnLeft(isLayoutRtl())) {
            // Bubble bar is laid out on left or right side of the screen. And the desired new
            // location is on the other side. Calculate x translation value required to shift
            // bubble bar from one side to the other.
            final float shift = getDistanceFromOtherSide();
            if (location.isOnLeft(isLayoutRtl())) {
                // New location is on the left, shift left
                // before -> |......ooo.| after -> |.ooo......|
                dragEndTranslationX = -shift;
            } else {
                // New location is on the right, shift right
                // before -> |.ooo......| after -> |......ooo.|
                dragEndTranslationX = shift;
            }
        }
        return new PointF(dragEndTranslationX, mController.getBubbleBarTranslationY());
    }

    /**
     * Get translation for a bubble when drag is released and it needs to animate back to the
     * resting position.
     * Resting position is based on the supplied location. If the supplied location is different
     * from the internal location that was used during bubble bar layout, translation values are
     * calculated to position the bar at the desired location.
     *
     * @param initialTranslation initial bubble translation inside the bar at the start of drag
     * @param location           desired location of the bubble bar when drag is released
     * @return point with x and y values representing translation on x and y-axis
     */
    public PointF getDraggedBubbleReleaseTranslation(PointF initialTranslation,
            BubbleBarLocation location) {
        float dragEndTranslationX = initialTranslation.x;
        boolean newLocationOnLeft = location.isOnLeft(isLayoutRtl());
        if (getBubbleBarLocation().isOnLeft(isLayoutRtl()) != newLocationOnLeft) {
            // Calculate translationX based on bar and bubble translations
            float bubbleBarTx = getBubbleBarDragReleaseTranslation(initialTranslation, location).x;
            float bubbleTx =
                    getExpandedBubbleTranslationX(
                            indexOfChild(mDraggedBubbleView), getChildCount(), newLocationOnLeft);
            dragEndTranslationX = bubbleBarTx + bubbleTx;
        }
        // translationY does not change during drag and can be reused
        return new PointF(dragEndTranslationX, initialTranslation.y);
    }

    private float getDistanceFromOtherSide() {
        // Calculate the shift needed to position the bubble bar on the other side
        int displayWidth = getResources().getDisplayMetrics().widthPixels;
        int margin = 0;
        if (getLayoutParams() instanceof MarginLayoutParams lp) {
            margin += lp.leftMargin;
            margin += lp.rightMargin;
        }
        return (float) (displayWidth - getWidth() - margin);
    }

    /** Set whether the background should show the drop target */
    public void showDropTarget(boolean isDropTarget) {
        mBubbleBarBackground.showDropTarget(isDropTarget);
    }

    /** Returns whether the Bubble Bar is currently displaying a drop target. */
    public boolean isShowingDropTarget() {
        return mBubbleBarBackground.isShowingDropTarget();
    }

    /**
     * Animate bubble bar to the given location transiently. Does not modify the layout or the value
     * returned by {@link #getBubbleBarLocation()}.
     */
    public void animateToBubbleBarLocation(BubbleBarLocation bubbleBarLocation) {
        if (mBubbleBarLocationAnimator != null && mBubbleBarLocationAnimator.isRunning()) {
            mBubbleBarLocationAnimator.removeAllListeners();
            mBubbleBarLocationAnimator.cancel();
        }

        // Location animation uses two separate animators.
        // First animator hides the bar.
        // After it completes, bubble positions in the bar and arrow position is updated.
        // Second animator is started to show the bar.
        mBubbleBarLocationAnimator = animateToBubbleBarLocationOut(bubbleBarLocation);
        mBubbleBarLocationAnimator.addListener(AnimatorListeners.forEndCallback(() -> {
            // Animate it in
            mBubbleBarLocationAnimator = animateToBubbleBarLocationIn(mBubbleBarLocation,
                    bubbleBarLocation);
            mBubbleBarLocationAnimator.start();
        }));
        mBubbleBarLocationAnimator.start();
    }

    /** Creates animator for animating bubble bar in. */
    public Animator animateToBubbleBarLocationIn(BubbleBarLocation fromLocation,
            BubbleBarLocation toLocation) {
        updateBubblesLayoutProperties(toLocation);
        mBubbleBarBackground.setAnchorLeft(toLocation.isOnLeft(isLayoutRtl()));
        ObjectAnimator alphaInAnim = ObjectAnimator.ofFloat(BubbleBarView.this,
                getLocationAnimAlphaProperty(), 1f);
        return BarsLocationAnimatorHelper.getBubbleBarLocationInAnimator(toLocation, fromLocation,
                getDistanceFromOtherSide(), alphaInAnim, this);
    }

    /**
     * Creates animator for animating bubble bar out.
     *
     * @param targetLocation the location bubble br should animate to.
     */
    public Animator animateToBubbleBarLocationOut(BubbleBarLocation targetLocation) {
        ObjectAnimator alphaOutAnim = ObjectAnimator.ofFloat(
                this, getLocationAnimAlphaProperty(), 0f);
        Animator outAnimation = BarsLocationAnimatorHelper.getBubbleBarLocationOutAnimator(
                this,
                targetLocation,
                alphaOutAnim);
        outAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation, boolean isReverse) {
                // need to restore the original bar view state in case icon is dropped to the bubble
                // bar original location
                updateBubblesLayoutProperties(targetLocation);
                mBubbleBarBackground.setAnchorLeft(targetLocation.isOnLeft(isLayoutRtl()));
                setTranslationX(0f);
            }
        });
        return outAnimation;
    }

    /**
     * Get property that can be used to animate the alpha value for the bar.
     * When a bubble is being dragged, uses {@link #BUBBLE_DRAG_ALPHA}.
     * Falls back to {@link com.android.launcher3.LauncherAnimUtils#VIEW_ALPHA} otherwise.
     */
    private FloatProperty<? super BubbleBarView> getLocationAnimAlphaProperty() {
        return mDraggedBubbleView == null ? VIEW_ALPHA : BUBBLE_DRAG_ALPHA;
    }

    /**
     * Set alpha value for the bar while a bubble is being dragged.
     * We can not update the alpha on the bar directly because the dragged bubble would be affected
     * as well. As it is a child view.
     * Instead, while a bubble is being dragged, set alpha on each child view, that is not the
     * dragged view. And set an alpha on the background.
     * This allows for the dragged bubble to remain visible while the bar is hidden during
     * animation.
     */
    private void setAlphaDuringBubbleDrag(float alpha) {
        mAlphaDuringDrag = alpha;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View view = getChildAt(i);
            if (view != mDraggedBubbleView) {
                view.setAlpha(alpha);
            }
        }
        if (mBubbleBarBackground != null) {
            mBubbleBarBackground.setAlpha((int) (255 * alpha));
        }
    }

    private void resetDragAnimation() {
        if (mBubbleBarLocationAnimator != null) {
            mBubbleBarLocationAnimator.removeAllListeners();
            mBubbleBarLocationAnimator.cancel();
            mBubbleBarLocationAnimator = null;
        }
        setAlphaDuringBubbleDrag(1f);
        setTranslationX(0f);
        if (mIsBarExpanded && getBubbleChildCount() > 0) {
            setAlpha(1f);
        }
    }

    /** Get the distance between the bubble bar top coordinate and the bottom of the screen */
    public int getTopToScreenBottom() {
        // the bottom of the bubble bar is aligned with the bottom of the screen. the distance
        // between the top of the bubble bar and the bottom of the screen is the height of the
        // bubble bar minus the y translation. since the bubble bar is always above the bottom of
        // the screen, the translation is negative and the overall result is a positive value that
        // represents the distance
        int bubbleBarHeight = getBubbleBarBounds().height();
        return bubbleBarHeight - (int) mController.getBubbleBarTranslationY();
    }

    /** Returns the bounds with translation that may have been applied. */
    public Rect getBubbleBarBounds() {
        Rect bounds = new Rect(mBubbleBarBounds);
        bounds.top = getTop() + (int) getTranslationY() + mPointerSize;
        bounds.bottom = getBottom() + (int) getTranslationY();
        return bounds;
    }

    /** Returns the expanded bounds with translation that may have been applied. */
    public Rect getBubbleBarExpandedBounds() {
        Rect expandedBounds = getBubbleBarBounds();
        if (!isExpanded() || isExpanding()) {
            if (mBubbleBarLocation.isOnLeft(isLayoutRtl())) {
                expandedBounds.right = expandedBounds.left + (int) expandedWidth();
            } else {
                expandedBounds.left = expandedBounds.right - (int) expandedWidth();
            }
        }
        return expandedBounds;
    }

    /**
     * Set bubble bar relative pivot value for X and Y, applied as a fraction of view width/height
     * respectively. If the value is not in range of 0 to 1 it will be normalized.
     *
     * @param x relative X pivot value in range 0..1
     * @param y relative Y pivot value in range 0..1
     */
    public void setRelativePivot(float x, float y) {
        mRelativePivotX = Float.max(Float.min(x, 1), 0);
        mRelativePivotY = Float.max(Float.min(y, 1), 0);
        requestLayout();
    }

    /** Like {@link #setRelativePivot(float, float)} but only updates pivot y. */
    public void setRelativePivotY(float y) {
        setRelativePivot(mRelativePivotX, y);
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

    /** Add a new bubble to the bubble bar without updating the selected bubble. */
    public void addBubble(BubbleView bubble, boolean suppressAnimation) {
        addBubble(bubble, /* bubbleToSelect = */ null, suppressAnimation);
    }

    /**
     * Add a new bubble to the bubble bar and selects the provided bubble.
     *
     * @param bubble         bubble to add
     * @param bubbleToSelect if {@code null}, then selected bubble does not change
     */
    public void addBubble(BubbleView bubble, @Nullable BubbleView bubbleToSelect,
            boolean suppressAnimation) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) mIconSize, (int) mIconSize,
                Gravity.LEFT);
        final int index = bubble.isOverflow() ? getChildCount() : 0;

        if (isExpanded() && !suppressAnimation) {
            // if we're expanded scale the new bubble in
            bubble.setScaleX(0f);
            bubble.setScaleY(0f);
            addView(bubble, index, lp);
            bubble.showDotIfNeeded(/* animate= */ false);

            mBubbleAnimator = new BubbleAnimator(mIconSize, mExpandedBarIconsSpacing,
                    getChildCount(), mBubbleBarLocation.isOnLeft(isLayoutRtl()));
            BubbleAnimator.Listener listener = new BubbleAnimator.Listener() {

                @Override
                public void onAnimationEnd() {
                    updateLayoutParams();
                    mBubbleAnimator = null;
                }

                @Override
                public void onAnimationCancel() {
                    bubble.setScaleX(1);
                    bubble.setScaleY(1);
                }

                @Override
                public void onAnimationUpdate(float animatedFraction) {
                    bubble.setScaleX(animatedFraction);
                    bubble.setScaleY(animatedFraction);
                    updateBubblesLayoutProperties(mBubbleBarLocation);
                    invalidate();
                }
            };
            if (bubbleToSelect != null) {
                mBubbleAnimator.animateNewBubble(indexOfChild(mSelectedBubbleView),
                        indexOfChild(bubbleToSelect), listener);
            } else {
                mBubbleAnimator.animateNewBubble(indexOfChild(mSelectedBubbleView), listener);
            }
        } else {
            addView(bubble, index, lp);
        }
    }

    /** Add a new bubble and remove an old bubble from the bubble bar. */
    public void addBubbleAndRemoveBubble(BubbleView addedBubble, BubbleView removedBubble,
            @Nullable BubbleView bubbleToSelect, Runnable onEndRunnable) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) mIconSize, (int) mIconSize,
                Gravity.LEFT);
        int addedIndex = addedBubble.isOverflow() ? getChildCount() : 0;
        if (!isExpanded()) {
            removeView(removedBubble);
            addView(addedBubble, addedIndex, lp);
            if (onEndRunnable != null) {
                onEndRunnable.run();
            }
            return;
        }
        addedBubble.setScaleX(0f);
        addedBubble.setScaleY(0f);
        addView(addedBubble, addedIndex, lp);
        int indexOfCurrentSelectedBubble = indexOfChild(mSelectedBubbleView);
        int indexOfBubbleToRemove = indexOfChild(removedBubble);
        int indexOfNewlySelectedBubble = bubbleToSelect == null
                ? indexOfCurrentSelectedBubble : indexOfChild(bubbleToSelect);
        // Since removed bubble is kept till the end of the animation we should check if there are
        // more than one bubble. In such a case the bar will remain open without the selected bubble
        if (mSelectedBubbleView == removedBubble
                && bubbleToSelect == null
                && getBubbleChildCount() > 1) {
            Log.w(TAG, "Remove the currently selected bubble without selecting a new one.");
        }
        mBubbleAnimator = new BubbleAnimator(mIconSize, mExpandedBarIconsSpacing,
                getChildCount(), mBubbleBarLocation.isOnLeft(isLayoutRtl()));
        BubbleAnimator.Listener listener = new BubbleAnimator.Listener() {

            @Override
            public void onAnimationEnd() {
                removeView(removedBubble);
                updateLayoutParams();
                mBubbleAnimator = null;
                if (onEndRunnable != null) {
                    onEndRunnable.run();
                }
            }

            @Override
            public void onAnimationCancel() {
                addedBubble.setScaleX(1);
                addedBubble.setScaleY(1);
                removedBubble.setScaleX(0);
                removedBubble.setScaleY(0);
            }

            @Override
            public void onAnimationUpdate(float animatedFraction) {
                addedBubble.setScaleX(animatedFraction);
                addedBubble.setScaleY(animatedFraction);
                removedBubble.setScaleX(1 - animatedFraction);
                removedBubble.setScaleY(1 - animatedFraction);
                updateBubblesLayoutProperties(mBubbleBarLocation);
                invalidate();
            }
        };
        mBubbleAnimator.animateNewAndRemoveOld(indexOfCurrentSelectedBubble,
                indexOfNewlySelectedBubble, indexOfBubbleToRemove, addedIndex, listener);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        updateLayoutParams();
        updateBubbleAccessibilityStates();
        updateContentDescription();
        updateDotsAndBadgesIfCollapsed();
    }

    /** Removes the given bubble from the bubble bar. */
    public void removeBubble(View bubble) {
        if (isExpanded()) {
            final boolean dismissedByDrag = mDraggedBubbleView == bubble;
            if (dismissedByDrag) {
                mDismissedByDragBubbleView = mDraggedBubbleView;
            }
            boolean removingLastRemainingBubble = getBubbleChildCount() == 1;
            int bubbleCount = getChildCount();
            mBubbleAnimator = new BubbleAnimator(mIconSize, mExpandedBarIconsSpacing,
                    bubbleCount, mBubbleBarLocation.isOnLeft(isLayoutRtl()));
            BubbleAnimator.Listener listener = new BubbleAnimator.Listener() {

                @Override
                public void onAnimationEnd() {
                    removeView(bubble);
                    mBubbleAnimator = null;
                }

                @Override
                public void onAnimationCancel() {
                    bubble.setScaleX(0);
                    bubble.setScaleY(0);
                }

                @Override
                public void onAnimationUpdate(float animatedFraction) {
                    // don't update the scale if this bubble was dismissed by drag
                    if (!dismissedByDrag) {
                        bubble.setScaleX(1 - animatedFraction);
                        bubble.setScaleY(1 - animatedFraction);
                    }
                    updateBubblesLayoutProperties(mBubbleBarLocation);
                    invalidate();
                }
            };
            int bubbleIndex = indexOfChild(bubble);
            BubbleView lastBubble = (BubbleView) getChildAt(bubbleCount - 1);
            String lastBubbleKey = lastBubble.getBubble().getKey();
            boolean removingLastBubble =
                    BubbleBarOverflow.KEY.equals(lastBubbleKey)
                            ? bubbleIndex == bubbleCount - 2
                            : bubbleIndex == bubbleCount - 1;
            mBubbleAnimator.animateRemovedBubble(
                    indexOfChild(bubble), indexOfChild(mSelectedBubbleView), removingLastBubble,
                    removingLastRemainingBubble, listener);
            if (removingLastRemainingBubble && mDismissAnimator == null) {
                createDismissAnimator().start();
            }
        } else {
            removeView(bubble);
        }
    }

    // TODO: (b/283309949) animate it
    @Override
    public void removeView(View view) {
        super.removeView(view);
        if (view == mSelectedBubbleView) {
            mSelectedBubbleView = null;
            mBubbleBarBackground.showArrow(false);
        }
        updateLayoutParams();
        updateBubbleAccessibilityStates();
        updateContentDescription();
        mDismissedByDragBubbleView = null;
        updateDotsAndBadgesIfCollapsed();
    }

    private ValueAnimator createDismissAnimator() {
        ValueAnimator animator =
                ValueAnimator.ofFloat(0, 1).setDuration(FADE_OUT_BUBBLE_BAR_DURATION_MS);
        animator.setInterpolator(Interpolators.EMPHASIZED);
        Runnable onEnd = () -> {
            mDismissAnimator = null;
            setAlpha(0);
        };
        addAnimationCallBacks(animator, /* onStart= */ null, onEnd,
                /* onUpdate= */ anim -> setAlpha(1 - anim.getAnimatedFraction()));
        mDismissAnimator = animator;
        return animator;
    }

    /** Dismisses the bubble bar */
    public void dismiss(Runnable onDismissed) {
        if (mDismissAnimator == null) {
            createDismissAnimator().start();
        }
        addAnimationCallBacks(mDismissAnimator, null, onDismissed, null);
    }

    /**
     * Return child views in the order which they are shown on the screen.
     * <p>
     * Child views (bubbles) are always ordered based on recency. The most recent bubble is at index
     * 0.
     * For example if the child views are (1)(2)(3) then (1) is the most recent bubble and at index
     * 0.<br>
     *
     * How bubbles show up on the screen depends on the bubble bar location. If the bar is on the
     * left, the most recent bubble is shown on the right. The bubbles from the example above would
     * be shown as: (3)(2)(1).<br>
     *
     * If bubble bar is on the right, then the most recent bubble is on the left. Bubbles from the
     * example above would be shown as: (1)(2)(3).
     */
    private List<View> getChildViewsInOnScreenOrder() {
        List<View> childViews = new ArrayList<>(getChildCount());
        for (int i = 0; i < getChildCount(); i++) {
            childViews.add(getChildAt(i));
        }
        if (mBubbleBarLocation.isOnLeft(isLayoutRtl())) {
            // Visually child views are shown in reverse order when bar is on the left
            return childViews.reversed();
        }
        return childViews;
    }

    private void updateDotsAndBadgesIfCollapsed() {
        if (isExpanded()) {
            return;
        }
        for (int i = 0; i < getChildCount(); i++) {
            BubbleView bubbleView = (BubbleView) getChildAt(i);
            // when we're collapsed, the first bubble should show the badge and the dot if it has
            // it. the rest of the bubbles should hide their badges and dots.
            if (i == 0) {
                bubbleView.showBadge();
                if (bubbleView.hasUnseenContent()) {
                    bubbleView.showDotIfNeeded(/* animate= */ true);
                } else {
                    bubbleView.hideDot();
                }
            } else {
                bubbleView.hideBadge();
                bubbleView.hideDot();
            }
        }
    }

    private void updateLayoutParams() {
        LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.height = (int) getBubbleBarExpandedHeight();
        lp.width = (int) (mIsBarExpanded ? expandedWidth() : collapsedWidth());
        setLayoutParams(lp);
    }

    private float getBubbleBarHeight() {
        return mIsBarExpanded ? getBubbleBarExpandedHeight()
                : getBubbleBarCollapsedHeight();
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
    private void updateBubblesLayoutProperties(BubbleBarLocation bubbleBarLocation) {
        final float widthState;
        if (mWidthAnimator == null) {
            widthState = mIsBarExpanded ? 1f : 0f;
        } else {
            widthState = (float) mWidthAnimator.getAnimatedValue();
        }
        final float currentWidth = getWidth();
        final float expandedWidth = expandedWidth();
        final float collapsedWidth = collapsedWidth();
        int childCount = getChildCount();
        final float ty = getBubbleTranslationY();
        final boolean onLeft = bubbleBarLocation.isOnLeft(isLayoutRtl());
        // elevation state is opposite to widthState - when expanded all icons are flat
        float elevationState = (1 - widthState);
        for (int i = 0; i < childCount; i++) {
            BubbleView bv = (BubbleView) getChildAt(i);
            if (bv == mDraggedBubbleView || bv == mDismissedByDragBubbleView) {
                // Skip the dragged bubble. Its translation is managed by the drag controller.
                continue;
            }
            // Clear out drag translation and offset
            bv.setDragTranslationX(0f);
            bv.setOffsetX(0f);

            if (mBubbleAnimator == null || !mBubbleAnimator.isRunning()) {
                // if the bubble animator is running don't set scale here, it will be set by the
                // animator
                bv.setScaleX(mIconScale);
                bv.setScaleY(mIconScale);
            }
            bv.setTranslationY(ty);

            // the position of the bubble when the bar is fully expanded
            final float expandedX = getExpandedBubbleTranslationX(i, childCount, onLeft);
            // the position of the bubble when the bar is fully collapsed
            final float collapsedX = getCollapsedBubbleTranslationX(i, childCount, onLeft);

            // slowly animate elevation while keeping correct Z ordering
            float fullElevationForChild = (MAX_BUBBLES * mBubbleElevation) - i;
            bv.setZ(fullElevationForChild * elevationState);

            // only update the dot and badge scale if we're expanding or collapsing
            if (mWidthAnimator != null && mWidthAnimator.isRunning()) {
                // The dot for the selected bubble scales in the opposite direction of the expansion
                // animation.
                bv.showDotIfNeeded(bv == mSelectedBubbleView ? 1 - widthState : widthState);
                // The badge for the selected bubble is always at full scale. All other bubbles
                // scale according to the expand animation.
                bv.setBadgeScale(bv == mSelectedBubbleView ? 1 : widthState);
            }

            if (mIsBarExpanded) {
                // If bar is on the right, account for bubble bar expanding and shifting left
                final float expandedBarShift = onLeft ? 0 : currentWidth - expandedWidth;
                // where the bubble will end up when the animation ends
                final float targetX = expandedX + expandedBarShift;
                bv.setTranslationX(widthState * (targetX - collapsedX) + collapsedX);
                bv.setVisibility(VISIBLE);
            } else {
                // If bar is on the right, account for bubble bar expanding and shifting left
                final float collapsedBarShift = onLeft ? 0 : currentWidth - collapsedWidth;
                final float targetX = collapsedX + collapsedBarShift;
                bv.setTranslationX(widthState * (expandedX - targetX) + targetX);
                // If we're fully collapsed, hide all bubbles except for the first 2, excluding
                // the overflow.
                if (widthState == 0) {
                    if (bv.isOverflow() || i > MAX_VISIBLE_BUBBLES_COLLAPSED - 1) {
                        bv.setVisibility(INVISIBLE);
                    } else {
                        bv.setVisibility(VISIBLE);
                    }
                }
            }
        }

        // update the arrow position
        final float collapsedArrowPosition = arrowPositionForSelectedWhenCollapsed(
                bubbleBarLocation);
        final float expandedArrowPosition = arrowPositionForSelectedWhenExpanded(bubbleBarLocation);
        final float interpolatedWidth =
                widthState * (expandedWidth - collapsedWidth) + collapsedWidth;
        final float arrowPosition;

        float interpolatedShift = (expandedArrowPosition - collapsedArrowPosition) * widthState;
        if (onLeft) {
            arrowPosition = collapsedArrowPosition + interpolatedShift;
        } else {
            if (mIsBarExpanded) {
                arrowPosition = currentWidth - interpolatedWidth + collapsedArrowPosition
                        + interpolatedShift;
            } else {
                final float targetPosition = currentWidth - collapsedWidth + collapsedArrowPosition;
                arrowPosition =
                        targetPosition + widthState * (expandedArrowPosition - targetPosition);
            }
        }
        mBubbleBarBackground.setArrowPosition(arrowPosition);
        mBubbleBarBackground.setArrowHeightFraction(widthState);
        mBubbleBarBackground.setWidth(interpolatedWidth);
        mBubbleBarBackground.setBackgroundHeight(getBubbleBarExpandedHeight());
    }

    private float getScaleIconShift() {
        return (mIconSize - getScaledIconSize()) / 2;
    }

    private float getExpandedBubbleTranslationX(int bubbleIndex, int bubbleCount, boolean onLeft) {
        if (bubbleIndex < 0 || bubbleIndex >= bubbleCount) {
            return 0;
        }
        final float iconAndSpacing = getScaledIconSize() + mExpandedBarIconsSpacing;
        float translationX;
        if (mBubbleAnimator != null && mBubbleAnimator.isRunning()) {
            return mBubbleAnimator.getBubbleTranslationX(bubbleIndex) + mBubbleBarPadding;
        } else if (onLeft) {
            translationX = mBubbleBarPadding + (bubbleCount - bubbleIndex - 1) * iconAndSpacing;
        } else {
            translationX = mBubbleBarPadding + bubbleIndex * iconAndSpacing;
        }
        return translationX - getScaleIconShift();
    }

    private float getCollapsedBubbleTranslationX(int bubbleIndex, int childCount, boolean onLeft) {
        if (bubbleIndex < 0 || bubbleIndex >= childCount) {
            return 0;
        }
        float translationX;
        if (onLeft) {
            // Shift the first bubble only if there are more bubbles
            if (bubbleIndex == 0 && getBubbleChildCount() >= MAX_VISIBLE_BUBBLES_COLLAPSED) {
                translationX = mIconOverlapAmount;
            } else {
                translationX = 0f;
            }
        } else {
            // when the bar is on the right, the first bubble always has translation 0. the only
            // case where another bubble has translation 0 is when we only have 1 bubble and the
            // overflow. otherwise all other bubbles should be shifted by the overlap amount.
            if (bubbleIndex == 0 || getBubbleChildCount() == 1) {
                translationX = 0f;
            } else {
                translationX = mIconOverlapAmount;
            }
        }
        return mBubbleBarPadding + translationX - getScaleIconShift();
    }

    private float getBubbleTranslationY() {
        float viewBottom = mBubbleBarBounds.height() + (isExpanded() ? mPointerSize : 0);
        float bubbleBarAnimatedTop = viewBottom - getBubbleBarHeight();
        // When translating X & Y the scale is ignored, so need to deduct it from the translations
        return mBubbleOffsetY + bubbleBarAnimatedTop + mBubbleBarPadding - getScaleIconShift();
    }

    /**
     * Reorders the views to match the provided list.
     */
    public void reorder(List<BubbleView> viewOrder) {
        if (isExpanded() || (mWidthAnimator != null && mWidthAnimator.isRunning())) {
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
            updateBubblesLayoutProperties(mBubbleBarLocation);
            updateContentDescription();
            updateDotsAndBadgesIfCollapsed();
        }
    }

    public void setUpdateSelectedBubbleAfterCollapse(
            Consumer<String> updateSelectedBubbleAfterCollapse) {
        mUpdateSelectedBubbleAfterCollapse = updateSelectedBubbleAfterCollapse;
    }

    public void setController(Controller controller) {
        mController = controller;
    }

    /**
     * Sets which bubble view should be shown as selected.
     */
    public void setSelectedBubble(BubbleView view) {
        BubbleView previouslySelectedBubble = mSelectedBubbleView;
        mSelectedBubbleView = view;
        mBubbleBarBackground.showArrow(view != null);

        // if bubbles are being animated, the arrow position will be set as part of the animation
        if (mBubbleAnimator == null) {
            updateArrowForSelected(previouslySelectedBubble != null);
        }
        if (view != null) {
            if (isExpanded()) {
                view.markSeen();
            } else {
                // when collapsed, the selected bubble should show the dot if it has it
                view.showDotIfNeeded(/* animate= */ true);
            }
        }
    }

    /**
     * Sets the dragged bubble view to correctly apply Z order. Dragged view should appear on top
     */
    public void setDraggedBubble(@Nullable BubbleView view) {
        if (mDraggedBubbleView != null) {
            mDraggedBubbleView.setZ(0);
        }
        mDraggedBubbleView = view;
        if (view != null) {
            view.setZ(mDragElevation);
            // we started dragging a bubble. reset the bubble that was previously dismissed by drag
            mDismissedByDragBubbleView = null;
        }
        setIsDragging(view != null);
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
        final float tx = arrowPositionForSelectedWhenExpanded(mBubbleBarLocation);
        final float currentArrowPosition = mBubbleBarBackground.getArrowPositionX();
        if (tx == currentArrowPosition) {
            // arrow position remains unchanged
            return;
        }
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

    private float arrowPositionForSelectedWhenExpanded(BubbleBarLocation bubbleBarLocation) {
        if (mBubbleAnimator != null && mBubbleAnimator.isRunning()) {
            return mBubbleAnimator.getArrowPosition() + mBubbleBarPadding;
        }
        final int index = indexOfChild(mSelectedBubbleView);
        final float selectedBubbleTranslationX = getExpandedBubbleTranslationX(
                index, getChildCount(), bubbleBarLocation.isOnLeft(isLayoutRtl()));
        return selectedBubbleTranslationX + mIconSize / 2f;
    }

    private float arrowPositionForSelectedWhenCollapsed(BubbleBarLocation bubbleBarLocation) {
        final int index = indexOfChild(mSelectedBubbleView);
        final int bubblePosition;
        if (bubbleBarLocation.isOnLeft(isLayoutRtl())) {
            // Bubble positions are reversed. First bubble may be shifted, if there are more
            // bubbles than the current bubble and overflow.
            bubblePosition = index == 0 && getChildCount() > MAX_VISIBLE_BUBBLES_COLLAPSED ? 1 : 0;
        } else {
            bubblePosition = index >= MAX_VISIBLE_BUBBLES_COLLAPSED
                    ? MAX_VISIBLE_BUBBLES_COLLAPSED - 1 : index;
        }
        return mBubbleBarPadding + bubblePosition * (mIconOverlapAmount) + getScaledIconSize() / 2f;
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
     * Update bubble bar expanded state.
     */
    public void setExpanded(boolean isBarExpanded) {
        setExpandedInternal(isBarExpanded, false);
    }

    /**
     * Update bubble bar expanded state with animation.
     * <p>
     * Also triggers a talkback announcement for accessibility.
     */
    public void animateExpanded(boolean isBarExpanded) {
        setExpandedInternal(isBarExpanded, true);
    }

    private void setExpandedInternal(boolean isBarExpanded, boolean animate) {
        if (mIsBarExpanded == isBarExpanded) return;
        mIsBarExpanded = isBarExpanded;
        updateArrowForSelected(/* shouldAnimate= */ false);
        setOrUnsetClickListener();
        if (animate) {
            mWidthAnimator = createExpansionAnimator(isBarExpanded);
            mWidthAnimator.start();
            announceExpandedStateChange();
        } else {
            onExpandedChanged();
        }
        updateBubbleAccessibilityStates();
        mController.onBubbleBarExpandedStateChanged(mIsBarExpanded);
    }


    /**
     * Returns whether the bubble bar is expanded.
     */
    public boolean isExpanded() {
        return mIsBarExpanded;
    }

    /**
     * Returns whether the bubble bar is expanding.
     */
    public boolean isExpanding() {
        return mWidthAnimator != null && mWidthAnimator.isRunning() && mIsBarExpanded;
    }

    /**
     * Get width of the bubble bar as if it would be expanded.
     *
     * @return width of the bubble bar in its expanded state, regardless of current width
     */
    public float expandedWidth() {
        final int childCount = getChildCount();
        final float horizontalPadding = 2 * mBubbleBarPadding;
        if (mBubbleAnimator != null && mBubbleAnimator.isRunning()) {
            return mBubbleAnimator.getExpandedWidth() + horizontalPadding;
        }
        // spaces amount is less than child count by 1, or 0 if no child views
        final float totalSpace = Math.max(childCount - 1, 0) * mExpandedBarIconsSpacing;
        final float totalIconSize = childCount * getScaledIconSize();
        return totalIconSize + totalSpace + horizontalPadding;
    }

    /**
     * Get width of the bubble bar if it is collapsed
     */
    float collapsedWidth() {
        final int bubbleChildCount = getBubbleChildCount();
        final float horizontalPadding = 2 * mBubbleBarPadding;
        // If there are more than 2 bubbles, the first 2 should be visible when collapsed,
        // excluding the overflow.
        return bubbleChildCount >= MAX_VISIBLE_BUBBLES_COLLAPSED
                ? getCollapsedWidthWithMaxVisibleBubbles()
                : getScaledIconSize() + horizontalPadding;
    }

    float getCollapsedWidthWithMaxVisibleBubbles()  {
        return getScaledIconSize() + mIconOverlapAmount + 2 * mBubbleBarPadding;
    }

    float getCollapsedWidthForIconSizeAndPadding(int iconSize, int bubbleBarPadding) {
        final int bubbleChildCount = Math.min(getBubbleChildCount(), MAX_VISIBLE_BUBBLES_COLLAPSED);
        if (bubbleChildCount == 0) return 0;
        final int spacesCount = bubbleChildCount - 1;
        final float horizontalPadding = 2 * bubbleBarPadding;
        return iconSize * bubbleChildCount + mIconOverlapAmount * spacesCount + horizontalPadding;
    }

    /** Returns the child count excluding the overflow if it's present. */
    int getBubbleChildCount() {
        return hasOverflow() ? getChildCount() - 1 : getChildCount();
    }

    private float getBubbleBarExpandedHeight() {
        return getBubbleBarCollapsedHeight() + mPointerSize;
    }

    float getArrowHeight() {
        return mPointerSize;
    }

    float getBubbleBarCollapsedHeight() {
        // the pointer is invisible when collapsed
        return getScaledIconSize() + mBubbleBarPadding * 2;
    }

    /**
     * Returns whether the given MotionEvent, *in screen coordinates*, is within bubble bar
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        if (getVisibility() == VISIBLE) {
            getBoundsOnScreen(mTempRect);
            return mTempRect.contains((int) ev.getX(), (int) ev.getY());
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mController.onBubbleBarTouched();
        if (!mIsBarExpanded) {
            // When the bar is collapsed, all taps on it should expand it.
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    private boolean hasOverflow() {
        // Overflow is always the last bubble
        View lastChild = getChildAt(getChildCount() - 1);
        if (lastChild instanceof BubbleView bubbleView) {
            return bubbleView.getBubble() instanceof BubbleBarOverflow;
        }
        return false;
    }

    private void updateBubbleAccessibilityStates() {
        if (mIsBarExpanded) {
            // Bar is expanded, focus on the bubbles
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            // Set up a11y navigation order. Get list of child views in the order they are shown
            // on screen. And use that to set up navigation so that swiping left focuses the view
            // on the left and swiping right focuses view on the right.
            View prevChild = null;
            for (View childView : getChildViewsInOnScreenOrder()) {
                childView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                childView.setFocusable(true);
                final View finalPrevChild = prevChild;
                // Always need to set a new delegate to clear out any previous.
                childView.setAccessibilityDelegate(new AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(View host,
                            AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        if (finalPrevChild != null) {
                            info.setTraversalAfter(finalPrevChild);
                        }
                    }
                });
                prevChild = childView;
            }
        } else {
            // Bar is collapsed, only focus on the bar
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            for (int i = 0; i < getChildCount(); i++) {
                View childView = getChildAt(i);
                childView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                childView.setFocusable(false);
            }
        }
    }

    private void updateContentDescription() {
        View firstChild = getChildAt(0);
        CharSequence contentDesc = firstChild != null ? firstChild.getContentDescription() : "";

        // Don't count overflow if it exists
        int bubbleCount = getChildCount() - (hasOverflow() ? 1 : 0);
        if (bubbleCount > 1) {
            contentDesc = getResources().getString(R.string.bubble_bar_description_multiple_bubbles,
                    contentDesc, bubbleCount - 1);
        }
        setContentDescription(contentDesc);
    }

    private void announceExpandedStateChange() {
        final CharSequence selectedBubbleContentDesc;
        if (mSelectedBubbleView != null) {
            selectedBubbleContentDesc = mSelectedBubbleView.getContentDescription();
        } else {
            selectedBubbleContentDesc = getResources().getString(
                    R.string.bubble_bar_bubble_fallback_description);
        }

        final String msg;
        if (mIsBarExpanded) {
            msg = getResources().getString(R.string.bubble_bar_accessibility_announce_expand,
                    selectedBubbleContentDesc);
        } else {
            msg = getResources().getString(R.string.bubble_bar_accessibility_announce_collapse,
                    selectedBubbleContentDesc);
        }
        announceForAccessibility(msg);
    }

    private boolean isIconSizeOrPaddingUpdated(float newIconSize, float newBubbleBarPadding) {
        return isIconSizeUpdated(newIconSize) || isPaddingUpdated(newBubbleBarPadding);
    }

    private boolean isIconSizeUpdated(float newIconSize) {
        return Float.compare(mIconSize, newIconSize) != 0;
    }

    private boolean isPaddingUpdated(float newBubbleBarPadding) {
        return Float.compare(mBubbleBarPadding, newBubbleBarPadding) != 0;
    }

    private void addAnimationCallBacks(@NonNull ValueAnimator animator,
            @Nullable Runnable onStart,
            @Nullable Runnable onEnd,
            @Nullable ValueAnimator.AnimatorUpdateListener onUpdate) {
        if (onUpdate != null) animator.addUpdateListener(onUpdate);
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationStart(Animator animator) {
                if (onStart != null) onStart.run();
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                if (onEnd != null) onEnd.run();
            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
    }

    /** Dumps the current state of BubbleBarView. */
    public void dump(PrintWriter pw) {
        pw.println("BubbleBarView state:");
        pw.println("  visibility: " + getVisibility());
        pw.println("  alpha: " + getAlpha());
        pw.println("  translationY: " + getTranslationY());
        pw.println("  childCount: " + getChildCount());
        pw.println("  hasOverflow:  " + hasOverflow());
        for (BubbleView bubbleView: getBubbles()) {
            BubbleBarItem bubble = bubbleView.getBubble();
            String key = bubble == null ? "null" : bubble.getKey();
            pw.println("    bubble key: " + key);
        }
        pw.println("  isExpanded: " + isExpanded());
        if (mBubbleAnimator != null) {
            pw.println("  mBubbleAnimator.isRunning(): " + mBubbleAnimator.isRunning());
            pw.println("  mBubbleAnimator is null");
        }
        pw.println("  mDragging: " + mDragging);
    }

    private List<BubbleView> getBubbles() {
        List<BubbleView> bubbles = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof BubbleView bubble) {
                bubbles.add(bubble);
            }
        }
        return bubbles;
    }

    /** Creates an animator based on the expanding or collapsing action. */
    private ValueAnimator createExpansionAnimator(boolean expanding) {
        float startValue = expanding ? 0 : 1;
        if ((mWidthAnimator != null && mWidthAnimator.isRunning())) {
            startValue = (float) mWidthAnimator.getAnimatedValue();
            mWidthAnimator.cancel();
        }
        float endValue = expanding ? 1 : 0;
        ValueAnimator animator = ValueAnimator.ofFloat(startValue, endValue);
        animator.setDuration(WIDTH_ANIMATION_DURATION_MS);
        animator.setInterpolator(Interpolators.EMPHASIZED);
        addAnimationCallBacks(animator,
                /* onStart= */ () -> mBubbleBarBackground.showArrow(true),
                /* onEnd= */ this::onExpandedChanged,
                /* onUpdate= */ anim -> {
                    updateBubblesLayoutProperties(mBubbleBarLocation);
                    invalidate();
                });
        return animator;
    }

    private void onExpandedChanged() {
        mBubbleBarBackground.showArrow(mIsBarExpanded);
        if (!mIsBarExpanded && mReorderRunnable != null) {
            mReorderRunnable.run();
            mReorderRunnable = null;
        }
        // If the bar was just collapsed and the overflow was the last bubble that was
        // selected, set the first bubble as selected.
        if (!mIsBarExpanded && mUpdateSelectedBubbleAfterCollapse != null
                && mSelectedBubbleView != null
                && mSelectedBubbleView.getBubble() instanceof BubbleBarOverflow) {
            BubbleView firstBubble = (BubbleView) getChildAt(0);
            mUpdateSelectedBubbleAfterCollapse.accept(firstBubble.getBubble().getKey());
        }
        // If the bar was just expanded, remove the dot from the selected bubble.
        if (mIsBarExpanded && mSelectedBubbleView != null) {
            mSelectedBubbleView.markSeen();
        }
        updateLayoutParams();
    }

    /**
     * Returns the distance between the top left corner of the bubble bar to the center of the dot
     * of the selected bubble.
     */
    PointF getSelectedBubbleDotDistanceFromTopLeft() {
        if (mSelectedBubbleView == null) {
            return new PointF(0, 0);
        }
        final int indexOfSelectedBubble = indexOfChild(mSelectedBubbleView);
        final boolean onLeft = mBubbleBarLocation.isOnLeft(isLayoutRtl());
        final float selectedBubbleTx = isExpanded()
                ? getExpandedBubbleTranslationX(indexOfSelectedBubble, getChildCount(), onLeft)
                : getCollapsedBubbleTranslationX(indexOfSelectedBubble, getChildCount(), onLeft);
        PointF selectedBubbleDotCenter = mSelectedBubbleView.getDotCenter();

        return new PointF(
                selectedBubbleTx + selectedBubbleDotCenter.x,
                mBubbleBarPadding + mPointerSize + selectedBubbleDotCenter.y);
    }

    int getSelectedBubbleDotColor() {
        return mSelectedBubbleView == null ? 0 : mSelectedBubbleView.getDotColor();
    }

    int getPointerSize() {
        return mPointerSize;
    }

    float getBubbleElevation() {
        return mBubbleElevation;
    }

    /** Interface for BubbleBarView to communicate with its controller. */
    public interface Controller {

        /** Returns the translation Y that the bubble bar should have. */
        float getBubbleBarTranslationY();

        /** Notifies the controller that the bubble bar was touched. */
        void onBubbleBarTouched();

        /** Requests the controller to expand bubble bar */
        void expandBubbleBar();

        /** Requests the controller to dismiss the bubble bar */
        void dismissBubbleBar();

        /** Requests the controller to update bubble bar location to the given value */
        void updateBubbleBarLocation(BubbleBarLocation location,
                @BubbleBarLocation.UpdateSource int source);

        /** Notifies the controller that bubble bar is being dragged */
        void setIsDragging(boolean dragging);

        /** Notifies the controller that bubble bar expanded state changed */
        void onBubbleBarExpandedStateChanged(boolean expanded);
    }
}
