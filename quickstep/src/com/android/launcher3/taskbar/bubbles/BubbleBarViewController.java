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

import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.TaskbarInsetsController;
import com.android.launcher3.taskbar.TaskbarStashController;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.quickstep.SystemUiProxy;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Controller for {@link BubbleBarView}. Manages the visibility of the bubble bar as well as
 * responding to changes in bubble state provided by BubbleBarController.
 */
public class BubbleBarViewController {

    private static final String TAG = BubbleBarViewController.class.getSimpleName();

    private final SystemUiProxy mSystemUiProxy;
    private final TaskbarActivityContext mActivity;
    private final BubbleBarView mBarView;
    private final int mIconSize;

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
    private boolean mHiddenForNoBubbles;

    public BubbleBarViewController(TaskbarActivityContext activity, BubbleBarView barView) {
        mActivity = activity;
        mBarView = barView;
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(mActivity);
        mBubbleBarAlpha = new MultiValueAlpha(mBarView, 1 /* num alpha channels */);
        mBubbleBarAlpha.setUpdateVisibility(true);
        mIconSize = activity.getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_size);
    }

    public void init(TaskbarControllers controllers, BubbleControllers bubbleControllers) {
        mBubbleStashController = bubbleControllers.bubbleStashController;
        mBubbleBarController = bubbleControllers.bubbleBarController;
        mBubbleDragController = bubbleControllers.bubbleDragController;
        mTaskbarStashController = controllers.taskbarStashController;
        mTaskbarInsetsController = controllers.taskbarInsetsController;

        mActivity.addOnDeviceProfileChangeListener(dp ->
                mBarView.getLayoutParams().height = mActivity.getDeviceProfile().taskbarHeight
        );
        mBarView.getLayoutParams().height = mActivity.getDeviceProfile().taskbarHeight;
        mBubbleBarScale.updateValue(1f);
        mBubbleClickListener = v -> onBubbleClicked(v);
        mBubbleBarClickListener = v -> setExpanded(true);
        mBubbleDragController.setupBubbleBarView(mBarView);
        mBarView.setOnClickListener(mBubbleBarClickListener);
        mBarView.addOnLayoutChangeListener((view, i, i1, i2, i3, i4, i5, i6, i7) ->
                mTaskbarInsetsController.onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
        );
    }

    private void onBubbleClicked(View v) {
        BubbleBarItem bubble = ((BubbleView) v).getBubble();
        if (bubble == null) {
            Log.e(TAG, "bubble click listener, bubble was null");
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
     * The bounds of the bubble bar.
     */
    public Rect getBubbleBarBounds() {
        return mBarView.getBubbleBarBounds();
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
        }
    }

    /** Sets a callback that updates the selected bubble after the bubble bar collapses. */
    public void setUpdateSelectedBubbleAfterCollapse(
            Consumer<String> updateSelectedBubbleAfterCollapse) {
        mBarView.setUpdateSelectedBubbleAfterCollapse(updateSelectedBubbleAfterCollapse);
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
            mBarView.setAlpha(0);
            mBarView.setExpanded(false);
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
    public void addBubble(BubbleBarItem b) {
        if (b != null) {
            mBarView.addView(b.getView(), 0, new FrameLayout.LayoutParams(mIconSize, mIconSize));
            b.getView().setOnClickListener(mBubbleClickListener);
            mBubbleDragController.setupBubbleView(b.getView());
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
}
