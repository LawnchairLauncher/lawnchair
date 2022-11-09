/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.launcher3.tapl.LauncherInstrumentation.TASKBAR_RES_ID;
import static com.android.launcher3.testing.shared.TestProtocol.REQUEST_DISABLE_MANUAL_TASKBAR_STASHING;
import static com.android.launcher3.testing.shared.TestProtocol.REQUEST_ENABLE_MANUAL_TASKBAR_STASHING;

import android.graphics.Point;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Operations on the Taskbar from LaunchedApp.
 */
public final class Taskbar {

    private final LauncherInstrumentation mLauncher;

    Taskbar(LauncherInstrumentation launcher) {
        mLauncher = launcher;
    }

    /**
     * Returns an app icon with the given name. This fails if the icon is not found.
     */
    @NonNull
    public TaskbarAppIcon getAppIcon(String appName) {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get a taskbar icon")) {
            return new TaskbarAppIcon(mLauncher, mLauncher.waitForObjectInContainer(
                    mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID),
                    AppIcon.getAppIconSelector(appName, mLauncher)));
        }
    }

    /**
     * Hides this taskbar.
     *
     * The taskbar must already be visible when calling this method.
     */
    public void hide() {
        mLauncher.getTestInfo(REQUEST_ENABLE_MANUAL_TASKBAR_STASHING);

        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to hide the taskbar");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID);

            final long downTime = SystemClock.uptimeMillis();
            Point stashTarget = new Point(
                    mLauncher.getRealDisplaySize().x - 1, mLauncher.getRealDisplaySize().y - 1);

            mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, stashTarget,
                    LauncherInstrumentation.GestureScope.INSIDE);
            LauncherInstrumentation.log("hideTaskbar: sent down");

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer("pressed down")) {
                mLauncher.waitUntilSystemLauncherObjectGone(TASKBAR_RES_ID);
                mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_UP, stashTarget,
                        LauncherInstrumentation.GestureScope.INSIDE);
            }
        } finally {
            mLauncher.getTestInfo(REQUEST_DISABLE_MANUAL_TASKBAR_STASHING);
        }
    }

    /**
     * Opens the Taskbar all apps page.
     */
    public AllAppsFromTaskbar openAllApps() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to open taskbar all apps");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {

            mLauncher.clickLauncherObject(mLauncher.waitForObjectInContainer(
                    mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID),
                    getAllAppsButtonSelector()));

            return new AllAppsFromTaskbar(mLauncher);
        }
    }

    /** Returns a list of app icon names on the Taskbar */
    public List<String> getIconNames() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get all taskbar icons")) {
            return mLauncher.waitForObjectsInContainer(
                    mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID),
                    AppIcon.getAnyAppIconSelector())
                    .stream()
                    .map(UiObject2::getText)
                    .filter(text -> !TextUtils.isEmpty(text)) // Filter out the all apps button
                    .collect(Collectors.toList());
        }
    }

    private static BySelector getAllAppsButtonSelector() {
        // Look for an icon with no text
        return By.clazz(TextView.class).text("");
    }
}
