/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.common.split;

import static android.view.WindowManager.DOCKED_BOTTOM;
import static android.view.WindowManager.DOCKED_INVALID;
import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;
import static android.view.WindowManager.DOCKED_TOP;

import static com.android.wm.shell.common.split.SplitLayout.PARALLAX_ALIGN_CENTER;
import static com.android.wm.shell.common.split.SplitLayout.PARALLAX_DISMISSING;
import static com.android.wm.shell.common.split.SplitLayout.PARALLAX_FLEX;
import static com.android.wm.shell.common.split.SplitLayout.PARALLAX_FLEX_HYBRID;
import static com.android.wm.shell.common.split.SplitLayout.PARALLAX_NONE;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.SurfaceControl;

/**
 * This class governs how and when parallax and dimming effects are applied to task surfaces,
 * usually when the divider is being moved around by the user (or during an animation).
 */
class ResizingEffectPolicy {
    private final SplitLayout mSplitLayout;
    /** The parallax algorithm we are currently using. */
    private final int mParallaxType;
    /**
     * A convenience class, corresponding to {@link #mParallaxType}, that performs all the
     * calculations for parallax and dimming values.
     */
    private final ParallaxSpec mParallaxSpec;

    int mShrinkSide = DOCKED_INVALID;

    // The current dismissing side.
    int mDimmingSide = DOCKED_INVALID;

    /**
     * A {@link Point} that stores a single x and y value, representing the parallax translation
     * we use on the app that the divider is moving toward. The app is either shrinking in size or
     * getting pushed off the screen.
     */
    final Point mRetreatingSideParallax = new Point();
    /**
     * A {@link Point} that stores a single x and y value, representing the parallax translation
     * we use on the app that the divider is moving away from. The app is either growing in size or
     * getting pulled onto the screen.
     */
    final Point mAdvancingSideParallax = new Point();

    // The dimming value to hint the dismissing side and progress.
    float mDimValue = 0.0f;

    /**
     * Content bounds for the app that the divider is moving toward. This is the content that is
     * currently drawn at the start of the divider movement. It stays unchanged throughout the
     * divider's movement.
     */
    final Rect mRetreatingContent = new Rect();
    /**
     * Surface bounds for the app that the divider is moving toward. This is the "canvas" on
     * which an app could potentially be drawn. It changes on every frame as the divider moves
     * around.
     */
    final Rect mRetreatingSurface = new Rect();
    /**
     * Content bounds for the app that the divider is moving away from. This is the content that
     * is currently drawn at the start of the divider movement. It stays unchanged throughout
     * the divider's movement.
     */
    final Rect mAdvancingContent = new Rect();
    /**
     * Surface bounds for the app that the divider is moving away from. This is the "canvas" on
     * which an app could potentially be drawn. It changes on every frame as the divider moves
     * around.
     */
    final Rect mAdvancingSurface = new Rect();

    final Rect mTempRect = new Rect();
    final Rect mTempRect2 = new Rect();

    ResizingEffectPolicy(int parallaxType, SplitLayout splitLayout) {
        mParallaxType = parallaxType;
        mSplitLayout = splitLayout;
        switch (mParallaxType) {
            case PARALLAX_DISMISSING:
                mParallaxSpec = new DismissingParallaxSpec();
                break;
            case PARALLAX_ALIGN_CENTER:
                mParallaxSpec = new CenterParallaxSpec();
                break;
            case PARALLAX_FLEX:
                mParallaxSpec = new FlexParallaxSpec();
                break;
            case PARALLAX_FLEX_HYBRID:
                mParallaxSpec = new FlexHybridParallaxSpec();
                break;
            case PARALLAX_NONE:
            default:
                mParallaxSpec = new NoParallaxSpec();
                break;
        }
    }

    /**
     * Calculates the desired parallax and dimming values for a task surface and stores them in
     * {@link #mRetreatingSideParallax}, {@link #mAdvancingSideParallax}, and
     * {@link #mDimValue} These values will be then be applied in
     * {@link #adjustRootSurface} and {@link #adjustDimSurface} respectively.
     */
    void applyDividerPosition(int position, boolean isLeftRightSplit,
            DividerSnapAlgorithm snapAlgorithm, SplitState splitState) {
        mDimmingSide = DOCKED_INVALID;
        mRetreatingSideParallax.set(0, 0);
        mAdvancingSideParallax.set(0, 0);
        mDimValue = 0;
        Rect displayBounds = mSplitLayout.getRootBounds();

        // Figure out which side is shrinking, and assign retreating/advancing bounds
        final boolean topLeftShrink = isLeftRightSplit
                ? position < mSplitLayout.getTopLeftContentBounds().right
                : position < mSplitLayout.getTopLeftContentBounds().bottom;
        if (topLeftShrink) {
            mShrinkSide = isLeftRightSplit ? DOCKED_LEFT : DOCKED_TOP;
            mRetreatingContent.set(mSplitLayout.getTopLeftContentBounds());
            mRetreatingSurface.set(mSplitLayout.getTopLeftBounds());
            mAdvancingContent.set(mSplitLayout.getBottomRightContentBounds());
            mAdvancingSurface.set(mSplitLayout.getBottomRightBounds());
        } else {
            mShrinkSide = isLeftRightSplit ? DOCKED_RIGHT : DOCKED_BOTTOM;
            mRetreatingContent.set(mSplitLayout.getBottomRightContentBounds());
            mRetreatingSurface.set(mSplitLayout.getBottomRightBounds());
            mAdvancingContent.set(mSplitLayout.getTopLeftContentBounds());
            mAdvancingSurface.set(mSplitLayout.getTopLeftBounds());
        }

        // Figure out if we should be dimming one side
        mDimmingSide = mParallaxSpec.getDimmingSide(position, snapAlgorithm, isLeftRightSplit);

        // If so, calculate dimming
        if (mDimmingSide != DOCKED_INVALID) {
            mDimValue = mParallaxSpec.getDimValue(position, snapAlgorithm);
        }

        // Calculate parallax and modify mRetreatingSideParallax and mAdvancingSideParallax, for use
        // in adjustRootSurface().
        mParallaxSpec.getParallax(mRetreatingSideParallax, mAdvancingSideParallax, position,
                snapAlgorithm, isLeftRightSplit, displayBounds, mRetreatingSurface,
                mRetreatingContent, mAdvancingSurface, mAdvancingContent, mDimmingSide,
                topLeftShrink, splitState);
    }

