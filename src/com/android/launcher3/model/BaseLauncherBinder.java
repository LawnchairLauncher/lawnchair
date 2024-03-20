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

import static com.android.launcher3.Flags.enableWorkspaceInflation;
import static com.android.launcher3.config.FeatureFlags.ENABLE_SMARTSPACE_REMOVAL;
import static com.android.launcher3.model.ItemInstallQueue.FLAG_LOADER_RUNNING;
import static com.android.launcher3.model.ModelUtils.filterCurrentWorkspaceItems;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.os.Process;
import android.os.Trace;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Workspace;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInflater;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.RunnableList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Binds the results of {@link com.android.launcher3.model.LoaderTask} to the Callbacks objects.
 */
public abstract class BaseLauncherBinder {

    protected static final String TAG = "LauncherBinder";
    private static final int ITEMS_CHUNK = 6; // batch size for the workspace icons

    protected final LooperExecutor mUiExecutor;

    protected final LauncherAppState mApp;
    protected final BgDataModel mBgDataModel;
    private final AllAppsList mBgAllAppsList;

    final Callbacks[] mCallbacksList;

    private int mMyBindingId;

    public BaseLauncherBinder(LauncherAppState app, BgDataModel dataModel,
            AllAppsList allAppsList, Callbacks[] callbacksList, LooperExecutor uiExecutor) {
        mUiExecutor = uiExecutor;
        mApp = app;
        mBgDataModel = dataModel;
        mBgAllAppsList = allAppsList;
        mCallbacksList = callbacksList;
    }

