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

import static com.android.launcher3.TestProtocol.ALL_APPS_STATE_ORDINAL;

import static junit.framework.TestCase.assertTrue;

import android.graphics.Point;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.TestProtocol;

/**
 * Operations on the workspace screen.
 */
public final class Workspace extends Home {
    private static final float FLING_SPEED = 3500.0F;
    private static final int DRAG_DURACTION = 2000;
    private final UiObject2 mHotseat;

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
        try (LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to switch from workspace to all apps")) {
            verifyActiveContainer();
            final UiObject2 hotseat = mHotseat;
            final Point start = hotseat.getVisibleCenter();
            final int swipeHeight = mLauncher.getTestInfo(
                    TestProtocol.REQUEST_HOME_TO_ALL_APPS_SWIPE_HEIGHT).
                    getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
            mLauncher.swipe(
                    start.x,
                    start.y,
                    start.x,
                    start.y - swipeHeight - mLauncher.getTouchSlop(),
                    ALL_APPS_STATE_ORDINAL
            );

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "swiped to all apps")) {
                return new AllApps(mLauncher);
            }
        }
    }

    /**
     * Returns an icon for the app, if currently visible.
     *
     * @param appName name of the app
     * @return app icon, if found, null otherwise.
     */
    @Nullable
    public AppIcon tryGetWorkspaceAppIcon(String appName) {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get a workspace icon")) {
            final UiObject2 workspace = verifyActiveContainer();
            final UiObject2 icon = workspace.findObject(
                    AppIcon.getAppIconSelector(appName, mLauncher));
            return icon != null ? new AppIcon(mLauncher, icon) : null;
        }
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
            dragIconToWorkspace(
                    mLauncher,
                    getHotseatAppIcon("Chrome"),
                    new Point(mLauncher.getDevice().getDisplayWidth(),
                            workspace.getVisibleBounds().centerY()));
            verifyActiveContainer();
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

    static void dragIconToWorkspace(
            LauncherInstrumentation launcher, Launchable launchable, Point dest) {
        LauncherInstrumentation.log("dragIconToWorkspace: begin");
        final Point launchableCenter = launchable.getObject().getVisibleCenter();
        final long downTime = SystemClock.uptimeMillis();
        launcher.sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, launchableCenter);
        launcher.waitForLauncherObject("deep_shortcuts_container");
        launcher.movePointer(downTime, DRAG_DURACTION, launchableCenter, dest);
        launcher.sendPointer(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, dest);
        LauncherInstrumentation.log("dragIconToWorkspace: end");
        launcher.waitUntilGone("drop_target_bar");
    }

    /**
     * Flings to get to screens on the right. Waits for scrolling and a possible overscroll
     * recoil to complete.
     */
    public void flingForward() {
        final UiObject2 workspace = verifyActiveContainer();
        workspace.setGestureMargins(0, 0, mLauncher.getEdgeSensitivityWidth(), 0);
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
        workspace.setGestureMargins(mLauncher.getEdgeSensitivityWidth(), 0, 0, 0);
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
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer("pressed Ctrl+W")) {
            return new Widgets(mLauncher);
        }
    }

    @Override
    protected String getSwipeHeightRequestName() {
        return TestProtocol.REQUEST_HOME_TO_OVERVIEW_SWIPE_HEIGHT;
    }

    @Override
    protected int getSwipeStartY() {
        return mLauncher.waitForLauncherObject("hotseat").getVisibleBounds().top;
    }
}