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

import static com.android.launcher3.BuildConfigs.WIDGETS_ENABLED;
import static com.android.launcher3.Flags.enableSmartspaceRemovalToggle;
import static com.android.launcher3.Flags.enableWorkspaceInflation;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.model.ItemInstallQueue.FLAG_LOADER_RUNNING;
import static com.android.launcher3.model.ModelUtils.WIDGET_FILTER;
import static com.android.launcher3.model.ModelUtils.currentScreenContentFilter;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.os.Trace;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.ItemInflater;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.widget.model.WidgetsListBaseEntriesBuilder;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Binds the results of {@link com.android.launcher3.model.LoaderTask} to the Callbacks objects.
 */
public class BaseLauncherBinder {

    protected static final String TAG = "LauncherBinder";
    private static final int ITEMS_CHUNK = 6; // batch size for the workspace icons

    protected final LooperExecutor mUiExecutor;

    private final Context mContext;
    private final InvariantDeviceProfile mIDP;
    private final LauncherModel mModel;
    protected final BgDataModel mBgDataModel;
    private final AllAppsList mBgAllAppsList;

    final Callbacks[] mCallbacksList;

    private int mMyBindingId;

    @AssistedInject
    public BaseLauncherBinder(
            @ApplicationContext Context context,
            InvariantDeviceProfile idp,
            LauncherModel model,
            BgDataModel dataModel,
            AllAppsList allAppsList,
            @Assisted Callbacks[] callbacksList) {
        mUiExecutor = MAIN_EXECUTOR;
        mContext = context;
        mIDP = idp;
        mModel = model;
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
            // Save a copy of all the bg-thread collections
            IntSparseArrayMap<ItemInfo> itemsIdMap;
            final IntArray orderedScreenIds = new IntArray();
            ArrayList<FixedContainerItems> extraItems = new ArrayList<>();
            final int workspaceItemCount;
            synchronized (mBgDataModel) {
                itemsIdMap = mBgDataModel.itemsIdMap.clone();
                orderedScreenIds.addAll(mBgDataModel.collectWorkspaceScreens());
                mBgDataModel.extraItems.forEach(extraItems::add);
                if (incrementBindId) {
                    mBgDataModel.lastBindId++;
                    mBgDataModel.lastLoadId = mModel.getLastLoadId();
                }
                mMyBindingId = mBgDataModel.lastBindId;
                workspaceItemCount = mBgDataModel.itemsIdMap.size();
            }

            for (Callbacks cb : mCallbacksList) {
                new UnifiedWorkspaceBinder(cb, itemsIdMap, extraItems, orderedScreenIds)
                        .bind(isBindSync, workspaceItemCount);
            }
        } finally {
            Trace.endSection();
        }
    }

    /**
     * BindDeepShortcuts is abstract because it is a no-op for the go launcher.
     */
    public void bindDeepShortcuts() {
        if (!WIDGETS_ENABLED) {
            return;
        }
        final HashMap<ComponentKey, Integer> shortcutMapCopy;
        synchronized (mBgDataModel) {
            shortcutMapCopy = new HashMap<>(mBgDataModel.deepShortcutMap);
        }
        executeCallbacksTask(c -> c.bindDeepShortcutMap(shortcutMapCopy), mUiExecutor);
    }

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
    public void bindWidgets() {
        if (!WIDGETS_ENABLED) {
            return;
        }
        List<WidgetsListBaseEntry> widgets = new WidgetsListBaseEntriesBuilder(mContext)
                .build(mBgDataModel.widgetsModel.getWidgetsByPackageItemForPicker());
        executeCallbacksTask(c -> c.bindAllWidgets(widgets), mUiExecutor);
    }

    /**
     * bindWidgets is abstract because it is a no-op for the go launcher.
     */
    public void bindSmartspaceWidget() {
        if (!WIDGETS_ENABLED) {
            return;
        }
        executeCallbacksTask(c -> c.bindSmartspaceWidget(), mUiExecutor);
    }

    /**
     * Sorts the set of items by hotseat, workspace (spatially from top to bottom, left to right)
     */
    protected void sortWorkspaceItemsSpatially(ArrayList<ItemInfo> workspaceItems) {
        final int screenCols = mIDP.numColumns;
        final int screenCellCount = mIDP.numColumns * mIDP.numRows;
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

        private final Callbacks mCallbacks;

        private final IntSparseArrayMap<ItemInfo> mItemIdMap;
        private final IntArray mOrderedScreenIds;
        private final ArrayList<FixedContainerItems> mExtraItems;

        UnifiedWorkspaceBinder(
                Callbacks callbacks,
                IntSparseArrayMap<ItemInfo> itemIdMap,
                ArrayList<FixedContainerItems> extraItems,
                IntArray orderedScreenIds) {
            mCallbacks = callbacks;
            mItemIdMap = itemIdMap;
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

            Predicate<ItemInfo> currentScreenCheck = currentScreenContentFilter(currentScreenIds);
            mItemIdMap.forEach(item -> {
                if (currentScreenCheck.test(item)) {
                    (WIDGET_FILTER.test(item) ? currentAppWidgets : currentWorkspaceItems)
                            .add(item);
                } else if (item.container == CONTAINER_DESKTOP) {
                    (WIDGET_FILTER.test(item) ? otherAppWidgets : otherWorkspaceItems).add(item);
                }
            });
            sortWorkspaceItemsSpatially(currentWorkspaceItems);
            sortWorkspaceItemsSpatially(otherWorkspaceItems);

            // Tell the workspace that we're about to start binding items
            executeCallbacksTask(c -> {
                c.clearPendingBinds();
                c.startBinding();
                if (enableSmartspaceRemovalToggle()) {
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
            mExtraItems.forEach(item ->
                    executeCallbacksTask(c -> c.bindExtraContainerItems(item), mUiExecutor));

            RunnableList pendingTasks = new RunnableList();
            Executor pendingExecutor = pendingTasks::add;

            RunnableList onCompleteSignal = new RunnableList();
            onCompleteSignal.add(() -> Log.d(TAG, "Calling onCompleteSignal"));

            if (enableWorkspaceInflation() && inflater != null) {
                Log.d(TAG, "Starting async inflation");
                MODEL_EXECUTOR.execute(() ->  {
                    inflateAsyncAndBind(otherWorkspaceItems, inflater, pendingExecutor);
                    inflateAsyncAndBind(otherAppWidgets, inflater, pendingExecutor);
                    setupPendingBind(currentScreenIds, pendingExecutor);

                    // Wait for the async inflation to complete and then notify the completion
                    // signal on UI thread.
                    MAIN_EXECUTOR.execute(onCompleteSignal::executeAllAndDestroy);
                });
            } else {
                Log.d(TAG, "Starting sync inflation");
                bindItemsInChunks(otherWorkspaceItems, ITEMS_CHUNK, pendingExecutor);
                bindItemsInChunks(otherAppWidgets, 1, pendingExecutor);
                setupPendingBind(currentScreenIds, pendingExecutor);
                onCompleteSignal.executeAllAndDestroy();
            }

            executeCallbacksTask(c -> c.onInitialBindComplete(currentScreenIds, pendingTasks,
                    onCompleteSignal, workspaceItemCount, isBindSync), mUiExecutor);
        }

        private void setupPendingBind(
                IntSet currentScreenIds,
                Executor pendingExecutor) {
            StringCache cacheClone = mBgDataModel.stringCache.clone();
            executeCallbacksTask(c -> c.bindStringCache(cacheClone), pendingExecutor);

            executeCallbacksTask(c -> c.finishBindingItems(currentScreenIds), pendingExecutor);
            pendingExecutor.execute(() -> ItemInstallQueue.INSTANCE.get(mContext)
                    .resumeModelPush(FLAG_LOADER_RUNNING));
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

            ModelWriter writer = mModel.getWriter(
                    false /* verifyChanges */, CellPosMapper.DEFAULT, null);
            List<Pair<ItemInfo, View>> bindItems = items.stream()
                    .map(i -> Pair.create(i, inflater.inflateItem(i, writer, null)))
                    .collect(Collectors.toList());
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

    @AssistedFactory
    public interface BaseLauncherBinderFactory {
        BaseLauncherBinder createBinder(Callbacks[] callbacks);
    }
}
