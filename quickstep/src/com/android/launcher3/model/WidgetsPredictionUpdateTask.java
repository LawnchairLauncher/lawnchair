/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.Flags.enableCategorizedWidgetSuggestions;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION;

import android.app.prediction.AppTarget;
import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.QuickstepModelDelegate.PredictorState;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.picker.WidgetRecommendationCategoryProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Task to update model as a result of predicted widgets update */
public final class WidgetsPredictionUpdateTask implements ModelUpdateTask {
    private final PredictorState mPredictorState;
    private final List<AppTarget> mTargets;

    WidgetsPredictionUpdateTask(PredictorState predictorState, List<AppTarget> targets) {
        mPredictorState = predictorState;
        mTargets = targets;
    }

    /**
     * Uses the app predication result to infer widgets that the user may want to use.
     *
     * <p>The algorithm uses the app prediction ranking to create a widgets ranking which only
     * includes one widget per app and excludes widgets that have already been added to the
     * workspace.
     */
    @Override
    public void execute(@NonNull ModelTaskController taskController, @NonNull BgDataModel dataModel,
            @NonNull AllAppsList apps) {
        Set<ComponentKey> widgetsInWorkspace = dataModel.appWidgets.stream().map(
                widget -> new ComponentKey(widget.providerName, widget.user)).collect(
                Collectors.toSet());
        Predicate<WidgetItem> notOnWorkspace = w -> !widgetsInWorkspace.contains(w);
        Map<ComponentKey, WidgetItem> allWidgets =
                dataModel.widgetsModel.getAllWidgetComponentsWithoutShortcuts();

        List<WidgetItem> servicePredictedItems = new ArrayList<>();

        for (AppTarget app : mTargets) {
            ComponentKey componentKey = new ComponentKey(
                    new ComponentName(app.getPackageName(), app.getClassName()), app.getUser());
            WidgetItem widget = allWidgets.get(componentKey);
            if (widget == null) {
                continue;
            }
            String className = app.getClassName();
            if (!TextUtils.isEmpty(className)) {
                if (notOnWorkspace.test(widget)) {
                    servicePredictedItems.add(widget);
                }
            }
        }

        List<ItemInfo> items;
        if (enableCategorizedWidgetSuggestions()) {
            Context context = taskController.getApp().getContext();
            WidgetRecommendationCategoryProvider categoryProvider =
                    WidgetRecommendationCategoryProvider.newInstance(context);
            items = servicePredictedItems.stream()
                    .map(it -> new PendingAddWidgetInfo(it.widgetInfo, CONTAINER_WIDGETS_PREDICTION,
                            categoryProvider.getWidgetRecommendationCategory(context, it)))
                    .collect(Collectors.toList());
        } else {
            items = servicePredictedItems.stream()
                    .map(it -> new PendingAddWidgetInfo(it.widgetInfo,
                            CONTAINER_WIDGETS_PREDICTION)).collect(
                            Collectors.toList());
        }
        FixedContainerItems fixedContainerItems =
                new FixedContainerItems(mPredictorState.containerId, items);

        dataModel.extraItems.put(mPredictorState.containerId, fixedContainerItems);
        taskController.bindExtraContainerItems(fixedContainerItems);

        // Don't store widgets prediction to disk because it is not used frequently.
    }
}
