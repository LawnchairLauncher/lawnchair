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
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.picker.WidgetRecommendationCategoryProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Works with app predictor to fetch and process widget predictions displayed in a standalone
 * widget picker activity for a UI surface.
 */
public class WidgetPredictionsRequester implements AppPredictor.Callback {
    private static final int NUM_OF_RECOMMENDED_WIDGETS_PREDICATION = 20;
    private static final String BUNDLE_KEY_ADDED_APP_WIDGETS = "added_app_widgets";
    // container/screenid/[positionx,positiony]/[spanx,spany]
    // Matches the format passed used by PredictionHelper; But, position and size values aren't
    // used, so, we pass default values.
    @VisibleForTesting
    static final String LAUNCH_LOCATION = "workspace/1/[0,0]/[2,2]";

    @Nullable
    private AppPredictor mAppPredictor;
    private final Context mContext;
    @NonNull
    private final String mUiSurface;
    private boolean mPredictionsAvailable;
    @Nullable
    private WidgetPredictionsListener mPredictionsListener = null;
    @Nullable Predicate<WidgetItem> mFilter = null;
    @NonNull
    private final Map<ComponentKey, WidgetItem> mAllWidgets;

    public WidgetPredictionsRequester(Context context, @NonNull String uiSurface,
            @NonNull Map<ComponentKey, WidgetItem> allWidgets) {
        mContext = context;
        mUiSurface = uiSurface;
        mAllWidgets = Collections.unmodifiableMap(allWidgets);
    }

    // AppPredictor.Callback -> onTargetsAvailable
    @Override
    @WorkerThread
    public void onTargetsAvailable(List<AppTarget> targets) {
        List<WidgetItem> filteredPredictions = filterPredictions(targets, mAllWidgets, mFilter);
        List<ItemInfo> mappedPredictions = mapWidgetItemsToItemInfo(filteredPredictions);

        if (!mPredictionsAvailable && mPredictionsListener != null) {
            mPredictionsAvailable = true;
            MAIN_EXECUTOR.execute(
                    () -> mPredictionsListener.onPredictionsAvailable(mappedPredictions));
        }
    }

    /**
     * Requests one time predictions from the app predictions manager and invokes provided callback
     * once predictions are available. Any previous requests may be cancelled.
     *
     * @param existingWidgets widgets that are currently added to the surface;
     * @param listener        consumer of prediction results to be called when predictions are
     *                        available; any previous listener will no longer receive updates.
     */
    @WorkerThread // e.g. MODEL_EXECUTOR
    public void request(List<AppWidgetProviderInfo> existingWidgets,
            WidgetPredictionsListener listener) {
        clear();
        mPredictionsListener = listener;
        mFilter = notOnUiSurfaceFilter(existingWidgets);

        AppPredictionManager apm = mContext.getSystemService(AppPredictionManager.class);
        if (apm == null) {
            return;
        }

        Bundle bundle = buildBundleForPredictionSession(existingWidgets);
        mAppPredictor = apm.createAppPredictionSession(
                new AppPredictionContext.Builder(mContext)
                        .setUiSurface(mUiSurface)
                        .setExtras(bundle)
                        .setPredictedTargetCount(NUM_OF_RECOMMENDED_WIDGETS_PREDICATION)
                        .build());
        mAppPredictor.registerPredictionUpdates(MODEL_EXECUTOR, /*callback=*/ this);
        mAppPredictor.requestPredictionUpdate();
    }

    /**
     * Returns a bundle that can be passed in a prediction session
     *
     * @param addedWidgets widgets that are already added by the user in the ui surface
     */
    @VisibleForTesting
    static Bundle buildBundleForPredictionSession(List<AppWidgetProviderInfo> addedWidgets) {
        Bundle bundle = new Bundle();
        ArrayList<AppTargetEvent> addedAppTargetEvents = new ArrayList<>();
        for (AppWidgetProviderInfo info : addedWidgets) {
            ComponentName componentName = info.provider;
            AppTargetEvent appTargetEvent = buildAppTargetEvent(info, componentName);
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
    private static AppTargetEvent buildAppTargetEvent(AppWidgetProviderInfo info,
            ComponentName componentName) {
        AppTargetId appTargetId = new AppTargetId("widget:" + componentName.getPackageName());
        AppTarget appTarget = new AppTarget.Builder(appTargetId, componentName.getPackageName(),
                /*user=*/ info.getProfile()).setClassName(componentName.getClassName()).build();
        return new AppTargetEvent.Builder(appTarget, AppTargetEvent.ACTION_PIN).setLaunchLocation(
                LAUNCH_LOCATION).build();
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

    /**
     * Applies the provided filter (e.g. widgets not on workspace) on the predictions returned by
     * the predictor.
     */
    @VisibleForTesting
    static List<WidgetItem> filterPredictions(List<AppTarget> predictions,
            @NonNull Map<ComponentKey, WidgetItem> allWidgets,
            @Nullable Predicate<WidgetItem> filter) {
        List<WidgetItem> servicePredictedItems = new ArrayList<>();

        for (AppTarget prediction : predictions) {
            String className = prediction.getClassName();
            if (!TextUtils.isEmpty(className)) {
                WidgetItem widgetItem = allWidgets.get(
                        new ComponentKey(new ComponentName(prediction.getPackageName(), className),
                                prediction.getUser()));
                if (widgetItem != null && (filter == null || filter.test(widgetItem))) {
                    servicePredictedItems.add(widgetItem);
                }
            }
        }

        return servicePredictedItems;
    }

    /**
     * Converts the list of {@link WidgetItem}s to the list of {@link ItemInfo}s.
     */
    private List<ItemInfo> mapWidgetItemsToItemInfo(List<WidgetItem> widgetItems) {
        WidgetRecommendationCategoryProvider categoryProvider =
                new WidgetRecommendationCategoryProvider();
        return widgetItems.stream()
                .map(it -> new PendingAddWidgetInfo(it.widgetInfo, CONTAINER_WIDGETS_PREDICTION,
                        categoryProvider.getWidgetRecommendationCategory(mContext, it)))
                .collect(Collectors.toList());
    }

    /** Cleans up any open prediction sessions. */
    public void clear() {
        if (mAppPredictor != null) {
            mAppPredictor.unregisterPredictionUpdates(this);
            mAppPredictor.destroy();
            mAppPredictor = null;
        }
        mPredictionsListener = null;
        mPredictionsAvailable = false;
        mFilter = null;
    }

    /**
     * Listener class to listen to updates from the {@link WidgetPredictionsRequester}
     */
    public interface WidgetPredictionsListener {
        /**
         * Callback method that is called when the predicted widgets are available.
         * @param predictions list of predicted widgets {@link PendingAddWidgetInfo}
         */
        void onPredictionsAvailable(List<ItemInfo> predictions);
    }
}
