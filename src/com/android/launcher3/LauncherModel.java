/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.MutableInt;
import android.util.Pair;

import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.PackageInstallerCompat.PackageInstallInfo;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dynamicui.ExtractionUtils;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.FolderIconPreviewVerifier;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.AddWorkspaceItemsTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.CacheDataUpdatedTask;
import com.android.launcher3.model.ExtendedModelTask;
import com.android.launcher3.model.GridSizeMigrationTask;
import com.android.launcher3.model.LoaderCursor;
import com.android.launcher3.model.LoaderResults;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.PackageInstallStateChangedTask;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.model.PackageUpdatedTask;
import com.android.launcher3.model.SdCardAvailableReceiver;
import com.android.launcher3.model.ShortcutsChangedTask;
import com.android.launcher3.model.UserLockStateChangedTask;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.provider.ImportDataTask;
import com.android.launcher3.provider.LauncherDbUtils;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.ManagedProfileHeuristic;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.Provider;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.ViewOnDrawExecutor;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state
 * for the Launcher.
 */
public class LauncherModel extends BroadcastReceiver
        implements LauncherAppsCompat.OnAppsChangedCallbackCompat {
    static final boolean DEBUG_LOADERS = false;
    private static final boolean DEBUG_RECEIVER = false;

    static final String TAG = "Launcher.Model";

    private final MainThreadExecutor mUiExecutor = new MainThreadExecutor();
    @Thunk final LauncherAppState mApp;
    @Thunk final Object mLock = new Object();
    @Thunk LoaderTask mLoaderTask;
    @Thunk boolean mIsLoaderTaskRunning;

    @Thunk static final HandlerThread sWorkerThread = new HandlerThread("launcher-loader");
    static {
        sWorkerThread.start();
    }
    @Thunk static final Handler sWorker = new Handler(sWorkerThread.getLooper());

    // Indicates whether the current model data is valid or not.
    // We start off with everything not loaded. After that, we assume that
    // our monitoring of the package manager provides all updates and we never
    // need to do a requery. This is only ever touched from the loader thread.
    private boolean mModelLoaded;
    public boolean isModelLoaded() {
        synchronized (mLock) {
            return mModelLoaded && mLoaderTask == null;
        }
    }

    @Thunk WeakReference<Callbacks> mCallbacks;

    // < only access in worker thread >
    private final AllAppsList mBgAllAppsList;

    /**
     * All the static data should be accessed on the background thread, A lock should be acquired
     * on this object when accessing any data from this model.
     */
    static final BgDataModel sBgDataModel = new BgDataModel();

    // Runnable to check if the shortcuts permission has changed.
    private final Runnable mShortcutPermissionCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (mModelLoaded) {
                boolean hasShortcutHostPermission =
                        DeepShortcutManager.getInstance(mApp.getContext()).hasHostPermission();
                if (hasShortcutHostPermission != sBgDataModel.hasShortcutHostPermission) {
                    forceReload();
                }
            }
        }
    };

    public interface Callbacks {
        public boolean setLoadOnResume();
        public int getCurrentWorkspaceScreen();
        public void clearPendingBinds();
        public void startBinding();
        public void bindItems(ArrayList<ItemInfo> shortcuts, int start, int end,
                              boolean forceAnimateIcons);
        public void bindScreens(ArrayList<Long> orderedScreenIds);
        public void finishFirstPageBind(ViewOnDrawExecutor executor);
        public void finishBindingItems();
        public void bindAppWidget(LauncherAppWidgetInfo info);
        public void bindAllApplications(ArrayList<AppInfo> apps);
        public void bindAppsAdded(ArrayList<Long> newScreens,
                                  ArrayList<ItemInfo> addNotAnimated,
                                  ArrayList<ItemInfo> addAnimated,
                                  ArrayList<AppInfo> addedApps);
        public void bindAppsUpdated(ArrayList<AppInfo> apps);
        public void bindPromiseAppProgressUpdated(PromiseAppInfo app);
        public void bindShortcutsChanged(ArrayList<ShortcutInfo> updated,
                ArrayList<ShortcutInfo> removed, UserHandle user);
        public void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets);
        public void bindRestoreItemsChange(HashSet<ItemInfo> updates);
        public void bindWorkspaceComponentsRemoved(
                HashSet<String> packageNames, HashSet<ComponentName> components,
                UserHandle user);
        public void bindAppInfosRemoved(ArrayList<AppInfo> appInfos);
        public void notifyWidgetProvidersChanged();
        public void bindAllWidgets(MultiHashMap<PackageItemInfo, WidgetItem> widgets);
        public void onPageBoundSynchronously(int page);
        public void executeOnNextDraw(ViewOnDrawExecutor executor);
        public void bindDeepShortcutMap(MultiHashMap<ComponentKey, String> deepShortcutMap);
    }

    LauncherModel(LauncherAppState app, IconCache iconCache, AppFilter appFilter) {
        mApp = app;
        mBgAllAppsList = new AllAppsList(iconCache, appFilter);
    }

    /** Runs the specified runnable immediately if called from the worker thread, otherwise it is
     * posted on the worker thread handler. */
    private static void runOnWorkerThread(Runnable r) {
        if (sWorkerThread.getThreadId() == Process.myTid()) {
            r.run();
        } else {
            // If we are not on the worker thread, then post to the worker handler
            sWorker.post(r);
        }
    }

    public void setPackageState(PackageInstallInfo installInfo) {
        enqueueModelUpdateTask(new PackageInstallStateChangedTask(installInfo));
    }

    /**
     * Updates the icons and label of all pending icons for the provided package name.
     */
    public void updateSessionDisplayInfo(final String packageName) {
        HashSet<String> packages = new HashSet<>();
        packages.add(packageName);
        enqueueModelUpdateTask(new CacheDataUpdatedTask(
                CacheDataUpdatedTask.OP_SESSION_UPDATE, Process.myUserHandle(), packages));
    }

    /**
     * Adds the provided items to the workspace.
     */
    public void addAndBindAddedWorkspaceItems(
            Provider<List<Pair<ItemInfo, Object>>> appsProvider) {
        enqueueModelUpdateTask(new AddWorkspaceItemsTask(appsProvider));
    }

    public ModelWriter getWriter(boolean hasVerticalHotseat) {
        return new ModelWriter(mApp.getContext(), sBgDataModel, hasVerticalHotseat);
    }

    static void checkItemInfoLocked(
            final long itemId, final ItemInfo item, StackTraceElement[] stackTrace) {
        ItemInfo modelItem = sBgDataModel.itemsIdMap.get(itemId);
        if (modelItem != null && item != modelItem) {
            // check all the data is consistent
            if (modelItem instanceof ShortcutInfo && item instanceof ShortcutInfo) {
                ShortcutInfo modelShortcut = (ShortcutInfo) modelItem;
                ShortcutInfo shortcut = (ShortcutInfo) item;
                if (modelShortcut.title.toString().equals(shortcut.title.toString()) &&
                        modelShortcut.intent.filterEquals(shortcut.intent) &&
                        modelShortcut.id == shortcut.id &&
                        modelShortcut.itemType == shortcut.itemType &&
                        modelShortcut.container == shortcut.container &&
                        modelShortcut.screenId == shortcut.screenId &&
                        modelShortcut.cellX == shortcut.cellX &&
                        modelShortcut.cellY == shortcut.cellY &&
                        modelShortcut.spanX == shortcut.spanX &&
                        modelShortcut.spanY == shortcut.spanY) {
                    // For all intents and purposes, this is the same object
                    return;
                }
            }

            // the modelItem needs to match up perfectly with item if our model is
            // to be consistent with the database-- for now, just require
            // modelItem == item or the equality check above
            String msg = "item: " + ((item != null) ? item.toString() : "null") +
                    "modelItem: " +
                    ((modelItem != null) ? modelItem.toString() : "null") +
                    "Error: ItemInfo passed to checkItemInfo doesn't match original";
            RuntimeException e = new RuntimeException(msg);
            if (stackTrace != null) {
                e.setStackTrace(stackTrace);
            }
            throw e;
        }
    }

    static void checkItemInfo(final ItemInfo item) {
        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        final long itemId = item.id;
        Runnable r = new Runnable() {
            public void run() {
                synchronized (sBgDataModel) {
                    checkItemInfoLocked(itemId, item, stackTrace);
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Update the order of the workspace screens in the database. The array list contains
     * a list of screen ids in the order that they should appear.
     */
    public static void updateWorkspaceScreenOrder(Context context, final ArrayList<Long> screens) {
        final ArrayList<Long> screensCopy = new ArrayList<Long>(screens);
        final ContentResolver cr = context.getContentResolver();
        final Uri uri = LauncherSettings.WorkspaceScreens.CONTENT_URI;

        // Remove any negative screen ids -- these aren't persisted
        Iterator<Long> iter = screensCopy.iterator();
        while (iter.hasNext()) {
            long id = iter.next();
            if (id < 0) {
                iter.remove();
            }
        }

        Runnable r = new Runnable() {
            @Override
            public void run() {
                ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
                // Clear the table
                ops.add(ContentProviderOperation.newDelete(uri).build());
                int count = screensCopy.size();
                for (int i = 0; i < count; i++) {
                    ContentValues v = new ContentValues();
                    long screenId = screensCopy.get(i);
                    v.put(LauncherSettings.WorkspaceScreens._ID, screenId);
                    v.put(LauncherSettings.WorkspaceScreens.SCREEN_RANK, i);
                    ops.add(ContentProviderOperation.newInsert(uri).withValues(v).build());
                }

                try {
                    cr.applyBatch(LauncherProvider.AUTHORITY, ops);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }

                synchronized (sBgDataModel) {
                    sBgDataModel.workspaceScreens.clear();
                    sBgDataModel.workspaceScreens.addAll(screensCopy);
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Set this as the current Launcher activity object for the loader.
     */
    public void initialize(Callbacks callbacks) {
        synchronized (mLock) {
            Preconditions.assertUIThread();
            mCallbacks = new WeakReference<>(callbacks);
        }
    }

    @Override
    public void onPackageChanged(String packageName, UserHandle user) {
        int op = PackageUpdatedTask.OP_UPDATE;
        enqueueModelUpdateTask(new PackageUpdatedTask(op, user, packageName));
    }

    @Override
    public void onPackageRemoved(String packageName, UserHandle user) {
        onPackagesRemoved(user, packageName);
    }

    public void onPackagesRemoved(UserHandle user, String... packages) {
        int op = PackageUpdatedTask.OP_REMOVE;
        enqueueModelUpdateTask(new PackageUpdatedTask(op, user, packages));
    }

    @Override
    public void onPackageAdded(String packageName, UserHandle user) {
        int op = PackageUpdatedTask.OP_ADD;
        enqueueModelUpdateTask(new PackageUpdatedTask(op, user, packageName));
    }

    @Override
    public void onPackagesAvailable(String[] packageNames, UserHandle user,
            boolean replacing) {
        enqueueModelUpdateTask(
                new PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, user, packageNames));
    }

    @Override
    public void onPackagesUnavailable(String[] packageNames, UserHandle user,
            boolean replacing) {
        if (!replacing) {
            enqueueModelUpdateTask(new PackageUpdatedTask(
                    PackageUpdatedTask.OP_UNAVAILABLE, user, packageNames));
        }
    }

    @Override
    public void onPackagesSuspended(String[] packageNames, UserHandle user) {
        enqueueModelUpdateTask(new PackageUpdatedTask(
                PackageUpdatedTask.OP_SUSPEND, user, packageNames));
    }

    @Override
    public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {
        enqueueModelUpdateTask(new PackageUpdatedTask(
                PackageUpdatedTask.OP_UNSUSPEND, user, packageNames));
    }

    @Override
    public void onShortcutsChanged(String packageName, List<ShortcutInfoCompat> shortcuts,
            UserHandle user) {
        enqueueModelUpdateTask(new ShortcutsChangedTask(packageName, shortcuts, user, true));
    }

    public void updatePinnedShortcuts(String packageName, List<ShortcutInfoCompat> shortcuts,
            UserHandle user) {
        enqueueModelUpdateTask(new ShortcutsChangedTask(packageName, shortcuts, user, false));
    }

    /**
     * Call from the handler for ACTION_PACKAGE_ADDED, ACTION_PACKAGE_REMOVED and
     * ACTION_PACKAGE_CHANGED.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (DEBUG_RECEIVER) Log.d(TAG, "onReceive intent=" + intent);

        final String action = intent.getAction();
        if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            // If we have changed locale we need to clear out the labels in all apps/workspace.
            forceReload();
        } else if (Intent.ACTION_MANAGED_PROFILE_ADDED.equals(action)
                || Intent.ACTION_MANAGED_PROFILE_REMOVED.equals(action)) {
            UserManagerCompat.getInstance(context).enableAndResetCache();
            forceReload();
        } else if (Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action) ||
                Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action) ||
                Intent.ACTION_MANAGED_PROFILE_UNLOCKED.equals(action)) {
            UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
            if (user != null) {
                if (Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action) ||
                        Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action)) {
                    enqueueModelUpdateTask(new PackageUpdatedTask(
                            PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE, user));
                }

                // ACTION_MANAGED_PROFILE_UNAVAILABLE sends the profile back to locked mode, so
                // we need to run the state change task again.
                if (Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action) ||
                        Intent.ACTION_MANAGED_PROFILE_UNLOCKED.equals(action)) {
                    enqueueModelUpdateTask(new UserLockStateChangedTask(user));
                }
            }
        } else if (Intent.ACTION_WALLPAPER_CHANGED.equals(action)) {
            ExtractionUtils.startColorExtractionServiceIfNecessary(context);
        }
    }

    /**
     * Reloads the workspace items from the DB and re-binds the workspace. This should generally
     * not be called as DB updates are automatically followed by UI update
     */
    public void forceReload() {
        synchronized (mLock) {
            // Stop any existing loaders first, so they don't set mModelLoaded to true later
            stopLoaderLocked();
            mModelLoaded = false;
        }

        // Do this here because if the launcher activity is running it will be restarted.
        // If it's not running startLoaderFromBackground will merely tell it that it needs
        // to reload.
        startLoaderFromBackground();
    }

    /**
     * When the launcher is in the background, it's possible for it to miss paired
     * configuration changes.  So whenever we trigger the loader from the background
     * tell the launcher that it needs to re-run the loader when it comes back instead
     * of doing it now.
     */
    public void startLoaderFromBackground() {
        Callbacks callbacks = getCallback();
        if (callbacks != null) {
            // Only actually run the loader if they're not paused.
            if (!callbacks.setLoadOnResume()) {
                startLoader(callbacks.getCurrentWorkspaceScreen());
            }
        }
    }

    /**
     * If there is already a loader task running, tell it to stop.
     */
    private void stopLoaderLocked() {
        LoaderTask oldTask = mLoaderTask;
        if (oldTask != null) {
            oldTask.stopLocked();
        }
    }

    public boolean isCurrentCallbacks(Callbacks callbacks) {
        return (mCallbacks != null && mCallbacks.get() == callbacks);
    }

    /**
     * Starts the loader. Tries to bind {@params synchronousBindPage} synchronously if possible.
     * @return true if the page could be bound synchronously.
     */
    public boolean startLoader(int synchronousBindPage) {
        // Enable queue before starting loader. It will get disabled in Launcher#finishBindingItems
        InstallShortcutReceiver.enableInstallQueue(InstallShortcutReceiver.FLAG_LOADER_RUNNING);
        synchronized (mLock) {
            // Don't bother to start the thread if we know it's not going to do anything
            if (mCallbacks != null && mCallbacks.get() != null) {
                final Callbacks oldCallbacks = mCallbacks.get();
                // Clear any pending bind-runnables from the synchronized load process.
                mUiExecutor.execute(new Runnable() {
                            public void run() {
                                oldCallbacks.clearPendingBinds();
                            }
                        });

                // If there is already one running, tell it to stop.
                stopLoaderLocked();
                LoaderResults loaderResults = new LoaderResults(mApp, sBgDataModel,
                        mBgAllAppsList, synchronousBindPage, mCallbacks);
                if (synchronousBindPage != PagedView.INVALID_RESTORE_PAGE
                        && mModelLoaded && !mIsLoaderTaskRunning) {

                    // Divide the set of loaded items into those that we are binding synchronously,
                    // and everything else that is to be bound normally (asynchronously).
                    loaderResults.bindWorkspace();
                    // For now, continue posting the binding of AllApps as there are other
                    // issues that arise from that.
                    loaderResults.bindAllApps();
                    loaderResults.bindDeepShortcuts();
                    loaderResults.bindWidgets();
                    return true;
                } else {
                    mLoaderTask = new LoaderTask(mApp, mBgAllAppsList, sBgDataModel, loaderResults);
                    sWorker.post(mLoaderTask);
                }
            }
        }
        return false;
    }

    public void stopLoader() {
        synchronized (mLock) {
            if (mLoaderTask != null) {
                mLoaderTask.stopLocked();
            }
        }
    }

    /**
     * Loads the workspace screen ids in an ordered list.
     */
    public static ArrayList<Long> loadWorkspaceScreensDb(Context context) {
        final ContentResolver contentResolver = context.getContentResolver();
        final Uri screensUri = LauncherSettings.WorkspaceScreens.CONTENT_URI;

        // Get screens ordered by rank.
        return LauncherDbUtils.getScreenIdsFromCursor(contentResolver.query(
                screensUri, null, null, null, LauncherSettings.WorkspaceScreens.SCREEN_RANK));
    }

    public void onInstallSessionCreated(final PackageInstallInfo sessionInfo) {
        enqueueModelUpdateTask(new ExtendedModelTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                apps.addPromiseApp(app.getContext(), sessionInfo);
                if (!apps.added.isEmpty()) {
                    final ArrayList<AppInfo> arrayList = new ArrayList<>(apps.added);
                    apps.added.clear();
                    scheduleCallbackTask(new CallbackTask() {
                        @Override
                        public void execute(Callbacks callbacks) {
                            callbacks.bindAppsAdded(null, null, null, arrayList);
                        }
                    });
                }
            }
        });
    }

    public class LoaderTransaction implements AutoCloseable {

        private final LoaderTask mTask;

        private LoaderTransaction(LoaderTask task) throws CancellationException {
            synchronized (mLock) {
                if (mLoaderTask != task) {
                    throw new CancellationException("Loader already stopped");
                }
                mTask = task;
                mIsLoaderTaskRunning = true;
                mModelLoaded = false;
            }
        }

        public void commit() {
            synchronized (mLock) {
                // Everything loaded bind the data.
                mModelLoaded = true;
            }
        }

        @Override
        public void close() {
            synchronized (mLock) {
                // If we are still the last one to be scheduled, remove ourselves.
                if (mLoaderTask == mTask) {
                    mLoaderTask = null;
                }
                mIsLoaderTaskRunning = false;
            }
        }
    }

    public LoaderTransaction beginLoader(LoaderTask task) throws CancellationException {
        return new LoaderTransaction(task);
    }

    /**
     * Runnable for the thread that loads the contents of the launcher:
     *   - workspace icons
     *   - widgets
     *   - all apps icons
     *   - deep shortcuts within apps
     */
    private static class LoaderTask implements Runnable {
        private final LauncherAppState mApp;
        private final AllAppsList mBgAllAppsList;
        private final BgDataModel mBgDataModel;

        private final LoaderResults mResults;

        private final LauncherAppsCompat mLauncherApps;
        private final UserManagerCompat mUserManager;
        private final DeepShortcutManager mShortcutManager;
        private final PackageInstallerCompat mPackageInstaller;
        private final AppWidgetManagerCompat mAppWidgetManager;
        private final IconCache mIconCache;

        private boolean mStopped;

        LoaderTask(LauncherAppState app, AllAppsList bgAllAppsList, BgDataModel dataModel,
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

        private synchronized void waitForIdle() {
            // Wait until the either we're stopped or the other threads are done.
            // This way we don't start loading all apps until the workspace has settled
            // down.
            LooperIdleLock idleLock = new LooperIdleLock(this, Looper.getMainLooper());
            // Just in case mFlushingWorkerThread changes but we aren't woken up,
            // wait no longer than 1sec at a time
            while (!mStopped && idleLock.awaitLocked(1000));
        }

        private synchronized void verifyNotStopped() throws CancellationException {
            if (mStopped) {
                throw new CancellationException("Loader stopped");
            }
        }

        public void run() {
            synchronized (this) {
                // Skip fast if we are already stopped.
                if (mStopped) {
                    return;
                }
            }

            try (LoaderTransaction transaction = mApp.getModel().beginLoader(this)) {
                long now = 0;
                if (DEBUG_LOADERS) Log.d(TAG, "step 1.1: loading workspace");
                loadWorkspace();

                verifyNotStopped();
                if (DEBUG_LOADERS) Log.d(TAG, "step 1.2: bind workspace workspace");
                mResults.bindWorkspace();

                // Take a break
                if (DEBUG_LOADERS) {
                    Log.d(TAG, "step 1 completed, wait for idle");
                    now = SystemClock.uptimeMillis();
                }
                waitForIdle();
                if (DEBUG_LOADERS) Log.d(TAG, "Waited " + (SystemClock.uptimeMillis() - now) + "ms");
                verifyNotStopped();

                // second step
                if (DEBUG_LOADERS) Log.d(TAG, "step 2.1: loading all apps");
                loadAllApps();

                if (DEBUG_LOADERS) Log.d(TAG, "step 2.2: Binding all apps");
                verifyNotStopped();
                mResults.bindAllApps();

                verifyNotStopped();
                if (DEBUG_LOADERS) Log.d(TAG, "step 2.3: Update icon cache");
                updateIconCache();

                // Take a break
                if (DEBUG_LOADERS) {
                    Log.d(TAG, "step 2 completed, wait for idle");
                    now = SystemClock.uptimeMillis();
                }
                waitForIdle();
                if (DEBUG_LOADERS) Log.d(TAG, "Waited " + (SystemClock.uptimeMillis() - now) + "ms");
                verifyNotStopped();

                // third step
                if (DEBUG_LOADERS) Log.d(TAG, "step 3.1: loading deep shortcuts");
                loadDeepShortcuts();

                verifyNotStopped();
                if (DEBUG_LOADERS) Log.d(TAG, "step 3.2: bind deep shortcuts");
                mResults.bindDeepShortcuts();

                // Take a break
                if (DEBUG_LOADERS) Log.d(TAG, "step 3 completed, wait for idle");
                waitForIdle();
                verifyNotStopped();

                // fourth step
                if (DEBUG_LOADERS) Log.d(TAG, "step 4.1: loading widgets");
                mBgDataModel.widgetsModel.update(mApp, null);

                verifyNotStopped();
                if (DEBUG_LOADERS) Log.d(TAG, "step 4.2: Binding widgets");
                mResults.bindWidgets();

                transaction.commit();
            } catch (CancellationException e) {
              // Loader stopped, ignore
            }
        }

        public synchronized void stopLocked() {
            mStopped = true;
            this.notify();
        }

        private void loadWorkspace() {
            if (LauncherAppState.PROFILE_STARTUP) {
                Trace.beginSection("Loading Workspace");
            }

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

            if (!clearDb && GridSizeMigrationTask.ENABLED &&
                    !GridSizeMigrationTask.migrateGridIfNeeded(context)) {
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

                final HashMap<String, Integer> installingPkgs =
                        mPackageInstaller.updateAndGetActiveSessionCache();
                mBgDataModel.workspaceScreens.addAll(loadWorkspaceScreensDb(context));

                Map<ShortcutKey, ShortcutInfoCompat> shortcutKeyToPinnedShortcuts = new HashMap<>();
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
                            List<ShortcutInfoCompat> pinnedShortcuts =
                                    mShortcutManager.queryForPinnedShortcuts(null, user);
                            if (mShortcutManager.wasLastCallSuccess()) {
                                for (ShortcutInfoCompat shortcut : pinnedShortcuts) {
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

                    ShortcutInfo info;
                    LauncherAppWidgetInfo appWidgetInfo;
                    Intent intent;
                    String targetPkg;

                    FolderIconPreviewVerifier verifier =
                            new FolderIconPreviewVerifier(mApp.getInvariantDeviceProfile());
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
                                        ShortcutInfo.FLAG_DISABLED_QUIET_USER : 0;
                                ComponentName cn = intent.getComponent();
                                targetPkg = cn == null ? intent.getPackage() : cn.getPackageName();

                                if (!Process.myUserHandle().equals(c.user)) {
                                    if (c.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                                        c.markDeleted("Legacy shortcuts are only allowed for default user");
                                        continue;
                                    } else if (c.restoreFlag != 0) {
                                        // Don't restore items for other profiles.
                                        c.markDeleted("Restore from managed profile not supported");
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
                                        if (c.hasRestoreFlag(ShortcutInfo.FLAG_AUTOINSTALL_ICON)) {
                                            // We allow auto install apps to have their intent
                                            // updated after an install.
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
                                        } else {
                                            // The app is installed but the component is no
                                            // longer available.
                                            c.markDeleted("Invalid component removed: " + cn);
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

                                        if (c.hasRestoreFlag(ShortcutInfo.FLAG_RESTORE_STARTED)) {
                                            // Restore has started once.
                                        } else if (installingPkgs.containsKey(targetPkg)) {
                                            // App restore has started. Update the flag
                                            c.restoreFlag |= ShortcutInfo.FLAG_RESTORE_STARTED;
                                            c.updater().commit();
                                        } else {
                                            c.markDeleted("Unrestored app removed: " + targetPkg);
                                            continue;
                                        }
                                    } else if (pmHelper.isAppOnSdcard(targetPkg, c.user)) {
                                        // Package is present but not available.
                                        disabledState |= ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE;
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

                                if (validTarget) {
                                    // The shortcut points to a valid target (either no target
                                    // or something which is ready to be used)
                                    c.markRestored();
                                }

                                boolean useLowResIcon = !c.isOnWorkspaceOrHotseat() &&
                                        !verifier.isItemInPreview(c.getInt(rankIndex));

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
                                        ShortcutInfoCompat pinnedShortcut =
                                                shortcutKeyToPinnedShortcuts.get(key);
                                        if (pinnedShortcut == null) {
                                            // The shortcut is no longer valid.
                                            c.markDeleted("Pinned shortcut not found");
                                            continue;
                                        }
                                        info = new ShortcutInfo(pinnedShortcut, context);
                                        info.iconBitmap = LauncherIcons
                                                .createShortcutIcon(pinnedShortcut, context);
                                        if (pmHelper.isAppSuspended(
                                                pinnedShortcut.getPackage(), info.user)) {
                                            info.isDisabled |= ShortcutInfo.FLAG_DISABLED_SUSPENDED;
                                        }
                                        intent = info.intent;
                                    } else {
                                        // Create a shortcut info in disabled mode for now.
                                        info = c.loadSimpleShortcut();
                                        info.isDisabled |= ShortcutInfo.FLAG_DISABLED_LOCKED_USER;
                                    }
                                } else { // item type == ITEM_TYPE_SHORTCUT
                                    info = c.loadSimpleShortcut();

                                    // Shortcuts are only available on the primary profile
                                    if (!TextUtils.isEmpty(targetPkg)
                                            && pmHelper.isAppSuspended(targetPkg, c.user)) {
                                        disabledState |= ShortcutInfo.FLAG_DISABLED_SUSPENDED;
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
                                    info.isDisabled |= disabledState;
                                    if (isSafeMode && !Utilities.isSystemApp(context, intent)) {
                                        info.isDisabled |= ShortcutInfo.FLAG_DISABLED_SAFEMODE;
                                    }

                                    if (c.restoreFlag != 0 && !TextUtils.isEmpty(targetPkg)) {
                                        Integer progress = installingPkgs.get(targetPkg);
                                        if (progress != null) {
                                            info.setInstallProgress(progress);
                                        } else {
                                            info.status &= ~ShortcutInfo.FLAG_INSTALL_SESSION_ACTIVE;
                                        }
                                    }

                                    c.checkAndAddItem(info, mBgDataModel);
                                } else {
                                    throw new RuntimeException("Unexpected null ShortcutInfo");
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
                                                ~LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;
                                        if (!wasProviderReady) {
                                            // If provider was not previously ready, update the
                                            // status and UI flag.

                                            // Id would be valid only if the widget restore broadcast was received.
                                            if (isIdValid) {
                                                status |= LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
                                            } else {
                                                status &= ~LauncherAppWidgetInfo
                                                        .FLAG_PROVIDER_NOT_READY;
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
                                        Integer installProgress = installingPkgs.get(component.getPackageName());

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
                    ArrayList<Long> deletedFolderIds = (ArrayList<Long>) LauncherSettings.Settings
                            .call(contentResolver,
                                    LauncherSettings.Settings.METHOD_DELETE_EMPTY_FOLDERS)
                            .getSerializable(LauncherSettings.Settings.EXTRA_VALUE);
                    for (long folderId : deletedFolderIds) {
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

                FolderIconPreviewVerifier verifier =
                        new FolderIconPreviewVerifier(mApp.getInvariantDeviceProfile());
                // Sort the folder items and make sure all items in the preview are high resolution.
                for (FolderInfo folder : mBgDataModel.folders) {
                    Collections.sort(folder.contents, Folder.ITEM_POS_COMPARATOR);
                    verifier.setFolderInfo(folder);

                    int numItemsInPreview = 0;
                    for (ShortcutInfo info : folder.contents) {
                        if (info.usingLowResIcon
                                && info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                                && verifier.isItemInPreview(info.rank)) {
                            mIconCache.getTitleAndIcon(info, false);
                            numItemsInPreview++;
                        }

                        if (numItemsInPreview >= FolderIcon.NUM_ITEMS_IN_PREVIEW) {
                            break;
                        }
                    }
                }

                c.commitRestoredItems();
                if (!isSdCardReady && !pendingPackages.isEmpty()) {
                    context.registerReceiver(
                            new SdCardAvailableReceiver(mApp, pendingPackages),
                            new IntentFilter(Intent.ACTION_BOOT_COMPLETED),
                            null,
                            sWorker);
                }

                // Remove any empty screens
                ArrayList<Long> unusedScreens = new ArrayList<>(mBgDataModel.workspaceScreens);
                for (ItemInfo item: mBgDataModel.itemsIdMap) {
                    long screenId = item.screenId;
                    if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                            unusedScreens.contains(screenId)) {
                        unusedScreens.remove(screenId);
                    }
                }

                // If there are any empty screens remove them, and update.
                if (unusedScreens.size() != 0) {
                    mBgDataModel.workspaceScreens.removeAll(unusedScreens);
                    updateWorkspaceScreenOrder(context, mBgDataModel.workspaceScreens);
                }
            }
            if (LauncherAppState.PROFILE_STARTUP) {
                Trace.endSection();
            }
        }

        private void updateIconCache() {
            // Ignore packages which have a promise icon.
            HashSet<String> packagesToIgnore = new HashSet<>();
            synchronized (mBgDataModel) {
                for (ItemInfo info : mBgDataModel.itemsIdMap) {
                    if (info instanceof ShortcutInfo) {
                        ShortcutInfo si = (ShortcutInfo) info;
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
            mIconCache.updateDbIcons(packagesToIgnore);
        }

        private void loadAllApps() {
            final long loadTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

            final List<UserHandle> profiles = mUserManager.getUserProfiles();

            // Clear the list of apps
            mBgAllAppsList.clear();
            for (UserHandle user : profiles) {
                // Query for the set of apps
                final long qiaTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;
                final List<LauncherActivityInfo> apps = mLauncherApps.getActivityList(null, user);
                if (DEBUG_LOADERS) {
                    Log.d(TAG, "getActivityList took "
                            + (SystemClock.uptimeMillis()-qiaTime) + "ms for user " + user);
                    Log.d(TAG, "getActivityList got " + apps.size() + " apps for user " + user);
                }
                // Fail if we don't have any apps
                // TODO: Fix this. Only fail for the current user.
                if (apps == null || apps.isEmpty()) {
                    return;
                }
                boolean quietMode = mUserManager.isQuietModeEnabled(user);
                // Create the ApplicationInfos
                for (int i = 0; i < apps.size(); i++) {
                    LauncherActivityInfo app = apps.get(i);
                    // This builds the icon bitmaps.
                    mBgAllAppsList.add(new AppInfo(app, user, quietMode), app);
                }

                ManagedProfileHeuristic.onAllAppsLoaded(mApp.getContext(), apps, user);
            }

            if (FeatureFlags.LAUNCHER3_PROMISE_APPS_IN_ALL_APPS) {
                // get all active sessions and add them to the all apps list
                for (PackageInstaller.SessionInfo info :
                        mPackageInstaller.getAllVerifiedSessions()) {
                    mBgAllAppsList.addPromiseApp(mApp.getContext(),
                            PackageInstallInfo.fromInstallingState(info));
                }
            }

            mBgAllAppsList.added = new ArrayList<>();
            if (DEBUG_LOADERS) {
                Log.d(TAG, "All apps loaded in in "
                        + (SystemClock.uptimeMillis() - loadTime) + "ms");
            }
        }

        private void loadDeepShortcuts() {
            mBgDataModel.deepShortcutMap.clear();
            mBgDataModel.hasShortcutHostPermission = mShortcutManager.hasHostPermission();
            if (mBgDataModel.hasShortcutHostPermission) {
                for (UserHandle user : mUserManager.getUserProfiles()) {
                    if (mUserManager.isUserUnlocked(user)) {
                        List<ShortcutInfoCompat> shortcuts =
                                mShortcutManager.queryForAllShortcuts(user);
                        mBgDataModel.updateDeepShortcutMap(null, user, shortcuts);
                    }
                }
            }
        }
    }

    /**
     * Refreshes the cached shortcuts if the shortcut permission has changed.
     * Current implementation simply reloads the workspace, but it can be optimized to
     * use partial updates similar to {@link UserManagerCompat}
     */
    public void refreshShortcutsIfRequired() {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            sWorker.removeCallbacks(mShortcutPermissionCheckRunnable);
            sWorker.post(mShortcutPermissionCheckRunnable);
        }
    }

    /**
     * Called when the icons for packages have been updated in the icon cache.
     */
    public void onPackageIconsUpdated(HashSet<String> updatedPackages, UserHandle user) {
        // If any package icon has changed (app was updated while launcher was dead),
        // update the corresponding shortcuts.
        enqueueModelUpdateTask(new CacheDataUpdatedTask(
                CacheDataUpdatedTask.OP_CACHE_UPDATE, user, updatedPackages));
    }

    public void enqueueModelUpdateTask(BaseModelUpdateTask task) {
        task.init(this);
        runOnWorkerThread(task);
    }

    /**
     * A task to be executed on the current callbacks on the UI thread.
     * If there is no current callbacks, the task is ignored.
     */
    public interface CallbackTask {

        void execute(Callbacks callbacks);
    }

    /**
     * A runnable which changes/updates the data model of the launcher based on certain events.
     */
    public static abstract class BaseModelUpdateTask implements Runnable {

        private LauncherModel mModel;
        private Executor mUiExecutor;

        /* package private */
        void init(LauncherModel model) {
            mModel = model;
            mUiExecutor = mModel.mUiExecutor;
        }

        @Override
        public final void run() {
            if (!mModel.mModelLoaded) {
                if (DEBUG_LOADERS) {
                    Log.d(TAG, "Ignoring model task since loader is pending=" + this);
                }
                // Loader has not yet run.
                return;
            }
            execute(mModel.mApp, sBgDataModel, mModel.mBgAllAppsList);
        }

        /**
         * Execute the actual task. Called on the worker thread.
         */
        public abstract void execute(
                LauncherAppState app, BgDataModel dataModel, AllAppsList apps);

        /**
         * Schedules a {@param task} to be executed on the current callbacks.
         */
        public final void scheduleCallbackTask(final CallbackTask task) {
            final Callbacks callbacks = mModel.getCallback();
            mUiExecutor.execute(new Runnable() {
                public void run() {
                    Callbacks cb = mModel.getCallback();
                    if (callbacks == cb && cb != null) {
                        task.execute(callbacks);
                    }
                }
            });
        }

        public ModelWriter getModelWriter() {
            // Updates from model task, do not deal with icon position in hotseat.
            return mModel.getWriter(false /* hasVerticalHotseat */);
        }
    }

    public void updateAndBindShortcutInfo(final ShortcutInfo si, final ShortcutInfoCompat info) {
        updateAndBindShortcutInfo(new Provider<ShortcutInfo>() {
            @Override
            public ShortcutInfo get() {
                si.updateFromDeepShortcutInfo(info, mApp.getContext());
                si.iconBitmap = LauncherIcons.createShortcutIcon(info, mApp.getContext());
                return si;
            }
        });
    }

    /**
     * Utility method to update a shortcut on the background thread.
     */
    public void updateAndBindShortcutInfo(final Provider<ShortcutInfo> shortcutProvider) {
        enqueueModelUpdateTask(new ExtendedModelTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                ShortcutInfo info = shortcutProvider.get();
                ArrayList<ShortcutInfo> update = new ArrayList<>();
                update.add(info);
                bindUpdatedShortcuts(update, info.user);
            }
        });
    }

    public void refreshAndBindWidgetsAndShortcuts(@Nullable final PackageUserKey packageUser) {
        enqueueModelUpdateTask(new ExtendedModelTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                dataModel.widgetsModel.update(app, packageUser);
                bindUpdatedWidgets(dataModel);
            }
        });
    }

    static boolean isValidProvider(AppWidgetProviderInfo provider) {
        return (provider != null) && (provider.provider != null)
                && (provider.provider.getPackageName() != null);
    }

    public void dumpState(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args.length > 0 && TextUtils.equals(args[0], "--all")) {
            writer.println(prefix + "All apps list: size=" + mBgAllAppsList.data.size());
            for (AppInfo info : mBgAllAppsList.data) {
                writer.println(prefix + "   title=\"" + info.title + "\" iconBitmap=" + info.iconBitmap
                        + " componentName=" + info.componentName.getPackageName());
            }
        }
        sBgDataModel.dump(prefix, fd, writer, args);
    }

    public Callbacks getCallback() {
        return mCallbacks != null ? mCallbacks.get() : null;
    }

    /**
     * @return the looper for the worker thread which can be used to start background tasks.
     */
    public static Looper getWorkerLooper() {
        return sWorkerThread.getLooper();
    }
}
