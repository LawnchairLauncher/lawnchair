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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller.SessionInfo
import android.os.Process
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.InstallSessionHelper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PackageUserKey

/**
 * Helper class to send broadcasts to package installers that have:
 * - Pending Items on first screen
 * - Installed/Archived Items on first screen
 * - Installed/Archived Widgets on every screen
 *
 * The packages are broken down by: folder items, workspace items, hotseat items, and widgets.
 * Package installers only receive data for items that they are installing or have installed.
 */
object FirstScreenBroadcastHelper {
    @VisibleForTesting const val MAX_BROADCAST_SIZE = 70

    private const val TAG = "FirstScreenBroadcastHelper"
    private const val DEBUG = true
    private const val ACTION_FIRST_SCREEN_ACTIVE_INSTALLS =
        "com.android.launcher3.action.FIRST_SCREEN_ACTIVE_INSTALLS"
    // String retained as "folderItem" for back-compatibility reasons.
    private const val PENDING_COLLECTION_ITEM_EXTRA = "folderItem"
    private const val PENDING_WORKSPACE_ITEM_EXTRA = "workspaceItem"
    private const val PENDING_HOTSEAT_ITEM_EXTRA = "hotseatItem"
    private const val PENDING_WIDGET_ITEM_EXTRA = "widgetItem"
    // Extras containing all installed items, including Archived Apps.
    private const val INSTALLED_WORKSPACE_ITEMS_EXTRA = "workspaceInstalledItems"
    private const val INSTALLED_HOTSEAT_ITEMS_EXTRA = "hotseatInstalledItems"
    // This includes installed widgets on all screens, not just first.
    private const val ALL_INSTALLED_WIDGETS_ITEM_EXTRA = "widgetInstalledItems"
    private const val VERIFICATION_TOKEN_EXTRA = "verificationToken"

    /**
     * Return list of [FirstScreenBroadcastModel] for each installer and their
     * installing/installed/archived items. If the FirstScreenBroadcastModel data is greater in size
     * than [MAX_BROADCAST_SIZE], then we will truncate the data until it meets the size limit to
     * avoid overloading the broadcast.
     *
     * @param packageManagerHelper helper for querying PackageManager
     * @param firstScreenItems every ItemInfo on first screen
     * @param userKeyToSessionMap map of pending SessionInfo's for installing items
     * @param allWidgets list of all Widgets added to every screen
     */
    @WorkerThread
    @JvmStatic
    fun createModelsForFirstScreenBroadcast(
        packageManagerHelper: PackageManagerHelper,
        firstScreenItems: List<ItemInfo>,
        userKeyToSessionMap: Map<PackageUserKey, SessionInfo>,
        allWidgets: List<LauncherAppWidgetInfo>
    ): List<FirstScreenBroadcastModel> {

        // installers for installing items
        val pendingItemInstallerMap: Map<String, MutableSet<String>> =
            createPendingItemsMap(userKeyToSessionMap)
        val installingPackages = pendingItemInstallerMap.values.flatten().toSet()

        // installers for installed items on first screen
        val installedItemInstallerMap: Map<String, MutableSet<ItemInfo>> =
            createInstalledItemsMap(firstScreenItems, installingPackages, packageManagerHelper)

        // installers for widgets on all screens
        val allInstalledWidgetsMap: Map<String, MutableSet<LauncherAppWidgetInfo>> =
            createAllInstalledWidgetsMap(allWidgets, installingPackages, packageManagerHelper)

        val allInstallers: Set<String> =
            pendingItemInstallerMap.keys +
                installedItemInstallerMap.keys +
                allInstalledWidgetsMap.keys
        val models = mutableListOf<FirstScreenBroadcastModel>()
        // create broadcast for each installer, with extras for each item category
        allInstallers.forEach { installer ->
            val installingItems = pendingItemInstallerMap[installer]
            val broadcastModel =
                FirstScreenBroadcastModel(installerPackage = installer).apply {
                    addPendingItems(installingItems, firstScreenItems)
                    addInstalledItems(installer, installedItemInstallerMap)
                    addAllScreenWidgets(installer, allInstalledWidgetsMap)
                }
            broadcastModel.truncateModelForBroadcast()
            models.add(broadcastModel)
        }
        return models
    }

