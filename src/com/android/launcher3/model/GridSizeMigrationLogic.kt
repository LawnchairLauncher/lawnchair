/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import android.util.Log
import androidx.annotation.VisibleForTesting
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.BuildConfig
import com.android.launcher3.BuildConfigs
import com.android.launcher3.Flags
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.get
import com.android.launcher3.LauncherSettings
import com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME
import com.android.launcher3.LauncherSettings.Favorites.TMP_TABLE
import com.android.launcher3.logging.FileLog
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ROW_SHIFT_GRID_MIGRATION
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ROW_SHIFT_ONE_GRID_MIGRATION
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_STANDARD_GRID_MIGRATION
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_STANDARD_ONE_GRID_MIGRATION
import com.android.launcher3.model.GridSizeMigrationDBController.DbReader
import com.android.launcher3.model.GridSizeMigrationDBController.isOneGridMigration
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction
import com.android.launcher3.provider.LauncherDbUtils.copyTable
import com.android.launcher3.provider.LauncherDbUtils.dropTable
import com.android.launcher3.provider.LauncherDbUtils.shiftWorkspaceByXCells
import com.android.launcher3.util.CellAndSpan
import com.android.launcher3.util.GridOccupancy
import com.android.launcher3.util.IntArray
import com.patrykmichalik.opto.core.firstBlocking

class GridSizeMigrationLogic {
    /**
     * Migrates the grid size from srcDeviceState to destDeviceState and make those changes in the
     * target DB, using the source DB to determine what to add/remove/move/resize in the destination
     * DB.
     */
    fun migrateGrid(
        context: Context,
        srcDeviceState: DeviceGridState,
        destDeviceState: DeviceGridState,
        target: DatabaseHelper,
        source: SQLiteDatabase,
        isDestNewDb: Boolean,
        modelDelegate: ModelDelegate,
    ) {
        if (!GridSizeMigrationDBController.needsToMigrate(srcDeviceState, destDeviceState)) {
            return
        }

        val statsLogManager: StatsLogManager = StatsLogManager.newInstance(context)

        val isAfterRestore = get(context).get(LauncherPrefs.IS_FIRST_LOAD_AFTER_RESTORE)
        FileLog.d(
            TAG,
            "Begin grid migration. isAfterRestore: $isAfterRestore\nsrcDeviceState: " +
                "$srcDeviceState\ndestDeviceState: $destDeviceState\nisDestNewDb: $isDestNewDb",
        )

        val shouldMigrateToStrtictlyTallerGrid =
            shouldMigrateToStrictlyTallerGrid(isDestNewDb, srcDeviceState, destDeviceState)
        if (shouldMigrateToStrtictlyTallerGrid) {
            copyTable(source, TABLE_NAME, target.writableDatabase, TABLE_NAME, context)
        } else {
            copyTable(source, TABLE_NAME, target.writableDatabase, TMP_TABLE, context)
        }

        val migrationStartTime = System.currentTimeMillis()
        try {
            SQLiteTransaction(target.writableDatabase).use { t ->
                // We want to add the extra row(s) to the top of the screen, so we shift the grid
                // down.
                if (shouldMigrateToStrtictlyTallerGrid) {
                    Log.d(TAG, "Migrating to strictly taller grid")
                    if (Flags.oneGridSpecs()) {
                        shiftWorkspaceByXCells(
                            target.writableDatabase,
                            (destDeviceState.rows - srcDeviceState.rows),
                            TABLE_NAME,
                        )
                    }
                    // Save current configuration, so that the migration does not run again.
                    destDeviceState.writeToPrefs(context)
                    t.commit()

                    if (isOneGridMigration(srcDeviceState, destDeviceState)) {
                        statsLogManager.logger().log(LAUNCHER_ROW_SHIFT_ONE_GRID_MIGRATION)
                    }
                    statsLogManager.logger().log(LAUNCHER_ROW_SHIFT_GRID_MIGRATION)

                    return
                }

                val srcReader = DbReader(t.db, TMP_TABLE, context)
                val destReader = DbReader(t.db, TABLE_NAME, context)

                val targetSize = Point(destDeviceState.columns, destDeviceState.rows)

                // Here we keep all the DB ids we have in the destination DB such that we don't
                // assign
                // an item that we want to add to the destination DB the same id as an already
                // existing
                // item.
                val idsInUse = mutableListOf<Int>()

                // Migrate hotseat.
                migrateHotseat(
                    srcDeviceState.numHotseat,
                    destDeviceState.numHotseat,
                    srcReader,
                    destReader,
                    target,
                    idsInUse,
                )
                // Migrate workspace.
                migrateWorkspace(srcReader, destReader, target, targetSize, idsInUse)

                dropTable(t.db, TMP_TABLE)
                t.commit()

                if (isOneGridMigration(srcDeviceState, destDeviceState)) {
                    statsLogManager.logger().log(LAUNCHER_STANDARD_ONE_GRID_MIGRATION)
                }
                statsLogManager.logger().log(LAUNCHER_STANDARD_GRID_MIGRATION)
            }
        } catch (e: Exception) {
            FileLog.e(TAG, "Error during grid migration", e)
        } finally {
            Log.v(
                TAG,
                "Workspace migration completed in " +
                    (System.currentTimeMillis() - migrationStartTime),
            )

            // Save current configuration, so that the migration does not run again.
            destDeviceState.writeToPrefs(context)

            // Notify if we've migrated successfully
            modelDelegate.gridMigrationComplete(srcDeviceState, destDeviceState)
        }
    }

