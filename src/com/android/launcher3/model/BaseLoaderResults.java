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

import static com.android.launcher3.model.ItemInstallQueue.FLAG_LOADER_RUNNING;
import static com.android.launcher3.model.ModelUtils.filterCurrentWorkspaceItems;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.os.Process;
import android.util.Log;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.RunnableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Base Helper class to handle results of {@link com.android.launcher3.model.LoaderTask}.
 */
public abstract class BaseLoaderResults {

    protected static final String TAG = "LoaderResults";
    protected static final int INVALID_SCREEN_ID = -1;
    private static final int ITEMS_CHUNK = 6; // batch size for the workspace icons

    protected final LooperExecutor mUiExecutor;

    protected final LauncherAppState mApp;
    protected final BgDataModel mBgDataModel;
    private final AllAppsList mBgAllAppsList;

    private final Callbacks[] mCallbacksList;

    private int mMyBindingId;

    public BaseLoaderResults(LauncherAppState app, BgDataModel dataModel,
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
    public void bindWorkspace(boolean incrementBindId) {
        // Save a copy of all the bg-thread collections
        ArrayList<ItemInfo> workspaceItems = new ArrayList<>();
        ArrayList<LauncherAppWidgetInfo> appWidgets = new ArrayList<>();
        final IntArray orderedScreenIds = new IntArray();
        ArrayList<FixedContainerItems> extraItems = new ArrayList<>();

        synchronized (mBgDataModel) {
            workspaceItems.addAll(mBgDataModel.workspaceItems);
            appWidgets.addAll(mBgDataModel.appWidgets);
            orderedScreenIds.addAll(mBgDataModel.collectWorkspaceScreens());
            mBgDataModel.extraItems.forEach(extraItems::add);
            if (incrementBindId) {
                mBgDataModel.lastBindId++;
            }
            mMyBindingId = mBgDataModel.lastBindId;
        }

        for (Callbacks cb : mCallbacksList) {
            new WorkspaceBinder(cb, mUiExecutor, mApp, mBgDataModel, mMyBindingId,
                    workspaceItems, appWidgets, extraItems, orderedScreenIds).bind();
        }
    }

    public abstract void bindDeepShortcuts();

    public void bindAllApps() {
        // shallow copy
        AppInfo[] apps = mBgAllAppsList.copyData();
        int flags = mBgAllAppsList.getFlags();
        executeCallbacksTask(c -> c.bindAllApplications(apps, flags), mUiExecutor);
    }

    public abstract void bindWidgets();

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

    public LooperIdleLock newIdleLock(Object lock) {
        LooperIdleLock idleLock = new LooperIdleLock(lock, mUiExecutor.getLooper());
        // If we are not binding or if the main looper is already idle, there is no reason to wait
        if (mUiExecutor.getLooper().getQueue().isIdle()) {
            idleLock.queueIdle();
        }
        return idleLock;
    }

    private class WorkspaceBinder {

        private final Executor mUiExecutor;
        private final Callbacks mCallbacks;

        private final LauncherAppState mApp;
        private final BgDataModel mBgDataModel;

        private final int mMyBindingId;
        private final ArrayList<ItemInfo> mWorkspaceItems;
        private final ArrayList<LauncherAppWidgetInfo> mAppWidgets;
        private final IntArray mOrderedScreenIds;
        private final ArrayList<FixedContainerItems> mExtraItems;

        WorkspaceBinder(Callbacks callbacks,
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

        private void bind() {
            final IntSet currentScreenIds =
                    mCallbacks.getPagesToBindSynchronously(mOrderedScreenIds);
            Objects.requireNonNull(currentScreenIds, "Null screen ids provided by " + mCallbacks);

            // Separate the items that are on the current screen, and all the other remaining items
            ArrayList<ItemInfo> currentWorkspaceItems = new ArrayList<>();
            ArrayList<ItemInfo> otherWorkspaceItems = new ArrayList<>();
            ArrayList<LauncherAppWidgetInfo> currentAppWidgets = new ArrayList<>();
            ArrayList<LauncherAppWidgetInfo> otherAppWidgets = new ArrayList<>();

            if (TestProtocol.sDebugTracing) {
                Log.d(TestProtocol.NULL_INT_SET, "bind (1) currentScreenIds: "
                        + currentScreenIds
                        + ", pointer: "
                        + mCallbacks
                        + ", name: "
                        + mCallbacks.getClass().getName());
            }
            filterCurrentWorkspaceItems(currentScreenIds, mWorkspaceItems, currentWorkspaceItems,
                    otherWorkspaceItems);
            if (TestProtocol.sDebugTracing) {
                Log.d(TestProtocol.NULL_INT_SET, "bind (2) currentScreenIds: "
                        + currentScreenIds);
            }
            filterCurrentWorkspaceItems(currentScreenIds, mAppWidgets, currentAppWidgets,
                    otherAppWidgets);
            final InvariantDeviceProfile idp = mApp.getInvariantDeviceProfile();
            sortWorkspaceItemsSpatially(idp, currentWorkspaceItems);
            sortWorkspaceItemsSpatially(idp, otherWorkspaceItems);

            // Tell the workspace that we're about to start binding items
            executeCallbacksTask(c -> {
                c.clearPendingBinds();
                c.startBinding();
            }, mUiExecutor);

            // Bind workspace screens
            executeCallbacksTask(c -> c.bindScreens(mOrderedScreenIds), mUiExecutor);

            // Load items on the current page.
            bindWorkspaceItems(currentWorkspaceItems, mUiExecutor);
            bindAppWidgets(currentAppWidgets, mUiExecutor);
            mExtraItems.forEach(item ->
                    executeCallbacksTask(c -> c.bindExtraContainerItems(item), mUiExecutor));

            RunnableList pendingTasks = new RunnableList();
            Executor pendingExecutor = pendingTasks::add;
            bindWorkspaceItems(otherWorkspaceItems, pendingExecutor);
            bindAppWidgets(otherAppWidgets, pendingExecutor);
            executeCallbacksTask(c -> c.finishBindingItems(currentScreenIds), pendingExecutor);
            pendingExecutor.execute(
                    () -> {
                        MODEL_EXECUTOR.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                        ItemInstallQueue.INSTANCE.get(mApp.getContext())
                                .resumeModelPush(FLAG_LOADER_RUNNING);
                    });

            executeCallbacksTask(
                    c -> {
                        MODEL_EXECUTOR.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        c.onInitialBindComplete(currentScreenIds, pendingTasks);
                    }, mUiExecutor);

            mCallbacks.bindStringCache(mBgDataModel.stringCache.clone());
        }

        private void bindWorkspaceItems(
                final ArrayList<ItemInfo> workspaceItems, final Executor executor) {
            // Bind the workspace items
            int count = workspaceItems.size();
            for (int i = 0; i < count; i += ITEMS_CHUNK) {
                final int start = i;
                final int chunkSize = (i + ITEMS_CHUNK <= count) ? ITEMS_CHUNK : (count - i);
                executeCallbacksTask(
                        c -> c.bindItems(workspaceItems.subList(start, start + chunkSize), false),
                        executor);
            }
        }

        private void bindAppWidgets(List<LauncherAppWidgetInfo> appWidgets, Executor executor) {
            // Bind the widgets, one at a time
            int count = appWidgets.size();
            for (int i = 0; i < count; i++) {
                final ItemInfo widget = appWidgets.get(i);
                executeCallbacksTask(
                        c -> c.bindItems(Collections.singletonList(widget), false), executor);
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
}
