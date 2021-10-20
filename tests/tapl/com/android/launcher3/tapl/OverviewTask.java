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

import android.graphics.Rect;

import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.TestProtocol;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A recent task in the overview panel carousel.
 */
public final class OverviewTask {
    static final Pattern TASK_START_EVENT =
            Pattern.compile("startActivityFromRecentsAsync");
    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mTask;
    private final BaseOverview mOverview;

    OverviewTask(LauncherInstrumentation launcher, UiObject2 task, BaseOverview overview) {
        mLauncher = launcher;
        mTask = task;
        mOverview = overview;
        verifyActiveContainer();
    }

    private void verifyActiveContainer() {
        mOverview.verifyActiveContainer();
    }

    int getVisibleHeight() {
        return mTask.getVisibleBounds().height();
    }

    int getTaskCenterX() {
        return mTask.getVisibleCenter().x;
    }

    /**
     * Dismisses the task by swiping up.
     */
    public void dismiss() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to dismiss an overview task")) {
            verifyActiveContainer();
            int taskCountBeforeDismiss = mOverview.getTaskCount();
            mLauncher.assertNotEquals("Unable to find a task", 0, taskCountBeforeDismiss);
            if (taskCountBeforeDismiss == 1) {
                dismissBySwipingUp();
                return;
            }

            boolean taskWasFocused = mLauncher.isTablet() && getVisibleHeight() == mLauncher
                    .getFocusedTaskHeightForTablet();
            List<Integer> originalTasksCenterX = getCurrentTasksCenterXList();
            boolean isClearAllVisibleBeforeDismiss = mOverview.isClearAllVisible();

            dismissBySwipingUp();

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer("dismissed")) {
                if (taskWasFocused) {
                    mLauncher.assertNotNull("No task became focused",
                            mOverview.getFocusedTaskForTablet());
                }
                if (!isClearAllVisibleBeforeDismiss) {
                    List<Integer> currentTasksCenterX = getCurrentTasksCenterXList();
                    if (originalTasksCenterX.size() == currentTasksCenterX.size()) {
                        // Check for the same number of visible tasks before and after to
                        // avoid asserting on cases of shifting all tasks to close the distance
                        // between clear all and tasks at the end of the grid.
                        mLauncher.assertTrue("Task centers not aligned",
                                originalTasksCenterX.equals(currentTasksCenterX));
                    }
                }
            }
        }
    }

    private void dismissBySwipingUp() {
        verifyActiveContainer();
        // Dismiss the task via flinging it up.
        final Rect taskBounds = mLauncher.getVisibleBounds(mTask);
        final int centerX = taskBounds.centerX();
        final int centerY = taskBounds.centerY();
        mLauncher.executeAndWaitForLauncherEvent(
                () -> mLauncher.linearGesture(centerX, centerY, centerX, 0, 10, false,
                        LauncherInstrumentation.GestureScope.INSIDE),
                event -> TestProtocol.DISMISS_ANIMATION_ENDS_MESSAGE.equals(event.getClassName()),
                () -> "Didn't receive a dismiss animation ends message: " + centerX + ", "
                        + centerY, "swiping to dismiss");
    }

    private List<Integer> getCurrentTasksCenterXList() {
        return mLauncher.isTablet()
                ? mOverview.getCurrentTasksForTablet().stream()
                    .map(OverviewTask::getTaskCenterX)
                    .collect(Collectors.toList())
                : List.of(mOverview.getCurrentTask().getTaskCenterX());
    }

    /**
     * Clicks at the task.
     */
    public Background open() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            verifyActiveContainer();
            mLauncher.executeAndWaitForEvent(
                    () -> mLauncher.clickLauncherObject(mTask),
                    event -> event.getEventType() == TYPE_WINDOW_STATE_CHANGED,
                    () -> "Launching task didn't open a new window: "
                            + mTask.getParent().getContentDescription(),
                    "clicking an overview task");
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, TASK_START_EVENT);
            return new Background(mLauncher);
        }
    }
}
