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

import static com.android.launcher3.BuildConfig.WIDGET_ON_FIRST_SCREEN;
import static com.android.launcher3.Flags.enableLauncherBrMetricsFixed;
import static com.android.launcher3.Flags.enableSmartspaceAsAWidget;
import static com.android.launcher3.Flags.enableSmartspaceRemovalToggle;
import static com.android.launcher3.LauncherPrefs.IS_FIRST_LOAD_AFTER_RESTORE;
import static com.android.launcher3.LauncherPrefs.SHOULD_SHOW_SMARTSPACE;
import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_HAS_SHORTCUT_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_CHANGE_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.ModelUtils.filterCurrentWorkspaceItems;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.PackageManagerHelper.hasShortcutsPermission;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger;
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
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.IconRequestInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.shortcuts.ShortcutRequest.QueryResult;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IOUtils;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.widget.WidgetInflater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * Runnable for the thread that loads the contents of the launcher:
 *   - workspace icons
 *   - widgets
 *   - all apps icons
 *   - deep shortcuts within apps
 */
@SuppressWarnings("NewApi")
public class LoaderTask implements Runnable {
    private static final String TAG = "LoaderTask";
    public static final String SMARTSPACE_ON_HOME_SCREEN = "pref_smartspace_home_screen";

    private static final boolean DEBUG = true;

    @NonNull
    protected final LauncherAppState mApp;
    private final AllAppsList mBgAllAppsList;
    protected final BgDataModel mBgDataModel;
    private final ModelDelegate mModelDelegate;
    private boolean mIsRestoreFromBackup;

    private FirstScreenBroadcast mFirstScreenBroadcast;

    @NonNull
    private final BaseLauncherBinder mLauncherBinder;

    private final LauncherApps mLauncherApps;
    private final UserManager mUserManager;
    private final UserCache mUserCache;
    private final PackageManagerHelper mPmHelper;

    private final InstallSessionHelper mSessionHelper;
    private final IconCache mIconCache;

    private final UserManagerState mUserManagerState;
    protected final Map<ComponentKey, AppWidgetProviderInfo> mWidgetProvidersMap = new ArrayMap<>();
    private Map<ShortcutKey, ShortcutInfo> mShortcutKeyToPinnedShortcuts;
    private HashMap<PackageUserKey, SessionInfo> mInstallingPkgsCached;

    private boolean mStopped;

    private final Set<PackageUserKey> mPendingPackages = new HashSet<>();
    private boolean mItemsDeleted = false;
    private String mDbName;

    public LoaderTask(@NonNull LauncherAppState app, AllAppsList bgAllAppsList, BgDataModel bgModel,
            ModelDelegate modelDelegate, @NonNull BaseLauncherBinder launcherBinder) {
        this(app, bgAllAppsList, bgModel, modelDelegate, launcherBinder, new UserManagerState());
    }

    @VisibleForTesting
    LoaderTask(@NonNull LauncherAppState app, AllAppsList bgAllAppsList, BgDataModel bgModel,
            ModelDelegate modelDelegate, @NonNull BaseLauncherBinder launcherBinder,
            UserManagerState userManagerState) {
        mApp = app;
        mBgAllAppsList = bgAllAppsList;
        mBgDataModel = bgModel;
        mModelDelegate = modelDelegate;
        mLauncherBinder = launcherBinder;
        mLauncherApps = mApp.getContext().getSystemService(LauncherApps.class);
        mUserManager = mApp.getContext().getSystemService(UserManager.class);
        mUserCache = UserCache.INSTANCE.get(mApp.getContext());
        mPmHelper = PackageManagerHelper.INSTANCE.get(mApp.getContext());
        mSessionHelper = InstallSessionHelper.INSTANCE.get(mApp.getContext());
        mIconCache = mApp.getIconCache();
        mUserManagerState = userManagerState;
        mInstallingPkgsCached = null;
    }

