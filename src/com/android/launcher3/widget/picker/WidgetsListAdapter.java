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
package com.android.launcher3.widget.picker;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.TableRow;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.launcher3.R;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.util.LabelComparator;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.picker.WidgetsListHeaderViewHolderBinder.OnHeaderClickListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Recycler view adapter for the widget tray.
 *
 * <p>This adapter supports view binding of subclasses of {@link WidgetsListBaseEntry}. There are 2
 * subclasses: {@link WidgetsListHeader} & {@link WidgetsListContentEntry}.
 * {@link WidgetsListHeader} entries are always visible in the recycler view. At most one
 * {@link WidgetsListContentEntry} is shown in the recycler view at any time. Clicking a
 * {@link WidgetsListHeader} will result in expanding / collapsing a corresponding
 * {@link WidgetsListContentEntry} of the same app.
 */
public class WidgetsListAdapter extends Adapter<ViewHolder> implements OnHeaderClickListener {

    private static final String TAG = "WidgetsListAdapter";
    private static final boolean DEBUG = false;

    /** Uniquely identifies widgets list view type within the app. */
    private static final int VIEW_TYPE_WIDGETS_LIST = R.layout.widgets_list_row_view;
    private static final int VIEW_TYPE_WIDGETS_HEADER = R.layout.widgets_list_row_header;

    private final WidgetsDiffReporter mDiffReporter;
    private final SparseArray<ViewHolderBinder> mViewHolderBinders = new SparseArray<>();
    private final WidgetsListTableViewHolderBinder mWidgetsListTableViewHolderBinder;
    private final WidgetListBaseRowEntryComparator mRowComparator =
            new WidgetListBaseRowEntryComparator();

    private List<WidgetsListBaseEntry> mAllEntries = new ArrayList<>();
    private ArrayList<WidgetsListBaseEntry> mVisibleEntries = new ArrayList<>();
    @Nullable private String mWidgetsContentVisiblePackage = null;

    private Predicate<WidgetsListBaseEntry> mHeaderAndSelectedContentFilter = entry ->
            entry instanceof WidgetsListHeaderEntry
                    || entry.mPkgItem.packageName.equals(mWidgetsContentVisiblePackage);
    @Nullable private Predicate<WidgetsListBaseEntry> mFilter = null;

    public WidgetsListAdapter(Context context, LayoutInflater layoutInflater,
            WidgetPreviewLoader widgetPreviewLoader, IconCache iconCache,
            OnClickListener iconClickListener, OnLongClickListener iconLongClickListener) {
        mDiffReporter = new WidgetsDiffReporter(iconCache, this);
        mWidgetsListTableViewHolderBinder = new WidgetsListTableViewHolderBinder(context,
                layoutInflater, iconClickListener, iconLongClickListener, widgetPreviewLoader);
        mViewHolderBinders.put(VIEW_TYPE_WIDGETS_LIST, mWidgetsListTableViewHolderBinder);
        mViewHolderBinders.put(VIEW_TYPE_WIDGETS_HEADER,
                new WidgetsListHeaderViewHolderBinder(layoutInflater, this::onHeaderClicked));
    }

    public void setFilter(Predicate<WidgetsListBaseEntry> filter) {
        mFilter = filter;
    }

