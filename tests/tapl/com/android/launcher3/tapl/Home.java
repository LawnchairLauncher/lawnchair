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

import static junit.framework.TestCase.assertTrue;

import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiObject2;
import android.view.KeyEvent;

/**
 * Operations on the home screen.
 */
public final class Home {

    private final Launcher mLauncher;
    private final UiObject2 mHotseat;
    private final int ICON_DRAG_SPEED = 2000;

    Home(Launcher launcher) {
        mLauncher = launcher;
        assertState();
        mHotseat = launcher.waitForLauncherObject("hotseat");
    }

    /**
     * Asserts that we are in home.
     *
     * @return Workspace.
     */
    @NonNull
    private UiObject2 assertState() {
        return mLauncher.assertState(Launcher.State.HOME);
    }

    /**
     * Swipes up or presses the square button to switch to Overview.
     *
     * @return the Overview panel object.
     */
    @NonNull
    public Overview switchToOverview() {
        assertState();
        if (mLauncher.isSwipeUpEnabled()) {
            final int height = mLauncher.getDevice().getDisplayHeight();
            final UiObject2 navBar = mLauncher.getSystemUiObject("navigation_bar_frame");

            // Swipe from nav bar to 2/3rd down the screen.
            mLauncher.swipe(
                    navBar.getVisibleBounds().centerX(), navBar.getVisibleBounds().centerY(),
                    navBar.getVisibleBounds().centerX(), height * 2 / 3,
                    (navBar.getVisibleBounds().centerY() - height * 2 / 3) / 100); // 100 px/step
        } else {
            mLauncher.getSystemUiObject("recent_apps").click();
        }

        return new Overview(mLauncher);
    }

    /**
     * Swipes up to All Apps.
     *
     * @return the App Apps object.
     */
    @NonNull
    public AllAppsFromHome switchToAllApps() {
        assertState();
        if (mLauncher.isSwipeUpEnabled()) {
            int midX = mLauncher.getDevice().getDisplayWidth() / 2;
            int height = mLauncher.getDevice().getDisplayHeight();
            // Swipe from 6/7ths down the screen to 1/7th down the screen.
            mLauncher.swipe(
                    midX,
                    height * 6 / 7,
                    midX,
                    height / 7,
                    (height * 2 / 3) / 100); // 100 px/step
        } else {
            // Swipe from the hotseat to near the top, e.g. 10% of the screen.
            final UiObject2 hotseat = mHotseat;
            final Point start = hotseat.getVisibleCenter();
            final int endY = (int) (mLauncher.getDevice().getDisplayHeight() * 0.1f);
            mLauncher.swipe(
                    start.x,
                    start.y,
                    start.x,
                    endY,
                    (start.y - endY) / 100); // 100 px/step
        }

        return new AllAppsFromHome(mLauncher);
    }

    /**
     * Returns an icon for the app, if currently visible.
     *
     * @param appName name of the app
     * @return app icon, if found, null otherwise.
     */
    @Nullable
    public AppIcon tryGetWorkspaceAppIcon(String appName) {
        final UiObject2 workspace = assertState();
        final UiObject2 icon = workspace.findObject(AppIcon.getAppIconSelector(appName));
        return icon != null ? new AppIcon(mLauncher, icon) : null;
    }

    /**
     * Ensures that workspace is scrollable. If it's not, drags an icon icons from hotseat to the
     * second screen.
     */
    public void ensureWorkspaceIsScrollable() {
        final UiObject2 workspace = assertState();
        if (!isWorkspaceScrollable(workspace)) {
            dragIconToNextScreen(getHotseatAppIcon("Messages"), workspace);
        }
        assertTrue("Home screen workspace didn't become scrollable",
                isWorkspaceScrollable(workspace));
    }

    private boolean isWorkspaceScrollable(UiObject2 workspace) {
        return workspace.isScrollable();
    }

    @NonNull
    private AppIcon getHotseatAppIcon(String appName) {
        return new AppIcon(mLauncher, mLauncher.getObjectInContainer(
                mHotseat, AppIcon.getAppIconSelector(appName)));
    }

    private void dragIconToNextScreen(AppIcon app, UiObject2 workspace) {
        final Point dest = new Point(
                mLauncher.getDevice().getDisplayWidth(), workspace.getVisibleBounds().centerY());
        app.getIcon().drag(dest, ICON_DRAG_SPEED);
        assertState();
    }

    /**
     * Flings to get to screens on the right. Waits for scrolling and a possible overscroll
     * recoil to complete.
     */
    public void flingForward() {
        final UiObject2 workspace = assertState();
        workspace.fling(Direction.RIGHT);
        mLauncher.waitForIdle();
        assertState();
    }

    /**
     * Flings to get to screens on the left.  Waits for scrolling and a possible overscroll
     * recoil to complete.
     */
    public void flingBackward() {
        final UiObject2 workspace = assertState();
        workspace.fling(Direction.LEFT);
        mLauncher.waitForIdle();
        assertState();
    }

    /**
     * Opens widgets container by pressing Ctrl+W.
     *
     * @return the widgets container.
     */
    @NonNull
    public Widgets openAllWidgets() {
        assertState();
        mLauncher.getDevice().pressKeyCode(KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON);
        return new Widgets(mLauncher);
    }
}