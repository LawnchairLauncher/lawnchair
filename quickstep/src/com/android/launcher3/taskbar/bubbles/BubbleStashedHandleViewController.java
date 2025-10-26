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
import android.annotation.Nullable;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.RevealOutlineAnimation;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.taskbar.StashedHandleView;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.wm.shell.shared.animation.PhysicsAnimator;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;
import com.android.wm.shell.shared.handles.RegionSamplingHelper;

/**
 * Handles properties/data collection, then passes the results to our stashed handle View to render.
 */
public class BubbleStashedHandleViewController {

    private final TaskbarActivityContext mActivity;
    private final StashedHandleView mStashedHandleView;
    private final MultiValueAlpha mStashedHandleAlpha;
    private float mTranslationForSwipeY;
    private float mTranslationForStashY;

    // Initialized in init.
    private BubbleBarViewController mBarViewController;
    private BubbleStashController mBubbleStashController;
    private RegionSamplingHelper mRegionSamplingHelper;
    // Height of the area for the stash handle. Handle will be drawn in the center of this.
    // This is also the area where touch is handled on the handle.
    private int mStashedBubbleBarHeight;
    private int mStashedHandleWidth;
    private int mStashedHandleHeight;

    // The bounds of the stashed handle in settled state.
    private final Rect mStashedHandleBounds = new Rect();
    private float mStashedHandleRadius;

    // When the reveal animation is cancelled, we can assume it's about to create a new animation,
    // which should start off at the same point the cancelled one left off.
    private float mStartProgressForNextRevealAnim;
    // Use a nullable boolean to handle initial case where the last animation direction is not known
    @Nullable
    private Boolean mWasLastRevealAnimReversed = null;

    // XXX: if there are more of these maybe do state flags instead
    private boolean mHiddenForSysui;
    private boolean mHiddenForNoBubbles;
    private boolean mHiddenForHomeButtonDisabled;

    public BubbleStashedHandleViewController(TaskbarActivityContext activity,
            StashedHandleView stashedHandleView) {
        mActivity = activity;
        mStashedHandleView = stashedHandleView;
        mStashedHandleAlpha = new MultiValueAlpha(mStashedHandleView, 1);
        mStashedHandleAlpha.setUpdateVisibility(true);
    }

