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
import static com.android.launcher3.tapl.LauncherInstrumentation.log;
import static com.android.launcher3.tapl.OverviewTask.TASK_START_EVENT;
import static com.android.launcher3.tapl.TestHelpers.getOverviewPackageName;
import static com.android.launcher3.testing.shared.TestProtocol.NORMAL_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.testLogD;

import android.graphics.Rect;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Common overview panel for both Launcher and fallback recents
 */
public class BaseOverview extends LauncherInstrumentation.VisibleContainer {
    private static final String TAG = "BaseOverview";
    protected static final BySelector TASK_SELECTOR = By.res(Pattern.compile(
            getOverviewPackageName()
                    + ":id/(task_view_single|task_view_grouped|task_view_desktop)"));
    private static final Pattern EVENT_ALT_ESC_UP = Pattern.compile(
            "Key event: KeyEvent.*?action=ACTION_UP.*?keyCode=KEYCODE_ESCAPE.*?metaState=0");
    private static final Pattern EVENT_ENTER_DOWN = Pattern.compile(
            "Key event: KeyEvent.*?action=ACTION_DOWN.*?keyCode=KEYCODE_ENTER");
    private static final Pattern EVENT_ENTER_UP = Pattern.compile(
            "Key event: KeyEvent.*?action=ACTION_UP.*?keyCode=KEYCODE_ENTER");

    private static final int FLINGS_FOR_DISMISS_LIMIT = 40;

    private final @Nullable UiObject2 mLiveTileTask;


    BaseOverview(LauncherInstrumentation launcher) {
        this(launcher, /*launchedFromApp=*/false);
    }

