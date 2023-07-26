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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.android.launcher3.R;
import com.android.launcher3.anim.RevealOutlineAnimation;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.taskbar.StashedHandleView;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.systemui.shared.navigationbar.RegionSamplingHelper;

/**
 * Handles properties/data collection, then passes the results to our stashed handle View to render.
 */
public class BubbleStashedHandleViewController {

    private final TaskbarActivityContext mActivity;
    private final StashedHandleView mStashedHandleView;
    private final MultiValueAlpha mTaskbarStashedHandleAlpha;

    // Initialized in init.
    private BubbleBarViewController mBarViewController;
    private BubbleStashController mBubbleStashController;
    private RegionSamplingHelper mRegionSamplingHelper;
    private int mBarSize;
    private int mStashedTaskbarHeight;
    private int mStashedHandleWidth;
    private int mStashedHandleHeight;

    // The bounds we want to clip to in the settled state when showing the stashed handle.
    private final Rect mStashedHandleBounds = new Rect();

    // When the reveal animation is cancelled, we can assume it's about to create a new animation,
    // which should start off at the same point the cancelled one left off.
    private float mStartProgressForNextRevealAnim;
    private boolean mWasLastRevealAnimReversed;

    // XXX: if there are more of these maybe do state flags instead
    private boolean mHiddenForSysui;
    private boolean mHiddenForNoBubbles;
    private boolean mHiddenForHomeButtonDisabled;

    public BubbleStashedHandleViewController(TaskbarActivityContext activity,
            StashedHandleView stashedHandleView) {
        mActivity = activity;
        mStashedHandleView = stashedHandleView;
        mTaskbarStashedHandleAlpha = new MultiValueAlpha(mStashedHandleView, 1);
    }

