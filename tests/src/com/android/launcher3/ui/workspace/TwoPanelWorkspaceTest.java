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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.view.View;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.TaplTestsLauncher3;

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
public class TwoPanelWorkspaceTest extends AbstractLauncherUiTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        TaplTestsLauncher3.initialize(this);

        assumeTrue(mLauncher.isTwoPanels());

        // Removing the Gmail widget so there are space in the right panel to run the test.
        Workspace workspace = mLauncher.getWorkspace();
        workspace.deleteWidget(workspace.tryGetWidget("Gmail", DEFAULT_UI_TIMEOUT));

        // Pre verifying the screens
        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "Photos", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
        });
    }

    @Test
    @PortraitLandscape
    public void testDragIconToRightPanel() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getHotseatAppIcon("Chrome"), 1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "Photos", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather", "Chrome");
        });
    }

    @Test
    @PortraitLandscape
    public void testSinglePageDragIconWhenMultiplePageScrollingIsPossible() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getHotseatAppIcon("Chrome"), 2);

        workspace.flingBackward();

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Photos"), 3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
            assertItemsOnPage(launcher, 2, "Chrome");
            assertItemsOnPage(launcher, 3, "Photos");
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Photos"), 3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3, 4, 5);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
            assertItemsOnPage(launcher, 2, "Chrome");
            assertPageEmpty(launcher, 3);
            assertPageEmpty(launcher, 4);
            assertItemsOnPage(launcher, 5, "Photos");
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Photos"), -1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
            assertItemsOnPage(launcher, 2, "Chrome");
            assertItemsOnPage(launcher, 3, "Photos");
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Photos"), -1);

        workspace.flingForward();

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Chrome"), -2);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Chrome", "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather", "Photos");
        });
    }

    @Test
    @PortraitLandscape
    public void testDragIconToPage2() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Photos"), 2);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
            assertItemsOnPage(launcher, 2, "Photos");
            assertPageEmpty(launcher, 3);
        });
    }

    @Test
    @PortraitLandscape
    public void testDragIconToPage3() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getHotseatAppIcon("Phone"), 3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "Photos", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
            assertPageEmpty(launcher, 2);
            assertItemsOnPage(launcher, 3, "Phone");
        });
    }

    @Test
    @PortraitLandscape
    public void testMultiplePageDragIcon() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getHotseatAppIcon("Messages"), 2);

        workspace.flingBackward();

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Photos"), 5);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3, 4, 5);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
            assertItemsOnPage(launcher, 2, "Messages");
            assertPageEmpty(launcher, 3);
            assertPageEmpty(launcher, 4);
            assertItemsOnPage(launcher, 5, "Photos");
        });

        workspace.flingBackward();

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Messages"), 4);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 4, 5, 6, 7);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
            assertPageEmpty(launcher, 4);
            assertItemsOnPage(launcher, 5, "Photos");
            assertItemsOnPage(launcher, 6, "Messages");
            assertPageEmpty(launcher, 7);
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Messages"), -3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 4, 5);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather", "Messages");
            assertPageEmpty(launcher, 4);
            assertItemsOnPage(launcher, 5, "Photos");
        });
    }

    @Test
    @PortraitLandscape
    public void testEmptyPageDoesNotGetRemovedIfPagePairIsNotEmpty() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Photos"), 3);
        workspace.dragIcon(workspace.getHotseatAppIcon("Chrome"), 0);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
            assertItemsOnPage(launcher, 2, "Chrome");
            assertItemsOnPage(launcher, 3, "Photos");
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Photos"), -1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather", "Photos");
            assertItemsOnPage(launcher, 2, "Chrome");
            assertPageEmpty(launcher, 3);
        });

        // Move Chrome to the right panel as well, to make sure pages are not deleted whichever
        // page is the empty one
        workspace.flingForward();
        workspace.dragIcon(workspace.getWorkspaceAppIcon("Chrome"), 1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather", "Photos");
            assertPageEmpty(launcher, 2);
            assertItemsOnPage(launcher, 3, "Chrome");
        });
    }

    @Test
    @PortraitLandscape
    public void testEmptyPagesGetRemovedIfBothPagesAreEmpty() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Play Store"), 2);
        workspace.dragIcon(workspace.getHotseatAppIcon("Chrome"), 1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Gmail", "Photos", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
            assertItemsOnPage(launcher, 2, "Play Store");
            assertItemsOnPage(launcher, 3, "Chrome");
        });

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Chrome"), -1);
        workspace.flingForward();
        workspace.dragIcon(workspace.getWorkspaceAppIcon("Play Store"), -2);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "Photos", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather", "Chrome");
        });
    }

    @Test
    @PortraitLandscape
    public void testMiddleEmptyPagesGetRemoved() {
        Workspace workspace = mLauncher.getWorkspace();

        workspace.dragIcon(workspace.getWorkspaceAppIcon("Photos"), 2);
        workspace.dragIcon(workspace.getHotseatAppIcon("Messages"), 3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3, 4, 5);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
            assertItemsOnPage(launcher, 2, "Photos");
            assertPageEmpty(launcher, 3);
            assertPageEmpty(launcher, 4);
            assertItemsOnPage(launcher, 5, "Messages");
        });

        workspace.flingBackward();
        workspace.dragIcon(workspace.getWorkspaceAppIcon("Photos"), 2);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 4, 5);
            assertItemsOnPage(launcher, 0, "Play Store", "Gmail", "YouTube");
            assertItemsOnPage(launcher, 1, "Weather");
            assertItemsOnPage(launcher, 4, "Photos");
            assertItemsOnPage(launcher, 5, "Messages");
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
            CharSequence title = null;
            View child = page.getShortcutsAndWidgets().getChildAt(i);
            ItemInfo itemInfo = (ItemInfo) child.getTag();
            if (itemInfo != null) {
                title = itemInfo.title;
            }
            if (title == null) {
                title = child.getContentDescription();
            }
            if (title != null) {
                assertTrue("There was an extra item on page " + pageId + ": " + title,
                        itemTitleSet.remove(title));
            }
        }
        assertTrue("Could NOT find some of the items on page " + pageId + ": "
                        + String.join(",", itemTitleSet), itemTitleSet.isEmpty());
    }
}
