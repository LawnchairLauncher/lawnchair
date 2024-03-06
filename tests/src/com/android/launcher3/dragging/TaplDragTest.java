/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.dragging;

import static com.android.launcher3.util.TestConstants.AppNames.TEST_APP_NAME;
import static com.android.launcher3.util.TestConstants.AppNames.GMAIL_APP_NAME;
import static com.android.launcher3.util.TestConstants.AppNames.MAPS_APP_NAME;
import static com.android.launcher3.util.TestConstants.AppNames.STORE_APP_NAME;
import static com.android.launcher3.ui.AbstractLauncherUiTest.initialize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Point;
import android.os.SystemClock;
import android.platform.test.annotations.PlatinumTest;
import android.util.Log;

import com.android.launcher3.tapl.Folder;
import com.android.launcher3.tapl.FolderIcon;
import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.tapl.HomeAppIcon;
import com.android.launcher3.tapl.HomeAppIconMenuItem;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.rule.ScreenRecordRule.ScreenRecord;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This test run in both Out of process (Oop) and in-process (Ipc).
 * Tests multiple facets of the drag interaction in the Launcher:
 *    * Can create a folder by dragging items.
 *    * Can create a shortcut by dragging it to Workspace.
 *    * Can create shortcuts in multiple spaces in the Workspace.
 *    * Can cancel a drag icon to workspace by dragging outside of the Workspace.
 *    * Can drag an icon from AllApps into the workspace
 *    * Can drag an icon on the Workspace to other positions of the Workspace.
 */
