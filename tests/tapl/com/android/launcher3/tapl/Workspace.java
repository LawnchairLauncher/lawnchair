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

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED;

import static com.android.launcher3.testing.TestProtocol.ALL_APPS_STATE_ORDINAL;
import static com.android.launcher3.testing.TestProtocol.NORMAL_STATE_ORDINAL;
import static com.android.launcher3.testing.TestProtocol.SPRING_LOADED_STATE_ORDINAL;

import static junit.framework.TestCase.assertTrue;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.TestProtocol;

import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Operations on the workspace screen.
 */
public final class Workspace extends Home {
    private static final int FLING_STEPS = 10;
    private static final int DEFAULT_DRAG_STEPS = 10;
    private static final String DROP_BAR_RES_ID = "drop_target_bar";
    private static final String DELETE_TARGET_TEXT_ID = "delete_target_text";

    static final Pattern EVENT_CTRL_W_DOWN = Pattern.compile(
            "Key event: KeyEvent.*?action=ACTION_DOWN.*?keyCode=KEYCODE_W"
                    + ".*?metaState=META_CTRL_ON");
    static final Pattern EVENT_CTRL_W_UP = Pattern.compile(
            "Key event: KeyEvent.*?action=ACTION_UP.*?keyCode=KEYCODE_W"
                    + ".*?metaState=META_CTRL_ON");
    private static final Pattern LONG_CLICK_EVENT = Pattern.compile("onWorkspaceItemLongClick");

    private final UiObject2 mHotseat;

    Workspace(LauncherInstrumentation launcher) {
        super(launcher);
        mHotseat = launcher.waitForLauncherObject("hotseat");
    }

    /**
     * Swipes up to All Apps.
     *
     * @return the All Apps object.
     */
    @NonNull
    public AllApps switchToAllApps() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to switch from workspace to all apps")) {
            verifyActiveContainer();
            final int deviceHeight = mLauncher.getDevice().getDisplayHeight();
            final int bottomGestureMargin = mLauncher.getBottomGestureSize();
            final int windowCornerRadius = (int) Math.ceil(mLauncher.getWindowCornerRadius());
            final int startY = deviceHeight - Math.max(bottomGestureMargin, windowCornerRadius) - 1;
            final int swipeHeight = mLauncher.getTestInfo(
                    TestProtocol.REQUEST_HOME_TO_ALL_APPS_SWIPE_HEIGHT).
                    getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
            LauncherInstrumentation.log(
                    "switchToAllApps: deviceHeight = " + deviceHeight + ", startY = " + startY
                            + ", swipeHeight = " + swipeHeight + ", slop = "
                            + mLauncher.getTouchSlop());

