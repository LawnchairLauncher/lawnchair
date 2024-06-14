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

import static android.app.admin.DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED;

import static com.android.launcher3.LauncherAppState.ACTION_FORCE_ROLOAD;
import static com.android.launcher3.LauncherPrefs.WORK_EDU_STEP;
import static com.android.launcher3.config.FeatureFlags.IS_STUDIO_BUILD;
import static com.android.launcher3.icons.cache.BaseIconCache.EMPTY_CLASS_NAME;
import static com.android.launcher3.model.PackageUpdatedTask.OP_UPDATE;
import static com.android.launcher3.pm.UserCache.ACTION_PROFILE_AVAILABLE;
import static com.android.launcher3.pm.UserCache.ACTION_PROFILE_UNAVAILABLE;
import static com.android.launcher3.testing.shared.TestProtocol.sDebugTracing;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.AddWorkspaceItemsTask;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BaseLauncherBinder;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.CacheDataUpdatedTask;
import com.android.launcher3.model.ItemInstallQueue;
import com.android.launcher3.model.LoaderTask;
import com.android.launcher3.model.ModelDbController;
import com.android.launcher3.model.ModelDelegate;
import com.android.launcher3.model.ModelLauncherCallbacks;
import com.android.launcher3.model.ModelTaskController;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.PackageInstallStateChangedTask;
import com.android.launcher3.model.PackageUpdatedTask;
import com.android.launcher3.model.ReloadStringCacheTask;
import com.android.launcher3.model.ShortcutsChangedTask;
import com.android.launcher3.model.UserLockStateChangedTask;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionTracker;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state
 * for the Launcher.
 */
public class LauncherModel implements InstallSessionTracker.Callback {
    private static final boolean DEBUG_RECEIVER = false;

    static final String TAG = "Launcher.Model";

    @NonNull
    private final LauncherAppState mApp;
    @NonNull
    private final PackageManagerHelper mPmHelper;
    @NonNull
    private final ModelDbController mModelDbController;
    @NonNull
    private final Object mLock = new Object();
    @Nullable
    private LoaderTask mLoaderTask;
    private boolean mIsLoaderTaskRunning;

    // only allow this once per reboot to reload work apps
    private boolean mShouldReloadWorkProfile = true;

    // Indicates whether the current model data is valid or not.
    // We start off with everything not loaded. After that, we assume that
    // our monitoring of the package manager provides all updates and we never
    // need to do a requery. This is only ever touched from the loader thread.
    private boolean mModelLoaded;
    private boolean mModelDestroyed = false;
    public boolean isModelLoaded() {
        synchronized (mLock) {
            return mModelLoaded && mLoaderTask == null && !mModelDestroyed;
        }
    }

    @NonNull
    private final ArrayList<Callbacks> mCallbacksList = new ArrayList<>(1);

    // < only access in worker thread >
    @NonNull
    private final AllAppsList mBgAllAppsList;

    /**
     * All the static data should be accessed on the background thread, A lock should be acquired
     * on this object when accessing any data from this model.
     */
    @NonNull
    private final BgDataModel mBgDataModel = new BgDataModel();

    @NonNull
    private final ModelDelegate mModelDelegate;

    private int mLastLoadId = -1;

    // Runnable to check if the shortcuts permission has changed.
    @NonNull
    private final Runnable mDataValidationCheck = new Runnable() {
        @Override
        public void run() {
            if (mModelLoaded) {
                mModelDelegate.validateData();
            }
        }
    };

    LauncherModel(@NonNull final Context context, @NonNull final LauncherAppState app,
            @NonNull final IconCache iconCache, @NonNull final AppFilter appFilter,
            @NonNull final PackageManagerHelper pmHelper, final boolean isPrimaryInstance) {
        mApp = app;
        mPmHelper = pmHelper;
        mModelDbController = new ModelDbController(context);
        mBgAllAppsList = new AllAppsList(iconCache, appFilter);
        mModelDelegate = ModelDelegate.newInstance(context, app, mPmHelper, mBgAllAppsList,
                mBgDataModel, isPrimaryInstance);
    }

