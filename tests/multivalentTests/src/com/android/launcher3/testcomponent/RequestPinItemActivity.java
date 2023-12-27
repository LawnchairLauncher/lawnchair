/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.testcomponent;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.IntentSender;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.widget.RemoteViews;

/**
 * Sample activity to request pinning an item.
 */
@TargetApi(26)
public class RequestPinItemActivity extends BaseTestingActivity {

    private PendingIntent mCallback = null;
    private String mShortcutId = "test-id";
    private int mRemoteViewColor = Color.TRANSPARENT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addButton("Pin Shortcut", "pinShortcut");
        addButton("Pin Widget without config ", "pinWidgetNoConfig");
        addButton("Pin Widget with config", "pinWidgetWithConfig");
    }

    public void setCallback(PendingIntent callback) {
        mCallback = callback;
    }

    public void setRemoteViewColor(int color) {
        mRemoteViewColor = color;
    }

    public void setShortcutId(String id) {
        mShortcutId = id;
    }

    public void pinShortcut() {
        ShortcutManager sm = getSystemService(ShortcutManager.class);

        // Generate icon
        int r = sm.getIconMaxWidth() / 2;
        Bitmap icon = Bitmap.createBitmap(r * 2, r * 2, Bitmap.Config.ARGB_8888);
        Paint p = new Paint();
        p.setColor(Color.RED);
        new Canvas(icon).drawCircle(r, r, r, p);

        ShortcutInfo info = new ShortcutInfo.Builder(this, mShortcutId)
                .setIntent(getPackageManager().getLaunchIntentForPackage(getPackageName()))
                .setIcon(Icon.createWithBitmap(icon))
                .setShortLabel("Test shortcut")
                .build();

        IntentSender callback = mCallback == null ? null : mCallback.getIntentSender();
        sm.requestPinShortcut(info, callback);
    }

    public void pinWidgetNoConfig() {
        requestWidget(new ComponentName(this, AppWidgetNoConfig.class));
    }

    public void pinWidgetWithConfig() {
        requestWidget(new ComponentName(this, AppWidgetWithConfig.class));
    }

    private void requestWidget(ComponentName cn) {
        Bundle extras = null;
        if (mRemoteViewColor != Color.TRANSPARENT) {
            int layoutId = getResources().getIdentifier(
                    "test_layout_appwidget_view", "layout", getPackageName());
            RemoteViews views = new RemoteViews(getPackageName(), layoutId);
            views.setInt(android.R.id.icon, "setBackgroundColor", mRemoteViewColor);
            extras = new Bundle();
            extras.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, views);
        }

        AppWidgetManager.getInstance(this).requestPinAppWidget(cn, extras, mCallback);
    }
}
