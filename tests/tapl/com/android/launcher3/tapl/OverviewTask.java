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

import static com.android.launcher3.tapl.OverviewTask.OverviewSplitTask.DEFAULT;
import static com.android.launcher3.tapl.OverviewTask.OverviewSplitTask.SPLIT_BOTTOM_OR_RIGHT;
import static com.android.launcher3.tapl.OverviewTask.OverviewSplitTask.SPLIT_TOP_OR_LEFT;
import static com.android.launcher3.testing.shared.TestProtocol.SUCCESSFUL_GESTURE_MISMATCH_EVENTS;

import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A recent task in the overview panel carousel.
 */
public final class OverviewTask {
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    static final Pattern TASK_START_EVENT = Pattern.compile("startActivityFromRecentsAsync");
    static final Pattern SPLIT_SELECT_EVENT = Pattern.compile("enterSplitSelect");
    static final Pattern SPLIT_START_EVENT = Pattern.compile("launchSplitTasks");
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

    /**
     * Returns the height of the visible task, or the combined height of two tasks in split with a
     * divider between.
     */
    int getVisibleHeight() {
        if (isTaskSplit()) {
            return getCombinedSplitTaskHeight();
        }

        UiObject2 taskSnapshot1 = findObjectInTask(DEFAULT.snapshotRes);
        return taskSnapshot1.getVisibleBounds().height();
    }

    /**
     * Calculates the visible height for split tasks, containing 2 snapshot tiles and a divider.
     */
    private int getCombinedSplitTaskHeight() {
        UiObject2 taskSnapshot1 = findObjectInTask(SPLIT_TOP_OR_LEFT.snapshotRes);
        UiObject2 taskSnapshot2 = findObjectInTask(SPLIT_BOTTOM_OR_RIGHT.snapshotRes);

        // If the split task is partly off screen, taskSnapshot1 can be invisible.
        if (taskSnapshot1 == null) {
            return taskSnapshot2.getVisibleBounds().height();
        }

        int top = Math.min(
                taskSnapshot1.getVisibleBounds().top, taskSnapshot2.getVisibleBounds().top);
        int bottom = Math.max(
                taskSnapshot1.getVisibleBounds().bottom, taskSnapshot2.getVisibleBounds().bottom);

        return bottom - top;
    }

    /**
     * Returns the width of the visible task, or the combined width of two tasks in split with a
     * divider between.
     */
    int getVisibleWidth() {
        if (isTaskSplit()) {
            return getCombinedSplitTaskWidth();
        }

        UiObject2 taskSnapshot1 = findObjectInTask(DEFAULT.snapshotRes);
        return taskSnapshot1.getVisibleBounds().width();
    }

    /**
     * Calculates the visible width for split tasks, containing 2 snapshot tiles and a divider.
     */
    private int getCombinedSplitTaskWidth() {
        UiObject2 taskSnapshot1 = findObjectInTask(SPLIT_TOP_OR_LEFT.snapshotRes);
        UiObject2 taskSnapshot2 = findObjectInTask(SPLIT_BOTTOM_OR_RIGHT.snapshotRes);

        int left = Math.min(
                taskSnapshot1.getVisibleBounds().left, taskSnapshot2.getVisibleBounds().left);
        int right = Math.max(
                taskSnapshot1.getVisibleBounds().right, taskSnapshot2.getVisibleBounds().right);

        return right - left;
    }

    int getTaskCenterX() {
        return mTask.getVisibleCenter().x;
    }

    int getTaskCenterY() {
        return mTask.getVisibleCenter().y;
    }

    float getExactCenterX() {
        return mTask.getVisibleBounds().exactCenterX();
    }

    UiObject2 getUiObject() {
        return mTask;
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
            List<Integer> originalTasksCenterX =
                    getCurrentTasksCenterXList().stream().sorted().toList();
            boolean isClearAllVisibleBeforeDismiss = mOverview.isClearAllVisible();

            dismissBySwipingUp();

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer("dismissed")) {
                if (taskWasFocused) {
                    mLauncher.assertNotNull("No task became focused",
                            mOverview.getFocusedTaskForTablet());
                }
                if (!isClearAllVisibleBeforeDismiss) {
                    List<Integer> currentTasksCenterX =
                            getCurrentTasksCenterXList().stream().sorted().toList();
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
        final int centerY = taskBounds.bottom - 1;
        mLauncher.executeAndWaitForLauncherEvent(
                () -> mLauncher.linearGesture(centerX, centerY, centerX, 0, 10, false,
                        LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER),
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
     * Clicks the task.
     */
    public LaunchedAppState open() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            verifyActiveContainer();
            mLauncher.executeAndWaitForLauncherStop(
                    () -> mLauncher.clickLauncherObject(mTask),
                    "clicking an overview task");
            if (mOverview.getContainerType()
                    == LauncherInstrumentation.ContainerType.SPLIT_SCREEN_SELECT) {
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SPLIT_START_EVENT);

                try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                        "launched splitscreen")) {

                    BySelector divider = By.res(SYSTEMUI_PACKAGE, "docked_divider_handle");
                    mLauncher.waitForSystemUiObject(divider);
                    return new LaunchedAppState(mLauncher);
                }
            } else {
                Log.d(SUCCESSFUL_GESTURE_MISMATCH_EVENTS, "TaskView.launchTaskAnimated");
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, TASK_START_EVENT);
                return new LaunchedAppState(mLauncher);
            }
        }
    }

    /** Taps the task menu. Returns the task menu object. */
    @NonNull
    public OverviewTaskMenu tapMenu() {
        return tapMenu(DEFAULT);
    }

    /** Taps the task menu of the split task. Returns the split task's menu object. */
    @NonNull
    public OverviewTaskMenu tapMenu(OverviewSplitTask task) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to tap the task menu")) {
            mLauncher.clickLauncherObject(
                    mLauncher.waitForObjectInContainer(mTask, task.iconAppRes));

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "tapped the task menu")) {
                return new OverviewTaskMenu(mLauncher);
            }
        }
    }

    boolean isTaskSplit() {
        return findObjectInTask(SPLIT_BOTTOM_OR_RIGHT.snapshotRes) != null;
    }

    private UiObject2 findObjectInTask(String resName) {
        return mTask.findObject(mLauncher.getOverviewObjectSelector(resName));
    }

    /**
     * Returns whether the given String is contained in this Task's contentDescription. Also returns
     * true if both Strings are null.
     *
     * TODO(b/326565120): remove Nullable support once the bug causing it to be null is fixed.
     */
    public boolean containsContentDescription(@Nullable String expected) {
        String actual = mTask.getContentDescription();
        if (actual == null && expected == null) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        return actual.contains(expected);
    }

    /**
     * Enum used to specify  which task is retrieved when it is a split task.
     */
    public enum OverviewSplitTask {
        // The main task when the task is not split.
        DEFAULT("snapshot", "icon"),
        // The first task in split task.
        SPLIT_TOP_OR_LEFT("snapshot", "icon"),
        // The second task in split task.
        SPLIT_BOTTOM_OR_RIGHT("bottomright_snapshot", "bottomRight_icon");

        public final String snapshotRes;
        public final String iconAppRes;

        OverviewSplitTask(String snapshotRes, String iconAppRes) {
            this.snapshotRes = snapshotRes;
            this.iconAppRes = iconAppRes;
        }
    }
}
