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
package com.android.launcher3.ui.workspace;

import static com.android.launcher3.util.TestConstants.AppNames.CHROME_APP_NAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.HomeAppIcon;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.TestUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test the basic interactions of the Workspace, adding pages, moving the pages and removing pages.
 */
public class TaplWorkspaceTest extends AbstractLauncherUiTest<Launcher> {

    private AutoCloseable mLauncherLayout;

    private static boolean isWorkspaceScrollable(Launcher launcher) {
        return launcher.getWorkspace().getPageCount() > launcher.getWorkspace().getPanelCount();
    }

    private int getCurrentWorkspacePage(Launcher launcher) {
        return launcher.getWorkspace().getCurrentPage();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        initialize(this);
    }

    @After
    public void tearDown() throws Exception {
        if (mLauncherLayout != null) {
            mLauncherLayout.close();
        }
    }

    /**
     * Add an icon and add a page to ensure the Workspace is scrollable and also make sure we can
     * move between workspaces. After, make sure we can launch an app from the Workspace.
     * @throws Exception if we can't set the defaults icons that will appear at the beginning.
     */
    @Test
    public void testWorkspace() throws Exception {
        // Set workspace  that includes the chrome Activity app icon on the hotseat.
        LauncherLayoutBuilder builder = new LauncherLayoutBuilder()
                .atHotseat(0).putApp("com.android.chrome", "com.google.android.apps.chrome.Main");
        mLauncherLayout = TestUtil.setLauncherDefaultLayout(mTargetContext, builder);
        reinitializeLauncherData();

        final Workspace workspace = mLauncher.getWorkspace();

        // Test that ensureWorkspaceIsScrollable adds a page by dragging an icon there.
        executeOnLauncher(launcher -> assertFalse("Initial workspace state is scrollable",
                isWorkspaceScrollable(launcher)));
        assertEquals("Initial workspace doesn't have the correct page", workspace.pagesPerScreen(),
                workspace.getPageCount());
        workspace.verifyWorkspaceAppIconIsGone("Chrome app was found on empty workspace",
                CHROME_APP_NAME);
        workspace.ensureWorkspaceIsScrollable();

        executeOnLauncher(
                launcher -> assertEquals(
                        "Ensuring workspace scrollable didn't switch to next screen",
                        workspace.pagesPerScreen(), getCurrentWorkspacePage(launcher)));
        executeOnLauncher(
                launcher -> assertTrue("ensureScrollable didn't make workspace scrollable",
                        isWorkspaceScrollable(launcher)));
        assertNotNull("ensureScrollable didn't add Chrome app",
                workspace.getWorkspaceAppIcon(CHROME_APP_NAME));

        // Test flinging workspace.
        workspace.flingBackward();
        assertTrue("Launcher internal state is not Home", isInState(() -> LauncherState.NORMAL));
        executeOnLauncher(
                launcher -> assertEquals("Flinging back didn't switch workspace to page #0",
                        0, getCurrentWorkspacePage(launcher)));

        workspace.flingForward();
        executeOnLauncher(
                launcher -> assertEquals("Flinging forward didn't switch workspace to next screen",
                        workspace.pagesPerScreen(), getCurrentWorkspacePage(launcher)));
        assertTrue("Launcher internal state is not Home", isInState(() -> LauncherState.NORMAL));

        // Test starting a workspace app.
        final HomeAppIcon app = workspace.getWorkspaceAppIcon(CHROME_APP_NAME);
        assertNotNull("No Chrome app in workspace", app);
    }


    /**
     * Similar to {@link TaplWorkspaceTest#testWorkspace} but here we also make sure we can delete
     * the pages.
     */
    @Test
    public void testAddAndDeletePageAndFling() {
        Workspace workspace = mLauncher.getWorkspace();
        // Get the first app from the hotseat
        HomeAppIcon hotSeatIcon = workspace.getHotseatAppIcon(0);
        String appName = hotSeatIcon.getIconName();

        // Add one page by dragging app to page 1.
        workspace.dragIcon(hotSeatIcon, workspace.pagesPerScreen());
        assertEquals("Incorrect Page count Number",
                workspace.pagesPerScreen() * 2,
                workspace.getPageCount());

        // Delete one page by dragging app to hot seat.
        workspace.getWorkspaceAppIcon(appName).dragToHotseat(0);

        // Refresh workspace to avoid using stale container error.
        workspace = mLauncher.getWorkspace();
        assertEquals("Incorrect Page count Number",
                workspace.pagesPerScreen(),
                workspace.getPageCount());
    }
}
