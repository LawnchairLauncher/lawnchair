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
import android.graphics.Rect;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.ResourceUtils;
import com.android.launcher3.testing.TestProtocol;

/**
 * Operations on AllApps opened from Home. Also a parent for All Apps opened from Overview.
 */
public class AllApps extends LauncherInstrumentation.VisibleContainer {
    private static final int MAX_SCROLL_ATTEMPTS = 40;

    private final int mHeight;

    AllApps(LauncherInstrumentation launcher) {
        super(launcher);
        final UiObject2 allAppsContainer = verifyActiveContainer();
        mHeight = allAppsContainer.getVisibleBounds().height();
        final UiObject2 appListRecycler = mLauncher.waitForObjectInContainer(allAppsContainer,
                "apps_list_view");
        // Wait for the recycler to populate.
        mLauncher.waitForObjectInContainer(appListRecycler, By.clazz(TextView.class));
        verifyNotFrozen("All apps freeze flags upon opening all apps");
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.ALL_APPS;
    }

    private boolean hasClickableIcon(
            UiObject2 allAppsContainer, UiObject2 appListRecycler, BySelector appIconSelector) {
        final UiObject2 icon = appListRecycler.findObject(appIconSelector);
        if (icon == null) {
            LauncherInstrumentation.log("hasClickableIcon: icon not visible");
            return false;
        }
        final Rect iconBounds = icon.getVisibleBounds();
        LauncherInstrumentation.log("hasClickableIcon: icon bounds: " + iconBounds);
        if (iconCenterInSearchBox(allAppsContainer, icon)) {
            LauncherInstrumentation.log("hasClickableIcon: icon center is under search box");
            return false;
        }
        LauncherInstrumentation.log("hasClickableIcon: icon is clickable");
        return true;
    }

    private boolean iconCenterInSearchBox(UiObject2 allAppsContainer, UiObject2 icon) {
        final Point iconCenter = icon.getVisibleCenter();
        return getSearchBox(allAppsContainer).getVisibleBounds().contains(
                iconCenter.x, iconCenter.y);
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
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "getting app icon " + appName + " on all apps")) {
            final UiObject2 allAppsContainer = verifyActiveContainer();
            final UiObject2 appListRecycler = mLauncher.waitForObjectInContainer(allAppsContainer,
                    "apps_list_view");
            allAppsContainer.setGestureMargins(
                    0,
                    getSearchBox(allAppsContainer).getVisibleBounds().bottom + 1,
                    0,
                    ResourceUtils.getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE,
                            mLauncher.getResources()) + 1);
            final BySelector appIconSelector = AppIcon.getAppIconSelector(appName, mLauncher);
            if (!hasClickableIcon(allAppsContainer, appListRecycler, appIconSelector)) {
                scrollBackToBeginning();
                int attempts = 0;
                int scroll = getScroll(allAppsContainer);
                try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("scrolled")) {
                    while (!hasClickableIcon(allAppsContainer, appListRecycler, appIconSelector)) {
                        mLauncher.scroll(allAppsContainer, Direction.DOWN, 0.8f, null, 50);
                        final int newScroll = getScroll(allAppsContainer);
                        if (newScroll == scroll) break;

                        mLauncher.assertTrue(
                                "Exceeded max scroll attempts: " + MAX_SCROLL_ATTEMPTS,
                                ++attempts <= MAX_SCROLL_ATTEMPTS);
                        verifyActiveContainer();
                        scroll = newScroll;
                    }
                }
                verifyActiveContainer();
            }

            mLauncher.assertTrue("Unable to scroll to a clickable icon: " + appName,
                    hasClickableIcon(allAppsContainer, appListRecycler, appIconSelector));

            final UiObject2 appIcon = mLauncher.waitForObjectInContainer(appListRecycler,
                    appIconSelector);
            return new AppIcon(mLauncher, appIcon);
        }
    }

    private void scrollBackToBeginning() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to scroll back in all apps")) {
            LauncherInstrumentation.log("Scrolling to the beginning");
            final UiObject2 allAppsContainer = verifyActiveContainer();
            final UiObject2 searchBox = getSearchBox(allAppsContainer);

            int attempts = 0;
            final Rect margins = new Rect(0, searchBox.getVisibleBounds().bottom + 1, 0, 5);

            for (int scroll = getScroll(allAppsContainer);
                    scroll != 0;
                    scroll = getScroll(allAppsContainer)) {
                mLauncher.assertTrue("Negative scroll position", scroll > 0);

                mLauncher.assertTrue(
                        "Exceeded max scroll attempts: " + MAX_SCROLL_ATTEMPTS,
                        ++attempts <= MAX_SCROLL_ATTEMPTS);

                mLauncher.scroll(allAppsContainer, Direction.UP, 1, margins, 50);
            }

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("scrolled up")) {
                verifyActiveContainer();
            }
        }
    }

    private int getScroll(UiObject2 allAppsContainer) {
        return mLauncher.getAnswerFromLauncher(allAppsContainer, TestProtocol.GET_SCROLL_MESSAGE).
                getInt(TestProtocol.SCROLL_Y_FIELD, -1);
    }

    private UiObject2 getSearchBox(UiObject2 allAppsContainer) {
        return mLauncher.waitForObjectInContainer(allAppsContainer, "search_container_all_apps");
    }

    /**
     * Flings forward (down) and waits the fling's end.
     */
    public void flingForward() {
        try (LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to fling forward in all apps")) {
            final UiObject2 allAppsContainer = verifyActiveContainer();
            // Start the gesture in the center to avoid starting at elements near the top.
            mLauncher.scroll(
                    allAppsContainer, Direction.DOWN, 1, new Rect(0, 0, 0, mHeight / 2), 10);
            verifyActiveContainer();
        }
    }

    /**
     * Flings backward (up) and waits the fling's end.
     */
    public void flingBackward() {
        try (LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to fling backward in all apps")) {
            final UiObject2 allAppsContainer = verifyActiveContainer();
            // Start the gesture in the center, for symmetry with forward.
            mLauncher.scroll(
                    allAppsContainer, Direction.UP, 1, new Rect(0, mHeight / 2, 0, 0), 10);
            verifyActiveContainer();
        }
    }

    /**
     * Freezes updating app list upon app install/uninstall/update.
     */
    public void freeze() {
        mLauncher.getTestInfo(TestProtocol.REQUEST_FREEZE_APP_LIST);
    }

    /**
     * Resumes updating app list upon app install/uninstall/update.
     */
    public void unfreeze() {
        mLauncher.getTestInfo(TestProtocol.REQUEST_UNFREEZE_APP_LIST);
        verifyNotFrozen("All apps freeze flags upon unfreezing");
    }

    private void verifyNotFrozen(String message) {
        mLauncher.assertEquals(message, 0, mLauncher.getTestInfo(
                TestProtocol.REQUEST_APP_LIST_FREEZE_FLAGS).
                getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD));
    }
}