    /**
     * Defers applying bitmap on all the {@link WidgetCell} in the {@param rv}.
     *
     * @see WidgetCell#setApplyBitmapDeferred(boolean)
     */
    public void setApplyBitmapDeferred(boolean isDeferred, RecyclerView rv) {
        mWidgetsListTableViewHolderBinder.setApplyBitmapDeferred(isDeferred);

        for (int i = rv.getChildCount() - 1; i >= 0; i--) {
            ViewHolder viewHolder = rv.getChildViewHolder(rv.getChildAt(i));
            if (viewHolder.getItemViewType() == VIEW_TYPE_WIDGETS_LIST) {
                WidgetsRowViewHolder holder = (WidgetsRowViewHolder) viewHolder;
                for (int j = holder.mTableContainer.getChildCount() - 1; j >= 0; j--) {
                    TableRow row =  (TableRow) holder.mTableContainer.getChildAt(j);
                    for (int k = row.getChildCount() - 1; k >= 0; k--) {
                        ((WidgetCell) row.getChildAt(k)).setApplyBitmapDeferred(isDeferred);
                    }
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return mVisibleEntries.size();
    }

    /** Gets the section name for {@link com.android.launcher3.views.RecyclerViewFastScroller}. */
    public String getSectionName(int pos) {
        return mVisibleEntries.get(pos).mTitleSectionName;
    }

    /** Updates the widget list. */
    public void setWidgets(List<WidgetsListBaseEntry> tempEntries) {
        mAllEntries = tempEntries.stream().sorted(mRowComparator)
                .collect(Collectors.toList());
        updateVisibleEntries();
    }

    private void updateVisibleEntries() {
        mAllEntries.forEach(entry -> {
            if (entry instanceof WidgetsListHeaderEntry) {
                ((WidgetsListHeaderEntry) entry).setIsWidgetListShown(
                        entry.mPkgItem.packageName.equals(mWidgetsContentVisiblePackage));
            }
        });
        List<WidgetsListBaseEntry> newVisibleEntries = mAllEntries.stream()
                .filter(entry -> (mFilter == null || mFilter.test(entry))
                        && mHeaderAndSelectedContentFilter.test(entry))
                .collect(Collectors.toList());
        mDiffReporter.process(mVisibleEntries, newVisibleEntries, mRowComparator);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int pos) {
        ViewHolderBinder viewHolderBinder = mViewHolderBinders.get(getItemViewType(pos));
        viewHolderBinder.bindViewHolder(holder, mVisibleEntries.get(pos));
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (DEBUG) {
            Log.v(TAG, "\nonCreateViewHolder");
        }

        return mViewHolderBinders.get(viewType).newViewHolder(parent);
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        mViewHolderBinders.get(holder.getItemViewType()).unbindViewHolder(holder);
    }

    @Override
    public boolean onFailedToRecycleView(ViewHolder holder) {
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

    @Override
    public int getItemViewType(int pos) {
        WidgetsListBaseEntry entry = mVisibleEntries.get(pos);
        if (entry instanceof WidgetsListContentEntry) {
            return VIEW_TYPE_WIDGETS_LIST;
        } else if (entry instanceof WidgetsListHeaderEntry) {
            return VIEW_TYPE_WIDGETS_HEADER;
        }
        throw new UnsupportedOperationException("ViewHolderBinder not found for " + entry);
    }

    @Override
    public void onHeaderClicked(boolean showWidgets, String expandedPackage) {
        if (showWidgets) {
            mWidgetsContentVisiblePackage = expandedPackage;
            updateVisibleEntries();
        } else if (expandedPackage.equals(mWidgetsContentVisiblePackage)) {
            mWidgetsContentVisiblePackage = null;
            updateVisibleEntries();
        }
    }

    /**
     * Sets the max horizontal spans that are allowed for grouping more than one widgets in a table
     * row.
     *
     * <p>If there is only one widget in a row, that widget horizontal span is allowed to exceed
     * {@code maxHorizontalSpans}.
     * <p>Let's say the max horizontal spans is set to 5. Widgets can be grouped in the same row if
     * their total horizontal spans added don't exceed 5.
     * Example 1: Row 1: 2x2, 2x3, 1x1. Total horizontal spans is 5. This is okay.
     * Example 2: Row 1: 2x2, 4x3, 1x1. the total horizontal spans is 7. This is wrong.
     *            4x3 and 1x1 should be moved to a new row.
     * Example 3: Row 1: 6x4. This is okay because this is the only item in the row.
     */
    public void setMaxHorizontalSpansPerRow(int maxHorizontalSpans) {
        mWidgetsListTableViewHolderBinder.setMaxSpansPerRow(maxHorizontalSpans);
    }

    /** Comparator for sorting WidgetListRowEntry based on package title. */
    public static class WidgetListBaseRowEntryComparator implements
            Comparator<WidgetsListBaseEntry> {

        private final LabelComparator mComparator = new LabelComparator();

        @Override
        public int compare(WidgetsListBaseEntry a, WidgetsListBaseEntry b) {
            return mComparator.compare(a.mPkgItem.title.toString(), b.mPkgItem.title.toString());
        }
    }
}
