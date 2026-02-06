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

import static com.android.launcher3.BuildConfigs.WIDGET_ON_FIRST_SCREEN;
import static com.android.launcher3.Flags.enableLauncherBrMetricsFixed;
import static com.android.launcher3.LauncherPrefs.IS_FIRST_LOAD_AFTER_RESTORE;
import static com.android.launcher3.icons.CacheableShortcutInfo.convertShortcutsToCacheableShortcuts;
import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;
import static com.android.launcher3.folder.FolderGridOrganizer.createFolderGridOrganizer;
import static com.android.launcher3.model.ModelUtils.WIDGET_FILTER;
import static com.android.launcher3.model.ModelUtils.currentScreenContentFilter;
import static com.android.launcher3.model.data.AppsListData.FLAG_HAS_SHORTCUT_PERMISSION;
import static com.android.launcher3.model.data.AppsListData.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.data.AppsListData.FLAG_QUIET_MODE_CHANGE_PERMISSION;
import static com.android.launcher3.model.data.AppsListData.FLAG_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.data.AppsListData.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.LooperExecutor.CALLER_LOADER_TASK;
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
import android.util.Log;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.Utilities;
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.folder.FolderNameInfos;
import com.android.launcher3.folder.FolderNameProvider;
import com.android.launcher3.icons.CacheableShortcutCachingLogic;
import com.android.launcher3.icons.CacheableShortcutInfo;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.cache.CachedObject;
import com.android.launcher3.icons.cache.CachedObjectCachingLogic;
import com.android.launcher3.icons.cache.IconCacheUpdateHandler;
import com.android.launcher3.icons.cache.LauncherActivityCachingLogic;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.LoaderCursor.LoaderCursorFactory;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.IconRequestInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.LoaderParams;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.shortcuts.ShortcutRequest.QueryResult;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.IOUtils;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.widget.WidgetInflater;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import app.lawnchair.preferences.PreferenceManager;

