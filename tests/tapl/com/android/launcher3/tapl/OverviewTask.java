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

import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

/**
 * A recent task in the overview panel carousel.
 */
public final class OverviewTask {
    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mTask;
    private final Overview mOverview;

    OverviewTask(LauncherInstrumentation launcher, UiObject2 task, Overview overview) {
        mLauncher = launcher;
        mTask = task;
        mOverview = overview;
        verifyActiveContainer();
    }

    private void verifyActiveContainer() {
        mOverview.verifyActiveContainer();
    }

    /**
     * Swipes the task up.
     */
    public void dismiss() {
        verifyActiveContainer();
        // Dismiss the task via flinging it up.
        mTask.fling(Direction.DOWN);
        mLauncher.waitForIdle();
    }

    /**
     * Clicks at the task.
     */
    public Background open() {
        verifyActiveContainer();
        LauncherInstrumentation.assertTrue("Launching task didn't open a new window: " +
                        mTask.getParent().getContentDescription(),
                mTask.clickAndWait(Until.newWindow(), LauncherInstrumentation.WAIT_TIME_MS));
        return new Background(mLauncher);
    }
}
