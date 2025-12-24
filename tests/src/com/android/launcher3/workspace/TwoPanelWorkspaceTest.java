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

package com.android.launcher3.workspace;

import static com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE;
import static com.android.launcher3.util.WorkspaceDragHelper.className;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.test.core.app.ActivityScenario.ActivityAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.launcher3.CellLayout;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.BaseLauncherActivityTest;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.LauncherModelHelper;
import com.android.launcher3.util.LayoutResource;
import com.android.launcher3.util.ModelTestExtensions;
import com.android.launcher3.util.WorkspaceDragHelper;

import org.junit.Before;
import org.junit.Rule;
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
public class TwoPanelWorkspaceTest extends BaseLauncherActivityTest<Launcher> {

    @Rule public LayoutResource layoutRule = new LayoutResource(targetContext());

    private static final String CHROME_APP_NAME = LauncherModelHelper.TEST_ACTIVITY;
    private static final String MAPS_APP_NAME = LauncherModelHelper.TEST_ACTIVITY2;
    private static final String MESSAGES_APP_NAME = LauncherModelHelper.TEST_ACTIVITY3;
    private static final String STORE_APP_NAME = LauncherModelHelper.TEST_ACTIVITY4;

    @Before
    public void setUp() throws Exception {
        assumeTrue("Ignoring test because Launcher doesn't have two panels",
                InvariantDeviceProfile.INSTANCE.get(targetContext())
                        .getDeviceProfile(targetContext()).getDeviceProperties().isTwoPanels());

        // Set layout that includes Maps/Play on workspace, and Messaging/Chrome on hotseat.
        LauncherLayoutBuilder builder = new LauncherLayoutBuilder()
                .atHotseat(0).putApp(TEST_PACKAGE, MESSAGES_APP_NAME)
                .atHotseat(1).putApp(TEST_PACKAGE, CHROME_APP_NAME)
                .atWorkspace(0, -1, 0).putApp(TEST_PACKAGE, MAPS_APP_NAME)
                .atWorkspace(3, -1, 0).putApp(TEST_PACKAGE, STORE_APP_NAME);
        layoutRule.set(builder);
        loadLauncherSync();

        // Pre verifying the screens
        executeOnLauncher(launcher -> {
            launcher.enableHotseatEdu(false);
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, STORE_APP_NAME, MAPS_APP_NAME);
            assertPageEmpty(launcher, 1);
        });
    }

    @Test
    public void testDragIconToRightPanel() {
        WorkspaceDragHelper workspace = new WorkspaceDragHelper(getLauncherActivity());

        workspace.dragIcon(workspace.getHotseatAppIcon(CHROME_APP_NAME), 1);

        executeOnLauncher(launcher -> {
            assertPagesExist(launcher, 0, 1);
            assertItemsOnPage(launcher, 0, MAPS_APP_NAME, STORE_APP_NAME);
            assertItemsOnPage(launcher, 1, CHROME_APP_NAME);
        });
    }

    @Test
    public void testSinglePageDragIconWhenMultiplePageScrollingIsPossible() {
        WorkspaceDragHelper workspace = new WorkspaceDragHelper(getLauncherActivity());

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
    public void testDragIconToPage2() {
        WorkspaceDragHelper workspace = new WorkspaceDragHelper(getLauncherActivity());

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
    public void testDragIconToPage3() {
        WorkspaceDragHelper workspace = new WorkspaceDragHelper(getLauncherActivity());

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
    public void testMultiplePageDragIcon() {
        WorkspaceDragHelper workspace = new WorkspaceDragHelper(getLauncherActivity());

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
    public void testEmptyPageDoesNotGetRemovedIfPagePairIsNotEmpty() {
        WorkspaceDragHelper workspace = new WorkspaceDragHelper(getLauncherActivity());

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
    public void testEmptyPagesGetRemovedIfBothPagesAreEmpty() {
        WorkspaceDragHelper workspace = new WorkspaceDragHelper(getLauncherActivity());

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
    public void testMiddleEmptyPagesGetRemoved() {
        WorkspaceDragHelper workspace = new WorkspaceDragHelper(getLauncherActivity());

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

    private void executeOnLauncher(ActivityAction<Launcher> action) {
        getLauncherActivity().executeOnLauncher(action);
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
            int pageId = launcher.getWorkspace().getCellLayoutId(page);
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
            if (itemInfo != null && ModelTestExtensions.isPersistedModelItem(itemInfo)) {
                assertTrue("There was an extra item on page " + pageId + ": " + className(itemInfo),
                        itemTitleSet.remove(className(itemInfo)));
            }
        }
        assertTrue("Could NOT find some of the items on page " + pageId + ": "
                        + itemTitleSet.stream().collect(Collectors.joining(",")),
                itemTitleSet.isEmpty());
    }
}
