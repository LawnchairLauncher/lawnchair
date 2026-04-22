/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.model;

import static com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE;
import static com.android.launcher3.util.ModelTestExtensions.nonPredictedItemCount;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceData;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.rule.LayoutProviderRule;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests to verify multiple callbacks in Loader
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ModelMultiCallbacksTest {

    @Rule public SandboxApplication mContext = new SandboxApplication().withModelDependency();
    @Rule public LayoutProviderRule mLayoutProvider = new LayoutProviderRule(mContext);

    @After
    public void tearDown() throws Exception {
        TestUtil.uninstallDummyApp();
    }

    private ModelLauncherCallbacks getCallbacks() {
        return getModel().newModelCallbacks();
    }

    @Test
    public void testTwoCallbacks_loadedTogether() throws Exception {
        setupWorkspacePages(3);

        MyCallbacks cb1 = spy(MyCallbacks.class);
        Executors.MAIN_EXECUTOR.execute(() -> getModel().addCallbacksAndLoad(cb1));

        waitForLoaderAndTempMainThread();
        cb1.verifyItemsBound(3);

        // Add a new callback
        cb1.reset();
        MyCallbacks cb2 = spy(MyCallbacks.class);
        Executors.MAIN_EXECUTOR.execute(() -> getModel().addCallbacksAndLoad(cb2));

        waitForLoaderAndTempMainThread();
        assertNull(cb1.mItems);
        cb2.verifyItemsBound(3);

        // Remove callbacks
        cb1.reset();
        cb2.reset();

        // No effect on callbacks when removing an callback
        Executors.MAIN_EXECUTOR.execute(() -> getModel().removeCallbacks(cb2));
        waitForLoaderAndTempMainThread();
        assertNull(cb1.mItems);
        assertNull(cb2.mItems);

        // Reloading only loads registered callbacks
        getModel().startLoader();
        waitForLoaderAndTempMainThread();
        cb1.verifyItemsBound(3);
        assertNull(cb2.mItems);
    }

    @Test
    public void testTwoCallbacks_receiveUpdates() throws Exception {
        TestUtil.uninstallDummyApp();

        setupWorkspacePages(1);

        MyCallbacks cb1 = spy(MyCallbacks.class);
        MyCallbacks cb2 = spy(MyCallbacks.class);
        Executors.MAIN_EXECUTOR.execute(() -> getModel().addCallbacksAndLoad(cb1));
        Executors.MAIN_EXECUTOR.execute(() -> getModel().addCallbacksAndLoad(cb2));
        waitForLoaderAndTempMainThread();

        assertTrue(cb1.allApps().contains(TEST_PACKAGE));
        assertTrue(cb2.allApps().contains(TEST_PACKAGE));

        // Install package 1
        TestUtil.installDummyApp();
        getCallbacks().onPackageAdded(TestUtil.DUMMY_PACKAGE, Process.myUserHandle());
        waitForLoaderAndTempMainThread();
        assertTrue(cb1.allApps().contains(TestUtil.DUMMY_PACKAGE));
        assertTrue(cb2.allApps().contains(TestUtil.DUMMY_PACKAGE));

        // Uninstall package 2
        TestUtil.uninstallDummyApp();
        getCallbacks().onPackageRemoved(TestUtil.DUMMY_PACKAGE, Process.myUserHandle());
        waitForLoaderAndTempMainThread();
        assertFalse(cb1.allApps().contains(TestUtil.DUMMY_PACKAGE));
        assertFalse(cb2.allApps().contains(TestUtil.DUMMY_PACKAGE));

        // Unregister a callback and verify updates no longer received
        Executors.MAIN_EXECUTOR.execute(() -> getModel().removeCallbacks(cb2));
        TestUtil.installDummyApp();
        getCallbacks().onPackageAdded(TestUtil.DUMMY_PACKAGE, Process.myUserHandle());
        waitForLoaderAndTempMainThread();

        // cb2 didn't get the update
        assertTrue(cb1.allApps().contains(TestUtil.DUMMY_PACKAGE));
        assertFalse(cb2.allApps().contains(TestUtil.DUMMY_PACKAGE));
    }

    private void waitForLoaderAndTempMainThread() throws Exception {
        Executors.MAIN_EXECUTOR.submit(() -> { }).get();
        Executors.MODEL_EXECUTOR.submit(() -> { }).get();
        Executors.MAIN_EXECUTOR.submit(() -> { }).get();
    }

    private void setupWorkspacePages(int pageCount) throws Exception {
        // Create a layout with 3 pages
        LauncherLayoutBuilder builder = new LauncherLayoutBuilder();
        for (int i = 0; i < pageCount; i++) {
            builder.atWorkspace(1, 1, i).putApp(TEST_PACKAGE, TEST_PACKAGE);
        }
        mLayoutProvider.setupDefaultLayoutProvider(builder);
    }

    private LauncherModel getModel() {
        return LauncherAppState.getInstance(mContext).getModel();
    }

    private abstract static class MyCallbacks implements Callbacks {

        List<ItemInfo> mItems = null;
        AppInfo[] mAppInfos;

        MyCallbacks() { }

        @Override
        public void bindCompleteModel(WorkspaceData itemIdMap, boolean isBindingSync) {
            mItems = itemIdMap.stream().toList();
        }

        @Override
        public void bindAllApplications(AppInfo[] apps, int flags,
                Map<PackageUserKey, Integer> packageUserKeytoUidMap) {
            mAppInfos = apps;
        }

        public void reset() {
            mItems = null;
            mAppInfos = null;
        }

        public void verifyItemsBound(int totalItems) {
            assertNotNull(mItems);
            assertEquals(totalItems, nonPredictedItemCount(mItems));
        }

        public Set<String> allApps() {
            return Arrays.stream(mAppInfos)
                    .map(ai -> ai.getTargetComponent().getPackageName())
                    .collect(Collectors.toSet());
        }
    }
}
