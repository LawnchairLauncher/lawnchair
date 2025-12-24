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
 * Calculation class, used when {@link com.android.wm.shell.common.split.SplitLayout#PARALLAX_FLEX}
 * is the desired parallax effect.
 */
public class FlexParallaxSpec implements ParallaxSpec {
    final Rect mTempRect = new Rect();

    @Override
    public int getDimmingSide(int position, DividerSnapAlgorithm snapAlgorithm,
            boolean isLeftRightSplit) {
        if (position < snapAlgorithm.getMiddleTarget().getPosition()) {
            return isLeftRightSplit ? DOCKED_LEFT : DOCKED_TOP;
        } else if (position > snapAlgorithm.getMiddleTarget().getPosition()) {
            return isLeftRightSplit ? DOCKED_RIGHT : DOCKED_BOTTOM;
        }
        return DOCKED_INVALID;
    }

    /**
     * Calculates the amount of dim to apply to a task surface moving offscreen in flexible split.
     * In flexible split, there are two dimming "behaviors".
     *   1) "slow dim": when moving the divider from the middle of the screen to a target at 10% or
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
        int middleTargetPos = snapAlgorithm.getMiddleTarget().getPosition();
        int lastTargetPos = snapAlgorithm.getLastSplitTarget().getPosition();
        int endDismissPos = snapAlgorithm.getDismissEndTarget().getPosition();
        float progress;

        boolean between0and10 = startDismissPos <= position && position < firstTargetPos;
        boolean between10and50 = firstTargetPos <= position && position < middleTargetPos;
        boolean between50and90 = middleTargetPos <= position && position < lastTargetPos;
        boolean between90and100 = lastTargetPos <= position && position <= endDismissPos;

        if (between0and10) {
            // "Fast dim" as the divider moves toward the screen edge.
            progress = (float) (firstTargetPos - position) / (firstTargetPos - startDismissPos);
            return fastDim(progress);
        } else if (between10and50) {
            // "Slow dim" as the divider moves toward the left/top.
            progress = (float) (middleTargetPos - position) / (middleTargetPos - firstTargetPos);
            return slowDim(progress);
        } else if (between50and90) {
            // "Slow dim" as the divider moves toward the right/bottom.
            progress = (float) (position - middleTargetPos) / (lastTargetPos - middleTargetPos);
            return slowDim(progress);
        } else if (between90and100) {
            // "Fast dim" as the divider moves toward the screen edge.
            progress = (float) (position - lastTargetPos) / (endDismissPos - lastTargetPos);
            return fastDim(progress);
        }
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
        // Whether an app is getting pushed offscreen by the divider.
        boolean isRetreatingOffscreen = !displayBounds.contains(retreatingSurface);
        // Whether an app was getting pulled onscreen at the beginning of the drag.
        boolean advancingSideStartedOffscreen = !displayBounds.contains(advancingContent);

        // If this is during the offscreen-tap animation, we adjust the left-top app to simulate the
        // contents sticking to the divider. (Needed because the underlying surfaces are contracting
        // and expanding unevenly as they move on- and offscreen.)
        if (splitState.get() == ANIMATING_OFFSCREEN_TAP) {
            if (topLeftShrink) {
                if (isLeftRightSplit) {
                    retreatingOut.x = retreatingSurface.width() - retreatingContent.width();
                } else {
                    retreatingOut.y = retreatingSurface.height() - retreatingContent.height();
                }
            } else {
                if (isLeftRightSplit) {
                    advancingOut.x = advancingSurface.width() - advancingContent.width();
                } else {
                    advancingOut.y = advancingSurface.height() - advancingContent.height();
                }
            }
        } else if (isRetreatingOffscreen && !advancingSideStartedOffscreen) {
            // Simple user-controlled case when an app gets pushed offscreen (e.g. 50:50 -> 90:10).
            // On the left/top side, we use parallax to simulate the contents sticking to the
            // divider. (Not needed on the right/bottom side because of the natural left-top
            // alignment of content surfaces.)
            if (topLeftShrink) {
                if (isLeftRightSplit) {
                    retreatingOut.x = retreatingSurface.width() - retreatingContent.width();
                } else {
                    retreatingOut.y = retreatingSurface.height() - retreatingContent.height();
                }
            }
        } else {
            // All other user-controlled cases (10:90 -> 50:50, 10:90 -> 90:10, 10:90 -> dismiss)
            mTempRect.set(retreatingSurface);
            Point rootOffset = new Point();
            // 10:90 -> 50:50, 10:90, or dismiss right
            if (advancingSideStartedOffscreen) {
                // We have to handle a complicated case here to keep the parallax smooth.
                // When the divider crosses the 50% mark, the retreating-side app surface
                // will start expanding offscreen. This is expected and unavoidable, but
                // makes the parallax look disjointed. In order to preserve the illusion,
                // we add another offset (rootOffset) to simulate the surface staying
                // onscreen.
                if (mTempRect.intersect(displayBounds)) {
                    if (retreatingSurface.left < displayBounds.left) {
                        rootOffset.x = displayBounds.left - retreatingSurface.left;
                    }
                    if (retreatingSurface.top < displayBounds.top) {
                        rootOffset.y = displayBounds.top - retreatingSurface.top;
                    }
                }

                // On the left side, we again have to simulate the contents sticking to the
                // divider.
                if (!topLeftShrink) {
                    if (isLeftRightSplit) {
                        advancingOut.x = advancingSurface.width() - advancingContent.width();
                    } else {
                        advancingOut.y = advancingSurface.height() - advancingContent.height();
                    }
                }
            }

            // In all these cases, the shrinking app also receives a center parallax.
            if (isLeftRightSplit) {
                retreatingOut.x = rootOffset.x
                        + ((mTempRect.width() - retreatingContent.width()) / 2);
            } else {
                retreatingOut.y = rootOffset.y
                        + ((mTempRect.height() - retreatingContent.height()) / 2);
            }
        }
    }
}