    protected synchronized void waitForIdle() {
        // Wait until the either we're stopped or the other threads are done.
        // This way we don't start loading all apps until the workspace has settled
        // down.
        LooperIdleLock idleLock = mLauncherBinder.newIdleLock(this);
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
        // Screen set is never empty
        IntArray allScreens = mBgDataModel.collectWorkspaceScreens();
        final int firstScreen = allScreens.get(0);
        IntSet firstScreens = IntSet.wrap(firstScreen);

        ArrayList<ItemInfo> allItems = mBgDataModel.getAllWorkspaceItems();
        ArrayList<ItemInfo> firstScreenItems = new ArrayList<>();
        filterCurrentWorkspaceItems(firstScreens, allItems, firstScreenItems,
                new ArrayList<>() /* otherScreenItems are ignored */);
        final int launcherBroadcastInstalledApps = Settings.Secure.getInt(
                mApp.getContext().getContentResolver(),
                "launcher_broadcast_installed_apps",
                /* def= */ 0);
        if (launcherBroadcastInstalledApps == 1 && mIsRestoreFromBackup) {
            List<FirstScreenBroadcastModel> broadcastModels =
                    FirstScreenBroadcastHelper.createModelsForFirstScreenBroadcast(
                            mPmHelper,
                            firstScreenItems,
                            mInstallingPkgsCached,
                            mBgDataModel.appWidgets
                    );
            logASplit("Sending first screen broadcast with additional archiving Extras");
            FirstScreenBroadcastHelper.sendBroadcastsForModels(mApp.getContext(), broadcastModels);
        } else {
            mFirstScreenBroadcast.sendBroadcasts(mApp.getContext(), firstScreenItems);
        }
    }

