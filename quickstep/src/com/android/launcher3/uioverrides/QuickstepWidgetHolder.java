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
import android.util.Log;
import android.util.SparseArray;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LauncherWidgetHolder;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/**
 * {@link LauncherWidgetHolder} that puts the app widget host in the background
 */
public final class QuickstepWidgetHolder extends LauncherWidgetHolder {

    private static final String TAG = "QuickstepWidgetHolder";

    private static final UpdateKey<AppWidgetProviderInfo> KEY_PROVIDER_UPDATE =
            AppWidgetHostView::onUpdateProviderInfo;
    private static final UpdateKey<RemoteViews> KEY_VIEWS_UPDATE =
            AppWidgetHostView::updateAppWidget;
    private static final UpdateKey<Integer> KEY_VIEW_DATA_CHANGED =
            AppWidgetHostView::onViewDataChanged;

    private static final List<QuickstepWidgetHolder> sHolders = new ArrayList<>();
    private static final SparseArray<QuickstepWidgetHolderListener> sListeners =
            new SparseArray<>();

    private static AppWidgetHost sWidgetHost = null;

    private final SparseArray<AppWidgetHostView> mViews = new SparseArray<>();

    private final @Nullable RemoteViews.InteractionHandler mInteractionHandler;

    private final @NonNull IntConsumer mAppWidgetRemovedCallback;

    private final ArrayList<ProviderChangedListener> mProviderChangedListeners = new ArrayList<>();
    // Map to all pending updated keyed with appWidgetId;
    private final SparseArray<PendingUpdate> mPendingUpdateMap = new SparseArray<>();

