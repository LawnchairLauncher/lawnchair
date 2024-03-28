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

import static android.view.KeyEvent.KEYCODE_META_RIGHT;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED;

import static com.android.launcher3.testing.shared.TestProtocol.ALL_APPS_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.NORMAL_STATE_ORDINAL;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.testing.shared.HotseatCellCenterRequest;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.testing.shared.WorkspaceCellCenterRequest;

import java.util.List;
import java.util.Map;
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
    private static final String UNINSTALL_TARGET_TEXT_ID = "uninstall_target_text";

    static final Pattern EVENT_CTRL_W_DOWN = Pattern.compile(
            "Key event: KeyEvent.*?action=ACTION_DOWN.*?keyCode=KEYCODE_W"
                    + ".*?metaState=META_CTRL_ON");
    static final Pattern EVENT_CTRL_W_UP = Pattern.compile(
            "Key event: KeyEvent.*?action=ACTION_UP.*?keyCode=KEYCODE_W"
                    + ".*?metaState=META_CTRL_ON");
    static final Pattern LONG_CLICK_EVENT = Pattern.compile("onWorkspaceItemLongClick");
    public static final int MAX_WORKSPACE_DRAG_TRIES = 100;

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
    public HomeAllApps switchToAllApps() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to switch from workspace to all apps")) {
            verifyActiveContainer();
            final int deviceHeight = mLauncher.getDevice().getDisplayHeight();
            final int bottomGestureMargin = mLauncher.getBottomGestureSize();
            final int windowCornerRadius = (int) Math.ceil(mLauncher.getWindowCornerRadius());
            final int startY = deviceHeight - Math.max(bottomGestureMargin, windowCornerRadius) - 1;
            final int swipeHeight = mLauncher.getTestInfo(
                            TestProtocol.REQUEST_HOME_TO_ALL_APPS_SWIPE_HEIGHT)
                    .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
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
                    ALL_APPS_STATE_ORDINAL,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "swiped to all apps")) {
                return new HomeAllApps(mLauncher);
            }
        }
    }

    /** Opens the Launcher all apps page with the meta keyboard shortcut. */
    public HomeAllApps openAllAppsFromKeyboardShortcut() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to open all apps search")) {
            verifyActiveContainer();
            mLauncher.getDevice().pressKeyCode(KEYCODE_META_RIGHT);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "pressed meta key")) {
                return new HomeAllApps(mLauncher);
            }
        }
    }

    /**
     * Returns the home qsb.
     *
     * The qsb must already be visible when calling this method.
     */
    @NonNull
    public Qsb getQsb() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get the home qsb")) {
            return new HomeQsb(mLauncher, mHotseat);
        }
    }

    /**
     * Returns an icon for the app, if currently visible.
     *
     * @param appName name of the app
     * @return app icon, if found, null otherwise.
     */
    @Nullable
    public HomeAppIcon tryGetWorkspaceAppIcon(String appName) {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get a workspace icon")) {
            final UiObject2 workspace = verifyActiveContainer();
            final UiObject2 icon = workspace.findObject(
                    AppIcon.getAppIconSelector(appName, mLauncher));
            return icon != null ? new WorkspaceAppIcon(mLauncher, icon) : null;
        }
    }

    /**
     * Waits for an app icon to be gone (e.g. after uninstall). Fails if it remains.
     *
     * @param errorMessage error message thrown then the icon doesn't disappear.
     * @param appName      app that should be gone.
     */
    public void verifyWorkspaceAppIconIsGone(String errorMessage, String appName) {
        final UiObject2 workspace = verifyActiveContainer();
        assertTrue(errorMessage,
                workspace.wait(
                        Until.gone(AppIcon.getAppIconSelector(appName, mLauncher)),
                        LauncherInstrumentation.WAIT_TIME_MS));
    }


    /**
     * Returns an icon for the app; fails if the icon doesn't exist.
     *
     * @param appName name of the app
     * @return app icon.
     */
    @NonNull
    public HomeAppIcon getWorkspaceAppIcon(String appName) {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get a workspace icon")) {
            return new WorkspaceAppIcon(mLauncher,
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

    /** Returns the number of pages. */
    public int getPageCount() {
        final UiObject2 workspace = verifyActiveContainer();
        return workspace.getChildCount();
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
     * @param homeAppIcon - icon to drag.
     * @param pageDelta   - how many pages should the icon be dragged from the current page.
     *                    It can be a negative value. currentPage + pageDelta should be greater
     *                    than or equal to 0.
     */
    public void dragIcon(HomeAppIcon homeAppIcon, int pageDelta) {
        if (mHotseat.getVisibleBounds().height() > mHotseat.getVisibleBounds().width()) {
            throw new UnsupportedOperationException(
                    "dragIcon does NOT support dragging when the hotseat is on the side.");
        }
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            final UiObject2 workspace = verifyActiveContainer();
            try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                    "dragging icon to page with delta: " + pageDelta)) {
                dragIcon(workspace, homeAppIcon, pageDelta);
                verifyActiveContainer();
            }
        }
    }

    private void dragIcon(UiObject2 workspace, HomeAppIcon homeAppIcon, int pageDelta) {
        int pageWidth = mLauncher.getDevice().getDisplayWidth() / pagesPerScreen();
        int targetX = (pageWidth / 2) + pageWidth * pageDelta;
        int targetY = mLauncher.getVisibleBounds(workspace).centerY();
        dragIconToWorkspace(
                mLauncher,
                homeAppIcon,
                () -> new Point(targetX, targetY),
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
    public HomeAppIcon getHotseatAppIcon(String appName) {
        return new WorkspaceAppIcon(mLauncher, mLauncher.waitForObjectInContainer(
                mHotseat, AppIcon.getAppIconSelector(appName, mLauncher)));
    }

    /**
     * Returns an icon for the given cell; fails if the icon doesn't exist.
     *
     * @param cellInd zero based index number of the hotseat cells.
     * @return app icon.
     */
    @NonNull
    public HomeAppIcon getHotseatAppIcon(int cellInd) {
        List<UiObject2> icons = mHotseat.findObjects(AppIcon.getAnyAppIconSelector());
        final Point center = getHotseatCellCenter(mLauncher, cellInd);
        return icons.stream()
                .filter(icon -> icon.getVisibleBounds().contains(center.x, center.y))
                .findFirst()
                .map(icon -> new WorkspaceAppIcon(mLauncher, icon))
                .orElseThrow(() ->
                        new AssertionError("Unable to get a hotseat icon on " + cellInd));
    }

    /**
     * @return map of text -> center of the view. In case of icons with the same name, the one with
     * lower x coordinate is selected.
     */
    public Map<String, Point> getWorkspaceIconsPositions() {
        final UiObject2 workspace = verifyActiveContainer();
        List<UiObject2> workspaceIcons =
                mLauncher.waitForObjectsInContainer(workspace, AppIcon.getAnyAppIconSelector());
        return workspaceIcons.stream()
                .collect(
                        Collectors.toMap(
                                /* keyMapper= */ UiObject2::getText,
                                /* valueMapper= */ UiObject2::getVisibleCenter,
                                /* mergeFunction= */ (p1, p2) -> p1.x < p2.x ? p1 : p2));
    }

    /*
     * Get the center point of the delete/uninstall icon in the drop target bar.
     */
    private static Point getDropPointFromDropTargetBar(
            LauncherInstrumentation launcher, String targetId) {
        return launcher.waitForObjectInContainer(
                launcher.waitForLauncherObject(DROP_BAR_RES_ID),
                targetId).getVisibleCenter();
    }

    /**
     * Drag the appIcon from the workspace and cancel by dragging icon to corner of screen where no
     * drop point exists.
     *
     * @param homeAppIcon to be dragged.
     */
    @NonNull
    public Workspace dragAndCancelAppIcon(HomeAppIcon homeAppIcon) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "dragging app icon across workspace")) {
            dragIconToWorkspace(
                    mLauncher,
                    homeAppIcon,
                    () -> new Point(0, 0),
                    () -> mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, LONG_CLICK_EVENT),
                    null,
                    /* startsActivity = */ false);

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "dragged the app across workspace")) {
                return new Workspace(mLauncher);
            }
        }
    }

    /**
     * Delete the appIcon from the workspace.
     *
     * @param homeAppIcon to be deleted.
     * @return validated workspace after the existing appIcon being deleted.
     */
    public Workspace deleteAppIcon(HomeAppIcon homeAppIcon) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "removing app icon from workspace")) {
            dragIconToWorkspace(
                    mLauncher,
                    homeAppIcon,
                    () -> getDropPointFromDropTargetBar(mLauncher, DELETE_TARGET_TEXT_ID),
                    () -> mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, LONG_CLICK_EVENT),
                    /* expectDropEvents= */ null,
                    /* startsActivity = */ false);

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "dragged the app to the drop bar")) {
                return new Workspace(mLauncher);
            }
        }
    }

    /**
     * Uninstall the appIcon by dragging it to the 'uninstall' drop point of the drop_target_bar.
     *
     * @param launcher              the root TAPL instrumentation object of {@link
     *                              LauncherInstrumentation} type.
     * @param homeAppIcon           to be uninstalled.
     * @param launcher              the root TAPL instrumentation object of {@link
     *                              LauncherInstrumentation} type.
     * @param homeAppIcon           to be uninstalled.
     * @param expectLongClickEvents the runnable to be executed to verify expected longclick event.
     * @return validated workspace after the existing appIcon being uninstalled.
     */
    static Workspace uninstallAppIcon(LauncherInstrumentation launcher, HomeAppIcon homeAppIcon,
            Runnable expectLongClickEvents) {
        try (LauncherInstrumentation.Closable c = launcher.addContextLayer(
                "uninstalling app icon")) {
            dragIconToWorkspace(
                    launcher,
                    homeAppIcon,
                    () -> getDropPointFromDropTargetBar(launcher, UNINSTALL_TARGET_TEXT_ID),
                    expectLongClickEvents,
                    /* expectDropEvents= */null,
                    /* startsActivity = */ false);

            launcher.waitUntilLauncherObjectGone(DROP_BAR_RES_ID);

            final BySelector installerAlert = By.text(Pattern.compile(
                    "Do you want to uninstall this app\\?",
                    Pattern.DOTALL | Pattern.MULTILINE));
            final UiDevice device = launcher.getDevice();
            assertTrue("uninstall alert is not shown", device.wait(
                    Until.hasObject(installerAlert), LauncherInstrumentation.WAIT_TIME_MS));
            final UiObject2 ok = device.findObject(By.text("OK"));
            assertNotNull("OK button is not shown", ok);
            launcher.clickObject(ok);
            assertTrue("Uninstall alert is not dismissed after clicking OK", device.wait(
                    Until.gone(installerAlert), LauncherInstrumentation.WAIT_TIME_MS));

            try (LauncherInstrumentation.Closable c1 = launcher.addContextLayer(
                    "uninstalled app by dragging to the drop bar")) {
                return new Workspace(launcher);
            }
        }
    }

    /**
     * Get cell layout's grids size. The return point's x and y values are the cell counts in X and
     * Y directions respectively, not the values in pixels.
     */
    public Point getIconGridDimensions() {
        int[] countXY = mLauncher.getTestInfo(
                TestProtocol.REQUEST_WORKSPACE_CELL_LAYOUT_SIZE).getIntArray(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
        return new Point(countXY[0], countXY[1]);
    }

    static Point getCellCenter(LauncherInstrumentation launcher, int cellX, int cellY) {
        return launcher.getTestInfo(WorkspaceCellCenterRequest.builder().setCellX(cellX).setCellY(
                cellY).build()).getParcelable(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    static Point getCellCenter(LauncherInstrumentation launcher, int cellX, int cellY, int spanX,
            int spanY) {
        return launcher.getTestInfo(WorkspaceCellCenterRequest.builder().setCellX(cellX)
                .setCellY(cellY).setSpanX(spanX).setSpanY(spanY).build())
                .getParcelable(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    static Point getHotseatCellCenter(LauncherInstrumentation launcher, int cellInd) {
        return launcher.getTestInfo(HotseatCellCenterRequest.builder()
                .setCellInd(cellInd).build()).getParcelable(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    /** Returns the number of rows and columns in the workspace */
    public Point getRowsAndCols() {
        return mLauncher.getTestInfo(TestProtocol.REQUEST_WORKSPACE_COLUMNS_ROWS).getParcelable(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    /** Returns the index of the current page */
    private static int geCurrentPage(LauncherInstrumentation launcher) {
        return launcher.getTestInfo(TestProtocol.REQUEST_WORKSPACE_CURRENT_PAGE_INDEX).getInt(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
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

    private static void sendUp(LauncherInstrumentation launcher, Point dest,
            long downTime) {
        launcher.sendPointer(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, dest,
                LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
    }

    private static void dropDraggedIcon(LauncherInstrumentation launcher, Point dest, long downTime,
            @Nullable Runnable expectedEvents, boolean startsActivity) {
        if (startsActivity) {
            launcher.executeAndWaitForLauncherStop(
                    () -> sendUp(launcher, dest, downTime),
                    "sending UP event");
        } else {
            launcher.runToState(
                    () -> sendUp(launcher, dest, downTime),
                    NORMAL_STATE_ORDINAL,
                    "sending UP event");
        }
        if (expectedEvents != null) {
            expectedEvents.run();
        }
        LauncherInstrumentation.log("dropIcon: end");
        launcher.waitUntilLauncherObjectGone("drop_target_bar");
    }

    static void dragIconToWorkspace(LauncherInstrumentation launcher, Launchable launchable,
            Supplier<Point> dest, boolean startsActivity, boolean isWidgetShortcut,
            Runnable expectLongClickEvents) {
        Runnable expectDropEvents = null;
        if (startsActivity || isWidgetShortcut) {
            expectDropEvents = () -> launcher.expectEvent(TestProtocol.SEQUENCE_MAIN,
                    LauncherInstrumentation.EVENT_START);
        }
        dragIconToWorkspace(
                launcher, launchable, dest, expectLongClickEvents, expectDropEvents,
                startsActivity);
    }

    static void dragIconToWorkspaceCellPosition(LauncherInstrumentation launcher,
            Launchable launchable, int cellX, int cellY, int spanX, int spanY,
            boolean startsActivity, boolean isWidgetShortcut, Runnable expectLongClickEvents) {
        Runnable expectDropEvents = null;
        if (startsActivity || isWidgetShortcut) {
            expectDropEvents = () -> launcher.expectEvent(TestProtocol.SEQUENCE_MAIN,
                    LauncherInstrumentation.EVENT_START);
        }
        dragIconToWorkspaceCellPosition(
                launcher, launchable, cellX, cellY, spanX, spanY, true, expectLongClickEvents,
                expectDropEvents);
    }

    /**
     * Drag icon in workspace to else where and drop it immediately.
     * (There is no slow down time before drop event)
     * This function expects the launchable is inside the workspace and there is no drop event.
     */
    static void dragIconToWorkspace(
            LauncherInstrumentation launcher, Launchable launchable, Supplier<Point> destSupplier) {
        dragIconToWorkspace(
                launcher,
                launchable,
                destSupplier,
                /* isDecelerating= */ false,
                () -> launcher.expectEvent(TestProtocol.SEQUENCE_MAIN, LONG_CLICK_EVENT),
                /* expectDropEvents= */ null,
                /* startsActivity = */ false);
    }

    static void dragIconToWorkspace(
            LauncherInstrumentation launcher,
            Launchable launchable,
            Supplier<Point> dest,
            Runnable expectLongClickEvents,
            @Nullable Runnable expectDropEvents,
            boolean startsActivity) {
        dragIconToWorkspace(launcher, launchable, dest, /* isDecelerating */ true,
                expectLongClickEvents, expectDropEvents, startsActivity);
    }

    static void dragIconToWorkspace(
            LauncherInstrumentation launcher,
            Launchable launchable,
            Supplier<Point> dest,
            boolean isDecelerating,
            Runnable expectLongClickEvents,
            @Nullable Runnable expectDropEvents,
            boolean startsActivity) {
        try (LauncherInstrumentation.Closable ignored = launcher.addContextLayer(
                "want to drag icon to workspace")) {
            final long downTime = SystemClock.uptimeMillis();
            Point dragStart = launchable.startDrag(
                    downTime,
                    expectLongClickEvents,
                    /* runToSpringLoadedState= */ true);
            Point targetDest = dest.get();
            int displayX = launcher.getRealDisplaySize().x;

            // Since the destination can be on another page, we need to drag to the edge first
            // until we reach the target page
            while (targetDest.x > displayX || targetDest.x < 0) {
                // Don't drag all the way to the edge to prevent touch events from getting out of
                //screen bounds.
                int edgeX = targetDest.x > 0 ? displayX - 1 : 1;
                Point screenEdge = new Point(edgeX, targetDest.y);
                Point finalDragStart = dragStart;
                executeAndWaitForPageScroll(launcher,
                        () -> launcher.movePointer(finalDragStart, screenEdge, DEFAULT_DRAG_STEPS,
                                true, downTime, downTime, true,
                                LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER));
                targetDest.x += displayX * (targetDest.x > 0 ? -1 : 1);
                dragStart = screenEdge;
            }

            // targetDest.x is now between 0 and displayX so we found the target page,
            // we just have to put move the icon to the destination and drop it
            launcher.movePointer(dragStart, targetDest, DEFAULT_DRAG_STEPS, isDecelerating,
                    downTime, SystemClock.uptimeMillis(), false,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
            dropDraggedIcon(launcher, targetDest, downTime, expectDropEvents, startsActivity);
        }
    }

    static void dragIconToWorkspaceCellPosition(
            LauncherInstrumentation launcher,
            Launchable launchable,
            int cellX, int cellY, int spanX, int spanY,
            boolean isDecelerating,
            Runnable expectLongClickEvents,
            @Nullable Runnable expectDropEvents) {
        try (LauncherInstrumentation.Closable ignored = launcher.addContextLayer(
                "want to drag icon to workspace")) {
            Point rowsAndCols = launcher.getWorkspace().getRowsAndCols();
            int destinationWorkspace = cellX / rowsAndCols.x;
            cellX = cellX % rowsAndCols.x;

            final long downTime = SystemClock.uptimeMillis();
            Point dragStart = launchable.startDrag(
                    downTime,
                    expectLongClickEvents,
                    /* runToSpringLoadedState= */ true);
            Point targetDest = getCellCenter(launcher, cellX, cellY, spanX, spanY);
            // Since the destination can be on another page, we need to drag to the edge first
            // until we reach the target page
            dragStart = dragToGivenWorkspace(launcher, dragStart, destinationWorkspace,
                    targetDest.y);

            // targetDest.x is now between 0 and displayX so we found the target page,
            // we just have to put move the icon to the destination and drop it
            launcher.movePointer(dragStart, targetDest, DEFAULT_DRAG_STEPS, isDecelerating,
                    downTime, SystemClock.uptimeMillis(), false,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
            dropDraggedIcon(launcher, targetDest, downTime, expectDropEvents,
                    /* startsActivity = */ false);
        }
    }

    /**
     * Given a drag that already started at currentPosition, drag the item to the given destination
     * index defined by destinationWorkspaceIndex.
     *
     * @param launcher
     * @param currentPosition
     * @param destinationWorkspaceIndex
     * @param y
     * @return the finishing position of the drag.
     */
    private static Point dragToGivenWorkspace(LauncherInstrumentation launcher,
            Point currentPosition, int destinationWorkspaceIndex, int y) {
        final long downTime = SystemClock.uptimeMillis();
        int displayX = launcher.getRealDisplaySize().x;
        int currentPage = Workspace.geCurrentPage(launcher);
        int counter = 0;
        while (currentPage != destinationWorkspaceIndex) {
            counter++;
            if (counter > MAX_WORKSPACE_DRAG_TRIES) {
                throw new RuntimeException(
                        "Wrong destination workspace index " + destinationWorkspaceIndex
                                + ", desired workspace was never reached");
            }
            // if the destination is greater than current page, set the display edge to be the
            // right edge. Don't drag all the way to the edge to prevent touch events from
            // getting out of screen bounds.
            int displayEdge = destinationWorkspaceIndex > currentPage ? displayX - 1 : 1;
            Point screenEdge = new Point(displayEdge, y);
            Point finalDragStart = currentPosition;
            executeAndWaitForPageScroll(launcher,
                    () -> launcher.movePointer(finalDragStart, screenEdge, DEFAULT_DRAG_STEPS,
                            true, downTime, downTime, true,
                            LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER));
            currentPage = Workspace.geCurrentPage(launcher);
            currentPosition = screenEdge;
        }
        return currentPosition;
    }

    private static void executeAndWaitForPageScroll(LauncherInstrumentation launcher,
            Runnable command) {
        launcher.executeAndWaitForEvent(command,
                event -> event.getEventType() == TYPE_VIEW_SCROLLED,
                () -> "Page scroll didn't happen", "Scrolling page");
    }

    static void dragIconToHotseat(
            LauncherInstrumentation launcher,
            Launchable launchable,
            Supplier<Point> dest,
            Runnable expectLongClickEvents,
            @Nullable Runnable expectDropEvents) {
        final long downTime = SystemClock.uptimeMillis();
        Point dragStart = launchable.startDrag(
                downTime,
                expectLongClickEvents,
                /* runToSpringLoadedState= */ true);
        Point targetDest = dest.get();

        launcher.movePointer(dragStart, targetDest, DEFAULT_DRAG_STEPS, true,
                downTime, SystemClock.uptimeMillis(), false,
                LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
        dropDraggedIcon(launcher, targetDest, downTime, expectDropEvents,
                /* startsActivity = */ false);
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

    /**
     * @param cellX X position of the widget trying to get.
     * @param cellY Y position of the widget trying to get.
     * @return returns the Widget in the given position in the Launcher or an Exception if no such
     * widget is in that position.
     */
    @NonNull
    public Widget getWidgetAtCell(int cellX, int cellY) {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "getting widget at cell position " + cellX + "," + cellY)) {
            final List<UiObject2> widgets = mLauncher.waitForObjectsBySelector(
                    By.clazz("com.android.launcher3.widget.LauncherAppWidgetHostView"));
            Point coordinateInScreen = Workspace.getCellCenter(mLauncher, cellX, cellY);
            for (UiObject2 widget : widgets) {
                if (widget.getVisibleBounds().contains(coordinateInScreen.x,
                        coordinateInScreen.y)) {
                    return new Widget(mLauncher, widget);
                }
            }
        }
        mLauncher.fail("Unable to find widget at cell " + cellX + "," + cellY);
        // This statement is unreachable because mLauncher.fail throws an exception
        // but is needed for compiling
        return null;
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