    public void run() {
        synchronized (this) {
            // Skip fast if we are already stopped.
            if (mStopped) {
                return;
            }
        }

        TraceHelper.INSTANCE.beginSection(TAG);
        LoaderMemoryLogger memoryLogger = new LoaderMemoryLogger();
        mIsRestoreFromBackup =
                (Boolean) LauncherPrefs.get(mApp.getContext()).get(IS_FIRST_LOAD_AFTER_RESTORE);
        LauncherRestoreEventLogger restoreEventLogger = null;
        if (enableLauncherBrMetricsFixed()) {
            restoreEventLogger = LauncherRestoreEventLogger.Companion
                    .newInstance(mApp.getContext());
        }
        try (LauncherModel.LoaderTransaction transaction = mApp.getModel().beginLoader(this)) {

            List<ShortcutInfo> allShortcuts = new ArrayList<>();
            loadWorkspace(allShortcuts, "", memoryLogger, restoreEventLogger);

            // Sanitize data re-syncs widgets/shortcuts based on the workspace loaded from db.
            // sanitizeData should not be invoked if the workspace is loaded from a db different
            // from the main db as defined in the invariant device profile.
            // (e.g. both grid preview and minimal device mode uses a different db)
            if (Objects.equals(mApp.getInvariantDeviceProfile().dbFile, mDbName)) {
                verifyNotStopped();
                sanitizeFolders(mItemsDeleted);
                sanitizeAppPairs();
                sanitizeWidgetsShortcutsAndPackages();
                logASplit("sanitizeData");
            }

            verifyNotStopped();
            mLauncherBinder.bindWorkspace(true /* incrementBindId */, /* isBindSync= */ false);
            logASplit("bindWorkspace");

            mModelDelegate.workspaceLoadComplete();
            // Notify the installer packages of packages with active installs on the first screen.
            sendFirstScreenActiveInstallsBroadcast();
            logASplit("sendFirstScreenBroadcast");

            // Take a break
            waitForIdle();
            logASplit("step 1 complete");
            verifyNotStopped();

            // second step
            Trace.beginSection("LoadAllApps");
            List<LauncherActivityInfo> allActivityList;
            try {
                allActivityList = loadAllApps();
            } finally {
                Trace.endSection();
            }
            logASplit("loadAllApps");

            if (FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
                mModelDelegate.loadAndBindAllAppsItems(mUserManagerState,
                        mLauncherBinder.mCallbacksList, mShortcutKeyToPinnedShortcuts);
                logASplit("allAppsDelegateItems");
            }
            verifyNotStopped();
            mLauncherBinder.bindAllApps();
            logASplit("bindAllApps");

            verifyNotStopped();
            IconCacheUpdateHandler updateHandler = mIconCache.getUpdateHandler();
            setIgnorePackages(updateHandler);
            updateHandler.updateIcons(allActivityList,
                    LauncherActivityCachingLogic.newInstance(mApp.getContext()),
                    mApp.getModel()::onPackageIconsUpdated);
            logASplit("update icon cache");

            verifyNotStopped();
            logASplit("save shortcuts in icon cache");
            updateHandler.updateIcons(allShortcuts, new ShortcutCachingLogic(),
                    mApp.getModel()::onPackageIconsUpdated);

            // Take a break
            waitForIdle();
            logASplit("step 2 complete");
            verifyNotStopped();

            // third step
            List<ShortcutInfo> allDeepShortcuts = loadDeepShortcuts();
            logASplit("loadDeepShortcuts");

            verifyNotStopped();
            mLauncherBinder.bindDeepShortcuts();
            logASplit("bindDeepShortcuts");

            verifyNotStopped();
            logASplit("save deep shortcuts in icon cache");
            updateHandler.updateIcons(allDeepShortcuts,
                    new ShortcutCachingLogic(), (pkgs, user) -> { });

            // Take a break
            waitForIdle();
            logASplit("step 3 complete");
            verifyNotStopped();

            // fourth step
            List<ComponentWithLabelAndIcon> allWidgetsList =
                    mBgDataModel.widgetsModel.update(mApp, null);
            logASplit("load widgets");

            verifyNotStopped();
            mLauncherBinder.bindWidgets();
            logASplit("bindWidgets");
            verifyNotStopped();
            LauncherPrefs prefs = LauncherPrefs.get(mApp.getContext());

            if (enableSmartspaceAsAWidget() && prefs.get(SHOULD_SHOW_SMARTSPACE)) {
                mLauncherBinder.bindSmartspaceWidget();
                // Turn off pref.
                prefs.putSync(SHOULD_SHOW_SMARTSPACE.to(false));
                logASplit("bindSmartspaceWidget");
                verifyNotStopped();
            } else if (!enableSmartspaceAsAWidget() && WIDGET_ON_FIRST_SCREEN
                    && !prefs.get(LauncherPrefs.SHOULD_SHOW_SMARTSPACE)) {
                // Turn on pref.
                prefs.putSync(SHOULD_SHOW_SMARTSPACE.to(true));
            }

            if (FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
                mModelDelegate.loadAndBindOtherItems(mLauncherBinder.mCallbacksList);
                logASplit("otherDelegateItems");
                verifyNotStopped();
            }

            updateHandler.updateIcons(allWidgetsList,
                    new ComponentWithIconCachingLogic(mApp.getContext(), true),
                    mApp.getModel()::onWidgetLabelsUpdated);
            logASplit("save widgets in icon cache");

            // fifth step
            loadFolderNames();

            verifyNotStopped();
            updateHandler.finish();
            logASplit("finish icon update");

            mModelDelegate.modelLoadComplete();
            transaction.commit();
            memoryLogger.clearLogs();
            if (mIsRestoreFromBackup) {
                mIsRestoreFromBackup = false;
                LauncherPrefs.get(mApp.getContext()).putSync(IS_FIRST_LOAD_AFTER_RESTORE.to(false));
                if (restoreEventLogger != null) {
                    restoreEventLogger.reportLauncherRestoreResults();
                }
            }
        } catch (CancellationException e) {
            // Loader stopped, ignore
            logASplit("Cancelled");
        } catch (Exception e) {
            memoryLogger.printLogs();
            throw e;
        }
        TraceHelper.INSTANCE.endSection();
    }

    public synchronized void stopLocked() {
        mStopped = true;
        this.notify();
    }

