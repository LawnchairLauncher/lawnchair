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
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.waitForUpdateHandlerToFinish
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper.*
import com.android.launcher3.util.ModelTestExtensions.bgDataModel
import com.android.launcher3.util.ModelTestExtensions.loadModelSync
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.rule.LayoutProviderRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests to verify that folder icons are loaded with appropriate resolution */
@SmallTest
@RunWith(AndroidJUnit4::class)
class FolderIconLoadTest {

    @get:Rule val context = SandboxApplication()
    @get:Rule val layoutProvider = LayoutProviderRule(context)

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
            TEST_ACTIVITY14,
        )

    fun getIdp() = InvariantDeviceProfile.INSTANCE.get(context)

    @After
    @Throws(Exception::class)
    fun tearDown() {
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
    fun folderLoadedWithHighRes_max_3x3() {
        val idp = getIdp()
        idp.numFolderColumns = intArrayOf(3, 3, 3, 3)
        idp.numFolderRows = intArrayOf(3, 3, 3, 3)
        recreateSupportedDeviceProfiles()

        val items = setupAndLoadFolder(14)
        verifyHighRes(items, 0, 1, 3, 4)
        verifyLowRes(items, 2, 5, 6, 7, 8, 9, 10, 11, 12, 13)
    }

    @Test
    @Throws(Exception::class)
    fun folderLoadedWithHighRes_max_4x4() {
        val idp = getIdp()
        idp.numFolderColumns = intArrayOf(4, 4, 4, 4)
        idp.numFolderRows = intArrayOf(4, 4, 4, 4)
        recreateSupportedDeviceProfiles()

        val items = setupAndLoadFolder(14)
        verifyHighRes(items, 0, 1, 4, 5)
        verifyLowRes(items, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13)
    }

    @Test
    @Throws(Exception::class)
    fun folderLoadedWithHighRes_differentFolderConfigurations() {
        val idp = getIdp()
        idp.numFolderColumns = intArrayOf(4, 3, 4, 4)
        idp.numFolderRows = intArrayOf(4, 3, 4, 4)
        recreateSupportedDeviceProfiles()

        val items = setupAndLoadFolder(14)
        verifyHighRes(items, 0, 1, 3, 4, 5)
        verifyLowRes(items, 2, 6, 7, 8, 9, 10, 11, 12, 13)
    }

    @Throws(Exception::class)
    private fun setupAndLoadFolder(itemCount: Int): ArrayList<WorkspaceItemInfo> {
        val app = LauncherAppState.getInstance(context)

        val builder =
            LauncherLayoutBuilder()
                .atWorkspace(0, 0, 1)
                .putFolder("Sample")
                .apply {
                    for (i in 0..itemCount - 1) this.addApp(TEST_PACKAGE, uniqueActivities[i])
                }
                .build()

        layoutProvider.setupDefaultLayoutProvider(builder)
        app.model.loadModelSync()

        // The first load initializes the DB, load again so that icons are now used from the DB
        // Wait for the icon cache to be updated and then reload
        app.iconCache.waitForUpdateHandlerToFinish()

        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) { app.iconCache.clearMemoryCache() }
        // Reload again with correct icon state
        app.model.forceReload()
        app.model.loadModelSync()
        val collections =
            app.model.bgDataModel.itemsIdMap
                .filter { it.itemType == ITEM_TYPE_FOLDER }
                .map { it as FolderInfo }
        assertThat(collections.size).isEqualTo(1)
        assertThat(collections[0].getAppContents().size).isEqualTo(itemCount)
        return collections[0].getAppContents()
    }

    private fun verifyHighRes(items: ArrayList<WorkspaceItemInfo>, vararg indices: Int) {
        for (index in indices) {
            assertWithMessage("Index $index was not highRes")
                .that(items[index].bitmap.isNullOrLowRes)
                .isFalse()
            assertWithMessage("Index $index was the default icon")
                .that(isDefaultIcon(items[index].bitmap))
                .isFalse()
        }
    }

    private fun verifyLowRes(items: ArrayList<WorkspaceItemInfo>, vararg indices: Int) {
        for (index in indices) {
            assertWithMessage("Index $index was not lowRes")
                .that(items[index].bitmap.isNullOrLowRes)
                .isTrue()
            assertWithMessage("Index $index was the default icon")
                .that(isDefaultIcon(items[index].bitmap))
                .isFalse()
        }
    }

    private fun isDefaultIcon(bitmap: BitmapInfo) =
        LauncherAppState.getInstance(context).iconCache.isDefaultIcon(bitmap, context.user)

    /** Recreate DeviceProfiles after changing InvariantDeviceProfile */
    private fun recreateSupportedDeviceProfiles() {
        getIdp().supportedProfiles = getIdp().supportedProfiles.map { it.copy(context) }
    }
}
