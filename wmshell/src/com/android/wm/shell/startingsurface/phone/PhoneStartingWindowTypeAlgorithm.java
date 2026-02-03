/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.startingsurface.phone;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_NONE;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SNAPSHOT;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SOLID_COLOR_SPLASH_SCREEN;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_WINDOWLESS;
import static android.window.StartingWindowInfo.TYPE_PARAMETER_ACTIVITY_CREATED;
import static android.window.StartingWindowInfo.TYPE_PARAMETER_ACTIVITY_DRAWN;
import static android.window.StartingWindowInfo.TYPE_PARAMETER_ALLOW_TASK_SNAPSHOT;
import static android.window.StartingWindowInfo.TYPE_PARAMETER_LEGACY_SPLASH_SCREEN;
import static android.window.StartingWindowInfo.TYPE_PARAMETER_NEW_TASK;
import static android.window.StartingWindowInfo.TYPE_PARAMETER_PROCESS_RUNNING;
import static android.window.StartingWindowInfo.TYPE_PARAMETER_TASK_SWITCH;
import static android.window.StartingWindowInfo.TYPE_PARAMETER_USE_SOLID_COLOR_SPLASH_SCREEN;
import static android.window.StartingWindowInfo.TYPE_PARAMETER_WINDOWLESS;

import android.window.StartingWindowInfo;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.startingsurface.StartingWindowTypeAlgorithm;

/**
 * Algorithm for determining the type of a new starting window on handheld devices.
 * At the moment also used on Android Auto and Wear OS.
 */
public class PhoneStartingWindowTypeAlgorithm implements StartingWindowTypeAlgorithm {
    @Override
    public int getSuggestedWindowType(StartingWindowInfo windowInfo) {
        final int parameter = windowInfo.startingWindowTypeParameter;
        final boolean newTask = (parameter & TYPE_PARAMETER_NEW_TASK) != 0;
        final boolean taskSwitch = (parameter & TYPE_PARAMETER_TASK_SWITCH) != 0;
        final boolean processRunning = (parameter & TYPE_PARAMETER_PROCESS_RUNNING) != 0;
        final boolean allowTaskSnapshot = (parameter & TYPE_PARAMETER_ALLOW_TASK_SNAPSHOT) != 0;
        final boolean activityCreated = (parameter & TYPE_PARAMETER_ACTIVITY_CREATED) != 0;
        final boolean isSolidColorSplashScreen =
                (parameter & TYPE_PARAMETER_USE_SOLID_COLOR_SPLASH_SCREEN) != 0;
        final boolean legacySplashScreen =
                ((parameter & TYPE_PARAMETER_LEGACY_SPLASH_SCREEN) != 0);
        final boolean activityDrawn = (parameter & TYPE_PARAMETER_ACTIVITY_DRAWN) != 0;
        final boolean windowlessSurface = (parameter & TYPE_PARAMETER_WINDOWLESS) != 0;
        final boolean topIsHome = windowInfo.taskInfo.topActivityType == ACTIVITY_TYPE_HOME;

        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                "preferredStartingWindowType "
                        + "newTask=%b, "
                        + "taskSwitch=%b, "
                        + "processRunning=%b, "
                        + "allowTaskSnapshot=%b, "
                        + "activityCreated=%b, "
                        + "isSolidColorSplashScreen=%b, "
                        + "legacySplashScreen=%b, "
                        + "activityDrawn=%b, "
                        + "windowless=%b, "
                        + "topIsHome=%b",
                newTask, taskSwitch, processRunning, allowTaskSnapshot, activityCreated,
                isSolidColorSplashScreen, legacySplashScreen, activityDrawn, windowlessSurface,
                topIsHome);

        if (windowlessSurface) {
            return STARTING_WINDOW_TYPE_WINDOWLESS;
        }
        if (!topIsHome) {
            if (!processRunning || newTask || (taskSwitch && !activityCreated)) {
                return getSplashscreenType(isSolidColorSplashScreen, legacySplashScreen);
            }
        }

        if (taskSwitch) {
            if (allowTaskSnapshot) {
                if (windowInfo.taskSnapshot != null) {
                    return STARTING_WINDOW_TYPE_SNAPSHOT;
                }
                if (!topIsHome) {
                    return STARTING_WINDOW_TYPE_SOLID_COLOR_SPLASH_SCREEN;
                }
            }
            if (!activityDrawn && !topIsHome) {
                return getSplashscreenType(isSolidColorSplashScreen, legacySplashScreen);
            }
        }
        return STARTING_WINDOW_TYPE_NONE;
    }

    private static int getSplashscreenType(boolean solidColorSplashScreen,
            boolean legacySplashScreen) {
        return solidColorSplashScreen
                ? STARTING_WINDOW_TYPE_SOLID_COLOR_SPLASH_SCREEN
                : legacySplashScreen
                        ? STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN
                        : STARTING_WINDOW_TYPE_SPLASH_SCREEN;
    }
}