    protected void loadWorkspace(
            List<ShortcutInfo> allDeepShortcuts,
            String selection,
            LoaderMemoryLogger memoryLogger,
            @Nullable LauncherRestoreEventLogger restoreEventLogger
    ) {
        Trace.beginSection("LoadWorkspace");
        try {
            loadWorkspaceImpl(allDeepShortcuts, selection, memoryLogger, restoreEventLogger);
        } finally {
            Trace.endSection();
        }
        logASplit("loadWorkspace");

        if (FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
            verifyNotStopped();
            mModelDelegate.loadAndBindWorkspaceItems(mUserManagerState,
                    mLauncherBinder.mCallbacksList, mShortcutKeyToPinnedShortcuts);
            mModelDelegate.markActive();
            logASplit("workspaceDelegateItems");
        }
        mBgDataModel.isFirstPagePinnedItemEnabled = FeatureFlags.QSB_ON_FIRST_SCREEN
                && (!enableSmartspaceRemovalToggle() || LauncherPrefs.getPrefs(
                mApp.getContext()).getBoolean(SMARTSPACE_ON_HOME_SCREEN, true));
    }

    private void loadWorkspaceImpl(
            List<ShortcutInfo> allDeepShortcuts,
            String selection,
            @Nullable LoaderMemoryLogger memoryLogger,
            @Nullable LauncherRestoreEventLogger restoreEventLogger) {
        final Context context = mApp.getContext();
        final boolean isSdCardReady = Utilities.isBootCompleted();
        final WidgetInflater widgetInflater = new WidgetInflater(context);

        ModelDbController dbController = mApp.getModel().getModelDbController();
        dbController.tryMigrateDB(restoreEventLogger);
        Log.d(TAG, "loadWorkspace: loading default favorites");
        dbController.loadDefaultFavoritesIfNecessary();

        synchronized (mBgDataModel) {
            mBgDataModel.clear();
            mPendingPackages.clear();

            final HashMap<PackageUserKey, SessionInfo> installingPkgs =
                    mSessionHelper.getActiveSessions();
            if (Flags.enableSupportForArchiving()) {
                mInstallingPkgsCached = installingPkgs;
            }
            installingPkgs.forEach(mApp.getIconCache()::updateSessionCache);
            FileLog.d(TAG, "loadWorkspace: Packages with active install sessions: "
                    + installingPkgs.keySet().stream().map(info -> info.mPackageName).toList());

            mFirstScreenBroadcast = new FirstScreenBroadcast(installingPkgs);

            mShortcutKeyToPinnedShortcuts = new HashMap<>();
            final LoaderCursor c = new LoaderCursor(
                    dbController.query(TABLE_NAME, null, selection, null, null),
                    mApp, mUserManagerState, mPmHelper,
                    mIsRestoreFromBackup ? restoreEventLogger : null);
            final Bundle extras = c.getExtras();
            mDbName = extras == null ? null : extras.getString(ModelDbController.EXTRA_DB_NAME);
            try {
                final LongSparseArray<Boolean> unlockedUsers = new LongSparseArray<>();
                queryPinnedShortcutsForUnlockedUsers(context, unlockedUsers);

                List<IconRequestInfo<WorkspaceItemInfo>> iconRequestInfos = new ArrayList<>();

                WorkspaceItemProcessor itemProcessor = new WorkspaceItemProcessor(c, memoryLogger,
                        mUserCache, mUserManagerState, mLauncherApps, mPendingPackages,
                        mShortcutKeyToPinnedShortcuts, mApp, mBgDataModel,
                        mWidgetProvidersMap, installingPkgs, isSdCardReady,
                        widgetInflater, mPmHelper, iconRequestInfos, unlockedUsers,
                        allDeepShortcuts);

                while (!mStopped && c.moveToNext()) {
                    itemProcessor.processItem();
                }
                tryLoadWorkspaceIconsInBulk(iconRequestInfos);
            } finally {
                IOUtils.closeSilently(c);
            }

            if (!FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
                mModelDelegate.loadAndBindWorkspaceItems(mUserManagerState,
                        mLauncherBinder.mCallbacksList, mShortcutKeyToPinnedShortcuts);
                mModelDelegate.loadAndBindAllAppsItems(mUserManagerState,
                        mLauncherBinder.mCallbacksList, mShortcutKeyToPinnedShortcuts);
                mModelDelegate.loadAndBindOtherItems(mLauncherBinder.mCallbacksList);
                mModelDelegate.markActive();
            }

            // Break early if we've stopped loading
            if (mStopped) {
                mBgDataModel.clear();
                return;
            }

            // Remove dead items
            mItemsDeleted = c.commitDeleted();

            processFolderItems();
            processAppPairItems();

            c.commitRestoredItems();
        }
    }

