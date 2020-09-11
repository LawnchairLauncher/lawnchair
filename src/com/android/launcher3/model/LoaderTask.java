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

import static com.android.launcher3.config.FeatureFlags.MULTI_DB_GRID_MIRATION_ALGO;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_HAS_SHORTCUT_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_CHANGE_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.ModelUtils.filterCurrentWorkspaceItems;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_LOCKED_USER;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SAFEMODE;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.PackageManagerHelper.hasShortcutsPermission;
import static com.android.launcher3.util.PackageManagerHelper.isSystemApp;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.MutableInt;
import android.util.TimingLogger;

import androidx.annotation.WorkerThread;

import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderGridOrganizer;
import com.android.launcher3.folder.FolderNameInfos;
import com.android.launcher3.folder.FolderNameProvider;
import com.android.launcher3.icons.ComponentWithLabelAndIcon;
import com.android.launcher3.icons.ComponentWithLabelAndIcon.ComponentWithIconCachingLogic;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.LauncherActivityCachingLogic;
import com.android.launcher3.icons.ShortcutCachingLogic;
import com.android.launcher3.icons.cache.IconCacheUpdateHandler;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.provider.ImportDataTask;
import com.android.launcher3.qsb.QsbContainerView;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.shortcuts.ShortcutRequest.QueryResult;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IOUtils;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.widget.WidgetManagerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Runnable for the thread that loads the contents of the launcher:
 *   - workspace icons
 *   - widgets
 *   - all apps icons
 *   - deep shortcuts within apps
 */
public class LoaderTask implements Runnable {
    private static final String TAG = "LoaderTask";

    protected final LauncherAppState mApp;
    private final AllAppsList mBgAllAppsList;
    protected final BgDataModel mBgDataModel;

    private FirstScreenBroadcast mFirstScreenBroadcast;

    private final LoaderResults mResults;

    private final LauncherApps mLauncherApps;
    private final UserManager mUserManager;
    private final UserCache mUserCache;

    private final InstallSessionHelper mSessionHelper;
    private final IconCache mIconCache;

    private final UserManagerState mUserManagerState = new UserManagerState();

    protected Map<ComponentKey, AppWidgetProviderInfo> mWidgetProvidersMap;

    private boolean mStopped;

