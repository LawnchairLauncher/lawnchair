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

import static com.android.launcher3.tapl.Launchable.DEFAULT_DRAG_STEPS;

import static org.junit.Assert.assertTrue;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.test.uiautomator.UiObject2;

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

    /** Resizes the widget to double its height, and returns the resize frame. */
    public WidgetResizeFrame resize() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to resize the widget frame.")) {
            UiObject2 widget = mLauncher.waitForLauncherObject("widget_resize_frame");
            UiObject2 bottomResizeHandle =
                    mLauncher.waitForLauncherObject("widget_resize_bottom_handle");
            Rect originalWidgetSize = widget.getVisibleBounds();
            Point targetStart = bottomResizeHandle.getVisibleCenter();
            Point targetDest = bottomResizeHandle.getVisibleCenter();
            targetDest.offset(0,
                    originalWidgetSize.height() + mLauncher.getCellLayoutBoarderHeight());

            final long downTime = SystemClock.uptimeMillis();
            mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, targetStart,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
            try {
                mLauncher.movePointer(targetStart, targetDest, DEFAULT_DRAG_STEPS,
                        true, downTime, downTime, true,
                        LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
            } finally {
                mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_UP, targetDest,
                        LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
            }

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                         "want to return resized widget resize frame")) {
                float newHeight = mLauncher.waitForLauncherObject(
                        "widget_resize_frame").getVisibleBounds().height();
                assertTrue("Widget not resized.", newHeight >= originalWidgetSize.height() * 2);
                return this;
            }
        }
    }
}
