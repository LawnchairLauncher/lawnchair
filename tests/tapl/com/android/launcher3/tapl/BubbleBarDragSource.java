/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.launcher3.tapl.LaunchedAppState.DEFAULT_DRAG_STEPS;
import static com.android.launcher3.tapl.LauncherInstrumentation.DEFAULT_POLL_INTERVAL;
import static com.android.launcher3.tapl.LauncherInstrumentation.WAIT_TIME_MS;
import static com.android.launcher3.testing.shared.TestProtocol.REQUEST_SHELL_DRAG_READY;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;

import com.android.launcher3.tapl.Taskbar.TaskbarLocation;
import com.android.launcher3.testing.shared.TestProtocol;

/** {@link Launchable} that can serve as a source for dragging and dropping to the bubble bar. */
public interface BubbleBarDragSource {

    /**
     * Drags this app icon to the provided bubble bar location drop zone.
     */
    default void dragToBubbleBarLocation(boolean isBubbleBarLeftDropTarget) {
        Launchable launchable = getLaunchable();
        LauncherInstrumentation launcher = launchable.mLauncher;
        int bubbleBarDropTargetHalfSize = launcher.getBubbleBarDropTargetSize() / 2;
        final Point displaySize = launcher.getRealDisplaySize();
        int endX = isBubbleBarLeftDropTarget ? bubbleBarDropTargetHalfSize
                : displaySize.x - bubbleBarDropTargetHalfSize;
        final Point endPoint = new Point(endX, displaySize.y - bubbleBarDropTargetHalfSize);
        dragToPoint(launcher, launchable, endPoint,
                /* waitForShell = */ getTaskbarLocation() == TaskbarLocation.LAUNCHED_APP);
    }

    private static void dragToPoint(
            LauncherInstrumentation launcher,
            Launchable launchable,
            Point endPoint,
            boolean waitForShell
    ) {
        try (LauncherInstrumentation.Closable e = launcher.eventsCheck()) {
            try (LauncherInstrumentation.Closable c1 = launcher.addContextLayer(
                    "want to drag taskbar item to point: " + endPoint)) {
                final long downTime = SystemClock.uptimeMillis();
                Point itemVisibleCenter = launchable.mObject.getVisibleCenter();
                Rect itemVisibleBounds = launcher.getVisibleBounds(launchable.mObject);
                Point dragStart = launchable.startDrag(
                        downTime,
                        launchable::addExpectedEventsForLongClick,
                        /* runToSpringLoadedState= */ false);
                try (LauncherInstrumentation.Closable c2 = launcher.addContextLayer(
                        "started item drag")) {
                    if (waitForShell) {
                        launcher.assertTrue("Shell drag not marked as ready",
                                launcher.waitAndGet(() -> {
                                    LauncherInstrumentation.log("Checking shell drag ready");
                                    return launcher.getTestInfo(REQUEST_SHELL_DRAG_READY)
                                            .getBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                                                    false);
                                }, WAIT_TIME_MS, DEFAULT_POLL_INTERVAL));
                    }
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
                            "perform drop")) {
                        LauncherInstrumentation.log("BubbleBarDragSource.dragToPoint: "
                                + "before drop " + itemVisibleCenter + " in " + itemVisibleBounds);
                        launcher.sendPointer(
                                downTime,
                                SystemClock.uptimeMillis(),
                                MotionEvent.ACTION_UP,
                                endPoint,
                                LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
                        try (LauncherInstrumentation.Closable c4 = launcher.addContextLayer(
                                "BubbleBar should appear")) {
                            new BubbleBar(launcher);
                        }
                    }
                }
            }
        }
    }

    /**
     * NOT INTENDED TO BE USED FROM TESTS<br>
     * Returns the taskbar location
     */
    TaskbarLocation getTaskbarLocation();

    /**
     * NOT INTENDED TO BE USED FROM TESTS<br>
     * This method requires public access, however should not be called in tests.
     */
    Launchable getLaunchable();
}
