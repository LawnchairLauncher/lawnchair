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

import static com.android.launcher3.testing.TestProtocol.REQUEST_DISABLE_MANUAL_TASKBAR_STASHING;
import static com.android.launcher3.testing.TestProtocol.REQUEST_ENABLE_MANUAL_TASKBAR_STASHING;
import static com.android.launcher3.testing.TestProtocol.REQUEST_STASHED_TASKBAR_HEIGHT;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.test.uiautomator.By;

import com.android.launcher3.testing.TestProtocol;

/**
 * Background state operations specific to when an app has been launched.
 */
public final class LaunchedAppState extends Background {

    // More drag steps than Launchables to give the window manager time to register the drag.
    private static final int DEFAULT_DRAG_STEPS = 35;

    LaunchedAppState(LauncherInstrumentation launcher) {
        super(launcher);
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.LAUNCHED_APP;
    }

    /**
     * Returns the taskbar.
     *
     * The taskbar must already be visible when calling this method.
     */
    public Taskbar getTaskbar() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get the taskbar")) {
            mLauncher.waitForLauncherObject("taskbar_view");

            return new Taskbar(mLauncher);
        }
    }

    /**
     * Returns the Taskbar in a visible state.
     *
     * The taskbar must already be hidden when calling this method.
     */
    public Taskbar showTaskbar() {
        mLauncher.getTestInfo(REQUEST_ENABLE_MANUAL_TASKBAR_STASHING);

        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                     "want to show the taskbar")) {
            mLauncher.waitUntilLauncherObjectGone("taskbar_view");

            final long downTime = SystemClock.uptimeMillis();
            final int unstashTargetY = mLauncher.getRealDisplaySize().y
                    - (mLauncher.getTestInfo(REQUEST_STASHED_TASKBAR_HEIGHT)
                            .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD) / 2);
            final Point unstashTarget = new Point(
                    mLauncher.getRealDisplaySize().x / 2, unstashTargetY);

            mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, unstashTarget,
                    LauncherInstrumentation.GestureScope.OUTSIDE_WITH_PILFER);
            LauncherInstrumentation.log("showTaskbar: sent down");

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer("pressed down")) {
                mLauncher.waitForLauncherObject("taskbar_view");
                mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_UP, unstashTarget,
                        LauncherInstrumentation.GestureScope.OUTSIDE_WITH_PILFER);

                return new Taskbar(mLauncher);
            }
        } finally {
            mLauncher.getTestInfo(REQUEST_DISABLE_MANUAL_TASKBAR_STASHING);
        }
    }

    static void dragToSplitscreen(
            LauncherInstrumentation launcher, Launchable launchable, String expectedNewPackageName,
            String expectedExistingPackageName) {
        try (LauncherInstrumentation.Closable c1 = launcher.addContextLayer(
                "want to drag taskbar item to splitscreen")) {
            final Point displaySize = launcher.getRealDisplaySize();
            final Point endPoint = new Point(displaySize.x / 4, 3 * displaySize.y / 4);
            final long downTime = SystemClock.uptimeMillis();
            // Use mObject before starting drag since the system drag and drop moves the original
            // view.
            Point itemVisibleCenter = launchable.mObject.getVisibleCenter();
            Rect itemVisibleBounds = launcher.getVisibleBounds(launchable.mObject);
            String itemLabel = launchable.mObject.getText();

            Point dragStart = launchable.startDrag(
                    downTime,
                    launchable::addExpectedEventsForLongClick,
                    /* runToSpringLoadedState= */ false);

            try (LauncherInstrumentation.Closable c2 = launcher.addContextLayer(
                    "started item drag")) {
                launcher.movePointer(
                        dragStart,
                        endPoint,
                        DEFAULT_DRAG_STEPS,
                        /* isDecelerating= */ true,
                        downTime,
                        SystemClock.uptimeMillis(),
                        /* slowDown= */ false,
                        LauncherInstrumentation.GestureScope.INSIDE);

                try (LauncherInstrumentation.Closable c3 = launcher.addContextLayer(
                        "moved pointer to drop point")) {
                    dropDraggedItem(
                            launcher,
                            launchable,
                            expectedNewPackageName,
                            endPoint, downTime,
                            itemVisibleCenter,
                            itemVisibleBounds,
                            itemLabel,
                            expectedExistingPackageName);
                }
            }
        }
    }

    private static void dropDraggedItem(
            LauncherInstrumentation launcher, Launchable launchable, String expectedNewPackageName,
            Point endPoint, long downTime, Point itemVisibleCenter, Rect itemVisibleBounds,
            String itemLabel, String expectedExistingPackageName) {
        LauncherInstrumentation.log("SplitscreenDragSource.dragToSplitscreen before drop "
                + itemVisibleCenter + " in " + itemVisibleBounds);

        launchable.executeAndWaitForWindowChange(() -> {
            launcher.sendPointer(
                    downTime,
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_UP,
                    endPoint,
                    LauncherInstrumentation.GestureScope.INSIDE_TO_OUTSIDE_WITHOUT_PILFER);
            LauncherInstrumentation.log("SplitscreenDragSource.dragToSplitscreen: after "
                    + "drop");
        }, itemLabel, "dropping taskbar item");

        try (LauncherInstrumentation.Closable c = launcher.addContextLayer("dropped item")) {
            launchable.assertAppLaunched(itemLabel, By.pkg(expectedNewPackageName));
            launcher.checkPackagesVisible(
                    new String[] {expectedNewPackageName, expectedExistingPackageName});
        }
    }
}