    BaseOverview(LauncherInstrumentation launcher, boolean launchedFromApp) {
        super(launcher);
        verifyActiveContainer();
        verifyActionsViewVisibility();
        if (launchedFromApp) {
            mLiveTileTask = getCurrentTaskUnchecked();
        } else {
            mLiveTileTask = null;
        }
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
            log("Overview.flingForward before fling");
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
            log("Overview.flingBackward before fling");
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
            flingForwardUntilClearAllVisibleImpl();

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
     * Scrolls until Clear-all button is visible.
     */
    public void flingForwardUntilClearAllVisible() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            flingForwardUntilClearAllVisibleImpl();
        }
    }

    private void flingForwardUntilClearAllVisibleImpl() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "flinging forward to clear all")) {
            final BySelector clearAllSelector = mLauncher.getOverviewObjectSelector("clear_all");
            for (int i = 0; i < FLINGS_FOR_DISMISS_LIMIT && !verifyActiveContainer().hasObject(
                    clearAllSelector); ++i) {
                flingForwardImpl();
            }
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
                                        .getOverviewGridTaskSize().height());
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
        UiObject2 currentTask = getCurrentTaskUnchecked();
        mLauncher.assertNotNull("Unable to find a task", currentTask);
        return new OverviewTask(mLauncher, currentTask, this);
    }

    @Nullable
    private UiObject2 getCurrentTaskUnchecked() {
        final List<UiObject2> taskViews = getTasks();
        if (taskViews.isEmpty()) {
            return null;
        }

        // The widest, and most top-right task should be the current task
        return Collections.max(taskViews,
                Comparator.comparingInt((UiObject2 t) -> t.getVisibleBounds().width())
                        .thenComparingInt((UiObject2 t) -> t.getVisibleCenter().x)
                        .thenComparing(Comparator.comparing(
                                (UiObject2 t) -> t.getVisibleCenter().y).reversed()));
    }

    /**
     * Returns an overview task that contains the specified test activity in its thumbnails.
     *
     * @param activityIndex index of TestActivity to match against
     */
    @NonNull
    public OverviewTask getTestActivityTask(int activityIndex) {
        return getTestActivityTask(Collections.singleton(activityIndex));
    }

    /**
     * Returns an overview task that contains all the specified test activities in its thumbnails.
     *
     * @param activityNumbers collection of indices of TestActivity to match against
     */
    @NonNull
    public OverviewTask getTestActivityTask(Collection<Integer> activityNumbers) {
        final List<UiObject2> taskViews = getTasks();
        mLauncher.assertNotEquals("Unable to find a task", 0, taskViews.size());

        Optional<UiObject2> task = taskViews.stream().filter(
                taskView -> activityNumbers.stream().allMatch(activityNumber ->
                    // TODO(b/239452415): Use equals instead of descEndsWith
                    taskView.hasObject(By.descEndsWith("TestActivity" + activityNumber))
                )).findFirst();

        mLauncher.assertTrue("Unable to find a task with test activities " + activityNumbers
                + " from the task list", task.isPresent());

        return new OverviewTask(mLauncher, task.get(), this);
    }

    /**
     * Returns a list of all tasks fully visible in the tablet grid overview.
     */
    @NonNull
    public List<OverviewTask> getCurrentTasksForTablet() {
        final List<UiObject2> taskViews = getTasks();
        mLauncher.assertNotEquals("Unable to find a task", 0, taskViews.size());

        final int gridTaskWidth = mLauncher.getOverviewGridTaskSize().width();

        return taskViews.stream().filter(t -> t.getVisibleBounds().width() == gridTaskWidth).map(
                t -> new OverviewTask(mLauncher, t, this)).collect(Collectors.toList());
    }

    @NonNull
    private List<UiObject2> getTasks() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get overview tasks")) {
            verifyActiveContainer();
            return mLauncher.getDevice().findObjects(TASK_SELECTOR);
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
            testLogD(TAG, "Not expecting an actions bar: no tasks/'Clear all' is visible");
            return false;
        }
        boolean isTablet = mLauncher.isTablet();
        if (isTablet && mLauncher.isGridOnlyOverviewEnabled()) {
            testLogD(TAG, "Not expecting an actions bar: device is tablet with grid-only Overview");
            return false;
        }
        OverviewTask task = isTablet ? getFocusedTaskForTablet() : getCurrentTask();
        if (task == null) {
            testLogD(TAG, "Not expecting an actions bar: no current task");
            return false;
        }
        // In tablets, if focused task is not in center, overview actions aren't visible.
        if (isTablet && Math.abs(task.getExactCenterX() - mLauncher.getExactScreenCenterX()) >= 1) {
            testLogD(TAG,
                    "Not expecting an actions bar: device is tablet and task is not centered");
            return false;
        }
        if (task.isGrouped() && !isTablet) {
            testLogD(TAG, "Not expecting an actions bar: device is phone and task is split");
            // Overview actions aren't visible for split screen tasks, except for save app pair
            // button on tablets.
            return false;
        }
        testLogD(TAG, "Expecting an actions bar");
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
        // If no running tasks, no need to verify actions view visibility.
        if (getTasks().isEmpty()) {
            return;
        }

        boolean isTablet = mLauncher.isTablet();
        OverviewTask task = isTablet ? getFocusedTaskForTablet() : getCurrentTask();

        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to assert overview actions view visibility="
                        + isActionsViewVisible()
                        + ", focused task is "
                        + (task == null ? "null" : (task.isGrouped() ? "split" : "not split"))
                )) {

            if (isActionsViewVisible()) {
                if (task.isGrouped()) {
                    mLauncher.waitForOverviewObject("action_save_app_pair");
                } else {
                    mLauncher.waitForOverviewObject("action_buttons");
                }
            } else {
                mLauncher.waitUntilOverviewObjectGone("action_buttons");
                mLauncher.waitUntilOverviewObjectGone("action_save_app_pair");
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
        if (taskViews.isEmpty()) {
            return null;
        }
        Rect focusTaskSize = mLauncher.getOverviewTaskSize();
        int focusedTaskHeight = focusTaskSize.height();
        for (UiObject2 task : taskViews) {
            OverviewTask overviewTask = new OverviewTask(mLauncher, task, this);
            // Desktop tasks can't be focused tasks, but are the same size.
            if (overviewTask.isDesktop()) {
                continue;
            }
            if (overviewTask.getVisibleHeight() == focusedTaskHeight) {
                return overviewTask;
            }
        }
        return null;
    }

    protected boolean isLiveTile(UiObject2 task) {
        // UiObject2.equals returns false even when mLiveTileTask and task have the same node, hence
        // compare only hashCode as a workaround.
        return mLiveTileTask != null && mLiveTileTask.hashCode() == task.hashCode();
    }
}
