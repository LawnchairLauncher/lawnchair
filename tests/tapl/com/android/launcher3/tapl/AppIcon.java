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

package com.android.launcher3.tapl;

import android.widget.TextView;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

/**
 * App icon, whether in all apps or in workspace/
 */
public final class AppIcon {
    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mIcon;

    AppIcon(LauncherInstrumentation launcher, UiObject2 icon) {
        mLauncher = launcher;
        mIcon = icon;
    }

    static BySelector getAppIconSelector(String appName) {
        return By.clazz(TextView.class).text(appName).pkg(LauncherInstrumentation.LAUNCHER_PKG);
    }

    /**
     * Clicks the icon to launch its app.
     */
    public Background launch() {
        LauncherInstrumentation.log("AppIcon.launch before click");
        LauncherInstrumentation.assertTrue(
                "Launching an app didn't open a new window: " + mIcon.getText(),
                mIcon.clickAndWait(Until.newWindow(), LauncherInstrumentation.WAIT_TIME_MS));
        return new Background(mLauncher);
    }

    UiObject2 getIcon() {
        return mIcon;
    }
}
