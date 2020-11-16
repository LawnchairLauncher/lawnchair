/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.AbstractFloatingView.TYPE_ACCESSIBLE;
import static com.android.launcher3.AbstractFloatingView.TYPE_ALL_APPS_EDU;
import static com.android.launcher3.AbstractFloatingView.getTopOpenViewWithType;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.UNSTABLE_SPRINGS;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.states.StateAnimationConfig.AnimationFlags;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.uioverrides.states.OverviewState;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TouchInteractionService;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;

/**
 * Touch controller for handling various state transitions in portrait UI.
 */
public class PortraitStatesTouchController extends AbstractStateChangeTouchController {

    private static final String TAG = "PortraitStatesTouchCtrl";

    /**
     * The progress at which all apps content will be fully visible when swiping up from overview.
     */
    protected static final float ALL_APPS_CONTENT_FADE_THRESHOLD = 0.08f;

    /**
     * The progress at which recents will begin fading out when swiping up from overview.
     */
    private static final float RECENTS_FADE_THRESHOLD = 0.88f;

    private final PortraitOverviewStateTouchHelper mOverviewPortraitStateTouchHelper;

    private final InterpolatorWrapper mAllAppsInterpolatorWrapper = new InterpolatorWrapper();

    private final boolean mAllowDragToOverview;

    // If true, we will finish the current animation instantly on second touch.
    private boolean mFinishFastOnSecondTouch;

