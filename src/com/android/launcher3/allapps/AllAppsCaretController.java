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
package com.android.launcher3.allapps;

import android.animation.ObjectAnimator;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.pageindicators.CaretDrawable;
import com.android.launcher3.touch.SwipeDetector;

public class AllAppsCaretController {
    // Determines when the caret should flip. Should be accessed via getThreshold()
    private static final float CARET_THRESHOLD = 0.015f;
    private static final float CARET_THRESHOLD_LAND = 0.5f;
    // The velocity at which the caret will peak (i.e. exhibit a 90 degree bend)
    private static final float PEAK_VELOCITY = SwipeDetector.RELEASE_VELOCITY_PX_MS * .7f;

    private Launcher mLauncher;

    private ObjectAnimator mCaretAnimator;
    private CaretDrawable mCaretDrawable;
    private float mLastCaretProgress;
    private boolean mThresholdCrossed;

    public AllAppsCaretController(CaretDrawable caret, Launcher launcher) {
        mLauncher = launcher;
        mCaretDrawable = caret;

        final long caretAnimationDuration = launcher.getResources().getInteger(
                R.integer.config_caretAnimationDuration);
        final Interpolator caretInterpolator = AnimationUtils.loadInterpolator(launcher,
                android.R.interpolator.fast_out_slow_in);

        // We will set values later
        mCaretAnimator = ObjectAnimator.ofFloat(mCaretDrawable, "caretProgress", 0);
        mCaretAnimator.setDuration(caretAnimationDuration);
        mCaretAnimator.setInterpolator(caretInterpolator);
    }

    /**
     * Updates the state of the caret based on the progress of the {@link AllAppsContainerView}, as
     * defined by the {@link AllAppsTransitionController}. Uses the container's velocity to adjust
     * angle of caret.
     *
     * @param containerProgress The progress of the container in the range [0..1]
     * @param velocity The velocity of the container
     * @param dragging {@code true} if the container is being dragged
     */
    public void updateCaret(float containerProgress, float velocity, boolean dragging) {
        // If we're in portrait and the shift is not 0 or 1, adjust the caret based on velocity
        if (getThreshold() < containerProgress && containerProgress < 1 - getThreshold() &&
                !mLauncher.useVerticalBarLayout()) {
            mThresholdCrossed = true;

            // How fast are we moving as a percentage of the peak velocity?
            final float pctOfFlingVelocity = Math.max(-1, Math.min(velocity / PEAK_VELOCITY, 1));

            mCaretDrawable.setCaretProgress(pctOfFlingVelocity);

            // Set the last caret progress to this progress to prevent animator cancellation
            mLastCaretProgress = pctOfFlingVelocity;
            // Animate to neutral. This is necessary so the caret doesn't "freeze" when the
            // container stops moving (e.g., during a drag or when the threshold is reached).
            animateCaretToProgress(CaretDrawable.PROGRESS_CARET_NEUTRAL);
        } else if (!dragging) {
            // Otherwise, if we're not dragging, match the caret to the appropriate state
            if (containerProgress <= getThreshold()) { // All Apps is up
                animateCaretToProgress(CaretDrawable.PROGRESS_CARET_POINTING_DOWN);
            } else if (containerProgress >= 1 - getThreshold()) { // All Apps is down
                animateCaretToProgress(CaretDrawable.PROGRESS_CARET_POINTING_UP);
            }
        }
    }

    private void animateCaretToProgress(float progress) {
        // If the new progress is the same as the last progress we animated to, terminate early
        if (Float.compare(mLastCaretProgress, progress) == 0) {
            return;
        }

        if (mCaretAnimator.isRunning()) {
            mCaretAnimator.cancel(); // Stop the animator in its tracks
        }

        // Update the progress and start the animation
        mLastCaretProgress = progress;
        mCaretAnimator.setFloatValues(progress);
        mCaretAnimator.start();
    }

    private float getThreshold() {
        // In landscape, just return the landscape threshold.
        if (mLauncher.useVerticalBarLayout()) {
            return CARET_THRESHOLD_LAND;
        }

        // Before the threshold is crossed, it is reported as zero. This makes the caret immediately
        // responsive when a drag begins. After the threshold is crossed, we return the constant
        // value. This means the caret will start its state-based adjustment sooner. That is, we
        // won't have to wait until the panel is completely settled to begin animation.
        return mThresholdCrossed ? CARET_THRESHOLD : 0f;
    }

    public void onDragStart() {
        mThresholdCrossed = false;
    }
}
