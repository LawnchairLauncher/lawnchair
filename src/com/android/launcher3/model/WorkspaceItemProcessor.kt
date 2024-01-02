/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.ShortcutInfo
import android.graphics.Point
import android.text.TextUtils
import android.util.Log
import android.util.LongSparseArray
import androidx.annotation.VisibleForTesting
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.Utilities
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger
import com.android.launcher3.icons.IconCache
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.PackageInstallInfo
import com.android.launcher3.qsb.QsbContainerView
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.WidgetManagerHelper
import com.android.launcher3.widget.custom.CustomWidgetManager

/**
 * This items is used by LoaderTask to process items that have been loaded from the Launcher's DB.
 * This data, stored in the Favorites table, needs to be processed in order to be shown on the Home
 * Page.
 *
 * This class processes each of those items: App Shortcuts, Widgets, Folders, etc., one at a time.
 */
class WorkspaceItemProcessor(
    private val c: LoaderCursor,
    private val memoryLogger: LoaderMemoryLogger?,
    private val restoreEventLogger: LauncherRestoreEventLogger?,
    private val userManagerState: UserManagerState,
    private val launcherApps: LauncherApps,
    private val pendingPackages: MutableSet<PackageUserKey>,
    private val shortcutKeyToPinnedShortcuts: Map<ShortcutKey, ShortcutInfo>,
    private val app: LauncherAppState,
    private val iconCache: IconCache,
    private val bgDataModel: BgDataModel,
    private val widgetProvidersMap: MutableMap<ComponentKey, AppWidgetProviderInfo?>,
    private val isRestoreFromBackup: Boolean,
    private val installingPkgs: HashMap<PackageUserKey, PackageInstaller.SessionInfo>,
    private val isSdCardReady: Boolean,
    private val tempPackageKey: PackageUserKey,
    private val widgetHelper: WidgetManagerHelper,
    private val pmHelper: PackageManagerHelper,
    private val iconRequestInfos: MutableList<IconRequestInfo<WorkspaceItemInfo>>,
    private val unlockedUsers: LongSparseArray<Boolean>,
    private val isSafeMode: Boolean,
    private val allDeepShortcuts: MutableList<ShortcutInfo>
) {
    /**
     * This is the entry point for processing 1 workspace item. This method is like the midfielder
     * that delegates the actual processing to either processAppShortcut, processFolder, or
     * processWidget depending on what type of item is being processed.
     *
     * All the parameters are expected to be shared between many repeated calls of this method, one
     * for each workspace item.
     */
    fun processItem() {
        try {
            if (c.user == null) {
                // User has been deleted, remove the item.
                c.markDeleted("User has been deleted")
                if (isRestoreFromBackup) {
                    restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                        c.itemType,
                        LauncherRestoreEventLogger.RESTORE_ERROR_PROFILE_DELETED
                    )
                }
                return
            }
            when (c.itemType) {
                Favorites.ITEM_TYPE_APPLICATION,
                Favorites.ITEM_TYPE_DEEP_SHORTCUT -> processAppOrDeepShortcut()
                Favorites.ITEM_TYPE_FOLDER,
                Favorites.ITEM_TYPE_APP_PAIR -> processFolderOrAppPair()
                Favorites.ITEM_TYPE_APPWIDGET,
                Favorites.ITEM_TYPE_CUSTOM_APPWIDGET -> processWidget()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Desktop items loading interrupted", e)
        }
    }

    /**
     * This method verifies that an app shortcut should be shown on the home screen, updates the
     * database accordingly, formats the data in such a way that it is ready to be added to the data
     * model, and then adds it to the launcher’s data model.
     *
     * In this method, verification means that an an app shortcut database entry is required to:
     * Have a Launch Intent. This is how the app component symbolized by the shortcut is launched.
     * Have a Package Name. Not be in a funky “Restoring, but never actually restored” state. Not
     * have null or missing ShortcutInfos or ItemInfos in other data models.
     *
     * If any of the above are found to be true, the database entry is deleted, and not shown on the
     * user’s home screen. When an app is verified, it is marked as restored, meaning that the app
     * is viable to show on the home screen.
     *
     * In order to accommodate different types and versions of App Shortcuts, different properties
     * and flags are set on the ItemInfo objects that are added to the data model. For example,
     * icons that are not a part of the workspace or hotseat are marked as using low resolution icon
     * bitmaps. Currently suspended app icons are marked as such. Installing packages are also
     * marked as such. Lastly, after applying common properties to the ItemInfo, it is added to the
     * data model to be bound to the launcher’s data model.
     */
    @SuppressLint("NewApi")
    @VisibleForTesting
    fun processAppOrDeepShortcut() {
        var allowMissingTarget = false
        var intent = c.parseIntent()
        if (intent == null) {
            c.markDeleted("Invalid or null intent")
            if (isRestoreFromBackup) {
                restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                    c.itemType,
                    LauncherRestoreEventLogger.RESTORE_ERROR_MISSING_INFO
                )
            }
            return
        }
        var disabledState =
            if (userManagerState.isUserQuiet(c.serialNumber))
                WorkspaceItemInfo.FLAG_DISABLED_QUIET_USER
            else 0
        var cn = intent.component
        val targetPkg = if (cn == null) intent.getPackage() else cn.packageName
        if (TextUtils.isEmpty(targetPkg)) {
            c.markDeleted("Shortcuts can't have null package")
            if (isRestoreFromBackup) {
                restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                    c.itemType,
                    LauncherRestoreEventLogger.RESTORE_ERROR_MISSING_INFO
                )
            }
            return
        }

        // If there is no target package, it's an implicit intent
        // (legacy shortcut) which is always valid
        var validTarget =
            (TextUtils.isEmpty(targetPkg) || launcherApps.isPackageEnabled(targetPkg, c.user))

        // If it's a deep shortcut, we'll use pinned shortcuts to restore it
        if (cn != null && validTarget && (c.itemType != Favorites.ITEM_TYPE_DEEP_SHORTCUT)) {
            // If the apk is present and the shortcut points to a specific component.

            // If the component is already present
            if (launcherApps.isActivityEnabled(cn, c.user)) {
                // no special handling necessary for this item
                c.markRestored()
                if (isRestoreFromBackup) {
                    restoreEventLogger?.logSingleFavoritesItemRestored(c.itemType)
                }
            } else {
                // Gracefully try to find a fallback activity.
                intent = pmHelper.getAppLaunchIntent(targetPkg, c.user)
                if (intent != null) {
                    c.restoreFlag = 0
                    c.updater().put(Favorites.INTENT, intent.toUri(0)).commit()
                } else {
                    c.markDeleted("Intent null, unable to find a launch target")
                    if (isRestoreFromBackup) {
                        restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                            c.itemType,
                            LauncherRestoreEventLogger.RESTORE_ERROR_MISSING_INFO
                        )
                    }
                    return
                }
            }
        }
        // else if cn == null => can't infer much, leave it
        // else if !validPkg => could be restored icon or missing sd-card
        when {
            !TextUtils.isEmpty(targetPkg) && !validTarget -> {
                // Points to a valid app (superset of cn != null) but the apk
                // is not available.
                when {
                    c.restoreFlag != 0 -> {
                        // Package is not yet available but might be
                        // installed later.
                        FileLog.d(TAG, "package not yet restored: $targetPkg")
                        tempPackageKey.update(targetPkg, c.user)
                        when {
                            c.hasRestoreFlag(WorkspaceItemInfo.FLAG_RESTORE_STARTED) -> {
                                // Restore has started once.
                            }
                            installingPkgs.containsKey(tempPackageKey) -> {
                                // App restore has started. Update the flag
                                c.restoreFlag =
                                    c.restoreFlag or WorkspaceItemInfo.FLAG_RESTORE_STARTED
                                FileLog.d(TAG, "restore started for installing app: $targetPkg")
                                c.updater().put(Favorites.RESTORED, c.restoreFlag).commit()
                            }
                            else -> {
                                c.markDeleted(
                                    "removing app that is not restored and not installing. package: $targetPkg"
                                )
                                if (isRestoreFromBackup) {
                                    restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                                        c.itemType,
                                        LauncherRestoreEventLogger.RESTORE_ERROR_APP_NOT_INSTALLED
                                    )
                                }
                                return
                            }
                        }
                    }
                    pmHelper.isAppOnSdcard(targetPkg!!, c.user) -> {
                        // Package is present but not available.
                        disabledState =
                            disabledState or WorkspaceItemInfo.FLAG_DISABLED_NOT_AVAILABLE
                        // Add the icon on the workspace anyway.
                        allowMissingTarget = true
                    }
                    !isSdCardReady -> {
                        // SdCard is not ready yet. Package might get available,
                        // once it is ready.
                        Log.d(TAG, "Missing package, will check later: $targetPkg")
                        pendingPackages.add(PackageUserKey(targetPkg, c.user))
                        // Add the icon on the workspace anyway.
                        allowMissingTarget = true
                    }
                    else -> {
                        // Do not wait for external media load anymore.
                        c.markDeleted("Invalid package removed: $targetPkg")
                        if (isRestoreFromBackup) {
                            restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                                c.itemType,
                                LauncherRestoreEventLogger.RESTORE_ERROR_APP_NOT_INSTALLED
                            )
                        }
                        return
                    }
                }
            }
        }
        if (c.restoreFlag and WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI != 0) {
            validTarget = false
        }
        if (validTarget) {
            // The shortcut points to a valid target (either no target
            // or something which is ready to be used)
            c.markRestored()
        }
        val useLowResIcon = !c.isOnWorkspaceOrHotseat
        val info: WorkspaceItemInfo?
        when {
            c.restoreFlag != 0 -> {
                // Already verified above that user is same as default user
                info = c.getRestoredItemInfo(intent)
            }
            c.itemType == Favorites.ITEM_TYPE_APPLICATION ->
                info = c.getAppShortcutInfo(intent, allowMissingTarget, useLowResIcon, false)
            c.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT -> {
                val key = ShortcutKey.fromIntent(intent, c.user)
                if (unlockedUsers[c.serialNumber]) {
                    val pinnedShortcut = shortcutKeyToPinnedShortcuts[key]
                    if (pinnedShortcut == null) {
                        // The shortcut is no longer valid.
                        c.markDeleted(
                            "Pinned shortcut not found from request. package=${key.packageName}, user=${c.user}"
                        )
                        if (isRestoreFromBackup) {
                            restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                                c.itemType,
                                LauncherRestoreEventLogger.RESTORE_ERROR_SHORTCUT_NOT_FOUND
                            )
                        }
                        return
                    }
                    info = WorkspaceItemInfo(pinnedShortcut, app.context)
                    // If the pinned deep shortcut is no longer published,
                    // use the last saved icon instead of the default.
                    iconCache.getShortcutIcon(info, pinnedShortcut, c::loadIcon)
                    if (pmHelper.isAppSuspended(pinnedShortcut.getPackage(), info.user)) {
                        info.runtimeStatusFlags =
                            info.runtimeStatusFlags or ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED
                    }
                    intent = info.getIntent()
                    allDeepShortcuts.add(pinnedShortcut)
                } else {
                    // Create a shortcut info in disabled mode for now.
                    info = c.loadSimpleWorkspaceItem()
                    info.runtimeStatusFlags =
                        info.runtimeStatusFlags or ItemInfoWithIcon.FLAG_DISABLED_LOCKED_USER
                }
                if (isRestoreFromBackup) {
                    restoreEventLogger?.logSingleFavoritesItemRestored(c.itemType)
                }
            }
            else -> { // item type == ITEM_TYPE_SHORTCUT
                info = c.loadSimpleWorkspaceItem()

                // Shortcuts are only available on the primary profile
                if (!TextUtils.isEmpty(targetPkg) && pmHelper.isAppSuspended(targetPkg!!, c.user)) {
                    disabledState = disabledState or ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED
                }
                info.options = c.options

                // App shortcuts that used to be automatically added to Launcher
                // didn't always have the correct intent flags set, so do that here
                if (
                    intent.action != null &&
                        intent.categories != null &&
                        intent.action == Intent.ACTION_MAIN &&
                        intent.categories.contains(Intent.CATEGORY_LAUNCHER)
                ) {
                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                }
            }
        }
        if (info != null) {
            if (info.itemType != Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                // Skip deep shortcuts; their title and icons have already been
                // loaded above.
                iconRequestInfos.add(c.createIconRequestInfo(info, useLowResIcon))
            }
            c.applyCommonProperties(info)
            info.intent = intent
            info.rank = c.rank
            info.spanX = 1
            info.spanY = 1
            info.runtimeStatusFlags = info.runtimeStatusFlags or disabledState
            if (isSafeMode && !PackageManagerHelper.isSystemApp(app.context, intent)) {
                info.runtimeStatusFlags =
                    info.runtimeStatusFlags or ItemInfoWithIcon.FLAG_DISABLED_SAFEMODE
            }
            val activityInfo = c.launcherActivityInfo
            if (activityInfo != null) {
                info.setProgressLevel(
                    PackageManagerHelper.getLoadingProgress(activityInfo),
                    PackageInstallInfo.STATUS_INSTALLED_DOWNLOADING
                )
            }
            if (
                (c.restoreFlag != 0 ||
                    Flags.enableSupportForArchiving() &&
                        activityInfo != null &&
                        activityInfo.applicationInfo.isArchived) && !TextUtils.isEmpty(targetPkg)
            ) {
                tempPackageKey.update(targetPkg, c.user)
                val si = installingPkgs[tempPackageKey]
                if (si == null) {
                    info.runtimeStatusFlags =
                        info.runtimeStatusFlags and
                            ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE.inv()
                } else if (
                    activityInfo ==
                        null // For archived apps, include progress info in case there is
                    // a pending install session post restart of device.
                    ||
                        (Flags.enableSupportForArchiving() &&
                            activityInfo.applicationInfo.isArchived)
                ) {
                    val installProgress = (si.getProgress() * 100).toInt()
                    info.setProgressLevel(installProgress, PackageInstallInfo.STATUS_INSTALLING)
                }
            }
            c.checkAndAddItem(info, bgDataModel, memoryLogger)
        } else {
            throw RuntimeException("Unexpected null WorkspaceItemInfo")
        }
    }

    /**
     * Loads the folder information from the database and formats it into a FolderInfo. Some of the
     * processing for folder content items is done in LoaderTask after all the items in the
     * workspace have been loaded. The loaded FolderInfos are stored in the BgDataModel.
     */
    @VisibleForTesting
    fun processFolderOrAppPair() {
        val folderInfo =
            bgDataModel.findOrMakeFolder(c.id).apply {
                c.applyCommonProperties(this)
                itemType = c.itemType
                // Do not trim the folder label, as is was set by the user.
                title = c.getString(c.mTitleIndex)
                spanX = 1
                spanY = 1
                options = c.options
            }

        // no special handling required for restored folders
        c.markRestored()
        if (isRestoreFromBackup) {
            restoreEventLogger?.logSingleFavoritesItemRestored(c.itemType)
        }
        c.checkAndAddItem(folderInfo, bgDataModel, memoryLogger)
    }

    /**
     * This method, similar to processAppShortcut above, verifies that a widget should be shown on
     * the home screen, updates the database accordingly, formats the data in such a way that it is
     * ready to be added to the data model, and then adds it to the launcher’s data model.
     *
     * It verifies that: Widgets are not disabled due to the Launcher variety being of the `Go`
     * type. Search Widgets have a package name. The app behind the widget is still installed on the
     * device. The app behind the widget is not in a funky “Restoring, but never actually restored”
     * state. The widget has a valid size. The widget is in the workspace or the hotseat. If any of
     * the above are found to be true, the database entry is deleted, and the widget is not shown on
     * the user’s home screen. When a widget is verified, it is marked as restored, meaning that the
     * widget is viable to show on the home screen.
     *
     * Common properties are applied to the Widget’s Info object, and other information as well
     * depending on the type of widget. Custom widgets are treated differently than non-custom
     * widgets, installing / restoring widgets are treated differently, etc.
     */
    @VisibleForTesting
    fun processWidget() {
        if (WidgetsModel.GO_DISABLE_WIDGETS && c.itemType == Favorites.ITEM_TYPE_APPWIDGET) {
            c.markDeleted("Only legacy shortcuts can have null package")
            if (isRestoreFromBackup) {
                restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                    c.itemType,
                    LauncherRestoreEventLogger.RESTORE_ERROR_WIDGETS_DISABLED
                )
            }
            return
        }

        // Read all Launcher-specific widget details
        val customWidget = (c.itemType == Favorites.ITEM_TYPE_CUSTOM_APPWIDGET)
        val appWidgetId = c.appWidgetId
        val savedProvider = c.appWidgetProvider
        val component: ComponentName?
        if (c.options and LauncherAppWidgetInfo.OPTION_SEARCH_WIDGET != 0) {
            component = QsbContainerView.getSearchComponentName(app.context)
            if (component == null) {
                c.markDeleted("Discarding SearchWidget without package name")
                if (isRestoreFromBackup) {
                    restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                        c.itemType,
                        LauncherRestoreEventLogger.RESTORE_ERROR_MISSING_INFO
                    )
                }
                return
            }
        } else {
            component = ComponentName.unflattenFromString(savedProvider)
        }
        val isIdValid = !c.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)
        val wasProviderReady = !c.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)
        val providerKey = ComponentKey(component, c.user)
        if (!widgetProvidersMap.containsKey(providerKey)) {
            if (customWidget) {
                widgetProvidersMap[providerKey] =
                    CustomWidgetManager.INSTANCE[app.context].getWidgetProvider(component)
            } else {
                widgetProvidersMap[providerKey] = widgetHelper.findProvider(component, c.user)
            }
        }
        val provider = widgetProvidersMap[providerKey]
        val isProviderReady = LoaderTask.isValidProvider(provider)
        if (!isSafeMode && !customWidget && wasProviderReady && !isProviderReady) {
            c.markDeleted("Deleting widget that isn't installed anymore: $provider")
            if (isRestoreFromBackup) {
                restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                    c.itemType,
                    LauncherRestoreEventLogger.RESTORE_ERROR_APP_NOT_INSTALLED
                )
            }
        } else {
            val appWidgetInfo: LauncherAppWidgetInfo
            if (isProviderReady) {
                appWidgetInfo = LauncherAppWidgetInfo(appWidgetId, provider!!.provider)

                // The provider is available. So the widget is either
                // available or not available. We do not need to track
                // any future restore updates.
                var status =
                    (c.restoreFlag and
                        LauncherAppWidgetInfo.FLAG_RESTORE_STARTED.inv() and
                        LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY.inv())
                if (!wasProviderReady) {
                    // If provider was not previously ready, update status and UI flag.

                    // Id would be valid only if the widget restore broadcast received.
                    if (isIdValid) {
                        status = status or LauncherAppWidgetInfo.FLAG_UI_NOT_READY
                    }
                }
                appWidgetInfo.restoreStatus = status
            } else {
                Log.v(
                    TAG,
                    "Widget restore pending id=${c.id} appWidgetId=$appWidgetId status=${c.restoreFlag}"
                )
                appWidgetInfo = LauncherAppWidgetInfo(appWidgetId, component)
                appWidgetInfo.restoreStatus = c.restoreFlag
                tempPackageKey.update(component!!.packageName, c.user)
                val si = installingPkgs[tempPackageKey]
                val installProgress = if (si == null) null else (si.getProgress() * 100).toInt()
                when {
                    c.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_RESTORE_STARTED) -> {
                        // Restore has started once.
                    }
                    installProgress != null -> {
                        // App restore has started. Update the flag
                        appWidgetInfo.restoreStatus =
                            appWidgetInfo.restoreStatus or
                                LauncherAppWidgetInfo.FLAG_RESTORE_STARTED
                    }
                    !isSafeMode -> {
                        c.markDeleted("Unrestored widget removed: $component")
                        if (isRestoreFromBackup) {
                            restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                                c.itemType,
                                LauncherRestoreEventLogger.RESTORE_ERROR_APP_NOT_INSTALLED
                            )
                        }
                        return
                    }
                }
                appWidgetInfo.installProgress = installProgress ?: 0
            }
            if (appWidgetInfo.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG)) {
                appWidgetInfo.bindOptions = c.parseIntent()
            }
            c.applyCommonProperties(appWidgetInfo)
            appWidgetInfo.spanX = c.spanX
            appWidgetInfo.spanY = c.spanY
            appWidgetInfo.options = c.options
            appWidgetInfo.user = c.user
            appWidgetInfo.sourceContainer = c.appWidgetSource
            if (appWidgetInfo.spanX <= 0 || appWidgetInfo.spanY <= 0) {
                c.markDeleted(
                    "Widget has invalid size: ${appWidgetInfo.spanX}x${appWidgetInfo.spanY}"
                )
                if (isRestoreFromBackup) {
                    restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                        c.itemType,
                        LauncherRestoreEventLogger.RESTORE_ERROR_INVALID_LOCATION
                    )
                }
                return
            }
            val widgetProviderInfo =
                widgetHelper.getLauncherAppWidgetInfo(appWidgetId, appWidgetInfo.targetComponent)
            if (
                widgetProviderInfo != null &&
                    (appWidgetInfo.spanX < widgetProviderInfo.minSpanX ||
                        appWidgetInfo.spanY < widgetProviderInfo.minSpanY)
            ) {
                FileLog.d(
                    TAG,
                    "Widget ${widgetProviderInfo.component} minSizes not meet:" +
                        " span=${appWidgetInfo.spanX}x${appWidgetInfo.spanY}" +
                        " minSpan=${widgetProviderInfo.minSpanX}x${widgetProviderInfo.minSpanY}"
                )
                logWidgetInfo(app.invariantDeviceProfile, widgetProviderInfo)
            }
            if (!c.isOnWorkspaceOrHotseat) {
                c.markDeleted(
                    "Widget found where container != CONTAINER_DESKTOP" +
                        " nor CONTAINER_HOTSEAT - ignoring!"
                )
                if (isRestoreFromBackup) {
                    restoreEventLogger?.logSingleFavoritesItemRestoreFailed(
                        c.itemType,
                        LauncherRestoreEventLogger.RESTORE_ERROR_INVALID_LOCATION
                    )
                }
                return
            }
            if (!customWidget) {
                val providerName = appWidgetInfo.providerName.flattenToString()
                if (providerName != savedProvider || appWidgetInfo.restoreStatus != c.restoreFlag) {
                    c.updater()
                        .put(Favorites.APPWIDGET_PROVIDER, providerName)
                        .put(Favorites.RESTORED, appWidgetInfo.restoreStatus)
                        .commit()
                }
            }
            if (appWidgetInfo.restoreStatus != LauncherAppWidgetInfo.RESTORE_COMPLETED) {
                appWidgetInfo.pendingItemInfo =
                    WidgetsModel.newPendingItemInfo(
                        app.context,
                        appWidgetInfo.providerName,
                        appWidgetInfo.user
                    )
                iconCache.getTitleAndIconForApp(appWidgetInfo.pendingItemInfo, false)
            }
            c.checkAndAddItem(appWidgetInfo, bgDataModel)
        }
    }

    companion object {
        private val TAG = WorkspaceItemProcessor::class.java.simpleName
        private fun logWidgetInfo(
            idp: InvariantDeviceProfile,
            widgetProviderInfo: LauncherAppWidgetProviderInfo
        ) {
            val cellSize = Point()
            for (deviceProfile in idp.supportedProfiles) {
                deviceProfile.getCellSize(cellSize)
                FileLog.d(
                    TAG,
                    "DeviceProfile available width: ${deviceProfile.availableWidthPx}," +
                        " available height: ${deviceProfile.availableHeightPx}," +
                        " cellLayoutBorderSpacePx Horizontal: ${deviceProfile.cellLayoutBorderSpacePx.x}," +
                        " cellLayoutBorderSpacePx Vertical: ${deviceProfile.cellLayoutBorderSpacePx.y}," +
                        " cellSize: $cellSize"
                )
            }
            val widgetDimension = StringBuilder()
            widgetDimension
                .append("Widget dimensions:\n")
                .append("minResizeWidth: ")
                .append(widgetProviderInfo.minResizeWidth)
                .append("\n")
                .append("minResizeHeight: ")
                .append(widgetProviderInfo.minResizeHeight)
                .append("\n")
                .append("defaultWidth: ")
                .append(widgetProviderInfo.minWidth)
                .append("\n")
                .append("defaultHeight: ")
                .append(widgetProviderInfo.minHeight)
                .append("\n")
            if (Utilities.ATLEAST_S) {
                widgetDimension
                    .append("targetCellWidth: ")
                    .append(widgetProviderInfo.targetCellWidth)
                    .append("\n")
                    .append("targetCellHeight: ")
                    .append(widgetProviderInfo.targetCellHeight)
                    .append("\n")
                    .append("maxResizeWidth: ")
                    .append(widgetProviderInfo.maxResizeWidth)
                    .append("\n")
                    .append("maxResizeHeight: ")
                    .append(widgetProviderInfo.maxResizeHeight)
                    .append("\n")
            }
            FileLog.d(TAG, widgetDimension.toString())
        }
    }
}
