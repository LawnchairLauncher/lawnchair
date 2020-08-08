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

import static com.android.launcher3.InvariantDeviceProfile.CHANGE_FLAG_GRID;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PREDICTION;

import android.app.prediction.AppPredictionContext;
import android.app.prediction.AppPredictionManager;
import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.content.Context;

import androidx.annotation.WorkerThread;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.util.Executors;
import com.android.quickstep.logging.StatsLogCompatManager;

import java.util.List;

/**
 * Model delegate which loads prediction items
 */
public class QuickstepModelDelegate extends ModelDelegate implements OnIDPChangeListener {

    public static final String LAST_PREDICTION_ENABLED_STATE = "last_prediction_enabled_state";

    private final InvariantDeviceProfile mIDP;
    private final AppEventProducer mAppEventProducer;

    private AppPredictor mAllAppsPredictor;
    private boolean mActive = false;

    public QuickstepModelDelegate(Context context) {
        mAppEventProducer = new AppEventProducer(context, this::onAppTargetEvent);

        mIDP = InvariantDeviceProfile.INSTANCE.get(context);
        mIDP.addOnChangeListener(this);
        StatsLogCompatManager.LOGS_CONSUMER.add(mAppEventProducer);
    }

    @Override
    public void loadItems() {
        // TODO: Implement caching and preloading
        super.loadItems();
        mDataModel.extraItems.put(
                CONTAINER_PREDICTION, new FixedContainerItems(CONTAINER_PREDICTION));

        mActive = true;
        recreatePredictors();
    }

    @Override
    public void validateData() {
        super.validateData();
        if (mAllAppsPredictor != null) {
            mAllAppsPredictor.requestPredictionUpdate();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        mActive = false;
        StatsLogCompatManager.LOGS_CONSUMER.remove(mAppEventProducer);

        destroyPredictors();
        mIDP.removeOnChangeListener(this);
    }

    private void destroyPredictors() {
        if (mAllAppsPredictor != null) {
            mAllAppsPredictor.destroy();
            mAllAppsPredictor = null;
        }
    }

    @WorkerThread
    private void recreatePredictors() {
        destroyPredictors();
        if (!mActive) {
            return;
        }

        Context context = mApp.getContext();
        AppPredictionManager apm = context.getSystemService(AppPredictionManager.class);
        if (apm == null) {
            return;
        }

        int count = mIDP.numAllAppsColumns;

        mAllAppsPredictor = apm.createAppPredictionSession(
                new AppPredictionContext.Builder(context)
                        .setUiSurface("home")
                        .setPredictedTargetCount(count)
                        .build());
        mAllAppsPredictor.registerPredictionUpdates(
                Executors.MODEL_EXECUTOR, this::onAllAppsPredictionChanged);
        mAllAppsPredictor.requestPredictionUpdate();
    }

    private void onAllAppsPredictionChanged(List<AppTarget> targets) {
        mApp.getModel().enqueueModelUpdateTask(
                new PredictionUpdateTask(CONTAINER_PREDICTION, targets));
    }

    @Override
    public void onIdpChanged(int changeFlags, InvariantDeviceProfile profile) {
        if ((changeFlags & CHANGE_FLAG_GRID) != 0) {
            // Reinitialize everything
            Executors.MODEL_EXECUTOR.execute(this::recreatePredictors);
        }
    }

    private void onAppTargetEvent(AppTargetEvent event) {
        if (mAllAppsPredictor != null) {
            mAllAppsPredictor.notifyAppTargetEvent(event);
        }
    }
}
