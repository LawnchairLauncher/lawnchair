/**
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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.util.SparseArray;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * {@link LauncherWidgetHolder} that puts the app widget host in the background
 */
public final class QuickstepWidgetHolder extends LauncherWidgetHolder {
    private static final List<QuickstepWidgetHolder> sHolders = new ArrayList<>();
    private static final SparseArray<QuickstepWidgetHolderListener> sListeners =
            new SparseArray<>();

    private static AppWidgetHost sWidgetHost = null;

    private final @Nullable RemoteViews.InteractionHandler mInteractionHandler;

    private final @NonNull IntConsumer mAppWidgetRemovedCallback;

    private final ArrayList<ProviderChangedListener> mProviderChangedListeners = new ArrayList<>();

    @Thunk
    QuickstepWidgetHolder(@NonNull Context context,
            @Nullable IntConsumer appWidgetRemovedCallback,
            @Nullable RemoteViews.InteractionHandler interactionHandler) {
        super(context, appWidgetRemovedCallback);
        mAppWidgetRemovedCallback = appWidgetRemovedCallback != null ? appWidgetRemovedCallback
                : i -> {};
        mInteractionHandler = interactionHandler;
        sHolders.add(this);
    }

    @Override
    @NonNull
    protected AppWidgetHost createHost(@NonNull Context context,
            @Nullable IntConsumer appWidgetRemovedCallback) {
        if (sWidgetHost == null) {
            sWidgetHost = new QuickstepAppWidgetHost(context.getApplicationContext(),
                    i -> MAIN_EXECUTOR.execute(() ->
                            sHolders.forEach(h -> h.mAppWidgetRemovedCallback.accept(i))),
                    () -> MAIN_EXECUTOR.execute(() ->
                            sHolders.forEach(h -> h.mProviderChangedListeners.forEach(
                            ProviderChangedListener::notifyWidgetProvidersChanged))),
                    UI_HELPER_EXECUTOR.getLooper());
            if (!WidgetsModel.GO_DISABLE_WIDGETS) {
                sWidgetHost.startListening();
            }
        }
        return sWidgetHost;
    }

    /**
     * Delete the specified app widget from the host
     * @param appWidgetId The ID of the app widget to be deleted
     */
    @Override
    public void deleteAppWidgetId(int appWidgetId) {
        super.deleteAppWidgetId(appWidgetId);
        sListeners.remove(appWidgetId);
    }

    /**
     * Called when the launcher is destroyed
     */
    @Override
    public void destroy() {
        sHolders.remove(this);
    }

    /**
     * Add a listener that is triggered when the providers of the widgets are changed
     * @param listener The listener that notifies when the providers changed
     */
    @Override
    public void addProviderChangeListener(
            @NonNull LauncherWidgetHolder.ProviderChangedListener listener) {
        mProviderChangedListeners.add(listener);
    }

    /**
     * Remove the specified listener from the host
     * @param listener The listener that is to be removed from the host
     */
    @Override
    public void removeProviderChangeListener(
            LauncherWidgetHolder.ProviderChangedListener listener) {
        mProviderChangedListeners.remove(listener);
    }

    /**
     * Stop the host from updating the widget views
     */
    @Override
    public void stopListening() {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return;
        }

