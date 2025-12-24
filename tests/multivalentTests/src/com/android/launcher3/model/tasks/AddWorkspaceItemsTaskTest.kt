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

package com.android.launcher3.model.tasks

import android.content.ComponentName
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.util.Pair
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID
import com.android.launcher3.model.BgDataModel.Callbacks
import com.android.launcher3.model.TestableModelState
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceChangeEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.AddEvent
import com.android.launcher3.model.data.WorkspaceItemCoordinates
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.IntSet
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.LayoutResource
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil.runOnExecutorSync
import com.google.common.truth.Truth.assertThat
import java.util.ArrayList
import java.util.UUID
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

/** Tests for [AddWorkspaceItemsTask] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AddWorkspaceItemsTaskTest {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val targetContext = SandboxApplication().withModelDependency()
    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule var layout = LayoutResource(targetContext)

    private var mDataModelCallbacks = MyCallbacks()

    @Mock lateinit var workspaceItemSpaceFinder: WorkspaceItemSpaceFinder

    private val modelState: TestableModelState
        get() = targetContext.appComponent.testableModelState

    @Before
    fun setup() {
        runOnExecutorSync(Executors.MAIN_EXECUTOR) {
            modelState.model.addCallbacks(mDataModelCallbacks)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun givenNewItemAndNonEmptyPages_whenExecuteTask_thenAddNewItem() {
        val itemToAdd = getNewItem()
        val nonEmptyScreenIds = listOf(0, 1, 2)
        givenNewItemSpaces(WorkspaceItemCoordinates(1, 2, 2))

        testAddItems(nonEmptyScreenIds, itemToAdd) { addedItems ->
            assertThat(addedItems.size).isEqualTo(1)
            assertThat(addedItems.first().screenId).isEqualTo(1)
            verifyItemSpaceFinderCall(numberOfExpectedCall = 1, addedItems)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun givenNewAndExistingItems_whenExecuteTask_thenOnlyAddNewItem() {
        val itemsToAdd = arrayOf(getNewItem(), getExistingItem())
        givenNewItemSpaces(WorkspaceItemCoordinates(1, 0, 0))
        val nonEmptyScreenIds = listOf(0)

        testAddItems(nonEmptyScreenIds, *itemsToAdd) { addedItems ->
            assertThat(addedItems.size).isEqualTo(1)
            assertThat(addedItems.first().screenId).isEqualTo(1)
            verifyItemSpaceFinderCall(numberOfExpectedCall = 1, addedItems)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun givenOnlyExistingItem_whenExecuteTask_thenDoNotAddItem() {
        val itemToAdd = getExistingItem()
        givenNewItemSpaces(WorkspaceItemCoordinates(1, 0, 0))
        val nonEmptyScreenIds = listOf(0)

        testAddItems(nonEmptyScreenIds, itemToAdd) { addedItems ->
            assertThat(addedItems.size).isEqualTo(0)
            // b/343530737
            verifyNoMoreInteractions(workspaceItemSpaceFinder)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun givenNonSequentialScreenIds_whenExecuteTask_thenReturnNewScreenId() {
        val itemToAdd = getNewItem()
        givenNewItemSpaces(WorkspaceItemCoordinates(2, 1, 3))
        val nonEmptyScreenIds = listOf(0, 2, 3)

        testAddItems(nonEmptyScreenIds, itemToAdd) { addedItems ->
            assertThat(addedItems.size).isEqualTo(1)
            assertThat(addedItems.first().screenId).isEqualTo(2)
            verifyItemSpaceFinderCall(numberOfExpectedCall = 1, addedItems)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun givenMultipleItems_whenExecuteTask_thenAddThem() {
        val itemsToAdd =
            arrayOf(getNewItem(), getExistingItem(), getNewItem(), getNewItem(), getExistingItem())
        givenNewItemSpaces(
            WorkspaceItemCoordinates(1, 3, 3),
            WorkspaceItemCoordinates(2, 0, 0),
            WorkspaceItemCoordinates(2, 0, 1),
        )
        val nonEmptyScreenIds = listOf(0, 1)

        testAddItems(nonEmptyScreenIds, *itemsToAdd) { addedItems ->

            // Only the new items should be added
            assertThat(addedItems.size).isEqualTo(3)

            // Items that are added to the first screen should not be animated
            val itemsAddedToFirstScreen = addedItems.filter { it.screenId == 1 }
            assertThat(itemsAddedToFirstScreen.size).isEqualTo(1)

            // Items that are added to the second screen should be animated
            val itemsAddedToSecondScreen = addedItems.filter { it.screenId == 2 }
            assertThat(itemsAddedToSecondScreen.size).isEqualTo(2)
            verifyItemSpaceFinderCall(numberOfExpectedCall = 3, addedItems)
        }
    }

    /** Sets up the item space data that will be returned from WorkspaceItemSpaceFinder. */
    private fun givenNewItemSpaces(vararg newItemSpaces: WorkspaceItemCoordinates) {
        val spaceStack = newItemSpaces.toMutableList()
        whenever(workspaceItemSpaceFinder.findSpaceForItem(any(), any(), any(), any())).then {
            spaceStack.removeFirst()
        }
    }

    /**
     * Verifies if WorkspaceItemSpaceFinder was called with proper arguments and how many times was
     * it called.
     */
    private fun verifyItemSpaceFinderCall(numberOfExpectedCall: Int, items: List<ItemInfo>) {
        verify(workspaceItemSpaceFinder, times(numberOfExpectedCall))
            .findSpaceForItem(eq(ArrayList(items)), eq(1), eq(1), eq(IntSet.wrap(FIRST_SCREEN_ID)))
    }

    /**
     * Sets up the workspaces with items, executes the task, collects the added items from the model
     * callback then returns it.
     */
    private fun testAddItems(
        nonEmptyScreenIds: List<Int>,
        vararg itemsToAdd: WorkspaceItemInfo,
        verification: (List<ItemInfo>) -> Unit,
    ) {
        val layoutBuilder = LauncherLayoutBuilder()
        nonEmptyScreenIds.forEach { screenId ->
            val idp = targetContext.appComponent.idp
            for (x in 0 until idp.numColumns) {
                for (y in 0 until idp.numRows) {
                    layoutBuilder.atWorkspace(x, y, screenId).putApp(TEST_PACKAGE, TEST_ACTIVITY)
                }
            }
        }
        layout.set(layoutBuilder)
        val task = newTask(*itemsToAdd)

        runOnExecutorSync(MODEL_EXECUTOR) {
            val workspaceUpdates = mutableListOf<WorkspaceChangeEvent?>()
            modelState.homeRepo.workspaceState.changes.forEach(MODEL_EXECUTOR) {
                workspaceUpdates.add(it)
            }

            mDataModelCallbacks.addedItems.clear()
            modelState.model.enqueueModelUpdateTask(task)

            // Verify that only one workspace update was pushed
            assertThat(workspaceUpdates).hasSize(1)
            val addEvent = workspaceUpdates[0] as AddEvent
            verification.invoke(addEvent.items)

            // Verify the legacy callback behavior
            runOnExecutorSync(Executors.MAIN_EXECUTOR) {}
            verification.invoke(mDataModelCallbacks.addedItems.toList())
        }
    }

    /**
     * Creates the task with the given items and replaces the WorkspaceItemSpaceFinder dependency
     * with a mock.
     */
    private fun newTask(vararg items: ItemInfo) =
        AddWorkspaceItemsTask(items.map { Pair.create(it, Any()) }, workspaceItemSpaceFinder)

    private fun getExistingItem() =
        WorkspaceItemInfo().apply {
            intent = AppInfo.makeLaunchIntent(ComponentName(TEST_PACKAGE, TEST_ACTIVITY))
        }

    private fun getNewItem(): WorkspaceItemInfo {
        val itemPackage = UUID.randomUUID().toString()
        return WorkspaceItemInfo().apply {
            intent = AppInfo.makeLaunchIntent(ComponentName(itemPackage, itemPackage))
        }
    }
}

private class MyCallbacks : Callbacks {

    val addedItems = mutableListOf<ItemInfo>()

    override fun bindItemsAdded(items: List<ItemInfo>) {
        addedItems.addAll(items)
    }
}
