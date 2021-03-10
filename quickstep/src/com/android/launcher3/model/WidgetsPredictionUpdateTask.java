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

import android.app.prediction.AppTarget;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.QuickstepModelDelegate.PredictorState;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.PendingAddWidgetInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Task to update model as a result of predicted widgets update */
public final class WidgetsPredictionUpdateTask extends BaseModelUpdateTask {
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
    public void execute(LauncherAppState appState, BgDataModel dataModel, AllAppsList apps) {
        Set<ComponentKey> widgetsInWorkspace = dataModel.appWidgets.stream().map(
                widget -> new ComponentKey(widget.providerName, widget.user)).collect(
                Collectors.toSet());
        Map<PackageUserKey, List<WidgetItem>> allWidgets =
                dataModel.widgetsModel.getAllWidgetsWithoutShortcuts();

        ArrayList<ItemInfo> recommendedWidgetsInDescendingOrder = new ArrayList<>();
        for (AppTarget app : mTargets) {
            PackageUserKey packageUserKey = new PackageUserKey(app.getPackageName(), app.getUser());
            if (allWidgets.containsKey(packageUserKey)) {
                List<WidgetItem> notAddedWidgets = allWidgets.get(packageUserKey).stream()
                        .filter(item ->
                                !widgetsInWorkspace.contains(
                                        new ComponentKey(item.componentName, item.user)))
                        .collect(Collectors.toList());
                if (notAddedWidgets.size() > 0) {
                    // Even an apps have more than one widgets, we only include one widget.
                    recommendedWidgetsInDescendingOrder.add(
                            new PendingAddWidgetInfo(notAddedWidgets.get(0).widgetInfo));
                }
            }
        }
        FixedContainerItems fixedContainerItems = mPredictorState.items;
        fixedContainerItems.items.clear();
        fixedContainerItems.items.addAll(recommendedWidgetsInDescendingOrder);
        bindExtraContainerItems(fixedContainerItems);

        // Don't store widgets prediction to disk because it is not used frequently.
    }
}
