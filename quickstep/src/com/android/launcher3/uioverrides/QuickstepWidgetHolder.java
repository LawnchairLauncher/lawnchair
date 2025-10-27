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

import static com.android.launcher3.BuildConfigs.WIDGETS_ENABLED;
import static com.android.launcher3.uioverrides.QuickstepAppWidgetHostProvider.getStaticQuickstepHost;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.widget.ListenableAppWidgetHost.getWidgetHolderExecutor;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.util.SparseArray;
import android.widget.RemoteViews;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LauncherWidgetHolder;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * {@link LauncherWidgetHolder} that puts the app widget host in the background
 */
public final class QuickstepWidgetHolder extends LauncherWidgetHolder {

    private static final UpdateKey<AppWidgetProviderInfo> KEY_PROVIDER_UPDATE =
            AppWidgetHostView::onUpdateProviderInfo;
    private static final UpdateKey<RemoteViews> KEY_VIEWS_UPDATE =
            AppWidgetHostView::updateAppWidget;
    private static final UpdateKey<Integer> KEY_VIEW_DATA_CHANGED =
            AppWidgetHostView::onViewDataChanged;

    private static final SparseArray<QuickstepWidgetHolderListener> sListeners =
            new SparseArray<>();

    private final UpdateHandler mUpdateHandler = this::onWidgetUpdate;

    // Map to all pending updated keyed with appWidgetId;
    private final SparseArray<PendingUpdate> mPendingUpdateMap = new SparseArray<>();

    @AssistedInject
    public QuickstepWidgetHolder(@Assisted("UI_CONTEXT") @NonNull Context context) {
        super(context, getStaticQuickstepHost());
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

        getWidgetHolderExecutor().execute(() -> {
            mWidgetHost.setAppWidgetHidden();
            setListeningFlag(false);
        });
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
        return () -> holderListener.removeHolder(handler);
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
        widgetView.setAppWidget(appWidgetId, appWidget);
        widgetView.updateAppWidget(getHolderListener(appWidgetId).addHolder(mUpdateHandler));
        return widgetView;
    }

    private static QuickstepWidgetHolderListener getHolderListener(int appWidgetId) {
        QuickstepWidgetHolderListener listener = sListeners.get(appWidgetId);
        if (listener == null) {
            listener = new QuickstepWidgetHolderListener(appWidgetId);
            getStaticQuickstepHost().setListener(appWidgetId, listener);
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
            sListeners.valueAt(i).removeHolder(mUpdateHandler);
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

        public RemoteViews addHolder(@NonNull UpdateHandler holder) {
            MAIN_EXECUTOR.execute(() -> mListeningHolders.add(holder));
            return mRemoteViews;
        }

        public void removeHolder(@NonNull UpdateHandler holder) {
            MAIN_EXECUTOR.execute(() -> mListeningHolders.remove(holder));
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


    /** A factory that generates new instances of {@code LauncherWidgetHolder} */
    @AssistedFactory
    public interface QuickstepWidgetHolderFactory extends WidgetHolderFactory {

        @Override
        QuickstepWidgetHolder newInstance(@Assisted("UI_CONTEXT") @NonNull Context context);
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