public class TaplDragTest extends AbstractLauncherUiTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        initialize(this);
    }

    /**
     * Adds two icons to the Workspace and combines them into a folder, then makes sure the icons
     * are no longer in the Workspace then adds a third one to test adding an icon to an existing
     * folder instead of creating one and drags it to the folder.
     */
    @Test
    @PortraitLandscape
    @ScreenRecord
    @Ignore // b/233075289
    @PlatinumTest(focusArea = "launcher")
    public void testDragToFolder() {
        // TODO: add the use case to drag an icon to an existing folder. Currently it either fails
        // on tablets or phones due to difference in resolution.
        final HomeAppIcon playStoreIcon = createShortcutIfNotExist(STORE_APP_NAME, 0, 1);
        final HomeAppIcon gmailIcon = createShortcutInCenterIfNotExist(GMAIL_APP_NAME);

        FolderIcon folderIcon = gmailIcon.dragToIcon(playStoreIcon);
        Folder folder = folderIcon.open();
        folder.getAppIcon(STORE_APP_NAME);
        folder.getAppIcon(GMAIL_APP_NAME);
        Workspace workspace = folder.close();

        workspace.verifyWorkspaceAppIconIsGone(STORE_APP_NAME + " should be moved to a folder.",
                STORE_APP_NAME);
        workspace.verifyWorkspaceAppIconIsGone(GMAIL_APP_NAME + " should be moved to a folder.",
                GMAIL_APP_NAME);

        final HomeAppIcon mapIcon = createShortcutInCenterIfNotExist(MAPS_APP_NAME);
        folderIcon = mapIcon.dragToIcon(folderIcon);
        folder = folderIcon.open();
        folder.getAppIcon(MAPS_APP_NAME);
        workspace = folder.close();

        workspace.verifyWorkspaceAppIconIsGone(MAPS_APP_NAME + " should be moved to a folder.",
                MAPS_APP_NAME);
    }


    /** Drags a shortcut from a long press menu into the workspace.
     * 1. Open all apps and wait for load complete.
     * 2. Find the app and long press it to show shortcuts.
     * 3. Press icon center until shortcuts appear
     * 4. Drags shortcut to any free space in the Workspace.
     */
    @Test
    @PortraitLandscape
    @PlatinumTest(focusArea = "launcher")
    public void testDragShortcut() {

        final HomeAllApps allApps = mLauncher
                .getWorkspace()
                .switchToAllApps();
        allApps.freeze();
        try {
            final HomeAppIconMenuItem menuItem = allApps
                    .getAppIcon(TEST_APP_NAME)
                    .openDeepShortcutMenu()
                    .getMenuItem(0);
            final String actualShortcutName = menuItem.getText();
            final String expectedShortcutName = "Shortcut 1";

            assertEquals(expectedShortcutName, actualShortcutName);
            menuItem.dragToWorkspace(false, false);
            mLauncher.getWorkspace().getWorkspaceAppIcon(expectedShortcutName)
                    .launch(getAppPackageName());
        } finally {
            allApps.unfreeze();
        }
    }

    /**
     * Similar to testDragShortcut but it adds shortcuts to multiple positions of the Workspace
     * namely the corners and the center.
     */
    @Test
    @PortraitLandscape
    @PlatinumTest(focusArea = "launcher")
    public void testDragShortcutToMultipleWorkspaceCells() {
        Point[] targets = TestUtil.getCornersAndCenterPositions(mLauncher);

        for (Point target : targets) {
            final HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
            allApps.freeze();
            try {
                allApps.getAppIcon(TEST_APP_NAME)
                        .openDeepShortcutMenu()
                        .getMenuItem(0)
                        .dragToWorkspace(target.x, target.y);
            } finally {
                allApps.unfreeze();
            }
        }
    }

    /**
     * Drags an icon to the workspace but instead of submitting it, it gets dragged outside of the
     * Workspace to cancel it.
     */

    @Test
    @PortraitLandscape
    @PlatinumTest(focusArea = "launcher")
    public void testDragAndCancelAppIcon() {
        final HomeAppIcon homeAppIcon = createShortcutInCenterIfNotExist(GMAIL_APP_NAME);
        Point positionBeforeDrag =
                mLauncher.getWorkspace().getWorkspaceIconsPositions().get(GMAIL_APP_NAME);
        assertNotNull("App not found in Workspace before dragging.", positionBeforeDrag);

        mLauncher.getWorkspace().dragAndCancelAppIcon(homeAppIcon);

        Point positionAfterDrag =
                mLauncher.getWorkspace().getWorkspaceIconsPositions().get(GMAIL_APP_NAME);
        assertNotNull("App not found in Workspace after dragging.", positionAfterDrag);
        assertEquals("App not returned to same position in Workspace after drag & cancel",
                positionBeforeDrag, positionAfterDrag);
    }

    /**
     * Drags app icon from AllApps into the Workspace.
     * 1. Open all apps and wait for load complete.
     * 2. Drag icon to homescreen.
     * 3. Verify that the icon works on homescreen.
     */
    @PlatinumTest(focusArea = "launcher")
    @Test
    @PortraitLandscape
    public void testDragAppIcon() {

        final HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();
        try {
            allApps.getAppIcon(TEST_APP_NAME).dragToWorkspace(false, false);
            mLauncher.getWorkspace().getWorkspaceAppIcon(TEST_APP_NAME).launch(getAppPackageName());
        } finally {
            allApps.unfreeze();
        }
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInLaunchedApp(launcher)));
    }

    /**
     * Similar start to testDragAppIcon but after dragging the icon to the workspace, it drags the
     * icon inside of the workspace to different positions.
     * @throws Exception
     */
    @Test
    @PortraitLandscape
    @PlatinumTest(focusArea = "launcher")
    public void testDragAppIconToMultipleWorkspaceCells() throws Exception {
        long startTime, endTime, elapsedTime;
        Point[] targets = TestUtil.getCornersAndCenterPositions(mLauncher);

        for (Point target : targets) {
            startTime = SystemClock.uptimeMillis();
            final HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
            allApps.freeze();
            try {
                allApps.getAppIcon(TEST_APP_NAME).dragToWorkspace(target.x, target.y);
            } finally {
                allApps.unfreeze();
            }
            // Reset the workspace for the next shortcut creation.
            initialize(this, true);
            endTime = SystemClock.uptimeMillis();
            elapsedTime = endTime - startTime;
            Log.d("testDragAppIconToWorkspaceCellTime",
                    "Milliseconds taken to drag app icon to workspace cell: " + elapsedTime);
        }

        // test to move a shortcut to other cell.
        final HomeAppIcon launcherTestAppIcon = createShortcutInCenterIfNotExist(TEST_APP_NAME);
        for (Point target : targets) {
            startTime = SystemClock.uptimeMillis();
            launcherTestAppIcon.dragToWorkspace(target.x, target.y);
            endTime = SystemClock.uptimeMillis();
            elapsedTime = endTime - startTime;
            Log.d("testDragAppIconToWorkspaceCellTime",
                    "Milliseconds taken to move shortcut to other cell: " + elapsedTime);
        }
    }
}
