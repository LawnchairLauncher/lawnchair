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

import static com.android.launcher3.LauncherAppState.ACTION_FORCE_ROLOAD;
import static com.android.launcher3.config.FeatureFlags.IS_DOGFOOD_BUILD;

import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import ch.deletescape.lawnchair.LawnchairUtilsKt;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat.PackageInstallInfo;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.model.AddWorkspaceItemsTask;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.CacheDataUpdatedTask;
import com.android.launcher3.model.LoaderResults;
import com.android.launcher3.model.LoaderTask;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.PackageInstallStateChangedTask;
import com.android.launcher3.model.PackageUpdatedTask;
import com.android.launcher3.model.ShortcutsChangedTask;
import com.android.launcher3.model.UserLockStateChangedTask;
import com.android.launcher3.provider.LauncherDbUtils;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.Provider;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.widget.WidgetListRowEntry;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state
 * for the Launcher.
 */
public class LauncherModel extends BroadcastReceiver
        implements LauncherAppsCompat.OnAppsChangedCallbackCompat {
    private static final boolean DEBUG_RECEIVER = false;

    static final String TAG = "Launcher.Model";

    private final MainThreadExecutor mUiExecutor = new MainThreadExecutor();
    @Thunk final LauncherAppState mApp;
    @Thunk final Object mLock = new Object();
    @Thunk
    LoaderTask mLoaderTask;
    @Thunk boolean mIsLoaderTaskRunning;

    @Thunk static final HandlerThread sWorkerThread = new HandlerThread("launcher-loader");
    @Thunk static final HandlerThread sUiWorkerThread = new HandlerThread("launcher-ui-loader");
    @Thunk static final HandlerThread sIconPackThread = new HandlerThread("launcher-icon-pack");
    @Thunk static final HandlerThread sIconPackUiThread = new HandlerThread("launcher-icon-pack-ui");
    static {
        sWorkerThread.start();
        sUiWorkerThread.start();
        sIconPackThread.start();
        sIconPackUiThread.start();
    }
    @Thunk static final Handler sWorker = new Handler(sWorkerThread.getLooper());
    @Thunk static final Handler sUiWorker = new Handler(sUiWorkerThread.getLooper());
    @Thunk static final Handler sIconPack = new Handler(sIconPackThread.getLooper());

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
        public void rebindModel();

        public int getCurrentWorkspaceScreen();
        public void clearPendingBinds();
        public void startBinding();
        public void bindItems(List<ItemInfo> shortcuts, boolean forceAnimateIcons);
        public void bindScreens(ArrayList<Long> orderedScreenIds);
        public void finishFirstPageBind(ViewOnDrawExecutor executor);
        public void finishBindingItems(int pageBoundFirst);
        public void bindAllApplications(ArrayList<AppInfo> apps);
        public void bindAppsAddedOrUpdated(ArrayList<AppInfo> apps);
        public void preAddApps();
        public void bindAppsAdded(ArrayList<Long> newScreens,
                                  ArrayList<ItemInfo> addNotAnimated,
                                  ArrayList<ItemInfo> addAnimated);
        public void bindPromiseAppProgressUpdated(PromiseAppInfo app);
        public void bindShortcutsChanged(ArrayList<ShortcutInfo> updated, UserHandle user);
        public void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets);
        public void bindRestoreItemsChange(HashSet<ItemInfo> updates);
        public void bindWorkspaceComponentsRemoved(ItemInfoMatcher matcher);
        public void bindAppInfosRemoved(ArrayList<AppInfo> appInfos);
        public void bindAllWidgets(ArrayList<WidgetListRowEntry> widgets);
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
    public void addAndBindAddedWorkspaceItems(List<Pair<ItemInfo, Object>> itemList) {
        Callbacks callbacks = getCallback();
        if (callbacks != null) {
            callbacks.preAddApps();
        }
        enqueueModelUpdateTask(new AddWorkspaceItemsTask(itemList));
    }

    public ModelWriter getWriter(boolean hasVerticalHotseat, boolean verifyChanges) {
        return new ModelWriter(mApp.getContext(), this, sBgDataModel,
                hasVerticalHotseat, verifyChanges);
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

    public void onPackagesReload(UserHandle user) {
        enqueueModelUpdateTask(new PackageUpdatedTask(
                PackageUpdatedTask.OP_RELOAD, user));
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
        } else if (IS_DOGFOOD_BUILD && ACTION_FORCE_ROLOAD.equals(action)) {
            forceReload();
        }
    }

    public void forceReloadOnNextLaunch() {
        synchronized (this.mLock) {
            stopLoader();
            mModelLoaded = false;
        }
    }

    public void forceReload() {
        forceReload(-1);
    }

    /**
     * Reloads the workspace items from the DB and re-binds the workspace. This should generally
     * not be called as DB updates are automatically followed by UI update
     * @param synchronousBindPage The page to bind first. Can pass -1 to use the current page.
     */
    public void forceReload(int synchronousBindPage) {
        synchronized (mLock) {
            // Stop any existing loaders first, so they don't set mModelLoaded to true later
            stopLoader();
            mModelLoaded = false;
        }

        // Start the loader if launcher is already running, otherwise the loader will run,
        // the next time launcher starts
        Callbacks callbacks = getCallback();
        if (callbacks != null) {
            if (synchronousBindPage < 0) {
                synchronousBindPage = callbacks.getCurrentWorkspaceScreen();
            }
            startLoader(synchronousBindPage);
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
                mUiExecutor.execute(oldCallbacks::clearPendingBinds);

                // If there is already one running, tell it to stop.
                stopLoader();
                LoaderResults loaderResults = new LoaderResults(mApp, sBgDataModel,
                        mBgAllAppsList, synchronousBindPage, mCallbacks);
                if (mModelLoaded && !mIsLoaderTaskRunning) {
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
                    startLoaderForResults(loaderResults);
                }
            }
        }
        return false;
    }

    /**
     * If there is already a loader task running, tell it to stop.
     */
    public void stopLoader() {
        synchronized (mLock) {
            LoaderTask oldTask = mLoaderTask;
            mLoaderTask = null;
            if (oldTask != null) {
                oldTask.stopLocked();
            }
        }
    }

    public void startLoaderForResults(LoaderResults results) {
        synchronized (mLock) {
            stopLoader();
            mLoaderTask = new LoaderTask(mApp, mBgAllAppsList, sBgDataModel, results);
            runOnWorkerThread(mLoaderTask);
        }
    }

    public void startLoaderForResultsIfNotLoaded(LoaderResults results) {
        synchronized (mLock) {
            if (!isModelLoaded()) {
                Log.d(TAG, "Workspace not loaded, loading now");
                startLoaderForResults(results);
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
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                apps.addPromiseApp(app.getContext(), sessionInfo);
                if (!apps.added.isEmpty()) {
                    final ArrayList<AppInfo> arrayList = new ArrayList<>(apps.added);
                    apps.added.clear();
                    scheduleCallbackTask(new CallbackTask() {
                        @Override
                        public void execute(Callbacks callbacks) {
                            callbacks.bindAppsAddedOrUpdated(arrayList);
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

    public void enqueueModelUpdateTask(ModelUpdateTask task) {
        task.init(mApp, this, sBgDataModel, mBgAllAppsList, mUiExecutor);
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
    public interface ModelUpdateTask extends Runnable {

        /**
         * Called before the task is posted to initialize the internal state.
         */
        void init(LauncherAppState app, LauncherModel model,
                BgDataModel dataModel, AllAppsList allAppsList, Executor uiExecutor);

    }

    public void updateAndBindShortcutInfo(final ShortcutInfo si, final ShortcutInfoCompat info) {
        updateAndBindShortcutInfo(new Provider<ShortcutInfo>() {
            @Override
            public ShortcutInfo get() {
                si.updateFromDeepShortcutInfo(info, mApp.getContext());
                LauncherIcons li = LauncherIcons.obtain(mApp.getContext());
                li.createShortcutIcon(info, si.isBadgeVisible()).applyTo(si);
                li.recycle();
                return si;
            }
        });
    }

    /**
     * Utility method to update a shortcut on the background thread.
     */
    public void updateAndBindShortcutInfo(final Provider<ShortcutInfo> shortcutProvider) {
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                ShortcutInfo info = shortcutProvider.get();
                getModelWriter().updateItemInDatabase(info);
                ArrayList<ShortcutInfo> update = new ArrayList<>();
                update.add(info);
                bindUpdatedShortcuts(update, info.user);
            }
        });
    }

    public void refreshAndBindWidgetsAndShortcuts(@Nullable final PackageUserKey packageUser) {
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                dataModel.widgetsModel.update(app, packageUser);
                bindUpdatedWidgets(dataModel);
            }
        });
    }

    public List<LauncherAppWidgetInfo> getLoadedWidgets() {
        synchronized (sBgDataModel) {
            return new ArrayList<>(sBgDataModel.appWidgets);
        }
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

    /**
     * @return the looper for the ui worker thread which can be used to start background tasksfor ui.
     */
    public static Looper getUiWorkerLooper() {
        return sUiWorkerThread.getLooper();
    }

    /**
     * @return the looper for the icon pack thread which can be used to load icon packs.
     */
    public static Looper getIconPackLooper() {
        return sIconPackThread.getLooper();
    }

    /**
     * @return the looper for the icon pack ui thread which can be used to load icon pickers.
     */
    public static Looper getIconPackUiLooper() {
        return sIconPackUiThread.getLooper();
    }

    public static void setWorkerPriority(final int priority) {
        Process.setThreadPriority(sWorkerThread.getThreadId(), priority);
    }
}
