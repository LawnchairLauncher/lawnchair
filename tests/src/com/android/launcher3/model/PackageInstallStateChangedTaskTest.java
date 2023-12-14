package com.android.launcher3.model;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY;
import static com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2;
import static com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY3;
import static com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE;
import static com.android.launcher3.util.TestUtil.runOnExecutorSync;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.LauncherModelHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link PackageInstallStateChangedTask}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PackageInstallStateChangedTaskTest {

    private static final String PENDING_APP_1 = TEST_PACKAGE + ".pending1";
    private static final String PENDING_APP_2 = TEST_PACKAGE + ".pending2";

    private LauncherModelHelper mModelHelper;
    private IntSet mDownloadingApps;

    @Before
    public void setup() throws Exception {
        mModelHelper = new LauncherModelHelper();
        mModelHelper.createInstallerSession(PENDING_APP_1);
        mModelHelper.createInstallerSession(PENDING_APP_2);

        LauncherLayoutBuilder builder = new LauncherLayoutBuilder()
                .atWorkspace(0, 0, 1).putApp(TEST_PACKAGE, TEST_ACTIVITY)               // 1
                .atWorkspace(0, 0, 2).putApp(TEST_PACKAGE, TEST_ACTIVITY2)              // 2
                .atWorkspace(0, 0, 3).putApp(TEST_PACKAGE, TEST_ACTIVITY3)              // 3

                .atWorkspace(0, 0, 4).putApp(PENDING_APP_1, TEST_ACTIVITY)              // 4
                .atWorkspace(0, 0, 5).putApp(PENDING_APP_1, TEST_ACTIVITY2)             // 5
                .atWorkspace(0, 0, 6).putApp(PENDING_APP_1, TEST_ACTIVITY3)             // 6
                .atWorkspace(0, 0, 7).putWidget(PENDING_APP_1, "pending.widget", 1, 1)  // 7

                .atWorkspace(0, 0, 8).putApp(PENDING_APP_2, TEST_ACTIVITY)              // 8
                .atWorkspace(0, 0, 9).putApp(PENDING_APP_2, TEST_ACTIVITY2)             // 9
                .atWorkspace(0, 0, 10).putApp(PENDING_APP_2, TEST_ACTIVITY3);           // 10

        mDownloadingApps = IntSet.wrap(4, 5, 6, 7, 8, 9, 10);
        mModelHelper.setupDefaultLayoutProvider(builder);
        mModelHelper.loadModelSync();
        assertEquals(10, mModelHelper.getBgDataModel().itemsIdMap.size());
    }

    @After
    public void tearDown() {
        mModelHelper.destroy();
    }

    private PackageInstallStateChangedTask newTask(String pkg, int progress) {
        int state = PackageInstallInfo.STATUS_INSTALLING;
        PackageInstallInfo installInfo = new PackageInstallInfo(pkg, state, progress,
                android.os.Process.myUserHandle());
        return new PackageInstallStateChangedTask(installInfo);
    }

    @Test
    public void testSessionUpdate_ignore_installed() {
        // Run on model executor so that no other task runs in the middle.
        runOnExecutorSync(MODEL_EXECUTOR, () -> {
            mModelHelper.getModel().enqueueModelUpdateTask(newTask(TEST_PACKAGE, 30));

            // No shortcuts were updated
            verifyProgressUpdate(0);
        });
    }

    @Test
    public void testSessionUpdate_shortcuts_updated() {
        // Run on model executor so that no other task runs in the middle.
        runOnExecutorSync(MODEL_EXECUTOR, () -> {
            mModelHelper.getModel().enqueueModelUpdateTask(newTask(PENDING_APP_1, 30));

            verifyProgressUpdate(30, 4, 5, 6, 7);
        });
    }

    @Test
    public void testSessionUpdate_widgets_updated() {
        // Run on model executor so that no other task runs in the middle.
        runOnExecutorSync(MODEL_EXECUTOR, () -> {
            mModelHelper.getModel().enqueueModelUpdateTask(newTask(PENDING_APP_2, 30));

            verifyProgressUpdate(30, 8, 9, 10);
        });
    }

    private void verifyProgressUpdate(int progress, int... idsUpdated) {
        IntSet updates = IntSet.wrap(idsUpdated);
        for (ItemInfo info : mModelHelper.getBgDataModel().itemsIdMap) {
            int expectedProgress = updates.contains(info.id) ? progress
                    : (mDownloadingApps.contains(info.id) ? 0 : 100);
            if (info instanceof WorkspaceItemInfo wi) {
                assertEquals(expectedProgress, wi.getProgressLevel());
            } else {
                assertEquals(expectedProgress, ((LauncherAppWidgetInfo) info).installProgress);
            }
        }
    }
}
