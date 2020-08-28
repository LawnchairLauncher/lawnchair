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
import static com.android.launcher3.config.FeatureFlags.IS_STUDIO_BUILD;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.PackageManagerHelper.hasShortcutsPermission;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.AddWorkspaceItemsTask;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.CacheDataUpdatedTask;
import com.android.launcher3.model.LoaderResults;
import com.android.launcher3.model.LoaderTask;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.PackageInstallStateChangedTask;
import com.android.launcher3.model.PackageUpdatedTask;
import com.android.launcher3.model.ShortcutsChangedTask;
import com.android.launcher3.model.UserLockStateChangedTask;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionTracker;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state
 * for the Launcher.
 */
public class LauncherModel extends LauncherApps.Callback implements InstallSessionTracker.Callback {
    private static final boolean DEBUG_RECEIVER = false;

    static final String TAG = "Launcher.Model";

    private final LauncherAppState mApp;
    private final Object mLock = new Object();
    private final LooperExecutor mMainExecutor = MAIN_EXECUTOR;

    private LoaderTask mLoaderTask;
    private boolean mIsLoaderTaskRunning;

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

    private final ArrayList<Callbacks> mCallbacksList = new ArrayList<>(1);

    // < only access in worker thread >
    private final AllAppsList mBgAllAppsList;

    /**
     * All the static data should be accessed on the background thread, A lock should be acquired
     * on this object when accessing any data from this model.
     */
    private final BgDataModel mBgDataModel = new BgDataModel();

