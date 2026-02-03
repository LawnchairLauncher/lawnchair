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

import static com.android.wm.shell.shared.animation.Interpolators.DIM_INTERPOLATOR;

import android.graphics.Point;
import android.graphics.Rect;

/**
 * Default interface for a set of calculation classes, used for calculating various parallax and
 * dimming effects in split screen.
 */
public interface ParallaxSpec {
    /** Returns an int indicating which side of the screen is being dimmed (if any). */
    default int getDimmingSide(int position, DividerSnapAlgorithm snapAlgorithm,
            boolean isLeftRightSplit) {
        if (position < snapAlgorithm.getFirstSplitTarget().getPosition()) {
            return isLeftRightSplit ? DOCKED_LEFT : DOCKED_TOP;
        } else if (position > snapAlgorithm.getLastSplitTarget().getPosition()) {
            return isLeftRightSplit ? DOCKED_RIGHT : DOCKED_BOTTOM;
        }
        return DOCKED_INVALID;
    }

    /** Returns the dim amount that we'll apply to the app surface. 0f = no dim, 1f = full black */
    default float getDimValue(int position, DividerSnapAlgorithm snapAlgorithm) {
        float progressTowardScreenEdge =
                Math.max(0, Math.min(snapAlgorithm.calculateDismissingFraction(position), 1f));
        return DIM_INTERPOLATOR.getInterpolation(progressTowardScreenEdge);
    }

    /**
     * Calculates the amount to offset app surfaces to create nice parallax effects. Writes to
     * {@link ResizingEffectPolicy#mRetreatingSideParallax} and
     * {@link ResizingEffectPolicy#mAdvancingSideParallax}.
     */
    void getParallax(Point retreatingOut, Point advancingOut, int position,
            DividerSnapAlgorithm snapAlgorithm, boolean isLeftRightSplit, Rect displayBounds,
            Rect retreatingSurface, Rect retreatingContent, Rect advancingSurface,
            Rect advancingContent, int dimmingSide, boolean topLeftShrink, SplitState splitState);
}
