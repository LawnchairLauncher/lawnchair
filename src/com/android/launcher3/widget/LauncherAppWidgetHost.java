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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;

import java.util.ArrayList;
import java.util.function.IntConsumer;

/**
 * Specific {@link AppWidgetHost} that creates our {@link LauncherAppWidgetHostView}
 * which correctly captures all long-press events. This ensures that users can
 * always pick up and move widgets.
 */
class LauncherAppWidgetHost extends AppWidgetHost {
    @NonNull
    private final ArrayList<LauncherWidgetHolder.ProviderChangedListener>
            mProviderChangeListeners = new ArrayList<>();

    @NonNull
    private final Context mContext;

    @Nullable
    private final IntConsumer mAppWidgetRemovedCallback;

    @NonNull
    private final LauncherWidgetHolder mHolder;

    public LauncherAppWidgetHost(@NonNull Context context,
            @Nullable IntConsumer appWidgetRemovedCallback, @NonNull LauncherWidgetHolder holder) {
        super(context, APPWIDGET_HOST_ID);
        mContext = context;
        mAppWidgetRemovedCallback = appWidgetRemovedCallback;
        mHolder = holder;
    }

    /**
     * Add a listener that is triggered when the providers of the widgets are changed
     * @param listener The listener that notifies when the providers changed
     */
    public void addProviderChangeListener(
            @NonNull LauncherWidgetHolder.ProviderChangedListener listener) {
        mProviderChangeListeners.add(listener);
    }

    /**
     * Remove the specified listener from the host
     * @param listener The listener that is to be removed from the host
     */
    public void removeProviderChangeListener(
            LauncherWidgetHolder.ProviderChangedListener listener) {
        mProviderChangeListeners.remove(listener);
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

    @Override
    @NonNull
    public LauncherAppWidgetHostView onCreateView(Context context, int appWidgetId,
            AppWidgetProviderInfo appWidget) {
        return mHolder.onCreateView(context, appWidgetId, appWidget);
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
        mAppWidgetRemovedCallback.accept(appWidgetId);
    }

    /**
     * The same as super.clearViews(), except with the scope exposed
     */
    @Override
    public void clearViews() {
        super.clearViews();
    }

}
