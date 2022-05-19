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
import static com.android.launcher3.anim.Interpolators.FINAL_FRAME;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_HOTSEAT_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_HOTSEAT_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_HOTSEAT_TRANSLATE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_SCRIM_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_SCALE;

import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.uioverrides.states.OverviewState;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

/**
 * Touch controller for handling various state transitions in portrait UI.
 */
public class PortraitStatesTouchController extends AbstractStateChangeTouchController {

    private static final String TAG = "PortraitStatesTouchCtrl";

    /**
     * The progress at which all apps content will be fully visible.
     */
    public static final float ALL_APPS_CONTENT_FADE_MAX_CLAMPING_THRESHOLD = 0.8f;

    /**
     * Minimum clamping progress for fading in all apps content
     */
    public static final float ALL_APPS_CONTENT_FADE_MIN_CLAMPING_THRESHOLD = 0.5f;

    /**
     * Minimum clamping progress for fading in all apps scrim
     */
    public static final float ALL_APPS_SCRIM_VISIBLE_THRESHOLD = .1f;

    /**
     * Maximum clamping progress for opaque all apps scrim
     */
    public static final float ALL_APPS_SCRIM_OPAQUE_THRESHOLD = .5f;

    // Custom timing for NORMAL -> ALL_APPS on phones only.
    private static final float ALL_APPS_STATE_TRANSITION = 0.4f;
    private static final float ALL_APPS_FULL_DEPTH_PROGRESS = 0.5f;

    // Custom interpolators for NORMAL -> ALL_APPS on phones only.
    private static final Interpolator LINEAR_EARLY =
            Interpolators.clampToProgress(LINEAR, 0f, ALL_APPS_STATE_TRANSITION);
    private static final Interpolator STEP_TRANSITION =
            Interpolators.clampToProgress(FINAL_FRAME, 0f, ALL_APPS_STATE_TRANSITION);
    // The blur to and from All Apps is set to be complete when the interpolator is at 0.5.
    public static final Interpolator BLUR =
            Interpolators.clampToProgress(
                    Interpolators.mapToProgress(LINEAR, 0f, ALL_APPS_FULL_DEPTH_PROGRESS),
                    0f, ALL_APPS_STATE_TRANSITION);
    public static final Interpolator WORKSPACE_FADE = STEP_TRANSITION;
    public static final Interpolator WORKSPACE_SCALE = LINEAR_EARLY;
    public static final Interpolator HOTSEAT_FADE = STEP_TRANSITION;
    public static final Interpolator HOTSEAT_SCALE = LINEAR_EARLY;
    public static final Interpolator HOTSEAT_TRANSLATE = STEP_TRANSITION;
    public static final Interpolator SCRIM_FADE = LINEAR_EARLY;
    public static final Interpolator ALL_APPS_FADE =
            Interpolators.clampToProgress(LINEAR, ALL_APPS_STATE_TRANSITION, 1f);
    public static final Interpolator ALL_APPS_VERTICAL_PROGRESS =
            Interpolators.clampToProgress(
                    Interpolators.mapToProgress(LINEAR, ALL_APPS_STATE_TRANSITION, 1f),
                    ALL_APPS_STATE_TRANSITION, 1f);

    private final PortraitOverviewStateTouchHelper mOverviewPortraitStateTouchHelper;

