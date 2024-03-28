/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static com.android.launcher3.util.TestConstants.AppNames.MAPS_APP_NAME;
import static com.android.launcher3.util.TestConstants.AppNames.MESSAGES_APP_NAME;
import static com.android.launcher3.util.TestConstants.AppNames.STORE_APP_NAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.TestUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests for two panel workspace.
 *
 * Note running these tests will clear the workspace on the device.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTwoPanelWorkspaceTest extends AbstractLauncherUiTest {

    private AutoCloseable mLauncherLayout;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        // Set layout that includes Maps/Play on workspace, and Messaging/Chrome on hotseat.
        LauncherLayoutBuilder builder = new LauncherLayoutBuilder()
                .atHotseat(0).putApp(
                        "com.google.android.apps.messaging",
                        "com.google.android.apps.messaging.ui.ConversationListActivity")
                .atHotseat(1).putApp("com.android.chrome", "com.google.android.apps.chrome.Main")
                .atWorkspace(0, -1, 0).putApp(
                        "com.google.android.apps.maps", "com.google.android.maps.MapsActivity")
                .atWorkspace(3, -1, 0).putApp(
                        "com.android.vending", "com.android.vending.AssetBrowserActivity");
        mLauncherLayout = TestUtil.setLauncherDefaultLayout(mTargetContext, builder);
        AbstractLauncherUiTest.initialize(this);
        assumeTrue(mLauncher.isTwoPanels());

        // Pre verifying the screens
        executeOnLauncher(launcher -> {
            launcher.enableHotseatEdu(false);
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME, MAPS_APP_NAME);
            assertPageEmpty(launcher, 1);
        });
    }

    @After
    public void tearDown() throws Exception {
        executeOnLauncherInTearDown(launcher -> launcher.enableHotseatEdu(true));
        if (mLauncherLayout != null) {
            mLauncherLayout.close();
        }
    }

    @Test
    @PortraitLandscape
    public void testDragIconToRightPanel() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getHotseatAppIcon(CHROME_APP_NAME), 1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, MAPS_APP_NAME, STORE_APP_NAME);
            assertItemsOnPage(launcher, 1, CHROME_APP_NAME);
        });
    }

    @Test
    @PortraitLandscape
    public void testSinglePageDragIconWhenMultiplePageScrollingIsPossible() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getHotseatAppIcon(CHROME_APP_NAME), 2);

        workspace.flingBackward();

        workspace.dragIcon(workspace.getWorkspaceAppIcon(MAPS_APP_NAME), 3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, CHROME_APP_NAME);
            assertItemsOnPage(launcher, 3, MAPS_APP_NAME);
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon(MAPS_APP_NAME), 3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3, 4, 5);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, CHROME_APP_NAME);
            assertPageEmpty(launcher, 3);
            assertPageEmpty(launcher, 4);
            assertItemsOnPage(launcher, 5, MAPS_APP_NAME);
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon(MAPS_APP_NAME), -1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, CHROME_APP_NAME);
            assertItemsOnPage(launcher, 3, MAPS_APP_NAME);
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon(MAPS_APP_NAME), -1);

        workspace.flingForward();

        workspace.dragIcon(workspace.getWorkspaceAppIcon(CHROME_APP_NAME), -2);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, CHROME_APP_NAME, STORE_APP_NAME);
            assertItemsOnPage(launcher, 1, MAPS_APP_NAME);
        });
    }

    @Test
    @PortraitLandscape
    public void testDragIconToPage2() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getWorkspaceAppIcon(MAPS_APP_NAME), 2);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, MAPS_APP_NAME);
            assertPageEmpty(launcher, 3);
        });
    }

    @Test
    @PortraitLandscape
    public void testDragIconToPage3() {
        Workspace workspace = mLauncher.getWorkspace();

        // b/299522368 sometimes the phone app is not present in the hotseat.
        workspace.dragIcon(workspace.getHotseatAppIcon(CHROME_APP_NAME), 3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME, MAPS_APP_NAME);
            assertPageEmpty(launcher, 1);
            assertPageEmpty(launcher, 2);
            assertItemsOnPage(launcher, 3, CHROME_APP_NAME);
        });
    }

    @Test
    @PortraitLandscape
    public void testMultiplePageDragIcon() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getHotseatAppIcon(MESSAGES_APP_NAME), 2);

        workspace.flingBackward();

        workspace.dragIcon(workspace.getWorkspaceAppIcon(MAPS_APP_NAME), 5);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3, 4, 5);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, MESSAGES_APP_NAME);
            assertPageEmpty(launcher, 3);
            assertPageEmpty(launcher, 4);
            assertItemsOnPage(launcher, 5, MAPS_APP_NAME);
        });

        workspace.flingBackward();

        workspace.dragIcon(workspace.getWorkspaceAppIcon(MESSAGES_APP_NAME), 4);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 4, 5, 6, 7);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertPageEmpty(launcher, 1);
            assertPageEmpty(launcher, 4);
            assertItemsOnPage(launcher, 5, MAPS_APP_NAME);
            assertItemsOnPage(launcher, 6, MESSAGES_APP_NAME);
            assertPageEmpty(launcher, 7);
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon(MESSAGES_APP_NAME), -3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 4, 5);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertItemsOnPage(launcher, 1, MESSAGES_APP_NAME);
            assertPageEmpty(launcher, 4);
            assertItemsOnPage(launcher, 5, MAPS_APP_NAME);
        });
    }

    @Test
    @PortraitLandscape
    public void testEmptyPageDoesNotGetRemovedIfPagePairIsNotEmpty() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getWorkspaceAppIcon(MAPS_APP_NAME), 3);
        workspace.dragIcon(workspace.getHotseatAppIcon(CHROME_APP_NAME), 0);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, CHROME_APP_NAME);
            assertItemsOnPage(launcher, 3, MAPS_APP_NAME);
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon(MAPS_APP_NAME), -1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertItemsOnPage(launcher, 1, MAPS_APP_NAME);
            assertItemsOnPage(launcher, 2, CHROME_APP_NAME);
            assertPageEmpty(launcher, 3);
        });

        // Move Chrome to the right panel as well, to make sure pages are not deleted whichever
        // page is the empty one
        workspace.flingForward();
        workspace.dragIcon(workspace.getWorkspaceAppIcon(CHROME_APP_NAME), 1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertItemsOnPage(launcher, 1, MAPS_APP_NAME);
            assertPageEmpty(launcher, 2);
            assertItemsOnPage(launcher, 3, CHROME_APP_NAME);
        });
    }

    @Test
    @PortraitLandscape
    public void testEmptyPagesGetRemovedIfBothPagesAreEmpty() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getWorkspaceAppIcon(STORE_APP_NAME), 2);
        workspace.dragIcon(workspace.getHotseatAppIcon(CHROME_APP_NAME), 1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, MAPS_APP_NAME);
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, STORE_APP_NAME);
            assertItemsOnPage(launcher, 3, CHROME_APP_NAME);
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon(CHROME_APP_NAME), -1);
        workspace.flingForward();
        workspace.dragIcon(workspace.getWorkspaceAppIcon(STORE_APP_NAME), -2);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME, MAPS_APP_NAME);
            assertItemsOnPage(launcher, 1, CHROME_APP_NAME);
        });
    }

    @Test
    @PortraitLandscape
    public void testMiddleEmptyPagesGetRemoved() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getWorkspaceAppIcon(MAPS_APP_NAME), 2);
        workspace.dragIcon(workspace.getHotseatAppIcon(MESSAGES_APP_NAME), 3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3, 4, 5);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, MAPS_APP_NAME);
            assertPageEmpty(launcher, 3);
            assertPageEmpty(launcher, 4);
            assertItemsOnPage(launcher, 5, MESSAGES_APP_NAME);
        });

        workspace.flingBackward();
        workspace.dragIcon(workspace.getWorkspaceAppIcon(MAPS_APP_NAME), 2);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 4, 5);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME);
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 4, MAPS_APP_NAME);
            assertItemsOnPage(launcher, 5, MESSAGES_APP_NAME);
        });
    }

    private void assertPageEmpty(Launcher launcher, int pageId) {
        CellLayout page = launcher.getWorkspace().getScreenWithId(pageId);
        assertNotNull("Page " + pageId + " does NOT exist.", page);
        assertEquals("Page " + pageId + " is NOT empty. Number of items on the page:", 0,
                page.getShortcutsAndWidgets().getChildCount());
    }

    private void assertPagesExist(Launcher launcher, int... pageIds) {
        int pageCount = launcher.getWorkspace().getPageCount();
        assertEquals("Existing page count does NOT match.", pageIds.length, pageCount);
        for (int i = 0; i < pageCount; i++) {
            CellLayout page = (CellLayout) launcher.getWorkspace().getPageAt(i);
            int pageId = launcher.getWorkspace().getIdForScreen(page);
            assertEquals("The page's id at index " + i + " does NOT match.", pageId,
                    pageIds[i]);
        }
    }

    private void assertItemsOnPage(Launcher launcher, int pageId, String... itemTitles) {
        Set<String> itemTitleSet = Arrays.stream(itemTitles).collect(Collectors.toSet());
        CellLayout page = launcher.getWorkspace().getScreenWithId(pageId);
        int itemCount = page.getShortcutsAndWidgets().getChildCount();
        for (int i = 0; i < itemCount; i++) {
            ItemInfo itemInfo = (ItemInfo) page.getShortcutsAndWidgets().getChildAt(i).getTag();
            if (itemInfo != null) {
                assertTrue("There was an extra item on page " + pageId + ": " + itemInfo.title,
                        itemTitleSet.remove(itemInfo.title));
            }
        }
        assertTrue("Could NOT find some of the items on page " + pageId + ": "
                        + itemTitleSet.stream().collect(Collectors.joining(",")),
                itemTitleSet.isEmpty());
    }
}