/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.allapps.search;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * A placeholder {@link AppWidgetHostView} used for managing widget search results
 */
public class SearchWidgetInfoContainer extends AppWidgetHostView {
    private int mAppWidgetId;
    private AppWidgetProviderInfo mProviderInfo;
    private RemoteViews mViews;
    private List<AppWidgetHostView> mListeners = new ArrayList<>();

    public SearchWidgetInfoContainer(Context context) {
        super(context);
    }

    @Override
    public void setAppWidget(int appWidgetId, AppWidgetProviderInfo info) {
        mAppWidgetId = appWidgetId;
        mProviderInfo = info;
        for (AppWidgetHostView listener : mListeners) {
            listener.setAppWidget(mAppWidgetId, mProviderInfo);
        }
    }

    @Override
    public void updateAppWidget(RemoteViews remoteViews) {
        mViews = remoteViews;
        for (AppWidgetHostView listener : mListeners) {
            listener.updateAppWidget(remoteViews);
        }
    }

    /**
     * Create a live {@link AppWidgetHostView} from placeholder
     */
    public void attachWidget(AppWidgetHostView hv) {
        hv.setTag(getTag());
        hv.setAppWidget(mAppWidgetId, mProviderInfo);
        hv.updateAppWidget(mViews);
        mListeners.add(hv);
    }

    /**
     * stops AppWidgetHostView from getting updates
     */
    public void detachWidget(AppWidgetHostView hostView) {
        mListeners.remove(hostView);
    }

    /**
     * Removes all AppWidgetHost update listeners
     */
    public void clearListeners() {
        mListeners.clear();
    }
}
