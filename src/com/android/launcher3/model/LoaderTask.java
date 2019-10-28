/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.model;

import static com.android.launcher3.ItemInfoWithIcon.FLAG_DISABLED_LOCKED_USER;
import static com.android.launcher3.ItemInfoWithIcon.FLAG_DISABLED_SAFEMODE;
import static com.android.launcher3.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED;
import static com.android.launcher3.compat.PackageInstallerCompat.getUserHandle;
import static com.android.launcher3.model.LoaderResults.filterCurrentWorkspaceItems;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.ShortcutInfo;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.MutableInt;

import com.android.launcher3.AllAppsList;
import com.android.launcher3.AppInfo;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIconPreviewVerifier;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.ComponentWithLabel.ComponentCachingLogic;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.LauncherActivityCachingLogic;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.cache.IconCacheUpdateHandler;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.provider.ImportDataTask;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.TraceHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

/**
 * Runnable for the thread that loads the contents of the launcher:
 *   - workspace icons
 *   - widgets
 *   - all apps icons
 *   - deep shortcuts within apps
 */
public class LoaderTask implements Runnable {
    private static final String TAG = "LoaderTask";

    private final LauncherAppState mApp;
    private final AllAppsList mBgAllAppsList;
    private final BgDataModel mBgDataModel;

    private FirstScreenBroadcast mFirstScreenBroadcast;

    private final LoaderResults mResults;

    private final LauncherAppsCompat mLauncherApps;
    private final UserManagerCompat mUserManager;
    private final DeepShortcutManager mShortcutManager;
    private final PackageInstallerCompat mPackageInstaller;
    private final AppWidgetManagerCompat mAppWidgetManager;
    private final IconCache mIconCache;

    private boolean mStopped;

    public LoaderTask(LauncherAppState app, AllAppsList bgAllAppsList, BgDataModel dataModel,
            LoaderResults results) {
        mApp = app;
        mBgAllAppsList = bgAllAppsList;
        mBgDataModel = dataModel;
        mResults = results;

        mLauncherApps = LauncherAppsCompat.getInstance(mApp.getContext());
        mUserManager = UserManagerCompat.getInstance(mApp.getContext());
        mShortcutManager = DeepShortcutManager.getInstance(mApp.getContext());
        mPackageInstaller = PackageInstallerCompat.getInstance(mApp.getContext());
        mAppWidgetManager = AppWidgetManagerCompat.getInstance(mApp.getContext());
        mIconCache = mApp.getIconCache();
    }

    protected synchronized void waitForIdle() {
        // Wait until the either we're stopped or the other threads are done.
        // This way we don't start loading all apps until the workspace has settled
        // down.
        LooperIdleLock idleLock = mResults.newIdleLock(this);
        // Just in case mFlushingWorkerThread changes but we aren't woken up,
        // wait no longer than 1sec at a time
        while (!mStopped && idleLock.awaitLocked(1000));
    }

    private synchronized void verifyNotStopped() throws CancellationException {
        if (mStopped) {
            throw new CancellationException("Loader stopped");
        }
    }

    private void sendFirstScreenActiveInstallsBroadcast() {
        ArrayList<ItemInfo> firstScreenItems = new ArrayList<>();

        ArrayList<ItemInfo> allItems = new ArrayList<>();
        synchronized (mBgDataModel) {
            allItems.addAll(mBgDataModel.workspaceItems);
            allItems.addAll(mBgDataModel.appWidgets);
        }
        // Screen set is never empty
        final int firstScreen = mBgDataModel.collectWorkspaceScreens().get(0);

        filterCurrentWorkspaceItems(firstScreen, allItems, firstScreenItems,
                new ArrayList<>() /* otherScreenItems are ignored */);
        mFirstScreenBroadcast.sendBroadcasts(mApp.getContext(), firstScreenItems);
    }