    public PortraitStatesTouchController(Launcher l, boolean allowDragToOverview) {
        super(l, SingleAxisSwipeDetector.VERTICAL);
        mOverviewPortraitStateTouchHelper = new PortraitOverviewStateTouchHelper(l);
        mAllowDragToOverview = allowDragToOverview;
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        // If we are swiping to all apps instead of overview, allow it from anywhere.
        boolean interceptAnywhere = mLauncher.isInState(NORMAL) && !mAllowDragToOverview;
        if (mCurrentAnimation != null) {
            if (mFinishFastOnSecondTouch) {
                mCurrentAnimation.getAnimationPlayer().end();
            }

            AllAppsTransitionController allAppsController = mLauncher.getAllAppsController();
            if (ev.getY() >= allAppsController.getShiftRange() * allAppsController.getProgress()
                    || interceptAnywhere) {
                // If we are already animating from a previous state, we can intercept as long as
                // the touch is below the current all apps progress (to allow for double swipe).
                return true;
            }
            // Otherwise, make sure everything is settled and don't intercept so they can scroll
            // recents, dismiss a task, etc.
            if (mAtomicAnim != null) {
                mAtomicAnim.end();
            }
            return false;
        }
        if (mLauncher.isInState(ALL_APPS)) {
            // In all-apps only listen if the container cannot scroll itself
            if (!mLauncher.getAppsView().shouldContainerScroll(ev)) {
                return false;
            }
        } else if (mLauncher.isInState(OVERVIEW)) {
            if (!mOverviewPortraitStateTouchHelper.canInterceptTouch(ev)) {
                return false;
            }
        } else {
            // For non-normal states, only listen if the event originated below the hotseat height
            if (!interceptAnywhere && !isTouchOverHotseat(mLauncher, ev)) {
                return false;
            }
        }
        if (getTopOpenViewWithType(mLauncher, TYPE_ACCESSIBLE | TYPE_ALL_APPS_EDU) != null) {
            return false;
        }
        return true;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.OVERIEW_NOT_ALLAPPS, "PortraitStatesTouchController.getTargetState");
        }
        if (fromState == ALL_APPS && !isDragTowardPositive) {
            // Should swipe down go to OVERVIEW instead?
            if (TestProtocol.sDebugTracing) {
                Log.d(TestProtocol.OVERIEW_NOT_ALLAPPS,
                        "PortraitStatesTouchController.getTargetState 1");
            }
            if (removeShelfFromOverview(mLauncher)) {
                // Don't allow swiping down to overview.
                return NORMAL;
            }
            return TouchInteractionService.isConnected() ?
                    mLauncher.getStateManager().getLastState() : NORMAL;
        } else if (fromState == OVERVIEW) {
            if (TestProtocol.sDebugTracing) {
                Log.d(TestProtocol.OVERIEW_NOT_ALLAPPS,
                        "PortraitStatesTouchController.getTargetState 2");
            }
            LauncherState positiveDragTarget = ALL_APPS;
            if (removeShelfFromOverview(mLauncher)) {
                // Don't allow swiping up to all apps.
                positiveDragTarget = OVERVIEW;
            }
            return isDragTowardPositive ? positiveDragTarget : NORMAL;
        } else if (fromState == NORMAL && isDragTowardPositive) {
            if (TestProtocol.sDebugTracing) {
                Log.d(TestProtocol.OVERIEW_NOT_ALLAPPS,
                        "PortraitStatesTouchController.getTargetState 3");
            }
            int stateFlags = SystemUiProxy.INSTANCE.get(mLauncher).getLastSystemUiStateFlags();
            return mAllowDragToOverview && TouchInteractionService.isConnected()
                    && (stateFlags & SYSUI_STATE_OVERVIEW_DISABLED) == 0
                    ? OVERVIEW : ALL_APPS;
        }
        return fromState;
    }

    private StateAnimationConfig getNormalToOverviewAnimation() {
        mAllAppsInterpolatorWrapper.baseInterpolator = LINEAR;

        StateAnimationConfig builder = new StateAnimationConfig();
        builder.setInterpolator(ANIM_VERTICAL_PROGRESS, mAllAppsInterpolatorWrapper);
        return builder;
    }

    private static StateAnimationConfig getOverviewToAllAppsAnimation() {
        StateAnimationConfig builder = new StateAnimationConfig();
        builder.setInterpolator(ANIM_ALL_APPS_FADE, Interpolators.clampToProgress(ACCEL,
                0, ALL_APPS_CONTENT_FADE_THRESHOLD));
        builder.setInterpolator(ANIM_OVERVIEW_FADE, Interpolators.clampToProgress(DEACCEL,
                RECENTS_FADE_THRESHOLD, 1));
        return builder;
    }

    private StateAnimationConfig getAllAppsToOverviewAnimation() {
        StateAnimationConfig builder = new StateAnimationConfig();
        builder.setInterpolator(ANIM_ALL_APPS_FADE, Interpolators.clampToProgress(DEACCEL,
                1 - ALL_APPS_CONTENT_FADE_THRESHOLD, 1));
        builder.setInterpolator(ANIM_OVERVIEW_FADE, Interpolators.clampToProgress(ACCEL,
                0f, 1 - RECENTS_FADE_THRESHOLD));
        return builder;
    }

    private StateAnimationConfig getNormalToAllAppsAnimation() {
        StateAnimationConfig builder = new StateAnimationConfig();
        builder.setInterpolator(ANIM_ALL_APPS_FADE, Interpolators.clampToProgress(ACCEL,
                0, ALL_APPS_CONTENT_FADE_THRESHOLD));
        return builder;
    }

    private StateAnimationConfig getAllAppsToNormalAnimation() {
        StateAnimationConfig builder = new StateAnimationConfig();
        builder.setInterpolator(ANIM_ALL_APPS_FADE, Interpolators.clampToProgress(DEACCEL,
                1 - ALL_APPS_CONTENT_FADE_THRESHOLD, 1));
        return builder;
    }

    @Override
    protected StateAnimationConfig getConfigForStates(
            LauncherState fromState, LauncherState toState) {
        final StateAnimationConfig config;
        if (fromState == NORMAL && toState == OVERVIEW) {
            config = getNormalToOverviewAnimation();
        } else if (fromState == OVERVIEW && toState == ALL_APPS) {
            config = getOverviewToAllAppsAnimation();
        } else if (fromState == ALL_APPS && toState == OVERVIEW) {
            config = getAllAppsToOverviewAnimation();
        } else if (fromState == NORMAL && toState == ALL_APPS) {
            config = getNormalToAllAppsAnimation();
        } else if (fromState == ALL_APPS && toState == NORMAL) {
            config = getAllAppsToNormalAnimation();
        }  else {
            config = new StateAnimationConfig();
        }
        return config;
    }

    @Override
    protected float initCurrentAnimation(@AnimationFlags int animFlags) {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);

        float startVerticalShift = mFromState.getVerticalProgress(mLauncher) * range;
        float endVerticalShift = mToState.getVerticalProgress(mLauncher) * range;

        float totalShift = endVerticalShift - startVerticalShift;

        final StateAnimationConfig config = totalShift == 0 ? new StateAnimationConfig()
                : getConfigForStates(mFromState, mToState);
        config.animFlags = animFlags;
        config.duration = maxAccuracy;

        if (mCurrentAnimation != null) {
            mCurrentAnimation.getTarget().removeListener(mClearStateOnCancelListener);
            mCurrentAnimation.dispatchOnCancel();
        }

        mGoingBetweenStates = true;
        if (mFromState == OVERVIEW && mToState == NORMAL
                && mOverviewPortraitStateTouchHelper.shouldSwipeDownReturnToApp()) {
            // Reset the state manager, when changing the interaction mode
            mLauncher.getStateManager().goToState(OVERVIEW, false /* animate */);
            mGoingBetweenStates = false;
            mCurrentAnimation = mOverviewPortraitStateTouchHelper
                    .createSwipeDownToTaskAppAnimation(maxAccuracy, Interpolators.LINEAR)
                    .createPlaybackController();
            mLauncher.getStateManager().setCurrentUserControlledAnimation(mCurrentAnimation);
            RecentsView recentsView = mLauncher.getOverviewPanel();
            totalShift = LayoutUtils.getShelfTrackingDistance(mLauncher,
                    mLauncher.getDeviceProfile(), recentsView.getPagedOrientationHandler());
        } else {
            mCurrentAnimation = mLauncher.getStateManager()
                    .createAnimationToNewWorkspace(mToState, config);
        }
        mCurrentAnimation.getTarget().addListener(mClearStateOnCancelListener);

        if (totalShift == 0) {
            totalShift = Math.signum(mFromState.ordinal - mToState.ordinal)
                    * OverviewState.getDefaultSwipeHeight(mLauncher);
        }
        return 1 / totalShift;
    }

    @Override
    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
            LauncherState targetState, float velocity, boolean isFling) {
        super.updateSwipeCompleteAnimation(animator, expectedDuration, targetState,
                velocity, isFling);
        handleFirstSwipeToOverview(animator, expectedDuration, targetState, velocity, isFling);
    }

    private void handleFirstSwipeToOverview(final ValueAnimator animator,
            final long expectedDuration, final LauncherState targetState, final float velocity,
            final boolean isFling) {
        if (UNSTABLE_SPRINGS.get() && mFromState == OVERVIEW && mToState == ALL_APPS
                && targetState == OVERVIEW) {
            mFinishFastOnSecondTouch = true;
        } else  if (mFromState == NORMAL && mToState == OVERVIEW && targetState == OVERVIEW) {
            mFinishFastOnSecondTouch = true;
            if (isFling && expectedDuration != 0) {
                // Update all apps interpolator to add a bit of overshoot starting from currFraction
                final float currFraction = mCurrentAnimation.getProgressFraction();
                mAllAppsInterpolatorWrapper.baseInterpolator = Interpolators.clampToProgress(
                        Interpolators.overshootInterpolatorForVelocity(velocity), currFraction, 1);
                animator.setDuration(Math.min(expectedDuration, ATOMIC_DURATION))
                        .setInterpolator(LINEAR);
            }
        } else {
            mFinishFastOnSecondTouch = false;
        }
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState) {
        super.onSwipeInteractionCompleted(targetState);
        if (mStartState == NORMAL && targetState == OVERVIEW) {
            SystemUiProxy.INSTANCE.get(mLauncher).onOverviewShown(true, TAG);
        }
    }

    /**
     * Whether the motion event is over the hotseat.
     *
     * @param launcher the launcher activity
     * @param ev the event to check
     * @return true if the event is over the hotseat
     */
    static boolean isTouchOverHotseat(Launcher launcher, MotionEvent ev) {
        return (ev.getY() >= getHotseatTop(launcher));
    }

    public static int getHotseatTop(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        int hotseatHeight = dp.hotseatBarSizePx + dp.getInsets().bottom;
        return launcher.getDragLayer().getHeight() - hotseatHeight;
    }

    private static class InterpolatorWrapper implements Interpolator {

        public TimeInterpolator baseInterpolator = LINEAR;

        @Override
        public float getInterpolation(float v) {
            return baseInterpolator.getInterpolation(v);
        }
    }
}
