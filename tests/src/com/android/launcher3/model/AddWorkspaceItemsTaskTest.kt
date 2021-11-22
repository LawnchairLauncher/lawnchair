/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Pair
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.*
import com.android.launcher3.util.IntArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import kotlin.collections.ArrayList

/**
 * Tests for [AddWorkspaceItemsTask]
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AddWorkspaceItemsTaskTest {

    @Captor
    private lateinit var animatedItemArgumentCaptor: ArgumentCaptor<ArrayList<ItemInfo>>

    @Captor
    private lateinit var notAnimatedItemArgumentCaptor: ArgumentCaptor<ArrayList<ItemInfo>>

    @Mock
    private lateinit var dataModelCallbacks: BgDataModel.Callbacks

    private lateinit var mTargetContext: Context
    private lateinit var mIdp: InvariantDeviceProfile
    private lateinit var mAppState: LauncherAppState
    private lateinit var mModelHelper: LauncherModelHelper
    private lateinit var mExistingScreens: IntArray
    private lateinit var mNewScreens: IntArray
    private lateinit var mScreenOccupancy: IntSparseArrayMap<GridOccupancy>

    private val emptyScreenHoles = listOf(Rect(0, 0, 5, 5))
    private val fullScreenHoles = emptyList<Rect>()


    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mModelHelper = LauncherModelHelper()
        mTargetContext = mModelHelper.sandboxContext
        mIdp = InvariantDeviceProfile.INSTANCE[mTargetContext]
        mIdp.numRows = 5
        mIdp.numColumns = mIdp.numRows
        mAppState = LauncherAppState.getInstance(mTargetContext)
        mExistingScreens = IntArray()
        mScreenOccupancy = IntSparseArrayMap()
        mNewScreens = IntArray()
        Executors.MAIN_EXECUTOR.submit { mModelHelper.model.addCallbacks(dataModelCallbacks) }.get()
    }

    @After
    fun tearDown() {
        mModelHelper.destroy()
    }

    @Test
    fun justEnoughSpaceOnFirstScreen_whenFindSpaceForItem_thenReturnFirstScreenId() {
        setupWorkspacesWithHoles(
                screen1 = listOf(Rect(2, 2, 3, 3)), // 1x1 hole
                //  2 holes of sizes 3x2 and 2x3
                screen2 = listOf(Rect(2, 0, 5, 2), Rect(0, 2, 2, 5)),
        )

        val spaceFound = newTask().findSpaceForItem(
                mAppState, mModelHelper.bgDataModel, mExistingScreens, mNewScreens, 1, 1)
        assertEquals(1, spaceFound[0])
        assertTrue(mScreenOccupancy[spaceFound[0]]
                .isRegionVacant(spaceFound[1], spaceFound[2], 1, 1))
    }

    @Test
    fun notEnoughSpaceOnFirstScreen_whenFindSpaceForItem_thenReturnSecondScreenId() {
        setupWorkspacesWithHoles(
                screen1 = listOf(Rect(2, 2, 3, 3)), // 1x1 hole
                //  2 holes of sizes 3x2 and 2x3
                screen2 = listOf(Rect(2, 0, 5, 2), Rect(0, 2, 2, 5)),
        )

        // Find a larger space
        val spaceFound = newTask().findSpaceForItem(
                mAppState, mModelHelper.bgDataModel, mExistingScreens, mNewScreens, 2, 3)
        assertEquals(2, spaceFound[0])
        assertTrue(mScreenOccupancy[spaceFound[0]]
                .isRegionVacant(spaceFound[1], spaceFound[2], 2, 3))
    }

    @Test
    fun notEnoughSpaceOnExistingScreens_whenFindSpaceForItem_thenReturnNewScreenId() {
        setupWorkspacesWithHoles(
                //  2 holes of sizes 3x2 and 2x3
                screen1 = listOf(Rect(2, 0, 5, 2), Rect(0, 2, 2, 5)),
                //  2 holes of sizes 1x2 and 2x2
                screen2 = listOf(Rect(1, 0, 2, 2), Rect(3, 2, 5, 4)),
        )

        val oldScreens = mExistingScreens.clone()
        val spaceFound = newTask().findSpaceForItem(
                mAppState, mModelHelper.bgDataModel, mExistingScreens, mNewScreens, 3, 3)
        assertFalse(oldScreens.contains(spaceFound[0]))
        assertTrue(mNewScreens.contains(spaceFound[0]))
    }

    @Test
    fun enoughSpaceOnFirstScreen_whenTaskRuns_thenAddItemToFirstScreen() {
        val workspaceHoles = createWorkspaceHoles(
                screen1 = listOf(Rect(2, 2, 3, 3)), // 1x1 space
                screen2 = listOf(Rect(2, 0, 5, 2)), // 3x2 space
        )
        val addedItems = testAddItems(workspaceHoles, getNewItem())
        assertEquals(1, addedItems.size)
        assertEquals(1, addedItems.first().itemInfo.screenId)
    }

    @Test
    fun firstPageIsFull_whenTaskRuns_thenAddItemToSecondScreen() {
        val workspaceHoles = createWorkspaceHoles(
                screen1 = fullScreenHoles,
        )
        val addedItems = testAddItems(workspaceHoles, getNewItem())
        assertEquals(1, addedItems.size)
        assertEquals(2, addedItems.first().itemInfo.screenId)
    }

    @Test
    fun firstScreenIsEmptyButSecondIsNotEmpty_whenTaskRuns_thenAddItemToSecondScreen() {
        val workspaceHoles = createWorkspaceHoles(
                screen1 = emptyScreenHoles,
                screen2 = listOf(Rect(2, 0, 5, 2)), // 3x2 space
        )
        val addedItems = testAddItems(workspaceHoles, getNewItem())
        assertEquals(1, addedItems.size)
        assertEquals(2, addedItems.first().itemInfo.screenId)
    }

    @Test
    fun twoEmptyMiddleScreens_whenTaskRuns_thenAddItemToThirdScreen() {
        val workspaceHoles = createWorkspaceHoles(
                screen1 = emptyScreenHoles,
                screen2 = emptyScreenHoles,
                screen3 = listOf(Rect(1, 1, 4, 4)), // 3x3 space
        )
        val addedItems = testAddItems(workspaceHoles, getNewItem())
        assertEquals(1, addedItems.size)
        assertEquals(3, addedItems.first().itemInfo.screenId)
    }

    @Test
    fun allPagesAreFull_whenTaskRuns_thenAddItemToNewScreen() {
        val workspaceHoles = createWorkspaceHoles(
                screen1 = fullScreenHoles,
                screen2 = fullScreenHoles,
        )
        val addedItems = testAddItems(workspaceHoles, getNewItem())
        assertEquals(1, addedItems.size)
        assertEquals(3, addedItems.first().itemInfo.screenId)
    }

    @Test
    fun firstTwoPagesAreFull_and_ThirdPageIsEmpty_whenTaskRuns_thenAddItemToThirdPage() {
        val workspaceHoles = createWorkspaceHoles(
                screen1 = fullScreenHoles,
                screen2 = fullScreenHoles,
                screen3 = emptyScreenHoles
        )
        val addedItems = testAddItems(workspaceHoles, getNewItem())
        assertEquals(1, addedItems.size)
        assertEquals(3, addedItems.first().itemInfo.screenId)
    }

    @Test
    fun itemIsAlreadyAdded_whenTaskRun_thenIgnoreItem() {
        val task = newTask(getExistingItem())
        setupWorkspacesWithHoles(
                screen1 = listOf(Rect(2, 2, 3, 3)), // 1x1 hole
        )

        // Nothing was added
        assertTrue(mModelHelper.executeTaskForTest(task).isEmpty())
    }

    @Test
    fun newAndExistingItems_whenTaskRun_thenAddOnlyTheNewOne() {
        val newItem = getNewItem()
        val workspaceHoles = createWorkspaceHoles(
                screen1 = listOf(Rect(2, 2, 3, 3)), // 1x1 hole
        )
        val addedItems = testAddItems(workspaceHoles, getExistingItem(), newItem)
        assertEquals(1, addedItems.size)
        val addedItem = addedItems.first()
        assert(addedItem.isAnimated)
        val addedItemInfo = addedItem.itemInfo
        assertEquals(1, addedItemInfo.screenId)
        assertEquals(newItem, addedItemInfo)
    }

    private fun testAddItems(
            workspaceHoles: List<List<Rect>>,
            vararg itemsToAdd: WorkspaceItemInfo
    ): List<AddedItem> {
        setupWorkspaces(workspaceHoles)
        mModelHelper.executeTaskForTest(newTask(*itemsToAdd))[0].run()

        verify(dataModelCallbacks).bindAppsAdded(any(),
                notAnimatedItemArgumentCaptor.capture(), animatedItemArgumentCaptor.capture())

        val addedItems = mutableListOf<AddedItem>()
        addedItems.addAll(animatedItemArgumentCaptor.value.map { AddedItem(it, true) })
        addedItems.addAll(notAnimatedItemArgumentCaptor.value.map { AddedItem(it, false) })
        return addedItems
    }

    private fun setupWorkspaces(workspaceHoles: List<List<Rect>>) {
        var nextItemId = 1
        var screenId = 1
        workspaceHoles.forEach { holes ->
            nextItemId = setupWorkspace(nextItemId, screenId++, *holes.toTypedArray())
        }
    }

    private fun setupWorkspace(startId: Int, screenId: Int, vararg holes: Rect): Int {
        return mModelHelper.executeSimpleTask { dataModel ->
            writeWorkspaceWithHoles(dataModel, startId, screenId, *holes)
        }
    }

    private fun writeWorkspaceWithHoles(
            bgDataModel: BgDataModel,
            itemStartId: Int,
            screenId: Int,
            vararg holes: Rect,
    ): Int {
        var itemId = itemStartId
        val occupancy = GridOccupancy(mIdp.numColumns, mIdp.numRows)
        occupancy.markCells(0, 0, mIdp.numColumns, mIdp.numRows, true)
        holes.forEach { holeRect ->
            occupancy.markCells(holeRect, false)
        }
        mExistingScreens.add(screenId)
        mScreenOccupancy.append(screenId, occupancy)
        for (x in 0 until mIdp.numColumns) {
            for (y in 0 until mIdp.numRows) {
                if (!occupancy.cells[x][y]) {
                    continue
                }
                val info = getExistingItem()
                info.id = itemId++
                info.screenId = screenId
                info.cellX = x
                info.cellY = y
                info.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
                bgDataModel.addItem(mTargetContext, info, false)
                val writer = ContentWriter(mTargetContext)
                info.writeToValues(writer)
                writer.put(LauncherSettings.Favorites._ID, info.id)
                mTargetContext.contentResolver.insert(LauncherSettings.Favorites.CONTENT_URI,
                        writer.getValues(mTargetContext))
            }
        }
        return itemId
    }

    private fun setupWorkspacesWithHoles(
            screen1: List<Rect>? = null,
            screen2: List<Rect>? = null,
            screen3: List<Rect>? = null,
    ) = createWorkspaceHoles(screen1, screen2, screen3)
            .let(this::setupWorkspaces)

    private fun createWorkspaceHoles(
            screen1: List<Rect>? = null,
            screen2: List<Rect>? = null,
            screen3: List<Rect>? = null,
    ): List<List<Rect>> = listOfNotNull(screen1, screen2, screen3)

    private fun newTask(vararg items: ItemInfo): AddWorkspaceItemsTask =
            items.map { Pair.create(it, Any()) }
                    .toMutableList()
                    .let(::AddWorkspaceItemsTask)

    private fun getExistingItem() = WorkspaceItemInfo()
            .apply { intent = Intent().setComponent(ComponentName("a", "b")) }

    private fun getNewItem() = WorkspaceItemInfo()
            .apply { intent = Intent().setComponent(ComponentName("b", "b")) }
}

private data class AddedItem(
        val itemInfo: ItemInfo,
        val isAnimated: Boolean
)