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

import static com.android.launcher3.util.Executors.createAndStartNewForegroundLooper;
import static com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.spy;
import static org.robolectric.Shadows.shadowOf;

import android.os.Process;

import com.android.launcher3.PagedView;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.LauncherModelHelper;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.ViewOnDrawExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests to verify multiple callbacks in Loader
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(Mode.PAUSED)
public class ModelMultiCallbacksTest {

    private LauncherModelHelper mModelHelper;

    private ShadowPackageManager mSpm;
    private LooperExecutor mTempMainExecutor;

    @Before
    public void setUp() throws Exception {
        mModelHelper = new LauncherModelHelper();
        mModelHelper.installApp(TEST_PACKAGE);

        mSpm = shadowOf(RuntimeEnvironment.application.getPackageManager());

        // Since robolectric tests run on main thread, we run the loader-UI calls on a temp thread,
        // so that we can wait appropriately for the loader to complete.
        mTempMainExecutor = new LooperExecutor(createAndStartNewForegroundLooper("tempMain"));
        ReflectionHelpers.setField(mModelHelper.getModel(), "mMainExecutor", mTempMainExecutor);
    }

    @Test
    public void testTwoCallbacks_loadedTogether() throws Exception {
        setupWorkspacePages(3);

        MyCallbacks cb1 = spy(MyCallbacks.class);
        mModelHelper.getModel().addCallbacksAndLoad(cb1);

        waitForLoaderAndTempMainThread();
        cb1.verifySynchronouslyBound(3);

        // Add a new callback
        cb1.reset();
        MyCallbacks cb2 = spy(MyCallbacks.class);
        cb2.mPageToBindSync = 2;
        mModelHelper.getModel().addCallbacksAndLoad(cb2);

        waitForLoaderAndTempMainThread();
        cb1.verifySynchronouslyBound(3);
        cb2.verifySynchronouslyBound(3);

        // Remove callbacks
        cb1.reset();
        cb2.reset();

        // No effect on callbacks when removing an callback
        mModelHelper.getModel().removeCallbacks(cb2);
        waitForLoaderAndTempMainThread();
        assertNull(cb1.mDeferredExecutor);
        assertNull(cb2.mDeferredExecutor);

        // Reloading only loads registered callbacks
        mModelHelper.getModel().startLoader();
        waitForLoaderAndTempMainThread();
        cb1.verifySynchronouslyBound(3);
        assertNull(cb2.mDeferredExecutor);
    }

    @Test
    public void testTwoCallbacks_receiveUpdates() throws Exception {
        setupWorkspacePages(1);

        MyCallbacks cb1 = spy(MyCallbacks.class);
        MyCallbacks cb2 = spy(MyCallbacks.class);
        mModelHelper.getModel().addCallbacksAndLoad(cb1);
        mModelHelper.getModel().addCallbacksAndLoad(cb2);
        waitForLoaderAndTempMainThread();

        cb1.verifyApps(TEST_PACKAGE);
        cb2.verifyApps(TEST_PACKAGE);

        // Install package 1
        String pkg1 = "com.test.pkg1";
        mModelHelper.installApp(pkg1);
        mModelHelper.getModel().onPackageAdded(pkg1, Process.myUserHandle());
        waitForLoaderAndTempMainThread();
        cb1.verifyApps(TEST_PACKAGE, pkg1);
        cb2.verifyApps(TEST_PACKAGE, pkg1);

        // Install package 2
        String pkg2 = "com.test.pkg2";
        mModelHelper.installApp(pkg2);
        mModelHelper.getModel().onPackageAdded(pkg2, Process.myUserHandle());
        waitForLoaderAndTempMainThread();
        cb1.verifyApps(TEST_PACKAGE, pkg1, pkg2);
        cb2.verifyApps(TEST_PACKAGE, pkg1, pkg2);

        // Uninstall package 2
        mSpm.removePackage(pkg1);
        mModelHelper.getModel().onPackageRemoved(pkg1, Process.myUserHandle());
        waitForLoaderAndTempMainThread();
        cb1.verifyApps(TEST_PACKAGE, pkg2);
        cb2.verifyApps(TEST_PACKAGE, pkg2);

        // Unregister a callback and verify updates no longer received
        mModelHelper.getModel().removeCallbacks(cb2);
        mSpm.removePackage(pkg2);
        mModelHelper.getModel().onPackageRemoved(pkg2, Process.myUserHandle());
        waitForLoaderAndTempMainThread();
        cb1.verifyApps(TEST_PACKAGE);
        cb2.verifyApps(TEST_PACKAGE, pkg2);
    }

    private void waitForLoaderAndTempMainThread() throws Exception {
        Executors.MODEL_EXECUTOR.submit(() -> { }).get();
        mTempMainExecutor.submit(() -> { }).get();
    }

    private void setupWorkspacePages(int pageCount) throws Exception {
        // Create a layout with 3 pages
        LauncherLayoutBuilder builder = new LauncherLayoutBuilder();
        for (int i = 0; i < pageCount; i++) {
            builder.atWorkspace(1, 1, i).putApp(TEST_PACKAGE, TEST_PACKAGE);
        }
        mModelHelper.setupDefaultLayoutProvider(builder);
    }

    private abstract static class MyCallbacks implements Callbacks {

        final List<ItemInfo> mItems = new ArrayList<>();
        int mPageToBindSync = 0;
        int mPageBoundSync = PagedView.INVALID_PAGE;
        ViewOnDrawExecutor mDeferredExecutor;
        AppInfo[] mAppInfos;

        MyCallbacks() { }

        @Override
        public void onPageBoundSynchronously(int page) {
            mPageBoundSync = page;
        }

        @Override
        public void executeOnNextDraw(ViewOnDrawExecutor executor) {
            mDeferredExecutor = executor;
        }

        @Override
        public void bindItems(List<ItemInfo> shortcuts, boolean forceAnimateIcons) {
            mItems.addAll(shortcuts);
        }

        @Override
        public void bindAllApplications(AppInfo[] apps, int flags) {
            mAppInfos = apps;
        }

        @Override
        public int getPageToBindSynchronously() {
            return mPageToBindSync;
        }

        public void reset() {
            mItems.clear();
            mPageBoundSync = PagedView.INVALID_PAGE;
            mDeferredExecutor = null;
            mAppInfos = null;
        }

        public void verifySynchronouslyBound(int totalItems) {
            // Verify that the requested page is bound synchronously
            assertEquals(mPageBoundSync, mPageToBindSync);
            assertEquals(mItems.size(), 1);
            assertEquals(mItems.get(0).screenId, mPageBoundSync);
            assertNotNull(mDeferredExecutor);

            // Verify that all other pages are bound properly
            mDeferredExecutor.runAllTasks();
            assertEquals(mItems.size(), totalItems);
        }

        public void verifyApps(String... apps) {
            assertEquals(apps.length, mAppInfos.length);
            assertEquals(Arrays.stream(mAppInfos)
                    .map(ai -> ai.getTargetComponent().getPackageName())
                    .collect(Collectors.toSet()),
                    new HashSet<>(Arrays.asList(apps)));
        }
    }
}
