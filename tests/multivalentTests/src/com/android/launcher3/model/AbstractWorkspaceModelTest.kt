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
import android.graphics.Rect
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.GridOccupancy
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.IntSparseArrayMap
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import java.util.UUID

/** Base class for workspace related tests. */
abstract class AbstractWorkspaceModelTest {
    companion object {
        val emptyScreenSpaces = listOf(Rect(0, 0, 5, 5))
        val fullScreenSpaces = emptyList<Rect>()
        val nonEmptyScreenSpaces = listOf(Rect(1, 2, 3, 4))
    }

    protected lateinit var mLayoutBuilder: LauncherLayoutBuilder
    protected lateinit var mTargetContext: Context
    protected lateinit var mIdp: InvariantDeviceProfile
    protected lateinit var mAppState: LauncherAppState
    protected lateinit var mModelHelper: LauncherModelHelper
    protected lateinit var mExistingScreens: IntArray
    protected lateinit var mNewScreens: IntArray
    protected lateinit var mScreenOccupancy: IntSparseArrayMap<GridOccupancy>

    open fun setup() {
        mLayoutBuilder = LauncherLayoutBuilder()
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
        screenIdsWithItems.forEach { screenId -> setupWorkspace(screenId, nonEmptyScreenSpaces) }
        mModelHelper.setupDefaultLayoutProvider(mLayoutBuilder)
        mIdp.numRows = 5
        mIdp.numColumns = mIdp.numRows
        mModelHelper.loadModelSync()
    }

    /**
     * Sets up the given workspaces with the given spaces, and fills the remaining space with items.
     */
    fun setupWorkspacesWithSpaces(
        screen0: List<Rect>? = null,
        screen1: List<Rect>? = null,
        screen2: List<Rect>? = null,
        screen3: List<Rect>? = null,
    ) {
        listOf(screen0, screen1, screen2, screen3).let(this::setupWithSpaces)
        mModelHelper.setupDefaultLayoutProvider(mLayoutBuilder)
        mIdp.numRows = 5
        mIdp.numColumns = mIdp.numRows
        mModelHelper.loadModelSync()
    }

    private fun setupWithSpaces(workspaceSpaces: List<List<Rect>?>) {
        workspaceSpaces.forEachIndexed { screenId, spaces ->
            if (spaces != null) {
                setupWorkspace(screenId, spaces)
            }
        }
    }

    private fun setupWorkspace(screenId: Int, spaces: List<Rect>) {
        val occupancy = GridOccupancy(mIdp.numColumns, mIdp.numRows)
        occupancy.markCells(0, 0, mIdp.numColumns, mIdp.numRows, true)
        spaces.forEach { spaceRect -> occupancy.markCells(spaceRect, false) }
        mExistingScreens.add(screenId)
        mScreenOccupancy.append(screenId, occupancy)
        for (x in 0 until mIdp.numColumns) {
            for (y in 0 until mIdp.numRows) {
                if (occupancy.cells[x][y]) {
                    mLayoutBuilder.atWorkspace(x, y, screenId).putApp(TEST_PACKAGE, TEST_ACTIVITY)
                }
            }
        }
    }

    fun getExistingItem() =
        WorkspaceItemInfo().apply {
            intent = AppInfo.makeLaunchIntent(ComponentName(TEST_PACKAGE, TEST_ACTIVITY))
        }

    fun getNewItem(): WorkspaceItemInfo {
        val itemPackage = UUID.randomUUID().toString()
        return WorkspaceItemInfo().apply {
            intent = AppInfo.makeLaunchIntent(ComponentName(itemPackage, itemPackage))
        }
    }
}

data class NewItemSpace(val screenId: Int, val cellX: Int, val cellY: Int) {
    fun toIntArray() = intArrayOf(screenId, cellX, cellY)

    companion object {
        fun fromIntArray(array: kotlin.IntArray) = NewItemSpace(array[0], array[1], array[2])
    }
}
