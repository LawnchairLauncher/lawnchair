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

import android.graphics.Point;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

/**
 * App icon, whether in all apps or in workspace/
 */
public final class AppIcon extends Launchable {
    AppIcon(LauncherInstrumentation launcher, UiObject2 icon) {
        super(launcher, icon);
    }

    static BySelector getAppIconSelector(String appName, LauncherInstrumentation launcher) {
        return By.clazz(TextView.class).text(appName).pkg(launcher.getLauncherPackageName());
    }

    /**
     * Long-clicks the icon to open its menu.
     */
    public AppIconMenu openMenu() {
        final Point iconCenter = mObject.getVisibleCenter();
        final long downTime = SystemClock.uptimeMillis();
        mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, iconCenter);
        final UiObject2 deepShortcutsContainer = mLauncher.waitForLauncherObject(
                "deep_shortcuts_container");
        mLauncher.sendPointer(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, iconCenter);
        return new AppIconMenu(mLauncher, deepShortcutsContainer);
    }

    @Override
    protected String getLongPressIndicator() {
        return "deep_shortcuts_container";
    }
}
