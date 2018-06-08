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

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.util.TouchController;

import java.io.PrintWriter;

public class UiFactory {

    public static TouchController[] createTouchControllers(Launcher launcher) {
        return new TouchController[] {
                launcher.getDragController(), new AllAppsSwipeController(launcher)};
    }

    public static void setOnTouchControllersChangedListener(Context context, Runnable listener) { }

    public static StateHandler[] getStateHandler(Launcher launcher) {
        return new StateHandler[] {
                launcher.getAllAppsController(), launcher.getWorkspace() };
    }

    public static void resetOverview(Launcher launcher) { }

    public static void onLauncherStateOrFocusChanged(Launcher launcher) { }

    public static void onCreate(Launcher launcher) { }

    public static void onStart(Launcher launcher) { }

    public static void onLauncherStateOrResumeChanged(Launcher launcher) { }

    public static void onTrimMemory(Launcher launcher, int level) { }

    public static void useFadeOutAnimationForLauncherStart(Launcher launcher,
            CancellationSignal cancellationSignal) { }

    public static boolean dumpActivity(Activity activity, PrintWriter writer) {
        return false;
    }

    public static void prepareToShowOverview(Launcher launcher) { }

    public static void setBackButtonAlpha(Launcher launcher, float alpha, boolean animate) { }
}
