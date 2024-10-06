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

import static com.android.launcher3.BuildConfig.WIDGETS_ENABLED;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.widget.RemoteViews;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LauncherWidgetHolder;

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
public class QuickstepWidgetHolder extends LauncherWidgetHolder {

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

    private final UpdateHandler mUpdateHandler = this::onWidgetUpdate;
    private final @Nullable RemoteViews.InteractionHandler mInteractionHandler;

    private final @NonNull IntConsumer mAppWidgetRemovedCallback;

    // Map to all pending updated keyed with appWidgetId;
    private final SparseArray<PendingUpdate> mPendingUpdateMap = new SparseArray<>();

    public QuickstepWidgetHolder(@NonNull Context context,
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
            if (WIDGETS_ENABLED) {
                sWidgetHost.startListening();
            }
        }
        return sWidgetHost;
    }

    @Override
    protected void updateDeferredView() {
        int count = mPendingUpdateMap.size();
        for (int i = 0; i < count; i++) {
            int widgetId = mPendingUpdateMap.keyAt(i);
            AppWidgetHostView view = mViews.get(widgetId);
            PendingUpdate pendingUpdate = mPendingUpdateMap.valueAt(i);
            if (view == null || pendingUpdate == null) {
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
        sListeners.remove(appWidgetId);
    }

    /**
     * Called when the launcher is destroyed
     */
    @Override
    public void destroy() {
        try {
            MAIN_EXECUTOR.submit(() -> {
                clearViews();
                sHolders.remove(this);
            }).get();
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
     * Stop the host from updating the widget views
     */
    @Override
    public void stopListening() {
        if (!WIDGETS_ENABLED) {
            return;
        }

        try {
            sWidgetHost.setAppWidgetHidden();
        } catch (Throwable t) {
            // Ignore
        }
        setListeningFlag(false);
    }

    @Override
    public SafeCloseable addOnUpdateListener(int appWidgetId,
            LauncherAppWidgetProviderInfo appWidget, Runnable callback) {
        UpdateHandler handler = new UpdateHandler() {
            @Override
            public <T> void onWidgetUpdate(int widgetId, UpdateKey<T> key, T data) {
                if (KEY_VIEWS_UPDATE == key) {
                    callback.run();
                }
            }
        };
        QuickstepWidgetHolderListener holderListener = getHolderListener(appWidgetId);
        holderListener.addHolder(handler);
        return () -> holderListener.mListeningHolders.remove(handler);
    }

    /**
     * Recycling logic:
     * The holder doesn't maintain any states associated with the view, so if the view was
     * initially initialized by this holder, all its state are already set in the view. We just
     * update the RemoteViews for this view again, in case the widget sent an update during the
     * time between inflation and recycle.
     */
    @Override
    protected LauncherAppWidgetHostView recycleExistingView(LauncherAppWidgetHostView view) {
        RemoteViews views = getHolderListener(view.getAppWidgetId()).addHolder(mUpdateHandler);
        view.updateAppWidget(views);
        return view;
    }

    @NonNull
    @Override
    protected LauncherAppWidgetHostView createViewInternal(
            int appWidgetId, @NonNull LauncherAppWidgetProviderInfo appWidget) {
        LauncherAppWidgetHostView widgetView = new LauncherAppWidgetHostView(mContext);
        widgetView.setInteractionHandler(mInteractionHandler);
        widgetView.setAppWidget(appWidgetId, appWidget);
        widgetView.updateAppWidget(getHolderListener(appWidgetId).addHolder(mUpdateHandler));
        return widgetView;
    }

    private static QuickstepWidgetHolderListener getHolderListener(int appWidgetId) {
        QuickstepWidgetHolderListener listener = sListeners.get(appWidgetId);
        if (listener == null) {
            listener = new QuickstepWidgetHolderListener(appWidgetId);
            sWidgetHost.setListener(appWidgetId, listener);
            sListeners.put(appWidgetId, listener);
        }
        return listener;
    }

    /**
     * Clears all the views from the host
     */
    @Override
    public void clearViews() {
        mViews.clear();
        for (int i = sListeners.size() - 1; i >= 0; i--) {
            sListeners.valueAt(i).mListeningHolders.remove(mUpdateHandler);
        }
    }

    /**
     * Clears all the internal widget views excluding the update listeners
     */
    @Override
    public void clearWidgetViews() {
        mViews.clear();
    }

    private static class QuickstepWidgetHolderListener
            implements AppWidgetHost.AppWidgetHostListener {

        // Static listeners should use a set that is backed by WeakHashMap to avoid memory leak
        private final Set<UpdateHandler> mListeningHolders = Collections.newSetFromMap(
                new WeakHashMap<>());

        private final int mWidgetId;

        private @Nullable RemoteViews mRemoteViews;

        QuickstepWidgetHolderListener(int widgetId) {
            mWidgetId = widgetId;
        }

        @UiThread
        @Nullable
        public RemoteViews addHolder(@NonNull UpdateHandler holder) {
            mListeningHolders.add(holder);
            return mRemoteViews;
        }

        @Override
        @AnyThread
        public void onUpdateProviderInfo(@Nullable AppWidgetProviderInfo info) {
            mRemoteViews = null;
            executeOnMainExecutor(KEY_PROVIDER_UPDATE, info);
        }

        @Override
        @AnyThread
        public void updateAppWidget(@Nullable RemoteViews views) {
            mRemoteViews = views;
            executeOnMainExecutor(KEY_VIEWS_UPDATE, mRemoteViews);
        }

        @Override
        @AnyThread
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

    private interface UpdateKey<T> extends BiConsumer<AppWidgetHostView, T> { }

    private interface UpdateHandler {
        <T> void onWidgetUpdate(int widgetId, UpdateKey<T> key, T data);
    }

    private static class PendingUpdate {
        public final IntSet changedViews = new IntSet();
        public AppWidgetProviderInfo providerInfo;
        public RemoteViews remoteViews;
    }
}
