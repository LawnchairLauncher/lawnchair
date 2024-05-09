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

import static android.view.KeyEvent.KEYCODE_ESCAPE;

import static com.android.launcher3.tapl.LauncherInstrumentation.TASKBAR_RES_ID;
import static com.android.launcher3.tapl.OverviewTask.TASK_START_EVENT;
import static com.android.launcher3.testing.shared.TestProtocol.NORMAL_STATE_ORDINAL;

import android.graphics.Rect;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Common overview panel for both Launcher and fallback recents
 */
public class BaseOverview extends LauncherInstrumentation.VisibleContainer {
    protected static final String TASK_RES_ID = "task";
    private static final Pattern EVENT_ALT_ESC_UP = Pattern.compile(
            "Key event: KeyEvent.*?action=ACTION_UP.*?keyCode=KEYCODE_ESCAPE.*?metaState=0");
    private static final Pattern EVENT_ENTER_DOWN = Pattern.compile(
            "Key event: KeyEvent.*?action=ACTION_DOWN.*?keyCode=KEYCODE_ENTER");
    private static final Pattern EVENT_ENTER_UP = Pattern.compile(
            "Key event: KeyEvent.*?action=ACTION_UP.*?keyCode=KEYCODE_ENTER");

    private static final int FLINGS_FOR_DISMISS_LIMIT = 40;

    BaseOverview(LauncherInstrumentation launcher) {
        super(launcher);
        verifyActiveContainer();
        verifyActionsViewVisibility();
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.FALLBACK_OVERVIEW;
    }

