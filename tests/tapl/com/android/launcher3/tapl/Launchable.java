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

import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

import static com.android.launcher3.testing.shared.TestProtocol.SPRING_LOADED_STATE_ORDINAL;

import android.graphics.Point;
import android.view.MotionEvent;

import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

/**
 * Ancestor for AppIcon and AppMenuItem.
 */
public abstract class Launchable {

    protected static final int DEFAULT_DRAG_STEPS = 10;

    protected final LauncherInstrumentation mLauncher;

    protected final UiObject2 mObject;

    Launchable(LauncherInstrumentation launcher, UiObject2 object) {
        mObject = object;
        mLauncher = launcher;
    }

    UiObject2 getObject() {
        return mObject;
    }

    protected boolean launcherStopsAfterLaunch() {
        return true;
    }

    /**
     * Clicks the object to launch its app.
     * We are assuming non-translucent app launches because only such launches generate
     * LAUNCHER_ACTIVITY_STOPPED_MESSAGE.
     */
    public LaunchedAppState launch(String expectedPackageName) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "want to launch an app from " + launchableType())) {
                LauncherInstrumentation.log("Launchable.launch before click "
                        + mObject.getVisibleCenter() + " in "
                        + mLauncher.getVisibleBounds(mObject));

                if (launcherStopsAfterLaunch()) {
                    mLauncher.executeAndWaitForLauncherStop(
                            () -> mLauncher.clickLauncherObject(mObject),
                            "clicking the launchable");
                } else {
                    mLauncher.clickLauncherObject(mObject);
                }

                try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer("clicked")) {
                    expectActivityStartEvents();
                    return mLauncher.assertAppLaunched(expectedPackageName);
                }
            }
        }
    }

    protected abstract void expectActivityStartEvents();

    protected abstract String launchableType();

    /**
     * Clicks a launcher object to initiate splitscreen, where the selected app will be one of two
     * apps running on the screen. Should be called when Launcher is in a "split staging" state
     * and is waiting for the user's selection of a second app. Expects a SPLIT_START_EVENT to be
     * fired when the click is executed.
     */
    public LaunchedAppState launchIntoSplitScreen() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                     "want to launch split tasks from " + launchableType())) {
            LauncherInstrumentation.log("Launchable.launch before click "
                    + mObject.getVisibleCenter() + " in " + mLauncher.getVisibleBounds(
                    mObject));
            mLauncher.executeAndWaitForLauncherEvent(
                    () -> mLauncher.clickLauncherObject(mObject),
                    accessibilityEvent ->
                            accessibilityEvent.getEventType() == TYPE_WINDOW_STATE_CHANGED,
                    () -> "Unable to click object to launch split",
                    "Click launcher object to launch split");

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer("clicked")) {
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, OverviewTask.SPLIT_START_EVENT);
                return new LaunchedAppState(mLauncher);
            }
        }
    }

    Point startDrag(long downTime, Runnable expectLongClickEvents, boolean runToSpringLoadedState) {
        final Point iconCenter = getObject().getVisibleCenter();
        final Point dragStartCenter = new Point(iconCenter.x,
                iconCenter.y - getStartDragThreshold());

        if (runToSpringLoadedState) {
            mLauncher.runToState(() -> movePointerForStartDrag(
                    downTime,
                    iconCenter,
                    dragStartCenter,
                    expectLongClickEvents),
                    SPRING_LOADED_STATE_ORDINAL, "long-pressing and triggering drag start");
        } else {
            movePointerForStartDrag(
                    downTime,
                    iconCenter,
                    dragStartCenter,
                    expectLongClickEvents);
        }

        return dragStartCenter;
    }

    /**
     * Waits for a confirmation that a long press has successfully been triggered.
     *
     * This method waits for a view to either appear or disappear to confirm that the long press
     * has been triggered and fails if no confirmation is received before the default timeout.
     */
    protected abstract void waitForLongPressConfirmation();

    /**
     * Drags this Launchable a short distance before starting a full drag.
     *
     * This is necessary for shortcuts, which require being dragged beyond a threshold to close
     * their container and start drag callbacks.
     */
    private void movePointerForStartDrag(
            long downTime,
            Point iconCenter,
            Point dragStartCenter,
            Runnable expectLongClickEvents) {
        mLauncher.sendPointer(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                iconCenter,
                LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
        LauncherInstrumentation.log("movePointerForStartDrag: sent down");
        expectLongClickEvents.run();
        waitForLongPressConfirmation();
        LauncherInstrumentation.log("movePointerForStartDrag: indicator");
        mLauncher.movePointer(
                iconCenter,
                dragStartCenter,
                DEFAULT_DRAG_STEPS,
                /* isDecelerating= */ false,
                downTime,
                downTime,
                /* slowDown= */ true,
                LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
    }

    private int getStartDragThreshold() {
        return mLauncher.getTestInfo(TestProtocol.REQUEST_START_DRAG_THRESHOLD).getInt(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    protected abstract void addExpectedEventsForLongClick();
}
