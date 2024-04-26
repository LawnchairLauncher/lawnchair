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

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.TaskbarInsetsController;
import com.android.launcher3.taskbar.TaskbarStashController;
import com.android.launcher3.taskbar.bubbles.animation.BubbleBarViewAnimator;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.quickstep.SystemUiProxy;
import com.android.wm.shell.common.bubbles.BubbleBarLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Controller for {@link BubbleBarView}. Manages the visibility of the bubble bar as well as
 * responding to changes in bubble state provided by BubbleBarController.
 */
public class BubbleBarViewController {

    private static final String TAG = BubbleBarViewController.class.getSimpleName();
    private static final float APP_ICON_SMALL_DP = 44f;
    private static final float APP_ICON_MEDIUM_DP = 48f;
    private static final float APP_ICON_LARGE_DP = 52f;
    private final SystemUiProxy mSystemUiProxy;
    private final TaskbarActivityContext mActivity;
    private final BubbleBarView mBarView;
    private int mIconSize;

    // Initialized in init.
    private BubbleStashController mBubbleStashController;
    private BubbleBarController mBubbleBarController;
    private BubbleDragController mBubbleDragController;
    private TaskbarStashController mTaskbarStashController;
    private TaskbarInsetsController mTaskbarInsetsController;
    private View.OnClickListener mBubbleClickListener;
    private View.OnClickListener mBubbleBarClickListener;

    // These are exposed to {@link BubbleStashController} to animate for stashing/un-stashing
    private final MultiValueAlpha mBubbleBarAlpha;
    private final AnimatedFloat mBubbleBarScale = new AnimatedFloat(this::updateScale);
    private final AnimatedFloat mBubbleBarTranslationY = new AnimatedFloat(
            this::updateTranslationY);

    // Modified when swipe up is happening on the bubble bar or task bar.
    private float mBubbleBarSwipeUpTranslationY;

    // Whether the bar is hidden for a sysui state.
    private boolean mHiddenForSysui;
    // Whether the bar is hidden because there are no bubbles.
    private boolean mHiddenForNoBubbles = true;
    private boolean mShouldShowEducation;

    private BubbleBarViewAnimator mBubbleBarViewAnimator;

    @Nullable
    private BubbleBarBoundsChangeListener mBoundsChangeListener;

    private final Rect mPreviousBubbleBarBounds = new Rect();

    public BubbleBarViewController(TaskbarActivityContext activity, BubbleBarView barView) {
        mActivity = activity;
        mBarView = barView;
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(mActivity);
        mBubbleBarAlpha = new MultiValueAlpha(mBarView, 1 /* num alpha channels */);
        mBubbleBarAlpha.setUpdateVisibility(true);
        mIconSize = activity.getResources().getDimensionPixelSize(
                R.dimen.bubblebar_icon_size);
    }