    public LoaderTask(LauncherAppState app, AllAppsList bgAllAppsList, BgDataModel dataModel,
            LoaderResults results) {
        mApp = app;
        mBgAllAppsList = bgAllAppsList;
        mBgDataModel = dataModel;
        mResults = results;

        mLauncherApps = mApp.getContext().getSystemService(LauncherApps.class);
        mUserManager = mApp.getContext().getSystemService(UserManager.class);
        mUserCache = UserCache.INSTANCE.get(mApp.getContext());
        mSessionHelper = InstallSessionHelper.INSTANCE.get(mApp.getContext());
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

        Object traceToken = TraceHelper.INSTANCE.beginSection(TAG);
        TimingLogger logger = new TimingLogger(TAG, "run");
        try (LauncherModel.LoaderTransaction transaction = mApp.getModel().beginLoader(this)) {
            List<ShortcutInfo> allShortcuts = new ArrayList<>();
            loadWorkspace(allShortcuts);
            loadCachedPredictions();
            logger.addSplit("loadWorkspace");

            verifyNotStopped();
            mResults.bindWorkspace();
            logger.addSplit("bindWorkspace");

            // Notify the installer packages of packages with active installs on the first screen.
            sendFirstScreenActiveInstallsBroadcast();
            logger.addSplit("sendFirstScreenActiveInstallsBroadcast");

            // Take a break
            waitForIdle();
            logger.addSplit("step 1 complete");
            verifyNotStopped();

            // second step
            List<LauncherActivityInfo> allActivityList = loadAllApps();
            logger.addSplit("loadAllApps");

            verifyNotStopped();
            mResults.bindAllApps();
            logger.addSplit("bindAllApps");

            verifyNotStopped();
            IconCacheUpdateHandler updateHandler = mIconCache.getUpdateHandler();
            setIgnorePackages(updateHandler);
            updateHandler.updateIcons(allActivityList,
                    LauncherActivityCachingLogic.newInstance(mApp.getContext()),
                    mApp.getModel()::onPackageIconsUpdated);
            logger.addSplit("update icon cache");

            if (FeatureFlags.ENABLE_DEEP_SHORTCUT_ICON_CACHE.get()) {
                verifyNotStopped();
                logger.addSplit("save shortcuts in icon cache");
                updateHandler.updateIcons(allShortcuts, new ShortcutCachingLogic(),
                        mApp.getModel()::onPackageIconsUpdated);
            }

            // Take a break
            waitForIdle();
            logger.addSplit("step 2 complete");
            verifyNotStopped();

            // third step
            List<ShortcutInfo> allDeepShortcuts = loadDeepShortcuts();
            logger.addSplit("loadDeepShortcuts");

            verifyNotStopped();
            mResults.bindDeepShortcuts();
            logger.addSplit("bindDeepShortcuts");

            if (FeatureFlags.ENABLE_DEEP_SHORTCUT_ICON_CACHE.get()) {
                verifyNotStopped();
                logger.addSplit("save deep shortcuts in icon cache");
                updateHandler.updateIcons(allDeepShortcuts,
                        new ShortcutCachingLogic(), (pkgs, user) -> { });
            }

            // Take a break
            waitForIdle();
            logger.addSplit("step 3 complete");
            verifyNotStopped();

            // fourth step
            List<ComponentWithLabelAndIcon> allWidgetsList =
                    mBgDataModel.widgetsModel.update(mApp, null);
            logger.addSplit("load widgets");

            verifyNotStopped();
            mResults.bindWidgets();
            logger.addSplit("bindWidgets");
            verifyNotStopped();

            updateHandler.updateIcons(allWidgetsList,
                    new ComponentWithIconCachingLogic(mApp.getContext(), true),
                    mApp.getModel()::onWidgetLabelsUpdated);
            logger.addSplit("save widgets in icon cache");

            // fifth step
            if (FeatureFlags.FOLDER_NAME_SUGGEST.get()) {
                loadFolderNames();
            }

            verifyNotStopped();
            updateHandler.finish();
            logger.addSplit("finish icon update");

            transaction.commit();
        } catch (CancellationException e) {
            // Loader stopped, ignore
            logger.addSplit("Cancelled");
        } finally {
            logger.dumpToLog();
        }
        TraceHelper.INSTANCE.endSection(traceToken);
    }

    public synchronized void stopLocked() {
        mStopped = true;
        this.notify();
    }

    private void loadWorkspace(List<ShortcutInfo> allDeepShortcuts) {
        loadWorkspace(allDeepShortcuts, LauncherSettings.Favorites.CONTENT_URI,
                null /* selection */);
    }