    /**
     * Flings forward (left) and waits the fling's end.
     */
    public void flingForward() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            flingForwardImpl();
        }
    }

    private void flingForwardImpl() {
        try (LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to fling forward in overview")) {
            LauncherInstrumentation.log("Overview.flingForward before fling");
            final UiObject2 overview = verifyActiveContainer();
            final int leftMargin =
                    mLauncher.getTargetInsets().left + mLauncher.getEdgeSensitivityWidth();
            mLauncher.scroll(overview, Direction.LEFT, new Rect(leftMargin + 1, 0, 0, 0), 20,
                    false);
            try (LauncherInstrumentation.Closable c2 =
                         mLauncher.addContextLayer("flung forwards")) {
                verifyActiveContainer();
                verifyActionsViewVisibility();
            }
        }
    }

    /**
     * Flings backward (right) and waits the fling's end.
     */
    public void flingBackward() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            flingBackwardImpl();
        }
    }

    private void flingBackwardImpl() {
        try (LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to fling backward in overview")) {
            LauncherInstrumentation.log("Overview.flingBackward before fling");
            final UiObject2 overview = verifyActiveContainer();
            final int rightMargin =
                    mLauncher.getTargetInsets().right + mLauncher.getEdgeSensitivityWidth();
            mLauncher.scroll(
                    overview, Direction.RIGHT, new Rect(0, 0, rightMargin + 1, 0), 20, false);
            try (LauncherInstrumentation.Closable c2 =
                         mLauncher.addContextLayer("flung backwards")) {
                verifyActiveContainer();
                verifyActionsViewVisibility();
            }
        }
    }

    private OverviewTask flingToFirstTask() {
        OverviewTask currentTask = getCurrentTask();

        while (mLauncher.getRealDisplaySize().x - currentTask.getUiObject().getVisibleBounds().right
                <= mLauncher.getOverviewPageSpacing()) {
            flingBackwardImpl();
            currentTask = getCurrentTask();
        }

        return currentTask;
    }

    /**
     * Dismissed all tasks by scrolling to Clear-all button and pressing it.
     */
    public void dismissAllTasks() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "dismissing all tasks")) {
            final BySelector clearAllSelector = mLauncher.getOverviewObjectSelector("clear_all");
            for (int i = 0;
                    i < FLINGS_FOR_DISMISS_LIMIT
                            && !verifyActiveContainer().hasObject(clearAllSelector);
                    ++i) {
                flingForwardImpl();
            }

            final Runnable clickClearAll = () -> mLauncher.clickLauncherObject(
                    mLauncher.waitForObjectInContainer(verifyActiveContainer(),
                            clearAllSelector));
            if (mLauncher.is3PLauncher()) {
                mLauncher.executeAndWaitForLauncherStop(
                        clickClearAll,
                        "clicking 'Clear All'");
            } else {
                mLauncher.runToState(
                        clickClearAll,
                        NORMAL_STATE_ORDINAL,
                        "clicking 'Clear All'");
            }

            mLauncher.waitUntilLauncherObjectGone(clearAllSelector);
        }
    }

    /**
     * Touch to the right of current task. This should dismiss overview and go back to Workspace.
     */
    public Workspace touchOutsideFirstTask() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "touching outside the focused task")) {

            if (getTaskCount() < 2) {
                throw new IllegalStateException(
                        "Need to have at least 2 tasks");
            }

            OverviewTask currentTask = flingToFirstTask();

            mLauncher.runToState(
                    () -> mLauncher.touchOutsideContainer(currentTask.getUiObject(),
                            /* tapRight= */ true,
                            /* halfwayToEdge= */ false),
                    NORMAL_STATE_ORDINAL,
                    "touching outside of first task");

            return new Workspace(mLauncher);
        }
    }

    /**
     * Touch between two tasks
     */
    public void touchBetweenTasks() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "touching outside the focused task")) {
            if (getTaskCount() < 2) {
                throw new IllegalStateException(
                        "Need to have at least 2 tasks");
            }

            OverviewTask currentTask = flingToFirstTask();

            mLauncher.touchOutsideContainer(currentTask.getUiObject(),
                    /* tapRight= */ false,
                    /* halfwayToEdge= */ false);
        }
    }

    /**
     * Touch either on the right or the left corner of the screen, 1 pixel from the bottom and
     * from the sides.
     */
    public void touchTaskbarBottomCorner(boolean tapRight) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            Taskbar taskbar = new Taskbar(mLauncher);
            if (mLauncher.isTransientTaskbar()) {
                mLauncher.runToState(
                        () -> taskbar.touchBottomCorner(tapRight),
                        NORMAL_STATE_ORDINAL,
                        "touching taskbar");
                // Tapping outside Transient Taskbar returns to Workspace, wait for that state.
                new Workspace(mLauncher);
            } else {
                taskbar.touchBottomCorner(tapRight);
                // Should stay in Overview.
                verifyActiveContainer();
                verifyActionsViewVisibility();
            }
        }
    }

    /**
     * Scrolls the current task via flinging forward until it is off screen.
     *
     * If only one task is present, it is only partially scrolled off screen and will still be
     * the current task.
     */
    public void scrollCurrentTaskOffScreen() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to scroll current task off screen in overview")) {
            verifyActiveContainer();

            OverviewTask task = getCurrentTask();
            mLauncher.assertNotNull("current task is null", task);
            mLauncher.scrollLeftByDistance(verifyActiveContainer(),
                    mLauncher.getRealDisplaySize().x - task.getUiObject().getVisibleBounds().left
                            + mLauncher.getOverviewPageSpacing());

            try (LauncherInstrumentation.Closable c2 =
                         mLauncher.addContextLayer("scrolled task off screen")) {
                verifyActiveContainer();
                verifyActionsViewVisibility();

                if (getTaskCount() > 1) {
                    if (mLauncher.isTablet()) {
                        mLauncher.assertTrue("current task is not grid height",
                                getCurrentTask().getVisibleHeight() == mLauncher
                                        .getGridTaskRectForTablet().height());
                    }
                    mLauncher.assertTrue("Current task not scrolled off screen",
                            !getCurrentTask().equals(task));
                }
            }
        }
    }

    /**
     * Gets the current task in the carousel, or fails if the carousel is empty.
     *
     * @return the task in the middle of the visible tasks list.
     */
    @NonNull
    public OverviewTask getCurrentTask() {
        final List<UiObject2> taskViews = getTasks();
        mLauncher.assertNotEquals("Unable to find a task", 0, taskViews.size());

        // The widest, and most top-right task should be the current task
        UiObject2 currentTask = Collections.max(taskViews,
                Comparator.comparingInt((UiObject2 t) -> t.getVisibleBounds().width())
                        .thenComparingInt((UiObject2 t) -> t.getVisibleCenter().x)
                        .thenComparing(Comparator.comparing(
                                (UiObject2 t) -> t.getVisibleCenter().y).reversed()));
        return new OverviewTask(mLauncher, currentTask, this);
    }

    /** Returns an overview task matching TestActivity {@param activityNumber}. */
    @NonNull
    public OverviewTask getTestActivityTask(int activityNumber) {
        final List<UiObject2> taskViews = getTasks();
        mLauncher.assertNotEquals("Unable to find a task", 0, taskViews.size());

        final String activityName = "TestActivity" + activityNumber;
        UiObject2 task = null;
        for (UiObject2 taskView : taskViews) {
            // TODO(b/239452415): Use equals instead of descEndsWith
            if (taskView.getParent().hasObject(By.descEndsWith(activityName))) {
                task = taskView;
                break;
            }
        }
        mLauncher.assertNotNull(
                "Unable to find a task with " + activityName + " from the task list", task);

        return new OverviewTask(mLauncher, task, this);
    }

    /**
     * Returns a list of all tasks fully visible in the tablet grid overview.
     */
    @NonNull
    public List<OverviewTask> getCurrentTasksForTablet() {
        final List<UiObject2> taskViews = getTasks();
        mLauncher.assertNotEquals("Unable to find a task", 0, taskViews.size());

        final int gridTaskWidth = mLauncher.getGridTaskRectForTablet().width();

        return taskViews.stream().filter(t -> t.getVisibleBounds().width() == gridTaskWidth).map(
                t -> new OverviewTask(mLauncher, t, this)).collect(Collectors.toList());
    }

    @NonNull
    private List<UiObject2> getTasks() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get overview tasks")) {
            verifyActiveContainer();
            return mLauncher.getDevice().findObjects(
                    mLauncher.getOverviewObjectSelector(TASK_RES_ID));
        }
    }


    int getTaskCount() {
        return getTasks().size();
    }

    /**
     * Returns whether Overview has tasks.
     */
    public boolean hasTasks() {
        return getTasks().size() > 0;
    }

    /**
     * Gets Overview Actions.
     *
     * @return The Overview Actions
     */
    @NonNull
    public OverviewActions getOverviewActions() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get overview actions")) {
            verifyActiveContainer();
            UiObject2 overviewActions = mLauncher.waitForOverviewObject("action_buttons");
            return new OverviewActions(overviewActions, mLauncher);
        }
    }

    /**
     * Gets Overview Actions specific to grouped tasks.
     *
     * @return The Overview group actions bar
     */
    @NonNull
    public OverviewActions getOverviewGroupActions() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get overview group actions")) {
            verifyActiveContainer();
            UiObject2 groupActions = mLauncher.waitForOverviewObject("group_action_buttons");
            return new OverviewActions(groupActions, mLauncher);
        }
    }

    /**
     * Returns if clear all button is visible.
     */
    public boolean isClearAllVisible() {
        return verifyActiveContainer().hasObject(
                mLauncher.getOverviewObjectSelector("clear_all"));
    }

    /**
     * Returns the taskbar if it's a tablet, or {@code null} otherwise.
     */
    @Nullable
    public Taskbar getTaskbar() {
        if (!mLauncher.isTablet()) {
            return null;
        }
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get the taskbar")) {
            mLauncher.waitForSystemLauncherObject(TASKBAR_RES_ID);

            return new Taskbar(mLauncher);
        }
    }

    protected boolean isActionsViewVisible() {
        if (!hasTasks() || isClearAllVisible()) {
            return false;
        }
        boolean isTablet = mLauncher.isTablet();
        if (isTablet && mLauncher.isGridOnlyOverviewEnabled()) {
            return false;
        }
        OverviewTask task = isTablet ? getFocusedTaskForTablet() : getCurrentTask();
        if (task == null) {
            return false;
        }
        // In tablets, if focused task is not in center, overview actions aren't visible.
        if (isTablet && Math.abs(task.getExactCenterX() - mLauncher.getExactScreenCenterX()) >= 1) {
            return false;
        }
        if (task.isTaskSplit() && (!mLauncher.isAppPairsEnabled() || !isTablet)) {
            // Overview actions aren't visible for split screen tasks, except for save app pair
            // button on tablets.
            return false;
        }
        return true;
    }

    /**
     * Presses the esc key to dismiss Overview.
     */
    public Workspace dismissByEscKey() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, EVENT_ALT_ESC_UP);
            mLauncher.runToState(
                    () -> mLauncher.getDevice().pressKeyCode(KEYCODE_ESCAPE),
                    NORMAL_STATE_ORDINAL, "pressing esc key");
            try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                    "pressed esc key")) {
                return mLauncher.getWorkspace();
            }
        }
    }

    /**
     * Presses the enter key to launch the focused task
     * <p>
     * If no task is focused, this will fail.
     */
    public LaunchedAppState launchFocusedTaskByEnterKey(@NonNull String expectedPackageName) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, EVENT_ENTER_UP);
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, TASK_START_EVENT);

            mLauncher.executeAndWaitForLauncherStop(
                    () -> mLauncher.assertTrue(
                            "Failed to press enter",
                            mLauncher.getDevice().pressKeyCode(KeyEvent.KEYCODE_ENTER)),
                    "pressing enter");
            mLauncher.assertAppLaunched(expectedPackageName);

            try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                    "pressed enter")) {
                return new LaunchedAppState(mLauncher);
            }
        }
    }

    private void verifyActionsViewVisibility() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to assert overview actions view visibility")) {
            boolean isTablet = mLauncher.isTablet();
            OverviewTask task = isTablet ? getFocusedTaskForTablet() : getCurrentTask();

            if (isActionsViewVisible()) {
                if (task.isTaskSplit()) {
                    mLauncher.waitForOverviewObject("group_action_buttons");
                } else {
                    mLauncher.waitForOverviewObject("action_buttons");
                }
            } else {
                mLauncher.waitUntilOverviewObjectGone("action_buttons");
                mLauncher.waitUntilOverviewObjectGone("group_action_buttons");
            }
        }
    }

    /**
     * Returns Overview focused task if it exists.
     *
     * @throws IllegalStateException if not run on a tablet device.
     */
    OverviewTask getFocusedTaskForTablet() {
        if (!mLauncher.isTablet()) {
            throw new IllegalStateException("Must be run on tablet device.");
        }
        final List<UiObject2> taskViews = getTasks();
        if (taskViews.size() == 0) {
            return null;
        }
        int focusedTaskHeight = mLauncher.getFocusedTaskHeightForTablet();
        for (UiObject2 task : taskViews) {
            OverviewTask overviewTask = new OverviewTask(mLauncher, task, this);

            if (overviewTask.getVisibleHeight() == focusedTaskHeight) {
                return overviewTask;
            }
        }
        return null;
    }
}