    private QuickstepWidgetHolder(@NonNull Context context,
            @Nullable IntConsumer appWidgetRemovedCallback,
            @Nullable RemoteViews.InteractionHandler interactionHandler) {
        super(context, appWidgetRemovedCallback);
        mAppWidgetRemovedCallback = appWidgetRemovedCallback != null ? appWidgetRemovedCallback
                : i -> {};
        mInteractionHandler = interactionHandler;
        MAIN_EXECUTOR.execute(() -> sHolders.add(this));
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
                            sHolders.forEach(h ->
                                    // Listeners might remove themselves from the list during the
                                    // iteration. Creating a copy of the list to avoid exceptions
                                    // for concurrent modification.
                                    new ArrayList<>(h.mProviderChangedListeners).forEach(
                                    ProviderChangedListener::notifyWidgetProvidersChanged))),
                    UI_HELPER_EXECUTOR.getLooper());
            if (!WidgetsModel.GO_DISABLE_WIDGETS) {
                sWidgetHost.startListening();
            }
        }
        return sWidgetHost;
    }

    @Override
    protected void updateDeferredView() {
        super.updateDeferredView();
        int count = mPendingUpdateMap.size();
        for (int i = 0; i < count; i++) {
            int widgetId = mPendingUpdateMap.keyAt(i);
            AppWidgetHostView view = mViews.get(widgetId);
            if (view == null) {
                continue;
            }
            PendingUpdate pendingUpdate = mPendingUpdateMap.valueAt(i);
            if (pendingUpdate == null) {
                continue;
            }
            if (pendingUpdate.providerInfo != null) {
                KEY_PROVIDER_UPDATE.accept(view, pendingUpdate.providerInfo);
            }
            if (pendingUpdate.remoteViews != null) {
                KEY_VIEWS_UPDATE.accept(view, pendingUpdate.remoteViews);
            }
            pendingUpdate.changedViews.forEach(
                    viewId -> KEY_VIEW_DATA_CHANGED.accept(view, viewId));
        }
        mPendingUpdateMap.clear();
    }

    private <T> void onWidgetUpdate(int widgetId, UpdateKey<T> key, T data) {
        if (isListening()) {
            AppWidgetHostView view = mViews.get(widgetId);
            if (view == null) {
                return;
            }
            key.accept(view, data);
            return;
        }

        PendingUpdate pendingUpdate = mPendingUpdateMap.get(widgetId);
        if (pendingUpdate == null) {
            pendingUpdate = new PendingUpdate();
            mPendingUpdateMap.put(widgetId, pendingUpdate);
        }

        if (KEY_PROVIDER_UPDATE.equals(key)) {
            // For provider change, remove all updates
            pendingUpdate.providerInfo = (AppWidgetProviderInfo) data;
            pendingUpdate.remoteViews = null;
            pendingUpdate.changedViews.clear();
        } else if (KEY_VIEWS_UPDATE.equals(key)) {
            // For views update, remove all previous updates, except the provider
            pendingUpdate.remoteViews = (RemoteViews) data;
        } else if (KEY_VIEW_DATA_CHANGED.equals(key)) {
            pendingUpdate.changedViews.add((Integer) data);
        }
    }

    /**
     * Delete the specified app widget from the host
     * @param appWidgetId The ID of the app widget to be deleted
     */
    @Override
    public void deleteAppWidgetId(int appWidgetId) {
        super.deleteAppWidgetId(appWidgetId);
        mViews.remove(appWidgetId);
        sListeners.remove(appWidgetId);
    }

    /**
     * Called when the launcher is destroyed
     */
    @Override
    public void destroy() {
        try {
            MAIN_EXECUTOR.submit(() -> sHolders.remove(this)).get();
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove self from holder list", e);
        }
    }

    @Override
    protected boolean shouldListen(int flags) {
        return (flags & (FLAG_STATE_IS_NORMAL | FLAG_ACTIVITY_STARTED))
                == (FLAG_STATE_IS_NORMAL | FLAG_ACTIVITY_STARTED);
    }

    /**
     * Add a listener that is triggered when the providers of the widgets are changed
     * @param listener The listener that notifies when the providers changed
     */
    @Override
    public void addProviderChangeListener(
            @NonNull LauncherWidgetHolder.ProviderChangedListener listener) {
        MAIN_EXECUTOR.execute(() -> mProviderChangedListeners.add(listener));
    }

    /**
     * Remove the specified listener from the host
     * @param listener The listener that is to be removed from the host
     */
    @Override
    public void removeProviderChangeListener(
            LauncherWidgetHolder.ProviderChangedListener listener) {
        MAIN_EXECUTOR.execute(() -> mProviderChangedListeners.remove(listener));
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

        if (appWidget.isCustomWidget()) {
            LauncherAppWidgetHostView lahv = new LauncherAppWidgetHostView(context);
            lahv.setAppWidget(appWidgetId, appWidget);
            CustomWidgetManager.INSTANCE.get(context).onViewCreated(lahv);
            return lahv;
        }

        LauncherAppWidgetHostView widgetView = getPendingView(appWidgetId);
        if (widgetView != null) {
            removePendingView(appWidgetId);
        } else {
            widgetView = new LauncherAppWidgetHostView(context);
        }
        widgetView.setIsWidgetCachingDisabled(true);
        widgetView.setInteractionHandler(mInteractionHandler);
        widgetView.setAppWidget(appWidgetId, appWidget);
        mViews.put(appWidgetId, widgetView);

        QuickstepWidgetHolderListener listener = sListeners.get(appWidgetId);
        if (listener == null) {
            listener = new QuickstepWidgetHolderListener(appWidgetId);
            sWidgetHost.setListener(appWidgetId, listener);
            sListeners.put(appWidgetId, listener);
        }
        RemoteViews remoteViews = listener.addHolder(this);
        widgetView.updateAppWidget(remoteViews);

        return widgetView;
    }

    /**
     * Clears all the views from the host
     */
    @Override
    public void clearViews() {
        mViews.clear();
        for (int i = sListeners.size() - 1; i >= 0; i--) {
            sListeners.valueAt(i).mListeningHolders.remove(this);
        }
    }

    private static class QuickstepWidgetHolderListener
            implements AppWidgetHost.AppWidgetHostListener {

        // Static listeners should use a set that is backed by WeakHashMap to avoid memory leak
        private final Set<QuickstepWidgetHolder> mListeningHolders = Collections.newSetFromMap(
                new WeakHashMap<>());

        private final int mWidgetId;

        private @Nullable RemoteViews mRemoteViews;

        QuickstepWidgetHolderListener(int widgetId) {
            mWidgetId = widgetId;
        }

        @UiThread
        @Nullable
        public RemoteViews addHolder(@NonNull QuickstepWidgetHolder holder) {
            mListeningHolders.add(holder);
            return mRemoteViews;
        }

        @Override
        @WorkerThread
        public void onUpdateProviderInfo(@Nullable AppWidgetProviderInfo info) {
            mRemoteViews = null;
            executeOnMainExecutor(KEY_PROVIDER_UPDATE, info);
        }

        @Override
        @WorkerThread
        public void updateAppWidget(@Nullable RemoteViews views) {
            mRemoteViews = views;
            executeOnMainExecutor(KEY_VIEWS_UPDATE, mRemoteViews);
        }

        @Override
        @WorkerThread
        public void onViewDataChanged(int viewId) {
            executeOnMainExecutor(KEY_VIEW_DATA_CHANGED, viewId);
        }

        private <T> void executeOnMainExecutor(UpdateKey<T> key, T data) {
            MAIN_EXECUTOR.execute(() -> mListeningHolders.forEach(holder ->
                    holder.onWidgetUpdate(mWidgetId, key, data)));
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

    private static class PendingUpdate {
        public final IntSet changedViews = new IntSet();
        public AppWidgetProviderInfo providerInfo;
        public RemoteViews remoteViews;
    }

    private interface UpdateKey<T> extends BiConsumer<AppWidgetHostView, T> { }
}