    /**
     * Binds all loaded data to actual views on the main thread.
     */
    public void bindWorkspace(boolean incrementBindId, boolean isBindSync) {
        Trace.beginSection("BaseLauncherBinder#bindWorkspace");
        try {
            if (FeatureFlags.ENABLE_WORKSPACE_LOADING_OPTIMIZATION.get()) {
                DisjointWorkspaceBinder workspaceBinder =
                    initWorkspaceBinder(incrementBindId, mBgDataModel.collectWorkspaceScreens());
                workspaceBinder.bindCurrentWorkspacePages(isBindSync);
                workspaceBinder.bindOtherWorkspacePages();
            } else {
                bindWorkspaceAllAtOnce(incrementBindId, isBindSync);
            }
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Initializes the WorkspaceBinder for binding.
     *
     * @param incrementBindId this is used to stop previously started binding tasks that are
     *                        obsolete but still queued.
     * @param workspacePages this allows the Launcher to add the correct workspace screens.
     */
    public DisjointWorkspaceBinder initWorkspaceBinder(boolean incrementBindId,
            IntArray workspacePages) {

        synchronized (mBgDataModel) {
            if (incrementBindId) {
                mBgDataModel.lastBindId++;
                mBgDataModel.lastLoadId = mApp.getModel().getLastLoadId();
            }
            mMyBindingId = mBgDataModel.lastBindId;
            return new DisjointWorkspaceBinder(workspacePages);
        }
    }

    private void bindWorkspaceAllAtOnce(boolean incrementBindId, boolean isBindSync) {
        // Save a copy of all the bg-thread collections
        ArrayList<ItemInfo> workspaceItems = new ArrayList<>();
        ArrayList<LauncherAppWidgetInfo> appWidgets = new ArrayList<>();
        final IntArray orderedScreenIds = new IntArray();
        ArrayList<FixedContainerItems> extraItems = new ArrayList<>();
        final int workspaceItemCount;
        synchronized (mBgDataModel) {
            workspaceItems.addAll(mBgDataModel.workspaceItems);
            appWidgets.addAll(mBgDataModel.appWidgets);
            orderedScreenIds.addAll(mBgDataModel.collectWorkspaceScreens());
            mBgDataModel.extraItems.forEach(extraItems::add);
            if (incrementBindId) {
                mBgDataModel.lastBindId++;
                mBgDataModel.lastLoadId = mApp.getModel().getLastLoadId();
            }
            mMyBindingId = mBgDataModel.lastBindId;
            workspaceItemCount = mBgDataModel.itemsIdMap.size();
        }

        for (Callbacks cb : mCallbacksList) {
            new UnifiedWorkspaceBinder(cb, mUiExecutor, mApp, mBgDataModel, mMyBindingId,
                    workspaceItems, appWidgets, extraItems, orderedScreenIds)
                    .bind(isBindSync, workspaceItemCount);
        }
    }

    /**
     * BindDeepShortcuts is abstract because it is a no-op for the go launcher.
     */
    public abstract void bindDeepShortcuts();

    /**
     * Binds the all apps results from LoaderTask to the callbacks UX.
     */
    public void bindAllApps() {
        // shallow copy
        AppInfo[] apps = mBgAllAppsList.copyData();
        int flags = mBgAllAppsList.getFlags();
        Map<PackageUserKey, Integer> packageUserKeytoUidMap = Arrays.stream(apps).collect(
                Collectors.toMap(
                        appInfo -> new PackageUserKey(appInfo.componentName.getPackageName(),
                                appInfo.user), appInfo -> appInfo.uid, (a, b) -> a));
        executeCallbacksTask(c -> c.bindAllApplications(apps, flags, packageUserKeytoUidMap),
                mUiExecutor);
    }

    /**
     * bindWidgets is abstract because it is a no-op for the go launcher.
     */
    public abstract void bindWidgets();

    /**
     * bindWidgets is abstract because it is a no-op for the go launcher.
     */
    public abstract void bindSmartspaceWidget();

    /**
     * Sorts the set of items by hotseat, workspace (spatially from top to bottom, left to right)
     */
    protected void sortWorkspaceItemsSpatially(InvariantDeviceProfile profile,
            ArrayList<ItemInfo> workspaceItems) {
        final int screenCols = profile.numColumns;
        final int screenCellCount = profile.numColumns * profile.numRows;
        Collections.sort(workspaceItems, (lhs, rhs) -> {
            if (lhs.container == rhs.container) {
                // Within containers, order by their spatial position in that container
                switch (lhs.container) {
                    case LauncherSettings.Favorites.CONTAINER_DESKTOP: {
                        int lr = (lhs.screenId * screenCellCount + lhs.cellY * screenCols
                                + lhs.cellX);
                        int rr = (rhs.screenId * screenCellCount + +rhs.cellY * screenCols
                                + rhs.cellX);
                        return Integer.compare(lr, rr);
                    }
                    case LauncherSettings.Favorites.CONTAINER_HOTSEAT: {
                        // We currently use the screen id as the rank
                        return Integer.compare(lhs.screenId, rhs.screenId);
                    }
                    default:
                        if (FeatureFlags.IS_STUDIO_BUILD) {
                            throw new RuntimeException(
                                    "Unexpected container type when sorting workspace items.");
                        }
                        return 0;
                }
            } else {
                // Between containers, order by hotseat, desktop
                return Integer.compare(lhs.container, rhs.container);
            }
        });
    }

    protected void executeCallbacksTask(CallbackTask task, Executor executor) {
        executor.execute(() -> {
            if (mMyBindingId != mBgDataModel.lastBindId) {
                Log.d(TAG, "Too many consecutive reloads, skipping obsolete data-bind");
                return;
            }
            for (Callbacks cb : mCallbacksList) {
                task.execute(cb);
            }
        });
    }

    /**
     * Only used in LoaderTask.
     */
    public LooperIdleLock newIdleLock(Object lock) {
        LooperIdleLock idleLock = new LooperIdleLock(lock, mUiExecutor.getLooper());
        // If we are not binding or if the main looper is already idle, there is no reason to wait
        if (mUiExecutor.getLooper().getQueue().isIdle()) {
            idleLock.queueIdle();
        }
        return idleLock;
    }

    private class UnifiedWorkspaceBinder {

        private final Executor mUiExecutor;
        private final Callbacks mCallbacks;

        private final LauncherAppState mApp;
        private final BgDataModel mBgDataModel;

        private final int mMyBindingId;
        private final ArrayList<ItemInfo> mWorkspaceItems;
        private final ArrayList<LauncherAppWidgetInfo> mAppWidgets;
        private final IntArray mOrderedScreenIds;
        private final ArrayList<FixedContainerItems> mExtraItems;

        UnifiedWorkspaceBinder(Callbacks callbacks,
                Executor uiExecutor,
                LauncherAppState app,
                BgDataModel bgDataModel,
                int myBindingId,
                ArrayList<ItemInfo> workspaceItems,
                ArrayList<LauncherAppWidgetInfo> appWidgets,
                ArrayList<FixedContainerItems> extraItems,
                IntArray orderedScreenIds) {
            mCallbacks = callbacks;
            mUiExecutor = uiExecutor;
            mApp = app;
            mBgDataModel = bgDataModel;
            mMyBindingId = myBindingId;
            mWorkspaceItems = workspaceItems;
            mAppWidgets = appWidgets;
            mExtraItems = extraItems;
            mOrderedScreenIds = orderedScreenIds;
        }

        private void bind(boolean isBindSync, int workspaceItemCount) {
            final IntSet currentScreenIds =
                    mCallbacks.getPagesToBindSynchronously(mOrderedScreenIds);
            Objects.requireNonNull(currentScreenIds, "Null screen ids provided by " + mCallbacks);

            // Separate the items that are on the current screen, and all the other remaining items
            ArrayList<ItemInfo> currentWorkspaceItems = new ArrayList<>();
            ArrayList<ItemInfo> otherWorkspaceItems = new ArrayList<>();
            ArrayList<ItemInfo> currentAppWidgets = new ArrayList<>();
            ArrayList<ItemInfo> otherAppWidgets = new ArrayList<>();

            filterCurrentWorkspaceItems(currentScreenIds, mWorkspaceItems, currentWorkspaceItems,
                    otherWorkspaceItems);
            filterCurrentWorkspaceItems(currentScreenIds, mAppWidgets, currentAppWidgets,
                    otherAppWidgets);
            final InvariantDeviceProfile idp = mApp.getInvariantDeviceProfile();
            sortWorkspaceItemsSpatially(idp, currentWorkspaceItems);
            sortWorkspaceItemsSpatially(idp, otherWorkspaceItems);

            // Tell the workspace that we're about to start binding items
            executeCallbacksTask(c -> {
                c.clearPendingBinds();
                c.startBinding();
                if (ENABLE_SMARTSPACE_REMOVAL.get()) {
                    c.setIsFirstPagePinnedItemEnabled(
                            mBgDataModel.isFirstPagePinnedItemEnabled);
                }
            }, mUiExecutor);

            // Bind workspace screens
            executeCallbacksTask(c -> c.bindScreens(mOrderedScreenIds), mUiExecutor);

            ItemInflater inflater = mCallbacks.getItemInflater();

            // Load items on the current page.
            if (enableWorkspaceInflation() && inflater != null) {
                inflateAsyncAndBind(currentWorkspaceItems, inflater, mUiExecutor);
                inflateAsyncAndBind(currentAppWidgets, inflater, mUiExecutor);
            } else {
                bindItemsInChunks(currentWorkspaceItems, ITEMS_CHUNK, mUiExecutor);
                bindItemsInChunks(currentAppWidgets, 1, mUiExecutor);
            }
            if (!FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
                mExtraItems.forEach(item ->
                        executeCallbacksTask(c -> c.bindExtraContainerItems(item), mUiExecutor));
            }

            RunnableList pendingTasks = new RunnableList();
            Executor pendingExecutor = pendingTasks::add;

            RunnableList onCompleteSignal = new RunnableList();

            if (enableWorkspaceInflation() && inflater != null) {
                MODEL_EXECUTOR.execute(() ->  {
                    inflateAsyncAndBind(otherWorkspaceItems, inflater, pendingExecutor);
                    inflateAsyncAndBind(otherAppWidgets, inflater, pendingExecutor);
                    setupPendingBind(currentScreenIds, pendingExecutor);

                    // Wait for the async inflation to complete and then notify the completion
                    // signal on UI thread.
                    MAIN_EXECUTOR.execute(onCompleteSignal::executeAllAndDestroy);
                });
            } else {
                bindItemsInChunks(otherWorkspaceItems, ITEMS_CHUNK, pendingExecutor);
                bindItemsInChunks(otherAppWidgets, 1, pendingExecutor);
                setupPendingBind(currentScreenIds, pendingExecutor);
                onCompleteSignal.executeAllAndDestroy();
            }

            executeCallbacksTask(
                    c -> {
                        if (!enableWorkspaceInflation()) {
                            MODEL_EXECUTOR.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        }
                        c.onInitialBindComplete(currentScreenIds, pendingTasks, onCompleteSignal,
                                workspaceItemCount, isBindSync);
                    }, mUiExecutor);
        }

        private void setupPendingBind(
                IntSet currentScreenIds,
                Executor pendingExecutor) {
            StringCache cacheClone = mBgDataModel.stringCache.clone();
            executeCallbacksTask(c -> c.bindStringCache(cacheClone), pendingExecutor);

            executeCallbacksTask(c -> c.finishBindingItems(currentScreenIds), pendingExecutor);
            pendingExecutor.execute(
                    () -> {
                        MODEL_EXECUTOR.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                        ItemInstallQueue.INSTANCE.get(mApp.getContext())
                                .resumeModelPush(FLAG_LOADER_RUNNING);
                    });
        }

        /**
         * Tries to inflate the items asynchronously and bind. Returns true on success or false if
         * async-binding is not supported in this case.
         */
        private void inflateAsyncAndBind(
                List<ItemInfo> items, @NonNull ItemInflater inflater, Executor executor) {
            if (mMyBindingId != mBgDataModel.lastBindId) {
                Log.d(TAG, "Too many consecutive reloads, skipping obsolete view inflation");
                return;
            }

            ModelWriter writer = mApp.getModel()
                    .getWriter(false /* verifyChanges */, CellPosMapper.DEFAULT, null);
            List<Pair<ItemInfo, View>> bindItems = items.stream().map(i ->
                    Pair.create(i, inflater.inflateItem(i, writer, null))).toList();
            executeCallbacksTask(c -> c.bindInflatedItems(bindItems), executor);
        }

        private void bindItemsInChunks(
                List<ItemInfo> workspaceItems, int chunkCount, Executor executor) {
            // Bind the workspace items
            int count = workspaceItems.size();
            for (int i = 0; i < count; i += chunkCount) {
                final int start = i;
                final int chunkSize = (i + chunkCount <= count) ? chunkCount : (count - i);
                executeCallbacksTask(
                        c -> c.bindItems(workspaceItems.subList(start, start + chunkSize), false),
                        executor);
            }
        }

        protected void executeCallbacksTask(CallbackTask task, Executor executor) {
            executor.execute(() -> {
                if (mMyBindingId != mBgDataModel.lastBindId) {
                    Log.d(TAG, "Too many consecutive reloads, skipping obsolete data-bind");
                    return;
                }
                task.execute(mCallbacks);
            });
        }
    }

    private class DisjointWorkspaceBinder {
        private final IntArray mOrderedScreenIds;
        private final IntSet mCurrentScreenIds = new IntSet();
        private final Set<Integer> mBoundItemIds = new HashSet<>();

        protected DisjointWorkspaceBinder(IntArray orderedScreenIds) {
            mOrderedScreenIds = orderedScreenIds;

            for (Callbacks cb : mCallbacksList) {
                mCurrentScreenIds.addAll(cb.getPagesToBindSynchronously(orderedScreenIds));
            }
            if (mCurrentScreenIds.size() == 0) {
                mCurrentScreenIds.add(Workspace.FIRST_SCREEN_ID);
            }
        }

        /**
         * Binds the currently loaded items in the Data Model. Also signals to the Callbacks[]
         * that these items have been bound and their respective screens are ready to be shown.
         *
         * If this method is called after all the items on the workspace screen have already been
         * loaded, it will bind all workspace items immediately, and bindOtherWorkspacePages() will
         * not bind any items.
         */
        protected void bindCurrentWorkspacePages(boolean isBindSync) {
            // Save a copy of all the bg-thread collections
            ArrayList<ItemInfo> workspaceItems;
            ArrayList<LauncherAppWidgetInfo> appWidgets;
            ArrayList<FixedContainerItems> fciList = new ArrayList<>();
            final int workspaceItemCount;
            synchronized (mBgDataModel) {
                workspaceItems = new ArrayList<>(mBgDataModel.workspaceItems);
                appWidgets = new ArrayList<>(mBgDataModel.appWidgets);
                if (!FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
                    mBgDataModel.extraItems.forEach(fciList::add);
                }
                workspaceItemCount = mBgDataModel.itemsIdMap.size();
            }

            workspaceItems.forEach(it -> mBoundItemIds.add(it.id));
            appWidgets.forEach(it -> mBoundItemIds.add(it.id));
            if (!FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
                fciList.forEach(item ->
                        executeCallbacksTask(c -> c.bindExtraContainerItems(item), mUiExecutor));
            }

            sortWorkspaceItemsSpatially(mApp.getInvariantDeviceProfile(), workspaceItems);

            // Tell the workspace that we're about to start binding items
            executeCallbacksTask(c -> {
                c.clearPendingBinds();
                c.startBinding();
            }, mUiExecutor);

            // Bind workspace screens
            executeCallbacksTask(c -> c.bindScreens(mOrderedScreenIds), mUiExecutor);

            bindWorkspaceItems(workspaceItems);
            bindAppWidgets(appWidgets);
            executeCallbacksTask(c -> {
                MODEL_EXECUTOR.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

                RunnableList onCompleteSignal = new RunnableList();
                onCompleteSignal.executeAllAndDestroy();
                c.onInitialBindComplete(mCurrentScreenIds, new RunnableList(), onCompleteSignal,
                        workspaceItemCount, isBindSync);
            }, mUiExecutor);
        }

        protected void bindOtherWorkspacePages() {
            // Save a copy of all the bg-thread collections
            ArrayList<ItemInfo> workspaceItems;
            ArrayList<LauncherAppWidgetInfo> appWidgets;

            synchronized (mBgDataModel) {
                workspaceItems = new ArrayList<>(mBgDataModel.workspaceItems);
                appWidgets = new ArrayList<>(mBgDataModel.appWidgets);
            }

            workspaceItems.removeIf(it -> mBoundItemIds.contains(it.id));
            appWidgets.removeIf(it -> mBoundItemIds.contains(it.id));

            sortWorkspaceItemsSpatially(mApp.getInvariantDeviceProfile(), workspaceItems);

            bindWorkspaceItems(workspaceItems);
            bindAppWidgets(appWidgets);

            executeCallbacksTask(c -> c.finishBindingItems(mCurrentScreenIds), mUiExecutor);
            mUiExecutor.execute(() -> {
                MODEL_EXECUTOR.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                ItemInstallQueue.INSTANCE.get(mApp.getContext())
                        .resumeModelPush(FLAG_LOADER_RUNNING);
            });

            StringCache cacheClone = mBgDataModel.stringCache.clone();
            executeCallbacksTask(c -> c.bindStringCache(cacheClone), mUiExecutor);
        }

        private void bindWorkspaceItems(final ArrayList<ItemInfo> workspaceItems) {
            // Bind the workspace items
            int count = workspaceItems.size();
            for (int i = 0; i < count; i += ITEMS_CHUNK) {
                final int start = i;
                final int chunkSize = (i + ITEMS_CHUNK <= count) ? ITEMS_CHUNK : (count - i);
                executeCallbacksTask(
                        c -> c.bindItems(workspaceItems.subList(start, start + chunkSize), false),
                        mUiExecutor);
            }
        }

        private void bindAppWidgets(List<LauncherAppWidgetInfo> appWidgets) {
            // Bind the widgets, one at a time
            int count = appWidgets.size();
            for (int i = 0; i < count; i++) {
                final ItemInfo widget = appWidgets.get(i);
                executeCallbacksTask(
                        c -> c.bindItems(Collections.singletonList(widget), false),
                        mUiExecutor);
            }
        }
    }
}
