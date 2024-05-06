/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher3.widget;

import static com.android.launcher3.widget.LauncherWidgetHolder.APPWIDGET_HOST_ID;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.widget.LauncherWidgetHolder.ProviderChangedListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;

/**
 * Specific {@link AppWidgetHost} that creates our {@link LauncherAppWidgetHostView}
 * which correctly captures all long-press events. This ensures that users can
 * always pick up and move widgets.
 */
class LauncherAppWidgetHost extends AppWidgetHost {
    @NonNull
    private final List<ProviderChangedListener> mProviderChangeListeners;

    @NonNull
    private final Context mContext;

    @Nullable
    private final IntConsumer mAppWidgetRemovedCallback;

    @Nullable
    private ListenableHostView mViewToRecycle;

    public LauncherAppWidgetHost(@NonNull Context context,
            @Nullable IntConsumer appWidgetRemovedCallback,
            List<ProviderChangedListener> providerChangeListeners) {
        super(context, APPWIDGET_HOST_ID);
        mContext = context;
        mAppWidgetRemovedCallback = appWidgetRemovedCallback;
        mProviderChangeListeners = providerChangeListeners;
    }

    @Override
    protected void onProvidersChanged() {
        if (!mProviderChangeListeners.isEmpty()) {
            for (LauncherWidgetHolder.ProviderChangedListener callback :
                    new ArrayList<>(mProviderChangeListeners)) {
                callback.notifyWidgetProvidersChanged();
            }
        }
    }

    /**
     * Sets the view to be recycled for the next widget creation.
     */
    public void recycleViewForNextCreation(ListenableHostView viewToRecycle) {
        mViewToRecycle = viewToRecycle;
    }

    @Override
    @NonNull
    public LauncherAppWidgetHostView onCreateView(Context context, int appWidgetId,
            AppWidgetProviderInfo appWidget) {
        ListenableHostView result =
                mViewToRecycle != null ? mViewToRecycle : new ListenableHostView(context);
        mViewToRecycle = null;
        return result;
    }

    /**
     * Called when the AppWidget provider for a AppWidget has been upgraded to a new apk.
     */
    @Override
    protected void onProviderChanged(int appWidgetId, @NonNull AppWidgetProviderInfo appWidget) {
        LauncherAppWidgetProviderInfo info = LauncherAppWidgetProviderInfo.fromProviderInfo(
                mContext, appWidget);
        super.onProviderChanged(appWidgetId, info);
        // The super method updates the dimensions of the providerInfo. Update the
        // launcher spans accordingly.
        info.initSpans(mContext, LauncherAppState.getIDP(mContext));
    }

    /**
     * Called on an appWidget is removed for a widgetId
     *
     * @param appWidgetId TODO: make this override when SDK is updated
     */
    @Override
    public void onAppWidgetRemoved(int appWidgetId) {
        if (mAppWidgetRemovedCallback == null) {
            return;
        }
        // Route the call via model thread, in case it comes while a loader-bind is in progress
        Executors.MODEL_EXECUTOR.execute(
                () -> Executors.MAIN_EXECUTOR.execute(
                        () -> mAppWidgetRemovedCallback.accept(appWidgetId)));
    }

    /**
     * The same as super.clearViews(), except with the scope exposed
     */
    @Override
    public void clearViews() {
        super.clearViews();
    }

    public static class ListenableHostView extends LauncherAppWidgetHostView {

        private Set<Runnable> mUpdateListeners = Collections.EMPTY_SET;

        ListenableHostView(Context context) {
            super(context);
        }

        @Override
        public void updateAppWidget(RemoteViews remoteViews) {
            super.updateAppWidget(remoteViews);
            mUpdateListeners.forEach(Runnable::run);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(LauncherAppWidgetHostView.class.getName());
        }

        /**
         * Adds a callback to be run everytime the provided app widget updates.
         * @return a closable to remove this callback
         */
        public SafeCloseable addUpdateListener(Runnable callback) {
            if (mUpdateListeners == Collections.EMPTY_SET) {
                mUpdateListeners = Collections.newSetFromMap(new WeakHashMap<>());
            }
            mUpdateListeners.add(callback);
            return () -> mUpdateListeners.remove(callback);
        }
    }
}