    @NonNull
    public ModelDelegate getModelDelegate() {
        return mModelDelegate;
    }

    public ModelDbController getModelDbController() {
        return mModelDbController;
    }

    public ModelLauncherCallbacks newModelCallbacks() {
        return new ModelLauncherCallbacks(this::enqueueModelUpdateTask);
    }

    /**
     * Adds the provided items to the workspace.
     */
    public void addAndBindAddedWorkspaceItems(
            @NonNull final List<Pair<ItemInfo, Object>> itemList) {
        for (Callbacks cb : getCallbacks()) {
            cb.preAddApps();
        }
        enqueueModelUpdateTask(new AddWorkspaceItemsTask(itemList));
    }

    @NonNull
    public ModelWriter getWriter(final boolean verifyChanges, CellPosMapper cellPosMapper,
            @Nullable final Callbacks owner) {
        return new ModelWriter(mApp.getContext(), this, mBgDataModel, verifyChanges, cellPosMapper,
                owner);
    }

    /**
     * Called when the icon for an app changes, outside of package event
     */
    @WorkerThread
    public void onAppIconChanged(@NonNull final String packageName,
            @NonNull final UserHandle user) {
        // Update the icon for the calendar package
        Context context = mApp.getContext();
        enqueueModelUpdateTask(new PackageUpdatedTask(OP_UPDATE, user, packageName));

        List<ShortcutInfo> pinnedShortcuts = new ShortcutRequest(context, user)
                .forPackage(packageName).query(ShortcutRequest.PINNED);
        if (!pinnedShortcuts.isEmpty()) {
            enqueueModelUpdateTask(new ShortcutsChangedTask(packageName, pinnedShortcuts, user,
                    false));
        }
    }

    /**
     * Called when the workspace items have drastically changed
     */
    public void onWorkspaceUiChanged() {
        MODEL_EXECUTOR.execute(mModelDelegate::workspaceLoadComplete);
    }

    /**
     * Called when the model is destroyed
     */
    public void destroy() {
        mModelDestroyed = true;
        MODEL_EXECUTOR.execute(mModelDelegate::destroy);
    }

