package com.android.launcher3.model;

import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Pair;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.LongArrayMap;
import com.android.launcher3.util.Provider;

import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AddWorkspaceItemsTask}
 */
public class AddWorkspaceItemsTaskTest extends BaseModelUpdateTaskTestCase {

    private final ComponentName mComponent1 = new ComponentName("a", "b");
    private final ComponentName mComponent2 = new ComponentName("b", "b");

    private ArrayList<Long> existingScreens;
    private ArrayList<Long> newScreens;
    private LongArrayMap<GridOccupancy> screenOccupancy;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        existingScreens = new ArrayList<>();
        screenOccupancy = new LongArrayMap<>();
        newScreens = new ArrayList<>();

        idp.numColumns = 5;
        idp.numRows = 5;
    }

    private AddWorkspaceItemsTask newTask(ItemInfo... items) {
        List<Pair<ItemInfo, Object>> list = new ArrayList<>();
        for (ItemInfo item : items) {
            list.add(Pair.create(item, null));
        }
        return new AddWorkspaceItemsTask(Provider.of(list)) {

            @Override
            protected void updateScreens(Context context, ArrayList<Long> workspaceScreens) { }
        };
    }

    public void testFindSpaceForItem_prefers_second() {
        // First screen has only one hole of size 1
        int nextId = setupWorkspaceWithHoles(1, 1, new Rect(2, 2, 3, 3));

        // Second screen has 2 holes of sizes 3x2 and 2x3
        setupWorkspaceWithHoles(nextId, 2, new Rect(2, 0, 5, 2), new Rect(0, 2, 2, 5));

        Pair<Long, int[]> spaceFound = newTask()
                .findSpaceForItem(appState, bgDataModel, existingScreens, newScreens, 1, 1);
        assertEquals(2L, (long) spaceFound.first);
        assertTrue(screenOccupancy.get(spaceFound.first)
                .isRegionVacant(spaceFound.second[0], spaceFound.second[1], 1, 1));

        // Find a larger space
        spaceFound = newTask()
                .findSpaceForItem(appState, bgDataModel, existingScreens, newScreens, 2, 3);
        assertEquals(2L, (long) spaceFound.first);
        assertTrue(screenOccupancy.get(spaceFound.first)
                .isRegionVacant(spaceFound.second[0], spaceFound.second[1], 2, 3));
    }

    public void testFindSpaceForItem_adds_new_screen() throws Exception {
        // First screen has 2 holes of sizes 3x2 and 2x3
        setupWorkspaceWithHoles(1, 1, new Rect(2, 0, 5, 2), new Rect(0, 2, 2, 5));
        commitScreensToDb();

        when(appState.getContext()).thenReturn(getMockContext());

        ArrayList<Long> oldScreens = new ArrayList<>(existingScreens);
        Pair<Long, int[]> spaceFound = newTask()
                .findSpaceForItem(appState, bgDataModel, existingScreens, newScreens, 3, 3);
        assertFalse(oldScreens.contains(spaceFound.first));
        assertTrue(newScreens.contains(spaceFound.first));
    }

    public void testAddItem_existing_item_ignored() throws Exception {
        ShortcutInfo info = new ShortcutInfo();
        info.intent = new Intent().setComponent(mComponent1);

        // Setup a screen with a hole
        setupWorkspaceWithHoles(1, 1, new Rect(2, 2, 3, 3));
        commitScreensToDb();

        when(appState.getContext()).thenReturn(getMockContext());

        // Nothing was added
        assertTrue(executeTaskForTest(newTask(info)).isEmpty());
    }

    public void testAddItem_some_items_added() throws Exception {
        ShortcutInfo info = new ShortcutInfo();
        info.intent = new Intent().setComponent(mComponent1);

        ShortcutInfo info2 = new ShortcutInfo();
        info2.intent = new Intent().setComponent(mComponent2);

        // Setup a screen with a hole
        setupWorkspaceWithHoles(1, 1, new Rect(2, 2, 3, 3));
        commitScreensToDb();

        when(appState.getContext()).thenReturn(getMockContext());

        executeTaskForTest(newTask(info, info2)).get(0).run();
        ArgumentCaptor<ArrayList> notAnimated = ArgumentCaptor.forClass(ArrayList.class);
        ArgumentCaptor<ArrayList> animated = ArgumentCaptor.forClass(ArrayList.class);

        // only info2 should be added because info was already added to the workspace
        // in setupWorkspaceWithHoles()
        verify(callbacks).bindAppsAdded(any(ArrayList.class), notAnimated.capture(),
                animated.capture());
        assertTrue(notAnimated.getValue().isEmpty());

        assertEquals(1, animated.getValue().size());
        assertTrue(animated.getValue().contains(info2));
    }

    private int setupWorkspaceWithHoles(int startId, long screenId, Rect... holes) {
        GridOccupancy occupancy = new GridOccupancy(idp.numColumns, idp.numRows);
        occupancy.markCells(0, 0, idp.numColumns, idp.numRows, true);
        for (Rect r : holes) {
            occupancy.markCells(r, false);
        }

        existingScreens.add(screenId);
        screenOccupancy.append(screenId, occupancy);

        for (int x = 0; x < idp.numColumns; x++) {
            for (int y = 0; y < idp.numRows; y++) {
                if (!occupancy.cells[x][y]) {
                    continue;
                }

                ShortcutInfo info = new ShortcutInfo();
                info.intent = new Intent().setComponent(mComponent1);
                info.id = startId++;
                info.screenId = screenId;
                info.cellX = x;
                info.cellY = y;
                info.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
                bgDataModel.addItem(targetContext, info, false);
            }
        }
        return startId;
    }

    private void commitScreensToDb() throws Exception {
        LauncherSettings.Settings.call(getMockContentResolver(),
                LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);

        Uri uri = LauncherSettings.WorkspaceScreens.CONTENT_URI;
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        // Clear the table
        ops.add(ContentProviderOperation.newDelete(uri).build());
        int count = existingScreens.size();
        for (int i = 0; i < count; i++) {
            ContentValues v = new ContentValues();
            long screenId = existingScreens.get(i);
            v.put(LauncherSettings.WorkspaceScreens._ID, screenId);
            v.put(LauncherSettings.WorkspaceScreens.SCREEN_RANK, i);
            ops.add(ContentProviderOperation.newInsert(uri).withValues(v).build());
        }
        getMockContentResolver().applyBatch(LauncherProvider.AUTHORITY, ops);
    }
}
