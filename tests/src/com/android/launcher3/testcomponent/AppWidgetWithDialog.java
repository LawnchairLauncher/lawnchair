/*
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

package com.android.launcher3.testcomponent;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * A simple app widget with shows a dialog on clicking.
 */
public class AppWidgetWithDialog extends AppWidgetNoConfig {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        int layoutId = context.getResources().getIdentifier(
                "test_layout_appwidget_blue", "layout", context.getPackageName());
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

        PendingIntent pi = PendingIntent.getActivity(context, 0,
                new Intent(context, DialogTestActivity.class), PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(android.R.id.content, pi);
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetIds, views);
    }
}