    /** Applies the calculated parallax and dimming values to task surfaces. */
    void adjustRootSurface(SurfaceControl.Transaction t,
            SurfaceControl leash1, SurfaceControl leash2) {
        SurfaceControl retreatingLeash = null;
        SurfaceControl advancingLeash = null;

        if (mParallaxType == PARALLAX_DISMISSING) {
            switch (mDimmingSide) {
                case DOCKED_TOP:
                case DOCKED_LEFT:
                    retreatingLeash = leash1;
                    mTempRect.set(mSplitLayout.getTopLeftBounds());
                    advancingLeash = leash2;
                    mTempRect2.set(mSplitLayout.getBottomRightBounds());
                    break;
                case DOCKED_BOTTOM:
                case DOCKED_RIGHT:
                    retreatingLeash = leash2;
                    mTempRect.set(mSplitLayout.getBottomRightBounds());
                    advancingLeash = leash1;
                    mTempRect2.set(mSplitLayout.getTopLeftBounds());
                    break;
            }
        } else if (mParallaxType == PARALLAX_ALIGN_CENTER || mParallaxType == PARALLAX_FLEX
                || mParallaxType == PARALLAX_FLEX_HYBRID) {
            switch (mShrinkSide) {
                case DOCKED_TOP:
                case DOCKED_LEFT:
                    retreatingLeash = leash1;
                    mTempRect.set(mSplitLayout.getTopLeftBounds());
                    advancingLeash = leash2;
                    mTempRect2.set(mSplitLayout.getBottomRightBounds());
                    break;
                case DOCKED_BOTTOM:
                case DOCKED_RIGHT:
                    retreatingLeash = leash2;
                    mTempRect.set(mSplitLayout.getBottomRightBounds());
                    advancingLeash = leash1;
                    mTempRect2.set(mSplitLayout.getTopLeftBounds());
                    break;
            }
        }
        if (mParallaxType != PARALLAX_NONE
                && retreatingLeash != null && advancingLeash != null) {
            t.setPosition(retreatingLeash, mTempRect.left + mRetreatingSideParallax.x,
                    mTempRect.top + mRetreatingSideParallax.y);
            // Transform the screen-based split bounds to surface-based crop bounds.
            mTempRect.offsetTo(-mRetreatingSideParallax.x, -mRetreatingSideParallax.y);
            t.setWindowCrop(retreatingLeash, mTempRect);

            t.setPosition(advancingLeash, mTempRect2.left + mAdvancingSideParallax.x,
                    mTempRect2.top + mAdvancingSideParallax.y);
            // Transform the screen-based split bounds to surface-based crop bounds.
            mTempRect2.offsetTo(-mAdvancingSideParallax.x, -mAdvancingSideParallax.y);
            t.setWindowCrop(advancingLeash, mTempRect2);
        }
    }

    /**
     * Called on every frame while the user is dragging the divider to dismiss an app or move it
     * offscreen. Sets alpha and visibility on the two provided dim layers.
     */
    void adjustDimSurface(SurfaceControl.Transaction t,
            SurfaceControl dimLayer1, SurfaceControl dimLayer2) {
        SurfaceControl targetDimLayer;
        SurfaceControl oppositeDimLayer;
        switch (mDimmingSide) {
            case DOCKED_TOP:
            case DOCKED_LEFT:
                targetDimLayer = dimLayer1;
                oppositeDimLayer = dimLayer2;
                break;
            case DOCKED_BOTTOM:
            case DOCKED_RIGHT:
                targetDimLayer = dimLayer2;
                oppositeDimLayer = dimLayer1;
                break;
            case DOCKED_INVALID:
            default:
                t.setAlpha(dimLayer1, 0).hide(dimLayer1);
                t.setAlpha(dimLayer2, 0).hide(dimLayer2);
                return;
        }
        t.setAlpha(targetDimLayer, mDimValue)
                .setVisibility(targetDimLayer, mDimValue > 0.001f);
        t.setAlpha(oppositeDimLayer, 0f)
                .setVisibility(oppositeDimLayer, false);
    }
}
