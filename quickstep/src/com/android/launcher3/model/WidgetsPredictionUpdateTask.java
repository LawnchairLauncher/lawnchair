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

import static com.android.launcher3.Flags.enableTieredWidgetsByDefaultInPicker;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION;
import static com.android.launcher3.model.ModelUtils.WIDGET_FILTER;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import android.app.prediction.AppTarget;
import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PredictedContainerInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.picker.WidgetRecommendationCategoryProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
        Set<ComponentKey> widgetsInWorkspace = dataModel.itemsIdMap
                .stream()
                .filter(WIDGET_FILTER)
                .map(item -> new ComponentKey(item.getTargetComponent(), item.user))
                .collect(Collectors.toSet());

        // Widgets (excluding shortcuts & already added widgets) that belong to apps eligible for
        // being in predictions.
        Map<ComponentKey, WidgetItem> allEligibleWidgets =
                dataModel.widgetsModel.getWidgetsByComponentKeyForPicker()
                        .entrySet()
                        .stream()
                        .filter(entry -> entry.getValue().widgetInfo != null
                                && !widgetsInWorkspace.contains(entry.getValue())
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        Context context = taskController.getContext();

        List<WidgetItem> servicePredictedItems = new ArrayList<>();
        List<String> addedWidgetApps = new ArrayList<>();

        for (AppTarget app : mTargets) {
            ComponentKey componentKey = new ComponentKey(
                    new ComponentName(app.getPackageName(), app.getClassName()), app.getUser());
            WidgetItem widget = allEligibleWidgets.get(componentKey);
            if (widget == null) { // widget not eligible.
                continue;
            }
            String className = app.getClassName();
            if (!TextUtils.isEmpty(className)) {
                servicePredictedItems.add(widget);
                addedWidgetApps.add(componentKey.componentName.getPackageName());
            }
        }

        int minPredictionCount = context.getResources().getInteger(
                R.integer.widget_predictions_min_count);
        if (enableTieredWidgetsByDefaultInPicker()
                && servicePredictedItems.size() < minPredictionCount) {
            // Eligible apps that aren't already part of predictions.
            Map<String, List<WidgetItem>> eligibleWidgetsByApp =
                    allEligibleWidgets.values().stream()
                            .filter(w -> !addedWidgetApps.contains(
                                    w.componentName.getPackageName()))
                            .collect(groupingBy(w -> w.componentName.getPackageName()));

            // Randomize available apps list
            List<String> appPackages = new ArrayList<>(eligibleWidgetsByApp.keySet());
            Collections.shuffle(appPackages);

            int widgetsToAdd = minPredictionCount - servicePredictedItems.size();
            for (String appPackage : appPackages) {
                if (widgetsToAdd <= 0) break;

                List<WidgetItem> widgetsForApp = eligibleWidgetsByApp.get(appPackage);
                int index = new Random().nextInt(widgetsForApp.size());
                // Add a random widget from the app.
                servicePredictedItems.add(widgetsForApp.get(index));
                widgetsToAdd--;
            }
        }

        WidgetRecommendationCategoryProvider categoryProvider =
                new WidgetRecommendationCategoryProvider();
        List<ItemInfo> items = servicePredictedItems.stream()
                .map(it -> new PendingAddWidgetInfo(it.widgetInfo, CONTAINER_WIDGETS_PREDICTION,
                        categoryProvider.getWidgetRecommendationCategory(context, it)))
                .collect(Collectors.toList());
        PredictedContainerInfo pci =
                new PredictedContainerInfo(mPredictorState.containerId, items);

        dataModel.updateAndDispatchItem(pci /* item */, null /* owner */);
        taskController.bindUpdatedWorkspaceItems(Collections.singleton(pci));

        // Don't store widgets prediction to disk because it is not used frequently.
    }
}
