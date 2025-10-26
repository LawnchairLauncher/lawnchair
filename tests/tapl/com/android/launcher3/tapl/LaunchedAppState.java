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

import static com.android.launcher3.tapl.LauncherInstrumentation.DEFAULT_POLL_INTERVAL;
import static com.android.launcher3.tapl.LauncherInstrumentation.TASKBAR_RES_ID;
import static com.android.launcher3.tapl.LauncherInstrumentation.WAIT_TIME_MS;
import static com.android.launcher3.testing.shared.TestProtocol.REQUEST_DISABLE_BLOCK_TIMEOUT;
import static com.android.launcher3.testing.shared.TestProtocol.REQUEST_ENABLE_BLOCK_TIMEOUT;
import static com.android.launcher3.testing.shared.TestProtocol.REQUEST_SHELL_DRAG_READY;
import static com.android.launcher3.testing.shared.TestProtocol.REQUEST_STASHED_TASKBAR_SCALE;
import static com.android.launcher3.testing.shared.TestProtocol.REQUEST_TASKBAR_FROM_NAV_THRESHOLD;
import static com.android.launcher3.testing.shared.TestProtocol.TEST_INFO_RESPONSE_FIELD;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.Condition;
import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.testing.shared.TestProtocol;

/**
 * Background state operations specific to when an app has been launched.
 */
public final class LaunchedAppState extends Background {

    // More drag steps than Launchables to give the window manager time to register the drag.
    private static final int DEFAULT_DRAG_STEPS = 35;

    // UNSTASHED_TASKBAR_HANDLE_HINT_SCALE value from TaskbarStashController.
    private static final float UNSTASHED_TASKBAR_HANDLE_HINT_SCALE = 1.1f;

    private static final int STASHED_TASKBAR_BOTTOM_EDGE_DP = 1;

    private final Condition<UiDevice, Boolean> mStashedTaskbarHintScaleCondition =
            device -> Math.abs(mLauncher.getTestInfo(REQUEST_STASHED_TASKBAR_SCALE).getFloat(
                    TestProtocol.TEST_INFO_RESPONSE_FIELD) - UNSTASHED_TASKBAR_HANDLE_HINT_SCALE)
                    < 0.00001f;

    private final Condition<UiDevice, Boolean> mStashedTaskbarDefaultScaleCondition =
            device -> Math.abs(mLauncher.getTestInfo(REQUEST_STASHED_TASKBAR_SCALE).getFloat(
                    TestProtocol.TEST_INFO_RESPONSE_FIELD) - 1f) < 0.00001f;

    LaunchedAppState(LauncherInstrumentation launcher) {
        super(launcher);
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.LAUNCHED_APP;
    }

    @Override
    public boolean isHomeState() {
        return false;
    }

    @NonNull
    @Override
    public BaseOverview switchToOverview() {
        try (LauncherInstrumentation.Closable ignored = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable ignored1 = mLauncher.addContextLayer(
                     "want to switch from background to overview")) {
            verifyActiveContainer();
            goToOverviewUnchecked();
            return mLauncher.is3PLauncher()
                    ? new BaseOverview(mLauncher, /*launchedFromApp=*/true)
                    : new Overview(mLauncher, /*launchedFromApp=*/true);
        }
    }