    /**
     * After all items have been processed and added to the BgDataModel, this method sorts and
     * requests high-res icons for the items that are part of an app pair.
     */
    private void processAppPairItems() {
        for (CollectionInfo collection : mBgDataModel.collections) {
            if (!(collection instanceof AppPairInfo appPair)) {
                continue;
            }

            appPair.getContents().sort(Folder.ITEM_POS_COMPARATOR);
            appPair.fetchHiResIconsIfNeeded(mIconCache);
        }
    }

    /**
     * Initialized the UserManagerState, and determines which users are unlocked. Additionally, if
     * the user is unlocked, it queries LauncherAppsService for pinned shortcuts and stores the
     * result in a class variable to be used in other methods while processing workspace items.
     *
     * @param context used to query LauncherAppsService
     * @param unlockedUsers this param is changed, and the updated value is used outside this method
     */
    @WorkerThread
    private void queryPinnedShortcutsForUnlockedUsers(Context context,
            LongSparseArray<Boolean> unlockedUsers) {
        mUserManagerState.init(mUserCache, mUserManager);

        for (UserHandle user : mUserCache.getUserProfiles()) {
            long serialNo = mUserCache.getSerialNumberForUser(user);
            boolean userUnlocked = mUserManager.isUserUnlocked(user);

            // We can only query for shortcuts when the user is unlocked.
            if (userUnlocked) {
                QueryResult pinnedShortcuts = new ShortcutRequest(context, user)
                        .query(ShortcutRequest.PINNED);
                if (pinnedShortcuts.wasSuccess()) {
                    for (ShortcutInfo shortcut : pinnedShortcuts) {
                        mShortcutKeyToPinnedShortcuts.put(ShortcutKey.fromInfo(shortcut),
                                shortcut);
                    }
                    if (pinnedShortcuts.isEmpty()) {
                        FileLog.d(TAG, "No pinned shortcuts found for user " + user);
                    }
                } else {
                    // Shortcut manager can fail due to some race condition when the
                    // lock state changes too frequently. For the purpose of the loading
                    // shortcuts, consider the user is still locked.
                    FileLog.d(TAG, "Shortcut request failed for user "
                            + user + ", user may still be locked.");
                    userUnlocked = false;
                }
            }
            unlockedUsers.put(serialNo, userUnlocked);
        }

    }

    /**
     * After all items have been processed and added to the BgDataModel, this method can correctly
     * rank items inside folders and load the correct miniature preview icons to be shown when the
     * folder is collapsed.
     */
    @WorkerThread
    private void processFolderItems() {
        // Sort the folder items, update ranks, and make sure all preview items are high res.
        List<FolderGridOrganizer> verifiers = mApp.getInvariantDeviceProfile().supportedProfiles
                .stream().map(FolderGridOrganizer::new).toList();
        for (CollectionInfo collection : mBgDataModel.collections) {
            if (!(collection instanceof FolderInfo folder)) {
                continue;
            }

            folder.getContents().sort(Folder.ITEM_POS_COMPARATOR);
            verifiers.forEach(verifier -> verifier.setFolderInfo(folder));
            int size = folder.getContents().size();

            // Update ranks here to ensure there are no gaps caused by removed folder items.
            // Ranks are the source of truth for folder items, so cellX and cellY can be
            // ignored for now. Database will be updated once user manually modifies folder.
            for (int rank = 0; rank < size; ++rank) {
                ItemInfo info = folder.getContents().get(rank);
                info.rank = rank;

                if (info instanceof WorkspaceItemInfo wii
                        && wii.usingLowResIcon()
                        && wii.itemType == Favorites.ITEM_TYPE_APPLICATION
                        && verifiers.stream().anyMatch(it -> it.isItemInPreview(info.rank))) {
                    mIconCache.getTitleAndIcon(wii, false);
                } else if (info instanceof AppPairInfo api) {
                    api.fetchHiResIconsIfNeeded(mIconCache);
                }
            }
        }
    }