    public PortraitStatesTouchController(Launcher l) {
        super(l, SingleAxisSwipeDetector.VERTICAL);
        mOverviewPortraitStateTouchHelper = new PortraitOverviewStateTouchHelper(l);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        // If we are swiping to all apps instead of overview, allow it from anywhere.
        boolean interceptAnywhere = mLauncher.isInState(NORMAL);
        if (mCurrentAnimation != null) {
            AllAppsTransitionController allAppsController = mLauncher.getAllAppsController();
            if (ev.getY() >= allAppsController.getShiftRange() * allAppsController.getProgress()
                    || interceptAnywhere) {
                // If we are already animating from a previous state, we can intercept as long as
                // the touch is below the current all apps progress (to allow for double swipe).
                return true;
            }
            // Otherwise, don't intercept so they can scroll recents, dismiss a task, etc.
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
        if (fromState == ALL_APPS && !isDragTowardPositive) {
            return NORMAL;
        } else if (fromState == OVERVIEW) {
            return isDragTowardPositive ? OVERVIEW : NORMAL;
        } else if (fromState == NORMAL && isDragTowardPositive) {
            return ALL_APPS;
        }
        return fromState;
    }

    private StateAnimationConfig getNormalToAllAppsAnimation() {
        StateAnimationConfig builder = new StateAnimationConfig();
        if (mLauncher.getDeviceProfile().isTablet) {
            builder.setInterpolator(ANIM_ALL_APPS_FADE, INSTANT);
            builder.setInterpolator(ANIM_SCRIM_FADE,
                    Interpolators.clampToProgress(LINEAR,
                            ALL_APPS_SCRIM_VISIBLE_THRESHOLD,
                            ALL_APPS_SCRIM_OPAQUE_THRESHOLD));
        } else {
            // TODO(b/231682175): centralize this setup in AllAppsSwipeController.
            builder.setInterpolator(ANIM_DEPTH, BLUR);
            builder.setInterpolator(ANIM_WORKSPACE_FADE, WORKSPACE_FADE);
            builder.setInterpolator(ANIM_WORKSPACE_SCALE, WORKSPACE_SCALE);
            builder.setInterpolator(ANIM_HOTSEAT_FADE, HOTSEAT_FADE);
            builder.setInterpolator(ANIM_HOTSEAT_SCALE, HOTSEAT_SCALE);
            builder.setInterpolator(ANIM_HOTSEAT_TRANSLATE, HOTSEAT_TRANSLATE);
            builder.setInterpolator(ANIM_SCRIM_FADE, SCRIM_FADE);
            builder.setInterpolator(ANIM_ALL_APPS_FADE, ALL_APPS_FADE);
            builder.setInterpolator(ANIM_VERTICAL_PROGRESS, ALL_APPS_VERTICAL_PROGRESS);
        }
        return builder;
    }

    private StateAnimationConfig getAllAppsToNormalAnimation() {
        StateAnimationConfig builder = new StateAnimationConfig();
        if (mLauncher.getDeviceProfile().isTablet) {
            builder.setInterpolator(ANIM_ALL_APPS_FADE, FINAL_FRAME);
            builder.setInterpolator(ANIM_SCRIM_FADE,
                    Interpolators.clampToProgress(LINEAR,
                            1 - ALL_APPS_SCRIM_OPAQUE_THRESHOLD,
                            1 - ALL_APPS_SCRIM_VISIBLE_THRESHOLD));
        } else {
            // These interpolators are the reverse of the ones used above, so swiping out of All
            // Apps feels the same as swiping into it.
            // TODO(b/231682175): centralize this setup in AllAppsSwipeController.
            builder.setInterpolator(ANIM_DEPTH, Interpolators.reverse(BLUR));
            builder.setInterpolator(ANIM_WORKSPACE_FADE, Interpolators.reverse(WORKSPACE_FADE));
            builder.setInterpolator(ANIM_WORKSPACE_SCALE, Interpolators.reverse(WORKSPACE_SCALE));
            builder.setInterpolator(ANIM_HOTSEAT_FADE, Interpolators.reverse(HOTSEAT_FADE));
            builder.setInterpolator(ANIM_HOTSEAT_SCALE, Interpolators.reverse(HOTSEAT_SCALE));
            builder.setInterpolator(ANIM_HOTSEAT_TRANSLATE,
                    Interpolators.reverse(HOTSEAT_TRANSLATE));
            builder.setInterpolator(ANIM_SCRIM_FADE, Interpolators.reverse(SCRIM_FADE));
            builder.setInterpolator(ANIM_ALL_APPS_FADE, Interpolators.reverse(ALL_APPS_FADE));
            builder.setInterpolator(ANIM_VERTICAL_PROGRESS,
                    Interpolators.reverse(ALL_APPS_VERTICAL_PROGRESS));
        }
        return builder;
    }

    @Override
    protected StateAnimationConfig getConfigForStates(
            LauncherState fromState, LauncherState toState) {
        final StateAnimationConfig config;
        if (fromState == NORMAL && toState == ALL_APPS) {
            config = getNormalToAllAppsAnimation();
        } else if (fromState == ALL_APPS && toState == NORMAL) {
            config = getAllAppsToNormalAnimation();
        } else {
            config = new StateAnimationConfig();
        }
        return config;
    }

    @Override
    protected float initCurrentAnimation() {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);

        float startVerticalShift = mFromState.getVerticalProgress(mLauncher) * range;
        float endVerticalShift = mToState.getVerticalProgress(mLauncher) * range;

        float totalShift = endVerticalShift - startVerticalShift;

        final StateAnimationConfig config = totalShift == 0 ? new StateAnimationConfig()
                : getConfigForStates(mFromState, mToState);
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
     * @param ev       the event to check
     * @return true if the event is over the hotseat
     */
    static boolean isTouchOverHotseat(Launcher launcher, MotionEvent ev) {
        DeviceProfile dp = launcher.getDeviceProfile();
        int hotseatHeight = dp.hotseatBarSizePx + dp.getInsets().bottom;
        return (ev.getY() >= (launcher.getDragLayer().getHeight() - hotseatHeight));
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                InteractionJankMonitorWrapper.begin(
                        mLauncher.getRootView(), InteractionJankMonitorWrapper.CUJ_OPEN_ALL_APPS);
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                InteractionJankMonitorWrapper.cancel(
                        InteractionJankMonitorWrapper.CUJ_OPEN_ALL_APPS);
                break;
        }
        return super.onControllerInterceptTouchEvent(ev);

    }

    @Override
    protected void onReinitToState(LauncherState newToState) {
        super.onReinitToState(newToState);
        if (newToState != ALL_APPS) {
            InteractionJankMonitorWrapper.cancel(InteractionJankMonitorWrapper.CUJ_OPEN_ALL_APPS);
        }
    }

    @Override
    protected void onReachedFinalState(LauncherState toState) {
        super.onReachedFinalState(toState);
        if (toState == ALL_APPS) {
            InteractionJankMonitorWrapper.end(InteractionJankMonitorWrapper.CUJ_OPEN_ALL_APPS);
        }
    }

    @Override
    protected void clearState() {
        super.clearState();
        InteractionJankMonitorWrapper.cancel(InteractionJankMonitorWrapper.CUJ_OPEN_ALL_APPS);
    }
}
