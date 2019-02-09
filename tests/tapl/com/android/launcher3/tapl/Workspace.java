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
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

/**
 * Operations on the workspace screen.
 */
public final class Workspace extends Home {
    private static final float FLING_SPEED = 3500.0F;
    private final UiObject2 mHotseat;
    private final int ICON_DRAG_SPEED = 2000;

    Workspace(LauncherInstrumentation launcher) {
        super(launcher);
        mHotseat = launcher.waitForLauncherObject("hotseat");
    }

    /**
     * Swipes up to All Apps.
     *
     * @return the App Apps object.
     */
    @NonNull
    public AllApps switchToAllApps() {
        verifyActiveContainer();
        // Swipe from the hotseat to near the top, e.g. 10% of the screen.
        final UiObject2 hotseat = mHotseat;
        final Point start = hotseat.getVisibleCenter();
        final int endY = (int) (mLauncher.getDevice().getDisplayHeight() * 0.1f);
        mLauncher.swipe(
                start.x,
                start.y,
                start.x,
                endY
        );

        return new AllApps(mLauncher);
    }

    /**
     * Returns an icon for the app, if currently visible.
     *
     * @param appName name of the app
     * @return app icon, if found, null otherwise.
     */
    @Nullable
    public AppIcon tryGetWorkspaceAppIcon(String appName) {
        final UiObject2 workspace = verifyActiveContainer();
        final UiObject2 icon = workspace.findObject(AppIcon.getAppIconSelector(appName, mLauncher));
        return icon != null ? new AppIcon(mLauncher, icon) : null;
    }


    /**
     * Returns an icon for the app; fails if the icon doesn't exist.
     *
     * @param appName name of the app
     * @return app icon.
     */
    @NonNull
    public AppIcon getWorkspaceAppIcon(String appName) {
        return new AppIcon(mLauncher,
                mLauncher.getObjectInContainer(
                        verifyActiveContainer(),
                        AppIcon.getAppIconSelector(appName, mLauncher)));
    }

    /**
     * Ensures that workspace is scrollable. If it's not, drags an icon icons from hotseat to the
     * second screen.
     */
    public void ensureWorkspaceIsScrollable() {
        final UiObject2 workspace = verifyActiveContainer();
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
                mHotseat, AppIcon.getAppIconSelector(appName, mLauncher)));
    }

    private void dragIconToNextScreen(AppIcon app, UiObject2 workspace) {
        final Point dest = new Point(
                mLauncher.getDevice().getDisplayWidth(), workspace.getVisibleBounds().centerY());
        app.getObject().drag(dest, ICON_DRAG_SPEED);
        mLauncher.waitUntilGone("drop_target_bar");
        verifyActiveContainer();
    }

    /**
     * Flings to get to screens on the right. Waits for scrolling and a possible overscroll
     * recoil to complete.
     */
    public void flingForward() {
        final UiObject2 workspace = verifyActiveContainer();
        workspace.fling(Direction.RIGHT, (int) (FLING_SPEED * mLauncher.getDisplayDensity()));
        mLauncher.waitForIdle();
        verifyActiveContainer();
    }

    /**
     * Flings to get to screens on the left.  Waits for scrolling and a possible overscroll
     * recoil to complete.
     */
    public void flingBackward() {
        final UiObject2 workspace = verifyActiveContainer();
        workspace.fling(Direction.LEFT, (int) (FLING_SPEED * mLauncher.getDisplayDensity()));
        mLauncher.waitForIdle();
        verifyActiveContainer();
    }

    /**
     * Opens widgets container by pressing Ctrl+W.
     *
     * @return the widgets container.
     */
    @NonNull
    public Widgets openAllWidgets() {
        verifyActiveContainer();
        mLauncher.getDevice().pressKeyCode(KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON);
        return new Widgets(mLauncher);
    }
}