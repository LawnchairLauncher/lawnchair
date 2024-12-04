/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.app.prediction.AppPredictionContext;
import android.app.prediction.AppPredictionManager;
import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.app.prediction.AppTargetId;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.picker.WidgetRecommendationCategoryProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Works with app predictor to fetch and process widget predictions displayed in a standalone
 * widget picker activity for a UI surface.
 */
public class WidgetPredictionsRequester {
    private static final int NUM_OF_RECOMMENDED_WIDGETS_PREDICATION = 20;
    private static final String BUNDLE_KEY_ADDED_APP_WIDGETS = "added_app_widgets";

    @Nullable
    private AppPredictor mAppPredictor;
    private final Context mContext;
    @NonNull
    private final String mUiSurface;
    @NonNull
    private final Map<PackageUserKey, List<WidgetItem>> mAllWidgets;

    public WidgetPredictionsRequester(Context context, @NonNull String uiSurface,
            @NonNull Map<PackageUserKey, List<WidgetItem>> allWidgets) {
        mContext = context;
        mUiSurface = uiSurface;
        mAllWidgets = Collections.unmodifiableMap(allWidgets);
    }

    /**
     * Requests predictions from the app predictions manager and registers the provided callback to
     * receive updates when predictions are available.
     *
     * @param existingWidgets widgets that are currently added to the surface;
     * @param callback        consumer of prediction results to be called when predictions are
     *                        available
     */
    public void request(List<AppWidgetProviderInfo> existingWidgets,
            Consumer<List<ItemInfo>> callback) {
        Bundle bundle = buildBundleForPredictionSession(existingWidgets, mUiSurface);
        Predicate<WidgetItem> filter = notOnUiSurfaceFilter(existingWidgets);

        MODEL_EXECUTOR.execute(() -> {
            clear();
            AppPredictionManager apm = mContext.getSystemService(AppPredictionManager.class);
            if (apm == null) {
                return;
            }

            mAppPredictor = apm.createAppPredictionSession(
                    new AppPredictionContext.Builder(mContext)
                            .setUiSurface(mUiSurface)
                            .setExtras(bundle)
                            .setPredictedTargetCount(NUM_OF_RECOMMENDED_WIDGETS_PREDICATION)
                            .build());
            mAppPredictor.registerPredictionUpdates(MODEL_EXECUTOR,
                    targets -> bindPredictions(targets, filter, callback));
            mAppPredictor.requestPredictionUpdate();
        });
    }

    /**
     * Returns a bundle that can be passed in a prediction session
     *
     * @param addedWidgets widgets that are already added by the user in the ui surface
     * @param uiSurface    a unique identifier of the surface hosting widgets; format
     *                     "widgets_xx"; note - "widgets" is reserved for home screen surface.
     */
    @VisibleForTesting
    static Bundle buildBundleForPredictionSession(List<AppWidgetProviderInfo> addedWidgets,
            String uiSurface) {
        Bundle bundle = new Bundle();
        ArrayList<AppTargetEvent> addedAppTargetEvents = new ArrayList<>();
        for (AppWidgetProviderInfo info : addedWidgets) {
            ComponentName componentName = info.provider;
            AppTargetEvent appTargetEvent = buildAppTargetEvent(uiSurface, info, componentName);
            addedAppTargetEvents.add(appTargetEvent);
        }
        bundle.putParcelableArrayList(BUNDLE_KEY_ADDED_APP_WIDGETS, addedAppTargetEvents);
        return bundle;
    }

    /**
     * Builds the AppTargetEvent for added widgets in a form that can be passed to the widget
     * predictor.
     * Also see {@link PredictionHelper}
     */
    private static AppTargetEvent buildAppTargetEvent(String uiSurface, AppWidgetProviderInfo info,
            ComponentName componentName) {
        AppTargetId appTargetId = new AppTargetId("widget:" + componentName.getPackageName());
        AppTarget appTarget = new AppTarget.Builder(appTargetId, componentName.getPackageName(),
                /*user=*/ info.getProfile()).setClassName(componentName.getClassName()).build();
        return new AppTargetEvent.Builder(appTarget, AppTargetEvent.ACTION_PIN)
                .setLaunchLocation(uiSurface).build();
    }

