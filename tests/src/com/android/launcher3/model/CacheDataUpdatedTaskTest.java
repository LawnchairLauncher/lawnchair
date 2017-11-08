package com.android.launcher3.model;

import android.support.test.runner.AndroidJUnit4;

import com.android.launcher3.AppInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.ShortcutInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

/**
 * Tests for {@link CacheDataUpdatedTask}
 */
@RunWith(AndroidJUnit4.class)
public class CacheDataUpdatedTaskTest extends BaseModelUpdateTaskTestCase {

    private static final String NEW_LABEL_PREFIX = "new-label-";

    @Before
    public void initData() throws Exception {
        initializeData("cache_data_updated_task_data");
        // Add dummy entries in the cache to simulate update
        for (ItemInfo info : bgDataModel.itemsIdMap) {
            iconCache.addCache(info.getTargetComponent(), NEW_LABEL_PREFIX + info.id);
        }
    }

    private CacheDataUpdatedTask newTask(int op, String... pkg) {
        return new CacheDataUpdatedTask(op, myUser, new HashSet<>(Arrays.asList(pkg)));
    }

    @Test
    public void testCacheUpdate_update_apps() throws Exception {
        // Clear all icons from apps list so that its easy to check what was updated
        for (AppInfo info : allAppsList.data) {
            info.iconBitmap = null;
        }

        executeTaskForTest(newTask(CacheDataUpdatedTask.OP_CACHE_UPDATE, "app1"));

        // Verify that only the app icons of app1 (id 1 & 2) are updated. Custom shortcut (id 7)
        // is not updated
        verifyUpdate(1L, 2L);

        // Verify that only app1 var updated in allAppsList
        assertFalse(allAppsList.data.isEmpty());
        for (AppInfo info : allAppsList.data) {
            if (info.componentName.getPackageName().equals("app1")) {
                assertNotNull(info.iconBitmap);
            } else {
                assertNull(info.iconBitmap);
            }
        }
    }

    @Test
    public void testSessionUpdate_ignores_normal_apps() throws Exception {
        executeTaskForTest(newTask(CacheDataUpdatedTask.OP_SESSION_UPDATE, "app1"));

        // app1 has no restored shortcuts. Verify that nothing was updated.
        verifyUpdate();
    }

    @Test
    public void testSessionUpdate_updates_pending_apps() throws Exception {
        executeTaskForTest(newTask(CacheDataUpdatedTask.OP_SESSION_UPDATE, "app3"));

        // app3 has only restored apps (id 5, 6) and shortcuts (id 9). Verify that only apps were
        // were updated
        verifyUpdate(5L, 6L);
    }

    private void verifyUpdate(Long... idsUpdated) {
        HashSet<Long> updates = new HashSet<>(Arrays.asList(idsUpdated));
        for (ItemInfo info : bgDataModel.itemsIdMap) {
            if (updates.contains(info.id)) {
                assertEquals(NEW_LABEL_PREFIX + info.id, info.title);
                assertNotNull(((ShortcutInfo) info).iconBitmap);
            } else {
                assertNotSame(NEW_LABEL_PREFIX + info.id, info.title);
                assertNull(((ShortcutInfo) info).iconBitmap);
            }
        }
    }
}
