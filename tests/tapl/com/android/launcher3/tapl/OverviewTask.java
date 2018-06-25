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

import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

/**
 * A recent task in the overview panel carousel.
 */
public final class OverviewTask {
    private final Launcher mLauncher;
    private final UiObject2 mTask;

    OverviewTask(Launcher launcher, UiObject2 task) {
        mLauncher = launcher;
        assertState();
        mTask = task;
    }

    /**
     * Asserts that we are in overview.
     *
     * @return Overview panel.
     */
    private void assertState() {
        mLauncher.assertState(Launcher.State.OVERVIEW);
    }

    /**
     * Swipes the task up.
     */
    public void dismiss() {
        assertState();
        // Dismiss the task via flinging it up.
        mTask.fling(Direction.DOWN);
        mLauncher.waitForIdle();
    }

    /**
     * Clicks at the task.
     */
    public void open() {
        assertState();
        mLauncher.assertTrue("Launching task didn't open a new window: " +
                        mTask.getParent().getContentDescription(),
                mTask.clickAndWait(Until.newWindow(), Launcher.APP_LAUNCH_TIMEOUT_MS));
        mLauncher.assertState(Launcher.State.BACKGROUND);
    }
}