    protected void loadWorkspace(List<ShortcutInfo> allDeepShortcuts, Uri contentUri,
            String selection) {
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

        if (!clearDb && (MULTI_DB_GRID_MIRATION_ALGO.get()
                ? !GridSizeMigrationTaskV2.migrateGridIfNeeded(context)
                : !GridSizeMigrationTask.migrateGridIfNeeded(context))) {
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
                    mSessionHelper.getActiveSessions();
            installingPkgs.forEach(mApp.getIconCache()::updateSessionCache);

            final PackageUserKey tempPackageKey = new PackageUserKey(null, null);
            mFirstScreenBroadcast = new FirstScreenBroadcast(installingPkgs);

            Map<ShortcutKey, ShortcutInfo> shortcutKeyToPinnedShortcuts = new HashMap<>();
            final LoaderCursor c = new LoaderCursor(
                    contentResolver.query(contentUri, null, selection, null, null), contentUri,
                    mApp, mUserManagerState);

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
                final LongSparseArray<Boolean> unlockedUsers = new LongSparseArray<>();

                mUserManagerState.init(mUserCache, mUserManager);

                for (UserHandle user : mUserCache.getUserProfiles()) {
                    long serialNo = mUserCache.getSerialNumberForUser(user);
                    allUsers.put(serialNo, user);

                    boolean userUnlocked = mUserManager.isUserUnlocked(user);

                    // We can only query for shortcuts when the user is unlocked.
                    if (userUnlocked) {
                        QueryResult pinnedShortcuts = new ShortcutRequest(context, user)
                                .query(ShortcutRequest.PINNED);
                        if (pinnedShortcuts.wasSuccess()) {
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

                            int disabledState = mUserManagerState.isUserQuiet(c.serialNumber)
                                    ? WorkspaceItemInfo.FLAG_DISABLED_QUIET_USER : 0;
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
                                    mLauncherApps.isPackageEnabled(targetPkg, c.user);

                            // If it's a deep shortcut, we'll use pinned shortcuts to restore it
                            if (cn != null && validTarget && c.itemType
                                    != LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                                // If the apk is present and the shortcut points to a specific
                                // component.

                                // If the component is already present
                                if (mLauncherApps.isActivityEnabled(cn, c.user)) {
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
                                    // If the pinned deep shortcut is no longer published,
                                    // use the last saved icon instead of the default.
                                    mIconCache.getShortcutIcon(info, pinnedShortcut, c::loadIcon);

                                    if (pmHelper.isAppSuspended(
                                            pinnedShortcut.getPackage(), info.user)) {
                                        info.runtimeStatusFlags |= FLAG_DISABLED_SUSPENDED;
                                    }
                                    intent = info.getIntent();
                                    allDeepShortcuts.add(pinnedShortcut);
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
                                if (isSafeMode && !isSystemApp(context, intent)) {
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
                            if (WidgetsModel.GO_DISABLE_WIDGETS) {
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
                            final ComponentName component;

                            boolean isSearchWidget = (c.getInt(optionsIndex)
                                    & LauncherAppWidgetInfo.OPTION_SEARCH_WIDGET) != 0;
                            if (isSearchWidget) {
                                component  = QsbContainerView.getSearchComponentName(context);
                                if (component == null) {
                                    c.markDeleted("Discarding SearchWidget without packagename ");
                                    continue;
                                }
                            } else {
                                component = ComponentName.unflattenFromString(savedProvider);
                            }
                            final boolean isIdValid = !c.hasRestoreFlag(
                                    LauncherAppWidgetInfo.FLAG_ID_NOT_VALID);
                            final boolean wasProviderReady = !c.hasRestoreFlag(
                                    LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY);

                            if (mWidgetProvidersMap == null) {
                                mWidgetProvidersMap = WidgetManagerHelper.getAllProvidersMap(
                                        context);
                            }
                            final AppWidgetProviderInfo provider = mWidgetProvidersMap.get(
                                    new ComponentKey(component, c.user));

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
                                appWidgetInfo.options = c.getInt(optionsIndex);
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
                IOUtils.closeSilently(c);
            }

            // Break early if we've stopped loading
            if (mStopped) {
                mBgDataModel.clear();
                return;
            }

            if (contentUri == LauncherSettings.Favorites.CONTENT_URI) {
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
                        mBgDataModel.unpinShortcut(context, key);
                    }
                }
            }

            // Sort the folder items, update ranks, and make sure all preview items are high res.
            FolderGridOrganizer verifier =
                    new FolderGridOrganizer(mApp.getInvariantDeviceProfile());
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
                        MODEL_EXECUTOR.getHandler());
            }
        }
    }

    private void setIgnorePackages(IconCacheUpdateHandler updateHandler) {
        // Ignore packages which have a promise icon.
        synchronized (mBgDataModel) {
            for (ItemInfo info : mBgDataModel.itemsIdMap) {
                if (info instanceof WorkspaceItemInfo) {
                    WorkspaceItemInfo si = (WorkspaceItemInfo) info;
                    if (si.isPromise() && si.getTargetComponent() != null) {
                        updateHandler.addPackagesToIgnore(
                                si.user, si.getTargetComponent().getPackageName());
                    }
                } else if (info instanceof LauncherAppWidgetInfo) {
                    LauncherAppWidgetInfo lawi = (LauncherAppWidgetInfo) info;
                    if (lawi.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)) {
                        updateHandler.addPackagesToIgnore(
                                lawi.user, lawi.providerName.getPackageName());
                    }
                }
            }
        }
    }

    @WorkerThread
    private void loadCachedPredictions() {
        synchronized (mBgDataModel) {
            List<ComponentKey> componentKeys =
                    mApp.getPredictionModel().getPredictionComponentKeys();
            List<LauncherActivityInfo> l;
            mBgDataModel.cachedPredictedItems.clear();
            for (ComponentKey key : componentKeys) {
                l = mLauncherApps.getActivityList(key.componentName.getPackageName(), key.user);
                if (l.size() == 0) continue;
                AppInfo info = new AppInfo(l.get(0), key.user,
                        mUserManagerState.isUserQuiet(key.user));
                mBgDataModel.cachedPredictedItems.add(info);
                mIconCache.getTitleAndIcon(info, false);
            }
        }
    }

    private List<LauncherActivityInfo> loadAllApps() {
        final List<UserHandle> profiles = mUserCache.getUserProfiles();
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
            boolean quietMode = mUserManagerState.isUserQuiet(user);
            // Create the ApplicationInfos
            for (int i = 0; i < apps.size(); i++) {
                LauncherActivityInfo app = apps.get(i);
                // This builds the icon bitmaps.
                mBgAllAppsList.add(new AppInfo(app, user, quietMode), app);
            }
            allActivityList.addAll(apps);
        }

        if (FeatureFlags.PROMISE_APPS_IN_ALL_APPS.get()) {
            // get all active sessions and add them to the all apps list
            for (PackageInstaller.SessionInfo info :
                    mSessionHelper.getAllVerifiedSessions()) {
                mBgAllAppsList.addPromiseApp(mApp.getContext(),
                        PackageInstallInfo.fromInstallingState(info));
            }
        }
        for (AppInfo item : mBgDataModel.cachedPredictedItems) {
            List<LauncherActivityInfo> l = mLauncherApps.getActivityList(
                    item.componentName.getPackageName(), item.user);
            for (LauncherActivityInfo info : l) {
                boolean quietMode = mUserManagerState.isUserQuiet(item.user);
                mBgAllAppsList.add(new AppInfo(info, item.user, quietMode), info);
            }
        }

        mBgAllAppsList.setFlags(FLAG_QUIET_MODE_ENABLED,
                mUserManagerState.isAnyProfileQuietModeEnabled());
        mBgAllAppsList.setFlags(FLAG_HAS_SHORTCUT_PERMISSION,
                hasShortcutsPermission(mApp.getContext()));
        mBgAllAppsList.setFlags(FLAG_QUIET_MODE_CHANGE_PERMISSION,
                mApp.getContext().checkSelfPermission("android.permission.MODIFY_QUIET_MODE")
                        == PackageManager.PERMISSION_GRANTED);

        mBgAllAppsList.getAndResetChangeFlag();
        return allActivityList;
    }

