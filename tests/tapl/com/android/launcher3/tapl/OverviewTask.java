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

import static com.android.launcher3.tapl.OverviewTask.OverviewTaskContainer.DEFAULT;
import static com.android.launcher3.tapl.OverviewTask.OverviewTaskContainer.DESKTOP;
import static com.android.launcher3.tapl.OverviewTask.OverviewTaskContainer.SPLIT_BOTTOM_OR_RIGHT;
import static com.android.launcher3.tapl.OverviewTask.OverviewTaskContainer.SPLIT_TOP_OR_LEFT;

import android.graphics.Rect;

import androidx.annotation.NonNull;
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
    static final Pattern TASK_START_EVENT_DESKTOP = Pattern.compile("launchDesktopFromRecents");
    static final Pattern TASK_START_EVENT_LIVE_TILE = Pattern.compile(
            "composeRecentsLaunchAnimator");
    static final Pattern SPLIT_SELECT_EVENT = Pattern.compile("enterSplitSelect");
    static final Pattern SPLIT_START_EVENT = Pattern.compile("launchSplitTasks");
    private final LauncherInstrumentation mLauncher;
    @NonNull
    private final UiObject2 mTask;
    private final TaskViewType mType;
    private final BaseOverview mOverview;

    OverviewTask(LauncherInstrumentation launcher, @NonNull UiObject2 task, BaseOverview overview) {
        mLauncher = launcher;
        mLauncher.assertNotNull("task must not be null", task);
        mTask = task;
        mOverview = overview;
        mType = getType(task);
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
        if (isGrouped()) {
            return getCombinedSplitTaskHeight();
        }

        return getTaskSnapshot(isDesktop() ? DESKTOP : DEFAULT).getVisibleBounds().height();
    }

    /**
     * Calculates the visible height for split tasks, containing 2 snapshot tiles and a divider.
     */
    private int getCombinedSplitTaskHeight() {
        UiObject2 taskSnapshot1 = getTaskSnapshot(SPLIT_TOP_OR_LEFT);
        UiObject2 taskSnapshot2 = getTaskSnapshot(SPLIT_BOTTOM_OR_RIGHT);

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

    public int getTaskCenterX() {
        return mTask.getVisibleCenter().x;
    }

    public int getTaskCenterY() {
        return mTask.getVisibleCenter().y;
    }

    float getExactCenterX() {
        return mTask.getVisibleBounds().exactCenterX();
    }

    UiObject2 getUiObject() {
        return mTask;
    }

    /**
     * Returns the task snapshot (thumbnail) for the given `OverviewTaskContainer`.
     * If there are no `taskContentView`'s, then the `enableRefactorTaskContentView` feature flag is
     * off, in that case fallback to the `snapshotViewRes` id.
     */
    private UiObject2 getTaskSnapshot(OverviewTaskContainer overviewTaskContainer) {
        UiObject2 taskContentView = mTask.findObject(
                mLauncher.getOverviewObjectSelector(overviewTaskContainer.taskContentViewRes));
        if (taskContentView != null) {
            BySelector snapshotSelector = mLauncher.getOverviewObjectSelector("snapshot");
            UiObject2 snapshot = mTask.findObject(snapshotSelector);
            if (snapshot != null) {
                return snapshot;
            }
        }

        return mTask.findObject(
                mLauncher.getOverviewObjectSelector(overviewTaskContainer.snapshotViewRes));
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

            boolean taskWasFocused = mLauncher.isTablet()
                    && !isDesktop()
                    && getVisibleHeight() == mLauncher.getOverviewTaskSize().height();
            List<Integer> originalTasksCenterX =
                    getCurrentTasksCenterXList().stream().sorted().toList();
            boolean isClearAllVisibleBeforeDismiss = mOverview.isClearAllVisible();

            dismissBySwipingUp();

            long numNonDesktopTasks = mOverview.getCurrentTasksForTablet()
                    .stream().filter(t -> !t.isDesktop()).count();

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer("dismissed")) {
                if (taskWasFocused && numNonDesktopTasks > 0) {
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
        final int centerY = taskBounds.centerY();
        // Magnetic detach interpolates during the attached region with y = 0.3x. We must account
        // for this in the dismiss length to ensure the task is dragged far enough to dismiss.
        int magneticDetachLength = mLauncher.getMagneticDetachThreshold();
        int lengthTaskWillTravel =
                (int) ((magneticDetachLength * 0.3f) + (centerY - magneticDetachLength));
        int minimumDismissLength = taskBounds.bottom / 2;
        int extraDismissLength = Math.max(minimumDismissLength - lengthTaskWillTravel, 0);
        // Bound touch to a max of the bottom of the task, account for extra required dismiss length
        final int startY = Math.min(centerY + extraDismissLength, taskBounds.bottom);
        mLauncher.executeAndWaitForLauncherEvent(
                () -> mLauncher.linearGesture(centerX, startY, centerX, 0, 10, false,
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
     * Starts dismissing the task by swiping up, then cancels, and task springs back to start.
     */
    public void dismissCancel() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to start dismissing an overview task then cancel")) {
            verifyActiveContainer();
            int taskCountBeforeDismiss = mOverview.getTaskCount();
            mLauncher.assertNotEquals("Unable to find a task", 0, taskCountBeforeDismiss);

            final Rect taskBounds = mLauncher.getVisibleBounds(mTask);
            final int centerX = taskBounds.centerX();
            final int centerY = taskBounds.centerY();
            final int endCenterY = centerY - (taskBounds.height() / 4);
            mLauncher.executeAndWaitForLauncherEvent(
                    // Set slowDown to true so we do not fling the task at the end of the drag, as
                    // we want it to cancel and return back to the origin.
                    () -> mLauncher.linearGesture(centerX, centerY, centerX, endCenterY,
                            /* steps= */ 10, /* slowDown= */ true,
                            LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER),
                    event -> TestProtocol.DISMISS_ANIMATION_ENDS_MESSAGE.equals(
                            event.getClassName()),
                    () -> "Canceling swipe to dismiss did not end with task at origin.",
                    "cancel swiping to dismiss");

        }
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
                final Pattern event;
                if (mOverview.isLiveTile(mTask)) {
                    event = TASK_START_EVENT_LIVE_TILE;
                } else if (mType == TaskViewType.DESKTOP) {
                    event = TASK_START_EVENT_DESKTOP;
                } else {
                    event = TASK_START_EVENT;
                }
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, event);

                if (mType == TaskViewType.DESKTOP) {
                    try (LauncherInstrumentation.Closable ignored = mLauncher.addContextLayer(
                            "launched desktop")) {
                        mLauncher.waitForSystemUiObject("desktop_mode_caption");
                    }
                }
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
    public OverviewTaskMenu tapMenu(OverviewTaskContainer task) {
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

    /**
     * Returns whether the given String is contained in this Task's contentDescription. Also returns
     * true if both Strings are null.
     */
    public boolean containsContentDescription(String expected,
            OverviewTaskContainer overviewTaskContainer) {
        String actual = getTaskSnapshot(overviewTaskContainer).getContentDescription();
        if (actual == null && expected == null) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        return actual.contains(expected);
    }

    /**
     * Returns whether the given String is contained in this Task's contentDescription. Also returns
     * true if both Strings are null
     */
    public boolean containsContentDescription(String expected) {
        return containsContentDescription(expected, DEFAULT);
    }

    /**
     * Returns the TaskView type of the task. It will return whether the task is a single TaskView,
     * a GroupedTaskView or a DesktopTaskView.
     */
    static TaskViewType getType(UiObject2 task) {
        String resourceName = task.getResourceName();
        if (resourceName.endsWith("task_view_grouped")) {
            return TaskViewType.GROUPED;
        } else if (resourceName.endsWith("task_view_desktop")) {
            return TaskViewType.DESKTOP;
        } else {
            return TaskViewType.SINGLE;
        }
    }

    boolean isGrouped() {
        return mType == TaskViewType.GROUPED;
    }

    public boolean isDesktop() {
        return mType == TaskViewType.DESKTOP;
    }

    /**
     * Enum used to specify which resource name should be used depending on the type of the task.
     */
    public enum OverviewTaskContainer {
        // The main task when the task is not split.
        DEFAULT("task_content_view", "snapshot", "icon"),
        // The first task in split task.
        SPLIT_TOP_OR_LEFT("task_content_view", "snapshot", "icon"),
        // The second task in split task.
        SPLIT_BOTTOM_OR_RIGHT("bottomright_task_content_view", "bottomright_snapshot",
                "bottomRight_icon"),
        // The desktop task.
        DESKTOP("background", "background", "icon");

        public final String taskContentViewRes;
        // TODO (b/409248525) Delete `snapshotViewRes` when cleaning up
        //  enableRefactorTaskContentView flag.
        public final String snapshotViewRes;
        public final String iconAppRes;

        OverviewTaskContainer(String taskContentViewRes, String snapshotViewRes,
                String iconAppRes) {
            this.taskContentViewRes = taskContentViewRes;
            this.snapshotViewRes = snapshotViewRes;
            this.iconAppRes = iconAppRes;
        }
    }

    enum TaskViewType {
        SINGLE,
        GROUPED,
        DESKTOP
    }
}