    public void onBroadcastIntent(@NonNull final Intent intent) {
        if (DEBUG_RECEIVER || sDebugTracing) Log.d(TAG, "onReceive intent=" + intent);
        final String action = intent.getAction();
        if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            // If we have changed locale we need to clear out the labels in all apps/workspace.
            forceReload();
        } else if (ACTION_DEVICE_POLICY_RESOURCE_UPDATED.equals(action)) {
            enqueueModelUpdateTask(new ReloadStringCacheTask(mModelDelegate));
        } else if (IS_STUDIO_BUILD && ACTION_FORCE_ROLOAD.equals(action)) {
            for (Callbacks cb : getCallbacks()) {
                if (cb instanceof Launcher) {
                    ((Launcher) cb).recreate();
                }
            }
        }
    }

    /**
     * Called then there use a user event
     * @see UserCache#addUserEventListener
     */
    public void onUserEvent(UserHandle user, String action) {
        if (Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action)
                && mShouldReloadWorkProfile) {
            mShouldReloadWorkProfile = false;
            forceReload();
        } else if (Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action)
                || Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action)) {
            mShouldReloadWorkProfile = false;
            enqueueModelUpdateTask(new PackageUpdatedTask(
                    PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE, user));
        } else if (UserCache.ACTION_PROFILE_LOCKED.equals(action)
                || UserCache.ACTION_PROFILE_UNLOCKED.equals(action)) {
            enqueueModelUpdateTask(new UserLockStateChangedTask(
                    user, UserCache.ACTION_PROFILE_UNLOCKED.equals(action)));
        } else if (UserCache.ACTION_PROFILE_ADDED.equals(action)
                || UserCache.ACTION_PROFILE_REMOVED.equals(action)) {
            forceReload();
        } else if (ACTION_PROFILE_AVAILABLE.equals(action)
                || ACTION_PROFILE_UNAVAILABLE.equals(action)) {
            /*
             * This broadcast is only available when android.os.Flags.allowPrivateProfile() is set.
             * For Work-profile this broadcast will be sent in addition to
             * ACTION_MANAGED_PROFILE_AVAILABLE/UNAVAILABLE.
             * So effectively, this if block only handles the non-work profile case.
             */
            enqueueModelUpdateTask(new PackageUpdatedTask(
                    PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE, user));
        }
        if (Intent.ACTION_MANAGED_PROFILE_REMOVED.equals(action)) {
            LauncherPrefs.get(mApp.getContext()).put(WORK_EDU_STEP, 0);
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
    public void removeCallbacks(@NonNull final Callbacks callbacks) {
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
    public boolean addCallbacksAndLoad(@NonNull final Callbacks callbacks) {
        synchronized (mLock) {
            addCallbacks(callbacks);
            return startLoader(new Callbacks[] { callbacks });

        }
    }

    /**
     * Adds a callbacks to receive model updates
     */
    public void addCallbacks(@NonNull final Callbacks callbacks) {
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
        return startLoader(new Callbacks[0]);
    }

    private boolean startLoader(@NonNull final Callbacks[] newCallbacks) {
        // Enable queue before starting loader. It will get disabled in Launcher#finishBindingItems
        ItemInstallQueue.INSTANCE.get(mApp.getContext())
                .pauseModelPush(ItemInstallQueue.FLAG_LOADER_RUNNING);
        synchronized (mLock) {
            // If there is already one running, tell it to stop.
            boolean wasRunning = stopLoader();
            boolean bindDirectly = mModelLoaded && !mIsLoaderTaskRunning;
            boolean bindAllCallbacks = wasRunning || !bindDirectly || newCallbacks.length == 0;
            final Callbacks[] callbacksList = bindAllCallbacks ? getCallbacks() : newCallbacks;

            if (callbacksList.length > 0) {
                // Clear any pending bind-runnables from the synchronized load process.
                for (Callbacks cb : callbacksList) {
                    MAIN_EXECUTOR.execute(cb::clearPendingBinds);
                }

                BaseLauncherBinder launcherBinder = new BaseLauncherBinder(
                        mApp, mBgDataModel, mBgAllAppsList, callbacksList);
                if (bindDirectly) {
                    // Divide the set of loaded items into those that we are binding synchronously,
                    // and everything else that is to be bound normally (asynchronously).
                    launcherBinder.bindWorkspace(bindAllCallbacks, /* isBindSync= */ true);
                    // For now, continue posting the binding of AllApps as there are other
                    // issues that arise from that.
                    launcherBinder.bindAllApps();
                    launcherBinder.bindDeepShortcuts();
                    launcherBinder.bindWidgets();
                    if (FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
                        mModelDelegate.bindAllModelExtras(callbacksList);
                    }
                    return true;
                } else {
                    stopLoader();
                    mLoaderTask = new LoaderTask(
                            mApp, mBgAllAppsList, mBgDataModel, mModelDelegate, launcherBinder);

                    // Always post the loader task, instead of running directly
                    // (even on same thread) so that we exit any nested synchronized blocks
                    MODEL_EXECUTOR.post(mLoaderTask);
                }
            }
        }
        return false;
    }

    /**
     * If there is already a loader task running, tell it to stop.
     * @return true if an existing loader was stopped.
     */
    private boolean stopLoader() {
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

    /**
     * Loads the model if not loaded
     * @param callback called with the data model upon successful load or null on model thread.
     */
    public void loadAsync(@NonNull final Consumer<BgDataModel> callback) {
        synchronized (mLock) {
            if (!mModelLoaded && !mIsLoaderTaskRunning) {
                startLoader();
            }
        }
        MODEL_EXECUTOR.post(() -> callback.accept(isModelLoaded() ? mBgDataModel : null));
    }

    @Override
    public void onInstallSessionCreated(@NonNull final PackageInstallInfo sessionInfo) {
        if (FeatureFlags.PROMISE_APPS_IN_ALL_APPS.get()) {
            enqueueModelUpdateTask((taskController, dataModel, apps) -> {
                apps.addPromiseApp(mApp.getContext(), sessionInfo);
                taskController.bindApplicationsIfNeeded();
            });
        }
    }

    @Override
    public void onSessionFailure(@NonNull final String packageName,
            @NonNull final UserHandle user) {
        enqueueModelUpdateTask((taskController, dataModel, apps) -> {
            IconCache iconCache = mApp.getIconCache();
            final IntSet removedIds = new IntSet();
            HashSet<WorkspaceItemInfo> archivedWorkspaceItemsToCacheRefresh = new HashSet<>();
            boolean isAppArchived = PackageManagerHelper.INSTANCE.get(mApp.getContext())
                    .isAppArchivedForUser(packageName, user);
            synchronized (dataModel) {
                if (isAppArchived) {
                    // Remove package icon cache entry for archived app in case of a session
                    // failure.
                    mApp.getIconCache().remove(
                            new ComponentName(packageName, packageName + EMPTY_CLASS_NAME),
                            user);
                }

                for (ItemInfo info : dataModel.itemsIdMap) {
                    if (info instanceof WorkspaceItemInfo
                            && ((WorkspaceItemInfo) info).hasPromiseIconUi()
                            && user.equals(info.user)
                            && info.getIntent() != null) {
                        if (TextUtils.equals(packageName, info.getIntent().getPackage())) {
                            removedIds.add(info.id);
                        }
                        if (((WorkspaceItemInfo) info).isArchived()) {
                            WorkspaceItemInfo workspaceItem = (WorkspaceItemInfo) info;
                            // Refresh icons on the workspace for archived apps.
                            iconCache.getTitleAndIcon(workspaceItem,
                                    workspaceItem.usingLowResIcon());
                            archivedWorkspaceItemsToCacheRefresh.add(workspaceItem);
                        }
                    }
                }

                if (isAppArchived) {
                    apps.updateIconsAndLabels(new HashSet<>(List.of(packageName)), user);
                }
            }

            if (!removedIds.isEmpty() && !isAppArchived) {
                taskController.deleteAndBindComponentsRemoved(
                        ItemInfoMatcher.ofItemIds(removedIds),
                        "removed because install session failed");
            }
            if (!archivedWorkspaceItemsToCacheRefresh.isEmpty()) {
                taskController.bindUpdatedWorkspaceItems(
                        archivedWorkspaceItemsToCacheRefresh.stream().toList());
            }
            if (isAppArchived) {
                taskController.bindApplicationsIfNeeded();
            }
        });
    }

    @Override
    public void onPackageStateChanged(@NonNull final PackageInstallInfo installInfo) {
        enqueueModelUpdateTask(new PackageInstallStateChangedTask(installInfo));
    }

    /**
     * Updates the icons and label of all pending icons for the provided package name.
     */
    @Override
    public void onUpdateSessionDisplay(@NonNull final PackageUserKey key,
            @NonNull final PackageInstaller.SessionInfo info) {
        mApp.getIconCache().updateSessionCache(key, info);

        HashSet<String> packages = new HashSet<>();
        packages.add(key.mPackageName);
        enqueueModelUpdateTask(new CacheDataUpdatedTask(
                CacheDataUpdatedTask.OP_SESSION_UPDATE, key.mUser, packages));
    }

    public class LoaderTransaction implements AutoCloseable {

        @NonNull
        private final LoaderTask mTask;

        private LoaderTransaction(@NonNull final LoaderTask task) throws CancellationException {
            synchronized (mLock) {
                if (mLoaderTask != task) {
                    throw new CancellationException("Loader already stopped");
                }
                mLastLoadId++;
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

    public LoaderTransaction beginLoader(@NonNull final LoaderTask task)
            throws CancellationException {
        return new LoaderTransaction(task);
    }

    /**
     * Refreshes the cached shortcuts if the shortcut permission has changed.
     * Current implementation simply reloads the workspace, but it can be optimized to
     * use partial updates similar to {@link UserCache}
     */
    public void validateModelDataOnResume() {
        MODEL_EXECUTOR.getHandler().removeCallbacks(mDataValidationCheck);
        MODEL_EXECUTOR.post(mDataValidationCheck);
    }

    /**
     * Called when the icons for packages have been updated in the icon cache.
     */
    public void onPackageIconsUpdated(@NonNull final HashSet<String> updatedPackages,
            @NonNull final UserHandle user) {
        // If any package icon has changed (app was updated while launcher was dead),
        // update the corresponding shortcuts.
        enqueueModelUpdateTask(new CacheDataUpdatedTask(
                CacheDataUpdatedTask.OP_CACHE_UPDATE, user, updatedPackages));
    }

    /**
     * Called when the labels for the widgets has updated in the icon cache.
     */
    public void onWidgetLabelsUpdated(@NonNull final HashSet<String> updatedPackages,
            @NonNull final UserHandle user) {
        enqueueModelUpdateTask((taskController, dataModel, apps) ->  {
            dataModel.widgetsModel.onPackageIconsUpdated(updatedPackages, user, mApp);
            taskController.bindUpdatedWidgets(dataModel);
        });
    }

    public void enqueueModelUpdateTask(@NonNull final ModelUpdateTask task) {
        if (mModelDestroyed) {
            return;
        }
        MODEL_EXECUTOR.execute(() -> {
            if (!isModelLoaded()) {
                // Loader has not yet run.
                return;
            }
            ModelTaskController controller = new ModelTaskController(
                    mApp, mBgDataModel, mBgAllAppsList, this, MAIN_EXECUTOR);
            task.execute(controller, mBgDataModel, mBgAllAppsList);
        });
    }

    /**
     * A task to be executed on the current callbacks on the UI thread.
     * If there is no current callbacks, the task is ignored.
     */
    public interface CallbackTask {

        void execute(@NonNull Callbacks callbacks);
    }

    public interface ModelUpdateTask {

        void execute(@NonNull ModelTaskController taskController,
                @NonNull BgDataModel dataModel, @NonNull AllAppsList apps);
    }

    public void updateAndBindWorkspaceItem(@NonNull final WorkspaceItemInfo si,
            @NonNull final ShortcutInfo info) {
        updateAndBindWorkspaceItem(() -> {
            si.updateFromDeepShortcutInfo(info, mApp.getContext());
            mApp.getIconCache().getShortcutIcon(si, info);
            return si;
        });
    }

    /**
     * Utility method to update a shortcut on the background thread.
     */
    public void updateAndBindWorkspaceItem(
            @NonNull final Supplier<WorkspaceItemInfo> itemProvider) {
        enqueueModelUpdateTask((taskController, dataModel, apps) ->  {
            WorkspaceItemInfo info = itemProvider.get();
            taskController.getModelWriter().updateItemInDatabase(info);
            ArrayList<WorkspaceItemInfo> update = new ArrayList<>();
            update.add(info);
            taskController.bindUpdatedWorkspaceItems(update);
        });
    }

    public void refreshAndBindWidgetsAndShortcuts(@Nullable final PackageUserKey packageUser) {
        enqueueModelUpdateTask((taskController, dataModel, apps) ->  {
            dataModel.widgetsModel.update(taskController.getApp(), packageUser);
            taskController.bindUpdatedWidgets(dataModel);
        });
    }

    public void dumpState(@Nullable final String prefix, @Nullable final FileDescriptor fd,
            @NonNull final PrintWriter writer, @NonNull final String[] args) {
        if (args.length > 0 && TextUtils.equals(args[0], "--all")) {
            writer.println(prefix + "All apps list: size=" + mBgAllAppsList.data.size());
            for (AppInfo info : mBgAllAppsList.data) {
                writer.println(prefix + "   title=\"" + info.title
                        + "\" bitmapIcon=" + info.bitmap.icon
                        + " componentName=" + info.componentName.getPackageName());
            }
            writer.println();
        }
        mModelDelegate.dump(prefix, fd, writer, args);
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
    @NonNull
    public Callbacks[] getCallbacks() {
        synchronized (mCallbacksList) {
            return mCallbacksList.toArray(new Callbacks[mCallbacksList.size()]);
        }
    }

    /**
     * Returns the ID for the last model load. If the load ID doesn't match for a transaction, the
     * transaction should be ignored.
     */
    public int getLastLoadId() {
        return mLastLoadId;
    }
}
