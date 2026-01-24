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
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Trace;
import android.util.Log;

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
import com.android.launcher3.model.data.AppsListData;
import com.android.launcher3.model.data.WorkspaceData;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.widget.model.WidgetsListBaseEntriesBuilder;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Binds the results of {@link com.android.launcher3.model.LoaderTask} to the Callbacks objects.
 */
public class BaseLauncherBinder {

    protected static final String TAG = "LauncherBinder";

    protected final LooperExecutor mUiExecutor;

    private final Context mContext;
    private final LauncherModel mModel;
    protected final BgDataModel mBgDataModel;
    private final AllAppsList mBgAllAppsList;

    final Callbacks[] mCallbacksList;

    private int mMyBindingId;

    @AssistedInject
    public BaseLauncherBinder(
            @ApplicationContext Context context,
            LauncherModel model,
            BgDataModel dataModel,
            AllAppsList allAppsList,
            @Assisted Callbacks[] callbacksList) {
        mUiExecutor = MAIN_EXECUTOR;
        mContext = context;
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
            WorkspaceData itemsIdMap;
            ArrayList<FixedContainerItems> extraItems = new ArrayList<>();
            StringCache stringCache;

            synchronized (mBgDataModel) {
                itemsIdMap = mBgDataModel.itemsIdMap.copy();
                mBgDataModel.extraItems.forEach(extraItems::add);
                if (incrementBindId) {
                    mBgDataModel.lastBindId++;
                    mBgDataModel.lastLoadId = mModel.getLastLoadId();
                }
                mMyBindingId = mBgDataModel.lastBindId;
                stringCache = mBgDataModel.stringCache.clone();
            }

            for (Callbacks cb : mCallbacksList) {
                cb.bindCompleteModelAsync(itemsIdMap, isBindSync);
            }

            executeCallbacksTask(c -> c.bindStringCache(stringCache), mUiExecutor);
            for (FixedContainerItems extraItem: extraItems) {
                executeCallbacksTask(c -> c.bindExtraContainerItems(extraItem), mUiExecutor);
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
        AppsListData data = mBgAllAppsList.getImmutableData();
        executeCallbacksTask(c -> c.bindAllApplications(
                data.getApps(), data.getFlags(), data.getPackageUserKeyToUidMap()),
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
    @AssistedFactory
    public interface BaseLauncherBinderFactory {
        BaseLauncherBinder createBinder(Callbacks[] callbacks);
    }
}
