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
import android.support.annotation.NonNull;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiObject2;

import java.util.Collections;
import java.util.List;

/**
 * Overview pane.
 */
public final class Overview {
    private static final int DEFAULT_FLING_SPEED = 15000;

    private final Launcher mLauncher;

    Overview(Launcher launcher) {
        mLauncher = launcher;
        assertState();
    }

    /**
     * Asserts that we are in overview.
     *
     * @return Overview panel.
     */
    @NonNull
    private UiObject2 assertState() {
        return mLauncher.assertState(Launcher.State.OVERVIEW);
    }

    /**
     * Flings forward (left) and waits the fling's end.
     */
    public void flingForward() {
        final UiObject2 overview = assertState();
        overview.fling(Direction.LEFT, DEFAULT_FLING_SPEED);
        mLauncher.waitForIdle();
        assertState();
    }

    /**
     * Flings backward (right) and waits the fling's end.
     */
    public void flingBackward() {
        final UiObject2 overview = assertState();
        overview.fling(Direction.RIGHT, DEFAULT_FLING_SPEED);
        mLauncher.waitForIdle();
        assertState();
    }

    /**
     * Gets the current task in the carousel, or fails if the carousel is empty.
     *
     * @return the task in the middle of the visible tasks list.
     */
    @NonNull
    public OverviewTask getCurrentTask() {
        assertState();
        final List<UiObject2> taskViews = mLauncher.getDevice().findObjects(
                Launcher.getLauncherObjectSelector("snapshot"));
        mLauncher.assertNotEquals("Unable to find a task", 0, taskViews.size());

        // taskViews contains up to 3 task views: the 'main' (having the widest visible
        // part) one in the center, and parts of its right and left siblings. Find the
        // main task view by its width.
        final UiObject2 widestTask = Collections.max(taskViews,
                (t1, t2) -> Integer.compare(t1.getVisibleBounds().width(),
                        t2.getVisibleBounds().width()));

        return new OverviewTask(mLauncher, widestTask);
    }

    /**
     * Swipes up to All Apps.
     *
     * @return the App Apps object.
     */
    @NonNull
    public AllAppsFromOverview switchToAllApps() {
        assertState();

        // Swipe from the hotseat to near the top, e.g. 10% of the screen.
        final UiObject2 predictionRow = mLauncher.waitForLauncherObject(
                "prediction_row");
        final Point start = predictionRow.getVisibleCenter();
        final int endY = (int) (mLauncher.getDevice().getDisplayHeight() * 0.1f);
        mLauncher.swipe(
                start.x, start.y, start.x, endY, (start.y - endY) / 100); // 100 px/step

        return new AllAppsFromOverview(mLauncher);
    }
}
