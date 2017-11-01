/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3;

/**
 * Keeps track of when thresholds are passed during a pinch gesture,
 * used to inform {@link PinchAnimationManager} throughout.
 *
 * @see PinchToOverviewListener
 * @see PinchAnimationManager
 */
public class PinchThresholdManager {
    public static final float THRESHOLD_ZERO = 0.0f;
    public static final float THRESHOLD_ONE = 0.40f;
    public static final float THRESHOLD_TWO = 0.70f;
    public static final float THRESHOLD_THREE = 0.95f;

    private Workspace mWorkspace;

    private float mPassedThreshold = THRESHOLD_ZERO;

    public PinchThresholdManager(Workspace workspace) {
        mWorkspace = workspace;
    }

    /**
     * Uses the pinch progress to determine whether a threshold has been passed,
     * and asks the {@param animationManager} to animate if so.
     * @param progress From 0 to 1, where 0 is overview and 1 is workspace.
     * @param animationManager Animates the threshold change if one is passed.
     * @return The last passed threshold, one of
     *         {@link PinchThresholdManager#THRESHOLD_ZERO},
     *         {@link PinchThresholdManager#THRESHOLD_ONE},
     *         {@link PinchThresholdManager#THRESHOLD_TWO}, or
     *         {@link PinchThresholdManager#THRESHOLD_THREE}
     */
    public float updateAndAnimatePassedThreshold(float progress,
            PinchAnimationManager animationManager) {
        if (!mWorkspace.isInOverviewMode()) {
            // Invert the progress, because going from workspace to overview is 1 to 0.
            progress = 1f - progress;
        }

        float previousPassedThreshold = mPassedThreshold;

        if (progress < THRESHOLD_ONE) {
            mPassedThreshold = THRESHOLD_ZERO;
        } else if (progress < THRESHOLD_TWO) {
            mPassedThreshold = THRESHOLD_ONE;
        } else if (progress < THRESHOLD_THREE) {
            mPassedThreshold = THRESHOLD_TWO;
        } else {
            mPassedThreshold = THRESHOLD_THREE;
        }

        if (mPassedThreshold != previousPassedThreshold) {
            Workspace.State fromState = mWorkspace.isInOverviewMode() ? Workspace.State.OVERVIEW
                    : Workspace.State.NORMAL;
            Workspace.State toState = mWorkspace.isInOverviewMode() ? Workspace.State.NORMAL
                    : Workspace.State.OVERVIEW;
            float thresholdToAnimate = mPassedThreshold;
            if (mPassedThreshold < previousPassedThreshold) {
                // User reversed pinch, so heading back to the state that they started from.
                toState = fromState;
                thresholdToAnimate = previousPassedThreshold;
            }
            animationManager.animateThreshold(thresholdToAnimate, fromState, toState);
        }
        return mPassedThreshold;
    }

    public float getPassedThreshold() {
        return mPassedThreshold;
    }

    public void reset() {
        mPassedThreshold = THRESHOLD_ZERO;
    }
}
