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

import android.content.pm.PackageInstaller.SessionInfo
import android.os.Process
import android.provider.Settings
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PackageUserKey
import javax.inject.Inject

/**
 * Helper class to send broadcasts to package installers that have:
 * - Pending Items on first screen
 * - Installed/Archived Items on first screen
 * - Installed/Archived Widgets on every screen
 *
 * The packages are broken down by: folder items, workspace items, hotseat items, and widgets.
 * Package installers only receive data for items that they are installing or have installed.
 */
class FirstScreenBroadcastHelper
@Inject
constructor(private val packageManagerHelper: PackageManagerHelper) {

    /**
     * Return list of [FirstScreenBroadcastModel] for each installer and their
     * installing/installed/archived items. If the FirstScreenBroadcastModel data is greater in size
     * than [MAX_BROADCAST_SIZE], then we will truncate the data until it meets the size limit to
     * avoid overloading the broadcast.
     *
     * @param firstScreenItems every ItemInfo on first screen
     * @param userKeyToSessionMap map of pending SessionInfo's for installing items
     * @param allWidgets list of all Widgets added to every screen
     */
    @WorkerThread
    fun createModelsForFirstScreenBroadcast(
        firstScreenItems: List<ItemInfo>,
        userKeyToSessionMap: Map<PackageUserKey, SessionInfo>,
        allWidgets: List<ItemInfo>,
        shouldAttachArchivingExtras: Boolean,
    ): List<FirstScreenBroadcastModel> {

        // installers for installing items
        val pendingItemInstallerMap: Map<String, Set<String>> =
            createPendingItemsMap(userKeyToSessionMap)

        val installingPackages = pendingItemInstallerMap.values.flatten().toSet()

        // Map of packageName to its installer packageName
        val installerAppCache = mutableMapOf<String, String?>()
        // installers for installed items on first screen
        val installedItemInstallerMap: Map<String, List<ItemInfo>> =
            createInstalledItemsMap(firstScreenItems, installingPackages, installerAppCache)

        // installers for widgets on all screens
        val allInstalledWidgetsMap: Map<String, List<ItemInfo>> =
            createInstalledItemsMap(allWidgets, installingPackages, installerAppCache)

        val allInstallers: Set<String> =
            pendingItemInstallerMap.keys +
                installedItemInstallerMap.keys +
                allInstalledWidgetsMap.keys

        // create broadcast for each installer, with extras for each item category
        return allInstallers.map { installer ->
            FirstScreenBroadcastModel(
                    installerPackage = installer,
                    shouldAttachArchivingExtras = shouldAttachArchivingExtras,
                )
                .apply {
                    addPendingItems(pendingItemInstallerMap[installer], firstScreenItems)

                    if (shouldAttachArchivingExtras) {
                        addInstalledItems(installer, installedItemInstallerMap)
                        addAllScreenWidgets(installer, allInstalledWidgetsMap)
                    }
                    truncateModelForBroadcast()
                }
        }
    }

    /** Maps Installer packages to Set of app packages from install sessions */
    private fun createPendingItemsMap(
        userKeyToSessionMap: Map<PackageUserKey, SessionInfo>
    ): Map<String, Set<String>> {
        val myUser = Process.myUserHandle()
        return userKeyToSessionMap.values
            .filter { it.user == myUser && !it.installerPackageName.isNullOrEmpty() }
            .groupBy(
                keySelector = { it.installerPackageName },
                valueTransform = { it.appPackageName },
            )
            .mapValues { it.value.filterNotNull().toSet() } as Map<String, Set<String>>
    }

    /** Maps Installer packages to Set of ItemInfos. Filter out installing packages. */
    private fun createInstalledItemsMap(
        allItems: Iterable<ItemInfo>,
        installingPackages: Set<String>,
        installerAppCache: MutableMap<String, String?>,
    ): Map<String, List<ItemInfo>> =
        allItems
            .sortedBy { it.screenId }
            .groupByTo(mutableMapOf()) {
                getPackageName(it)?.let { pkg ->
                    if (installingPackages.contains(pkg)) {
                        null
                    } else {
                        installerAppCache.getOrPut(pkg) {
                            packageManagerHelper.getAppInstallerPackage(pkg)
                        }
                    }
                }
            }
            .apply { remove(null) } as Map<String, List<ItemInfo>>

    /**
     * Add first screen Pending Items from Map to [FirstScreenBroadcastModel] for given installer
     */
    private fun FirstScreenBroadcastModel.addPendingItems(
        installingItems: Set<String>?,
        firstScreenItems: List<ItemInfo>,
    ) {
        if (installingItems == null) return
        for (info in firstScreenItems) {
            addCollectionItems(info, installingItems)
            val packageName = getPackageName(info) ?: continue
            if (!installingItems.contains(packageName)) continue
            when {
                info is LauncherAppWidgetInfo -> pendingWidgetItems.add(packageName)
                info.container == CONTAINER_HOTSEAT -> pendingHotseatItems.add(packageName)
                info.container == CONTAINER_DESKTOP -> pendingWorkspaceItems.add(packageName)
            }
        }
    }

    /**
     * Add first screen installed Items from Map to [FirstScreenBroadcastModel] for given installer
     */
    private fun FirstScreenBroadcastModel.addInstalledItems(
        installer: String,
        installedItemInstallerMap: Map<String, List<ItemInfo>>,
    ) {
        installedItemInstallerMap[installer]?.forEach { info ->
            val packageName: String = getPackageName(info) ?: return@forEach
            when (info.container) {
                CONTAINER_HOTSEAT -> installedHotseatItems.add(packageName)
                CONTAINER_DESKTOP -> installedWorkspaceItems.add(packageName)
            }
        }
    }

    /** Add Widgets on every screen from Map to [FirstScreenBroadcastModel] for given installer */
    private fun FirstScreenBroadcastModel.addAllScreenWidgets(
        installer: String,
        allInstalledWidgetsMap: Map<String, List<ItemInfo>>,
    ) {
        allInstalledWidgetsMap[installer]?.forEach { widget ->
            val packageName: String = getPackageName(widget) ?: return@forEach
            if (widget.screenId == 0) {
                installedWidgets.add(packageName)
            } else {
                installedWidgets.addLast(packageName)
            }
        }
    }

    private fun FirstScreenBroadcastModel.addCollectionItems(
        info: ItemInfo,
        installingPackages: Set<String>,
    ) {
        if (info !is CollectionInfo) return
        pendingCollectionItems.addAll(
            cloneOnMainThread(info.getAppContents())
                .mapNotNull { getPackageName(it) }
                .filter { installingPackages.contains(it) }
        )
    }

    private fun getPackageName(info: ItemInfo): String? = info.targetComponent?.packageName

    /**
     * Clone the provided list on UI thread. This is used for [FolderInfo.getContents] which is
     * always modified on UI thread.
     */
    @AnyThread
    private fun cloneOnMainThread(list: List<WorkspaceItemInfo>): List<WorkspaceItemInfo> {
        return try {
            return Executors.MAIN_EXECUTOR.submit<ArrayList<WorkspaceItemInfo>> { ArrayList(list) }
                .get()
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        @VisibleForTesting const val MAX_BROADCAST_SIZE = 70

        /**
         * Creates a copy of [FirstScreenBroadcastModel] with items truncated to meet
         * [MAX_BROADCAST_SIZE] in a prioritized order.
         */
        @VisibleForTesting
        fun FirstScreenBroadcastModel.truncateModelForBroadcast() {
            val totalItemCount = getTotalItemCount()
            if (totalItemCount <= MAX_BROADCAST_SIZE) return
            var extraItemCount = totalItemCount - MAX_BROADCAST_SIZE

            while (extraItemCount > 0) {
                // In this order, remove items until we meet the max size limit.
                when {
                    pendingCollectionItems.isNotEmpty() -> pendingCollectionItems.removeLast()
                    pendingHotseatItems.isNotEmpty() -> pendingHotseatItems.removeLast()
                    installedHotseatItems.isNotEmpty() -> installedHotseatItems.removeLast()
                    installedWidgets.isNotEmpty() -> installedWidgets.removeLast()
                    pendingWidgetItems.isNotEmpty() -> pendingWidgetItems.removeLast()
                    pendingWorkspaceItems.isNotEmpty() -> pendingWorkspaceItems.removeLast()
                    installedWorkspaceItems.isNotEmpty() -> installedWorkspaceItems.removeLast()
                }
                extraItemCount--
            }
        }

        private fun MutableSet<String>.removeLast() = remove(last())

        @JvmField
        val DISABLE_INSTALLED_APPS_BROADCAST =
            Settings.Secure.getUriFor("disable_launcher_broadcast_installed_apps")
    }
}
