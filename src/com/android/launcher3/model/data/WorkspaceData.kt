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

package com.android.launcher3.model.data

import android.content.Context
import android.util.SparseArray
import androidx.annotation.VisibleForTesting
import androidx.core.util.putAll
import androidx.core.util.valueIterator
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.BuildConfig
import com.android.launcher3.BuildConfigs
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.Workspace
import com.android.launcher3.model.data.WorkspaceChangeEvent.AddEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.RemoveEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.UpdateEvent
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.IntSet
import com.android.launcher3.util.ItemInfoMatcher
import com.patrykmichalik.opto.core.firstBlocking
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import java.util.stream.StreamSupport

/**
 * An immutable representation of all the workspace items (shortcuts, folders, widgets and predicted
 * items)
 */
sealed class WorkspaceData : Iterable<ItemInfo> {

    /** Creates an array of valid workspace screens based on current items in the model. */
    // LC-Note: Add context to replace QSB_ON_FIRST_SCREEN config
    fun collectWorkspaceScreens(context: Context): IntArray {
        val prefs2 = PreferenceManager2.INSTANCE.get(context)
        val smartspaceEnabled = prefs2.enableSmartspace.firstBlocking()

        val screenSet = IntSet()
        forEach { if (it.container == CONTAINER_DESKTOP) screenSet.add(it.screenId) }
        if (smartspaceEnabled || screenSet.isEmpty) {
            screenSet.add(Workspace.FIRST_SCREEN_ID)
        }
        return screenSet.array
    }

    /** Returns the [ItemInfo] associated with the [id] or null */
    abstract operator fun get(id: Int): ItemInfo?

    fun stream(): Stream<ItemInfo> = StreamSupport.stream(spliterator(), false)

    /** Version determines the uniqueness per model load cycle */
    abstract val version: Int
    /** Number of times, this data has been modified */
    internal abstract val modificationId: Int
    /** Previous modifications to this data in reverse order: 1st entry is the latest update */
    protected abstract val changeHistory: List<WorkspaceChangeEvent>

    /**
     * Returns the predicted items for the provided [containerId] or an empty list id no such
     * container exists
     */
    fun getPredictedContents(containerId: Int): List<ItemInfo> =
        get(containerId).let { if (it is PredictedContainerInfo) it.getContents() else null }
            ?: emptyList()

    /** Returns an immutable copy of the dataset */
    abstract fun copy(): WorkspaceData

    /**
     * Returns a list of [WorkspaceChangeEvent] to apply for reaching from [source] to current
     * state. Returns an empty list of the source is same as the current state and null if such a
     * transition is not possible.
     */
    fun diff(source: WorkspaceData): List<WorkspaceChangeEvent>? {
        if (version != source.version) return null
        val requiredHistorySize = modificationId - source.modificationId

        val changes = changeHistory
        return when {
            requiredHistorySize < 0 -> null
            requiredHistorySize > changes.size -> null
            requiredHistorySize == 0 -> emptyList()
            else -> changes.subList(0, requiredHistorySize).reversed()
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is WorkspaceData &&
            other.version == version &&
            other.modificationId == modificationId
    }

    /** A mutable implementation of [WorkspaceData] */
    class MutableWorkspaceData : WorkspaceData() {

        private val itemsIdMap = SparseArray<ItemInfo>()

        override var version: Int = VERSION_COUNTER.incrementAndGet()

        override var modificationId: Int = 0

        override val changeHistory = mutableListOf<WorkspaceChangeEvent>()

        override fun iterator() = itemsIdMap.valueIterator()

        override fun get(id: Int): ItemInfo? = itemsIdMap.get(id)

        /** Replaces the existing dataset with [items] */
        fun replaceDataMap(items: SparseArray<ItemInfo>) {
            itemsIdMap.clear()
            itemsIdMap.putAll(items)
            version = VERSION_COUNTER.incrementAndGet()
            modificationId = 0
            changeHistory.clear()
        }

        /** Adds the [item] to the dataset */
        fun addItems(items: List<ItemInfo>, owner: Any?) {
            items.forEach { itemsIdMap[it.id] = it }
            pushUpdate(AddEvent(items, owner))
        }

        /** Removes existing [items] from the dataset */
        fun removeItems(items: Collection<ItemInfo>, owner: Any?) {
            items.forEach { itemsIdMap.remove(it.id) }
            pushUpdate(RemoveEvent(ItemInfoMatcher.ofItems(items), owner))
        }

        /** Replaces an existing [item] from the dataset */
        fun replaceItem(item: ItemInfo, owner: Any?) {
            itemsIdMap[item.id] = item
            notifyItemsUpdated(listOf(item), owner)
        }

        /**
         * Notifies this dataset that or updates already performed on existing [items]. Since the
         * underlying [ItemInfo]s are mutable objects, its possible to update their properties
         * without going though this dataset
         */
        fun notifyItemsUpdated(items: List<ItemInfo>, owner: Any?) {
            pushUpdate(UpdateEvent(items, owner))
        }

        private fun pushUpdate(update: WorkspaceChangeEvent) {
            modificationId++
            changeHistory.add(0, update)
            if (changeHistory.size > MAX_HISTORY_SIZE) changeHistory.removeAt(changeHistory.lastIndex)
        }

        override fun copy(): WorkspaceData =
            ImmutableWorkspaceData(version, modificationId, changeHistory.toList(), itemsIdMap)
    }

    /** An immutable implementation of [WorkspaceData] */
    class ImmutableWorkspaceData(
        override val version: Int,
        override val modificationId: Int,
        override val changeHistory: List<WorkspaceChangeEvent>,
        items: SparseArray<ItemInfo>,
    ) : WorkspaceData() {

        private val itemsIdMap = items.clone()

        override fun iterator() = itemsIdMap.valueIterator()

        override fun get(id: Int): ItemInfo? = itemsIdMap.get(id)

        override fun copy(): WorkspaceData = this
    }

    companion object {
        // Maximum number of historic change events to keep in memory
        @VisibleForTesting const val MAX_HISTORY_SIZE = 4

        private val VERSION_COUNTER = AtomicInteger()
    }
}
