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

package com.android.launcher3.uioverrides.touchcontrollers;

import static com.android.launcher3.LauncherAppTransitionManagerImpl.INDEX_PAUSE_TO_OVERVIEW_ANIM;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_PEEK;
import static com.android.launcher3.LauncherStateManager.ATOMIC_OVERVIEW_PEEK_COMPONENT;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_ALL_APPS_HEADER_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_WORKSPACE_SCALE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_WORKSPACE_TRANSLATE;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL_3;
import static com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppTransitionManagerImpl;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.util.VibratorWrapper;
import com.android.quickstep.OverviewInteractionState;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.quickstep.views.RecentsView;

/**
 * Touch controller which handles swipe and hold to go to Overview
 */
public class FlingAndHoldTouchController extends PortraitStatesTouchController {

    private static final long PEEK_IN_ANIM_DURATION = 240;
    private static final long PEEK_OUT_ANIM_DURATION = 100;
    private static final float MAX_DISPLACEMENT_PERCENT = 0.75f;

    private final MotionPauseDetector mMotionPauseDetector;
    private final float mMotionPauseMinDisplacement;
    private final float mMotionPauseMaxDisplacement;

    private AnimatorSet mPeekAnim;

    public FlingAndHoldTouchController(Launcher l) {
        super(l, false /* allowDragToOverview */);
        mMotionPauseDetector = new MotionPauseDetector(l);
        mMotionPauseMinDisplacement = ViewConfiguration.get(l).getScaledTouchSlop();
        mMotionPauseMaxDisplacement = getShiftRange() * MAX_DISPLACEMENT_PERCENT;
    }

    @Override
    protected long getAtomicDuration() {
        return LauncherAppTransitionManagerImpl.ATOMIC_DURATION_FROM_PAUSED_TO_OVERVIEW;
    }

