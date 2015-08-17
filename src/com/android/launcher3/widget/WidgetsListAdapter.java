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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.model.WidgetsModel;

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

    private Launcher mLauncher;
    private LayoutInflater mLayoutInflater;

    private WidgetsModel mWidgetsModel;
    private WidgetPreviewLoader mWidgetPreviewLoader;

    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;

    private static final int PRESET_INDENT_SIZE_TABLET = 56;
    private int mIndent = 0;

    public WidgetsListAdapter(Context context,
            View.OnClickListener iconClickListener,
            View.OnLongClickListener iconLongClickListener,
            Launcher launcher) {
        mLayoutInflater = LayoutInflater.from(context);

        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
        mLauncher = launcher;

        setContainerHeight();
    }

    public void setWidgetsModel(WidgetsModel w) {
        mWidgetsModel = w;
    }

    @Override
    public int getItemCount() {
        if (mWidgetsModel == null) {
            return 0;
        }
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
                WidgetCell widget = (WidgetCell) mLayoutInflater.inflate(
                        R.layout.widget_cell, row, false);

                // set up touch.
                widget.setOnClickListener(mIconClickListener);
                widget.setOnLongClickListener(mIconLongClickListener);
                LayoutParams lp = widget.getLayoutParams();
                lp.height = widget.cellSize;
                lp.width = widget.cellSize;
                widget.setLayoutParams(lp);

                row.addView(widget);
            }
        } else if (diff < 0) {
            for (int i=infoList.size() ; i < row.getChildCount(); i++) {
                row.getChildAt(i).setVisibility(View.GONE);
            }
        }

        // Bind the views in the application info section.
        PackageItemInfo infoOut = mWidgetsModel.getPackageItemInfo(pos);
        BubbleTextView tv = ((BubbleTextView) holder.getContent().findViewById(R.id.section));
        tv.applyFromPackageItemInfo(infoOut);

        // Bind the view in the widget horizontal tray region.
        if (getWidgetPreviewLoader() == null) {
            return;
        }
        for (int i=0; i < infoList.size(); i++) {
            WidgetCell widget = (WidgetCell) row.getChildAt(i);
            if (infoList.get(i) instanceof LauncherAppWidgetProviderInfo) {
                LauncherAppWidgetProviderInfo info = (LauncherAppWidgetProviderInfo) infoList.get(i);
                PendingAddWidgetInfo pawi = new PendingAddWidgetInfo(mLauncher, info, null);
                widget.setTag(pawi);
                widget.applyFromAppWidgetProviderInfo(info, mWidgetPreviewLoader);
            } else if (infoList.get(i) instanceof ResolveInfo) {
                ResolveInfo info = (ResolveInfo) infoList.get(i);
                PendingAddShortcutInfo pasi = new PendingAddShortcutInfo(info.activityInfo);
                widget.setTag(pasi);
                widget.applyFromResolveInfo(mLauncher.getPackageManager(), info, mWidgetPreviewLoader);
            }
            widget.ensurePreview();
            widget.setVisibility(View.VISIBLE);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public WidgetsRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (DEBUG) {
            Log.v(TAG, "\nonCreateViewHolder");
        }

        ViewGroup container = (ViewGroup) mLayoutInflater.inflate(
                R.layout.widgets_list_row_view, parent, false);
        LinearLayout cellList = (LinearLayout) container.findViewById(R.id.widgets_cell_list);

        // if the end padding is 0, then container view (horizontal scroll view) doesn't respect
        // the end of the linear layout width + the start padding and doesn't allow scrolling.
        if (Utilities.ATLEAST_JB_MR1) {
            cellList.setPaddingRelative(mIndent, 0, 1, 0);
        } else {
            cellList.setPadding(mIndent, 0, 1, 0);
        }

        return new WidgetsRowViewHolder(container);
    }

    @Override
    public void onViewRecycled(WidgetsRowViewHolder holder) {
        ViewGroup row = ((ViewGroup) holder.getContent().findViewById(R.id.widgets_cell_list));

        for (int i = 0; i < row.getChildCount(); i++) {
            WidgetCell widget = (WidgetCell) row.getChildAt(i);
            widget.clear();
        }
    }

    public boolean onFailedToRecycleView(WidgetsRowViewHolder holder) {
        // If child views are animating, then the RecyclerView may choose not to recycle the view,
        // causing extraneous onCreateViewHolder() calls.  It is safe in this case to continue
        // recycling this view, and take care in onViewRecycled() to cancel any existing
        // animations.
        return true;
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

    private void setContainerHeight() {
        Resources r = mLauncher.getResources();
        DeviceProfile profile = mLauncher.getDeviceProfile();
        if (profile.isLargeTablet || profile.isTablet) {
            mIndent = Utilities.pxFromDp(PRESET_INDENT_SIZE_TABLET, r.getDisplayMetrics());
        }
    }
}
