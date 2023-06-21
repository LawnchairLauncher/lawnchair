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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.ContentWriter
import com.android.launcher3.util.GridOccupancy
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.IntSparseArrayMap
import com.android.launcher3.util.LauncherModelHelper
import java.util.UUID

/** Base class for workspace related tests. */
abstract class AbstractWorkspaceModelTest {
    companion object {
        val emptyScreenSpaces = listOf(Rect(0, 0, 5, 5))
        val fullScreenSpaces = emptyList<Rect>()
        val nonEmptyScreenSpaces = listOf(Rect(1, 2, 3, 4))
    }

    protected lateinit var mTargetContext: Context
    protected lateinit var mIdp: InvariantDeviceProfile
    protected lateinit var mAppState: LauncherAppState
    protected lateinit var mModelHelper: LauncherModelHelper
    protected lateinit var mExistingScreens: IntArray
    protected lateinit var mNewScreens: IntArray
    protected lateinit var mScreenOccupancy: IntSparseArrayMap<GridOccupancy>

    open fun setup() {
        mModelHelper = LauncherModelHelper()
        mTargetContext = mModelHelper.sandboxContext
        mIdp = InvariantDeviceProfile.INSTANCE[mTargetContext]
        mIdp.numRows = 5
        mIdp.numColumns = mIdp.numRows
        mAppState = LauncherAppState.getInstance(mTargetContext)
        mExistingScreens = IntArray()
        mScreenOccupancy = IntSparseArrayMap()
        mNewScreens = IntArray()
    }

    open fun tearDown() {
        mModelHelper.destroy()
    }

    /** Sets up workspaces with the given screen IDs with some items and a 2x2 space. */
    fun setupWorkspaces(screenIdsWithItems: List<Int>) {
        var nextItemId = 1
        screenIdsWithItems.forEach { screenId ->
            nextItemId = setupWorkspace(nextItemId, screenId, nonEmptyScreenSpaces)
        }
    }

    /**
     * Sets up the given workspaces with the given spaces, and fills the remaining space with items.
     */
    fun setupWorkspacesWithSpaces(
        screen0: List<Rect>? = null,
        screen1: List<Rect>? = null,
        screen2: List<Rect>? = null,
        screen3: List<Rect>? = null,
    ) = listOf(screen0, screen1, screen2, screen3).let(this::setupWithSpaces)

    private fun setupWithSpaces(workspaceSpaces: List<List<Rect>?>) {
        var nextItemId = 1
        workspaceSpaces.forEachIndexed { screenId, spaces ->
            if (spaces != null) {
                nextItemId = setupWorkspace(nextItemId, screenId, spaces)
            }
        }
    }

    private fun setupWorkspace(startId: Int, screenId: Int, spaces: List<Rect>): Int {
        return mModelHelper.executeSimpleTask { dataModel ->
            writeWorkspaceWithSpaces(dataModel, startId, screenId, spaces)
        }
    }

    private fun writeWorkspaceWithSpaces(
        bgDataModel: BgDataModel,
        itemStartId: Int,
        screenId: Int,
        spaces: List<Rect>,
    ): Int {
        var itemId = itemStartId
        val occupancy = GridOccupancy(mIdp.numColumns, mIdp.numRows)
        occupancy.markCells(0, 0, mIdp.numColumns, mIdp.numRows, true)
        spaces.forEach { spaceRect -> occupancy.markCells(spaceRect, false) }
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
                mTargetContext.contentResolver.insert(
                    LauncherSettings.Favorites.CONTENT_URI,
                    writer.getValues(mTargetContext)
                )
            }
        }
        return itemId
    }

    fun getExistingItem() =
        WorkspaceItemInfo().apply { intent = Intent().setComponent(ComponentName("a", "b")) }

    fun getNewItem(): WorkspaceItemInfo {
        val itemPackage = UUID.randomUUID().toString()
        return WorkspaceItemInfo().apply {
            intent = Intent().setComponent(ComponentName(itemPackage, itemPackage))
        }
    }
}

data class NewItemSpace(val screenId: Int, val cellX: Int, val cellY: Int) {
    fun toIntArray() = intArrayOf(screenId, cellX, cellY)

    companion object {
        fun fromIntArray(array: kotlin.IntArray) = NewItemSpace(array[0], array[1], array[2])
    }
}
