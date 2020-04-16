package com.android.launcher3.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.cache.CachingLogic;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.LauncherModelHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Tests for {@link CacheDataUpdatedTask}
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(Mode.PAUSED)
public class CacheDataUpdatedTaskTest {

    private static final String NEW_LABEL_PREFIX = "new-label-";

    private LauncherModelHelper mModelHelper;

    @Before
    public void setup() throws Exception {
        mModelHelper = new LauncherModelHelper();
        mModelHelper.initializeData("/cache_data_updated_task_data.txt");

        // Add dummy entries in the cache to simulate update
        Context context = RuntimeEnvironment.application;
        IconCache iconCache = LauncherAppState.getInstance(context).getIconCache();
        CachingLogic<ItemInfo> dummyLogic = new CachingLogic<ItemInfo>() {
            @Override
            public ComponentName getComponent(ItemInfo info) {
                return info.getTargetComponent();
            }

            @Override
            public UserHandle getUser(ItemInfo info) {
                return info.user;
            }

            @Override
            public CharSequence getLabel(ItemInfo info) {
                return NEW_LABEL_PREFIX + info.id;
            }

            @NonNull
            @Override
            public BitmapInfo loadIcon(Context context, ItemInfo info) {
                return BitmapInfo.of(Bitmap.createBitmap(1, 1, Config.ARGB_8888), Color.RED);
            }
        };

        UserManager um = context.getSystemService(UserManager.class);
        for (ItemInfo info : mModelHelper.getBgDataModel().itemsIdMap) {
            iconCache.addIconToDBAndMemCache(info, dummyLogic, new PackageInfo(),
                    um.getSerialNumberForUser(info.user), true);
        }
    }

    private CacheDataUpdatedTask newTask(int op, String... pkg) {
        return new CacheDataUpdatedTask(op, Process.myUserHandle(),
                new HashSet<>(Arrays.asList(pkg)));
    }

    @Test
    public void testCacheUpdate_update_apps() throws Exception {
        // Clear all icons from apps list so that its easy to check what was updated
        for (AppInfo info : mModelHelper.getAllAppsList().data) {
            info.bitmap = BitmapInfo.LOW_RES_INFO;
        }

        mModelHelper.executeTaskForTest(newTask(CacheDataUpdatedTask.OP_CACHE_UPDATE, "app1"));

        // Verify that only the app icons of app1 (id 1 & 2) are updated. Custom shortcut (id 7)
        // is not updated
        verifyUpdate(1, 2);

        // Verify that only app1 var updated in allAppsList
        assertFalse(mModelHelper.getAllAppsList().data.isEmpty());
        for (AppInfo info : mModelHelper.getAllAppsList().data) {
            if (info.componentName.getPackageName().equals("app1")) {
                assertFalse(info.bitmap.isNullOrLowRes());
            } else {
                assertTrue(info.bitmap.isNullOrLowRes());
            }
        }
    }

    @Test
    public void testSessionUpdate_ignores_normal_apps() throws Exception {
        mModelHelper.executeTaskForTest(newTask(CacheDataUpdatedTask.OP_SESSION_UPDATE, "app1"));

        // app1 has no restored shortcuts. Verify that nothing was updated.
        verifyUpdate();
    }

    @Test
    public void testSessionUpdate_updates_pending_apps() throws Exception {
        mModelHelper.executeTaskForTest(newTask(CacheDataUpdatedTask.OP_SESSION_UPDATE, "app3"));

        // app3 has only restored apps (id 5, 6) and shortcuts (id 9). Verify that only apps were
        // were updated
        verifyUpdate(5, 6);
    }

    private void verifyUpdate(Integer... idsUpdated) {
        HashSet<Integer> updates = new HashSet<>(Arrays.asList(idsUpdated));
        for (ItemInfo info : mModelHelper.getBgDataModel().itemsIdMap) {
            if (updates.contains(info.id)) {
                assertEquals(NEW_LABEL_PREFIX + info.id, info.title);
                assertFalse(((WorkspaceItemInfo) info).bitmap.isNullOrLowRes());
            } else {
                assertNotSame(NEW_LABEL_PREFIX + info.id, info.title);
                assertTrue(((WorkspaceItemInfo) info).bitmap.isNullOrLowRes());
            }
        }
    }
}