    public void init(TaskbarControllers controllers, BubbleControllers bubbleControllers) {
        mBubbleStashController = bubbleControllers.bubbleStashController;
        mBubbleBarController = bubbleControllers.bubbleBarController;
        mBubbleDragController = bubbleControllers.bubbleDragController;
        mTaskbarStashController = controllers.taskbarStashController;
        mTaskbarInsetsController = controllers.taskbarInsetsController;

        mActivity.addOnDeviceProfileChangeListener(dp -> setBubbleBarIconSize(dp.taskbarIconSize));
        setBubbleBarIconSize(mActivity.getDeviceProfile().taskbarIconSize);
        mBubbleBarScale.updateValue(1f);
        mBubbleClickListener = v -> onBubbleClicked(v);
        mBubbleBarClickListener = v -> onBubbleBarClicked();
        mBubbleDragController.setupBubbleBarView(mBarView);
        mBarView.setOnClickListener(mBubbleBarClickListener);
        mBarView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    mTaskbarInsetsController.onTaskbarOrBubblebarWindowHeightOrInsetsChanged();
                    Rect bubbleBarBounds = mBarView.getBubbleBarBounds();
                    if (!bubbleBarBounds.equals(mPreviousBubbleBarBounds)) {
                        mPreviousBubbleBarBounds.set(bubbleBarBounds);
                        if (mBoundsChangeListener != null) {
                            mBoundsChangeListener.onBoundsChanged(bubbleBarBounds);
                        }
                    }
                });

        mBubbleBarViewAnimator = new BubbleBarViewAnimator(mBarView, mBubbleStashController);
    }

    private void onBubbleClicked(View v) {
        BubbleBarItem bubble = ((BubbleView) v).getBubble();
        if (bubble == null) {
            Log.e(TAG, "bubble click listener, bubble was null");
        }

        if (mBarView.isAnimatingNewBubble()) {
            mBubbleBarViewAnimator.onBubbleClickedWhileAnimating();
            mBubbleStashController.showBubbleBarImmediate();
            setExpanded(true);
            mBubbleBarController.showAndSelectBubble(bubble);
            return;
        }

        final String currentlySelected = mBubbleBarController.getSelectedBubbleKey();
        if (mBarView.isExpanded() && Objects.equals(bubble.getKey(), currentlySelected)) {
            // Tapping the currently selected bubble while expanded collapses the view.
            setExpanded(false);
            mBubbleStashController.stashBubbleBar();
        } else {
            mBubbleBarController.showAndSelectBubble(bubble);
        }
    }

    private void onBubbleBarClicked() {
        if (mShouldShowEducation) {
            mShouldShowEducation = false;
            // Get the bubble bar bounds on screen
            Rect bounds = new Rect();
            mBarView.getBoundsOnScreen(bounds);
            // Calculate user education reference position in Screen coordinates
            Point position = new Point(bounds.centerX(), bounds.top);
            // Show user education relative to the reference point
            mSystemUiProxy.showUserEducation(position);
        } else {
            setExpanded(true);
        }
    }

    //
    // The below animators are exposed to BubbleStashController so it can manage the stashing
    // animation.
    //

    public MultiPropertyFactory<View> getBubbleBarAlpha() {
        return mBubbleBarAlpha;
    }

    public AnimatedFloat getBubbleBarScale() {
        return mBubbleBarScale;
    }

    public AnimatedFloat getBubbleBarTranslationY() {
        return mBubbleBarTranslationY;
    }

    float getBubbleBarCollapsedHeight() {
        return mBarView.getBubbleBarCollapsedHeight();
    }

    /**
     * Whether the bubble bar is visible or not.
     */
    public boolean isBubbleBarVisible() {
        return mBarView.getVisibility() == VISIBLE;
    }

    /** Whether the bubble bar has bubbles. */
    public boolean hasBubbles() {
        return mBubbleBarController.getSelectedBubbleKey() != null;
    }

    /**
     * @return current {@link BubbleBarLocation}
     */
    public BubbleBarLocation getBubbleBarLocation() {
        return mBarView.getBubbleBarLocation();
    }

    /**
     * Update bar {@link BubbleBarLocation}
     */
    public void setBubbleBarLocation(BubbleBarLocation bubbleBarLocation, boolean animate) {
        mBarView.setBubbleBarLocation(bubbleBarLocation, animate);
    }

    /**
     * The bounds of the bubble bar.
     */
    public Rect getBubbleBarBounds() {
        return mBarView.getBubbleBarBounds();
    }

    /** Whether a new bubble is animating. */
    public boolean isAnimatingNewBubble() {
        return mBarView.isAnimatingNewBubble();
    }

    /** The horizontal margin of the bubble bar from the edge of the screen. */
    public int getHorizontalMargin() {
        return mBarView.getHorizontalMargin();
    }

    /**
     * When the bubble bar is not stashed, it can be collapsed (the icons are in a stack) or
     * expanded (the icons are in a row). This indicates whether the bubble bar is expanded.
     */
    public boolean isExpanded() {
        return mBarView.isExpanded();
    }

    /**
     * Whether the motion event is within the bounds of the bubble bar.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        return mBarView.isEventOverAnyItem(ev);
    }

    //
    // Visibility of the bubble bar
    //

    /**
     * Returns whether the bubble bar is hidden because there are no bubbles.
     */
    public boolean isHiddenForNoBubbles() {
        return mHiddenForNoBubbles;
    }

    /**
     * Sets whether the bubble bar should be hidden because there are no bubbles.
     */
    public void setHiddenForBubbles(boolean hidden) {
        if (mHiddenForNoBubbles != hidden) {
            mHiddenForNoBubbles = hidden;
            updateVisibilityForStateChange();
            if (hidden) {
                mBarView.setAlpha(0);
                mBarView.setExpanded(false);
            }
            mActivity.bubbleBarVisibilityChanged(!hidden);
        }
    }

    private void setBubbleBarIconSize(int newIconSize) {
        if (newIconSize == mIconSize) {
            return;
        }
        Resources res = mActivity.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        float smallIconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                APP_ICON_SMALL_DP, dm);
        float mediumIconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                APP_ICON_MEDIUM_DP, dm);
        float largeIconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                APP_ICON_LARGE_DP, dm);
        float smallMediumThreshold = (smallIconSize + mediumIconSize) / 2f;
        float mediumLargeThreshold = (mediumIconSize + largeIconSize) / 2f;
        mIconSize = newIconSize <= smallMediumThreshold
                ? res.getDimensionPixelSize(R.dimen.bubblebar_icon_size_small) :
                res.getDimensionPixelSize(R.dimen.bubblebar_icon_size);
        float bubbleBarPadding = newIconSize >= mediumLargeThreshold
                ? res.getDimensionPixelSize(R.dimen.bubblebar_icon_spacing_large) :
                res.getDimensionPixelSize(R.dimen.bubblebar_icon_spacing);

        mBarView.setIconSizeAndPadding(mIconSize, bubbleBarPadding);
        mBarView.setPadding((int) bubbleBarPadding, mBarView.getPaddingTop(),
                (int) bubbleBarPadding,
                mBarView.getPaddingBottom());
    }

    /** Sets a callback that updates the selected bubble after the bubble bar collapses. */
    public void setUpdateSelectedBubbleAfterCollapse(
            Consumer<String> updateSelectedBubbleAfterCollapse) {
        mBarView.setUpdateSelectedBubbleAfterCollapse(updateSelectedBubbleAfterCollapse);
    }

    /** Returns whether the bubble bar should be hidden because of the current sysui state. */
    boolean isHiddenForSysui() {
        return mHiddenForSysui;
    }

    /**
     * Sets whether the bubble bar should be hidden due to SysUI state (e.g. on lockscreen).
     */
    public void setHiddenForSysui(boolean hidden) {
        if (mHiddenForSysui != hidden) {
            mHiddenForSysui = hidden;
            updateVisibilityForStateChange();
        }
    }

    // TODO: (b/273592694) animate it
    private void updateVisibilityForStateChange() {
        if (!mHiddenForSysui && !mHiddenForNoBubbles) {
            mBarView.setVisibility(VISIBLE);
        } else {
            mBarView.setVisibility(INVISIBLE);
        }
    }

    //
    // Modifying view related properties.
    //

    /**
     * Sets the translation of the bubble bar during the swipe up gesture.
     */
    public void setTranslationYForSwipe(float transY) {
        mBubbleBarSwipeUpTranslationY = transY;
        updateTranslationY();
    }

    private void updateTranslationY() {
        mBarView.setTranslationY(mBubbleBarTranslationY.value
                + mBubbleBarSwipeUpTranslationY);
    }

    /**
     * Applies scale properties for the entire bubble bar.
     */
    private void updateScale() {
        float scale = mBubbleBarScale.value;
        mBarView.setScaleX(scale);
        mBarView.setScaleY(scale);
    }

    //
    // Manipulating the specific bubble views in the bar
    //

    /**
     * Removes the provided bubble from the bubble bar.
     */
    public void removeBubble(BubbleBarItem b) {
        if (b != null) {
            mBarView.removeView(b.getView());
        } else {
            Log.w(TAG, "removeBubble, bubble was null!");
        }
    }

    /**
     * Adds the provided bubble to the bubble bar.
     */
    public void addBubble(BubbleBarItem b, boolean isExpanding, boolean suppressAnimation) {
        if (b != null) {
            mBarView.addView(b.getView(), 0,
                    new FrameLayout.LayoutParams(mIconSize, mIconSize, Gravity.LEFT));
            b.getView().setOnClickListener(mBubbleClickListener);
            mBubbleDragController.setupBubbleView(b.getView());

            if (suppressAnimation) {
                return;
            }

            boolean isInApp = mTaskbarStashController.isInApp();
            // only animate the new bubble if we're in an app and not auto expanding
            if (b instanceof BubbleBarBubble && isInApp && !isExpanding && !isExpanded()) {
                mBubbleBarViewAnimator.animateBubbleInForStashed((BubbleBarBubble) b);
            }
        } else {
            Log.w(TAG, "addBubble, bubble was null!");
        }
    }

    /**
     * Reorders the bubbles based on the provided list.
     */
    public void reorderBubbles(List<BubbleBarBubble> newOrder) {
        List<BubbleView> viewList = newOrder.stream().filter(Objects::nonNull)
                .map(BubbleBarBubble::getView).toList();
        mBarView.reorder(viewList);
    }

    /**
     * Updates the selected bubble.
     */
    public void updateSelectedBubble(BubbleBarItem newlySelected) {
        mBarView.setSelectedBubble(newlySelected.getView());
    }

    /**
     * Sets whether the bubble bar should be expanded (not unstashed, but have the contents
     * within it expanded). This method notifies SystemUI that the bubble bar is expanded and
     * showing a selected bubble. This method should ONLY be called from UI events originating
     * from Launcher.
     */
    public void setExpanded(boolean isExpanded) {
        if (isExpanded != mBarView.isExpanded()) {
            mBarView.setExpanded(isExpanded);
            if (!isExpanded) {
                mSystemUiProxy.collapseBubbles();
            } else {
                mBubbleBarController.showSelectedBubble();
                mTaskbarStashController.updateAndAnimateTransientTaskbar(true /* stash */,
                        false /* shouldBubblesFollow */);
            }
        }
    }

    /**
     * Sets whether the bubble bar should be expanded. This method is used in response to UI events
     * from SystemUI.
     */
    public void setExpandedFromSysui(boolean isExpanded) {
        if (!isExpanded) {
            mBubbleStashController.stashBubbleBar();
        } else {
            mBubbleStashController.showBubbleBar(true /* expand the bubbles */);
        }
    }

    /** Marks as should show education and shows the bubble bar in a collapsed state */
    public void prepareToShowEducation() {
        mShouldShowEducation = true;
        mBubbleStashController.showBubbleBar(false /* expand the bubbles */);
    }

    /**
     * Updates the dragged bubble view in the bubble bar view, and notifies SystemUI
     * that a bubble is being dragged to dismiss.
     * @param bubbleView dragged bubble view
     */
    public void onDragStart(@NonNull BubbleView bubbleView) {
        if (bubbleView.getBubble() == null) return;
        mSystemUiProxy.onBubbleDrag(bubbleView.getBubble().getKey(), /* isBeingDragged = */ true);
        mBarView.setDraggedBubble(bubbleView);
    }

    /**
     * Notifies SystemUI to expand the selected bubble when the bubble is released.
     * @param bubbleView dragged bubble view
     */
    public void onDragRelease(@NonNull BubbleView bubbleView) {
        if (bubbleView.getBubble() == null) return;
        mSystemUiProxy.onBubbleDrag(bubbleView.getBubble().getKey(), /* isBeingDragged = */ false);
    }

    /**
     * Removes the dragged bubble view in the bubble bar view
     */
    public void onDragEnd() {
        mBarView.setDraggedBubble(null);
    }

    /**
     * Called when bubble was dragged into the dismiss target. Notifies System
     * @param bubble dismissed bubble item
     */
    public void onDismissBubbleWhileDragging(@NonNull BubbleBarItem bubble) {
        mSystemUiProxy.removeBubble(bubble.getKey());
    }

    /**
     * Called when bubble stack was dragged into the dismiss target
     */
    public void onDismissAllBubblesWhileDragging() {
        mSystemUiProxy.removeAllBubbles();
    }

    /**
     * Set listener to be notified when bubble bar bounds have changed
     */
    public void setBoundsChangeListener(@Nullable BubbleBarBoundsChangeListener listener) {
        mBoundsChangeListener = listener;
    }

    /**
     * Listener to receive updates about bubble bar bounds changing
     */
    public interface BubbleBarBoundsChangeListener {
        /** Called when bounds have changed */
        void onBoundsChanged(Rect newBounds);
    }
}
