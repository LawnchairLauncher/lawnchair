/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.wm.shell.shared.animation.Interpolators.DIM_INTERPOLATOR;
import static com.android.wm.shell.shared.animation.Interpolators.FAST_DIM_INTERPOLATOR;
import static com.android.wm.shell.shared.split.SplitScreenConstants.ANIMATING_OFFSCREEN_TAP;
import static com.android.wm.shell.shared.split.SplitScreenConstants.DEFAULT_OFFSCREEN_DIM;

import android.graphics.Point;
import android.graphics.Rect;

import com.android.wm.shell.shared.split.SplitScreenConstants;

/**
 * Calculation class, used when
 * {@link com.android.wm.shell.common.split.SplitLayout#PARALLAX_FLEX_HYBRID} is the desired
 * parallax effect.
 */
public class FlexHybridParallaxSpec implements ParallaxSpec {
    @Override
    public int getDimmingSide(int position, DividerSnapAlgorithm snapAlgorithm,
            boolean isLeftRightSplit) {
        int topLeftDimmingBreakpoint = snapAlgorithm.areOffscreenRatiosSupported()
                ? snapAlgorithm.getSecondSplitTarget().getPosition()
                : snapAlgorithm.getFirstSplitTarget().getPosition();
        int bottomRightDimmingBreakpoint = snapAlgorithm.areOffscreenRatiosSupported()
                ? snapAlgorithm.getSecondLastSplitTarget().getPosition()
                : snapAlgorithm.getLastSplitTarget().getPosition();
        if (position < topLeftDimmingBreakpoint) {
            return isLeftRightSplit ? DOCKED_LEFT : DOCKED_TOP;
        } else if (position > bottomRightDimmingBreakpoint) {
            return isLeftRightSplit ? DOCKED_RIGHT : DOCKED_BOTTOM;
        }
        return DOCKED_INVALID;
    }

    /**
     * Calculates the amount of dim to apply to a task surface moving offscreen in flexible split.
     * In flexible hybrid split, there are two dimming "behaviors".
     *   1) "slow dim": when moving the divider from the 33% mark to a target at 10%, or from 66% to
     *      90%, we dim the app slightly as it moves partially offscreen.
     *   2) "fast dim": when moving the divider from a side snap target further toward the screen
     *      edge, we dim the app rapidly as it approaches the dismiss point.
     * @return 0f = no dim applied. 1f = full black.
     */
    public float getDimValue(int position, DividerSnapAlgorithm snapAlgorithm) {
        // On tablets, apps don't go offscreen, so only dim for dismissal.
        if (!snapAlgorithm.areOffscreenRatiosSupported()) {
            return ParallaxSpec.super.getDimValue(position, snapAlgorithm);
        }

        int startDismissPos = snapAlgorithm.getDismissStartTarget().getPosition();
        int firstTargetPos = snapAlgorithm.getFirstSplitTarget().getPosition();
        int secondTargetPos = snapAlgorithm.getSecondSplitTarget().getPosition();
        int secondLastTargetPos = snapAlgorithm.getSecondLastSplitTarget().getPosition();
        int lastTargetPos = snapAlgorithm.getLastSplitTarget().getPosition();
        int endDismissPos = snapAlgorithm.getDismissEndTarget().getPosition();
        float progress;

        boolean between0and10 = startDismissPos <= position && position < firstTargetPos;
        boolean between10and33 = firstTargetPos <= position && position < secondTargetPos;
        boolean between66and90 = secondLastTargetPos <= position && position < lastTargetPos;
        boolean between90and100 = lastTargetPos <= position && position <= endDismissPos;

        if (between0and10) {
            // "Fast dim" as the divider moves toward the screen edge.
            progress = (float) (firstTargetPos - position) / (firstTargetPos - startDismissPos);
            return fastDim(progress);
        } else if (between10and33) {
            // "Slow dim" as the divider moves toward the left/top.
            progress = (float) (secondTargetPos - position) / (secondTargetPos - firstTargetPos);
            return slowDim(progress);
        } else if (between66and90) {
            // "Slow dim" as the divider moves toward the right/bottom.
            progress = (float) (position - secondLastTargetPos) / (lastTargetPos
                    - secondLastTargetPos);
            return slowDim(progress);
        } else if (between90and100) {
            // "Fast dim" as the divider moves toward the screen edge.
            progress = (float) (position - lastTargetPos) / (endDismissPos - lastTargetPos);
            return fastDim(progress);
        }
        // Divider is between 33 and 66, do not dim.
        return 0f;
    }

