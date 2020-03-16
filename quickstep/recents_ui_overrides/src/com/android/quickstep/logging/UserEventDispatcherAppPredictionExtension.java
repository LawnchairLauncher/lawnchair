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
package com.android.quickstep.logging;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.appprediction.PredictionUiStateManager;
import com.android.launcher3.userevent.nano.LauncherLogProto;

/**
 * This class handles AOSP MetricsLogger function calls and logging around
 * quickstep interactions and app launches.
 */
@SuppressWarnings("unused")
public class UserEventDispatcherAppPredictionExtension extends UserEventDispatcherExtension {

    public static final int ALL_APPS_PREDICTION_TIPS = 2;

    private static final String TAG = "UserEventDispatcher";

    public UserEventDispatcherAppPredictionExtension(Context context) {
        super(context);
    }

    @Override
    protected void onFillInLogContainerData(
            @NonNull ItemInfo itemInfo, @NonNull LauncherLogProto.Target target,
            @NonNull LauncherLogProto.Target targetParent) {
        PredictionUiStateManager.fillInPredictedRank(itemInfo, target);
    }
}
