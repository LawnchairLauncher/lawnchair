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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class WidgetListAdapter extends BaseAdapter {
    private final Launcher mLauncher;
    private List<AppWidgetProviderInfo> mWidgets;
    private final Canvas mCanvas = new Canvas();

    private final int[] mTempSize = new int[2];
    private final Rect mTempRect = new Rect();

    private static final String TAG = "Launcher.WidgetGalleryAdapter";

    WidgetListAdapter(Launcher launcher) {
        mLauncher = launcher;
        mWidgets = AppWidgetManager.getInstance(launcher).getInstalledProviders();
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
        TextView textView;

        if (convertView == null) {
            LayoutInflater inflater =
                (LayoutInflater)mLauncher.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            textView = (TextView) inflater.inflate(
                    R.layout.home_customization_drawer_item, parent, false);
        } else {
            textView = (TextView) convertView;
        }

        AppWidgetProviderInfo info = mWidgets.get(position);
        PackageManager packageManager = mLauncher.getPackageManager();
        String packageName = info.provider.getPackageName();
        Drawable drawable = null;
        if (info.previewImage != 0) {
            drawable = packageManager.getDrawable(packageName, info.previewImage, null);
            if (drawable == null) {
                Log.w(TAG, "Can't load icon drawable 0x" + Integer.toHexString(info.icon)
                        + " for provider: " + info.provider);
            }
        }
        // If we don't have a preview image, create a default one
        if (drawable == null) {
            Resources resources = mLauncher.getResources();

            // Determine the size the widget will take in the layout
            mLauncher.getWorkspace().estimateChildSize(info.minWidth, info.minHeight, mTempSize);

            // Create a new bitmap to hold the widget preview
            final int width = mTempSize[0];
            final int height = mTempSize[1];
            Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            mCanvas.setBitmap(bitmap);
            // For some reason, we must re-set the clip rect here, otherwise it will be wrong
            mCanvas.clipRect(0, 0, width, height, Op.REPLACE);

            Drawable background = resources.getDrawable(R.drawable.default_widget_preview);
            background.setBounds(0, 0, width, height);
            background.draw(mCanvas);

            // Draw the icon vertically centered, flush left
            Drawable icon = packageManager.getDrawable(packageName, info.icon, null);
            background.getPadding(mTempRect);

            final int left = mTempRect.left;
            final int top = (height - icon.getIntrinsicHeight()) / 2;
            icon.setBounds(
                    left, top, left + icon.getIntrinsicWidth(), top + icon.getIntrinsicHeight());
            icon.draw(mCanvas);

            drawable = new BitmapDrawable(resources, bitmap);
        }
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        textView.setCompoundDrawables(null, drawable, null, null);
        textView.setText(info.label);
        // Store the widget info on the associated view so we can easily fetch it later
        textView.setTag(info);

        return textView;
    }
}