            mLauncher.swipeToState(
                    windowCornerRadius,
                    startY,
                    windowCornerRadius,
                    startY - swipeHeight - mLauncher.getTouchSlop(),
                    12,
                    ALL_APPS_STATE_ORDINAL, LauncherInstrumentation.GestureScope.INSIDE);

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
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get a workspace icon")) {
            return new AppIcon(mLauncher,
                    mLauncher.waitForObjectInContainer(
                            verifyActiveContainer(),
                            AppIcon.getAppIconSelector(appName, mLauncher)));
        }
    }

    /**
     * Ensures that workspace is scrollable. If it's not, drags an icon icons from hotseat to the
     * second screen.
     */
    public void ensureWorkspaceIsScrollable() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            final UiObject2 workspace = verifyActiveContainer();
            if (!isWorkspaceScrollable(workspace)) {
                try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                        "dragging icon to a second page of workspace to make it scrollable")) {
                    dragIcon(workspace, getHotseatAppIcon("Chrome"), pagesPerScreen());
                    verifyActiveContainer();
                }
            }
            assertTrue("Home screen workspace didn't become scrollable",
                    isWorkspaceScrollable(workspace));
        }
    }

    /**
     * Returns the number of pages that are visible on the screen simultaneously.
     */
    public int pagesPerScreen() {
        return mLauncher.isTwoPanels() ? 2 : 1;
    }

    /**
     * Drags an icon to the (currentPage + pageDelta) page.
     * If the target page doesn't exist yet, a new page will be created.
     * In case the target page can't be created (e.g. existing pages are 0, 1, current: 0,
     * pageDelta: 3, the latest page that can be created is 2) the icon will be dragged onto the
     * page that can be created and is closest to the target page.
     *
     * @param appIcon   - icon to drag.
     * @param pageDelta - how many pages should the icon be dragged from the current page.
     *                    It can be a negative value. currentPage + pageDelta should be greater
     *                    than or equal to 0.
     */
    public void dragIcon(AppIcon appIcon, int pageDelta) {
        if (mHotseat.getVisibleBounds().height() > mHotseat.getVisibleBounds().width()) {
            throw new UnsupportedOperationException(
                    "dragIcon does NOT support dragging when the hotseat is on the side.");
        }
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            final UiObject2 workspace = verifyActiveContainer();
            try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                    "dragging icon to page with delta: " + pageDelta)) {
                dragIcon(workspace, appIcon, pageDelta);
                verifyActiveContainer();
            }
        }
    }

    private void dragIcon(UiObject2 workspace, AppIcon appIcon, int pageDelta) {
        int pageWidth = mLauncher.getDevice().getDisplayWidth() / pagesPerScreen();
        int targetX = (pageWidth / 2) + pageWidth * pageDelta;
        dragIconToWorkspace(
                mLauncher,
                appIcon,
                new Point(targetX, mLauncher.getVisibleBounds(workspace).centerY()),
                "popup_container",
                false,
                false,
                () -> mLauncher.expectEvent(
                        TestProtocol.SEQUENCE_MAIN, LONG_CLICK_EVENT));
        verifyActiveContainer();
    }

    private boolean isWorkspaceScrollable(UiObject2 workspace) {
        return workspace.getChildCount() > (mLauncher.isTwoPanels() ? 2 : 1);
    }

    @NonNull
    public AppIcon getHotseatAppIcon(String appName) {
        return new AppIcon(mLauncher, mLauncher.waitForObjectInContainer(
                mHotseat, AppIcon.getAppIconSelector(appName, mLauncher)));
    }

    private static int getStartDragThreshold(LauncherInstrumentation launcher) {
        return launcher.getTestInfo(TestProtocol.REQUEST_START_DRAG_THRESHOLD).getInt(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    /*
     * Get the center point of the delete icon in the drop target bar.
     */
    private Point getDeleteDropPoint() {
        return mLauncher.waitForObjectInContainer(
                mLauncher.waitForLauncherObject(DROP_BAR_RES_ID),
                DELETE_TARGET_TEXT_ID).getVisibleCenter();
    }

    /**
     * Delete the appIcon from the workspace.
     *
     * @param appIcon to be deleted.
     * @return validated workspace after the existing appIcon being deleted.
     */
    public Workspace deleteAppIcon(AppIcon appIcon) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "removing app icon from workspace")) {
            dragIconToWorkspace(
                    mLauncher, appIcon,
                    () -> getDeleteDropPoint(),
                    true, /* decelerating */
                    appIcon.getLongPressIndicator(),
                    () -> mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, LONG_CLICK_EVENT),
                    null);

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "dragged the app to the drop bar")) {
                return new Workspace(mLauncher);
            }
        }
    }

    /**
     * Finds folder icons in the current workspace.
     *
     * @return a list of folder icons.
     */
    List<FolderIcon> getFolderIcons() {
        final UiObject2 workspace = verifyActiveContainer();
        return mLauncher.getObjectsInContainer(workspace, "folder_icon_name").stream().map(
                o -> new FolderIcon(mLauncher, o)).collect(Collectors.toList());
    }

    /**
     * Drag an icon up with a short distance that makes workspace go to spring loaded state.
     *
     * @return the position after dragging.
     */
    private static Point dragIconToSpringLoaded(LauncherInstrumentation launcher, long downTime,
            UiObject2 icon,
            String longPressIndicator, Runnable expectLongClickEvents) {
        final Point iconCenter = icon.getVisibleCenter();
        final Point dragStartCenter = new Point(iconCenter.x,
                iconCenter.y - getStartDragThreshold(launcher));

        launcher.runToState(() -> {
            launcher.sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN,
                    iconCenter, LauncherInstrumentation.GestureScope.INSIDE);
            LauncherInstrumentation.log("dragIconToSpringLoaded: sent down");
            expectLongClickEvents.run();
            launcher.waitForLauncherObject(longPressIndicator);
            LauncherInstrumentation.log("dragIconToSpringLoaded: indicator");
            launcher.movePointer(iconCenter, dragStartCenter, DEFAULT_DRAG_STEPS, false,
                    downTime, true, LauncherInstrumentation.GestureScope.INSIDE);
        }, SPRING_LOADED_STATE_ORDINAL, "long-pressing and triggering drag start");
        return dragStartCenter;
    }

    private static void dropDraggedIcon(LauncherInstrumentation launcher, Point dest, long downTime,
            @Nullable Runnable expectedEvents) {
        launcher.runToState(
                () -> launcher.sendPointer(
                        downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, dest,
                        LauncherInstrumentation.GestureScope.INSIDE),
                NORMAL_STATE_ORDINAL,
                "sending UP event");
        if (expectedEvents != null) {
            expectedEvents.run();
        }
        LauncherInstrumentation.log("dropIcon: end");
        launcher.waitUntilLauncherObjectGone("drop_target_bar");
    }

    static void dragIconToWorkspace(LauncherInstrumentation launcher, Launchable launchable,
            Point dest, String longPressIndicator, boolean startsActivity, boolean isWidgetShortcut,
            Runnable expectLongClickEvents) {
        Runnable expectDropEvents = null;
        if (startsActivity || isWidgetShortcut) {
            expectDropEvents = () -> launcher.expectEvent(TestProtocol.SEQUENCE_MAIN,
                    LauncherInstrumentation.EVENT_START);
        }
        dragIconToWorkspace(launcher, launchable, () -> dest, false, longPressIndicator,
                expectLongClickEvents, expectDropEvents);
    }

    /**
     * Drag icon in workspace to else where.
     * This function expects the launchable is inside the workspace and there is no drop event.
     */
    static void dragIconToWorkspace(LauncherInstrumentation launcher, Launchable launchable,
            Supplier<Point> destSupplier, String longPressIndicator) {
        dragIconToWorkspace(launcher, launchable, destSupplier, false, longPressIndicator,
                () -> launcher.expectEvent(TestProtocol.SEQUENCE_MAIN, LONG_CLICK_EVENT), null);
    }

    static void dragIconToWorkspace(
            LauncherInstrumentation launcher, Launchable launchable, Supplier<Point> dest,
            boolean isDecelerating, String longPressIndicator, Runnable expectLongClickEvents,
            @Nullable Runnable expectDropEvents) {
        try (LauncherInstrumentation.Closable ignored = launcher.addContextLayer(
                "want to drag icon to workspace")) {
            final long downTime = SystemClock.uptimeMillis();
            Point dragStart = dragIconToSpringLoaded(launcher, downTime,
                    launchable.getObject(), longPressIndicator, expectLongClickEvents);
            Point targetDest = dest.get();
            int displayX = launcher.getRealDisplaySize().x;

            // Since the destination can be on another page, we need to drag to the edge first
            // until we reach the target page
            while (targetDest.x > displayX || targetDest.x < 0) {
                int edgeX = targetDest.x > 0 ? displayX : 0;
                Point screenEdge = new Point(edgeX, targetDest.y);
                Point finalDragStart = dragStart;
                executeAndWaitForPageScroll(launcher,
                        () -> launcher.movePointer(finalDragStart, screenEdge, DEFAULT_DRAG_STEPS,
                                isDecelerating, downTime, true,
                                LauncherInstrumentation.GestureScope.INSIDE));
                targetDest.x += displayX * (targetDest.x > 0 ? -1 : 1);
                dragStart = screenEdge;
            }

            // targetDest.x is now between 0 and displayX so we found the target page,
            // we just have to put move the icon to the destination and drop it
            launcher.movePointer(dragStart, targetDest, DEFAULT_DRAG_STEPS, isDecelerating,
                    downTime, true, LauncherInstrumentation.GestureScope.INSIDE);
            dropDraggedIcon(launcher, targetDest, downTime, expectDropEvents);
        }
    }

    private static void executeAndWaitForPageScroll(LauncherInstrumentation launcher,
            Runnable command) {
        launcher.executeAndWaitForEvent(command,
                event -> event.getEventType() == TYPE_VIEW_SCROLLED,
                () -> "Page scroll didn't happen", "Scrolling page");
    }

    /**
     * Flings to get to screens on the right. Waits for scrolling and a possible overscroll
     * recoil to complete.
     */
    public void flingForward() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            final UiObject2 workspace = verifyActiveContainer();
            mLauncher.scroll(workspace, Direction.RIGHT,
                    new Rect(0, 0, mLauncher.getEdgeSensitivityWidth() + 1, 0),
                    FLING_STEPS, false);
            verifyActiveContainer();
        }
    }

    /**
     * Flings to get to screens on the left.  Waits for scrolling and a possible overscroll
     * recoil to complete.
     */
    public void flingBackward() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            final UiObject2 workspace = verifyActiveContainer();
            mLauncher.scroll(workspace, Direction.LEFT,
                    new Rect(mLauncher.getEdgeSensitivityWidth() + 1, 0, 0, 0),
                    FLING_STEPS, false);
            verifyActiveContainer();
        }
    }

    /**
     * Opens widgets container by pressing Ctrl+W.
     *
     * @return the widgets container.
     */
    @NonNull
    public Widgets openAllWidgets() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            verifyActiveContainer();
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, EVENT_CTRL_W_DOWN);
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, EVENT_CTRL_W_UP);
            mLauncher.getDevice().pressKeyCode(KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON);
            try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer("pressed Ctrl+W")) {
                return new Widgets(mLauncher);
            }
        }
    }

    @Override
    protected String getSwipeHeightRequestName() {
        return TestProtocol.REQUEST_HOME_TO_OVERVIEW_SWIPE_HEIGHT;
    }

    @Override
    protected int getSwipeStartY() {
        return mLauncher.getRealDisplaySize().y - 1;
    }

    @Nullable
    public Widget tryGetWidget(String label, long timeout) {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "getting widget " + label + " on workspace with timeout " + timeout)) {
            final UiObject2 widget = mLauncher.tryWaitForLauncherObject(
                    By.clazz("com.android.launcher3.widget.LauncherAppWidgetHostView").desc(label),
                    timeout);
            return widget != null ? new Widget(mLauncher, widget) : null;
        }
    }

    @Nullable
    public Widget tryGetPendingWidget(long timeout) {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "getting pending widget on workspace with timeout " + timeout)) {
            final UiObject2 widget = mLauncher.tryWaitForLauncherObject(
                    By.clazz("com.android.launcher3.widget.PendingAppWidgetHostView"), timeout);
            return widget != null ? new Widget(mLauncher, widget) : null;
        }
    }
}