    private List<ShortcutInfo> loadDeepShortcuts() {
        List<ShortcutInfo> allShortcuts = new ArrayList<>();
        mBgDataModel.deepShortcutMap.clear();

        if (mBgAllAppsList.hasShortcutHostPermission()) {
            for (UserHandle user : mUserCache.getUserProfiles()) {
                if (mUserManager.isUserUnlocked(user)) {
                    List<ShortcutInfo> shortcuts = new ShortcutRequest(mApp.getContext(), user)
                            .query(ShortcutRequest.ALL);
                    allShortcuts.addAll(shortcuts);
                    mBgDataModel.updateDeepShortcutCounts(null, user, shortcuts);
                }
            }
        }
        return allShortcuts;
    }

    private void loadFolderNames() {
        FolderNameProvider provider = FolderNameProvider.newInstance(mApp.getContext(),
                mBgAllAppsList.data, mBgDataModel.folders);

        synchronized (mBgDataModel) {
            for (int i = 0; i < mBgDataModel.folders.size(); i++) {
                FolderNameInfos suggestionInfos = new FolderNameInfos();
                FolderInfo info = mBgDataModel.folders.valueAt(i);
                if (info.suggestedFolderNames == null) {
                    provider.getSuggestedFolderName(mApp.getContext(), info.contents,
                            suggestionInfos);
                    info.suggestedFolderNames = suggestionInfos;
                }
            }
        }
    }

    public static boolean isValidProvider(AppWidgetProviderInfo provider) {
        return (provider != null) && (provider.provider != null)
                && (provider.provider.getPackageName() != null);
    }
}
