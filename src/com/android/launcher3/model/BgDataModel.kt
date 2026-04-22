/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.util.Log
import android.util.SparseArray
import androidx.annotation.AnyThread
import com.android.launcher3.BuildConfig
import com.android.launcher3.BuildConfigs
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.logging.DumpManager
import com.android.launcher3.logging.DumpManager.LauncherDumpable
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.PredictedContainerInfo
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.data.WorkspaceData.MutableWorkspaceData
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.repository.HomeScreenRepository
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors
import com.android.launcher3.util.IntSparseArrayMap
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.widget.model.WidgetsListBaseEntry
import java.io.PrintWriter
import java.util.Collections
import java.util.function.Predicate
import javax.inject.Inject
import javax.inject.Provider

/**
 * All the data stored in-memory and managed by the LauncherModel
 *
 * All the static data should be accessed on the background thread, A lock should be acquired on
 * this object when accessing any data from this model.
 */
@LauncherAppSingleton
class BgDataModel
@Inject
constructor(
    /** Entire list of widgets. */
    @JvmField val widgetsModel: WidgetsModel,
    homeDataProvider: Provider<HomeScreenRepository?>,
    dumpManager: DumpManager,
    lifeCycle: DaggerSingletonTracker,
) : LauncherDumpable {

    private val mutableWorkspaceData = MutableWorkspaceData()

    /**
     * Map of all the ItemInfos (shortcuts, folders, widgets and predicted items) created by
     * LauncherModel to their ids
     */
    @JvmField val itemsIdMap: WorkspaceData = mutableWorkspaceData

    /** Extra container based items */
    @Deprecated("Use independent repository for each extra item")
    @JvmField
    val extraItems = IntSparseArrayMap<FixedContainerItems>()

    /** Maps all launcher activities to counts of their shortcuts. */
    @JvmField val deepShortcutMap = HashMap<ComponentKey, Int>()

    /** Cache for strings used in launcher */
    @JvmField val stringCache = StringCache()

    private val repo = if (Flags.modelRepository()) homeDataProvider.get() else null

    /** Id when the model was last bound */
    @JvmField var lastBindId: Int = 0

    /** Load id for which the callbacks were successfully bound */
    @JvmField var lastLoadId: Int = -1

    init {
        lifeCycle.addCloseable(dumpManager.register(this))
    }

    /** Clears all the data */
    @Synchronized
    fun clear() {
        deepShortcutMap.clear()
        extraItems.clear()
    }

    @Synchronized
    override fun dump(prefix: String, writer: PrintWriter, args: Array<String>?) {
        writer.println(prefix + "Data Model:")
        writer.println("$prefix ---- items id map ")
        itemsIdMap.forEach { writer.println("$prefix\t$it") }
        writer.println("$prefix ---- extra items ")
        extraItems.forEach { writer.println("$prefix\t$it") }
        if (args?.getOrNull(0) == "--all")
            writer.println(prefix + "shortcut counts: ${deepShortcutMap.values.joinToString()}")
    }

    @Synchronized
    fun removeItem(context: Context, vararg items: ItemInfo) {
        removeItem(context, listOf(*items))
    }

    @Synchronized
    @JvmOverloads
    fun removeItem(context: Context, items: Collection<ItemInfo>, owner: Any? = null) {
        if (BuildConfigs.IS_STUDIO_BUILD) {
            items
                .asSequence()
                .filter { it.itemType == ITEM_TYPE_FOLDER || it.itemType == ITEM_TYPE_APP_PAIR }
                .forEach { item: ItemInfo ->

                    // We are deleting a collection which still contains items that think they are
                    // contained by that collection.
                    itemsIdMap
                        .filter { it.container == item.id && !items.contains(it) }
                        .forEach { info: ItemInfo ->
                            Log.e(
                                TAG,
                                "deleting a collection ($item) which still contains item ($info)",
                            )
                        }
                }
        }

        mutableWorkspaceData.removeItems(items, owner)
        items
            .asSequence()
            .map { it.user }
            .distinct()
            .forEach { updateShortcutPinnedState(context, it) }

        updateHomeRepository()
    }

    private fun updateHomeRepository() {
        if (Flags.modelRepository() && repo != null) {
            repo.dispatchChange(mutableWorkspaceData.copy())
        }
    }

    @JvmOverloads
    fun addItem(context: Context, item: ItemInfo, owner: Any? = null) {
        addItems(context, listOf(item), owner)
    }

    @Synchronized
    @JvmOverloads
    fun addItems(context: Context, items: List<ItemInfo>, owner: Any? = null) {
        mutableWorkspaceData.addItems(items, owner)
        items
            .filter { it.itemType == ITEM_TYPE_DEEP_SHORTCUT }
            .map { it.user }
            .distinct()
            .forEach { updateShortcutPinnedState(context, it) }
        updateHomeRepository()
    }

    @Synchronized
    fun updateAndDispatchItem(item: ItemInfo, owner: Any?) {
        mutableWorkspaceData.replaceItem(item, owner)
        updateHomeRepository()
    }

    @Synchronized
    fun updateItems(items: List<ItemInfo>, owner: Any?) {
        mutableWorkspaceData.notifyItemsUpdated(items, owner)
        updateHomeRepository()
    }

    @Synchronized
    fun dataLoadComplete(allItems: SparseArray<ItemInfo>) {
        mutableWorkspaceData.replaceDataMap(allItems)
        updateHomeRepository()
    }

    /**
     * Updates the deep shortcuts state in system to match out internal model, pinning any missing
     * shortcuts and unpinning any extra shortcuts.
     */
    fun updateShortcutPinnedState(context: Context) {
        for (user in UserCache.INSTANCE[context].userProfiles) {
            updateShortcutPinnedState(context, user)
        }
    }

    /**
     * Updates the deep shortcuts state in system to match out internal model, pinning any missing
     * shortcuts and unpinning any extra shortcuts.
     */
    @Synchronized
    fun updateShortcutPinnedState(context: Context, user: UserHandle) {
        if (!BuildConfigs.WIDGETS_ENABLED) {
            return
        }

        // Collect all system shortcuts
        val result =
            ShortcutRequest(context, user)
                .query(ShortcutRequest.PINNED or ShortcutQuery.FLAG_GET_KEY_FIELDS_ONLY)
        if (!result.wasSuccess()) {
            return
        }

        // Map of packageName to shortcutIds that are currently in the system
        val systemMap: MutableMap<String, MutableSet<String>> =
            result
                .groupingBy { it.`package` }
                .foldTo(
                    destination = mutableMapOf(),
                    initialValueSelector = { _, element -> mutableSetOf(element.id) },
                    operation = { _, accumulator, element -> accumulator.apply { add(element.id) } },
                )

        // Collect all model shortcuts
        val allWorkspaceItems =
            mutableListOf<ShortcutKey>().apply {
                updateAndCollectWorkspaceItemInfos(
                    user,
                    {
                        if (it.itemType == ITEM_TYPE_DEEP_SHORTCUT)
                            add(ShortcutKey.fromItemInfo(it))
                        // We don't care about the returned list
                        false
                    },
                )
            }
        allWorkspaceItems.addAll(
            ItemInstallQueue.INSTANCE[context].getPendingShortcuts(user).toList()
        )
        // Map of packageName to shortcutIds that are currently in our model
        val modelMap =
            allWorkspaceItems
                .groupingBy { it.packageName }
                .foldTo(
                    destination = mutableMapOf(),
                    initialValueSelector = { _, element -> mutableSetOf(element.id) },
                    operation = { _, accumulator, element -> accumulator.apply { add(element.id) } },
                )

        // Check for diff
        for ((key, modelShortcuts) in modelMap) {
            val systemShortcuts = systemMap.remove(key) ?: emptySet()

            // Do not use .equals as it can vary based on the type of set
            if (
                systemShortcuts.size != modelShortcuts.size ||
                    !systemShortcuts.containsAll(modelShortcuts)
            ) {
                // Update system state for this package
                try {
                    FileLog.d(
                        TAG,
                        ("updateShortcutPinnedState:" + " Pinning Shortcuts: $key: $modelShortcuts"),
                    )
                    context
                        .getSystemService(LauncherApps::class.java)
                        ?.pinShortcuts(key, ArrayList(modelShortcuts), user)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Failed to pin shortcut", e)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Failed to pin shortcut", e)
                }
            }
        }

        // If there are any extra pinned shortcuts, remove them
        systemMap.keys.forEach { packageName: String ->
            // Update system state
            try {
                FileLog.d(
                    TAG,
                    ("updateShortcutPinnedState:" +
                        " Unpinning extra Shortcuts for package: $packageName: ${systemMap[packageName]}"),
                )
                context
                    .getSystemService(LauncherApps::class.java)
                    ?.pinShortcuts(packageName, emptyList(), user)
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to unpin shortcut", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Failed to unpin shortcut", e)
            }
        }
    }

    /**
     * Clear all the deep shortcut counts for the given package, and re-add the new shortcut counts.
     */
    @Synchronized
    fun updateDeepShortcutCounts(
        packageName: String?,
        user: UserHandle,
        shortcuts: List<ShortcutInfo>,
    ) {
        if (packageName != null) {
            deepShortcutMap.keys.removeAll {
                it.componentName.packageName == packageName && it.user == user
            }
        }

        // Now add the new shortcuts to the map.
        deepShortcutMap +=
            shortcuts
                .asSequence()
                .filter {
                    it.isEnabled && (it.isDeclaredInManifest || it.isDynamic) && it.activity != null
                }
                .groupingBy { ComponentKey(it.activity, it.userHandle) }
                .eachCount()
    }

    /**
     * Calls the [workspaceItemOp] for all workspaceItems [widgetItemOp] for all widget items in the
     * in-memory model (both persisted items and dynamic/predicted items for the [userHandle]) and
     * returns a list of updates to be dispatched to the callbacks. Note the call is not
     * synchronized over the model, that should be handled by the caller.
     */
    @JvmOverloads
    fun updateAndCollectWorkspaceItemInfos(
        userHandle: UserHandle,
        workspaceItemOp: (WorkspaceItemInfo) -> Boolean,
        widgetItemOp: ((LauncherAppWidgetInfo) -> Boolean)? = null,
    ): List<ItemInfo> =
        itemsIdMap
            .filter {
                when {
                    it is WorkspaceItemInfo && userHandle == it.user -> workspaceItemOp.invoke(it)
                    widgetItemOp != null && it is LauncherAppWidgetInfo && userHandle == it.user ->
                        widgetItemOp.invoke(it)
                    it is PredictedContainerInfo -> {
                        // Do not use filter or any as we want to run the update on every item. If
                        // any
                        // single item was updated, we add the container to the list of updates
                        it.getContents().count { predictedItem ->
                            predictedItem is WorkspaceItemInfo &&
                                predictedItem.user == userHandle &&
                                workspaceItemOp.invoke(predictedItem)
                        } > 0
                    }
                    else -> false
                }
            }
            .apply {
                // Dispatch an update
                if (isNotEmpty()) updateItems(this, null)
            }

    /** An object containing items corresponding to a fixed container */
    class FixedContainerItems(@JvmField val containerId: Int, items: List<ItemInfo>) {

        @JvmField val items: List<ItemInfo> = Collections.unmodifiableList(items)

        override fun toString() =
            "FixedContainerItems: id=$containerId itemCount=${items.size}, [${items.joinToString()}"
    }

    interface Callbacks {
        /**
         * Does a complete model rebind. The callback can be called on any thread and it is up to
         * the client to move the executor to appropriate thread
         */
        @AnyThread
        fun bindCompleteModelAsync(itemIdMap: WorkspaceData, isBindingSync: Boolean) {
            Executors.MAIN_EXECUTOR.execute { bindCompleteModel(itemIdMap, isBindingSync) }
        }

        fun bindCompleteModel(itemIdMap: WorkspaceData, isBindingSync: Boolean) {}

        fun bindItemsAdded(items: List<@JvmSuppressWildcards ItemInfo>) {}

        /** Called when a runtime property of the ItemInfo is updated due to some system event */
        fun bindItemsUpdated(updates: Set<@JvmSuppressWildcards ItemInfo>) {}

        fun bindWorkspaceComponentsRemoved(matcher: Predicate<ItemInfo?>) {}

        /** Binds updated incremental download progress */
        fun bindIncrementalDownloadProgressUpdated(app: AppInfo) {}

        /** Binds the app widgets to the providers that share widgets with the UI. */
        fun bindAllWidgets(widgets: List<@JvmSuppressWildcards WidgetsListBaseEntry>) {}

        fun bindDeepShortcutMap(deepShortcutMap: HashMap<ComponentKey, Int>) {}

        /** Binds extra item provided any external source */
        fun bindExtraContainerItems(item: FixedContainerItems) {}

        fun bindAllApplications(
            apps: Array<AppInfo>,
            flags: Int,
            packageUserKeytoUidMap: Map<PackageUserKey, Int>,
        ) {}

        /** Binds the cache of string resources */
        fun bindStringCache(cache: StringCache) {}
    }

    companion object {
        private const val TAG = "BgDataModel"
    }
}
