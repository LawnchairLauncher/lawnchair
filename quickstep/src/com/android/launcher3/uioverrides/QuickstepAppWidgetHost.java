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

import static com.android.launcher3.widget.LauncherWidgetHolder.APPWIDGET_HOST_ID;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.util.Executors;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.util.function.IntConsumer;

/**
 * {@link AppWidgetHost} that is used to receive the changes to the widgets without
 * storing any {@code Activity} info like that of the launcher.
 */
final class QuickstepAppWidgetHost extends AppWidgetHost {
    private final @NonNull Context mContext;
    private final @NonNull IntConsumer mAppWidgetRemovedCallback;
    private final @NonNull LauncherWidgetHolder.ProviderChangedListener mProvidersChangedListener;

    QuickstepAppWidgetHost(@NonNull Context context, @NonNull IntConsumer appWidgetRemovedCallback,
            @NonNull LauncherWidgetHolder.ProviderChangedListener listener,
            @NonNull Looper looper) {
        super(context, APPWIDGET_HOST_ID, null, looper);
        mContext = context;
        mAppWidgetRemovedCallback = appWidgetRemovedCallback;
        mProvidersChangedListener = listener;
    }

    @Override
    protected void onProvidersChanged() {
        mProvidersChangedListener.notifyWidgetProvidersChanged();
    }

    @Override
    public void onAppWidgetRemoved(int appWidgetId) {
        // Route the call via model thread, in case it comes while a loader-bind is in progress
        Executors.MODEL_EXECUTOR.execute(
                () -> Executors.MAIN_EXECUTOR.execute(
                        () -> mAppWidgetRemovedCallback.accept(appWidgetId)));
    }

    @Override
    protected void onProviderChanged(int appWidgetId, @NonNull AppWidgetProviderInfo appWidget) {
        LauncherAppWidgetProviderInfo info = LauncherAppWidgetProviderInfo.fromProviderInfo(
                mContext, appWidget);
        super.onProviderChanged(appWidgetId, info);
        // The super method updates the dimensions of the providerInfo. Update the
        // launcher spans accordingly.
        info.initSpans(mContext, LauncherAppState.getIDP(mContext));
    }
}
