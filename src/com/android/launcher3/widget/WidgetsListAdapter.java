/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.IconCache;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.R;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.compat.UserHandleCompat;

import java.util.List;

/**
 * List view adapter for the widget tray.
 *
 * <p>Memory vs. Performance:
 * The less number of types of views are inserted into a {@link RecyclerView}, the more recycling
 * happens and less memory is consumed. {@link #getItemViewType} was not overridden as there is
 * only a single type of view.
 */
public class WidgetsListAdapter extends Adapter<WidgetsRowViewHolder> {

    private static final String TAG = "WidgetsListAdapter";
    private static final boolean DEBUG = false;

    private Context mContext;
    private Launcher mLauncher;
    private LayoutInflater mLayoutInflater;
    private IconCache mIconCache;

    private WidgetsModel mWidgetsModel;
    private WidgetPreviewLoader mWidgetPreviewLoader;

    private View.OnTouchListener mTouchListener;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;


    public WidgetsListAdapter(Context context,
            View.OnTouchListener touchListener,
            View.OnClickListener iconClickListener,
            View.OnLongClickListener iconLongClickListener,
            Launcher launcher) {
        mLayoutInflater = LayoutInflater.from(context);
        mContext = context;

        mTouchListener = touchListener;
        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;

        mLauncher = launcher;
        mIconCache = LauncherAppState.getInstance().getIconCache();
    }

    public void setWidgetsModel(WidgetsModel w) {
        mWidgetsModel = w;
    }

    @Override
    public int getItemCount() {
        return mWidgetsModel.getPackageSize();
    }

    @Override
    public void onBindViewHolder(WidgetsRowViewHolder holder, int pos) {
        List<Object> infoList = mWidgetsModel.getSortedWidgets(pos);

        ViewGroup row = ((ViewGroup) holder.getContent().findViewById(R.id.widgets_cell_list));
        if (DEBUG) {
            Log.d(TAG, String.format(
                    "onBindViewHolder [pos=%d, widget#=%d, row.getChildCount=%d]",
                    pos, infoList.size(), row.getChildCount()));
        }

        // Add more views.
        // if there are too many, hide them.
        int diff = infoList.size() - row.getChildCount();
        if (diff > 0) {
            for (int i = 0; i < diff; i++) {
                WidgetCell widget = new WidgetCell(mContext);
                widget = (WidgetCell) mLayoutInflater.inflate(
                        R.layout.widget_cell, row, false);

                // set up touch.
                widget.setOnClickListener(mIconClickListener);
                widget.setOnLongClickListener(mIconLongClickListener);
                widget.setOnTouchListener(mTouchListener);
                row.addView(widget);
            }
        } else if (diff < 0) {
            for (int i=infoList.size() ; i < row.getChildCount(); i++) {
                row.getChildAt(i).setVisibility(View.GONE);
            }
        }

        // Bind the views in the application info section.
        PackageItemInfo infoOut = mWidgetsModel.getPackageItemInfo(pos);
        if (infoOut.usingLowResIcon) {
            // TODO(hyunyoungs): call this in none UI thread in the same way as BubbleTextView.
            mIconCache.getTitleAndIconForApp(infoOut.packageName,
                    UserHandleCompat.myUserHandle(), false /* useLowResIcon */, infoOut);
        }
        ((TextView) holder.getContent().findViewById(R.id.section)).setText(infoOut.title);
        ImageView iv = (ImageView) holder.getContent().findViewById(R.id.section_image);
        iv.setImageBitmap(infoOut.iconBitmap);

        // Bind the view in the widget horizontal tray region.
        for (int i=0; i < infoList.size(); i++) {
            WidgetCell widget = (WidgetCell) row.getChildAt(i);
            widget.reset();
            if (getWidgetPreviewLoader() == null) {
                return;
            }
            if (infoList.get(i) instanceof LauncherAppWidgetProviderInfo) {
                LauncherAppWidgetProviderInfo info = (LauncherAppWidgetProviderInfo) infoList.get(i);
                PendingAddWidgetInfo pawi = new PendingAddWidgetInfo(info, null);
                widget.setTag(pawi);
                widget.applyFromAppWidgetProviderInfo(info, -1, mWidgetPreviewLoader);
            } else if (infoList.get(i) instanceof ResolveInfo) {
                ResolveInfo info = (ResolveInfo) infoList.get(i);
                PendingAddShortcutInfo pasi = new PendingAddShortcutInfo(info.activityInfo);
                widget.setTag(pasi);
                widget.applyFromResolveInfo(mLauncher.getPackageManager(), info, mWidgetPreviewLoader);
            }
            widget.setVisibility(View.VISIBLE);
            widget.ensurePreview();
        }
    }

    @Override
    public WidgetsRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (DEBUG) {
            Log.v(TAG, String.format("\nonCreateViewHolder, [widget#=%d]", viewType));
        }

        ViewGroup container = (ViewGroup) mLayoutInflater.inflate(
                R.layout.widgets_list_row_view, parent, false);
        return new WidgetsRowViewHolder(container);
    }

    @Override
    public long getItemId(int pos) {
        return pos;
    }

    private WidgetPreviewLoader getWidgetPreviewLoader() {
        if (mWidgetPreviewLoader == null) {
            mWidgetPreviewLoader = LauncherAppState.getInstance().getWidgetCache();
        }
        return mWidgetPreviewLoader;
    }
}
