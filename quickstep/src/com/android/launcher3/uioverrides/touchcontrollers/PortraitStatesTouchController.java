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

import android.view.MotionEvent;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.AllAppsSwipeController;
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

    @Override
    protected StateAnimationConfig getConfigForStates(
            LauncherState fromState, LauncherState toState) {
        final StateAnimationConfig config = new StateAnimationConfig();
        config.userControlled = true;
        if (fromState == NORMAL && toState == ALL_APPS) {
            AllAppsSwipeController.applyNormalToAllAppsAnimConfig(mLauncher, config);
        } else if (fromState == ALL_APPS && toState == NORMAL) {
            AllAppsSwipeController.applyAllAppsToNormalConfig(mLauncher, config);
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
                InteractionJankMonitorWrapper.begin(
                        mLauncher.getRootView(),
                        InteractionJankMonitorWrapper.CUJ_CLOSE_ALL_APPS_SWIPE);
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                InteractionJankMonitorWrapper.cancel(
                        InteractionJankMonitorWrapper.CUJ_OPEN_ALL_APPS);
                InteractionJankMonitorWrapper.cancel(
                        InteractionJankMonitorWrapper.CUJ_CLOSE_ALL_APPS_SWIPE);
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
        if (newToState != NORMAL) {
            InteractionJankMonitorWrapper.cancel(
                    InteractionJankMonitorWrapper.CUJ_CLOSE_ALL_APPS_SWIPE);
        }
    }

    @Override
    protected void onReachedFinalState(LauncherState toState) {
        super.onReachedFinalState(toState);
        if (toState == ALL_APPS) {
            InteractionJankMonitorWrapper.end(InteractionJankMonitorWrapper.CUJ_OPEN_ALL_APPS);
        } else if (toState == NORMAL) {
            InteractionJankMonitorWrapper.end(
                    InteractionJankMonitorWrapper.CUJ_CLOSE_ALL_APPS_SWIPE);
        }
    }

    @Override
    protected void clearState() {
        super.clearState();
        InteractionJankMonitorWrapper.cancel(InteractionJankMonitorWrapper.CUJ_OPEN_ALL_APPS);
        InteractionJankMonitorWrapper.cancel(
                InteractionJankMonitorWrapper.CUJ_CLOSE_ALL_APPS_SWIPE);
    }
}