import javax.inject.Named;
import javax.inject.Provider;

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

    private static final boolean DEBUG = true;

    private final Context mContext;
    private final LauncherModel mModel;
    private final InvariantDeviceProfile mIDP;
    private final boolean mIsSafeModeEnabled;
    private final AllAppsList mBgAllAppsList;
    protected final BgDataModel mBgDataModel;
    private final LoaderCursorFactory mLoaderCursorFactory;
    private final LoaderParams mParams;

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
    private Map<ShortcutKey, ShortcutInfo> mShortcutKeyToPinnedShortcuts;
    private HashMap<PackageUserKey, SessionInfo> mInstallingPkgsCached;

    private List<IconRequestInfo<WorkspaceItemInfo>> mWorkspaceIconRequestInfos = new ArrayList<>();

    private boolean mStopped;

    private final Set<PackageUserKey> mPendingPackages = new HashSet<>();
    private String mDbName;
    private final Provider<FolderNameProvider> mFolderNameProviderFactory;
    private final Provider<LauncherRestoreEventLogger> mRestoreEventLoggerProvider;

    @AssistedInject
    protected LoaderTask(
            @ApplicationContext Context context,
            InvariantDeviceProfile idp,
            LauncherModel model,
            UserCache userCache,
            PackageManagerHelper pmHelper,
            InstallSessionHelper sessionHelper,
            IconCache iconCache,
            AllAppsList bgAllAppsList,
            BgDataModel bgModel,
            LoaderCursorFactory loaderCursorFactory,
            Provider<FolderNameProvider> folderNameProviderFactory,
            @Named("SAFE_MODE") boolean isSafeModeEnabled,
            @Assisted @NonNull BaseLauncherBinder launcherBinder,
            @Assisted UserManagerState userManagerState,
            Provider<LauncherRestoreEventLogger> restoreEventLoggerFactory,
            LoaderParams params) {
        mContext = context;
        mIDP = idp;
        mModel = model;
        mIsSafeModeEnabled = isSafeModeEnabled;
        mBgAllAppsList = bgAllAppsList;
        mBgDataModel = bgModel;
        mModelDelegate = model.getModelDelegate();
        mLauncherBinder = launcherBinder;
        mLoaderCursorFactory = loaderCursorFactory;
        mLauncherApps = mContext.getSystemService(LauncherApps.class);
        mUserManager = mContext.getSystemService(UserManager.class);
        mUserCache = userCache;
        mPmHelper = pmHelper;
        mSessionHelper = sessionHelper;
        mIconCache = iconCache;
        mUserManagerState = userManagerState;
        mInstallingPkgsCached = null;
        mFolderNameProviderFactory = folderNameProviderFactory;
        mRestoreEventLoggerProvider = restoreEventLoggerFactory;
        mParams = params;
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
        IntArray allScreens = mBgDataModel.itemsIdMap.collectWorkspaceScreens(mContext);
        final int firstScreen = allScreens.get(0);
        IntSet firstScreens = IntSet.wrap(firstScreen);

        List<ItemInfo> firstScreenItems =
                mBgDataModel.itemsIdMap.stream()
                        .filter(currentScreenContentFilter(firstScreens))
                        .toList();
        final int disableArchivingLauncherBroadcast = Settings.Secure.getInt(
                mContext.getContentResolver(),
                "disable_launcher_broadcast_installed_apps",
                /* default */ 0);
        boolean shouldAttachArchivingExtras = mIsRestoreFromBackup
                && disableArchivingLauncherBroadcast == 0
                && Flags.enableFirstScreenBroadcastArchivingExtras();
        if (shouldAttachArchivingExtras) {
            List<FirstScreenBroadcastModel> broadcastModels =
                    FirstScreenBroadcastHelper.createModelsForFirstScreenBroadcast(
                            mPmHelper,
                            firstScreenItems,
                            mInstallingPkgsCached,
                            mBgDataModel.itemsIdMap.stream().filter(WIDGET_FILTER).toList()
                    );
            logASplit("Sending first screen broadcast with additional archiving Extras");
            FirstScreenBroadcastHelper.sendBroadcastsForModels(mContext, broadcastModels);
        } else {
            logASplit("Sending first screen broadcast");
            mFirstScreenBroadcast.sendBroadcasts(mContext, firstScreenItems);
        }
    }

    private void loadAllSurfacesOrdered(
            LoaderMemoryLogger memoryLogger, LauncherRestoreEventLogger restoreEventLogger) {

        List<CacheableShortcutInfo> allShortcuts = new ArrayList<>();
        Trace.beginSection("LoadWorkspace");
        try {
            loadWorkspaceImpl(allShortcuts, mParams.getWorkspaceSelection(), memoryLogger,
                    restoreEventLogger);
        } finally {
            Trace.endSection();
        }
        logASplit("loadWorkspace finished");

        // Sanitize data re-syncs widgets/shortcuts based on the workspace loaded from db.
        // sanitizeData should not be invoked if the workspace is loaded from a db different
        // from the main db as defined in the invariant device profile.
        // (e.g. both grid preview and minimal device mode uses a different db)
        // TODO(b/384731096): Write Unit Test to make sure sanitizeWidgetsShortcutsAndPackages
        //  actually re-pins shortcuts that are in model but not in ShortcutManager, if possible
        //  after a simulated restore.
        if (Objects.equals(mIDP.dbFile, mDbName) && mParams.getSanitizeData()) {
            verifyNotStopped();
            sanitizeWidgetsShortcutsAndPackages();
            logASplit("sanitizeData finished");
        }

        verifyNotStopped();
        mLauncherBinder.bindWorkspace(true /* incrementBindId */, /* isBindSync= */ false);
        logASplit("bindWorkspace finished");

        if (!mParams.getLoadNonWorkspaceItems()) {
            logASplit("Skipping remaining items");
            return;
        }

        mModelDelegate.workspaceLoadComplete();
        // Notify the installer packages of packages with active installs on the first screen.
        sendFirstScreenActiveInstallsBroadcast();

        // Take a break
        waitForIdle();
        logASplit("step 1 loading workspace complete");
        verifyNotStopped();

        // second step
        Trace.beginSection("LoadAllApps");
        List<LauncherActivityInfo> allActivityList;
        try {
            allActivityList = loadAllApps();
        } finally {
            Trace.endSection();
        }
        logASplit("loadAllApps finished");

        verifyNotStopped();
        mLauncherBinder.bindAllApps();
        logASplit("bindAllApps finished");

        verifyNotStopped();
        IconCacheUpdateHandler updateHandler = mIconCache.getUpdateHandler();
        setIgnorePackages(updateHandler);
        updateHandler.updateIcons(allActivityList,
                LauncherActivityCachingLogic.INSTANCE,
                mModel::onPackageIconsUpdated);
        logASplit("update AllApps icon cache finished");

        verifyNotStopped();
        logASplit("saving all shortcuts in icon cache");
        updateHandler.updateIcons(allShortcuts, CacheableShortcutCachingLogic.INSTANCE,
                mModel::onPackageIconsUpdated);

        // Take a break
        waitForIdle();
        logASplit("step 2 loading AllApps complete");
        verifyNotStopped();

        // third step
        List<ShortcutInfo> allDeepShortcuts = loadDeepShortcuts();
        logASplit("loadDeepShortcuts finished");

        verifyNotStopped();
        mLauncherBinder.bindDeepShortcuts();
        logASplit("bindDeepShortcuts finished");

        verifyNotStopped();
        logASplit("saving deep shortcuts in icon cache");
        updateHandler.updateIcons(
                convertShortcutsToCacheableShortcuts(allDeepShortcuts, allActivityList),
                CacheableShortcutCachingLogic.INSTANCE,
                (pkgs, user) -> { });

        // Take a break
        waitForIdle();
        logASplit("step 3 loading all shortcuts complete");
        verifyNotStopped();

        // fourth step
        WidgetsModel widgetsModel = mBgDataModel.widgetsModel;
        List<CachedObject> allWidgetsList = widgetsModel.update(/*packageUser=*/null);
        logASplit("load widgets finished");

        verifyNotStopped();
        mLauncherBinder.bindWidgets();
        logASplit("bindWidgets finished");
        verifyNotStopped();

        logASplit("saving all widgets in icon cache");
        updateHandler.updateIcons(allWidgetsList,
                CachedObjectCachingLogic.INSTANCE,
                mModel::onWidgetLabelsUpdated);

        // fifth step
        loadFolderNames();

        verifyNotStopped();
        updateHandler.finish();
        logASplit("finish icon update");

        mModelDelegate.modelLoadComplete();
    }

    public void run() {
        synchronized (this) {
            // Skip fast if we are already stopped.
            if (mStopped) {
                return;
            }
        }

        TraceHelper.INSTANCE.beginSection(TAG);
        MODEL_EXECUTOR.elevatePriority(CALLER_LOADER_TASK);
        LoaderMemoryLogger memoryLogger = new LoaderMemoryLogger();
        mIsRestoreFromBackup =
                LauncherPrefs.get(mContext).get(IS_FIRST_LOAD_AFTER_RESTORE);
        LauncherRestoreEventLogger restoreEventLogger = null;
        if (enableLauncherBrMetricsFixed()) {
            restoreEventLogger = mRestoreEventLoggerProvider.get();
        }
        try (LauncherModel.LoaderTransaction transaction = mModel.beginLoader(this)) {
            loadAllSurfacesOrdered(memoryLogger, restoreEventLogger);

            transaction.commit();
            memoryLogger.clearLogs();
            if (mIsRestoreFromBackup) {
                mIsRestoreFromBackup = false;
                LauncherPrefs.get(mContext).putSync(IS_FIRST_LOAD_AFTER_RESTORE.to(false));
                if (restoreEventLogger != null) {
                    restoreEventLogger.reportLauncherRestoreResults();
                }
            }
        } catch (CancellationException e) {
            // Loader stopped, ignore
            FileLog.w(TAG, "LoaderTask cancelled");
        } catch (Exception e) {
            memoryLogger.printLogs();
            throw e;
        }
        MODEL_EXECUTOR.restorePriority(CALLER_LOADER_TASK);
        TraceHelper.INSTANCE.endSection();
    }

    public synchronized void stopLocked() {
        FileLog.w(TAG, "stopLocked: Loader stopping");
        mStopped = true;
        this.notify();
    }

    private void loadWorkspaceImpl(
            List<CacheableShortcutInfo> allDeepShortcuts,
            String selection,
            @Nullable LoaderMemoryLogger memoryLogger,
            @Nullable LauncherRestoreEventLogger restoreEventLogger) {
        final boolean isSdCardReady = Utilities.isBootCompleted();
        final WidgetInflater widgetInflater = new WidgetInflater(mContext, mIsSafeModeEnabled);

        ModelDbController dbController = mModel.getModelDbController();
        if (Flags.gridMigrationRefactor()) {
            try {
                dbController.attemptMigrateDb(restoreEventLogger, mModelDelegate);
            } catch (Exception e) {
                FileLog.e(TAG, "Failed to migrate grid", e);
            }
        } else {
            dbController.tryMigrateDB(restoreEventLogger, mModelDelegate);
        }
        Log.d(TAG, "loadWorkspace: loading default favorites if necessary");
        dbController.loadDefaultFavoritesIfNecessary();

        synchronized (mBgDataModel) {
            mBgDataModel.clear();
            mPendingPackages.clear();

            final HashMap<PackageUserKey, SessionInfo> installingPkgs =
                    mSessionHelper.getActiveSessions();
            if (Flags.enableSupportForArchiving()) {
                mInstallingPkgsCached = installingPkgs;
            }
            installingPkgs.forEach(mIconCache::updateSessionCache);
            if (Utilities.ATLEAST_U) { 
                FileLog.d(TAG, "loadWorkspace: Packages with active install sessions: "
                    + installingPkgs.keySet().stream().map(info -> info.mPackageName).toList());
            }

            mFirstScreenBroadcast = new FirstScreenBroadcast(installingPkgs);

            mShortcutKeyToPinnedShortcuts = new HashMap<>();
            final LoaderCursor c = mLoaderCursorFactory.createLoaderCursor(
                    dbController.query(null, selection, null, null),
                    mUserManagerState,
                    mIsRestoreFromBackup ? restoreEventLogger : null);
            final Bundle extras = c.getExtras();
            mDbName = extras == null ? null : extras.getString(ModelDbController.EXTRA_DB_NAME);
            WorkspaceItemProcessor itemProcessor;
            try {
                final LongSparseArray<Boolean> unlockedUsers = new LongSparseArray<>();
                queryPinnedShortcutsForUnlockedUsers(mContext, unlockedUsers);

                mWorkspaceIconRequestInfos = new ArrayList<>();
                itemProcessor = new WorkspaceItemProcessor(c, memoryLogger,
                        mUserCache, mUserManagerState, mLauncherApps, mPendingPackages,
                        mShortcutKeyToPinnedShortcuts, mContext, mIDP, mIconCache,
                        mIsSafeModeEnabled, installingPkgs, isSdCardReady, widgetInflater,
                        mPmHelper, mWorkspaceIconRequestInfos, unlockedUsers, allDeepShortcuts);

                if (mStopped) {
                    Log.w(TAG, "loadWorkspaceImpl: Loader stopped, skipping item processing");
                } else {
                    while (!mStopped && c.moveToNext()) {
                        itemProcessor.processItem();
                    }
                }
                tryLoadWorkspaceIconsInBulk(mWorkspaceIconRequestInfos);
            } finally {
                IOUtils.closeSilently(c);
            }

            // Break early if we've stopped loading
            if (mStopped) {
                mBgDataModel.clear();
                return;
            }

            mBgDataModel.stringCache.loadStrings(mContext);
            mBgDataModel.dataLoadComplete(
                    itemProcessor.finalizeData(mModelDelegate, mModel.getModelDbController()));
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

    private void tryLoadWorkspaceIconsInBulk(
            List<IconRequestInfo<WorkspaceItemInfo>> iconRequestInfos) {
        Trace.beginSection("LoadWorkspaceIconsInBulk");
        try {
            mIconCache.getTitlesAndIconsInBulk(iconRequestInfos);
            for (IconRequestInfo<WorkspaceItemInfo> iconRequestInfo : iconRequestInfos) {
                WorkspaceItemInfo wai = iconRequestInfo.itemInfo;
                if (mIconCache.isDefaultIcon(wai.bitmap, wai.user)) {
                    logASplit("tryLoadWorkspaceIconsInBulk: default icon found for "
                            + wai.getTargetComponent() + ", will attempt to load from iconBlob");
                    iconRequestInfo.loadIconFromDbBlob(mContext);
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
                if (info instanceof WorkspaceItemInfo si) {
                    if (si.isPromise() && si.getTargetComponent() != null) {
                        updateHandler.addPackagesToIgnore(
                                si.user, si.getTargetComponent().getPackageName());
                    }
                } else if (info instanceof LauncherAppWidgetInfo lawi) {
                    if (lawi.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)) {
                        updateHandler.addPackagesToIgnore(
                                lawi.user, lawi.providerName.getPackageName());
                    }
                }
            }
        }
    }


    private void sanitizeWidgetsShortcutsAndPackages() {
        // Remove any ghost widgets
        mModel.getModelDbController().removeGhostWidgets();

        // Update pinned state of model shortcuts
        mBgDataModel.updateShortcutPinnedState(mContext);

        if (!Utilities.isBootCompleted() && !mPendingPackages.isEmpty()) {
            mContext.registerReceiver(
                    new SdCardAvailableReceiver(mContext, mModel, mPendingPackages),
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

        var pref = PreferenceManager.getInstance(mContext);
        var enableBulkLoading = pref.getAllAppBulkIconLoading().get();
        
        List<IconRequestInfo<AppInfo>> allAppsItemRequestInfos = new ArrayList<>();
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
            // Query UserManager directly for current quiet mode state to avoid stale cached values
            boolean quietMode = mUserManager.isQuietModeEnabled(user);
            // Update the cached state for consistency
            mUserManagerState.updateUserQuietMode(mUserCache, user, quietMode);

            if (Flags.enablePrivateSpace()) {
                if (mUserCache.getUserInfo(user).isWork()) {
                    isWorkProfileQuiet |= quietMode;
                } else if (mUserCache.getUserInfo(user).isPrivate()) {
                    isPrivateProfileQuiet |= quietMode;
                }
            }
            // Create the ApplicationInfos
            for (int i = 0; i < apps.size(); i++) {
                LauncherActivityInfo app = apps.get(i);
                AppInfo appInfo = new AppInfo(app, mUserCache.getUserInfo(user),
                        ApiWrapper.INSTANCE.get(mContext), mPmHelper, quietMode);
                try {
                    if (Flags.enableSupportForArchiving()) {
                        if (app.getApplicationInfo().isArchived) {
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
                    }
                } catch (Throwable e) {
                    // LC-Ignored: Ignore any exceptions while trying to get the session info.
                }

                IconRequestInfo<AppInfo> iconRequestInfo = getAppInfoIconRequestInfo(
                        appInfo, app, mWorkspaceIconRequestInfos, mIsRestoreFromBackup);
                allAppsItemRequestInfos.add(iconRequestInfo);
                mBgAllAppsList.add(appInfo, app, false);
            }
            allActivityList.addAll(apps);
        }

        if (FeatureFlags.PROMISE_APPS_IN_ALL_APPS.get()) {
            // get all active sessions and add them to the all apps list
            for (PackageInstaller.SessionInfo info :
                    mSessionHelper.getAllVerifiedSessions()) {
                AppInfo promiseAppInfo = mBgAllAppsList.addPromiseApp(
                        mContext,
                        PackageInstallInfo.fromInstallingState(info),
                        false);

                if (promiseAppInfo != null) {
                    allAppsItemRequestInfos.add(new IconRequestInfo<>(
                            promiseAppInfo,
                            /* launcherActivityInfo= */ null,
                            promiseAppInfo.getMatchingLookupFlag().withThemeIcon(false)));
                }
            }
        }

        if (enableBulkLoading) {
            Trace.beginSection("LoadAllAppsIconsInBulk");

            try {
                mIconCache.getTitlesAndIconsInBulk(allAppsItemRequestInfos);
                if (Flags.restoreArchivedAppIconsFromDb()) {
                    for (IconRequestInfo<AppInfo> iconRequestInfo : allAppsItemRequestInfos) {
                        AppInfo appInfo = iconRequestInfo.itemInfo;
                        if (mIconCache.isDefaultIcon(appInfo.bitmap, appInfo.user)) {
                            logASplit("LoadAllAppsIconsInBulk: default icon found for "
                                + appInfo.getTargetComponent()
                                + ", will attempt to load from iconBlob: "
                                + Arrays.toString(iconRequestInfo.iconBlob));
                            iconRequestInfo.loadIconFromDbBlob(mContext);
                        }
                    }
                }
                allAppsItemRequestInfos.forEach(iconRequestInfo ->
                    mBgAllAppsList.updateSectionName(iconRequestInfo.itemInfo));
            } finally {
                Trace.endSection();
            }
        } else {
            // LC-Note: This code dark magic sorting fallback is powered by Opus 4.5
            Trace.beginSection("LoadAllAppsIconsIndividually");
            try {
                for (IconRequestInfo<AppInfo> iconRequestInfo : allAppsItemRequestInfos) {
                    AppInfo appInfo = iconRequestInfo.itemInfo;
                    if (iconRequestInfo.launcherActivityInfo != null) {
                        mIconCache.getTitleAndIcon(appInfo, iconRequestInfo.launcherActivityInfo,
                                appInfo.getMatchingLookupFlag());
                    } else {
                        mIconCache.getTitleAndIcon(appInfo, appInfo.getMatchingLookupFlag());
                    }
                    mBgAllAppsList.updateSectionName(appInfo);
                }
            } finally {
                Trace.endSection();
            }
        }

        if (Flags.enablePrivateSpace()) {
            mBgAllAppsList.setFlags(FLAG_WORK_PROFILE_QUIET_MODE_ENABLED, isWorkProfileQuiet);
            mBgAllAppsList.setFlags(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, isPrivateProfileQuiet);
        } else {
            mBgAllAppsList.setFlags(FLAG_QUIET_MODE_ENABLED,
                    mUserManagerState.isAnyProfileQuietModeEnabled());
        }
        mBgAllAppsList.setFlags(FLAG_HAS_SHORTCUT_PERMISSION,
                hasShortcutsPermission(mContext));
        mBgAllAppsList.setFlags(FLAG_QUIET_MODE_CHANGE_PERMISSION,
                mContext.checkSelfPermission("android.permission.MODIFY_QUIET_MODE")
                        == PackageManager.PERMISSION_GRANTED);

        mBgAllAppsList.getAndResetChangeFlag();
        return allActivityList;
    }

    @NonNull
    @VisibleForTesting
    IconRequestInfo<AppInfo> getAppInfoIconRequestInfo(
            AppInfo appInfo,
            LauncherActivityInfo activityInfo,
            List<IconRequestInfo<WorkspaceItemInfo>> workspaceRequestInfos,
            boolean isRestoreFromBackup
    ) {
        if (Flags.restoreArchivedAppIconsFromDb() && isRestoreFromBackup) {
            Optional<IconRequestInfo<WorkspaceItemInfo>> workspaceIconRequest =
                    workspaceRequestInfos.stream()
                            .filter(request -> appInfo.getTargetComponent().equals(
                                    request.itemInfo.getTargetComponent()))
                            .findFirst();

            if (workspaceIconRequest.isPresent() && activityInfo.getApplicationInfo().isArchived) {
                logASplit("getAppInfoIconRequestInfo:"
                            + " matching archived info found, loading icon blob into icon request."
                            + " Component=" + appInfo.getTargetComponent());
                IconRequestInfo<AppInfo> iconRequestInfo = new IconRequestInfo<>(
                        appInfo,
                        activityInfo,
                        workspaceIconRequest.get().iconBlob,
                        DEFAULT_LOOKUP_FLAG.withUseLowRes(false)
                );
                if (!iconRequestInfo.loadIconFromDbBlob(mContext)) {
                    Log.d(TAG, "AppInfo Icon failed to load from blob, using cache.");
                    mIconCache.getTitleAndIcon(
                            appInfo,
                            iconRequestInfo.launcherActivityInfo,
                            DEFAULT_LOOKUP_FLAG
                    );
                }
                return iconRequestInfo;
            } else {
                Log.d(TAG, "App not archived or workspace info not found"
                        + ", creating IconRequestInfo without icon blob."
                        + " Component:" + appInfo.getTargetComponent()
                        + ", isArchived: " + activityInfo.getApplicationInfo().isArchived);
            }
        }
        return new IconRequestInfo<>(appInfo, activityInfo,
                DEFAULT_LOOKUP_FLAG.withUseLowRes(false));
    }

    private List<ShortcutInfo> loadDeepShortcuts() {
        List<ShortcutInfo> allShortcuts = new ArrayList<>();
        mBgDataModel.deepShortcutMap.clear();

        if (mBgAllAppsList.hasShortcutHostPermission()) {
            for (UserHandle user : mUserCache.getUserProfiles()) {
                if (mUserManager.isUserUnlocked(user)) {
                    List<ShortcutInfo> shortcuts = new ShortcutRequest(mContext, user)
                            .query(ShortcutRequest.ALL);
                    allShortcuts.addAll(shortcuts);
                    mBgDataModel.updateDeepShortcutCounts(null, user, shortcuts);
                }
            }
        }
        return allShortcuts;
    }

    private void loadFolderNames() {
        FolderNameProvider provider = mFolderNameProviderFactory.get();
        provider.load(mBgAllAppsList.data,
                FolderNameProvider.getCollectionForSuggestions(mBgDataModel));

        synchronized (mBgDataModel) {
            mBgDataModel.itemsIdMap.stream()
                    .filter(item ->
                            item instanceof FolderInfo fi && fi.suggestedFolderNames == null)
                    .forEach(info -> {
                        FolderInfo fi = (FolderInfo) info;
                        FolderNameInfos suggestionInfos = new FolderNameInfos();
                        provider.getSuggestedFolderName(mContext, fi.getAppContents(),
                                suggestionInfos);
                        fi.suggestedFolderNames = suggestionInfos;
                    });
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

    @AssistedFactory
    public interface LoaderTaskFactory {

        LoaderTask newLoaderTask(BaseLauncherBinder binder, UserManagerState userState);
    }

}