        sWidgetHost.setAppWidgetHidden();
        setListeningFlag(false);
    }

    /**
     * Create a view for the specified app widget
     * @param context The activity context for which the view is created
     * @param appWidgetId The ID of the widget
     * @param appWidget The {@link LauncherAppWidgetProviderInfo} of the widget
     * @return A view for the widget
     */
    @NonNull
    @Override
    public LauncherAppWidgetHostView createView(@NonNull Context context, int appWidgetId,
            @NonNull LauncherAppWidgetProviderInfo appWidget) {
        LauncherAppWidgetHostView widgetView = getPendingView(appWidgetId);
        if (widgetView != null) {
            removePendingView(appWidgetId);
        } else {
            widgetView = new LauncherAppWidgetHostView(context);
        }
        widgetView.setInteractionHandler(mInteractionHandler);
        widgetView.setAppWidget(appWidgetId, appWidget);

        QuickstepWidgetHolderListener listener = sListeners.get(appWidgetId);
        if (listener == null) {
            listener = new QuickstepWidgetHolderListener(this, widgetView);
            sWidgetHost.setListener(appWidgetId, listener);
            sListeners.put(appWidgetId, listener);
        } else {
            listener.resetView(this, widgetView);
        }

        return widgetView;
    }

    /**
     * Clears all the views from the host
     */
    @Override
    public void clearViews() {
        for (int i = sListeners.size() - 1; i >= 0; i--) {
            sListeners.valueAt(i).mView.remove(this);
        }
    }

    private static class QuickstepWidgetHolderListener
            implements AppWidgetHost.AppWidgetHostListener {
        @NonNull
        private final Map<QuickstepWidgetHolder, AppWidgetHostView> mView = new WeakHashMap<>();

        @Nullable
        private RemoteViews mRemoteViews = null;

        QuickstepWidgetHolderListener(@NonNull QuickstepWidgetHolder holder,
                @NonNull LauncherAppWidgetHostView view) {
            mView.put(holder, view);
        }

        @UiThread
        public void resetView(@NonNull QuickstepWidgetHolder holder,
                @NonNull AppWidgetHostView view) {
            mView.put(holder, view);
            view.updateAppWidget(mRemoteViews);
        }

        @Override
        @WorkerThread
        public void onUpdateProviderInfo(@Nullable AppWidgetProviderInfo info) {
            mRemoteViews = null;
            executeOnMainExecutor(v -> v.onUpdateProviderInfo(info));
        }

        @Override
        @WorkerThread
        public void updateAppWidget(@Nullable RemoteViews views) {
            mRemoteViews = views;
            executeOnMainExecutor(v -> v.updateAppWidget(mRemoteViews));
        }

        @Override
        @WorkerThread
        public void onViewDataChanged(int viewId) {
            executeOnMainExecutor(v -> v.onViewDataChanged(viewId));
        }

        private void executeOnMainExecutor(Consumer<AppWidgetHostView> consumer) {
            MAIN_EXECUTOR.execute(() -> mView.values().forEach(consumer));
        }
    }

    /**
     * {@code HolderFactory} subclass that takes an interaction handler as one of the parameters
     * when creating a new instance.
     */
    public static class QuickstepHolderFactory extends HolderFactory {

        @SuppressWarnings("unused")
        public QuickstepHolderFactory(Context context) { }

        @Override
        public LauncherWidgetHolder newInstance(@NonNull Context context,
                @Nullable IntConsumer appWidgetRemovedCallback) {
            return newInstance(context, appWidgetRemovedCallback, null);
        }

        /**
         * @param context The context of the caller
         * @param appWidgetRemovedCallback The callback that is called when widgets are removed
         * @param interactionHandler The interaction handler when the widgets are clicked
         * @return A new {@link LauncherWidgetHolder} instance
         */
        public LauncherWidgetHolder newInstance(@NonNull Context context,
                @Nullable IntConsumer appWidgetRemovedCallback,
                @Nullable RemoteViews.InteractionHandler interactionHandler) {

            if (!FeatureFlags.ENABLE_WIDGET_HOST_IN_BACKGROUND.get()) {
                return new LauncherWidgetHolder(context, appWidgetRemovedCallback) {
                    @Override
                    protected AppWidgetHost createHost(Context context,
                            @Nullable IntConsumer appWidgetRemovedCallback) {
                        AppWidgetHost host = super.createHost(context, appWidgetRemovedCallback);
                        host.setInteractionHandler(interactionHandler);
                        return host;
                    }
                };
            }
            return new QuickstepWidgetHolder(context, appWidgetRemovedCallback, interactionHandler);
        }
    }
}
