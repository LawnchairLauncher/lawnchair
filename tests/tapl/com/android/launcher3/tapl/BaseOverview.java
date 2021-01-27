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

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import java.util.Collections;
import java.util.List;

/**
 * Common overview panel for both Launcher and fallback recents
 */
public class BaseOverview extends LauncherInstrumentation.VisibleContainer {
    private static final int FLINGS_FOR_DISMISS_LIMIT = 40;

    BaseOverview(LauncherInstrumentation launcher) {
        super(launcher);
        verifyActiveContainer();
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
            final int leftMargin = mLauncher.getTargetInsets().left;
            mLauncher.scroll(
                    overview, Direction.LEFT, new Rect(leftMargin + 1, 0, 0, 0), 20, false);
            verifyActiveContainer();
        }
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

            mLauncher.clickLauncherObject(
                    mLauncher.waitForObjectInContainer(verifyActiveContainer(), clearAllSelector));
        }
    }

    /**
     * Flings backward (right) and waits the fling's end.
     */
    public void flingBackward() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to fling backward in overview")) {
            LauncherInstrumentation.log("Overview.flingBackward before fling");
            final UiObject2 overview = verifyActiveContainer();
            final int rightMargin = mLauncher.getTargetInsets().right;
            mLauncher.scroll(
                    overview, Direction.RIGHT, new Rect(0, 0, rightMargin + 1, 0), 20, false);
            verifyActiveContainer();
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

        // taskViews contains up to 3 task views: the 'main' (having the widest visible part) one
        // in the center, and parts of its right and left siblings. Find the main task view by
        // its width.
        final UiObject2 widestTask = Collections.max(taskViews,
                (t1, t2) -> Integer.compare(mLauncher.getVisibleBounds(t1).width(),
                        mLauncher.getVisibleBounds(t2).width()));

        return new OverviewTask(mLauncher, widestTask, this);
    }

    @NonNull
    private List<UiObject2> getTasks() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get overview tasks")) {
            verifyActiveContainer();
            return mLauncher.getDevice().findObjects(
                    mLauncher.getOverviewObjectSelector("snapshot"));
        }
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
            UiObject2 overviewActions = mLauncher.waitForLauncherObject("action_buttons");
            return new OverviewActions(overviewActions, mLauncher);
        }
    }
}