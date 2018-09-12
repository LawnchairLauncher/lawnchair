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

import androidx.annotation.NonNull;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.TestProtocol;

/**
 * Operations on AllApps opened from Home. Also a parent for All Apps opened from Overview.
 */
public class AllApps extends LauncherInstrumentation.VisibleContainer {
    private static final int MAX_SCROLL_ATTEMPTS = 40;
    private static final int MIN_INTERACT_SIZE = 100;
    private static final int FLING_SPEED = 12000;

    private final int mHeight;

    AllApps(LauncherInstrumentation launcher) {
        super(launcher);
        final UiObject2 allAppsContainer = verifyActiveContainer();
        mHeight = allAppsContainer.getVisibleBounds().height();
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.ALL_APPS;
    }

    /**
     * Finds an icon. Fails if the icon doesn't exist. Scrolls the app list when needed to make
     * sure the icon is visible.
     *
     * @param appName name of the app.
     * @return The app.
     */
    @NonNull
    public AppIcon getAppIcon(String appName) {
        final UiObject2 allAppsContainer = verifyActiveContainer();
        final BySelector appIconSelector = AppIcon.getAppIconSelector(appName);
        if (!allAppsContainer.hasObject(appIconSelector)) {
            scrollBackToBeginning();
            int attempts = 0;
            while (!allAppsContainer.hasObject(appIconSelector) &&
                    allAppsContainer.scroll(Direction.DOWN, 0.8f)) {
                LauncherInstrumentation.assertTrue(
                        "Exceeded max scroll attempts: " + MAX_SCROLL_ATTEMPTS,
                        ++attempts <= MAX_SCROLL_ATTEMPTS);
                verifyActiveContainer();
            }
        }
        verifyActiveContainer();

        final UiObject2 appIcon = mLauncher.getObjectInContainer(allAppsContainer, appIconSelector);
        ensureIconVisible(appIcon, allAppsContainer);
        return new AppIcon(mLauncher, appIcon);
    }

    private void scrollBackToBeginning() {
        final UiObject2 allAppsContainer = verifyActiveContainer();
        final UiObject2 searchBox =
                mLauncher.waitForObjectInContainer(allAppsContainer, "search_container_all_apps");

        int attempts = 0;
        allAppsContainer.setGestureMargins(0, searchBox.getVisibleBounds().bottom + 1, 0, 5);

        for (int scroll = getScroll(allAppsContainer);
                scroll != 0;
                scroll = getScroll(allAppsContainer)) {
            LauncherInstrumentation.assertTrue("Negative scroll position", scroll > 0);

            LauncherInstrumentation.assertTrue(
                    "Exceeded max scroll attempts: " + MAX_SCROLL_ATTEMPTS,
                    ++attempts <= MAX_SCROLL_ATTEMPTS);

            allAppsContainer.scroll(Direction.UP, 1);
        }

        verifyActiveContainer();
    }

    private int getScroll(UiObject2 allAppsContainer) {
        return mLauncher.getAnswerFromLauncher(allAppsContainer, TestProtocol.GET_SCROLL_MESSAGE).
                getInt(TestProtocol.SCROLL_Y_FIELD, -1);
    }

    private void ensureIconVisible(UiObject2 appIcon, UiObject2 allAppsContainer) {
        final int appHeight = appIcon.getVisibleBounds().height();
        if (appHeight < MIN_INTERACT_SIZE) {
            // Try to figure out how much percentage of the container needs to be scrolled in order
            // to reveal the app icon to have the MIN_INTERACT_SIZE
            final float pct = Math.max(((float) (MIN_INTERACT_SIZE - appHeight)) / mHeight, 0.2f);
            allAppsContainer.scroll(Direction.DOWN, pct);
            mLauncher.waitForIdle();
            verifyActiveContainer();
        }
    }

    /**
     * Flings forward (down) and waits the fling's end.
     */
    public void flingForward() {
        final UiObject2 allAppsContainer = verifyActiveContainer();
        // Start the gesture in the center to avoid starting at elements near the top.
        allAppsContainer.setGestureMargins(0, 0, 0, mHeight / 2);
        allAppsContainer.fling(Direction.DOWN, FLING_SPEED);
        verifyActiveContainer();
    }

    /**
     * Flings backward (up) and waits the fling's end.
     */
    public void flingBackward() {
        final UiObject2 allAppsContainer = verifyActiveContainer();
        // Start the gesture in the center, for symmetry with forward.
        allAppsContainer.setGestureMargins(0, mHeight / 2, 0, 0);
        allAppsContainer.fling(Direction.UP, FLING_SPEED);
        verifyActiveContainer();
    }
}
