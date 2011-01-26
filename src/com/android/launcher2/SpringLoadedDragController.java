/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

public class SpringLoadedDragController implements OnAlarmListener {
    // how long the user must hover over a mini-screen before it unshrinks
    final long ENTER_SPRING_LOAD_HOVER_TIME = 1000;
    final long EXIT_SPRING_LOAD_HOVER_TIME = 200;

    Alarm mAlarm;

    // the screen the user is currently hovering over, if any
    private CellLayout mScreen;
    private Launcher mLauncher;
    boolean mFinishedAnimation = false;
    boolean mWaitingToReenter = false;

    public SpringLoadedDragController(Launcher launcher) {
        mLauncher = launcher;
        mAlarm = new Alarm();
        mAlarm.setOnAlarmListener(this);
    }

    public void onDragEnter(CellLayout cl, boolean isSpringLoaded) {
        mScreen = cl;
        mAlarm.setAlarm(ENTER_SPRING_LOAD_HOVER_TIME);
        mFinishedAnimation = isSpringLoaded;
        mWaitingToReenter = false;
    }

    public void onEnterSpringLoadedMode(boolean waitToReenter) {
        mFinishedAnimation = true;
        mWaitingToReenter = waitToReenter;
    }

    public void onDragExit() {
        if (mScreen != null) {
            mScreen.onDragExit();
        }
        mScreen = null;
        if (mFinishedAnimation && !mWaitingToReenter) {
            mAlarm.setAlarm(EXIT_SPRING_LOAD_HOVER_TIME);
        }
    }

    // this is called when our timer runs out
    public void onAlarm(Alarm alarm) {
        if (mScreen != null) {
            // we're currently hovering over a screen
            mLauncher.enterSpringLoadedDragMode(mScreen);
        } else {
            mLauncher.exitSpringLoadedDragMode();
        }
    }
}