    /**
     * Used by {@link #getDimValue} to determine the amount to dim an app. Starts at zero and ramps
     * up to the default amount of dimming for an offscreen app,
     * {@link SplitScreenConstants#DEFAULT_OFFSCREEN_DIM}.
     */
    private float slowDim(float progress) {
        return DIM_INTERPOLATOR.getInterpolation(progress) * DEFAULT_OFFSCREEN_DIM;
    }

    /**
     * Used by {@link #getDimValue} to determine the amount to dim an app. Starts at
     * {@link SplitScreenConstants#DEFAULT_OFFSCREEN_DIM} and ramps up to 100% dim (full black).
     */
    private float fastDim(float progress) {
        return DEFAULT_OFFSCREEN_DIM + (FAST_DIM_INTERPOLATOR.getInterpolation(progress)
                * (1 - DEFAULT_OFFSCREEN_DIM));
    }

    @Override
    public void getParallax(Point retreatingOut, Point advancingOut, int position,
            DividerSnapAlgorithm snapAlgorithm, boolean isLeftRightSplit, Rect displayBounds,
            Rect retreatingSurface, Rect retreatingContent, Rect advancingSurface,
            Rect advancingContent, int dimmingSide, boolean topLeftShrink,
            SplitState splitState) {
        // If this is during the offscreen-tap animation, we add parallax equal to the amount that
        // the divider has moved, while canceling out any discrepancy caused by an offscreen
        // left/top edge.
        if (splitState.get() == ANIMATING_OFFSCREEN_TAP) {
            if (topLeftShrink) {
                if (isLeftRightSplit) {
                    int offscreenFactor = displayBounds.left - retreatingSurface.left;
                    int delta = retreatingSurface.right - retreatingContent.right;
                    retreatingOut.x = offscreenFactor + delta;
                } else {
                    int offscreenFactor = displayBounds.top - retreatingSurface.top;
                    int delta = retreatingSurface.bottom - retreatingContent.bottom;
                    retreatingOut.y = offscreenFactor + delta;
                }
            } else {
                if (isLeftRightSplit) {
                    int offscreenFactor = displayBounds.left - advancingSurface.left;
                    int delta = advancingSurface.right - advancingContent.right;
                    advancingOut.x = advancingContent.left + offscreenFactor + delta;
                } else {
                    int offscreenFactor = displayBounds.top - advancingSurface.top;
                    int delta = advancingSurface.bottom - advancingContent.bottom;
                    advancingOut.y = advancingContent.top + offscreenFactor + delta;
                }
            }
        } else {
            // App receives a parallax when pushed towards the side of the screen.
            if (topLeftShrink) {
                if (isLeftRightSplit) {
                    int offscreenFactor = displayBounds.left - retreatingSurface.left;
                    int delta = retreatingSurface.right - retreatingContent.right;
                    retreatingOut.x = offscreenFactor + (delta / 2);
                } else {
                    int offscreenFactor = displayBounds.top - retreatingSurface.top;
                    int delta = retreatingSurface.bottom - retreatingContent.bottom;
                    retreatingOut.y = offscreenFactor + (delta / 2);
                }
            } else {
                // Bottom/right surface doesn't need an offscreenFactor because content is naturally
                // aligned to the left and top edges of the surface.
                if (isLeftRightSplit) {
                    int delta = retreatingSurface.left - retreatingContent.left;
                    retreatingOut.x = -(delta / 2);
                } else {
                    int delta = retreatingSurface.top - retreatingContent.top;
                    retreatingOut.y = -(delta / 2);
                }
            }
        }
    }
}