    // Runnable to check if the shortcuts permission has changed.
    private final Runnable mShortcutPermissionCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (mModelLoaded && hasShortcutsPermission(mApp.getContext())
                    != mBgAllAppsList.hasShortcutHostPermission()) {
                forceReload();
            }
        }
    };

    LauncherModel(LauncherAppState app, IconCache iconCache, AppFilter appFilter) {
        mApp = app;
        mBgAllAppsList = new AllAppsList(iconCache, appFilter);
    }

    /**
     * Adds the provided items to the workspace.
     */
    public void addAndBindAddedWorkspaceItems(List<Pair<ItemInfo, Object>> itemList) {
        for (Callbacks cb : getCallbacks()) {
            cb.preAddApps();
        }
        enqueueModelUpdateTask(new AddWorkspaceItemsTask(itemList));
    }

    public ModelWriter getWriter(boolean hasVerticalHotseat, boolean verifyChanges) {
        return new ModelWriter(mApp.getContext(), this, mBgDataModel,
                hasVerticalHotseat, verifyChanges);
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
        FileLog.d(TAG, "package removed received " + TextUtils.join(",", packages));
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
    public void onShortcutsChanged(String packageName, List<ShortcutInfo> shortcuts,
            UserHandle user) {
        enqueueModelUpdateTask(new ShortcutsChangedTask(packageName, shortcuts, user, true));
    }

    /**
     * Called when the icon for an app changes, outside of package event
     */
    @WorkerThread
    public void onAppIconChanged(String packageName, UserHandle user) {
        // Update the icon for the calendar package
        Context context = mApp.getContext();
        onPackageChanged(packageName, user);

        List<ShortcutInfo> pinnedShortcuts = new ShortcutRequest(context, user)
                .forPackage(packageName).query(ShortcutRequest.PINNED);
        if (!pinnedShortcuts.isEmpty()) {
            enqueueModelUpdateTask(new ShortcutsChangedTask(packageName, pinnedShortcuts, user,
                    false));
        }
    }

    public void onBroadcastIntent(Intent intent) {
        if (DEBUG_RECEIVER) Log.d(TAG, "onReceive intent=" + intent);
        final String action = intent.getAction();
        if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            // If we have changed locale we need to clear out the labels in all apps/workspace.
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
                    enqueueModelUpdateTask(new UserLockStateChangedTask(
                            user, Intent.ACTION_MANAGED_PROFILE_UNLOCKED.equals(action)));
                }
            }
        } else if (IS_STUDIO_BUILD && ACTION_FORCE_ROLOAD.equals(action)) {
            for (Callbacks cb : getCallbacks()) {
                if (cb instanceof Launcher) {
                    ((Launcher) cb).recreate();
                }
            }
        }
    }

    /**
     * Reloads the workspace items from the DB and re-binds the workspace. This should generally
     * not be called as DB updates are automatically followed by UI update
     */
    public void forceReload() {
        synchronized (mLock) {
            // Stop any existing loaders first, so they don't set mModelLoaded to true later
            stopLoader();
            mModelLoaded = false;
        }

        // Start the loader if launcher is already running, otherwise the loader will run,
        // the next time launcher starts
        if (hasCallbacks()) {
            startLoader();
        }
    }

    /**
     * Rebinds all existing callbacks with already loaded model
     */
    public void rebindCallbacks() {
        if (hasCallbacks()) {
            startLoader();
        }
    }

    /**
     * Removes an existing callback
     */
    public void removeCallbacks(Callbacks callbacks) {
        synchronized (mCallbacksList) {
            Preconditions.assertUIThread();
            if (mCallbacksList.remove(callbacks)) {
                if (stopLoader()) {
                    // Rebind existing callbacks
                    startLoader();
                }
            }
        }
    }

    /**
     * Adds a callbacks to receive model updates
     * @return true if workspace load was performed synchronously
     */
    public boolean addCallbacksAndLoad(Callbacks callbacks) {
        synchronized (mLock) {
            addCallbacks(callbacks);
            return startLoader();

        }
    }

    /**
     * Adds a callbacks to receive model updates
     */
    public void addCallbacks(Callbacks callbacks) {
        Preconditions.assertUIThread();
        synchronized (mCallbacksList) {
            mCallbacksList.add(callbacks);
        }
    }

    /**
     * Starts the loader. Tries to bind {@params synchronousBindPage} synchronously if possible.
     * @return true if the page could be bound synchronously.
     */
    public boolean startLoader() {
        // Enable queue before starting loader. It will get disabled in Launcher#finishBindingItems
        InstallShortcutReceiver.enableInstallQueue(InstallShortcutReceiver.FLAG_LOADER_RUNNING);
        synchronized (mLock) {
            // Don't bother to start the thread if we know it's not going to do anything
            final Callbacks[] callbacksList = getCallbacks();
            if (callbacksList.length > 0) {
                // Clear any pending bind-runnables from the synchronized load process.
                for (Callbacks cb : callbacksList) {
                    mMainExecutor.execute(cb::clearPendingBinds);
                }

                // If there is already one running, tell it to stop.
                stopLoader();
                LoaderResults loaderResults = new LoaderResults(
                        mApp, mBgDataModel, mBgAllAppsList, callbacksList, mMainExecutor);
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
     * @return true if an existing loader was stopped.
     */
    public boolean stopLoader() {
        synchronized (mLock) {
            LoaderTask oldTask = mLoaderTask;
            mLoaderTask = null;
            if (oldTask != null) {
                oldTask.stopLocked();
                return true;
            }
            return false;
        }
    }

    public void startLoaderForResults(LoaderResults results) {
        synchronized (mLock) {
            stopLoader();
            mLoaderTask = new LoaderTask(mApp, mBgAllAppsList, mBgDataModel, results);

            // Always post the loader task, instead of running directly (even on same thread) so
            // that we exit any nested synchronized blocks
            MODEL_EXECUTOR.post(mLoaderTask);
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

    @Override
    public void onInstallSessionCreated(final PackageInstallInfo sessionInfo) {
        if (FeatureFlags.PROMISE_APPS_IN_ALL_APPS.get()) {
            enqueueModelUpdateTask(new BaseModelUpdateTask() {
                @Override
                public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                    apps.addPromiseApp(app.getContext(), sessionInfo);
                    bindApplicationsIfNeeded();
                }
            });
        }
    }

    @Override
    public void onSessionFailure(String packageName, UserHandle user) {
        if (!FeatureFlags.PROMISE_APPS_NEW_INSTALLS.get()) {
            return;
        }
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                final IntSparseArrayMap<Boolean> removedIds = new IntSparseArrayMap<>();
                synchronized (dataModel) {
                    for (ItemInfo info : dataModel.itemsIdMap) {
                        if (info instanceof WorkspaceItemInfo
                                && ((WorkspaceItemInfo) info).hasPromiseIconUi()
                                && user.equals(info.user)
                                && info.getIntent() != null
                                && TextUtils.equals(packageName, info.getIntent().getPackage())) {
                            removedIds.put(info.id, true /* remove */);
                        }
                    }
                }

                if (!removedIds.isEmpty()) {
                    deleteAndBindComponentsRemoved(ItemInfoMatcher.ofItemIds(removedIds, false));
                }
            }
        });
    }

    @Override
    public void onPackageStateChanged(PackageInstallInfo installInfo) {
        enqueueModelUpdateTask(new PackageInstallStateChangedTask(installInfo));
    }

    /**
     * Updates the icons and label of all pending icons for the provided package name.
     */
    @Override
    public void onUpdateSessionDisplay(PackageUserKey key, PackageInstaller.SessionInfo info) {
        mApp.getIconCache().updateSessionCache(key, info);

        HashSet<String> packages = new HashSet<>();
        packages.add(key.mPackageName);
        enqueueModelUpdateTask(new CacheDataUpdatedTask(
                CacheDataUpdatedTask.OP_SESSION_UPDATE, key.mUser, packages));
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
     * use partial updates similar to {@link UserCache}
     */
    public void refreshShortcutsIfRequired() {
        MODEL_EXECUTOR.getHandler().removeCallbacks(mShortcutPermissionCheckRunnable);
        MODEL_EXECUTOR.post(mShortcutPermissionCheckRunnable);
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

    /**
     * Called when the labels for the widgets has updated in the icon cache.
     */
    public void onWidgetLabelsUpdated(HashSet<String> updatedPackages, UserHandle user) {
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                dataModel.widgetsModel.onPackageIconsUpdated(updatedPackages, user, app);
                bindUpdatedWidgets(dataModel);
            }
        });
    }

    public void enqueueModelUpdateTask(ModelUpdateTask task) {
        task.init(mApp, this, mBgDataModel, mBgAllAppsList, mMainExecutor);
        MODEL_EXECUTOR.execute(task);
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

    public void updateAndBindWorkspaceItem(WorkspaceItemInfo si, ShortcutInfo info) {
        updateAndBindWorkspaceItem(() -> {
            si.updateFromDeepShortcutInfo(info, mApp.getContext());
            mApp.getIconCache().getShortcutIcon(si, info);
            return si;
        });
    }

    /**
     * Utility method to update a shortcut on the background thread.
     */
    public void updateAndBindWorkspaceItem(final Supplier<WorkspaceItemInfo> itemProvider) {
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                WorkspaceItemInfo info = itemProvider.get();
                getModelWriter().updateItemInDatabase(info);
                ArrayList<WorkspaceItemInfo> update = new ArrayList<>();
                update.add(info);
                bindUpdatedWorkspaceItems(update);
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

    public void dumpState(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args.length > 0 && TextUtils.equals(args[0], "--all")) {
            writer.println(prefix + "All apps list: size=" + mBgAllAppsList.data.size());
            for (AppInfo info : mBgAllAppsList.data) {
                writer.println(prefix + "   title=\"" + info.title
                        + "\" bitmapIcon=" + info.bitmap.icon
                        + " componentName=" + info.componentName.getPackageName());
            }
        }
        mBgDataModel.dump(prefix, fd, writer, args);
    }

    /**
     * Returns true if there are any callbacks attached to the model
     */
    public boolean hasCallbacks() {
        synchronized (mCallbacksList) {
            return !mCallbacksList.isEmpty();
        }
    }

    /**
     * Returns an array of currently attached callbacks
     */
    public Callbacks[] getCallbacks() {
        synchronized (mCallbacksList) {
            return mCallbacksList.toArray(new Callbacks[mCallbacksList.size()]);
        }
    }
}
