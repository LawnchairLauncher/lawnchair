/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherAppState
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.LauncherModelHelper.*
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.rule.TestStabilityRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CountDownLatch
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests to verify that folder icons are loaded with appropriate resolution */
@SmallTest
@RunWith(AndroidJUnit4::class)
class FolderIconLoadTest {
    private lateinit var modelHelper: LauncherModelHelper

    private val uniqueActivities =
        listOf(
            TEST_ACTIVITY,
            TEST_ACTIVITY2,
            TEST_ACTIVITY3,
            TEST_ACTIVITY4,
            TEST_ACTIVITY5,
            TEST_ACTIVITY6,
            TEST_ACTIVITY7,
            TEST_ACTIVITY8,
            TEST_ACTIVITY9,
            TEST_ACTIVITY10,
            TEST_ACTIVITY11,
            TEST_ACTIVITY12,
            TEST_ACTIVITY13,
            TEST_ACTIVITY14
        )

    @get:Rule(order = 0) val testStabilityRule = TestStabilityRule()

    @Before
    fun setUp() {
        modelHelper = LauncherModelHelper()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        modelHelper.destroy()
        TestUtil.uninstallDummyApp()
    }

    @Test
    @Throws(Exception::class)
    fun folderLoadedWithHighRes_2x2() {
        val items = setupAndLoadFolder(4)
        assertThat(items.size).isEqualTo(4)
        verifyHighRes(items, 0, 1, 2, 3)
    }

    @Test
    @Throws(Exception::class)
    fun folderLoadedWithHighRes_3x2() {
        val items = setupAndLoadFolder(6)
        assertThat(items.size).isEqualTo(6)
        verifyHighRes(items, 0, 1, 3, 4)
        verifyLowRes(items, 2, 5)
    }

    @Test
    @Throws(Exception::class)
    @TestStabilityRule.Stability(
        flavors = TestStabilityRule.LOCAL or TestStabilityRule.PLATFORM_POSTSUBMIT
    ) // b/319923578
    fun folderLoadedWithHighRes_max_3x3() {
        val idp = LauncherAppState.getIDP(modelHelper.sandboxContext)
        idp.numFolderColumns = intArrayOf(3, 3, 3, 3)
        idp.numFolderRows = intArrayOf(3, 3, 3, 3)
        recreateSupportedDeviceProfiles()

        val items = setupAndLoadFolder(14)
        verifyHighRes(items, 0, 1, 3, 4)
        verifyLowRes(items, 2, 5, 6, 7, 8, 9, 10, 11, 12, 13)
    }

    @Test
    @Throws(Exception::class)
    @TestStabilityRule.Stability(
        flavors = TestStabilityRule.LOCAL or TestStabilityRule.PLATFORM_POSTSUBMIT
    ) // b/319923578
    fun folderLoadedWithHighRes_max_4x4() {
        val idp = LauncherAppState.getIDP(modelHelper.sandboxContext)
        idp.numFolderColumns = intArrayOf(4, 4, 4, 4)
        idp.numFolderRows = intArrayOf(4, 4, 4, 4)
        recreateSupportedDeviceProfiles()

        val items = setupAndLoadFolder(14)
        verifyHighRes(items, 0, 1, 4, 5)
        verifyLowRes(items, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13)
    }

    @Test
    @Throws(Exception::class)
    // Stress tests are long. We permanently demote them from presubmit to match the presubmit SLO.
    @TestStabilityRule.Stability(
        flavors = TestStabilityRule.LOCAL or TestStabilityRule.PLATFORM_POSTSUBMIT
    ) // b/319923578
    fun folderLoadedWithHighRes_differentFolderConfigurations() {
        val idp = LauncherAppState.getIDP(modelHelper.sandboxContext)
        idp.numFolderColumns = intArrayOf(4, 3, 4, 4)
        idp.numFolderRows = intArrayOf(4, 3, 4, 4)
        recreateSupportedDeviceProfiles()

        val items = setupAndLoadFolder(14)
        verifyHighRes(items, 0, 1, 3, 4, 5)
        verifyLowRes(items, 2, 6, 7, 8, 9, 10, 11, 12, 13)
    }

    @Throws(Exception::class)
    private fun setupAndLoadFolder(itemCount: Int): ArrayList<WorkspaceItemInfo> {
        val builder =
            LauncherLayoutBuilder()
                .atWorkspace(0, 0, 1)
                .putFolder("Sample")
                .apply {
                    for (i in 0..itemCount - 1) this.addApp(TEST_PACKAGE, uniqueActivities[i])
                }
                .build()

        modelHelper.setupDefaultLayoutProvider(builder)
        modelHelper.loadModelSync()

        // The first load initializes the DB, load again so that icons are now used from the DB
        // Wait for the icon cache to be updated and then reload
        val app = LauncherAppState.getInstance(modelHelper.sandboxContext)
        val cache = app.iconCache
        while (cache.isIconUpdateInProgress) {
            val wait = CountDownLatch(1)
            Executors.MODEL_EXECUTOR.handler.postDelayed({ wait.countDown() }, 10)
            wait.await()
        }
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) { cache.clearMemoryCache() }
        // Reload again with correct icon state
        app.model.forceReload()
        modelHelper.loadModelSync()
        val collections = modelHelper.getBgDataModel().collections

        assertThat(collections.size()).isEqualTo(1)
        assertThat(collections.valueAt(0).getAppContents().size).isEqualTo(itemCount)
        return collections.valueAt(0).getAppContents()
    }

    private fun verifyHighRes(items: ArrayList<WorkspaceItemInfo>, vararg indices: Int) {
        for (index in indices) {
            assertWithMessage("Index $index was not highRes")
                .that(items[index].bitmap.isNullOrLowRes)
                .isFalse()
        }
    }

    private fun verifyLowRes(items: ArrayList<WorkspaceItemInfo>, vararg indices: Int) {
        for (index in indices) {
            assertWithMessage("Index $index was not lowRes")
                .that(items[index].bitmap.isNullOrLowRes)
                .isTrue()
        }
    }

    /** Recreate DeviceProfiles after changing InvariantDeviceProfile */
    private fun recreateSupportedDeviceProfiles() {
        LauncherAppState.getIDP(modelHelper.sandboxContext).supportedProfiles =
            LauncherAppState.getIDP(modelHelper.sandboxContext).supportedProfiles.map {
                it.copy(modelHelper.sandboxContext)
            }
    }
}