    public void run() {
        synchronized (this) {
            // Skip fast if we are already stopped.
            if (mStopped) {
                return;
            }
        }

        TraceHelper.beginSection(TAG);
        try (LauncherModel.LoaderTransaction transaction = mApp.getModel().beginLoader(this)) {
            TraceHelper.partitionSection(TAG, "step 1.1: loading workspace");
            loadWorkspace();

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 1.2: bind workspace workspace");
            mResults.bindWorkspace();

            // Notify the installer packages of packages with active installs on the first screen.
            TraceHelper.partitionSection(TAG, "step 1.3: send first screen broadcast");
            sendFirstScreenActiveInstallsBroadcast();

            // Take a break
            TraceHelper.partitionSection(TAG, "step 1 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            // second step
            TraceHelper.partitionSection(TAG, "step 2.1: loading all apps");
            List<LauncherActivityInfo> allActivityList = loadAllApps();

            TraceHelper.partitionSection(TAG, "step 2.2: Binding all apps");
            verifyNotStopped();
            mResults.bindAllApps();

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 2.3: Update icon cache");
            IconCacheUpdateHandler updateHandler = mIconCache.getUpdateHandler();
            setIgnorePackages(updateHandler);
            updateHandler.updateIcons(allActivityList,
                    LauncherActivityCachingLogic.newInstance(mApp.getContext()),
                    mApp.getModel()::onPackageIconsUpdated);

            // Take a break
            TraceHelper.partitionSection(TAG, "step 2 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            // third step
            TraceHelper.partitionSection(TAG, "step 3.1: loading deep shortcuts");
            loadDeepShortcuts();

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 3.2: bind deep shortcuts");
            mResults.bindDeepShortcuts();

            // Take a break
            TraceHelper.partitionSection(TAG, "step 3 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            // fourth step
            TraceHelper.partitionSection(TAG, "step 4.1: loading widgets");
            List<ComponentWithLabel> allWidgetsList = mBgDataModel.widgetsModel.update(mApp, null);

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 4.2: Binding widgets");
            mResults.bindWidgets();

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 4.3: Update icon cache");
            updateHandler.updateIcons(allWidgetsList, new ComponentCachingLogic(mApp.getContext()),
                    mApp.getModel()::onWidgetLabelsUpdated);

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 5: Finish icon cache update");
            updateHandler.finish();

            transaction.commit();
        } catch (CancellationException e) {
            // Loader stopped, ignore
            TraceHelper.partitionSection(TAG, "Cancelled");
        }
        TraceHelper.endSection(TAG);
    }

    public synchronized void stopLocked() {
        mStopped = true;
        this.notify();
    }

    private void loadWorkspace() {
        final Context context = mApp.getContext();
        final ContentResolver contentResolver = context.getContentResolver();
        final PackageManagerHelper pmHelper = new PackageManagerHelper(context);
        final boolean isSafeMode = pmHelper.isSafeMode();
        final boolean isSdCardReady = Utilities.isBootCompleted();
        final MultiHashMap<UserHandle, String> pendingPackages = new MultiHashMap<>();

        boolean clearDb = false;
        try {
            ImportDataTask.performImportIfPossible(context);
        } catch (Exception e) {
            // Migration failed. Clear workspace.
            clearDb = true;
        }

        if (!clearDb && !GridSizeMigrationTask.migrateGridIfNeeded(context)) {
            // Migration failed. Clear workspace.
            clearDb = true;
        }

        if (clearDb) {
            Log.d(TAG, "loadWorkspace: resetting launcher database");
            LauncherSettings.Settings.call(contentResolver,
                    LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        }

        Log.d(TAG, "loadWorkspace: loading default favorites");
        LauncherSettings.Settings.call(contentResolver,
                LauncherSettings.Settings.METHOD_LOAD_DEFAULT_FAVORITES);

        synchronized (mBgDataModel) {
            mBgDataModel.clear();

            final HashMap<PackageUserKey, SessionInfo> installingPkgs =
                    mPackageInstaller.updateAndGetActiveSessionCache();
            final PackageUserKey tempPackageKey = new PackageUserKey(null, null);
            mFirstScreenBroadcast = new FirstScreenBroadcast(installingPkgs);

            Map<ShortcutKey, ShortcutInfo> shortcutKeyToPinnedShortcuts = new HashMap<>();
            final LoaderCursor c = new LoaderCursor(contentResolver.query(
                    LauncherSettings.Favorites.CONTENT_URI, null, null, null, null), mApp);

            HashMap<ComponentKey, AppWidgetProviderInfo> widgetProvidersMap = null;

            try {
                final int appWidgetIdIndex = c.getColumnIndexOrThrow(
                        LauncherSettings.Favorites.APPWIDGET_ID);
                final int appWidgetProviderIndex = c.getColumnIndexOrThrow(
                        LauncherSettings.Favorites.APPWIDGET_PROVIDER);
                final int spanXIndex = c.getColumnIndexOrThrow
                        (LauncherSettings.Favorites.SPANX);
                final int spanYIndex = c.getColumnIndexOrThrow(
                        LauncherSettings.Favorites.SPANY);
                final int rankIndex = c.getColumnIndexOrThrow(
                        LauncherSettings.Favorites.RANK);
                final int optionsIndex = c.getColumnIndexOrThrow(
                        LauncherSettings.Favorites.OPTIONS);

                final LongSparseArray<UserHandle> allUsers = c.allUsers;
                final LongSparseArray<Boolean> quietMode = new LongSparseArray<>();
                final LongSparseArray<Boolean> unlockedUsers = new LongSparseArray<>();
                for (UserHandle user : mUserManager.getUserProfiles()) {
                    long serialNo = mUserManager.getSerialNumberForUser(user);
                    allUsers.put(serialNo, user);
                    quietMode.put(serialNo, mUserManager.isQuietModeEnabled(user));

                    boolean userUnlocked = mUserManager.isUserUnlocked(user);

                    // We can only query for shortcuts when the user is unlocked.
                    if (userUnlocked) {
                        List<ShortcutInfo> pinnedShortcuts =
                                mShortcutManager.queryForPinnedShortcuts(null, user);
                        if (mShortcutManager.wasLastCallSuccess()) {
                            for (ShortcutInfo shortcut : pinnedShortcuts) {
                                shortcutKeyToPinnedShortcuts.put(ShortcutKey.fromInfo(shortcut),
                                        shortcut);
                            }
                        } else {
                            // Shortcut manager can fail due to some race condition when the
                            // lock state changes too frequently. For the purpose of the loading
                            // shortcuts, consider the user is still locked.
                            userUnlocked = false;
                        }
                    }
                    unlockedUsers.put(serialNo, userUnlocked);
                }

                WorkspaceItemInfo info;
                LauncherAppWidgetInfo appWidgetInfo;
                Intent intent;
                String targetPkg;

                while (!mStopped && c.moveToNext()) {
                    try {
                        if (c.user == null) {
                            // User has been deleted, remove the item.
                            c.markDeleted("User has been deleted");
                            continue;
                        }

                        boolean allowMissingTarget = false;
                        switch (c.itemType) {
                        case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                        case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                        case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                            intent = c.parseIntent();
                            if (intent == null) {
                                c.markDeleted("Invalid or null intent");
                                continue;
                            }

                            int disabledState = quietMode.get(c.serialNumber) ?
                                    WorkspaceItemInfo.FLAG_DISABLED_QUIET_USER : 0;
                            ComponentName cn = intent.getComponent();
                            targetPkg = cn == null ? intent.getPackage() : cn.getPackageName();

                            if (allUsers.indexOfValue(c.user) < 0) {
                                if (c.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                                    c.markDeleted("Legacy shortcuts are only allowed for current users");
                                    continue;
                                } else if (c.restoreFlag != 0) {
                                    // Don't restore items for other profiles.
                                    c.markDeleted("Restore from other profiles not supported");
                                    continue;
                                }
                            }
                            if (TextUtils.isEmpty(targetPkg) &&
                                    c.itemType != LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                                c.markDeleted("Only legacy shortcuts can have null package");
                                continue;
                            }

                            // If there is no target package, its an implicit intent
                            // (legacy shortcut) which is always valid
                            boolean validTarget = TextUtils.isEmpty(targetPkg) ||
                                    mLauncherApps.isPackageEnabledForProfile(targetPkg, c.user);

                            if (cn != null && validTarget) {
                                // If the apk is present and the shortcut points to a specific
                                // component.

                                // If the component is already present
                                if (mLauncherApps.isActivityEnabledForProfile(cn, c.user)) {
                                    // no special handling necessary for this item
                                    c.markRestored();
                                } else {
                                    // Gracefully try to find a fallback activity.
                                    intent = pmHelper.getAppLaunchIntent(targetPkg, c.user);
                                    if (intent != null) {
                                        c.restoreFlag = 0;
                                        c.updater().put(
                                                LauncherSettings.Favorites.INTENT,
                                                intent.toUri(0)).commit();
                                        cn = intent.getComponent();
                                    } else {
                                        c.markDeleted("Unable to find a launch target");
                                        continue;
                                    }
                                }
                            }
                            // else if cn == null => can't infer much, leave it
                            // else if !validPkg => could be restored icon or missing sd-card

                            if (!TextUtils.isEmpty(targetPkg) && !validTarget) {
                                // Points to a valid app (superset of cn != null) but the apk
                                // is not available.

                                if (c.restoreFlag != 0) {
                                    // Package is not yet available but might be
                                    // installed later.
                                    FileLog.d(TAG, "package not yet restored: " + targetPkg);

                                    tempPackageKey.update(targetPkg, c.user);
                                    if (c.hasRestoreFlag(WorkspaceItemInfo.FLAG_RESTORE_STARTED)) {
                                        // Restore has started once.
                                    } else if (installingPkgs.containsKey(tempPackageKey)) {
                                        // App restore has started. Update the flag
                                        c.restoreFlag |= WorkspaceItemInfo.FLAG_RESTORE_STARTED;
                                        c.updater().put(LauncherSettings.Favorites.RESTORED,
                                                c.restoreFlag).commit();
                                    } else {
                                        c.markDeleted("Unrestored app removed: " + targetPkg);
                                        continue;
                                    }
                                } else if (pmHelper.isAppOnSdcard(targetPkg, c.user)) {
                                    // Package is present but not available.
                                    disabledState |= WorkspaceItemInfo.FLAG_DISABLED_NOT_AVAILABLE;
                                    // Add the icon on the workspace anyway.
                                    allowMissingTarget = true;
                                } else if (!isSdCardReady) {
                                    // SdCard is not ready yet. Package might get available,
                                    // once it is ready.
                                    Log.d(TAG, "Missing pkg, will check later: " + targetPkg);
                                    pendingPackages.addToList(c.user, targetPkg);
                                    // Add the icon on the workspace anyway.
                                    allowMissingTarget = true;
                                } else {
                                    // Do not wait for external media load anymore.
                                    c.markDeleted("Invalid package removed: " + targetPkg);
                                    continue;
                                }
                            }

                            if ((c.restoreFlag & WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI) != 0) {
                                validTarget = false;
                            }

                            if (validTarget) {
                                // The shortcut points to a valid target (either no target
                                // or something which is ready to be used)
                                c.markRestored();
                            }

                            boolean useLowResIcon = !c.isOnWorkspaceOrHotseat();

                            if (c.restoreFlag != 0) {
                                // Already verified above that user is same as default user
                                info = c.getRestoredItemInfo(intent);
                            } else if (c.itemType ==
                                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                                info = c.getAppShortcutInfo(
                                        intent, allowMissingTarget, useLowResIcon);
                            } else if (c.itemType ==
                                    LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {

                                ShortcutKey key = ShortcutKey.fromIntent(intent, c.user);
                                if (unlockedUsers.get(c.serialNumber)) {
                                    ShortcutInfo pinnedShortcut =
                                            shortcutKeyToPinnedShortcuts.get(key);
                                    if (pinnedShortcut == null) {
                                        // The shortcut is no longer valid.
                                        c.markDeleted("Pinned shortcut not found");
                                        continue;
                                    }
                                    info = new WorkspaceItemInfo(pinnedShortcut, context);
                                    final WorkspaceItemInfo finalInfo = info;

                                    LauncherIcons li = LauncherIcons.obtain(context);
                                    // If the pinned deep shortcut is no longer published,
                                    // use the last saved icon instead of the default.
                                    Supplier<ItemInfoWithIcon> fallbackIconProvider = () ->
                                            c.loadIcon(finalInfo, li) ? finalInfo : null;
                                    info.applyFrom(li.createShortcutIcon(pinnedShortcut,
                                            true /* badged */, fallbackIconProvider));
                                    li.recycle();
                                    if (pmHelper.isAppSuspended(
                                            pinnedShortcut.getPackage(), info.user)) {
                                        info.runtimeStatusFlags |= FLAG_DISABLED_SUSPENDED;
                                    }
                                    intent = info.intent;
                                } else {
                                    // Create a shortcut info in disabled mode for now.
                                    info = c.loadSimpleWorkspaceItem();
                                    info.runtimeStatusFlags |= FLAG_DISABLED_LOCKED_USER;
                                }
                            } else { // item type == ITEM_TYPE_SHORTCUT
                                info = c.loadSimpleWorkspaceItem();

                                // Shortcuts are only available on the primary profile
                                if (!TextUtils.isEmpty(targetPkg)
                                        && pmHelper.isAppSuspended(targetPkg, c.user)) {
                                    disabledState |= FLAG_DISABLED_SUSPENDED;
                                }

                                // App shortcuts that used to be automatically added to Launcher
                                // didn't always have the correct intent flags set, so do that
                                // here
                                if (intent.getAction() != null &&
                                    intent.getCategories() != null &&
                                    intent.getAction().equals(Intent.ACTION_MAIN) &&
                                    intent.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
                                    intent.addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                                }
                            }

                            if (info != null) {
                                c.applyCommonProperties(info);

                                info.intent = intent;
                                info.rank = c.getInt(rankIndex);
                                info.spanX = 1;
                                info.spanY = 1;
                                info.runtimeStatusFlags |= disabledState;
                                if (isSafeMode && !Utilities.isSystemApp(context, intent)) {
                                    info.runtimeStatusFlags |= FLAG_DISABLED_SAFEMODE;
                                }

                                if (c.restoreFlag != 0 && !TextUtils.isEmpty(targetPkg)) {
                                    tempPackageKey.update(targetPkg, c.user);
                                    SessionInfo si = installingPkgs.get(tempPackageKey);
                                    if (si == null) {
                                        info.status &= ~WorkspaceItemInfo.FLAG_INSTALL_SESSION_ACTIVE;
                                    } else {
                                        info.setInstallProgress((int) (si.getProgress() * 100));
                                    }
                                }

                                c.checkAndAddItem(info, mBgDataModel);
                            } else {
                                throw new RuntimeException("Unexpected null WorkspaceItemInfo");
                            }
                            break;

                        case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                            FolderInfo folderInfo = mBgDataModel.findOrMakeFolder(c.id);
                            c.applyCommonProperties(folderInfo);

                            // Do not trim the folder label, as is was set by the user.
                            folderInfo.title = c.getString(c.titleIndex);
                            folderInfo.spanX = 1;
                            folderInfo.spanY = 1;
                            folderInfo.options = c.getInt(optionsIndex);

                            // no special handling required for restored folders
                            c.markRestored();

                            c.checkAndAddItem(folderInfo, mBgDataModel);
                            break;

                        case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                            if (FeatureFlags.GO_DISABLE_WIDGETS) {
                                c.markDeleted("Only legacy shortcuts can have null package");
                                continue;
                            }
                            // Follow through
                        case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
                            // Read all Launcher-specific widget details
                            boolean customWidget = c.itemType ==
                                LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;

                            int appWidgetId = c.getInt(appWidgetIdIndex);
                            String savedProvider = c.getString(appWidgetProviderIndex);

                            final ComponentName component =
                                    ComponentName.unflattenFromString(savedProvider);

                            final boolean isIdValid = !c.hasRestoreFlag(
                                    LauncherAppWidgetInfo.FLAG_ID_NOT_VALID);
                            final boolean wasProviderReady = !c.hasRestoreFlag(
                                    LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY);

                            if (widgetProvidersMap == null) {
                                widgetProvidersMap = mAppWidgetManager.getAllProvidersMap();
                            }
                            final AppWidgetProviderInfo provider = widgetProvidersMap.get(
                                    new ComponentKey(
                                            ComponentName.unflattenFromString(savedProvider),
                                            c.user));

                            final boolean isProviderReady = isValidProvider(provider);
                            if (!isSafeMode && !customWidget &&
                                    wasProviderReady && !isProviderReady) {
                                c.markDeleted(
                                        "Deleting widget that isn't installed anymore: "
                                        + provider);
                            } else {
                                if (isProviderReady) {
                                    appWidgetInfo = new LauncherAppWidgetInfo(appWidgetId,
                                            provider.provider);

                                    // The provider is available. So the widget is either
                                    // available or not available. We do not need to track
                                    // any future restore updates.
                                    int status = c.restoreFlag &
                                            ~LauncherAppWidgetInfo.FLAG_RESTORE_STARTED &
                                            ~LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;
                                    if (!wasProviderReady) {
                                        // If provider was not previously ready, update the
                                        // status and UI flag.

                                        // Id would be valid only if the widget restore broadcast was received.
                                        if (isIdValid) {
                                            status |= LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
                                        }
                                    }
                                    appWidgetInfo.restoreStatus = status;
                                } else {
                                    Log.v(TAG, "Widget restore pending id=" + c.id
                                            + " appWidgetId=" + appWidgetId
                                            + " status =" + c.restoreFlag);
                                    appWidgetInfo = new LauncherAppWidgetInfo(appWidgetId,
                                            component);
                                    appWidgetInfo.restoreStatus = c.restoreFlag;

                                    tempPackageKey.update(component.getPackageName(), c.user);
                                    SessionInfo si =
                                            installingPkgs.get(tempPackageKey);
                                    Integer installProgress = si == null
                                            ? null
                                            : (int) (si.getProgress() * 100);

                                    if (c.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_RESTORE_STARTED)) {
                                        // Restore has started once.
                                    } else if (installProgress != null) {
                                        // App restore has started. Update the flag
                                        appWidgetInfo.restoreStatus |=
                                                LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;
                                    } else if (!isSafeMode) {
                                        c.markDeleted("Unrestored widget removed: " + component);
                                        continue;
                                    }

                                    appWidgetInfo.installProgress =
                                            installProgress == null ? 0 : installProgress;
                                }
                                if (appWidgetInfo.hasRestoreFlag(
                                        LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG)) {
                                    appWidgetInfo.bindOptions = c.parseIntent();
                                }

                                c.applyCommonProperties(appWidgetInfo);
                                appWidgetInfo.spanX = c.getInt(spanXIndex);
                                appWidgetInfo.spanY = c.getInt(spanYIndex);
                                appWidgetInfo.user = c.user;

                                if (appWidgetInfo.spanX <= 0 || appWidgetInfo.spanY <= 0) {
                                    c.markDeleted("Widget has invalid size: "
                                            + appWidgetInfo.spanX + "x" + appWidgetInfo.spanY);
                                    continue;
                                }
                                if (!c.isOnWorkspaceOrHotseat()) {
                                    c.markDeleted("Widget found where container != " +
                                            "CONTAINER_DESKTOP nor CONTAINER_HOTSEAT - ignoring!");
                                    continue;
                                }

                                if (!customWidget) {
                                    String providerName =
                                            appWidgetInfo.providerName.flattenToString();
                                    if (!providerName.equals(savedProvider) ||
                                            (appWidgetInfo.restoreStatus != c.restoreFlag)) {
                                        c.updater()
                                                .put(LauncherSettings.Favorites.APPWIDGET_PROVIDER,
                                                        providerName)
                                                .put(LauncherSettings.Favorites.RESTORED,
                                                        appWidgetInfo.restoreStatus)
                                                .commit();
                                    }
                                }

                                if (appWidgetInfo.restoreStatus !=
                                        LauncherAppWidgetInfo.RESTORE_COMPLETED) {
                                    String pkg = appWidgetInfo.providerName.getPackageName();
                                    appWidgetInfo.pendingItemInfo = new PackageItemInfo(pkg);
                                    appWidgetInfo.pendingItemInfo.user = appWidgetInfo.user;
                                    mIconCache.getTitleAndIconForApp(
                                            appWidgetInfo.pendingItemInfo, false);
                                }

                                c.checkAndAddItem(appWidgetInfo, mBgDataModel);
                            }
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Desktop items loading interrupted", e);
                    }
                }
            } finally {
                Utilities.closeSilently(c);
            }

            // Break early if we've stopped loading
            if (mStopped) {
                mBgDataModel.clear();
                return;
            }

            // Remove dead items
            if (c.commitDeleted()) {
                // Remove any empty folder
                int[] deletedFolderIds = LauncherSettings.Settings
                        .call(contentResolver,
                                LauncherSettings.Settings.METHOD_DELETE_EMPTY_FOLDERS)
                        .getIntArray(LauncherSettings.Settings.EXTRA_VALUE);
                for (int folderId : deletedFolderIds) {
                    mBgDataModel.workspaceItems.remove(mBgDataModel.folders.get(folderId));
                    mBgDataModel.folders.remove(folderId);
                    mBgDataModel.itemsIdMap.remove(folderId);
                }

                // Remove any ghost widgets
                LauncherSettings.Settings.call(contentResolver,
                        LauncherSettings.Settings.METHOD_REMOVE_GHOST_WIDGETS);
            }

            // Unpin shortcuts that don't exist on the workspace.
            HashSet<ShortcutKey> pendingShortcuts =
                    InstallShortcutReceiver.getPendingShortcuts(context);
            for (ShortcutKey key : shortcutKeyToPinnedShortcuts.keySet()) {
                MutableInt numTimesPinned = mBgDataModel.pinnedShortcutCounts.get(key);
                if ((numTimesPinned == null || numTimesPinned.value == 0)
                        && !pendingShortcuts.contains(key)) {
                    // Shortcut is pinned but doesn't exist on the workspace; unpin it.
                    mShortcutManager.unpinShortcut(key);
                }
            }

            // Sort the folder items, update ranks, and make sure all preview items are high res.
            FolderIconPreviewVerifier verifier =
                    new FolderIconPreviewVerifier(mApp.getInvariantDeviceProfile());
            for (FolderInfo folder : mBgDataModel.folders) {
                Collections.sort(folder.contents, Folder.ITEM_POS_COMPARATOR);
                verifier.setFolderInfo(folder);
                int size = folder.contents.size();

                // Update ranks here to ensure there are no gaps caused by removed folder items.
                // Ranks are the source of truth for folder items, so cellX and cellY can be ignored
                // for now. Database will be updated once user manually modifies folder.
                for (int rank = 0; rank < size; ++rank) {
                    WorkspaceItemInfo info = folder.contents.get(rank);
                    info.rank = rank;

                    if (info.usingLowResIcon()
                            && info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                            && verifier.isItemInPreview(info.rank)) {
                        mIconCache.getTitleAndIcon(info, false);
                    }
                }
            }

            c.commitRestoredItems();
            if (!isSdCardReady && !pendingPackages.isEmpty()) {
                context.registerReceiver(
                        new SdCardAvailableReceiver(mApp, pendingPackages),
                        new IntentFilter(Intent.ACTION_BOOT_COMPLETED),
                        null,
                        new Handler(LauncherModel.getWorkerLooper()));
            }
        }
    }

    private void setIgnorePackages(IconCacheUpdateHandler updateHandler) {
        // Ignore packages which have a promise icon.
        HashSet<String> packagesToIgnore = new HashSet<>();
        synchronized (mBgDataModel) {
            for (ItemInfo info : mBgDataModel.itemsIdMap) {
                if (info instanceof WorkspaceItemInfo) {
                    WorkspaceItemInfo si = (WorkspaceItemInfo) info;
                    if (si.isPromise() && si.getTargetComponent() != null) {
                        packagesToIgnore.add(si.getTargetComponent().getPackageName());
                    }
                } else if (info instanceof LauncherAppWidgetInfo) {
                    LauncherAppWidgetInfo lawi = (LauncherAppWidgetInfo) info;
                    if (lawi.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)) {
                        packagesToIgnore.add(lawi.providerName.getPackageName());
                    }
                }
            }
        }
        updateHandler.setPackagesToIgnore(Process.myUserHandle(), packagesToIgnore);
    }

    private List<LauncherActivityInfo> loadAllApps() {
        final List<UserHandle> profiles = mUserManager.getUserProfiles();
        List<LauncherActivityInfo> allActivityList = new ArrayList<>();
        // Clear the list of apps
        mBgAllAppsList.clear();
        for (UserHandle user : profiles) {
            // Query for the set of apps
            final List<LauncherActivityInfo> apps = mLauncherApps.getActivityList(null, user);
            // Fail if we don't have any apps
            // TODO: Fix this. Only fail for the current user.
            if (apps == null || apps.isEmpty()) {
                return allActivityList;
            }
            boolean quietMode = mUserManager.isQuietModeEnabled(user);
            // Create the ApplicationInfos
            for (int i = 0; i < apps.size(); i++) {
                LauncherActivityInfo app = apps.get(i);
                // This builds the icon bitmaps.
                mBgAllAppsList.add(new AppInfo(app, user, quietMode), app);
            }
            allActivityList.addAll(apps);
        }

        if (FeatureFlags.LAUNCHER3_PROMISE_APPS_IN_ALL_APPS) {
            // get all active sessions and add them to the all apps list
            for (PackageInstaller.SessionInfo info :
                    mPackageInstaller.getAllVerifiedSessions()) {
                mBgAllAppsList.addPromiseApp(mApp.getContext(),
                        PackageInstallerCompat.PackageInstallInfo.fromInstallingState(info));
            }
        }

        mBgAllAppsList.added = new ArrayList<>();
        return allActivityList;
    }

    private void loadDeepShortcuts() {
        mBgDataModel.deepShortcutMap.clear();
        mBgDataModel.hasShortcutHostPermission = mShortcutManager.hasHostPermission();
        if (mBgDataModel.hasShortcutHostPermission) {
            for (UserHandle user : mUserManager.getUserProfiles()) {
                if (mUserManager.isUserUnlocked(user)) {
                    List<ShortcutInfo> shortcuts =
                            mShortcutManager.queryForAllShortcuts(user);
                    mBgDataModel.updateDeepShortcutCounts(null, user, shortcuts);
                }
            }
        }
    }

    public static boolean isValidProvider(AppWidgetProviderInfo provider) {
        return (provider != null) && (provider.provider != null)
                && (provider.provider.getPackageName() != null);
    }
}
