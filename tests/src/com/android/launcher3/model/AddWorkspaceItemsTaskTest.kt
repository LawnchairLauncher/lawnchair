/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.util.Pair
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.TestUtil.runOnExecutorSync
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyZeroInteractions
import org.mockito.kotlin.whenever

/** Tests for [AddWorkspaceItemsTask] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AddWorkspaceItemsTaskTest : AbstractWorkspaceModelTest() {

    private lateinit var mDataModelCallbacks: MyCallbacks

    private val mWorkspaceItemSpaceFinder: WorkspaceItemSpaceFinder = mock()

    @Before
    override fun setup() {
        super.setup()
        mDataModelCallbacks = MyCallbacks()
        Executors.MAIN_EXECUTOR.submit { mModelHelper.model.addCallbacks(mDataModelCallbacks) }
            .get()
    }

    @After
    override fun tearDown() {
        super.tearDown()
    }

    @Test
    fun givenNewItemAndNonEmptyPages_whenExecuteTask_thenAddNewItem() {
        val itemToAdd = getNewItem()
        val nonEmptyScreenIds = listOf(0, 1, 2)
        givenNewItemSpaces(NewItemSpace(1, 2, 2))

        val addedItems = testAddItems(nonEmptyScreenIds, itemToAdd)

        assertThat(addedItems.size).isEqualTo(1)
        assertThat(addedItems.first().itemInfo.screenId).isEqualTo(1)
        assertThat(addedItems.first().isAnimated).isTrue()
        verifyItemSpaceFinderCall(nonEmptyScreenIds, numberOfExpectedCall = 1)
    }

    @Test
    fun givenNewAndExistingItems_whenExecuteTask_thenOnlyAddNewItem() {
        val itemsToAdd = arrayOf(getNewItem(), getExistingItem())
        givenNewItemSpaces(NewItemSpace(1, 0, 0))
        val nonEmptyScreenIds = listOf(0)

        val addedItems = testAddItems(nonEmptyScreenIds, *itemsToAdd)

        assertThat(addedItems.size).isEqualTo(1)
        assertThat(addedItems.first().itemInfo.screenId).isEqualTo(1)
        assertThat(addedItems.first().isAnimated).isTrue()
        verifyItemSpaceFinderCall(nonEmptyScreenIds, numberOfExpectedCall = 1)
    }

    @Test
    fun givenOnlyExistingItem_whenExecuteTask_thenDoNotAddItem() {
        val itemToAdd = getExistingItem()
        givenNewItemSpaces(NewItemSpace(1, 0, 0))
        val nonEmptyScreenIds = listOf(0)

        val addedItems = testAddItems(nonEmptyScreenIds, itemToAdd)

        assertThat(addedItems.size).isEqualTo(0)
        verifyZeroInteractions(mWorkspaceItemSpaceFinder)
    }

    @Test
    fun givenNonSequentialScreenIds_whenExecuteTask_thenReturnNewScreenId() {
        val itemToAdd = getNewItem()
        givenNewItemSpaces(NewItemSpace(2, 1, 3))
        val nonEmptyScreenIds = listOf(0, 2, 3)

        val addedItems = testAddItems(nonEmptyScreenIds, itemToAdd)

        assertThat(addedItems.size).isEqualTo(1)
        assertThat(addedItems.first().itemInfo.screenId).isEqualTo(2)
        assertThat(addedItems.first().isAnimated).isTrue()
        verifyItemSpaceFinderCall(nonEmptyScreenIds, numberOfExpectedCall = 1)
    }

    @Test
    fun givenMultipleItems_whenExecuteTask_thenAddThem() {
        val itemsToAdd =
            arrayOf(
                getNewItem(),
                getExistingItem(),
                getNewItem(),
                getNewItem(),
                getExistingItem(),
            )
        givenNewItemSpaces(
            NewItemSpace(1, 3, 3),
            NewItemSpace(2, 0, 0),
            NewItemSpace(2, 0, 1),
        )
        val nonEmptyScreenIds = listOf(0, 1)

        val addedItems = testAddItems(nonEmptyScreenIds, *itemsToAdd)

        // Only the new items should be added
        assertThat(addedItems.size).isEqualTo(3)

        // Items that are added to the first screen should not be animated
        val itemsAddedToFirstScreen = addedItems.filter { it.itemInfo.screenId == 1 }
        assertThat(itemsAddedToFirstScreen.size).isEqualTo(1)
        assertThat(itemsAddedToFirstScreen.first().isAnimated).isFalse()

        // Items that are added to the second screen should be animated
        val itemsAddedToSecondScreen = addedItems.filter { it.itemInfo.screenId == 2 }
        assertThat(itemsAddedToSecondScreen.size).isEqualTo(2)
        itemsAddedToSecondScreen.forEach { assertThat(it.isAnimated).isTrue() }
        verifyItemSpaceFinderCall(nonEmptyScreenIds, numberOfExpectedCall = 3)
    }

    /** Sets up the item space data that will be returned from WorkspaceItemSpaceFinder. */
    private fun givenNewItemSpaces(vararg newItemSpaces: NewItemSpace) {
        val spaceStack = newItemSpaces.toMutableList()
        whenever(
                mWorkspaceItemSpaceFinder.findSpaceForItem(any(), any(), any(), any(), any(), any())
            )
            .then { spaceStack.removeFirst().toIntArray() }
    }

    /**
     * Verifies if WorkspaceItemSpaceFinder was called with proper arguments and how many times was
     * it called.
     */
    private fun verifyItemSpaceFinderCall(nonEmptyScreenIds: List<Int>, numberOfExpectedCall: Int) {
        verify(mWorkspaceItemSpaceFinder, times(numberOfExpectedCall))
            .findSpaceForItem(
                same(mAppState),
                same(mModelHelper.bgDataModel),
                eq(IntArray.wrap(*nonEmptyScreenIds.toIntArray())),
                eq(IntArray()),
                eq(1),
                eq(1)
            )
    }

    /**
     * Sets up the workspaces with items, executes the task, collects the added items from the model
     * callback then returns it.
     */
    private fun testAddItems(
        nonEmptyScreenIds: List<Int>,
        vararg itemsToAdd: WorkspaceItemInfo
    ): List<AddedItem> {
        setupWorkspaces(nonEmptyScreenIds)
        val task = newTask(*itemsToAdd)

        val addedItems = mutableListOf<AddedItem>()

        runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            mDataModelCallbacks.addedItems.clear()
            mModelHelper.model.enqueueModelUpdateTask(task)
            runOnExecutorSync(Executors.MAIN_EXECUTOR) {}
            addedItems.addAll(mDataModelCallbacks.addedItems)
        }

        return addedItems
    }

    /**
     * Creates the task with the given items and replaces the WorkspaceItemSpaceFinder dependency
     * with a mock.
     */
    private fun newTask(vararg items: ItemInfo): AddWorkspaceItemsTask =
        items
            .map { Pair.create(it, Any()) }
            .toMutableList()
            .let { AddWorkspaceItemsTask(it, mWorkspaceItemSpaceFinder) }
}

private data class AddedItem(val itemInfo: ItemInfo, val isAnimated: Boolean)

private class MyCallbacks : BgDataModel.Callbacks {

    val addedItems = mutableListOf<AddedItem>()

    override fun bindAppsAdded(
        newScreens: IntArray?,
        addNotAnimated: ArrayList<ItemInfo>,
        addAnimated: ArrayList<ItemInfo>
    ) {
        addedItems.addAll(addAnimated.map { AddedItem(it, true) })
        addedItems.addAll(addNotAnimated.map { AddedItem(it, false) })
    }
}
