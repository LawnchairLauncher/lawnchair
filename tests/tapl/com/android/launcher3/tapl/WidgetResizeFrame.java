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
package com.android.launcher3.tapl;

/** The resize frame that is shown for a widget on the workspace. */
public class WidgetResizeFrame {

    private final LauncherInstrumentation mLauncher;

    WidgetResizeFrame(LauncherInstrumentation launcher) {
        mLauncher = launcher;
        launcher.waitForLauncherObject("widget_resize_frame");
    }

    /** Dismisses the resize frame. */
    public void dismiss() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to dismiss widget resize frame")) {
            // Dismiss the resize frame by pressing the home button.
            mLauncher.getDevice().pressHome();
        }
    }
}
