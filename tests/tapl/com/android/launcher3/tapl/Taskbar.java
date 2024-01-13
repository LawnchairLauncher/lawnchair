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

import static android.view.KeyEvent.KEYCODE_META_RIGHT;

import static com.android.launcher3.tapl.LauncherInstrumentation.TASKBAR_RES_ID;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import org.junit.Assert;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Operations on the Taskbar from LaunchedApp.
 */
public final class Taskbar {

    private final LauncherInstrumentation mLauncher;

    Taskbar(LauncherInstrumentation launcher) {
        mLauncher = launcher;
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "expect new taskbar to be visible")) {
            mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID);
        }

        if (!mLauncher.isTransientTaskbar()) {
            Assert.assertEquals("Persistent taskbar should fill screen width",
                    getVisibleBounds().width(), mLauncher.getRealDisplaySize().x);
        }
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
     * Stashes this taskbar.
     * <p>
     * The taskbar must already be unstashed and in transient mode when calling this method.
     */
    public void swipeDownToStash() {
        mLauncher.assertTrue("Taskbar is not transient, swipe down not supported",
                mLauncher.isTransientTaskbar());

        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to hide the taskbar");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID);

            Rect taskbarBounds = getVisibleBounds();
            int startX = taskbarBounds.centerX();
            int startY = taskbarBounds.centerY();
            int endX = startX;
            int endY = mLauncher.getRealDisplaySize().y - 1;

            mLauncher.linearGesture(startX, startY, endX, endY, 10, false,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
            LauncherInstrumentation.log("swipeDownToStash: sent linear swipe down gesture");
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "expect transient taskbar to be hidden after swipe down")) {
                mLauncher.waitUntilSystemLauncherObjectGone(TASKBAR_RES_ID);
            }
        }
    }

    /**
     * Opens the Taskbar all apps page.
     */
    public TaskbarAllApps openAllApps() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to open taskbar all apps");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {

            mLauncher.clickLauncherObject(mLauncher.waitForObjectInContainer(
                    mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID),
                    getAllAppsButtonSelector()));

            return getAllApps();
        }
    }

    /** Opens the Taskbar all apps page with the meta keyboard shortcut. */
    public TaskbarAllApps openAllAppsFromKeyboardShortcut() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            mLauncher.getDevice().pressKeyCode(KEYCODE_META_RIGHT);
            try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                    "pressed meta key")) {
                return getAllApps();
            }
        }
    }

    /** Returns {@link TaskbarAllApps} if it is open, otherwise fails. */
    public TaskbarAllApps getAllApps() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get taskbar all apps object")) {
            return new TaskbarAllApps(mLauncher);
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

    public Rect getVisibleBounds() {
        return mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID).getVisibleBounds();
    }

    /**
     * Touch either on the right or the left corner of the screen, 1 pixel from the bottom and
     * from the sides.
     */
    void touchBottomCorner(boolean tapRight) {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to tap bottom corner on the " + (tapRight ? "right" : "left"))) {
            final long downTime = SystemClock.uptimeMillis();
            final Point tapTarget = new Point(
                    tapRight
                            ?
                            getVisibleBounds().right
                                    - mLauncher.getTargetInsets().right
                                    - 1
                            : getVisibleBounds().left
                                    + 1,
                    mLauncher.getRealDisplaySize().y - 1);

            mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, tapTarget,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
            mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_UP, tapTarget,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
        }
    }
}
