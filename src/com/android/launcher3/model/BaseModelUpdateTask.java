/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Extension of {@link ModelUpdateTask} with some utility methods
 */
public abstract class BaseModelUpdateTask implements ModelUpdateTask {

    private static final boolean DEBUG_TASKS = false;
    private static final String TAG = "BaseModelUpdateTask";

    // Nullabilities are explicitly omitted here because these are late-init fields,
    // They will be non-null after init(), which is always the case in enqueueModelUpdateTask().
    private LauncherAppState mApp;
    private LauncherModel mModel;
    private BgDataModel mDataModel;
    private AllAppsList mAllAppsList;
    private Executor mUiExecutor;

    public void init(@NonNull final LauncherAppState app, @NonNull final LauncherModel model,
            @NonNull final BgDataModel dataModel, @NonNull final AllAppsList allAppsList,
            @NonNull final Executor uiExecutor) {
        mApp = app;
        mModel = model;
        mDataModel = dataModel;
        mAllAppsList = allAppsList;
        mUiExecutor = uiExecutor;
    }

    @Override
    public final void run() {
        boolean isModelLoaded = Objects.requireNonNull(mModel).isModelLoaded();
        if (!isModelLoaded) {
            if (DEBUG_TASKS) {
                Log.d(TAG, "Ignoring model task since loader is pending=" + this);
            }
            // Loader has not yet run.
            return;
        }
        execute(mApp, mDataModel, mAllAppsList);
    }

    /**
     * Execute the actual task. Called on the worker thread.
     */
    public abstract void execute(@NonNull LauncherAppState app,
            @NonNull BgDataModel dataModel, @NonNull AllAppsList apps);

    /**
     * Schedules a {@param task} to be executed on the current callbacks.
     */
    public final void scheduleCallbackTask(@NonNull final CallbackTask task) {
        for (final Callbacks cb : mModel.getCallbacks()) {
            mUiExecutor.execute(() -> task.execute(cb));
        }
    }

    public ModelWriter getModelWriter() {
        // Updates from model task, do not deal with icon position in hotseat. Also no need to
        // verify changes as the ModelTasks always push the changes to callbacks
        return mModel.getWriter(false /* verifyChanges */, CellPosMapper.DEFAULT, null);
    }

    public void bindUpdatedWorkspaceItems(@NonNull final List<WorkspaceItemInfo> allUpdates) {
        // Bind workspace items
        List<WorkspaceItemInfo> workspaceUpdates = allUpdates.stream()
                .filter(info -> info.id != ItemInfo.NO_ID)
                .collect(Collectors.toList());
        if (!workspaceUpdates.isEmpty()) {
            scheduleCallbackTask(c -> c.bindWorkspaceItemsChanged(workspaceUpdates));
        }

        // Bind extra items if any
        allUpdates.stream()
                .mapToInt(info -> info.container)
                .distinct()
                .mapToObj(mDataModel.extraItems::get)
                .filter(Objects::nonNull)
                .forEach(this::bindExtraContainerItems);
    }

    public void bindExtraContainerItems(@NonNull final FixedContainerItems item) {
        scheduleCallbackTask(c -> c.bindExtraContainerItems(item));
    }

    public void bindDeepShortcuts(@NonNull final BgDataModel dataModel) {
        final HashMap<ComponentKey, Integer> shortcutMapCopy =
                new HashMap<>(dataModel.deepShortcutMap);
        scheduleCallbackTask(callbacks -> callbacks.bindDeepShortcutMap(shortcutMapCopy));
    }

    public void bindUpdatedWidgets(@NonNull final BgDataModel dataModel) {
        final ArrayList<WidgetsListBaseEntry> widgets =
                dataModel.widgetsModel.getWidgetsListForPicker(mApp.getContext());
        scheduleCallbackTask(c -> c.bindAllWidgets(widgets));
    }

    public void deleteAndBindComponentsRemoved(final Predicate<ItemInfo> matcher,
            @Nullable final String reason) {
        getModelWriter().deleteItemsFromDatabase(matcher, reason);

        // Call the components-removed callback
        scheduleCallbackTask(c -> c.bindWorkspaceComponentsRemoved(matcher));
    }

    public void bindApplicationsIfNeeded() {
        if (mAllAppsList.getAndResetChangeFlag()) {
            AppInfo[] apps = mAllAppsList.copyData();
            int flags = mAllAppsList.getFlags();
            Map<PackageUserKey, Integer> packageUserKeytoUidMap = Arrays.stream(apps).collect(
                    Collectors.toMap(
                            appInfo -> new PackageUserKey(appInfo.componentName.getPackageName(),
                                    appInfo.user), appInfo -> appInfo.uid, (a, b) -> a));
            scheduleCallbackTask(c -> c.bindAllApplications(apps, flags, packageUserKeytoUidMap));
        }
    }
}