    /** Initialize controller. */
    public void init(BubbleControllers bubbleControllers) {
        mBarViewController = bubbleControllers.bubbleBarViewController;
        mBubbleStashController = bubbleControllers.bubbleStashController;

        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        Resources resources = mActivity.getResources();
        mStashedHandleHeight = resources.getDimensionPixelSize(
                R.dimen.bubblebar_stashed_handle_height);
        mStashedHandleWidth = resources.getDimensionPixelSize(
                R.dimen.bubblebar_stashed_handle_width);

        int barSize = resources.getDimensionPixelSize(R.dimen.bubblebar_size);
        // Use the max translation for bubble bar whether it is on the home screen or in app.
        // Use values directly from device profile to avoid referencing other bubble controllers
        // during init flow.
        int maxTy = Math.max(deviceProfile.hotseatBarBottomSpacePx,
                deviceProfile.taskbarBottomMargin);
        // Adjust handle view size to accommodate the handle morphing into the bubble bar
        mStashedHandleView.getLayoutParams().height = barSize + maxTy;

        mStashedHandleAlpha.get(0).setValue(0);

        mStashedBubbleBarHeight = resources.getDimensionPixelSize(
                R.dimen.bubblebar_stashed_size);
        mStashedHandleView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                mStashedHandleRadius = view.getHeight() / 2f;
                outline.setRoundRect(mStashedHandleBounds, mStashedHandleRadius);
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
                }, Executors.MAIN_EXECUTOR, Executors.UI_HELPER_EXECUTOR);

        mStashedHandleView.addOnLayoutChangeListener((view, i, i1, i2, i3, i4, i5, i6, i7) ->
                updateBounds(mBarViewController.getBubbleBarLocation()));
    }

    /** Returns the [PhysicsAnimator] for the stashed handle view. */
    public PhysicsAnimator<View> getPhysicsAnimator() {
        return PhysicsAnimator.getInstance(mStashedHandleView);
    }

    private void updateBounds(BubbleBarLocation bubbleBarLocation) {
        // As more bubbles get added, the icon bounds become larger. To ensure a consistent
        // handle bar position, we pin it to the edge of the screen.
        final int stashedCenterY = mStashedHandleView.getHeight() - mStashedBubbleBarHeight / 2;
        final int stashedCenterX;
        if (bubbleBarLocation.isOnLeft(mStashedHandleView.isLayoutRtl())) {
            final int left = mBarViewController.getHorizontalMargin();
            stashedCenterX = left + mStashedHandleWidth / 2;
        } else {
            final int right =
                    mStashedHandleView.getRight() - mBarViewController.getHorizontalMargin();
            stashedCenterX = right - mStashedHandleWidth / 2;
        }
        mStashedHandleBounds.set(
                stashedCenterX - mStashedHandleWidth / 2,
                stashedCenterY - mStashedHandleHeight / 2,
                stashedCenterX + mStashedHandleWidth / 2,
                stashedCenterY + mStashedHandleHeight / 2
        );
        mStashedHandleView.updateSampledRegion(mStashedHandleBounds);
        mStashedHandleView.setPivotX(stashedCenterX);
        mStashedHandleView.setPivotY(stashedCenterY);
    }

    public void onDestroy() {
        mRegionSamplingHelper.stopAndDestroy();
        mRegionSamplingHelper = null;
    }

    /**
     * Returns the width of the stashed handle.
     */
    public int getStashedWidth() {
        return mStashedHandleWidth;
    }

    /**
     * Returns the height of the stashed handle.
     */
    public int getStashedHeight() {
        return mStashedHandleHeight;
    }

    /**
     * Returns bounds of the stashed handle view
     */
    public void getBounds(Rect bounds) {
        bounds.set(mStashedHandleBounds);
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
        mTranslationForSwipeY = transY;
        updateTranslationY();
    }

    /**
     * Sets the translation of the stashed handle during the spring on stash animation.
     */
    public void setTranslationYForStash(float transY) {
        mTranslationForStashY = transY;
        updateTranslationY();
    }

    /** Sets translation X for stash handle. */
    public void setTranslationX(float translationX) {
        mStashedHandleView.setTranslationX(translationX);
    }

    private void updateTranslationY() {
        mStashedHandleView.setTranslationY(mTranslationForSwipeY + mTranslationForStashY);
    }

    /** Returns the translation of the stashed handle. */
    public float getTranslationY() {
        return mStashedHandleView.getTranslationY();
    }

    /**
     * Used by {@link BubbleStashController} to animate the handle when stashing or un stashing.
     */
    public MultiPropertyFactory<View> getStashedHandleAlpha() {
        return mStashedHandleAlpha;
    }

    /**
     * Creates and returns an Animator that updates the stashed handle  shape and size.
     * When stashed, the shape is a thin rounded pill. When unstashed, the shape morphs into
     * the size of where the bubble bar icons will be.
     */
    public Animator createRevealAnimToIsStashed(boolean isStashed) {
        Rect bubbleBarBounds = getLocalBubbleBarBounds();

        float bubbleBarRadius = bubbleBarBounds.height() / 2f;
        final RevealOutlineAnimation handleRevealProvider = new RoundedRectRevealOutlineProvider(
                bubbleBarRadius, mStashedHandleRadius, bubbleBarBounds, mStashedHandleBounds);

        boolean isReversed = !isStashed;
        // We are only changing direction when mWasLastRevealAnimReversed is set at least once
        boolean changingDirection =
                mWasLastRevealAnimReversed != null && mWasLastRevealAnimReversed != isReversed;

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

    /**
     * Get bounds for the bubble bar in the space of the handle view
     */
    private Rect getLocalBubbleBarBounds() {
        // Position the bubble bar bounds to the space of handle view
        Rect bubbleBarBounds = new Rect(mBarViewController.getBubbleBarBounds());
        // Start by moving bubble bar bounds to the bottom of handle view
        int height = bubbleBarBounds.height();
        bubbleBarBounds.bottom = mStashedHandleView.getHeight();
        bubbleBarBounds.top = bubbleBarBounds.bottom - height;
        // Then apply translation that is applied to the bubble bar
        bubbleBarBounds.offset(0, (int) mBubbleStashController.getBubbleBarTranslationY());
        return bubbleBarBounds;
    }

    /** Checks that the stash handle is visible and that the motion event is within bounds. */
    public boolean isEventOverHandle(MotionEvent ev) {
        if (mStashedHandleView.getVisibility() != VISIBLE) {
            return false;
        }

        // the bounds of the handle only include the visible part, so we check that the Y coordinate
        // is anywhere within the stashed height of bubble bar (same as taskbar stashed height).
        final int top = mActivity.getDeviceProfile().heightPx - mStashedBubbleBarHeight;
        final float x = ev.getRawX();
        return ev.getRawY() >= top && x >= mStashedHandleBounds.left
                && x <= mStashedHandleBounds.right;
    }

    /** Set a bubble bar location */
    public void setBubbleBarLocation(BubbleBarLocation bubbleBarLocation) {
        updateBounds(bubbleBarLocation);
    }
}