    /** Handles hotseat migration. */
    @VisibleForTesting
    fun migrateHotseat(
        srcHotseatSize: Int,
        destHotseatSize: Int,
        srcReader: DbReader,
        destReader: DbReader,
        helper: DatabaseHelper,
        idsInUse: MutableList<Int>,
    ) {
        val srcHotseatItems = srcReader.loadHotseatEntries()
        val dstHotseatItems = destReader.loadHotseatEntries()

        // We want to filter out the hotseat items that are placed beyond the size of the source
        // grid as we always want to keep those extra items from the destination grid.
        var filteredDstHotseatItems = dstHotseatItems
        if (srcHotseatSize < destHotseatSize) {
            filteredDstHotseatItems =
                filteredDstHotseatItems.filter { entry -> entry.screenId < srcHotseatSize }
        }

        val itemsToBeAdded = getItemsToBeAdded(srcHotseatItems, filteredDstHotseatItems)
        val itemsToBeRemoved = getItemsToBeRemoved(srcHotseatItems, filteredDstHotseatItems)

        if (DEBUG) {
            Log.d(
                TAG,
                """Start hotseat migration:
            |Removing Hotseat Items: [${filteredDstHotseatItems.filter { itemsToBeRemoved.contains(it.id) }
                .joinToString(",\n") { it.toString() }}]
            |Adding Hotseat Items: [${itemsToBeAdded
                .joinToString(",\n") { it.toString() }}]
            |"""
                    .trimMargin(),
            )
        }

        // Removes the items that we need to remove from the destination DB.
        if (!itemsToBeRemoved.isEmpty) {
            GridSizeMigrationDBController.removeEntryFromDb(
                destReader.mDb,
                destReader.mTableName,
                itemsToBeRemoved,
            )
        }

        val remainingDstHotseatItems = destReader.loadHotseatEntries()

        placeHotseatItems(
            itemsToBeAdded,
            remainingDstHotseatItems,
            destHotseatSize,
            helper,
            srcReader,
            destReader,
            idsInUse,
        )
    }

