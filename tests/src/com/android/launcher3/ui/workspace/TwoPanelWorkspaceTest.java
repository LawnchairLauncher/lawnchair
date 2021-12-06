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

    Workspace mWorkspace;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        TaplTestsLauncher3.initialize(this);
        mWorkspace = mLauncher.getWorkspace();
    }

    @Test
    public void testDragIconToRightPanel() {
        if (!mLauncher.isTwoPanels()) {
            return;
        }

        // Pre verifying the screens
        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Play Store", "Maps");
            assertPageEmpty(launcher, 1);
        });

        mWorkspace.dragIcon(mWorkspace.getHotseatAppIcon("Chrome"), 1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Maps", "Play Store");
            assertItemsOnPage(launcher, 1, "Chrome");
        });
    }

    @Test
    public void testDragIconToPage2() {
        if (!mLauncher.isTwoPanels()) {
            return;
        }

        // Pre verifying the screens
        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Play Store", "Maps");
            assertPageEmpty(launcher, 1);
        });

        mWorkspace.dragIcon(mWorkspace.getWorkspaceAppIcon("Maps"), 2);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store");
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, "Maps");
            assertPageEmpty(launcher, 3);
        });
    }

    @Test
    public void testDragIconToPage3() {
        if (!mLauncher.isTwoPanels()) {
            return;
        }

        // Pre verifying the screens
        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Play Store", "Maps");
            assertPageEmpty(launcher, 1);
        });

        mWorkspace.dragIcon(mWorkspace.getHotseatAppIcon("Phone"), 3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store", "Maps");
            assertPageEmpty(launcher, 1);
            assertPageEmpty(launcher, 2);
            assertItemsOnPage(launcher, 3, "Phone");
        });
    }


    @Test
    public void testEmptyPageDoesNotGetRemovedIfPagePairIsNotEmpty() {
        if (!mLauncher.isTwoPanels()) {
            return;
        }

        // Pre verifying the screens
        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Play Store", "Maps");
            assertPageEmpty(launcher, 1);
        });

        mWorkspace.dragIcon(mWorkspace.getWorkspaceAppIcon("Maps"), 3);
        mWorkspace.dragIcon(mWorkspace.getHotseatAppIcon("Chrome"), 0);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store");
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, "Chrome");
            assertItemsOnPage(launcher, 3, "Maps");
        });

        mWorkspace.dragIcon(mWorkspace.getWorkspaceAppIcon("Maps"), -1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store");
            assertItemsOnPage(launcher, 1, "Maps");
            assertItemsOnPage(launcher, 2, "Chrome");
            assertPageEmpty(launcher, 3);
        });

        // Move Chrome to the right panel as well, to make sure pages are not deleted whichever
        // page is the empty one
        mWorkspace.flingForward();
        mWorkspace.dragIcon(mWorkspace.getWorkspaceAppIcon("Chrome"), 1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Play Store");
            assertItemsOnPage(launcher, 1, "Maps");
            assertPageEmpty(launcher, 2);
            assertItemsOnPage(launcher, 3, "Chrome");
        });
    }


    @Test
    public void testEmptyPagesGetRemovedIfBothPagesAreEmpty() {
        if (!mLauncher.isTwoPanels()) {
            return;
        }

        // Pre verifying the screens
        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Play Store", "Maps");
            assertPageEmpty(launcher, 1);
        });

        mWorkspace.dragIcon(mWorkspace.getWorkspaceAppIcon("Play Store"), 2);
        mWorkspace.dragIcon(mWorkspace.getHotseatAppIcon("Camera"), 1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3);
            assertItemsOnPage(launcher, 0, "Maps");
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, "Play Store");
            assertItemsOnPage(launcher, 3, "Camera");
        });

        mWorkspace.dragIcon(mWorkspace.getWorkspaceAppIcon("Camera"), -1);
        mWorkspace.flingForward();
        mWorkspace.dragIcon(mWorkspace.getWorkspaceAppIcon("Play Store"), -2);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Play Store", "Maps");
            assertItemsOnPage(launcher, 1, "Camera");
        });
    }

    @Test
    public void testMiddleEmptyPagesGetRemoved() {
        if (!mLauncher.isTwoPanels()) {
            return;
        }

        // Pre verifying the screens
        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, "Play Store", "Maps");
            assertPageEmpty(launcher, 1);
        });

        mWorkspace.dragIcon(mWorkspace.getWorkspaceAppIcon("Maps"), 2);
        mWorkspace.dragIcon(mWorkspace.getHotseatAppIcon("Messages"), 3);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 2, 3, 4, 5);
            assertItemsOnPage(launcher, 0, "Play Store");
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 2, "Maps");
            assertPageEmpty(launcher, 3);
            assertPageEmpty(launcher, 4);
            assertItemsOnPage(launcher, 5, "Messages");
        });

        mWorkspace.flingBackward();
        mWorkspace.dragIcon(mWorkspace.getWorkspaceAppIcon("Maps"), 2);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1, 4, 5);
            assertItemsOnPage(launcher, 0, "Play Store");
            assertPageEmpty(launcher, 1);
            assertItemsOnPage(launcher, 4, "Maps");
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
