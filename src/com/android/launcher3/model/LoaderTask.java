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
import static com.android.launcher3.LauncherPrefs.SHOULD_SHOW_SMARTSPACE;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR;
import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.config.FeatureFlags.ENABLE_SMARTSPACE_REMOVAL;
import static com.android.launcher3.config.FeatureFlags.SMARTSPACE_AS_A_WIDGET;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_HAS_SHORTCUT_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_CHANGE_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.ModelUtils.filterCurrentWorkspaceItems;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_LOCKED_USER;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SAFEMODE;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.PackageManagerHelper.hasShortcutsPermission;
import static com.android.launcher3.util.PackageManagerHelper.isSystemApp;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings.Favorites;
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
import com.android.launcher3.model.data.IconRequestInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.qsb.QsbContainerView;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.shortcuts.ShortcutRequest.QueryResult;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IOUtils;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetManagerHelper;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public static final String SMARTSPACE_ON_HOME_SCREEN = "pref_smartspace_home_screen";

    private static final boolean DEBUG = true;

    @NonNull
    protected final LauncherAppState mApp;
    private final AllAppsList mBgAllAppsList;
    protected final BgDataModel mBgDataModel;
    private final ModelDelegate mModelDelegate;

    private FirstScreenBroadcast mFirstScreenBroadcast;

    @NonNull
    private final LauncherBinder mLauncherBinder;

    private final LauncherApps mLauncherApps;
    private final UserManager mUserManager;
    private final UserCache mUserCache;

    private final InstallSessionHelper mSessionHelper;
    private final IconCache mIconCache;

    private final UserManagerState mUserManagerState;

    protected final Map<ComponentKey, AppWidgetProviderInfo> mWidgetProvidersMap = new ArrayMap<>();
    private Map<ShortcutKey, ShortcutInfo> mShortcutKeyToPinnedShortcuts;

    private boolean mStopped;

    private final Set<PackageUserKey> mPendingPackages = new HashSet<>();
    private boolean mItemsDeleted = false;
    private String mDbName;

    public LoaderTask(@NonNull LauncherAppState app, AllAppsList bgAllAppsList, BgDataModel bgModel,
            ModelDelegate modelDelegate, @NonNull LauncherBinder launcherBinder) {
        this(app, bgAllAppsList, bgModel, modelDelegate, launcherBinder, new UserManagerState());
    }

    @VisibleForTesting
    LoaderTask(@NonNull LauncherAppState app, AllAppsList bgAllAppsList, BgDataModel bgModel,
            ModelDelegate modelDelegate, @NonNull LauncherBinder launcherBinder,
            UserManagerState userManagerState) {
        mApp = app;
        mBgAllAppsList = bgAllAppsList;
        mBgDataModel = bgModel;
        mModelDelegate = modelDelegate;
        mLauncherBinder = launcherBinder;

        mLauncherApps = mApp.getContext().getSystemService(LauncherApps.class);
        mUserManager = mApp.getContext().getSystemService(UserManager.class);
        mUserCache = UserCache.getInstance(mApp.getContext());
        mSessionHelper = InstallSessionHelper.INSTANCE.get(mApp.getContext());
        mIconCache = mApp.getIconCache();
        mUserManagerState = userManagerState;
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
        ArrayList<ItemInfo> firstScreenItems = new ArrayList<>();
        ArrayList<ItemInfo> allItems = mBgDataModel.getAllWorkspaceItems();

        // Screen set is never empty
        IntArray allScreens = mBgDataModel.collectWorkspaceScreens();
        final int firstScreen = allScreens.get(0);
        IntSet firstScreens = IntSet.wrap(firstScreen);

        filterCurrentWorkspaceItems(firstScreens, allItems, firstScreenItems,
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

        TraceHelper.INSTANCE.beginSection(TAG);
        LoaderMemoryLogger memoryLogger = new LoaderMemoryLogger();
        try (LauncherModel.LoaderTransaction transaction = mApp.getModel().beginLoader(this)) {
            List<ShortcutInfo> allShortcuts = new ArrayList<>();
            loadWorkspace(allShortcuts, "", memoryLogger);

            // Sanitize data re-syncs widgets/shortcuts based on the workspace loaded from db.
            // sanitizeData should not be invoked if the workspace is loaded from a db different
            // from the main db as defined in the invariant device profile.
            // (e.g. both grid preview and minimal device mode uses a different db)
            if (mApp.getInvariantDeviceProfile().dbFile.equals(mDbName)) {
                verifyNotStopped();
                sanitizeFolders(mItemsDeleted);
                sanitizeWidgetsShortcutsAndPackages();
                logASplit("sanitizeData");
            }

            verifyNotStopped();
            mLauncherBinder.bindWorkspace(true /* incrementBindId */, /* isBindSync= */ false);
            logASplit("bindWorkspace");

            mModelDelegate.workspaceLoadComplete();
            // Notify the installer packages of packages with active installs on the first screen.
            sendFirstScreenActiveInstallsBroadcast();
            logASplit("sendFirstScreenActiveInstallsBroadcast");

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
            if (SMARTSPACE_AS_A_WIDGET.get() && prefs.get(SHOULD_SHOW_SMARTSPACE)) {
                mLauncherBinder.bindSmartspaceWidget();
                // Turn off pref.
                prefs.putSync(SHOULD_SHOW_SMARTSPACE.to(false));
                logASplit("bindSmartspaceWidget");
                verifyNotStopped();
            } else if (!SMARTSPACE_AS_A_WIDGET.get() && WIDGET_ON_FIRST_SCREEN
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
            LoaderMemoryLogger memoryLogger) {
        Trace.beginSection("LoadWorkspace");
        try {
            loadWorkspaceImpl(allDeepShortcuts, selection, memoryLogger);
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
                && (!ENABLE_SMARTSPACE_REMOVAL.get() || LauncherPrefs.getPrefs(
                mApp.getContext()).getBoolean(SMARTSPACE_ON_HOME_SCREEN, true));
    }

    private void loadWorkspaceImpl(
            List<ShortcutInfo> allDeepShortcuts,
            String selection,
            @Nullable LoaderMemoryLogger memoryLogger) {
        final Context context = mApp.getContext();
        final PackageManagerHelper pmHelper = new PackageManagerHelper(context);
        final boolean isSafeMode = pmHelper.isSafeMode();
        final boolean isSdCardReady = Utilities.isBootCompleted();
        final WidgetManagerHelper widgetHelper = new WidgetManagerHelper(context);

        ModelDbController dbController = mApp.getModel().getModelDbController();
        dbController.tryMigrateDB();
        Log.d(TAG, "loadWorkspace: loading default favorites");
        dbController.loadDefaultFavoritesIfNecessary();

        synchronized (mBgDataModel) {
            mBgDataModel.clear();
            mPendingPackages.clear();

            final HashMap<PackageUserKey, SessionInfo> installingPkgs =
                    mSessionHelper.getActiveSessions();
            installingPkgs.forEach(mApp.getIconCache()::updateSessionCache);
            FileLog.d(TAG, "loadWorkspace: Packages with active install sessions: "
                    + installingPkgs.keySet().stream().map(info -> info.mPackageName).toList());

            final PackageUserKey tempPackageKey = new PackageUserKey(null, null);
            mFirstScreenBroadcast = new FirstScreenBroadcast(installingPkgs);

            mShortcutKeyToPinnedShortcuts = new HashMap<>();
            final LoaderCursor c = new LoaderCursor(
                    dbController.query(TABLE_NAME, null, selection, null, null),
                    mApp, mUserManagerState);
            final Bundle extras = c.getExtras();
            mDbName = extras == null ? null : extras.getString(ModelDbController.EXTRA_DB_NAME);
            try {
                final LongSparseArray<Boolean> unlockedUsers = new LongSparseArray<>();

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

                List<IconRequestInfo<WorkspaceItemInfo>> iconRequestInfos = new ArrayList<>();

                while (!mStopped && c.moveToNext()) {
                    processWorkspaceItem(c, memoryLogger, installingPkgs, isSdCardReady,
                            tempPackageKey, widgetHelper, pmHelper,
                            iconRequestInfos, unlockedUsers, isSafeMode, allDeepShortcuts);
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

            // Sort the folder items, update ranks, and make sure all preview items are high res.
            List<FolderGridOrganizer> verifiers =
                    mApp.getInvariantDeviceProfile().supportedProfiles.stream().map(
                            FolderGridOrganizer::new).toList();
            for (FolderInfo folder : mBgDataModel.folders) {
                Collections.sort(folder.contents, Folder.ITEM_POS_COMPARATOR);
                verifiers.forEach(verifier -> verifier.setFolderInfo(folder));
                int size = folder.contents.size();

                // Update ranks here to ensure there are no gaps caused by removed folder items.
                // Ranks are the source of truth for folder items, so cellX and cellY can be
                // ignored for now. Database will be updated once user manually modifies folder.
                for (int rank = 0; rank < size; ++rank) {
                    WorkspaceItemInfo info = folder.contents.get(rank);
                    // rank is used differently in app pairs, so don't reset
                    if (folder.itemType != ITEM_TYPE_APP_PAIR) {
                        info.rank = rank;
                    }

                    if (info.usingLowResIcon() && info.itemType == Favorites.ITEM_TYPE_APPLICATION
                            && verifiers.stream().anyMatch(
                                verifier -> verifier.isItemInPreview(info.rank))) {
                        mIconCache.getTitleAndIcon(info, false);
                    }
                }
            }

            c.commitRestoredItems();
        }
    }

    private void processWorkspaceItem(LoaderCursor c,
            LoaderMemoryLogger memoryLogger,
            HashMap<PackageUserKey, SessionInfo> installingPkgs,
            boolean isSdCardReady,
            PackageUserKey tempPackageKey,
            WidgetManagerHelper widgetHelper,
            PackageManagerHelper pmHelper,
            List<IconRequestInfo<WorkspaceItemInfo>> iconRequestInfos,
            LongSparseArray<Boolean> unlockedUsers,
            boolean isSafeMode,
            List<ShortcutInfo> allDeepShortcuts) {

        try {
            if (c.user == null) {
                // User has been deleted, remove the item.
                c.markDeleted("User has been deleted");
                return;
            }

            boolean allowMissingTarget = false;
            switch (c.itemType) {
                case Favorites.ITEM_TYPE_APPLICATION:
                case Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                    Intent intent = c.parseIntent();
                    if (intent == null) {
                        c.markDeleted("Invalid or null intent");
                        return;
                    }

                    int disabledState = mUserManagerState.isUserQuiet(c.serialNumber)
                            ? WorkspaceItemInfo.FLAG_DISABLED_QUIET_USER : 0;
                    ComponentName cn = intent.getComponent();
                    String targetPkg = cn == null ? intent.getPackage() : cn.getPackageName();

                    if (TextUtils.isEmpty(targetPkg)) {
                        c.markDeleted("Shortcuts can't have null package");
                        return;
                    }

                    // If there is no target package, it's an implicit intent
                    // (legacy shortcut) which is always valid
                    boolean validTarget = TextUtils.isEmpty(targetPkg)
                            || mLauncherApps.isPackageEnabled(targetPkg, c.user);

                    // If it's a deep shortcut, we'll use pinned shortcuts to restore it
                    if (cn != null && validTarget && c.itemType
                            != Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                        // If the apk is present and the shortcut points to a specific component.

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
                                        Favorites.INTENT,
                                        intent.toUri(0)).commit();
                                cn = intent.getComponent();
                            } else {
                                c.markDeleted("Unable to find a launch target");
                                return;
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
                                FileLog.d(TAG, "restore started for installing app: " + targetPkg);
                                c.updater().put(Favorites.RESTORED, c.restoreFlag).commit();
                            } else {
                                c.markDeleted("removing app that is not restored and not "
                                        + "installing. package: " + targetPkg);
                                return;
                            }
                        } else if (pmHelper.isAppOnSdcard(targetPkg, c.user)) {
                            // Package is present but not available.
                            disabledState |= WorkspaceItemInfo.FLAG_DISABLED_NOT_AVAILABLE;
                            // Add the icon on the workspace anyway.
                            allowMissingTarget = true;
                        } else if (!isSdCardReady) {
                            // SdCard is not ready yet. Package might get available,
                            // once it is ready.
                            Log.d(TAG, "Missing package, will check later: " + targetPkg);
                            mPendingPackages.add(new PackageUserKey(targetPkg, c.user));
                            // Add the icon on the workspace anyway.
                            allowMissingTarget = true;
                        } else {
                            // Do not wait for external media load anymore.
                            c.markDeleted("Invalid package removed: " + targetPkg);
                            return;
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

                    WorkspaceItemInfo info;
                    if (c.restoreFlag != 0) {
                        // Already verified above that user is same as default user
                        info = c.getRestoredItemInfo(intent);
                    } else if (c.itemType == Favorites.ITEM_TYPE_APPLICATION) {
                        info = c.getAppShortcutInfo(
                                intent, allowMissingTarget, useLowResIcon, false);
                    } else if (c.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                        ShortcutKey key = ShortcutKey.fromIntent(intent, c.user);
                        if (unlockedUsers.get(c.serialNumber)) {
                            ShortcutInfo pinnedShortcut = mShortcutKeyToPinnedShortcuts.get(key);
                            if (pinnedShortcut == null) {
                                // The shortcut is no longer valid.
                                c.markDeleted("Pinned shortcut not found for package: "
                                        + key.getPackageName());
                                return;
                            }
                            info = new WorkspaceItemInfo(pinnedShortcut, mApp.getContext());
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
                        info.options = c.getOptions();

                        // App shortcuts that used to be automatically added to Launcher
                        // didn't always have the correct intent flags set, so do that here
                        if (intent.getAction() != null
                                && intent.getCategories() != null
                                && intent.getAction().equals(Intent.ACTION_MAIN)
                                && intent.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        }
                    }

                    if (info != null) {
                        if (info.itemType != Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                            // Skip deep shortcuts; their title and icons have already been
                            // loaded above.
                            iconRequestInfos.add(c.createIconRequestInfo(info, useLowResIcon));
                        }

                        c.applyCommonProperties(info);

                        info.intent = intent;
                        info.rank = c.getRank();
                        info.spanX = 1;
                        info.spanY = 1;
                        info.runtimeStatusFlags |= disabledState;
                        if (isSafeMode && !isSystemApp(mApp.getContext(), intent)) {
                            info.runtimeStatusFlags |= FLAG_DISABLED_SAFEMODE;
                        }
                        LauncherActivityInfo activityInfo = c.getLauncherActivityInfo();
                        if (activityInfo != null) {
                            info.setProgressLevel(
                                    PackageManagerHelper.getLoadingProgress(activityInfo),
                                    PackageInstallInfo.STATUS_INSTALLED_DOWNLOADING);
                        }

                        if (c.restoreFlag != 0 && !TextUtils.isEmpty(targetPkg)) {
                            tempPackageKey.update(targetPkg, c.user);
                            SessionInfo si = installingPkgs.get(tempPackageKey);
                            if (si == null) {
                                info.runtimeStatusFlags
                                        &= ~ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE;
                            } else if (activityInfo == null) {
                                int installProgress = (int) (si.getProgress() * 100);

                                info.setProgressLevel(installProgress,
                                        PackageInstallInfo.STATUS_INSTALLING);
                            }
                        }

                        c.checkAndAddItem(info, mBgDataModel, memoryLogger);
                    } else {
                        throw new RuntimeException("Unexpected null WorkspaceItemInfo");
                    }
                    break;

                case Favorites.ITEM_TYPE_FOLDER:
                case Favorites.ITEM_TYPE_APP_PAIR:
                    FolderInfo folderInfo = mBgDataModel.findOrMakeFolder(c.id);
                    c.applyCommonProperties(folderInfo);

                    folderInfo.itemType = c.itemType;
                    // Do not trim the folder label, as is was set by the user.
                    folderInfo.title = c.getString(c.mTitleIndex);
                    folderInfo.spanX = 1;
                    folderInfo.spanY = 1;
                    folderInfo.options = c.getOptions();

                    // no special handling required for restored folders
                    c.markRestored();

                    c.checkAndAddItem(folderInfo, mBgDataModel, memoryLogger);
                    break;

                case Favorites.ITEM_TYPE_APPWIDGET:
                    if (WidgetsModel.GO_DISABLE_WIDGETS) {
                        c.markDeleted("Only legacy shortcuts can have null package");
                        return;
                    }
                    // Follow through
                case Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
                    // Read all Launcher-specific widget details
                    boolean customWidget = c.itemType
                            == Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;

                    int appWidgetId = c.getAppWidgetId();
                    String savedProvider = c.getAppWidgetProvider();
                    final ComponentName component;

                    if ((c.getOptions() & LauncherAppWidgetInfo.OPTION_SEARCH_WIDGET) != 0) {
                        component  = QsbContainerView.getSearchComponentName(mApp.getContext());
                        if (component == null) {
                            c.markDeleted("Discarding SearchWidget without packagename ");
                            return;
                        }
                    } else {
                        component = ComponentName.unflattenFromString(savedProvider);
                    }
                    final boolean isIdValid =
                            !c.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID);
                    final boolean wasProviderReady =
                            !c.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY);

                    ComponentKey providerKey = new ComponentKey(component, c.user);
                    if (!mWidgetProvidersMap.containsKey(providerKey)) {
                        if (customWidget) {
                            mWidgetProvidersMap.put(providerKey, CustomWidgetManager.INSTANCE
                                    .get(mApp.getContext()).getWidgetProvider(component));
                        } else {
                            mWidgetProvidersMap.put(providerKey,
                                    widgetHelper.findProvider(component, c.user));
                        }
                    }
                    final AppWidgetProviderInfo provider = mWidgetProvidersMap.get(providerKey);

                    final boolean isProviderReady = isValidProvider(provider);
                    if (!isSafeMode && !customWidget && wasProviderReady && !isProviderReady) {
                        c.markDeleted("Deleting widget that isn't installed anymore: " + provider);
                    } else {
                        LauncherAppWidgetInfo appWidgetInfo;
                        if (isProviderReady) {
                            appWidgetInfo =
                                    new LauncherAppWidgetInfo(appWidgetId, provider.provider);

                            // The provider is available. So the widget is either
                            // available or not available. We do not need to track
                            // any future restore updates.
                            int status = c.restoreFlag
                                    & ~LauncherAppWidgetInfo.FLAG_RESTORE_STARTED
                                    & ~LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;
                            if (!wasProviderReady) {
                                // If provider was not previously ready, update status and UI flag.

                                // Id would be valid only if the widget restore broadcast received.
                                if (isIdValid) {
                                    status |= LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
                                }
                            }
                            appWidgetInfo.restoreStatus = status;
                        } else {
                            Log.v(TAG, "Widget restore pending id=" + c.id
                                    + " appWidgetId=" + appWidgetId
                                    + " status=" + c.restoreFlag);
                            appWidgetInfo = new LauncherAppWidgetInfo(appWidgetId, component);
                            appWidgetInfo.restoreStatus = c.restoreFlag;

                            tempPackageKey.update(component.getPackageName(), c.user);
                            SessionInfo si = installingPkgs.get(tempPackageKey);
                            Integer installProgress = si == null
                                    ? null
                                    : (int) (si.getProgress() * 100);

                            if (c.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_RESTORE_STARTED)) {
                                // Restore has started once.
                            } else if (installProgress != null) {
                                // App restore has started. Update the flag
                                appWidgetInfo.restoreStatus
                                        |= LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;
                            } else if (!isSafeMode) {
                                c.markDeleted("Unrestored widget removed: " + component);
                                return;
                            }

                            appWidgetInfo.installProgress =
                                    installProgress == null ? 0 : installProgress;
                        }
                        if (appWidgetInfo.hasRestoreFlag(
                                LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG)) {
                            appWidgetInfo.bindOptions = c.parseIntent();
                        }

                        c.applyCommonProperties(appWidgetInfo);
                        appWidgetInfo.spanX = c.getSpanX();
                        appWidgetInfo.spanY = c.getSpanY();
                        appWidgetInfo.options = c.getOptions();
                        appWidgetInfo.user = c.user;
                        appWidgetInfo.sourceContainer = c.getAppWidgetSource();

                        if (appWidgetInfo.spanX <= 0 || appWidgetInfo.spanY <= 0) {
                            c.markDeleted("Widget has invalid size: "
                                    + appWidgetInfo.spanX + "x" + appWidgetInfo.spanY);
                            return;
                        }
                        LauncherAppWidgetProviderInfo widgetProviderInfo =
                                widgetHelper.getLauncherAppWidgetInfo(appWidgetId,
                                        appWidgetInfo.getTargetComponent());
                        if (widgetProviderInfo != null
                                && (appWidgetInfo.spanX < widgetProviderInfo.minSpanX
                                || appWidgetInfo.spanY < widgetProviderInfo.minSpanY)) {
                            FileLog.d(TAG, "Widget " + widgetProviderInfo.getComponent()
                                    + " minSizes not meet: span=" + appWidgetInfo.spanX
                                    + "x" + appWidgetInfo.spanY + " minSpan="
                                    + widgetProviderInfo.minSpanX + "x"
                                    + widgetProviderInfo.minSpanY);
                            logWidgetInfo(mApp.getInvariantDeviceProfile(),
                                    widgetProviderInfo);
                        }
                        if (!c.isOnWorkspaceOrHotseat()) {
                            c.markDeleted("Widget found where container != CONTAINER_DESKTOP"
                                    + "nor CONTAINER_HOTSEAT - ignoring!");
                            return;
                        }

                        if (!customWidget) {
                            String providerName = appWidgetInfo.providerName.flattenToString();
                            if (!providerName.equals(savedProvider)
                                    || (appWidgetInfo.restoreStatus != c.restoreFlag)) {
                                c.updater()
                                        .put(Favorites.APPWIDGET_PROVIDER,
                                                providerName)
                                        .put(Favorites.RESTORED,
                                                appWidgetInfo.restoreStatus)
                                        .commit();
                            }
                        }

                        if (appWidgetInfo.restoreStatus
                                != LauncherAppWidgetInfo.RESTORE_COMPLETED) {
                            appWidgetInfo.pendingItemInfo = WidgetsModel.newPendingItemInfo(
                                    mApp.getContext(),
                                    appWidgetInfo.providerName,
                                    appWidgetInfo.user);
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
                    mBgDataModel.workspaceItems.remove(mBgDataModel.folders.get(folderId));
                    mBgDataModel.folders.remove(folderId);
                    mBgDataModel.itemsIdMap.remove(folderId);
                }
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
                AppInfo appInfo = new AppInfo(app, user, quietMode);

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

    @SuppressLint("NewApi") // Already added API check.
    private static void logWidgetInfo(InvariantDeviceProfile idp,
            LauncherAppWidgetProviderInfo widgetProviderInfo) {
        Point cellSize = new Point();
        for (DeviceProfile deviceProfile : idp.supportedProfiles) {
            deviceProfile.getCellSize(cellSize);
            FileLog.d(TAG, "DeviceProfile available width: " + deviceProfile.availableWidthPx
                    + ", available height: " + deviceProfile.availableHeightPx
                    + ", cellLayoutBorderSpacePx Horizontal: "
                    + deviceProfile.cellLayoutBorderSpacePx.x
                    + ", cellLayoutBorderSpacePx Vertical: "
                    + deviceProfile.cellLayoutBorderSpacePx.y
                    + ", cellSize: " + cellSize);
        }

        StringBuilder widgetDimension = new StringBuilder();
        widgetDimension.append("Widget dimensions:\n")
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
                .append("\n");
        if (Utilities.ATLEAST_S) {
            widgetDimension.append("targetCellWidth: ")
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
                    .append("\n");
        }
        FileLog.d(TAG, widgetDimension.toString());
    }

    private static void logASplit(String label) {
        if (DEBUG) {
            Log.d(TAG, label);
        }
    }
}
