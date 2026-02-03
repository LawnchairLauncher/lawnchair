/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.launcher3.util.PackageManagerHelper.hasShortcutsPermission;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.android.launcher3.LauncherModel;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.IntSparseArrayMap;

import javax.inject.Inject;

/**
 * Class to extend LauncherModel functionality to provide extra data
 */
public class ModelDelegate {

    protected final Context mContext;
    protected LauncherModel mModel;
    protected AllAppsList mAppsList;
    protected BgDataModel mDataModel;

    @Inject
    public ModelDelegate(@ApplicationContext Context context) {
        mContext = context;
    }

    /**
     * Initializes the object with the given params.
     */
    public void init(LauncherModel model, AllAppsList appsList, BgDataModel dataModel) {
        this.mModel = model;
        this.mAppsList = appsList;
        this.mDataModel = dataModel;
    }

    /** Called periodically to validate and update any data */
    @WorkerThread
    public void validateData() {
        if (hasShortcutsPermission(mContext) != mAppsList.hasShortcutHostPermission()) {
            mModel.forceReload();
        }
    }

    /** Load workspace items (for example, those in the hot seat) if any in the data model */
    @WorkerThread
    public void loadAndAddExtraModelItems(@NonNull IntSparseArrayMap<ItemInfo> outLoadedItems) { }

    /** Marks the ModelDelegate as active */
    public void markActive() { }

    /**
     * Called during loader after workspace loading is complete
     */
    @WorkerThread
    public void workspaceLoadComplete() { }

    /**
     * Called at the end of model load task
     */
    @WorkerThread
    public void modelLoadComplete() { }

    /** Called when grid migration has completed as part of grid size refactor. */
    @WorkerThread
    public void gridMigrationComplete(
            @NonNull DeviceGridState src, @NonNull DeviceGridState dest) { }

    /**
     * Called when the delegate is no loner needed
     */
    @WorkerThread
    public void destroy() { }

}
