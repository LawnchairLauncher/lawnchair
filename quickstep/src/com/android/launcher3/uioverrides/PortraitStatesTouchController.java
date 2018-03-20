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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;

import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.quickstep.TouchInteractionService;
import com.android.quickstep.util.SysuiEventLogger;

/**
 * Touch controller for handling various state transitions in portrait UI.
 */
public class PortraitStatesTouchController extends AbstractStateChangeTouchController {

    public PortraitStatesTouchController(Launcher l) {
        super(l, SwipeDetector.VERTICAL);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (mLauncher.isInState(ALL_APPS)) {
            // In all-apps only listen if the container cannot scroll itself
            if (!mLauncher.getAppsView().shouldContainerScroll(ev)) {
                return false;
            }
        } else {
            // For all other states, only listen if the event originated below the hotseat height
            DeviceProfile dp = mLauncher.getDeviceProfile();
            int hotseatHeight = dp.hotseatBarSizePx + dp.getInsets().bottom;
            if (ev.getY() < (mLauncher.getDragLayer().getHeight() - hotseatHeight)) {
                return false;
            }
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        return true;
    }

    @Override
    protected int getSwipeDirection(MotionEvent ev) {
        final int directionsToDetectScroll;
        if (mLauncher.isInState(ALL_APPS)) {
            directionsToDetectScroll = SwipeDetector.DIRECTION_NEGATIVE;
            mStartContainerType = ContainerType.ALLAPPS;
        } else if (mLauncher.isInState(NORMAL)) {
            directionsToDetectScroll = SwipeDetector.DIRECTION_POSITIVE;
            mStartContainerType = ContainerType.HOTSEAT;
        } else if (mLauncher.isInState(OVERVIEW)) {
            directionsToDetectScroll = SwipeDetector.DIRECTION_POSITIVE;
            mStartContainerType = ContainerType.TASKSWITCHER;
        } else {
            return 0;
        }
        mFromState = mLauncher.getStateManager().getState();
        mToState = getTargetState();
        if (mFromState == mToState) {
            return 0;
        }
        return directionsToDetectScroll;
    }

    protected LauncherState getTargetState() {
        if (mLauncher.isInState(ALL_APPS)) {
            // Should swipe down go to OVERVIEW instead?
            return TouchInteractionService.isConnected() ?
                    mLauncher.getStateManager().getLastState() : NORMAL;
        } else if (mLauncher.isInState(OVERVIEW)) {
            return ALL_APPS;
        } else {
            return TouchInteractionService.isConnected() ? OVERVIEW : ALL_APPS;
        }
    }

    @Override
    protected float initCurrentAnimation() {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);
        mCurrentAnimation = mLauncher.getStateManager()
                .createAnimationToNewWorkspace(mToState, maxAccuracy);

        float startVerticalShift = mFromState.getVerticalProgress(mLauncher) * range;
        float endVerticalShift = mToState.getVerticalProgress(mLauncher) * range;

        float totalShift = endVerticalShift - startVerticalShift;
        if (totalShift == 0) {
            totalShift = Math.signum(mFromState.ordinal - mToState.ordinal)
                    * OverviewState.getDefaultSwipeHeight(mLauncher);
        }
        return 1 / totalShift;
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState, int logAction) {
        super.onSwipeInteractionCompleted(targetState, logAction);
        if (mFromState == NORMAL && targetState == OVERVIEW) {
            SysuiEventLogger.writeDummyRecentsTransition(0);
        }
    }
}
