/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.android.launcher3.AllAppsList;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.ModelUpdateTask;

import java.util.concurrent.Executor;

/**
 * Utility class to preload LauncherModel
 */
public class ModelPreload implements ModelUpdateTask {

    private static final String TAG = "ModelPreload";

    private LauncherAppState mApp;
    private LauncherModel mModel;
    private BgDataModel mBgDataModel;
    private AllAppsList mAllAppsList;

    @Override
    public final void init(LauncherAppState app, LauncherModel model, BgDataModel dataModel,
            AllAppsList allAppsList, Executor uiExecutor) {
        mApp = app;
        mModel = model;
        mBgDataModel = dataModel;
        mAllAppsList = allAppsList;
    }

    @Override
    public final void run() {
        mModel.startLoaderForResultsIfNotLoaded(
                new LoaderResults(mApp, mBgDataModel, mAllAppsList, 0, null));
        Log.d(TAG, "Preload completed : " + mModel.isModelLoaded());
        onComplete(mModel.isModelLoaded());
    }

    /**
     * Called when the task is complete
     */
    @WorkerThread
    public void onComplete(boolean isSuccess) { }

    public void start(Context context) {
        LauncherAppState.getInstance(context).getModel().enqueueModelUpdateTask(this);
    }
}