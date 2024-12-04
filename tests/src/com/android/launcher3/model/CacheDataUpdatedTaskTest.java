package com.android.launcher3.model;

import static android.os.Process.myUserHandle;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY;
import static com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2;
import static com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY3;
import static com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE;
import static com.android.launcher3.util.TestUtil.runOnExecutorSync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.LauncherModelHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.rule.TestStabilityRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Tests for {@link CacheDataUpdatedTask}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class CacheDataUpdatedTaskTest {

    @Rule(order = 0)
    public TestRule testStabilityRule = new TestStabilityRule();

    private static final String PENDING_APP_1 = TEST_PACKAGE + ".pending1";
    private static final String PENDING_APP_2 = TEST_PACKAGE + ".pending2";

    private LauncherModelHelper mModelHelper;
    private Context mContext;

    private int mSession1;

    @Before
    public void setup() throws Exception {
        mModelHelper = new LauncherModelHelper();
        mContext = mModelHelper.sandboxContext;
        mSession1 = mModelHelper.createInstallerSession(PENDING_APP_1);
        mModelHelper.createInstallerSession(PENDING_APP_2);

        LauncherLayoutBuilder builder = new LauncherLayoutBuilder()
                .atHotseat(1).putFolder("MyFolder")
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)    // 2
                .addApp(TEST_PACKAGE, TEST_ACTIVITY2)   // 3
                .addApp(TEST_PACKAGE, TEST_ACTIVITY3)   // 4

                // Pending App 1
                .addApp(PENDING_APP_1, TEST_ACTIVITY)   // 5
                .addApp(PENDING_APP_1, TEST_ACTIVITY2)  // 6
                .addApp(PENDING_APP_1, TEST_ACTIVITY3)  // 7

                // Pending App 2
                .addApp(PENDING_APP_2, TEST_ACTIVITY)   // 8
                .addApp(PENDING_APP_2, TEST_ACTIVITY2)  // 9
                .addApp(PENDING_APP_2, TEST_ACTIVITY3)  // 10
                .build();
        mModelHelper.setupDefaultLayoutProvider(builder);
        mModelHelper.loadModelSync();
        assertEquals(10, mModelHelper.getBgDataModel().itemsIdMap.size());
    }

    @After
    public void tearDown() {
        mModelHelper.destroy();
    }

    private CacheDataUpdatedTask newTask(int op, String... pkg) {
        return new CacheDataUpdatedTask(op, myUserHandle(),
                new HashSet<>(Arrays.asList(pkg)));
    }

    @Test
    public void testCacheUpdate_update_apps() {
        // Run on model executor so that no other task runs in the middle.
        runOnExecutorSync(MODEL_EXECUTOR, () -> {
            // Clear all icons from apps list so that its easy to check what was updated
            allItems().forEach(wi -> wi.bitmap = BitmapInfo.LOW_RES_INFO);

            mModelHelper.getModel().enqueueModelUpdateTask(
                    newTask(CacheDataUpdatedTask.OP_CACHE_UPDATE, TEST_PACKAGE));

            // Verify that only the app icons of TEST_PACKAGE (id 2, 3, 4) are updated.
            verifyUpdate(2, 3, 4);
        });
    }

    @Test
    public void testSessionUpdate_ignores_normal_apps() {
        // Run on model executor so that no other task runs in the middle.
        runOnExecutorSync(MODEL_EXECUTOR, () -> {
            // Clear all icons from apps list so that its easy to check what was updated
            allItems().forEach(wi -> wi.bitmap = BitmapInfo.LOW_RES_INFO);

            mModelHelper.getModel().enqueueModelUpdateTask(
                    newTask(CacheDataUpdatedTask.OP_SESSION_UPDATE, TEST_PACKAGE));

            // TEST_PACKAGE has no restored shortcuts. Verify that nothing was updated.
            verifyUpdate();
        });
    }

    @Test
    public void testSessionUpdate_updates_pending_apps() {
        // Run on model executor so that no other task runs in the middle.
        runOnExecutorSync(MODEL_EXECUTOR, () -> {
            LauncherAppState.getInstance(mContext).getIconCache().updateSessionCache(
                    new PackageUserKey(PENDING_APP_1, myUserHandle()),
                    mContext.getPackageManager().getPackageInstaller().getSessionInfo(mSession1));

            // Clear all icons from apps list so that its easy to check what was updated
            allItems().forEach(wi -> wi.bitmap = BitmapInfo.LOW_RES_INFO);

            mModelHelper.getModel().enqueueModelUpdateTask(
                    newTask(CacheDataUpdatedTask.OP_SESSION_UPDATE, PENDING_APP_1));

            // Only restored apps from PENDING_APP_1 (id 5, 6, 7) are updated
            verifyUpdate(5, 6, 7);
        });
    }

    private void verifyUpdate(int... idsUpdated) {
        IntSet updates = IntSet.wrap(idsUpdated);
        for (WorkspaceItemInfo info : allItems()) {
            if (updates.contains(info.id)) {
                assertFalse(info.bitmap.isNullOrLowRes());
            } else {
                assertTrue(info.bitmap.isNullOrLowRes());
            }
        }
    }

    private List<WorkspaceItemInfo> allItems() {
        return ((FolderInfo) mModelHelper.getBgDataModel().itemsIdMap.get(1)).getAppContents();
    }
}
