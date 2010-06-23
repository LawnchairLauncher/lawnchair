/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

import com.android.launcher.R;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.util.List;

public class WidgetGalleryAdapter extends BaseAdapter {
    private LayoutInflater mLayoutInflater;
    private PackageManager mPackageManager;
    private List<AppWidgetProviderInfo> mWidgets;
    private static final String TAG = "Launcher.WidgetGalleryAdapter";

    WidgetGalleryAdapter(Context context) {
        mLayoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        mWidgets = widgetManager.getInstalledProviders();
        mPackageManager = context.getPackageManager();
    }

    public int getCount() {
        return mWidgets.size();
    }

    public Object getItem(int position) {
        return mWidgets.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imageView;

        if (convertView == null) {
            imageView = (ImageView) mLayoutInflater.inflate(R.layout.widget_item, parent, false);
        } else {
            imageView = (ImageView) convertView;
        }

        AppWidgetProviderInfo info = mWidgets.get(position);
        Drawable image = null;
        if (info.previewImage != 0) {
            image = mPackageManager.getDrawable(
                    info.provider.getPackageName(), info.previewImage, null);
            if (image == null) {
                Log.w(TAG, "Can't load icon drawable 0x" + Integer.toHexString(info.icon)
                        + " for provider: " + info.provider);
            }
        }
        if (image == null) {
            image = mPackageManager.getDrawable(info.provider.getPackageName(), info.icon, null);
        }
        imageView.setImageDrawable(image);

        return imageView;
    }
}
