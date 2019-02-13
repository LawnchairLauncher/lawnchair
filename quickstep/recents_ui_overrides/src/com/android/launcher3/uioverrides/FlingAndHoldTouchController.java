/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherStateManager.NON_ATOMIC_COMPONENT;

import android.animation.ValueAnimator;

import com.android.launcher3.Launcher;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.quickstep.views.RecentsView;

/**
 * Touch controller which handles swipe and hold to go to Overview
 */
public class FlingAndHoldTouchController extends PortraitStatesTouchController {

    private final MotionPauseDetector mMotionPauseDetector;

    public FlingAndHoldTouchController(Launcher l) {
        super(l, false /* allowDragToOverview */);
        mMotionPauseDetector = new MotionPauseDetector(l);
    }

    @Override
    public void onDragStart(boolean start) {
        mMotionPauseDetector.clear();

        super.onDragStart(start);

        if (mStartState == NORMAL) {
            mMotionPauseDetector.setOnMotionPauseListener(isPaused -> {
                RecentsView recentsView = mLauncher.getOverviewPanel();
                recentsView.setOverviewStateEnabled(isPaused);
                maybeUpdateAtomicAnim(NORMAL, OVERVIEW, isPaused ? 1 : 0);
            });
        }
    }

    @Override
    public boolean onDrag(float displacement) {
        mMotionPauseDetector.addPosition(displacement, 0);
        return super.onDrag(displacement);
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        if (mMotionPauseDetector.isPaused() && mStartState == NORMAL) {
            float range = getShiftRange();
            long maxAccuracy = (long) (2 * range);

            // Let the state manager know that the animation didn't go to the target state,
            // but don't cancel ourselves (we already clean up when the animation completes).
            Runnable onCancel = mCurrentAnimation.getOnCancelRunnable();
            mCurrentAnimation.setOnCancelRunnable(null);
            mCurrentAnimation.dispatchOnCancel();
            mCurrentAnimation = mLauncher.getStateManager()
                    .createAnimationToNewWorkspace(OVERVIEW, new AnimatorSetBuilder(), maxAccuracy,
                            onCancel, NON_ATOMIC_COMPONENT);

            final int logAction = fling ? Touch.FLING : Touch.SWIPE;
            mCurrentAnimation.setEndAction(() -> onSwipeInteractionCompleted(OVERVIEW, logAction));


            ValueAnimator anim = mCurrentAnimation.getAnimationPlayer();
            maybeUpdateAtomicAnim(NORMAL, OVERVIEW, 1f);
            mCurrentAnimation.dispatchOnStartWithVelocity(1, velocity);

            // TODO: Find a better duration
            anim.setDuration(100);
            anim.start();
            settleAtomicAnimation(1f, anim.getDuration());
        } else {
            super.onDragEnd(velocity, fling);
        }
        mMotionPauseDetector.clear();
    }
}