    /**
     * Returns a filter to match {@link WidgetItem}s that don't exist on the UI surface.
     */
    @NonNull
    @VisibleForTesting
    static Predicate<WidgetItem> notOnUiSurfaceFilter(
            List<AppWidgetProviderInfo> existingWidgets) {
        Set<ComponentKey> existingComponentKeys = existingWidgets.stream().map(
                widget -> new ComponentKey(widget.provider, widget.getProfile())).collect(
                Collectors.toSet());
        return widgetItem -> !existingComponentKeys.contains(widgetItem);
    }

    /** Provides the predictions returned by the predictor to the registered callback. */
    @WorkerThread
    private void bindPredictions(List<AppTarget> targets, Predicate<WidgetItem> filter,
            Consumer<List<ItemInfo>> callback) {
        List<WidgetItem> filteredPredictions = filterPredictions(targets, mAllWidgets, filter);
        List<ItemInfo> mappedPredictions = mapWidgetItemsToItemInfo(filteredPredictions);

        MAIN_EXECUTOR.execute(() -> callback.accept(mappedPredictions));
    }

    /**
     * Applies the provided filter (e.g. widgets not on workspace) on the predictions returned by
     * the predictor.
     */
    @VisibleForTesting
    static List<WidgetItem> filterPredictions(List<AppTarget> predictions,
            Map<PackageUserKey, List<WidgetItem>> allWidgets, Predicate<WidgetItem> filter) {
        List<WidgetItem> servicePredictedItems = new ArrayList<>();
        List<WidgetItem> localFilteredWidgets = new ArrayList<>();

        for (AppTarget prediction : predictions) {
            List<WidgetItem> widgetsInPackage = allWidgets.get(
                    new PackageUserKey(prediction.getPackageName(), prediction.getUser()));
            if (widgetsInPackage == null || widgetsInPackage.isEmpty()) {
                continue;
            }
            String className = prediction.getClassName();
            if (!TextUtils.isEmpty(className)) {
                WidgetItem item = widgetsInPackage.stream()
                        .filter(w -> className.equals(w.componentName.getClassName()))
                        .filter(filter)
                        .findFirst().orElse(null);
                if (item != null) {
                    servicePredictedItems.add(item);
                    continue;
                }
            }
            // No widget was added by the service, try local filtering
            widgetsInPackage.stream().filter(filter).findFirst()
                    .ifPresent(localFilteredWidgets::add);
        }
        if (servicePredictedItems.isEmpty()) {
            servicePredictedItems.addAll(localFilteredWidgets);
        }

        return servicePredictedItems;
    }

    /**
     * Converts the list of {@link WidgetItem}s to the list of {@link ItemInfo}s.
     */
    private List<ItemInfo> mapWidgetItemsToItemInfo(List<WidgetItem> widgetItems) {
        List<ItemInfo> items;
        if (enableCategorizedWidgetSuggestions()) {
            WidgetRecommendationCategoryProvider categoryProvider =
                    WidgetRecommendationCategoryProvider.newInstance(mContext);
            items = widgetItems.stream()
                    .map(it -> new PendingAddWidgetInfo(it.widgetInfo, CONTAINER_WIDGETS_PREDICTION,
                            categoryProvider.getWidgetRecommendationCategory(mContext, it)))
                    .collect(Collectors.toList());
        } else {
            items = widgetItems.stream().map(it -> new PendingAddWidgetInfo(it.widgetInfo,
                    CONTAINER_WIDGETS_PREDICTION)).collect(Collectors.toList());
        }
        return items;
    }

    /** Cleans up any open prediction sessions. */
    public void clear() {
        if (mAppPredictor != null) {
            mAppPredictor.destroy();
            mAppPredictor = null;
        }
    }
}
