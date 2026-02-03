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

import static android.view.WindowManager.DOCKED_INVALID;

import static com.android.wm.shell.shared.animation.Interpolators.SLOWDOWN_INTERPOLATOR;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.WindowManager;

/**
 * Calculation class, used when
 * {@link com.android.wm.shell.common.split.SplitLayout#PARALLAX_DISMISSING} is the desired parallax
 * effect.
 */
public class DismissingParallaxSpec implements ParallaxSpec {
    @Override
    public void getParallax(Point retreatingOut, Point advancingOut, int position,
            DividerSnapAlgorithm snapAlgorithm, boolean isLeftRightSplit, Rect displayBounds,
            Rect retreatingSurface, Rect retreatingContent, Rect advancingSurface,
            Rect advancingContent, int dimmingSide, boolean topLeftShrink, SplitState splitState) {
        if (dimmingSide == DOCKED_INVALID) {
            return;
        }

        float progressTowardScreenEdge =
                Math.max(0, Math.min(snapAlgorithm.calculateDismissingFraction(position), 1f));
        int totalDismissingDistance = 0;
        if (position < snapAlgorithm.getFirstSplitTarget().getPosition()) {
            totalDismissingDistance = snapAlgorithm.getDismissStartTarget().getPosition()
                    - snapAlgorithm.getFirstSplitTarget().getPosition();
        } else if (position > snapAlgorithm.getLastSplitTarget().getPosition()) {
            totalDismissingDistance = snapAlgorithm.getLastSplitTarget().getPosition()
                    - snapAlgorithm.getDismissEndTarget().getPosition();
        }

        float parallaxFraction =
                calculateParallaxDismissingFraction(progressTowardScreenEdge, dimmingSide);
        if (isLeftRightSplit) {
            retreatingOut.x = (int) (parallaxFraction * totalDismissingDistance);
        } else {
            retreatingOut.y = (int) (parallaxFraction * totalDismissingDistance);
        }
    }

    /**
     * @return for a specified {@code fraction}, this returns an adjusted value that simulates a
     * slowing down parallax effect
     */
    private float calculateParallaxDismissingFraction(float fraction, int dockSide) {
        float result = SLOWDOWN_INTERPOLATOR.getInterpolation(fraction) / 3.5f;

        // Less parallax at the top, just because.
        if (dockSide == WindowManager.DOCKED_TOP) {
            result /= 2f;
        }
        return result;
    }
}
