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

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import app.lawnchair.LawnchairAppWidgetHostView;

/**
 * Specific {@link AppWidgetHost} that creates our {@link LauncherAppWidgetHostView}
 * which correctly captures all long-press events. This ensures that users can
 * always pick up and move widgets.
 */
class LauncherAppWidgetHost extends ListenableAppWidgetHost {

    @Nullable
    private ListenableHostView mViewToRecycle;

    LauncherAppWidgetHost(@NonNull Context context, int appWidgetId) {
        super(context, appWidgetId);
    }

    /**
     * Sets the view to be recycled for the next widget creation.
     */
    public void recycleViewForNextCreation(ListenableHostView viewToRecycle) {
        mViewToRecycle = viewToRecycle;
    }

    @VisibleForTesting
    @Nullable ListenableHostView getViewToRecycle() {
        return mViewToRecycle;
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
     * The same as super.clearViews(), except with the scope exposed
     */
    @Override
    public void clearViews() {
        super.clearViews();
    }
}
