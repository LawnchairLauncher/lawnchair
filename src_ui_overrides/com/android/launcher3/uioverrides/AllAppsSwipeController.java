/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.view.MotionEvent;

import com.android.launcher3.Launcher;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.VerticalSwipeController;

/**
 * Extension of {@link VerticalSwipeController} to switch between NORMAL and ALL_APPS state.
 */
public class AllAppsSwipeController extends VerticalSwipeController {

    private int mStartContainerType;

    public AllAppsSwipeController(Launcher l) {
        super(l, NORMAL);
    }

    @Override
    protected boolean shouldInterceptTouch(MotionEvent ev) {
        if (!mLauncher.isInState(NORMAL) && !mLauncher.isInState(ALL_APPS)) {
            // Don't listen for the swipe gesture if we are already in some other state.
            return false;
        }

        if (mLauncher.isInState(ALL_APPS) && !mLauncher.getAppsView().shouldContainerScroll(ev)) {
            return false;
        }
        return true;
    }

    @Override
    protected int getSwipeDirection(MotionEvent ev) {
        if (mLauncher.isInState(ALL_APPS)) {
            mStartContainerType = ContainerType.ALLAPPS;
            return SwipeDetector.DIRECTION_NEGATIVE;
        } else {
            mStartContainerType = mLauncher.getDragLayer().isEventOverHotseat(ev) ?
                    ContainerType.HOTSEAT : ContainerType.WORKSPACE;
            return SwipeDetector.DIRECTION_POSITIVE;
        }
    }

    @Override
    protected void onTransitionComplete(boolean wasFling, boolean stateChanged) {
        if (stateChanged) {
            // Transition complete. log the action
            mLauncher.getUserEventDispatcher().logActionOnContainer(
                    wasFling ? Touch.FLING : Touch.SWIPE,
                    mLauncher.isInState(ALL_APPS) ? Direction.UP : Direction.DOWN,
                    mStartContainerType,
                    mLauncher.getWorkspace().getCurrentPage());
        }
    }
}