    private fun placeHotseatItems(
        hotseatToBeAdded: MutableList<DbEntry>,
        dstHotseatItems: List<DbEntry>,
        destHotseatSize: Int,
        helper: DatabaseHelper,
        srcReader: DbReader,
        destReader: DbReader,
        idsInUse: MutableList<Int>,
    ) {
        if (hotseatToBeAdded.isEmpty()) {
            return
        }

        idsInUse.addAll(dstHotseatItems.map { entry: DbEntry -> entry.id })

        hotseatToBeAdded.sort()

        val placementSolutionHotseat =
            solveHotseatPlacement(destHotseatSize, dstHotseatItems, hotseatToBeAdded)
        for (entryToPlace in placementSolutionHotseat) {
            GridSizeMigrationDBController.insertEntryInDb(
                helper,
                entryToPlace,
                srcReader.mTableName,
                destReader.mTableName,
                idsInUse,
            )
        }
    }

    @VisibleForTesting
    fun migrateWorkspace(
        srcReader: DbReader,
        destReader: DbReader,
        helper: DatabaseHelper,
        targetSize: Point,
        idsInUse: MutableList<Int>,
    ) {
        val srcWorkspaceItems = srcReader.loadAllWorkspaceEntries()

        val dstWorkspaceItems = destReader.loadAllWorkspaceEntries()

        val toBeRemoved = IntArray()

        val workspaceToBeAdded = getItemsToBeAdded(srcWorkspaceItems, dstWorkspaceItems)
        toBeRemoved.addAll(getItemsToBeRemoved(srcWorkspaceItems, dstWorkspaceItems))

        if (DEBUG) {
            Log.d(
                TAG,
                """Start workspace migration:
            |Source Device: [${srcWorkspaceItems.joinToString(",\n") { it.toString() }}]
            |Target Device: [${dstWorkspaceItems.joinToString(",\n") { it.toString() }}]
            |Removing Workspace Items: [${dstWorkspaceItems.filter { toBeRemoved.contains(it.id) }
                .joinToString(",\n") { it.toString() }}]
            |Adding Workspace Items: [${workspaceToBeAdded
                .joinToString(",\n") { it.toString() }}]
            |"""
                    .trimMargin(),
            )
        }

        // Removes the items that we need to remove from the destination DB.
        if (!toBeRemoved.isEmpty) {
            GridSizeMigrationDBController.removeEntryFromDb(
                destReader.mDb,
                destReader.mTableName,
                toBeRemoved,
            )
        }

        val remainingDstWorkspaceItems = destReader.loadAllWorkspaceEntries()
        placeWorkspaceItems(
            workspaceToBeAdded,
            remainingDstWorkspaceItems,
            targetSize.x,
            targetSize.y,
            helper,
            srcReader,
            destReader,
            idsInUse,
        )
    }

    private fun placeWorkspaceItems(
        workspaceToBeAdded: MutableList<DbEntry>,
        dstWorkspaceItems: List<DbEntry>,
        trgX: Int,
        trgY: Int,
        helper: DatabaseHelper,
        srcReader: DbReader,
        destReader: DbReader,
        idsInUse: MutableList<Int>,
    ) {
        if (workspaceToBeAdded.isEmpty()) {
            return
        }

        idsInUse.addAll(dstWorkspaceItems.map { entry: DbEntry -> entry.id })

        workspaceToBeAdded.sort()

        // First we create a collection of the screens
        val screens: MutableList<Int> = ArrayList()
        for (screenId in 0..destReader.mLastScreenId) {
            screens.add(screenId)
        }

        // Then we place the items on the screens
        var itemsToPlace = WorkspaceItemsToPlace(workspaceToBeAdded, mutableListOf())
        for (screenId in screens) {
            if (DEBUG) {
                Log.d(TAG, "Migrating $screenId")
            }
            itemsToPlace =
                solveGridPlacement(
                    destReader.mContext,
                    screenId,
                    trgX,
                    trgY,
                    itemsToPlace.mRemainingItemsToPlace,
                    destReader.mWorkspaceEntriesByScreenId[screenId],
                )
            placeItems(itemsToPlace, helper, srcReader, destReader, idsInUse)
            while (itemsToPlace.mPlacementSolution.isNotEmpty()) {
                GridSizeMigrationDBController.insertEntryInDb(
                    helper,
                    itemsToPlace.mPlacementSolution.removeAt(0),
                    srcReader.mTableName,
                    destReader.mTableName,
                    idsInUse,
                )
            }
            if (itemsToPlace.mRemainingItemsToPlace.isEmpty()) {
                break
            }
        }

        // In case the new grid is smaller, there might be some leftover items that don't fit on
        // any of the screens, in this case we add them to new screens until all of them are placed.
        var screenId = destReader.mLastScreenId + 1
        while (itemsToPlace.mRemainingItemsToPlace.isNotEmpty()) {
            itemsToPlace =
                solveGridPlacement(
                    destReader.mContext,
                    screenId,
                    trgX,
                    trgY,
                    itemsToPlace.mRemainingItemsToPlace,
                    destReader.mWorkspaceEntriesByScreenId[screenId],
                )
            placeItems(itemsToPlace, helper, srcReader, destReader, idsInUse)
            screenId++
        }
    }