    private void tryLoadWorkspaceIconsInBulk(
            List<IconRequestInfo<WorkspaceItemInfo>> iconRequestInfos) {
        Trace.beginSection("LoadWorkspaceIconsInBulk");
        try {
            mIconCache.getTitlesAndIconsInBulk(iconRequestInfos);
            for (IconRequestInfo<WorkspaceItemInfo> iconRequestInfo : iconRequestInfos) {
                WorkspaceItemInfo wai = iconRequestInfo.itemInfo;
                if (mIconCache.isDefaultIcon(wai.bitmap, wai.user)) {
                    iconRequestInfo.loadWorkspaceIcon(mApp.getContext());
                }
            }
        } finally {
            Trace.endSection();
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

    private void sanitizeFolders(boolean itemsDeleted) {
        if (itemsDeleted) {
            // Remove any empty folder
            IntArray deletedFolderIds = mApp.getModel().getModelDbController().deleteEmptyFolders();
            synchronized (mBgDataModel) {
                for (int folderId : deletedFolderIds) {
                    mBgDataModel.workspaceItems.remove(mBgDataModel.collections.get(folderId));
                    mBgDataModel.collections.remove(folderId);
                    mBgDataModel.itemsIdMap.remove(folderId);
                }
            }
        }
    }

    /** Cleans up app pairs if they don't have the right number of member apps (2). */
    private void sanitizeAppPairs() {
        IntArray deletedAppPairIds = mApp.getModel().getModelDbController().deleteBadAppPairs();
        IntArray deletedAppIds = mApp.getModel().getModelDbController().deleteUnparentedApps();

        IntArray deleted = new IntArray();
        deleted.addAll(deletedAppPairIds);
        deleted.addAll(deletedAppIds);

        synchronized (mBgDataModel) {
            for (int id : deleted) {
                mBgDataModel.workspaceItems.remove(mBgDataModel.collections.get(id));
                mBgDataModel.collections.remove(id);
                mBgDataModel.itemsIdMap.remove(id);
            }
        }
    }

    private void sanitizeWidgetsShortcutsAndPackages() {
        Context context = mApp.getContext();

        // Remove any ghost widgets
        mApp.getModel().getModelDbController().removeGhostWidgets();

        // Update pinned state of model shortcuts
        mBgDataModel.updateShortcutPinnedState(context);

        if (!Utilities.isBootCompleted() && !mPendingPackages.isEmpty()) {
            context.registerReceiver(
                    new SdCardAvailableReceiver(mApp, mPendingPackages),
                    new IntentFilter(Intent.ACTION_BOOT_COMPLETED),
                    null,
                    MODEL_EXECUTOR.getHandler());
        }
    }

    private List<LauncherActivityInfo> loadAllApps() {
        final List<UserHandle> profiles = mUserCache.getUserProfiles();
        List<LauncherActivityInfo> allActivityList = new ArrayList<>();
        // Clear the list of apps
        mBgAllAppsList.clear();

        List<IconRequestInfo<AppInfo>> iconRequestInfos = new ArrayList<>();
        boolean isWorkProfileQuiet = false;
        boolean isPrivateProfileQuiet = false;
        for (UserHandle user : profiles) {
            // Query for the set of apps
            final List<LauncherActivityInfo> apps = mLauncherApps.getActivityList(null, user);
            // Fail if we don't have any apps
            // TODO: Fix this. Only fail for the current user.
            if (apps == null || apps.isEmpty()) {
                return allActivityList;
            }
            boolean quietMode = mUserManagerState.isUserQuiet(user);

            if (Flags.enablePrivateSpace()) {
                if (mUserCache.getUserInfo(user).isWork()) {
                    isWorkProfileQuiet = quietMode;
                } else if (mUserCache.getUserInfo(user).isPrivate()) {
                    isPrivateProfileQuiet = quietMode;
                }
            }
            // Create the ApplicationInfos
            for (int i = 0; i < apps.size(); i++) {
                LauncherActivityInfo app = apps.get(i);
                AppInfo appInfo = new AppInfo(app, mUserCache.getUserInfo(user),
                        ApiWrapper.INSTANCE.get(mApp.getContext()), mPmHelper, quietMode);
                if (Flags.enableSupportForArchiving() && app.getApplicationInfo().isArchived) {
                    // For archived apps, include progress info in case there is a pending
                    // install session post restart of device.
                    String appPackageName = app.getApplicationInfo().packageName;
                    SessionInfo si = mInstallingPkgsCached != null ? mInstallingPkgsCached.get(
                            new PackageUserKey(appPackageName, user))
                            : mSessionHelper.getActiveSessionInfo(user,
                                    appPackageName);
                    if (si != null) {
                        appInfo.runtimeStatusFlags |= FLAG_INSTALL_SESSION_ACTIVE;
                        appInfo.setProgressLevel((int) (si.getProgress() * 100),
                                PackageInstallInfo.STATUS_INSTALLING);
                    }
                }

                iconRequestInfos.add(new IconRequestInfo<>(
                        appInfo, app, /* useLowResIcon= */ false));
                mBgAllAppsList.add(
                        appInfo, app, false);
            }
            allActivityList.addAll(apps);
        }


        if (FeatureFlags.PROMISE_APPS_IN_ALL_APPS.get()) {
            // get all active sessions and add them to the all apps list
            for (PackageInstaller.SessionInfo info :
                    mSessionHelper.getAllVerifiedSessions()) {
                AppInfo promiseAppInfo = mBgAllAppsList.addPromiseApp(
                        mApp.getContext(),
                        PackageInstallInfo.fromInstallingState(info),
                        false);

                if (promiseAppInfo != null) {
                    iconRequestInfos.add(new IconRequestInfo<>(
                            promiseAppInfo,
                            /* launcherActivityInfo= */ null,
                            promiseAppInfo.usingLowResIcon()));
                }
            }
        }

        Trace.beginSection("LoadAllAppsIconsInBulk");
        try {
            mIconCache.getTitlesAndIconsInBulk(iconRequestInfos);
            iconRequestInfos.forEach(iconRequestInfo ->
                    mBgAllAppsList.updateSectionName(iconRequestInfo.itemInfo));
        } finally {
            Trace.endSection();
        }

        if (Flags.enablePrivateSpace()) {
            mBgAllAppsList.setFlags(FLAG_WORK_PROFILE_QUIET_MODE_ENABLED, isWorkProfileQuiet);
            mBgAllAppsList.setFlags(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, isPrivateProfileQuiet);
        } else {
            mBgAllAppsList.setFlags(FLAG_QUIET_MODE_ENABLED,
                    mUserManagerState.isAnyProfileQuietModeEnabled());
        }
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
                mBgAllAppsList.data, mBgDataModel.collections);

        synchronized (mBgDataModel) {
            for (int i = 0; i < mBgDataModel.collections.size(); i++) {
                FolderNameInfos suggestionInfos = new FolderNameInfos();
                CollectionInfo info = mBgDataModel.collections.valueAt(i);
                if (info instanceof FolderInfo fi && fi.suggestedFolderNames == null) {
                    provider.getSuggestedFolderName(mApp.getContext(), fi.getAppContents(),
                            suggestionInfos);
                    fi.suggestedFolderNames = suggestionInfos;
                }
            }
        }
    }

    public static boolean isValidProvider(AppWidgetProviderInfo provider) {
        return (provider != null) && (provider.provider != null)
                && (provider.provider.getPackageName() != null);
    }

    private static void logASplit(String label) {
        if (DEBUG) {
            Log.d(TAG, label);
        }
    }
}
