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

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Extension of {@link ModelUpdateTask} with some utility methods
 */
public abstract class BaseModelUpdateTask implements ModelUpdateTask {

    private static final boolean DEBUG_TASKS = false;
    private static final String TAG = "BaseModelUpdateTask";

    private LauncherAppState mApp;
    private LauncherModel mModel;
    private BgDataModel mDataModel;
    private AllAppsList mAllAppsList;
    private Executor mUiExecutor;

    public void init(LauncherAppState app, LauncherModel model,
            BgDataModel dataModel, AllAppsList allAppsList, Executor uiExecutor) {
        mApp = app;
        mModel = model;
        mDataModel = dataModel;
        mAllAppsList = allAppsList;
        mUiExecutor = uiExecutor;
    }

    @Override
    public final void run() {
        if (!mModel.isModelLoaded()) {
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
    public abstract void execute(
            LauncherAppState app, BgDataModel dataModel, AllAppsList apps);

    /**
     * Schedules a {@param task} to be executed on the current callbacks.
     */
    public final void scheduleCallbackTask(final CallbackTask task) {
        for (final Callbacks cb : mModel.getCallbacks()) {
            mUiExecutor.execute(() -> task.execute(cb));
        }
    }

    public ModelWriter getModelWriter() {
        // Updates from model task, do not deal with icon position in hotseat. Also no need to
        // verify changes as the ModelTasks always push the changes to callbacks
        return mModel.getWriter(false /* hasVerticalHotseat */, false /* verifyChanges */);
    }

    public void bindUpdatedWorkspaceItems(List<WorkspaceItemInfo> allUpdates) {
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

    public void bindExtraContainerItems(FixedContainerItems item) {
        FixedContainerItems copy = item.clone();
        scheduleCallbackTask(c -> c.bindExtraContainerItems(copy));
    }

    public void bindDeepShortcuts(BgDataModel dataModel) {
        final HashMap<ComponentKey, Integer> shortcutMapCopy =
                new HashMap<>(dataModel.deepShortcutMap);
        scheduleCallbackTask(callbacks -> callbacks.bindDeepShortcutMap(shortcutMapCopy));
    }

    public void bindUpdatedWidgets(BgDataModel dataModel) {
        final ArrayList<WidgetsListBaseEntry> widgets =
                dataModel.widgetsModel.getWidgetsListForPicker(mApp.getContext());
        scheduleCallbackTask(c -> c.bindAllWidgets(widgets));
    }

    public void deleteAndBindComponentsRemoved(final ItemInfoMatcher matcher) {
        getModelWriter().deleteItemsFromDatabase(matcher);

        // Call the components-removed callback
        scheduleCallbackTask(c -> c.bindWorkspaceComponentsRemoved(matcher));
    }

    public void bindApplicationsIfNeeded() {
        if (mAllAppsList.getAndResetChangeFlag()) {
            AppInfo[] apps = mAllAppsList.copyData();
            int flags = mAllAppsList.getFlags();
            scheduleCallbackTask(c -> c.bindAllApplications(apps, flags));
        }
    }
}