    @Override
    public void onDragStart(boolean start) {
        mMotionPauseDetector.clear();

        super.onDragStart(start);

        if (handlingOverviewAnim()) {
            mMotionPauseDetector.setOnMotionPauseListener(isPaused -> {
                RecentsView recentsView = mLauncher.getOverviewPanel();
                recentsView.setOverviewStateEnabled(isPaused);
                if (mPeekAnim != null) {
                    mPeekAnim.cancel();
                }
                LauncherState fromState = isPaused ? NORMAL : OVERVIEW_PEEK;
                LauncherState toState = isPaused ? OVERVIEW_PEEK : NORMAL;
                long peekDuration = isPaused ? PEEK_IN_ANIM_DURATION : PEEK_OUT_ANIM_DURATION;
                mPeekAnim = mLauncher.getStateManager().createAtomicAnimation(fromState, toState,
                        new AnimatorSetBuilder(), ATOMIC_OVERVIEW_PEEK_COMPONENT, peekDuration);
                mPeekAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mPeekAnim = null;
                    }
                });
                mPeekAnim.start();
                VibratorWrapper.INSTANCE.get(mLauncher).vibrate(OVERVIEW_HAPTIC);

                mLauncher.getDragLayer().getScrim().animateToSysuiMultiplier(isPaused ? 0 : 1,
                        peekDuration, 0);
            });
        }
    }

    /**
     * @return Whether we are handling the overview animation, rather than
     * having it as part of the existing animation to the target state.
     */
    private boolean handlingOverviewAnim() {
        int stateFlags = OverviewInteractionState.INSTANCE.get(mLauncher).getSystemUiStateFlags();
        return mStartState == NORMAL && (stateFlags & SYSUI_STATE_OVERVIEW_DISABLED) == 0;
    }

    @Override
    protected AnimatorSetBuilder getAnimatorSetBuilderForStates(LauncherState fromState,
            LauncherState toState) {
        if (fromState == NORMAL && toState == ALL_APPS) {
            AnimatorSetBuilder builder = new AnimatorSetBuilder();
            // Fade in prediction icons quickly, then rest of all apps after reaching overview.
            float progressToReachOverview = NORMAL.getVerticalProgress(mLauncher)
                    - OVERVIEW.getVerticalProgress(mLauncher);
            builder.setInterpolator(ANIM_ALL_APPS_HEADER_FADE, Interpolators.clampToProgress(
                    ACCEL,
                    0,
                    ALL_APPS_CONTENT_FADE_THRESHOLD));
            builder.setInterpolator(ANIM_ALL_APPS_FADE, Interpolators.clampToProgress(
                    ACCEL,
                    progressToReachOverview,
                    progressToReachOverview + ALL_APPS_CONTENT_FADE_THRESHOLD));

            // Get workspace out of the way quickly, to prepare for potential pause.
            builder.setInterpolator(ANIM_WORKSPACE_SCALE, DEACCEL_3);
            builder.setInterpolator(ANIM_WORKSPACE_TRANSLATE, DEACCEL_3);
            builder.setInterpolator(ANIM_WORKSPACE_FADE, DEACCEL_3);
            return builder;
        } else if (fromState == ALL_APPS && toState == NORMAL) {
            AnimatorSetBuilder builder = new AnimatorSetBuilder();
            // Keep all apps/predictions opaque until the very end of the transition.
            float progressToReachOverview = OVERVIEW.getVerticalProgress(mLauncher);
            builder.setInterpolator(ANIM_ALL_APPS_FADE, Interpolators.clampToProgress(
                    DEACCEL,
                    progressToReachOverview - ALL_APPS_CONTENT_FADE_THRESHOLD,
                    progressToReachOverview));
            builder.setInterpolator(ANIM_ALL_APPS_HEADER_FADE, Interpolators.clampToProgress(
                    DEACCEL,
                    1 - ALL_APPS_CONTENT_FADE_THRESHOLD,
                    1));
            return builder;
        }
        return super.getAnimatorSetBuilderForStates(fromState, toState);
    }

    @Override
    public boolean onDrag(float displacement, MotionEvent event) {
        float upDisplacement = -displacement;
        mMotionPauseDetector.setDisallowPause(upDisplacement < mMotionPauseMinDisplacement
                || upDisplacement > mMotionPauseMaxDisplacement);
        mMotionPauseDetector.addPosition(displacement, event.getEventTime());
        return super.onDrag(displacement, event);
    }

    @Override
    public void onDragEnd(float velocity) {
        if (mMotionPauseDetector.isPaused() && handlingOverviewAnim()) {
            if (mPeekAnim != null) {
                mPeekAnim.cancel();
            }

            Animator overviewAnim = mLauncher.getAppTransitionManager().createStateElementAnimation(
                    INDEX_PAUSE_TO_OVERVIEW_ANIM);
            overviewAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onSwipeInteractionCompleted(OVERVIEW, Touch.SWIPE);
                }
            });
            overviewAnim.start();
        } else {
            super.onDragEnd(velocity);
        }

        View searchView = mLauncher.getAppsView().getSearchView();
        if (searchView instanceof FeedbackHandler) {
            ((FeedbackHandler) searchView).resetFeedback();
        }
        mMotionPauseDetector.clear();
    }

    @Override
    protected void goToTargetState(LauncherState targetState, int logAction) {
        if (mPeekAnim != null && mPeekAnim.isStarted()) {
            // Don't jump to the target state until overview is no longer peeking.
            mPeekAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    FlingAndHoldTouchController.super.goToTargetState(targetState, logAction);
                }
            });
        } else {
            super.goToTargetState(targetState, logAction);
        }
    }

    @Override
    protected void updateAnimatorBuilderOnReinit(AnimatorSetBuilder builder) {
        if (handlingOverviewAnim()) {
            // We don't want the state transition to all apps to animate overview,
            // as that will cause a jump after our atomic animation.
            builder.addFlag(AnimatorSetBuilder.FLAG_DONT_ANIMATE_OVERVIEW);
        }
    }

    /**
     * Interface for views with feedback animation requiring reset
     */
    public interface FeedbackHandler {

        /**
         * reset searchWidget feedback
         */
        void resetFeedback();
    }

}
