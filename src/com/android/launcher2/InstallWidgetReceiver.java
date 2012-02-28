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

import java.util.List;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.launcher.R;


/**
 * We will likely flesh this out later, to handle allow external apps to place widgets, but for now,
 * we just want to expose the action around for checking elsewhere.
 */
public class InstallWidgetReceiver {
    public static final String ACTION_INSTALL_WIDGET =
            "com.android.launcher.action.INSTALL_WIDGET";
    public static final String ACTION_SUPPORTS_CLIPDATA_MIMETYPE =
            "com.android.launcher.action.SUPPORTS_CLIPDATA_MIMETYPE";

    // Currently not exposed.  Put into Intent when we want to make it public.
    // TEMP: Should we call this "EXTRA_APPWIDGET_PROVIDER"?
    public static final String EXTRA_APPWIDGET_COMPONENT =
        "com.android.launcher.extra.widget.COMPONENT";
    public static final String EXTRA_APPWIDGET_CONFIGURATION_DATA_MIME_TYPE =
        "com.android.launcher.extra.widget.CONFIGURATION_DATA_MIME_TYPE";
    public static final String EXTRA_APPWIDGET_CONFIGURATION_DATA =
        "com.android.launcher.extra.widget.CONFIGURATION_DATA";

    /**
     * A simple data class that contains per-item information that the adapter below can reference.
     */
    public static class WidgetMimeTypeHandlerData {
        public ResolveInfo resolveInfo;
        public AppWidgetProviderInfo widgetInfo;

        public WidgetMimeTypeHandlerData(ResolveInfo rInfo, AppWidgetProviderInfo wInfo) {
            resolveInfo = rInfo;
            widgetInfo = wInfo;
        }
    }

    /**
     * The ListAdapter which presents all the valid widgets that can be created for a given drop.
     */
    public static class WidgetListAdapter implements ListAdapter, DialogInterface.OnClickListener {
        private LayoutInflater mInflater;
        private Launcher mLauncher;
        private String mMimeType;
        private ClipData mClipData;
        private List<WidgetMimeTypeHandlerData> mActivities;
        private CellLayout mTargetLayout;
        private int mTargetLayoutScreen;
        private int[] mTargetLayoutPos;

        public WidgetListAdapter(Launcher l, String mimeType, ClipData data,
                List<WidgetMimeTypeHandlerData> list, CellLayout target,
                int targetScreen, int[] targetPos) {
            mLauncher = l;
            mMimeType = mimeType;
            mClipData = data;
            mActivities = list;
            mTargetLayout = target;
            mTargetLayoutScreen = targetScreen;
            mTargetLayoutPos = targetPos;
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
        }

        @Override
        public int getCount() {
            return mActivities.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Context context = parent.getContext();
            final PackageManager packageManager = context.getPackageManager();

            // Lazy-create inflater
            if (mInflater == null) {
                mInflater = LayoutInflater.from(context);
            }

            // Use the convert-view where possible
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.external_widget_drop_list_item, parent,
                        false);
            }

            final WidgetMimeTypeHandlerData data = mActivities.get(position);
            final ResolveInfo resolveInfo = data.resolveInfo;
            final AppWidgetProviderInfo widgetInfo = data.widgetInfo;

            // Set the icon
            Drawable d = resolveInfo.loadIcon(packageManager);
            ImageView i = (ImageView) convertView.findViewById(R.id.provider_icon);
            i.setImageDrawable(d);

            // Set the text
            final CharSequence component = resolveInfo.loadLabel(packageManager);
            final int[] widgetSpan = new int[2];
            mTargetLayout.rectToCell(widgetInfo.minWidth, widgetInfo.minHeight, widgetSpan);
            TextView t = (TextView) convertView.findViewById(R.id.provider);
            t.setText(context.getString(R.string.external_drop_widget_pick_format,
                    component, widgetSpan[0], widgetSpan[1]));

            return convertView;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return mActivities.isEmpty();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            final AppWidgetProviderInfo widgetInfo = mActivities.get(which).widgetInfo;

            final PendingAddWidgetInfo createInfo = new PendingAddWidgetInfo(widgetInfo, mMimeType,
                    mClipData);
            mLauncher.addAppWidgetFromDrop(createInfo, LauncherSettings.Favorites.CONTAINER_DESKTOP,
                    mTargetLayoutScreen, null, null, mTargetLayoutPos);
        }
    }
}
