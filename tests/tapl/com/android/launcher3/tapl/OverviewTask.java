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

/**
 * A recent task in the overview panel carousel.
 */
public final class OverviewTask {
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
     * Swipes the task up.
     */
    public void dismiss() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to dismiss a task")) {
            verifyActiveContainer();
            // Dismiss the task via flinging it up.
            final Rect taskBounds = mTask.getVisibleBounds();
            final int centerX = taskBounds.centerX();
            final int centerY = taskBounds.centerY();
            mLauncher.linearGesture(centerX, centerY, centerX, 0, 10);
            mLauncher.waitForIdle();
        }
    }

    /**
     * Clicks at the task.
     */
    public Background open() {
        verifyActiveContainer();
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "clicking an overview task")) {
            mLauncher.executeAndWaitForEvent(
                    () -> mTask.click(),
                    event -> event.getEventType() == TYPE_WINDOW_STATE_CHANGED,
                    "Launching task didn't open a new window: " +
                            mTask.getParent().getContentDescription());
        }
        return new Background(mLauncher);
    }
}
