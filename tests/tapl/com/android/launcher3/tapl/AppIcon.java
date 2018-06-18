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

import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.widget.TextView;

/**
 * App icon, whether in all apps or in workspace/
 */
public final class AppIcon {
    private final Launcher mLauncher;
    private final UiObject2 mIcon;

    AppIcon(Launcher launcher, UiObject2 icon) {
        mLauncher = launcher;
        mIcon = icon;
    }

    static BySelector getAppIconSelector(String appName) {
        return By.clazz(TextView.class).text(appName).pkg(Launcher.LAUNCHER_PKG);
    }

    /**
     * Clicks the icon to launch its app.
     */
    public void launch() {
        mLauncher.assertTrue("Launching an app didn't open a new window: " + mIcon.getText(),
                mIcon.clickAndWait(Until.newWindow(), Launcher.APP_LAUNCH_TIMEOUT_MS));
        mLauncher.assertState(Launcher.State.BACKGROUND);
    }

    UiObject2 getIcon() {
        return mIcon;
    }
}