    public void init(TaskbarControllers controllers, BubbleControllers bubbleControllers) {
        mBarViewController = bubbleControllers.bubbleBarViewController;
        mBubbleStashController = bubbleControllers.bubbleStashController;

        Resources resources = mActivity.getResources();
        mStashedHandleHeight = resources.getDimensionPixelSize(
                R.dimen.bubblebar_stashed_handle_height);
        mStashedHandleWidth = resources.getDimensionPixelSize(
                R.dimen.bubblebar_stashed_handle_width);
        mBarSize = resources.getDimensionPixelSize(R.dimen.bubblebar_size);

        final int bottomMargin = resources.getDimensionPixelSize(
                R.dimen.transient_taskbar_bottom_margin);
        mStashedHandleView.getLayoutParams().height = mBarSize + bottomMargin;

        mTaskbarStashedHandleAlpha.get(0).setValue(0);

        mStashedTaskbarHeight = resources.getDimensionPixelSize(
                R.dimen.bubblebar_stashed_size);
        mStashedHandleView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float stashedHandleRadius = view.getHeight() / 2f;
                outline.setRoundRect(mStashedHandleBounds, stashedHandleRadius);
            }
        });

        mRegionSamplingHelper = new RegionSamplingHelper(mStashedHandleView,
                new RegionSamplingHelper.SamplingCallback() {
                    @Override
                    public void onRegionDarknessChanged(boolean isRegionDark) {
                        mStashedHandleView.updateHandleColor(isRegionDark, true /* animate */);
                    }

                    @Override
                    public Rect getSampledRegion(View sampledView) {
                        return mStashedHandleView.getSampledRegion();
                    }
                }, Executors.UI_HELPER_EXECUTOR);

        mStashedHandleView.addOnLayoutChangeListener((view, i, i1, i2, i3, i4, i5, i6, i7) ->
                updateBounds());
    }

    private void updateBounds() {
        // As more bubbles get added, the icon bounds become larger. To ensure a consistent
        // handle bar position, we pin it to the edge of the screen.
        final int right =
                mActivity.getDeviceProfile().widthPx - mBarViewController.getHorizontalMargin();

        final int stashedCenterY = mStashedHandleView.getHeight() - mStashedTaskbarHeight / 2;

        mStashedHandleBounds.set(
                right - mStashedHandleWidth,
                stashedCenterY - mStashedHandleHeight / 2,
                right,
                stashedCenterY + mStashedHandleHeight / 2);
        mStashedHandleView.updateSampledRegion(mStashedHandleBounds);

        mStashedHandleView.setPivotX(mStashedHandleView.getWidth());
        mStashedHandleView.setPivotY(mStashedHandleView.getHeight() - mStashedTaskbarHeight / 2f);
    }

    public void onDestroy() {
        mRegionSamplingHelper.stopAndDestroy();
        mRegionSamplingHelper = null;
    }

    /**
     * Returns the height of the stashed handle.
     */
    public int getStashedHeight() {
        return mStashedHandleHeight;
    }

    /**
     * Returns the height when the bubble bar is unstashed (so the height of the bubble bar).
     */
    public int getUnstashedHeight() {
        return mBarSize;
    }

    /**
     * Called when system ui state changes. Bubbles don't show when the device is locked.
     */
    public void setHiddenForSysui(boolean hidden) {
        if (mHiddenForSysui != hidden) {
            mHiddenForSysui = hidden;
            updateVisibilityForStateChange();
        }
    }

    /**
     * Called when the handle should be hidden (or shown) because there are no bubbles
     * (or 1+ bubbles).
     */
    public void setHiddenForBubbles(boolean hidden) {
        if (mHiddenForNoBubbles != hidden) {
            mHiddenForNoBubbles = hidden;
            updateVisibilityForStateChange();
        }
    }

    /**
     * Called when the home button is enabled / disabled. Bubbles don't show if home is disabled.
     */
    // TODO: is this needed for bubbles?
    public void setIsHomeButtonDisabled(boolean homeDisabled) {
        mHiddenForHomeButtonDisabled = homeDisabled;
        updateVisibilityForStateChange();
    }

    // TODO: (b/273592694) animate it?
    private void updateVisibilityForStateChange() {
        if (!mHiddenForSysui && !mHiddenForHomeButtonDisabled && !mHiddenForNoBubbles) {
            mStashedHandleView.setVisibility(VISIBLE);
        } else {
            mStashedHandleView.setVisibility(INVISIBLE);
            mStashedHandleView.setAlpha(0);
        }
        updateRegionSampling();
    }

    /**
     * Called when bubble bar is stash state changes so that updates to the stashed handle color
     * can be started or stopped.
     */
    public void onIsStashedChanged() {
        updateRegionSampling();
    }

    private void updateRegionSampling() {
        boolean handleVisible = mStashedHandleView.getVisibility() == VISIBLE
                && mBubbleStashController.isStashed();
        if (mRegionSamplingHelper != null) {
            mRegionSamplingHelper.setWindowVisible(handleVisible);
            if (handleVisible) {
                mStashedHandleView.updateSampledRegion(mStashedHandleBounds);
                mRegionSamplingHelper.start(mStashedHandleView.getSampledRegion());
            } else {
                mRegionSamplingHelper.stop();
            }
        }
    }

    /**
     * Sets the translation of the stashed handle during the swipe up gesture.
     */
    public void setTranslationYForSwipe(float transY) {
        mStashedHandleView.setTranslationY(transY);
    }

    /**
     * Used by {@link BubbleStashController} to animate the handle when stashing or un stashing.
     */
    public MultiPropertyFactory<View> getStashedHandleAlpha() {
        return mTaskbarStashedHandleAlpha;
    }

    /**
     * Creates and returns an Animator that updates the stashed handle  shape and size.
     * When stashed, the shape is a thin rounded pill. When unstashed, the shape morphs into
     * the size of where the bubble bar icons will be.
     */
    public Animator createRevealAnimToIsStashed(boolean isStashed) {
        Rect bubbleBarBounds = new Rect(mBarViewController.getBubbleBarBounds());

        // Account for the full visual height of the bubble bar
        int heightDiff = (mBarSize - bubbleBarBounds.height()) / 2;
        bubbleBarBounds.top -= heightDiff;
        bubbleBarBounds.bottom += heightDiff;
        float stashedHandleRadius = mStashedHandleView.getHeight() / 2f;
        final RevealOutlineAnimation handleRevealProvider = new RoundedRectRevealOutlineProvider(
                stashedHandleRadius, stashedHandleRadius, bubbleBarBounds, mStashedHandleBounds);

        boolean isReversed = !isStashed;
        boolean changingDirection = mWasLastRevealAnimReversed != isReversed;
        mWasLastRevealAnimReversed = isReversed;
        if (changingDirection) {
            mStartProgressForNextRevealAnim = 1f - mStartProgressForNextRevealAnim;
        }

        ValueAnimator revealAnim = handleRevealProvider.createRevealAnimator(mStashedHandleView,
                isReversed, mStartProgressForNextRevealAnim);
        revealAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mStartProgressForNextRevealAnim = ((ValueAnimator) animation).getAnimatedFraction();
            }
        });
        return revealAnim;
    }
}
