/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.secondarydisplay;

import static com.android.launcher3.util.OnboardingPrefs.ALL_APPS_VISITED_COUNT;

import android.content.Context;

import com.android.launcher3.appprediction.AppsDividerView;
import com.android.launcher3.appprediction.PredictionRowView;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.views.ActivityContext;

/**
 * Implementation of SecondaryDisplayPredictions.
 */
@SuppressWarnings("unused")
public final class SecondaryDisplayPredictionsImpl extends SecondaryDisplayPredictions {

    private final ActivityContext mActivityContext;
    private final Context mContext;

    public SecondaryDisplayPredictionsImpl(Context context) {
        mContext = context;
        mActivityContext = ActivityContext.lookupContext(context);
    }

    @Override
    void updateAppDivider() {
        mActivityContext.getAppsView().getFloatingHeaderView()
                .findFixedRowByType(AppsDividerView.class)
                .setShowAllAppsLabel(!ALL_APPS_VISITED_COUNT.hasReachedMax(mContext));
        ALL_APPS_VISITED_COUNT.increment(mContext);
    }

    @Override
    public void setPredictedApps(BgDataModel.FixedContainerItems item) {
        mActivityContext.getAppsView().getFloatingHeaderView()
                .findFixedRowByType(PredictionRowView.class)
                .setPredictedApps(item.items);
    }
}