    /**
     * Returns the taskbar.
     *
     * The taskbar must already be visible when calling this method.
     */
    public Taskbar getTaskbar() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get the taskbar")) {
            return new Taskbar(mLauncher);
        }
    }

    /**
     * Waits for the taskbar to be hidden, or fails.
     */
    public void assertTaskbarHidden() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "waiting for taskbar to be hidden")) {
            mLauncher.waitUntilSystemLauncherObjectGone(TASKBAR_RES_ID);
        }
    }

    /**
     * Waits for the taskbar to be visible, or fails.
     */
    public void assertTaskbarVisible() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "waiting for taskbar to be visible")) {
            mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID);
        }
    }

    /**
     * Returns the Taskbar in a visible state.
     *
     * The taskbar must already be hidden and in transient mode when calling this method.
     */
    public Taskbar swipeUpToUnstashTaskbar() {
        mLauncher.assertTrue("Taskbar is not transient, swipe up not supported",
                mLauncher.isTransientTaskbar());

        mLauncher.getTestInfo(REQUEST_ENABLE_BLOCK_TIMEOUT);

        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                     "want to swipe up to unstash the taskbar")) {
            mLauncher.waitUntilSystemLauncherObjectGone(TASKBAR_RES_ID);

            int taskbarFromNavThreshold = mLauncher.getTestInfo(REQUEST_TASKBAR_FROM_NAV_THRESHOLD)
                    .getInt(TEST_INFO_RESPONSE_FIELD);
            int startX = mLauncher.getRealDisplaySize().x / 2;
            int startY = mLauncher.getRealDisplaySize().y - 1;
            int endX = startX;
            int endY = startY - taskbarFromNavThreshold;

            mLauncher.executeAndWaitForLauncherStop(
                    () -> mLauncher.linearGesture(startX, startY, endX, endY, 10,
                            /* slowDown= */ true,
                            LauncherInstrumentation.GestureScope.EXPECT_PILFER),
                    "swiping");
            LauncherInstrumentation.log("swipeUpToUnstashTaskbar: sent linear swipe up gesture");

            return new Taskbar(mLauncher);
        } finally {
            mLauncher.getTestInfo(REQUEST_DISABLE_BLOCK_TIMEOUT);
        }
    }

    static void dragToSplitscreen(
            LauncherInstrumentation launcher,
            Launchable launchable,
            String expectedNewPackageName,
            String expectedExistingPackageName) {
        try (LauncherInstrumentation.Closable c1 = launcher.addContextLayer(
                "want to drag taskbar item to splitscreen")) {
            final Point displaySize = launcher.getRealDisplaySize();
            // Drag to the center of the top-left quadrant of the screen, this point will work in
            // both portrait and landscape.
            final Point endPoint = new Point(displaySize.x / 4, displaySize.y / 4);
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
                launcher.assertTrue("Shell drag not marked as ready", launcher.waitAndGet(() -> {
                    LauncherInstrumentation.log("Checking shell drag ready");
                    return launcher.getTestInfo(REQUEST_SHELL_DRAG_READY)
                            .getBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD, false);
                }, WAIT_TIME_MS, DEFAULT_POLL_INTERVAL));

                launcher.movePointer(
                        dragStart,
                        endPoint,
                        DEFAULT_DRAG_STEPS,
                        /* isDecelerating= */ true,
                        downTime,
                        SystemClock.uptimeMillis(),
                        /* slowDown= */ false,
                        LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);

                try (LauncherInstrumentation.Closable c3 = launcher.addContextLayer(
                        "moved pointer to drop point")) {
                    LauncherInstrumentation.log("SplitscreenDragSource.dragToSplitscreen: "
                            + "before drop " + itemVisibleCenter + " in " + itemVisibleBounds);
                    launcher.sendPointer(
                            downTime,
                            SystemClock.uptimeMillis(),
                            MotionEvent.ACTION_UP,
                            endPoint,
                            LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
                    LauncherInstrumentation.log("SplitscreenDragSource.dragToSplitscreen: "
                            + "after drop");

                    try (LauncherInstrumentation.Closable c4 = launcher.addContextLayer(
                            "dropped item")) {
                        launcher.assertAppLaunched(expectedNewPackageName);
                        launcher.assertAppLaunched(expectedExistingPackageName);
                    }
                }
            }
        }
    }

    /**
     * Emulate the cursor hovering the screen edge to unstash the taskbar.
     *
     * <p>This unstashing occurs when not actively hovering the taskbar.
     */
    public Taskbar hoverScreenBottomEdgeToUnstashTaskbar() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "cursor hover entering screen edge to unstash taskbar")) {
            mLauncher.getDevice().wait(mStashedTaskbarDefaultScaleCondition,
                    ViewConfiguration.DEFAULT_LONG_PRESS_TIMEOUT);

            long downTime = SystemClock.uptimeMillis();
            int leftEdge = 10;
            Point taskbarUnstashArea = new Point(leftEdge, mLauncher.getRealDisplaySize().y - 1);
            mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_HOVER_ENTER,
                    new Point(taskbarUnstashArea.x, taskbarUnstashArea.y), null,
                    InputDevice.SOURCE_MOUSE);

            mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID);

            mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_HOVER_EXIT,
                    new Point(taskbarUnstashArea.x, taskbarUnstashArea.y), null,
                    InputDevice.SOURCE_MOUSE);

            return new Taskbar(mLauncher);
        }
    }

    /**
     * Emulate the cursor hovering the taskbar to get unstash hint, then hovering below to unstash.
     */
    public Taskbar hoverBelowHintedTaskbarToUnstash() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "cursor hover entering stashed taskbar")) {
            long downTime = SystemClock.uptimeMillis();
            Point stashedTaskbarHintArea = new Point(mLauncher.getRealDisplaySize().x / 2,
                    mLauncher.getRealDisplaySize().y - 1);
            mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_HOVER_ENTER,
                    new Point(stashedTaskbarHintArea.x, stashedTaskbarHintArea.y), null,
                    InputDevice.SOURCE_MOUSE);

            mLauncher.getDevice().wait(mStashedTaskbarHintScaleCondition,
                    LauncherInstrumentation.WAIT_TIME_MS);

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                         "cursor hover enter below taskbar to unstash")) {
                downTime = SystemClock.uptimeMillis();
                Point taskbarUnstashArea = new Point(mLauncher.getRealDisplaySize().x / 2,
                        mLauncher.getRealDisplaySize().y - 1);
                mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_HOVER_EXIT,
                        new Point(taskbarUnstashArea.x, taskbarUnstashArea.y), null,
                        InputDevice.SOURCE_MOUSE);

                mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID);
                return new Taskbar(mLauncher);
            }
        }
    }

    /**
     * Emulate the cursor entering and exiting a hover over the taskbar.
     */
    public void hoverToShowTaskbarUnstashHint() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "cursor hover entering stashed taskbar")) {
            long downTime = SystemClock.uptimeMillis();
            Point stashedTaskbarHintArea = new Point(mLauncher.getRealDisplaySize().x / 2,
                    mLauncher.getRealDisplaySize().y - 1);
            mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_HOVER_ENTER,
                    new Point(stashedTaskbarHintArea.x, stashedTaskbarHintArea.y), null,
                    InputDevice.SOURCE_MOUSE);

            mLauncher.getDevice().wait(mStashedTaskbarHintScaleCondition,
                    LauncherInstrumentation.WAIT_TIME_MS);

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                         "cursor hover exiting stashed taskbar")) {
                Point outsideStashedTaskbarHintArea = new Point(
                        mLauncher.getRealDisplaySize().x / 2,
                        mLauncher.getRealDisplaySize().y - 500);
                mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_HOVER_EXIT,
                        new Point(outsideStashedTaskbarHintArea.x, outsideStashedTaskbarHintArea.y),
                        null, InputDevice.SOURCE_MOUSE);

                mLauncher.getDevice().wait(mStashedTaskbarDefaultScaleCondition,
                        LauncherInstrumentation.WAIT_TIME_MS);
            }
        }
    }

    /**
     * Emulate the cursor clicking the stashed taskbar to go home.
     */
    public Workspace clickStashedTaskbarToGoHome() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "cursor hover entering stashed taskbar")) {
            long downTime = SystemClock.uptimeMillis();
            int stashedTaskbarBottomEdge = ResourceUtils.pxFromDp(STASHED_TASKBAR_BOTTOM_EDGE_DP,
                    mLauncher.getResources().getDisplayMetrics());
            Point stashedTaskbarHintArea = new Point(mLauncher.getRealDisplaySize().x / 2,
                    mLauncher.getRealDisplaySize().y - stashedTaskbarBottomEdge - 1);
            mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_HOVER_ENTER,
                    new Point(stashedTaskbarHintArea.x, stashedTaskbarHintArea.y), null,
                    InputDevice.SOURCE_MOUSE);

            mLauncher.getDevice().wait(mStashedTaskbarHintScaleCondition,
                    LauncherInstrumentation.WAIT_TIME_MS);

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "cursor clicking stashed taskbar to go home")) {
                mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_HOVER_EXIT,
                        new Point(stashedTaskbarHintArea.x, stashedTaskbarHintArea.y),
                        null, InputDevice.SOURCE_MOUSE);
                mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN,
                        new Point(stashedTaskbarHintArea.x, stashedTaskbarHintArea.y),
                        LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER,
                        InputDevice.SOURCE_MOUSE);
                mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_BUTTON_PRESS,
                        new Point(stashedTaskbarHintArea.x, stashedTaskbarHintArea.y),
                        null, InputDevice.SOURCE_MOUSE);
                mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_BUTTON_RELEASE,
                        new Point(stashedTaskbarHintArea.x, stashedTaskbarHintArea.y),
                        null, InputDevice.SOURCE_MOUSE);
                mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_UP,
                        new Point(stashedTaskbarHintArea.x, stashedTaskbarHintArea.y),
                        LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER,
                        InputDevice.SOURCE_MOUSE);

                return mLauncher.getWorkspace();
            }
        }
    }

    /** Send the "back" gesture to go to workspace. */
    public Workspace pressBackToWorkspace() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to press back from launched app to workspace")) {
            if (mLauncher.isLauncher3()) {
                mLauncher.pressBackImpl();
            } else {
                mLauncher.executeAndWaitForWallpaperAnimation(
                        () -> mLauncher.pressBackImpl(),
                        "pressing back"
                );
            }
            return new Workspace(mLauncher);
        }
    }
}
