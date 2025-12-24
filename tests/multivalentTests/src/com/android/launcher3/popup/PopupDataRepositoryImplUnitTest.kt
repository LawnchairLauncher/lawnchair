/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.popup

import android.content.Context
import android.platform.test.annotations.EnableFlags
import android.util.SparseArray
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_QSB
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceData.ImmutableWorkspaceData
import com.android.launcher3.model.repository.HomeScreenRepository
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors
import com.android.launcher3.util.TestUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/** Tests for the [PopupDataRepositoryImpl] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PopupDataRepositoryImplUnitTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val homeScreenRepository = HomeScreenRepository()
    private val lifeCycle: DaggerSingletonTracker = mock()

    private val popupDataSource = PopupDataSource()
    private val popupDataRepository =
        PopupDataRepositoryImpl(popupDataSource, context, homeScreenRepository, lifeCycle)

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getAllPopupDataWithInvalidItemInfoShouldReturnEmptyList() {
        val itemInfo = ItemInfo()
        itemInfo.itemType = ITEM_TYPE_QSB
        itemInfo.id = 1
        seedData(itemInfo)
        val popupDataMap = popupDataRepository.getAllPopupData()

        assert(popupDataMap.isEmpty())
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getAllPopupDataWithEmptyItemInfoShouldReturnEmptyList() {
        val itemInfo = ItemInfo()
        itemInfo.id = 1
        seedData(itemInfo)
        val popupDataMap = popupDataRepository.getAllPopupData()

        assert(popupDataMap.isEmpty())
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getAllPopupDataWithFolderShouldReturnMapContainingFolderItem() {
        val itemInfo = ItemInfo()
        itemInfo.itemType = ITEM_TYPE_FOLDER
        itemInfo.id = 1
        seedData(itemInfo)
        val popupDataMap = popupDataRepository.getAllPopupData()

        assert(popupDataMap.size == 1)
        assert(popupDataMap[itemInfo.id] != null)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getAllPopupDataWithFolderAndWidgetShouldReturnMapContainingFolderAndWidgetItem() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.id = 1
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        val widgetItemInfo = ItemInfo()
        widgetItemInfo.itemType = ITEM_TYPE_APPWIDGET
        widgetItemInfo.id = 2
        seedData(folderItemInfo, widgetItemInfo)
        val popupDataMap = popupDataRepository.getAllPopupData()

        assert(popupDataMap.size == 2)
        assert(popupDataMap[folderItemInfo.id] != null)
        assert(popupDataMap[widgetItemInfo.id] != null)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getPopupDataByItemInfoShouldBeNullIfWeDontHaveThatItem() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.id = 1
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        val widgetItemInfo = ItemInfo()
        widgetItemInfo.id = 2
        widgetItemInfo.itemType = ITEM_TYPE_APPWIDGET
        seedData(folderItemInfo, widgetItemInfo)
        val popupDataStream = popupDataRepository.getPopupDataByItemInfo(ItemInfo())

        assert(popupDataStream == null)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getPopupDataByItemInfoShouldNotBeNullIfWeHaveThatItem() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.id = 1
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        val widgetItemInfo = ItemInfo()
        widgetItemInfo.id = 2
        widgetItemInfo.itemType = ITEM_TYPE_APPWIDGET
        seedData(folderItemInfo, widgetItemInfo)
        val popupDataStream = popupDataRepository.getPopupDataByItemInfo(folderItemInfo)

        assert(popupDataStream != null)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun getPopupDataByItemInfoShouldStillWorkIfMapDoesNotHaveItem() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.id = 1
        folderItemInfo.itemType = ITEM_TYPE_FOLDER

        // There should be no popup data since we didn't update the home screen repository.
        assert(popupDataRepository.getAllPopupData().isEmpty())

        val popupDataStream = popupDataRepository.getPopupDataByItemInfo(folderItemInfo)

        // Now that we called getPopupDataByItemInfo we should have the folderItemInfo.
        assert(popupDataRepository.getAllPopupData().size == 1)

        // Verify the stream is correct.
        assert(popupDataStream != null)
        assert(popupDataStream?.size == 1)
        assert(popupDataStream?.contains(popupDataSource.removePopupData) == true)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun popupDataShouldHaveAllTheDataFilledIn() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.id = 1
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        seedData(folderItemInfo)
        val popupData = popupDataRepository.getPopupDataByItemInfo(folderItemInfo)

        assert(popupData?.size == 1)
        assert(popupData?.get(0)?.category == PopupCategory.SYSTEM_SHORTCUT_FIXED)
        assert(popupData?.get(0)?.iconResId == R.drawable.ic_remove_no_shadow)
        assert(popupData?.get(0)?.labelResId == R.string.remove_drop_target_label)
        assert(popupData?.get(0)?.popupAction != null)
    }

    private fun seedData(vararg items: ItemInfo) {
        val data: SparseArray<ItemInfo> = SparseArray()
        items.forEachIndexed { i: Int, item: ItemInfo -> data[i] = item }
        homeScreenRepository.dispatchWorkspaceDataChange(
            ImmutableWorkspaceData(version = 0, modificationId = 0, items = data),
            null,
        )
        TestUtil.runOnExecutorSync(Executors.DATA_HELPER_EXECUTOR) {}
    }
}