    private fun placeItems(
        itemsToPlace: WorkspaceItemsToPlace,
        helper: DatabaseHelper,
        srcReader: DbReader,
        destReader: DbReader,
        idsInUse: List<Int>,
    ) {
        while (itemsToPlace.mPlacementSolution.isNotEmpty()) {
            GridSizeMigrationDBController.insertEntryInDb(
                helper,
                itemsToPlace.mPlacementSolution.removeAt(0),
                srcReader.mTableName,
                destReader.mTableName,
                idsInUse,
            )
        }
    }

    /** Only migrate the grid in this manner if the target grid is taller and not wider. */
    private fun shouldMigrateToStrictlyTallerGrid(
        isDestNewDb: Boolean,
        srcDeviceState: DeviceGridState,
        destDeviceState: DeviceGridState,
    ): Boolean {
        return (Flags.oneGridSpecs() || isDestNewDb) &&
            srcDeviceState.columns == destDeviceState.columns &&
            srcDeviceState.rows < destDeviceState.rows
    }

    /**
     * Finds all the items that are in the old grid which aren't in the new grid, meaning they need
     * to be added to the new grid.
     *
     * @return a list of DbEntry's which we need to add.
     */
    private fun getItemsToBeAdded(src: List<DbEntry>, dest: List<DbEntry>): MutableList<DbEntry> {
        val entryCountDiff = calcDiff(src, dest)
        val toBeAdded: MutableList<DbEntry> = ArrayList()
        src.forEach { entry ->
            entryCountDiff[entry]?.let { entryDiff ->
                if (entryDiff > 0) {
                    toBeAdded.add(entry)
                    entryCountDiff[entry] = entryDiff - 1
                }
            }
        }
        return toBeAdded
    }

    /**
     * Finds all the items that are in the new grid which aren't in the old grid, meaning they need
     * to be removed from the new grid.
     *
     * @return an IntArray of item id's which we need to remove.
     */
    private fun getItemsToBeRemoved(src: List<DbEntry>, dest: List<DbEntry>): IntArray {
        val entryCountDiff = calcDiff(src, dest)
        val toBeRemoved =
            IntArray().apply {
                dest.forEach { entry ->
                    entryCountDiff[entry]?.let { entryDiff ->
                        if (entryDiff < 0) {
                            add(entry.id)
                            if (entry.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
                                entry.mFolderItems.values.forEach { ids ->
                                    ids.forEach { value -> add(value) }
                                }
                            }
                        }
                        entryCountDiff[entry] = entryDiff.plus(1)
                    }
                }
            }
        return toBeRemoved
    }

    /**
     * Calculates the difference between the old and new grid items in terms of how many of each
     * item there are. E.g. if the old grid had 2 Calculator icons but the new grid has 0, then the
     * difference there would be 2. While if the old grid has 0 Calculator icons and the new grid
     * has 1, then the difference would be -1.
     *
     * @return a Map with each DbEntry as a key and the count of said entry as the value.
     */
    private fun calcDiff(src: List<DbEntry>, dest: List<DbEntry>): MutableMap<DbEntry, Int> {
        val entryCountDiff: MutableMap<DbEntry, Int> = HashMap()
        src.forEach { entry -> entryCountDiff[entry] = entryCountDiff.getOrDefault(entry, 0) + 1 }
        dest.forEach { entry -> entryCountDiff[entry] = entryCountDiff.getOrDefault(entry, 0) - 1 }
        return entryCountDiff
    }

