package com.android.launcher3.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.util.Pair;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSparseArrayMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tests for {@link AddWorkspaceItemsTask}
 */
@RunWith(RobolectricTestRunner.class)
public class AddWorkspaceItemsTaskTest extends BaseModelUpdateTaskTestCase {

    private final ComponentName mComponent1 = new ComponentName("a", "b");
    private final ComponentName mComponent2 = new ComponentName("b", "b");

    private IntArray existingScreens;
    private IntArray newScreens;
    private IntSparseArrayMap<GridOccupancy> screenOccupancy;

    @Before
    public void initData() throws Exception {
        existingScreens = new IntArray();
        screenOccupancy = new IntSparseArrayMap<>();
        newScreens = new IntArray();

        idp.numColumns = 5;
        idp.numRows = 5;
    }

    private AddWorkspaceItemsTask newTask(ItemInfo... items) {
        List<Pair<ItemInfo, Object>> list = new ArrayList<>();
        for (ItemInfo item : items) {
            list.add(Pair.create(item, null));
        }
        return new AddWorkspaceItemsTask(list);
    }

    @Test
    public void testFindSpaceForItem_prefers_second() throws Exception {
        // First screen has only one hole of size 1
        int nextId = setupWorkspaceWithHoles(1, 1, new Rect(2, 2, 3, 3));

        // Second screen has 2 holes of sizes 3x2 and 2x3
        setupWorkspaceWithHoles(nextId, 2, new Rect(2, 0, 5, 2), new Rect(0, 2, 2, 5));

        int[] spaceFound = newTask()
                .findSpaceForItem(appState, bgDataModel, existingScreens, newScreens, 1, 1);
        assertEquals(2, spaceFound[0]);
        assertTrue(screenOccupancy.get(spaceFound[0])
                .isRegionVacant(spaceFound[1], spaceFound[2], 1, 1));

        // Find a larger space
        spaceFound = newTask()
                .findSpaceForItem(appState, bgDataModel, existingScreens, newScreens, 2, 3);
        assertEquals(2, spaceFound[0]);
        assertTrue(screenOccupancy.get(spaceFound[0])
                .isRegionVacant(spaceFound[1], spaceFound[2], 2, 3));
    }

    @Test
    public void testFindSpaceForItem_adds_new_screen() throws Exception {
        // First screen has 2 holes of sizes 3x2 and 2x3
        setupWorkspaceWithHoles(1, 1, new Rect(2, 0, 5, 2), new Rect(0, 2, 2, 5));

        IntArray oldScreens = existingScreens.clone();
        int[] spaceFound = newTask()
                .findSpaceForItem(appState, bgDataModel, existingScreens, newScreens, 3, 3);
        assertFalse(oldScreens.contains(spaceFound[0]));
        assertTrue(newScreens.contains(spaceFound[0]));
    }

    @Test
    public void testAddItem_existing_item_ignored() throws Exception {
        WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.intent = new Intent().setComponent(mComponent1);

        // Setup a screen with a hole
        setupWorkspaceWithHoles(1, 1, new Rect(2, 2, 3, 3));

        // Nothing was added
        assertTrue(executeTaskForTest(newTask(info)).isEmpty());
    }

    @Test
    public void testAddItem_some_items_added() throws Exception {
        WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.intent = new Intent().setComponent(mComponent1);

        WorkspaceItemInfo info2 = new WorkspaceItemInfo();
        info2.intent = new Intent().setComponent(mComponent2);

        // Setup a screen with a hole
        setupWorkspaceWithHoles(1, 1, new Rect(2, 2, 3, 3));

        executeTaskForTest(newTask(info, info2)).get(0).run();
        ArgumentCaptor<ArrayList> notAnimated = ArgumentCaptor.forClass(ArrayList.class);
        ArgumentCaptor<ArrayList> animated = ArgumentCaptor.forClass(ArrayList.class);

        // only info2 should be added because info was already added to the workspace
        // in setupWorkspaceWithHoles()
        verify(callbacks).bindAppsAdded(any(IntArray.class), notAnimated.capture(),
                animated.capture());
        assertTrue(notAnimated.getValue().isEmpty());

        assertEquals(1, animated.getValue().size());
        assertTrue(animated.getValue().contains(info2));
    }

    private int setupWorkspaceWithHoles(int startId, int screenId, Rect... holes) throws Exception {
        GridOccupancy occupancy = new GridOccupancy(idp.numColumns, idp.numRows);
        occupancy.markCells(0, 0, idp.numColumns, idp.numRows, true);
        for (Rect r : holes) {
            occupancy.markCells(r, false);
        }

        existingScreens.add(screenId);
        screenOccupancy.append(screenId, occupancy);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        for (int x = 0; x < idp.numColumns; x++) {
            for (int y = 0; y < idp.numRows; y++) {
                if (!occupancy.cells[x][y]) {
                    continue;
                }

                WorkspaceItemInfo info = new WorkspaceItemInfo();
                info.intent = new Intent().setComponent(mComponent1);
                info.id = startId++;
                info.screenId = screenId;
                info.cellX = x;
                info.cellY = y;
                info.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
                bgDataModel.addItem(targetContext, info, false);

                executor.execute(() -> {
                    ContentWriter writer = new ContentWriter(targetContext);
                    info.writeToValues(writer);
                    writer.put(Favorites._ID, info.id);
                    targetContext.getContentResolver().insert(Favorites.CONTENT_URI,
                            writer.getValues(targetContext));
                });
            }
        }

        executor.submit(() -> null).get();
        executor.shutdown();
        return startId;
    }
}
