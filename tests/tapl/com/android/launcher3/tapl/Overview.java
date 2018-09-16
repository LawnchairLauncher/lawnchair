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

import android.graphics.Point;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import java.util.Collections;
import java.util.List;

/**
 * Overview pane.
 */
public final class Overview extends LauncherInstrumentation.VisibleContainer {
    private static final int DEFAULT_FLING_SPEED = 15000;

    Overview(LauncherInstrumentation launcher) {
        super(launcher);
        verifyActiveContainer();
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.OVERVIEW;
    }

    /**
     * Flings forward (left) and waits the fling's end.
     */
    public void flingForward() {
        final UiObject2 overview = verifyActiveContainer();
        LauncherInstrumentation.log("Overview.flingForward before fling");
        overview.fling(Direction.LEFT, DEFAULT_FLING_SPEED);
        mLauncher.waitForIdle();
        verifyActiveContainer();
    }

    /**
     * Flings backward (right) and waits the fling's end.
     */
    public void flingBackward() {
        final UiObject2 overview = verifyActiveContainer();
        LauncherInstrumentation.log("Overview.flingBackward before fling");
        overview.fling(Direction.RIGHT, DEFAULT_FLING_SPEED);
        mLauncher.waitForIdle();
        verifyActiveContainer();
    }

    /**
     * Gets the current task in the carousel, or fails if the carousel is empty.
     *
     * @return the task in the middle of the visible tasks list.
     */
    @NonNull
    public OverviewTask getCurrentTask() {
        verifyActiveContainer();
        final List<UiObject2> taskViews = mLauncher.getDevice().findObjects(
                LauncherInstrumentation.getLauncherObjectSelector("snapshot"));
        LauncherInstrumentation.assertNotEquals("Unable to find a task", 0, taskViews.size());

        // taskViews contains up to 3 task views: the 'main' (having the widest visible
        // part) one in the center, and parts of its right and left siblings. Find the
        // main task view by its width.
        final UiObject2 widestTask = Collections.max(taskViews,
                (t1, t2) -> Integer.compare(t1.getVisibleBounds().width(),
                        t2.getVisibleBounds().width()));

        return new OverviewTask(mLauncher, widestTask, this);
    }

    /**
     * Swipes up to All Apps.
     *
     * @return the App Apps object.
     */
    @NonNull
    public AllAppsFromOverview switchToAllApps() {
        verifyActiveContainer();

        // Swipe from navbar to the top.
        final UiObject2 navBar = mLauncher.getSystemUiObject("navigation_bar_frame");
        final Point start = navBar.getVisibleCenter();
        LauncherInstrumentation.log("Overview.switchToAllApps before swipe");
        mLauncher.swipe(start.x, start.y, start.x, 0);

        return new AllAppsFromOverview(mLauncher);
    }
}