    /** From the model data, create Intents to send broadcasts and fire them. */
    @WorkerThread
    @JvmStatic
    fun sendBroadcastsForModels(context: Context, models: List<FirstScreenBroadcastModel>) {
        for (model in models) {
            model.printDebugInfo()
            val intent =
                Intent(ACTION_FIRST_SCREEN_ACTIVE_INSTALLS)
                    .setPackage(model.installerPackage)
                    .putExtra(
                        VERIFICATION_TOKEN_EXTRA,
                        PendingIntent.getActivity(
                            context,
                            0 /* requestCode */,
                            Intent(),
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    .putStringArrayListExtra(
                        PENDING_COLLECTION_ITEM_EXTRA,
                        ArrayList(model.pendingCollectionItems)
                    )
                    .putStringArrayListExtra(
                        PENDING_WORKSPACE_ITEM_EXTRA,
                        ArrayList(model.pendingWorkspaceItems)
                    )
                    .putStringArrayListExtra(
                        PENDING_HOTSEAT_ITEM_EXTRA,
                        ArrayList(model.pendingHotseatItems)
                    )
                    .putStringArrayListExtra(
                        PENDING_WIDGET_ITEM_EXTRA,
                        ArrayList(model.pendingWidgetItems)
                    )
                    .putStringArrayListExtra(
                        INSTALLED_WORKSPACE_ITEMS_EXTRA,
                        ArrayList(model.installedWorkspaceItems)
                    )
                    .putStringArrayListExtra(
                        INSTALLED_HOTSEAT_ITEMS_EXTRA,
                        ArrayList(model.installedHotseatItems)
                    )
                    .putStringArrayListExtra(
                        ALL_INSTALLED_WIDGETS_ITEM_EXTRA,
                        ArrayList(
                            model.firstScreenInstalledWidgets +
                                model.secondaryScreenInstalledWidgets
                        )
                    )
            context.sendBroadcast(intent)
        }
    }

    /** Maps Installer packages to Set of app packages from install sessions */
    private fun createPendingItemsMap(
        userKeyToSessionMap: Map<PackageUserKey, SessionInfo>
    ): Map<String, MutableSet<String>> {
        val myUser = Process.myUserHandle()
        val result = mutableMapOf<String, MutableSet<String>>()
        userKeyToSessionMap.forEach { entry ->
            if (!myUser.equals(InstallSessionHelper.getUserHandle(entry.value))) return@forEach
            val installer = entry.value.installerPackageName
            val appPackage = entry.value.appPackageName
            if (installer.isNullOrEmpty() || appPackage.isNullOrEmpty()) return@forEach
            result.getOrPut(installer) { mutableSetOf() }.add(appPackage)
        }
        return result
    }

    /**
     * Maps Installer packages to Set of ItemInfo from first screen. Filter out installing packages.
     */
    private fun createInstalledItemsMap(
        firstScreenItems: List<ItemInfo>,
        installingPackages: Set<String>,
        packageManagerHelper: PackageManagerHelper
    ): Map<String, MutableSet<ItemInfo>> {
        val result = mutableMapOf<String, MutableSet<ItemInfo>>()
        firstScreenItems.forEach { item ->
            val appPackage = getPackageName(item) ?: return@forEach
            if (installingPackages.contains(appPackage)) return@forEach
            val installer = packageManagerHelper.getAppInstallerPackage(appPackage)
            if (installer.isNullOrEmpty()) return@forEach
            result.getOrPut(installer) { mutableSetOf() }.add(item)
        }
        return result
    }

    /**
     * Maps Installer packages to Set of AppWidget packages installed on all screens. Filter out
     * installing packages.
     */
    private fun createAllInstalledWidgetsMap(
        allWidgets: List<LauncherAppWidgetInfo>,
        installingPackages: Set<String>,
        packageManagerHelper: PackageManagerHelper
    ): Map<String, MutableSet<LauncherAppWidgetInfo>> {
        val result = mutableMapOf<String, MutableSet<LauncherAppWidgetInfo>>()
        allWidgets
            .sortedBy { widget -> widget.screenId }
            .forEach { widget ->
                val appPackage = getPackageName(widget) ?: return@forEach
                if (installingPackages.contains(appPackage)) return@forEach
                val installer = packageManagerHelper.getAppInstallerPackage(appPackage)
                if (installer.isNullOrEmpty()) return@forEach
                result.getOrPut(installer) { mutableSetOf() }.add(widget)
            }
        return result
    }

    /**
     * Add first screen Pending Items from Map to [FirstScreenBroadcastModel] for given installer
     */
    private fun FirstScreenBroadcastModel.addPendingItems(
        installingItems: Set<String>?,
        firstScreenItems: List<ItemInfo>
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
        installedItemInstallerMap: Map<String, Set<ItemInfo>>,
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
        allInstalledWidgetsMap: Map<String, Set<LauncherAppWidgetInfo>>
    ) {
        allInstalledWidgetsMap[installer]?.forEach { widget ->
            val packageName: String = getPackageName(widget) ?: return@forEach
            if (widget.screenId == 0) {
                firstScreenInstalledWidgets.add(packageName)
            } else {
                secondaryScreenInstalledWidgets.add(packageName)
            }
        }
    }

    private fun FirstScreenBroadcastModel.addCollectionItems(
        info: ItemInfo,
        installingPackages: Set<String>
    ) {
        if (info !is CollectionInfo) return
        pendingCollectionItems.addAll(
            cloneOnMainThread(info.getAppContents())
                .mapNotNull { getPackageName(it) }
                .filter { installingPackages.contains(it) }
        )
    }

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
                pendingCollectionItems.isNotEmpty() ->
                    pendingCollectionItems.apply { remove(last()) }
                pendingHotseatItems.isNotEmpty() -> pendingHotseatItems.apply { remove(last()) }
                installedHotseatItems.isNotEmpty() -> installedHotseatItems.apply { remove(last()) }
                secondaryScreenInstalledWidgets.isNotEmpty() ->
                    secondaryScreenInstalledWidgets.apply { remove(last()) }
                pendingWidgetItems.isNotEmpty() -> pendingWidgetItems.apply { remove(last()) }
                firstScreenInstalledWidgets.isNotEmpty() ->
                    firstScreenInstalledWidgets.apply { remove(last()) }
                pendingWorkspaceItems.isNotEmpty() -> pendingWorkspaceItems.apply { remove(last()) }
                installedWorkspaceItems.isNotEmpty() ->
                    installedWorkspaceItems.apply { remove(last()) }
            }
            extraItemCount--
        }
    }

    /** Returns count of all Items held by [FirstScreenBroadcastModel]. */
    @VisibleForTesting
    fun FirstScreenBroadcastModel.getTotalItemCount() =
        pendingCollectionItems.size +
            pendingWorkspaceItems.size +
            pendingHotseatItems.size +
            pendingWidgetItems.size +
            installedWorkspaceItems.size +
            installedHotseatItems.size +
            firstScreenInstalledWidgets.size +
            secondaryScreenInstalledWidgets.size

    private fun FirstScreenBroadcastModel.printDebugInfo() {
        if (DEBUG) {
            Log.d(
                TAG,
                "Sending First Screen Broadcast for installer=$installerPackage" +
                    ", total packages=${getTotalItemCount()}"
            )
            pendingCollectionItems.forEach {
                Log.d(TAG, "$installerPackage:Pending Collection item:$it")
            }
            pendingWorkspaceItems.forEach {
                Log.d(TAG, "$installerPackage:Pending Workspace item:$it")
            }
            pendingHotseatItems.forEach { Log.d(TAG, "$installerPackage:Pending Hotseat item:$it") }
            pendingWidgetItems.forEach { Log.d(TAG, "$installerPackage:Pending Widget item:$it") }
            installedWorkspaceItems.forEach {
                Log.d(TAG, "$installerPackage:Installed Workspace item:$it")
            }
            installedHotseatItems.forEach {
                Log.d(TAG, "$installerPackage:Installed Hotseat item:$it")
            }
            firstScreenInstalledWidgets.forEach {
                Log.d(TAG, "$installerPackage:Installed Widget item (first screen):$it")
            }
            secondaryScreenInstalledWidgets.forEach {
                Log.d(TAG, "$installerPackage:Installed Widget item (secondary screens):$it")
            }
        }
    }

    private fun getPackageName(info: ItemInfo): String? {
        var packageName: String? = null
        if (info is LauncherAppWidgetInfo) {
            info.providerName?.let { packageName = info.providerName.packageName }
        } else if (info.targetComponent != null) {
            packageName = info.targetComponent?.packageName
        }
        return packageName
    }

    /**
     * Clone the provided list on UI thread. This is used for [FolderInfo.getContents] which is
     * always modified on UI thread.
     */
    @AnyThread
    private fun cloneOnMainThread(list: ArrayList<WorkspaceItemInfo>): List<WorkspaceItemInfo> {
        return try {
            return Executors.MAIN_EXECUTOR.submit<ArrayList<WorkspaceItemInfo>> { ArrayList(list) }
                .get()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
