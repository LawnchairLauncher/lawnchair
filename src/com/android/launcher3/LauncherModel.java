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

import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.PackageInstallerCompat.PackageInstallInfo;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.ProviderConfig;
import com.android.launcher3.dynamicui.ExtractionUtils;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.AddWorkspaceItemsTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.CacheDataUpdatedTask;
import com.android.launcher3.model.ExtendedModelTask;
import com.android.launcher3.model.GridSizeMigrationTask;
import com.android.launcher3.model.LoaderCursor;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.PackageInstallStateChangedTask;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.model.PackageUpdatedTask;
import com.android.launcher3.model.SdCardAvailableReceiver;
import com.android.launcher3.model.ShortcutsChangedTask;
import com.android.launcher3.model.UserLockStateChangedTask;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.provider.ImportDataTask;
import com.android.launcher3.provider.LauncherDbUtils;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ComponentKey;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final int ITEMS_CHUNK = 6; // batch size for the workspace icons
    private static final long INVALID_SCREEN_ID = -1L;

    @Thunk final LauncherAppState mApp;
    @Thunk final Object mLock = new Object();
    @Thunk DeferredHandler mHandler = new DeferredHandler();
    @Thunk LoaderTask mLoaderTask;
    @Thunk boolean mIsLoaderTaskRunning;
    @Thunk boolean mHasLoaderCompletedOnce;
    @Thunk boolean mIsManagedHeuristicAppsUpdated;

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

    /**
     * Set of runnables to be called on the background thread after the workspace binding
     * is complete.
     */
    static final ArrayList<Runnable> mBindCompleteRunnables = new ArrayList<Runnable>();

    @Thunk WeakReference<Callbacks> mCallbacks;

    // < only access in worker thread >
    private final AllAppsList mBgAllAppsList;
    // Entire list of widgets.
    private final WidgetsModel mBgWidgetsModel;

    private boolean mHasShortcutHostPermission;
    // Runnable to check if the shortcuts permission has changed.
    private final Runnable mShortcutPermissionCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (mModelLoaded) {
                boolean hasShortcutHostPermission =
                        DeepShortcutManager.getInstance(mApp.getContext()).hasHostPermission();
                if (hasShortcutHostPermission != mHasShortcutHostPermission) {
                    forceReload();
                }
            }
        }
    };

    /**
     * All the static data should be accessed on the background thread, A lock should be acquired
     * on this object when accessing any data from this model.
     */
    static final BgDataModel sBgDataModel = new BgDataModel();

    // </ only access in worker thread >

    private final IconCache mIconCache;

    private final LauncherAppsCompat mLauncherApps;
    private final UserManagerCompat mUserManager;

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
        Context context = app.getContext();
        mApp = app;
        mBgAllAppsList = new AllAppsList(iconCache, appFilter);
        mBgWidgetsModel = new WidgetsModel(iconCache, appFilter);
        mIconCache = iconCache;

        mLauncherApps = LauncherAppsCompat.getInstance(context);
        mUserManager = UserManagerCompat.getInstance(context);
    }

    /** Runs the specified runnable immediately if called from the main thread, otherwise it is
     * posted on the main thread handler. */
    private void runOnMainThread(Runnable r) {
        if (sWorkerThread.getThreadId() == Process.myTid()) {
            // If we are on the worker thread, post onto the main handler
            mHandler.post(r);
        } else {
            r.run();
        }
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
    public void addAndBindAddedWorkspaceItems(List<ItemInfo> workspaceApps) {
        addAndBindAddedWorkspaceItems(Provider.of(workspaceApps));
    }

    /**
     * Adds the provided items to the workspace.
     */
    public void addAndBindAddedWorkspaceItems(
            Provider<List<ItemInfo>> appsProvider) {
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
            // Remove any queued UI runnables
            mHandler.cancelAll();
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
        InstallShortcutReceiver.enableInstallQueue();
        synchronized (mLock) {
            // Don't bother to start the thread if we know it's not going to do anything
            if (mCallbacks != null && mCallbacks.get() != null) {
                final Callbacks oldCallbacks = mCallbacks.get();
                // Clear any pending bind-runnables from the synchronized load process.
                runOnMainThread(new Runnable() {
                    public void run() {
                        oldCallbacks.clearPendingBinds();
                    }
                });

                // If there is already one running, tell it to stop.
                stopLoaderLocked();
                mLoaderTask = new LoaderTask(mApp.getContext(), synchronousBindPage);
                if (synchronousBindPage != PagedView.INVALID_RESTORE_PAGE
                        && mModelLoaded && !mIsLoaderTaskRunning) {
                    mLoaderTask.runBindSynchronousPage(synchronousBindPage);
                    return true;
                } else {
                    sWorkerThread.setPriority(Thread.NORM_PRIORITY);
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

    /**
     * Runnable for the thread that loads the contents of the launcher:
     *   - workspace icons
     *   - widgets
     *   - all apps icons
     *   - deep shortcuts within apps
     */
    private class LoaderTask implements Runnable {
        private Context mContext;
        private int mPageToBindFirst;

        @Thunk boolean mIsLoadingAndBindingWorkspace;
        private boolean mStopped;
        @Thunk boolean mLoadAndBindStepFinished;

        LoaderTask(Context context, int pageToBindFirst) {
            mContext = context;
            mPageToBindFirst = pageToBindFirst;
        }

        private void waitForIdle() {
            // Wait until the either we're stopped or the other threads are done.
            // This way we don't start loading all apps until the workspace has settled
            // down.
            synchronized (LoaderTask.this) {
                final long workspaceWaitTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

                mHandler.postIdle(new Runnable() {
                        public void run() {
                            synchronized (LoaderTask.this) {
                                mLoadAndBindStepFinished = true;
                                if (DEBUG_LOADERS) {
                                    Log.d(TAG, "done with previous binding step");
                                }
                                LoaderTask.this.notify();
                            }
                        }
                    });

                while (!mStopped && !mLoadAndBindStepFinished) {
                    try {
                        // Just in case mFlushingWorkerThread changes but we aren't woken up,
                        // wait no longer than 1sec at a time
                        this.wait(1000);
                    } catch (InterruptedException ex) {
                        // Ignore
                    }
                }
                if (DEBUG_LOADERS) {
                    Log.d(TAG, "waited "
                            + (SystemClock.uptimeMillis()-workspaceWaitTime)
                            + "ms for previous step to finish binding");
                }
            }
        }

        void runBindSynchronousPage(int synchronousBindPage) {
            if (synchronousBindPage == PagedView.INVALID_RESTORE_PAGE) {
                // Ensure that we have a valid page index to load synchronously
                throw new RuntimeException("Should not call runBindSynchronousPage() without " +
                        "valid page index");
            }
            if (!mModelLoaded) {
                // Ensure that we don't try and bind a specified page when the pages have not been
                // loaded already (we should load everything asynchronously in that case)
                throw new RuntimeException("Expecting AllApps and Workspace to be loaded");
            }
            synchronized (mLock) {
                if (mIsLoaderTaskRunning) {
                    // Ensure that we are never running the background loading at this point since
                    // we also touch the background collections
                    throw new RuntimeException("Error! Background loading is already running");
                }
            }

            // XXX: Throw an exception if we are already loading (since we touch the worker thread
            //      data structures, we can't allow any other thread to touch that data, but because
            //      this call is synchronous, we can get away with not locking).

            // The LauncherModel is static in the LauncherAppState and mHandler may have queued
            // operations from the previous activity.  We need to ensure that all queued operations
            // are executed before any synchronous binding work is done.
            mHandler.flush();

            // Divide the set of loaded items into those that we are binding synchronously, and
            // everything else that is to be bound normally (asynchronously).
            bindWorkspace(synchronousBindPage);
            // XXX: For now, continue posting the binding of AllApps as there are other issues that
            //      arise from that.
            onlyBindAllApps();

            bindDeepShortcuts();
        }

        private void verifyNotStopped() throws CancellationException {
            synchronized (LoaderTask.this) {
                if (mStopped) {
                    throw new CancellationException("Loader stopped");
                }
            }
        }

        public void run() {
            synchronized (mLock) {
                if (mStopped) {
                    return;
                }
                mIsLoaderTaskRunning = true;
            }

            try {
                if (DEBUG_LOADERS) Log.d(TAG, "step 1.1: loading workspace");
                // Set to false in bindWorkspace()
                mIsLoadingAndBindingWorkspace = true;
                loadWorkspace();

                verifyNotStopped();
                if (DEBUG_LOADERS) Log.d(TAG, "step 1.2: bind workspace workspace");
                bindWorkspace(mPageToBindFirst);

                // Take a break
                if (DEBUG_LOADERS) Log.d(TAG, "step 1 completed, wait for idle");
                waitForIdle();
                verifyNotStopped();

                // second step
                if (DEBUG_LOADERS) Log.d(TAG, "step 2.1: loading all apps");
                loadAllApps();

                verifyNotStopped();
                if (DEBUG_LOADERS) Log.d(TAG, "step 2.2: Update icon cache");
                updateIconCache();

                // Take a break
                if (DEBUG_LOADERS) Log.d(TAG, "step 2 completed, wait for idle");
                waitForIdle();
                verifyNotStopped();

                // third step
                if (DEBUG_LOADERS) Log.d(TAG, "step 3.1: loading deep shortcuts");
                loadDeepShortcuts();

                verifyNotStopped();
                if (DEBUG_LOADERS) Log.d(TAG, "step 3.2: bind deep shortcuts");
                bindDeepShortcuts();

                // Take a break
                if (DEBUG_LOADERS) Log.d(TAG, "step 3 completed, wait for idle");
                waitForIdle();
                verifyNotStopped();

                // fourth step
                if (DEBUG_LOADERS) Log.d(TAG, "step 4.1: loading widgets");
                refreshAndBindWidgetsAndShortcuts(getCallback(), false /* bindFirst */,
                        null /* packageUser */);

                synchronized (mLock) {
                    // Everything loaded bind the data.
                    mModelLoaded = true;
                    mHasLoaderCompletedOnce = true;
                }
            } catch (CancellationException e) {
              // Loader stopped, ignore
            } finally {
                // Clear out this reference, otherwise we end up holding it until all of the
                // callback runnables are done.
                mContext = null;

                synchronized (mLock) {
                    // If we are still the last one to be scheduled, remove ourselves.
                    if (mLoaderTask == this) {
                        mLoaderTask = null;
                    }
                    mIsLoaderTaskRunning = false;
                }
            }
        }

        public void stopLocked() {
            synchronized (LoaderTask.this) {
                mStopped = true;
                this.notify();
            }
        }

        /**
         * Gets the callbacks object.  If we've been stopped, or if the launcher object
         * has somehow been garbage collected, return null instead.  Pass in the Callbacks
         * object that was around when the deferred message was scheduled, and if there's
         * a new Callbacks object around then also return null.  This will save us from
         * calling onto it with data that will be ignored.
         */
        Callbacks tryGetCallbacks(Callbacks oldCallbacks) {
            synchronized (mLock) {
                if (mStopped) {
                    return null;
                }

                if (mCallbacks == null) {
                    return null;
                }

                final Callbacks callbacks = mCallbacks.get();
                if (callbacks != oldCallbacks) {
                    return null;
                }
                if (callbacks == null) {
                    Log.w(TAG, "no mCallbacks");
                    return null;
                }

                return callbacks;
            }
        }

        private void loadWorkspace() {
            if (LauncherAppState.PROFILE_STARTUP) {
                Trace.beginSection("Loading Workspace");
            }

            final Context context = mContext;
            final ContentResolver contentResolver = context.getContentResolver();
            final PackageManagerHelper pmHelper = new PackageManagerHelper(context);
            final boolean isSafeMode = pmHelper.isSafeMode();
            final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
            final DeepShortcutManager shortcutManager = DeepShortcutManager.getInstance(context);
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
                    !GridSizeMigrationTask.migrateGridIfNeeded(mContext)) {
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

            synchronized (sBgDataModel) {
                sBgDataModel.clear();

                final HashMap<String, Integer> installingPkgs = PackageInstallerCompat
                        .getInstance(mContext).updateAndGetActiveSessionCache();
                sBgDataModel.workspaceScreens.addAll(loadWorkspaceScreensDb(mContext));

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
                                    shortcutManager.queryForPinnedShortcuts(null, user);
                            if (shortcutManager.wasLastCallSuccess()) {
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
                                        launcherApps.isPackageEnabledForProfile(targetPkg, c.user);

                                if (cn != null && validTarget) {
                                    // If the apk is present and the shortcut points to a specific
                                    // component.

                                    // If the component is already present
                                    if (launcherApps.isActivityEnabledForProfile(cn, c.user)) {
                                        // no special handling necessary for this item
                                        c.markRestored();
                                    } else {
                                        if (c.hasRestoreFlag(ShortcutInfo.FLAG_AUTOINTALL_ICON)) {
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
                                        c.getInt(rankIndex) >= FolderIcon.NUM_ITEMS_IN_PREVIEW;

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

                                    c.checkAndAddItem(info, sBgDataModel);
                                } else {
                                    throw new RuntimeException("Unexpected null ShortcutInfo");
                                }
                                break;

                            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                                FolderInfo folderInfo = sBgDataModel.findOrMakeFolder(c.id);
                                c.applyCommonProperties(folderInfo);

                                // Do not trim the folder label, as is was set by the user.
                                folderInfo.title = c.getString(c.titleIndex);
                                folderInfo.spanX = 1;
                                folderInfo.spanY = 1;
                                folderInfo.options = c.getInt(optionsIndex);

                                // no special handling required for restored folders
                                c.markRestored();

                                c.checkAndAddItem(folderInfo, sBgDataModel);
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
                                    widgetProvidersMap = AppWidgetManagerCompat
                                            .getInstance(mContext).getAllProvidersMap();
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
                                    c.checkAndAddItem(appWidgetInfo, sBgDataModel);
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
                    sBgDataModel.clear();
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
                        sBgDataModel.workspaceItems.remove(sBgDataModel.folders.get(folderId));
                        sBgDataModel.folders.remove(folderId);
                        sBgDataModel.itemsIdMap.remove(folderId);
                    }

                    // Remove any ghost widgets
                    LauncherSettings.Settings.call(contentResolver,
                            LauncherSettings.Settings.METHOD_REMOVE_GHOST_WIDGETS);
                }

                // Unpin shortcuts that don't exist on the workspace.
                HashSet<ShortcutKey> pendingShortcuts =
                        InstallShortcutReceiver.getPendingShortcuts(context);
                for (ShortcutKey key : shortcutKeyToPinnedShortcuts.keySet()) {
                    MutableInt numTimesPinned = sBgDataModel.pinnedShortcutCounts.get(key);
                    if ((numTimesPinned == null || numTimesPinned.value == 0)
                            && !pendingShortcuts.contains(key)) {
                        // Shortcut is pinned but doesn't exist on the workspace; unpin it.
                        shortcutManager.unpinShortcut(key);
                    }
                }

                // Sort all the folder items and make sure the first 3 items are high resolution.
                for (FolderInfo folder : sBgDataModel.folders) {
                    Collections.sort(folder.contents, Folder.ITEM_POS_COMPARATOR);
                    int pos = 0;
                    for (ShortcutInfo info : folder.contents) {
                        if (info.usingLowResIcon &&
                                info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                            mIconCache.getTitleAndIcon(info, false);
                        }
                        pos ++;
                        if (pos >= FolderIcon.NUM_ITEMS_IN_PREVIEW) {
                            break;
                        }
                    }
                }

                c.commitRestoredItems();
                if (!isSdCardReady && !pendingPackages.isEmpty()) {
                    context.registerReceiver(
                            new SdCardAvailableReceiver(
                                    LauncherModel.this, mContext, pendingPackages),
                            new IntentFilter(Intent.ACTION_BOOT_COMPLETED),
                            null,
                            sWorker);
                }

                // Remove any empty screens
                ArrayList<Long> unusedScreens = new ArrayList<>(sBgDataModel.workspaceScreens);
                for (ItemInfo item: sBgDataModel.itemsIdMap) {
                    long screenId = item.screenId;
                    if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                            unusedScreens.contains(screenId)) {
                        unusedScreens.remove(screenId);
                    }
                }

                // If there are any empty screens remove them, and update.
                if (unusedScreens.size() != 0) {
                    sBgDataModel.workspaceScreens.removeAll(unusedScreens);
                    updateWorkspaceScreenOrder(context, sBgDataModel.workspaceScreens);
                }
            }
            if (LauncherAppState.PROFILE_STARTUP) {
                Trace.endSection();
            }
        }

        /** Filters the set of items who are directly or indirectly (via another container) on the
         * specified screen. */
        private void filterCurrentWorkspaceItems(long currentScreenId,
                ArrayList<ItemInfo> allWorkspaceItems,
                ArrayList<ItemInfo> currentScreenItems,
                ArrayList<ItemInfo> otherScreenItems) {
            // Purge any null ItemInfos
            Iterator<ItemInfo> iter = allWorkspaceItems.iterator();
            while (iter.hasNext()) {
                ItemInfo i = iter.next();
                if (i == null) {
                    iter.remove();
                }
            }

            // Order the set of items by their containers first, this allows use to walk through the
            // list sequentially, build up a list of containers that are in the specified screen,
            // as well as all items in those containers.
            Set<Long> itemsOnScreen = new HashSet<Long>();
            Collections.sort(allWorkspaceItems, new Comparator<ItemInfo>() {
                @Override
                public int compare(ItemInfo lhs, ItemInfo rhs) {
                    return Utilities.longCompare(lhs.container, rhs.container);
                }
            });
            for (ItemInfo info : allWorkspaceItems) {
                if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    if (info.screenId == currentScreenId) {
                        currentScreenItems.add(info);
                        itemsOnScreen.add(info.id);
                    } else {
                        otherScreenItems.add(info);
                    }
                } else if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                    currentScreenItems.add(info);
                    itemsOnScreen.add(info.id);
                } else {
                    if (itemsOnScreen.contains(info.container)) {
                        currentScreenItems.add(info);
                        itemsOnScreen.add(info.id);
                    } else {
                        otherScreenItems.add(info);
                    }
                }
            }
        }

        /** Filters the set of widgets which are on the specified screen. */
        private void filterCurrentAppWidgets(long currentScreenId,
                ArrayList<LauncherAppWidgetInfo> appWidgets,
                ArrayList<LauncherAppWidgetInfo> currentScreenWidgets,
                ArrayList<LauncherAppWidgetInfo> otherScreenWidgets) {

            for (LauncherAppWidgetInfo widget : appWidgets) {
                if (widget == null) continue;
                if (widget.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                        widget.screenId == currentScreenId) {
                    currentScreenWidgets.add(widget);
                } else {
                    otherScreenWidgets.add(widget);
                }
            }
        }

        /** Sorts the set of items by hotseat, workspace (spatially from top to bottom, left to
         * right) */
        private void sortWorkspaceItemsSpatially(ArrayList<ItemInfo> workspaceItems) {
            final InvariantDeviceProfile profile = mApp.getInvariantDeviceProfile();
            final int screenCols = profile.numColumns;
            final int screenCellCount = profile.numColumns * profile.numRows;
            Collections.sort(workspaceItems, new Comparator<ItemInfo>() {
                @Override
                public int compare(ItemInfo lhs, ItemInfo rhs) {
                    if (lhs.container == rhs.container) {
                        // Within containers, order by their spatial position in that container
                        switch ((int) lhs.container) {
                            case LauncherSettings.Favorites.CONTAINER_DESKTOP: {
                                long lr = (lhs.screenId * screenCellCount +
                                        lhs.cellY * screenCols + lhs.cellX);
                                long rr = (rhs.screenId * screenCellCount +
                                        rhs.cellY * screenCols + rhs.cellX);
                                return Utilities.longCompare(lr, rr);
                            }
                            case LauncherSettings.Favorites.CONTAINER_HOTSEAT: {
                                // We currently use the screen id as the rank
                                return Utilities.longCompare(lhs.screenId, rhs.screenId);
                            }
                            default:
                                if (ProviderConfig.IS_DOGFOOD_BUILD) {
                                    throw new RuntimeException("Unexpected container type when " +
                                            "sorting workspace items.");
                                }
                                return 0;
                        }
                    } else {
                        // Between containers, order by hotseat, desktop
                        return Utilities.longCompare(lhs.container, rhs.container);
                    }
                }
            });
        }

        private void bindWorkspaceScreens(final Callbacks oldCallbacks,
                final ArrayList<Long> orderedScreens) {
            final Runnable r = new Runnable() {
                @Override
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.bindScreens(orderedScreens);
                    }
                }
            };
            runOnMainThread(r);
        }

        private void bindWorkspaceItems(final Callbacks oldCallbacks,
                final ArrayList<ItemInfo> workspaceItems,
                final ArrayList<LauncherAppWidgetInfo> appWidgets,
                final Executor executor) {

            // Bind the workspace items
            int N = workspaceItems.size();
            for (int i = 0; i < N; i += ITEMS_CHUNK) {
                final int start = i;
                final int chunkSize = (i+ITEMS_CHUNK <= N) ? ITEMS_CHUNK : (N-i);
                final Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null) {
                            callbacks.bindItems(workspaceItems, start, start+chunkSize,
                                    false);
                        }
                    }
                };
                executor.execute(r);
            }

            // Bind the widgets, one at a time
            N = appWidgets.size();
            for (int i = 0; i < N; i++) {
                final LauncherAppWidgetInfo widget = appWidgets.get(i);
                final Runnable r = new Runnable() {
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null) {
                            callbacks.bindAppWidget(widget);
                        }
                    }
                };
                executor.execute(r);
            }
        }

        /**
         * Binds all loaded data to actual views on the main thread.
         */
        private void bindWorkspace(int synchronizeBindPage) {
            final long t = SystemClock.uptimeMillis();
            Runnable r;

            // Don't use these two variables in any of the callback runnables.
            // Otherwise we hold a reference to them.
            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher");
                return;
            }

            // Save a copy of all the bg-thread collections
            ArrayList<ItemInfo> workspaceItems = new ArrayList<>();
            ArrayList<LauncherAppWidgetInfo> appWidgets = new ArrayList<>();
            ArrayList<Long> orderedScreenIds = new ArrayList<>();

            synchronized (sBgDataModel) {
                workspaceItems.addAll(sBgDataModel.workspaceItems);
                appWidgets.addAll(sBgDataModel.appWidgets);
                orderedScreenIds.addAll(sBgDataModel.workspaceScreens);
            }

            final int currentScreen;
            {
                int currScreen = synchronizeBindPage != PagedView.INVALID_RESTORE_PAGE
                        ? synchronizeBindPage : oldCallbacks.getCurrentWorkspaceScreen();
                if (currScreen >= orderedScreenIds.size()) {
                    // There may be no workspace screens (just hotseat items and an empty page).
                    currScreen = PagedView.INVALID_RESTORE_PAGE;
                }
                currentScreen = currScreen;
            }
            final boolean validFirstPage = currentScreen >= 0;
            final long currentScreenId =
                    validFirstPage ? orderedScreenIds.get(currentScreen) : INVALID_SCREEN_ID;

            // Separate the items that are on the current screen, and all the other remaining items
            ArrayList<ItemInfo> currentWorkspaceItems = new ArrayList<>();
            ArrayList<ItemInfo> otherWorkspaceItems = new ArrayList<>();
            ArrayList<LauncherAppWidgetInfo> currentAppWidgets = new ArrayList<>();
            ArrayList<LauncherAppWidgetInfo> otherAppWidgets = new ArrayList<>();

            filterCurrentWorkspaceItems(currentScreenId, workspaceItems, currentWorkspaceItems,
                    otherWorkspaceItems);
            filterCurrentAppWidgets(currentScreenId, appWidgets, currentAppWidgets,
                    otherAppWidgets);
            sortWorkspaceItemsSpatially(currentWorkspaceItems);
            sortWorkspaceItemsSpatially(otherWorkspaceItems);

            // Tell the workspace that we're about to start binding items
            r = new Runnable() {
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.clearPendingBinds();
                        callbacks.startBinding();
                    }
                }
            };
            runOnMainThread(r);

            bindWorkspaceScreens(oldCallbacks, orderedScreenIds);

            Executor mainExecutor = new DeferredMainThreadExecutor();
            // Load items on the current page.
            bindWorkspaceItems(oldCallbacks, currentWorkspaceItems, currentAppWidgets, mainExecutor);

            // In case of validFirstPage, only bind the first screen, and defer binding the
            // remaining screens after first onDraw (and an optional the fade animation whichever
            // happens later).
            // This ensures that the first screen is immediately visible (eg. during rotation)
            // In case of !validFirstPage, bind all pages one after other.
            final Executor deferredExecutor =
                    validFirstPage ? new ViewOnDrawExecutor(mHandler) : mainExecutor;

            mainExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.finishFirstPageBind(
                                validFirstPage ? (ViewOnDrawExecutor) deferredExecutor : null);
                    }
                }
            });

            bindWorkspaceItems(oldCallbacks, otherWorkspaceItems, otherAppWidgets, deferredExecutor);

            // Tell the workspace that we're done binding items
            r = new Runnable() {
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.finishBindingItems();
                    }

                    mIsLoadingAndBindingWorkspace = false;

                    // Run all the bind complete runnables after workspace is bound.
                    if (!mBindCompleteRunnables.isEmpty()) {
                        synchronized (mBindCompleteRunnables) {
                            for (final Runnable r : mBindCompleteRunnables) {
                                runOnWorkerThread(r);
                            }
                            mBindCompleteRunnables.clear();
                        }
                    }

                    // If we're profiling, ensure this is the last thing in the queue.
                    if (DEBUG_LOADERS) {
                        Log.d(TAG, "bound workspace in "
                            + (SystemClock.uptimeMillis()-t) + "ms");
                    }

                }
            };
            deferredExecutor.execute(r);

            if (validFirstPage) {
                r = new Runnable() {
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null) {
                            // We are loading synchronously, which means, some of the pages will be
                            // bound after first draw. Inform the callbacks that page binding is
                            // not complete, and schedule the remaining pages.
                            if (currentScreen != PagedView.INVALID_RESTORE_PAGE) {
                                callbacks.onPageBoundSynchronously(currentScreen);
                            }
                            callbacks.executeOnNextDraw((ViewOnDrawExecutor) deferredExecutor);
                        }
                    }
                };
                runOnMainThread(r);
            }
        }

        private void updateIconCache() {
            // Ignore packages which have a promise icon.
            HashSet<String> packagesToIgnore = new HashSet<>();
            synchronized (sBgDataModel) {
                for (ItemInfo info : sBgDataModel.itemsIdMap) {
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

        private void onlyBindAllApps() {
            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher (onlyBindAllApps)");
                return;
            }

            // shallow copy
            @SuppressWarnings("unchecked")
            final ArrayList<AppInfo> list
                    = (ArrayList<AppInfo>) mBgAllAppsList.data.clone();
            Runnable r = new Runnable() {
                public void run() {
                    final long t = SystemClock.uptimeMillis();
                    final Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.bindAllApplications(list);
                    }
                    if (DEBUG_LOADERS) {
                        Log.d(TAG, "bound all " + list.size() + " apps from cache in "
                                + (SystemClock.uptimeMillis() - t) + "ms");
                    }
                }
            };
            runOnMainThread(r);
        }

        private void scheduleManagedHeuristicRunnable(final ManagedProfileHeuristic heuristic,
                final UserHandle user, final List<LauncherActivityInfo> apps) {
            if (heuristic != null) {
                // Assume the app lists now is updated.
                mIsManagedHeuristicAppsUpdated = false;
                final Runnable managedHeuristicRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (mIsManagedHeuristicAppsUpdated) {
                            // If app list is updated, we need to reschedule it otherwise old app
                            // list will override everything in processUserApps().
                            sWorker.post(new Runnable() {
                                public void run() {
                                    final List<LauncherActivityInfo> updatedApps =
                                            mLauncherApps.getActivityList(null, user);
                                    scheduleManagedHeuristicRunnable(heuristic, user,
                                            updatedApps);
                                }
                            });
                        } else {
                            heuristic.processUserApps(apps);
                        }
                    }
                };
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        // Check isLoadingWorkspace on the UI thread, as it is updated on the UI
                        // thread.
                        if (mIsLoadingAndBindingWorkspace) {
                            synchronized (mBindCompleteRunnables) {
                                mBindCompleteRunnables.add(managedHeuristicRunnable);
                            }
                        } else {
                            runOnWorkerThread(managedHeuristicRunnable);
                        }
                    }
                });
            }
        }

        private void loadAllApps() {
            final long loadTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher (loadAllApps)");
                return;
            }

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
                    continue;
                }
                boolean quietMode = mUserManager.isQuietModeEnabled(user);
                // Create the ApplicationInfos
                for (int i = 0; i < apps.size(); i++) {
                    LauncherActivityInfo app = apps.get(i);
                    // This builds the icon bitmaps.
                    mBgAllAppsList.add(new AppInfo(app, user, quietMode), app);
                }
                final ManagedProfileHeuristic heuristic = ManagedProfileHeuristic.get(mContext, user);
                if (heuristic != null) {
                    scheduleManagedHeuristicRunnable(heuristic, user, apps);
                }
            }
            // Huh? Shouldn't this be inside the Runnable below?
            final ArrayList<AppInfo> added = mBgAllAppsList.added;
            mBgAllAppsList.added = new ArrayList<AppInfo>();

            // Post callback on main thread
            mHandler.post(new Runnable() {
                public void run() {

                    final long bindTime = SystemClock.uptimeMillis();
                    final Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.bindAllApplications(added);
                        if (DEBUG_LOADERS) {
                            Log.d(TAG, "bound " + added.size() + " apps in "
                                    + (SystemClock.uptimeMillis() - bindTime) + "ms");
                        }
                    } else {
                        Log.i(TAG, "not binding apps: no Launcher activity");
                    }
                }
            });
            // Cleanup any data stored for a deleted user.
            ManagedProfileHeuristic.processAllUsers(profiles, mContext);
            if (DEBUG_LOADERS) {
                Log.d(TAG, "Icons processed in "
                        + (SystemClock.uptimeMillis() - loadTime) + "ms");
            }
        }

        private void loadDeepShortcuts() {
            sBgDataModel.deepShortcutMap.clear();
            DeepShortcutManager shortcutManager = DeepShortcutManager.getInstance(mContext);
            mHasShortcutHostPermission = shortcutManager.hasHostPermission();
            if (mHasShortcutHostPermission) {
                for (UserHandle user : mUserManager.getUserProfiles()) {
                    if (mUserManager.isUserUnlocked(user)) {
                        List<ShortcutInfoCompat> shortcuts =
                                shortcutManager.queryForAllShortcuts(user);
                        sBgDataModel.updateDeepShortcutMap(null, user, shortcuts);
                    }
                }
            }
        }
    }

    public void bindDeepShortcuts() {
        final MultiHashMap<ComponentKey, String> shortcutMapCopy =
                sBgDataModel.deepShortcutMap.clone();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                Callbacks callbacks = getCallback();
                if (callbacks != null) {
                    callbacks.bindDeepShortcutMap(shortcutMapCopy);
                }
            }
        };
        runOnMainThread(r);
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

    void enqueueModelUpdateTask(BaseModelUpdateTask task) {
        if (!mModelLoaded && mLoaderTask == null) {
            if (DEBUG_LOADERS) {
                Log.d(TAG, "enqueueModelUpdateTask Ignoring task since loader is pending=" + task);
            }
            return;
        }
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
        private DeferredHandler mUiHandler;

        /* package private */
        void init(LauncherModel model) {
            mModel = model;
            mUiHandler = mModel.mHandler;
        }

        @Override
        public void run() {
            if (!mModel.mHasLoaderCompletedOnce) {
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
            mUiHandler.post(new Runnable() {
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

    private void bindWidgetsModel(final Callbacks callbacks) {
        final MultiHashMap<PackageItemInfo, WidgetItem> widgets
                = mBgWidgetsModel.getWidgetsMap().clone();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Callbacks cb = getCallback();
                if (callbacks == cb && cb != null) {
                    callbacks.bindAllWidgets(widgets);
                }
            }
        });
    }

    public void refreshAndBindWidgetsAndShortcuts(final Callbacks callbacks,
            final boolean bindFirst, @Nullable final PackageUserKey packageUser) {
        runOnWorkerThread(new Runnable() {
            @Override
            public void run() {
                if (bindFirst && !mBgWidgetsModel.isEmpty()) {
                    bindWidgetsModel(callbacks);
                }
                ArrayList<WidgetItem> widgets = mBgWidgetsModel.update(
                        mApp.getContext(), packageUser);
                bindWidgetsModel(callbacks);

                // update the Widget entries inside DB on the worker thread.
                mApp.getWidgetCache().removeObsoletePreviews(widgets, packageUser);
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
     * @return {@link FolderInfo} if its already loaded.
     */
    public FolderInfo findFolderById(Long folderId) {
        synchronized (sBgDataModel) {
            return sBgDataModel.folders.get(folderId);
        }
    }

    @Thunk class DeferredMainThreadExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            runOnMainThread(command);
        }
    }

    /**
     * @return the looper for the worker thread which can be used to start background tasks.
     */
    public static Looper getWorkerLooper() {
        return sWorkerThread.getLooper();
    }
}
