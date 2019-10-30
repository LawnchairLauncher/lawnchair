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

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;

import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.userevent.nano.LauncherLogProto;

/**
 * Touch controller for landscape mode.
 */
public final class LandscapeStatesTouchController extends PortraitStatesTouchController {

    public LandscapeStatesTouchController(Launcher l) {
        super(l, true /* allowDragToOverview */);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        if (mLauncher.isInState(ALL_APPS)) {
            // In all-apps only listen if the container cannot scroll itself
            return mLauncher.getAppsView().shouldContainerScroll(ev);
        } else if (mLauncher.isInState(NORMAL)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (fromState == ALL_APPS && !isDragTowardPositive) {
            return NORMAL;
        } else if (isDragTowardPositive) {
            return ALL_APPS;
        }
        return fromState;
    }

    @Override
    protected int getLogContainerTypeForNormalState(MotionEvent ev) {
        return LauncherLogProto.ContainerType.WORKSPACE;
    }
}