    private fun solveHotseatPlacement(
        hotseatSize: Int,
        placedHotseatItems: List<DbEntry>,
        itemsToPlace: List<DbEntry>,
    ): List<DbEntry> {
        val placementSolution: MutableList<DbEntry> = ArrayList()
        val remainingItemsToPlace: MutableList<DbEntry> = ArrayList(itemsToPlace)
        val occupied = BooleanArray(hotseatSize)
        for (entry in placedHotseatItems) {
            occupied[entry.screenId] = true
        }

        for (i in occupied.indices) {
            if (!occupied[i] && remainingItemsToPlace.isNotEmpty()) {
                val entry: DbEntry =
                    remainingItemsToPlace.removeAt(0).apply {
                        screenId = i
                        // These values does not affect the item position, but we should set them
                        // to something other than -1.
                        cellX = i
                        cellY = 0
                    }
                placementSolution.add(entry)
                occupied[entry.screenId] = true
            }
        }
        return placementSolution
    }

    private fun solveGridPlacement(
        context: Context,
        screenId: Int,
        trgX: Int,
        trgY: Int,
        sortedItemsToPlace: MutableList<DbEntry>,
        existedEntries: MutableList<DbEntry>?,
    ): WorkspaceItemsToPlace {
        val itemsToPlace = WorkspaceItemsToPlace(sortedItemsToPlace, mutableListOf())
        val occupied = GridOccupancy(trgX, trgY)
        val trg = Point(trgX, trgY)

        val prefs2 = PreferenceManager2.INSTANCE.get(context)

        val next: Point =
            if (screenId == 0 && prefs2.enableSmartspace.firstBlocking()) {
                Point(0, 1 /* smartspace */)
            } else {
                Point(0, 0)
            }
        if (existedEntries != null) {
            for (entry in existedEntries) {
                occupied.markCells(entry, true)
            }
        }
        val iterator = itemsToPlace.mRemainingItemsToPlace.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.minSpanX > trgX || entry.minSpanY > trgY) {
                iterator.remove()
                continue
            }
            findPlacementForEntry(entry, next.x, next.y, trg, occupied)?.let {
                entry.screenId = screenId
                entry.cellX = it.cellX
                entry.cellY = it.cellY
                entry.spanX = it.spanX
                entry.spanY = it.spanY
                occupied.markCells(entry, true)
                next[entry.cellX + entry.spanX] = entry.cellY
                itemsToPlace.mPlacementSolution.add(entry)
                iterator.remove()
            }
        }
        return itemsToPlace
    }

    /**
     * Search for the next possible placement of an item. (mNextStartX, mNextStartY) serves as a
     * memoization of last placement, we can start our search for next placement from there to speed
     * up the search.
     *
     * @return NewEntryPlacement object if we found a valid placement, null if we didn't.
     */
    private fun findPlacementForEntry(
        entry: DbEntry,
        startPosX: Int,
        startPosY: Int,
        trg: Point,
        occupied: GridOccupancy,
    ): CellAndSpan? {
        var newStartPosX = startPosX
        for (y in startPosY until trg.y) {
            for (x in newStartPosX until trg.x) {
                if (occupied.isRegionVacant(x, y, entry.minSpanX, entry.minSpanY)) {
                    return (CellAndSpan(x, y, entry.minSpanX, entry.minSpanY))
                }
            }
            newStartPosX = 0
        }
        return null
    }

    private data class WorkspaceItemsToPlace(
        val mRemainingItemsToPlace: MutableList<DbEntry>,
        val mPlacementSolution: MutableList<DbEntry>,
    )

    companion object {
        private const val TAG = "GridSizeMigrationLogic"
        private const val DEBUG = true
    }
}
