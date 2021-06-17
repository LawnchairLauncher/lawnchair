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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGETSTRAY_APP_EXPANDED;

import android.content.Context;
import android.os.Process;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.TableRow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.util.LabelComparator;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.model.WidgetsListSearchHeaderEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private static final int VIEW_TYPE_WIDGETS_LIST = R.id.view_type_widgets_list;
    private static final int VIEW_TYPE_WIDGETS_HEADER = R.id.view_type_widgets_header;
    private static final int VIEW_TYPE_WIDGETS_SEARCH_HEADER = R.id.view_type_widgets_search_header;

    private final Launcher mLauncher;
    private final WidgetsDiffReporter mDiffReporter;
    private final SparseArray<ViewHolderBinder> mViewHolderBinders = new SparseArray<>();
    private final WidgetsListTableViewHolderBinder mWidgetsListTableViewHolderBinder;
    private final WidgetListBaseRowEntryComparator mRowComparator =
            new WidgetListBaseRowEntryComparator();

    private List<WidgetsListBaseEntry> mAllEntries = new ArrayList<>();
    private ArrayList<WidgetsListBaseEntry> mVisibleEntries = new ArrayList<>();
    @Nullable private PackageUserKey mWidgetsContentVisiblePackageUserKey = null;

    private Predicate<WidgetsListBaseEntry> mHeaderAndSelectedContentFilter = entry ->
            entry instanceof WidgetsListHeaderEntry
                    || entry instanceof WidgetsListSearchHeaderEntry
                    || new PackageUserKey(entry.mPkgItem.packageName, entry.mPkgItem.user)
                            .equals(mWidgetsContentVisiblePackageUserKey);
    @Nullable private Predicate<WidgetsListBaseEntry> mFilter = null;
    @Nullable private RecyclerView mRecyclerView;

    public WidgetsListAdapter(Context context, LayoutInflater layoutInflater,
            WidgetPreviewLoader widgetPreviewLoader, IconCache iconCache,
            OnClickListener iconClickListener, OnLongClickListener iconLongClickListener) {
        mLauncher = Launcher.getLauncher(context);
        mDiffReporter = new WidgetsDiffReporter(iconCache, this);
        WidgetsListDrawableFactory listDrawableFactory = new WidgetsListDrawableFactory(context);
        mWidgetsListTableViewHolderBinder = new WidgetsListTableViewHolderBinder(context,
                layoutInflater, iconClickListener, iconLongClickListener,
                widgetPreviewLoader, listDrawableFactory, /* listAdapter= */ this);
        mViewHolderBinders.put(VIEW_TYPE_WIDGETS_LIST, mWidgetsListTableViewHolderBinder);
        mViewHolderBinders.put(
                VIEW_TYPE_WIDGETS_HEADER,
                new WidgetsListHeaderViewHolderBinder(
                        layoutInflater,
                        /* onHeaderClickListener= */ this,
                        listDrawableFactory,
                        /* listAdapter= */ this));
        mViewHolderBinders.put(
                VIEW_TYPE_WIDGETS_SEARCH_HEADER,
                new WidgetsListSearchHeaderViewHolderBinder(
                        layoutInflater,
                        /* onHeaderClickListener= */ this,
                        listDrawableFactory,
                        /* listAdapter= */ this));
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        mRecyclerView = null;
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

    /** Returns all items that will be drawn in a recycler view. */
    public List<WidgetsListBaseEntry> getItems() {
        return mVisibleEntries;
    }

    /** Gets the section name for {@link com.android.launcher3.views.RecyclerViewFastScroller}. */
    public String getSectionName(int pos) {
        return mVisibleEntries.get(pos).mTitleSectionName;
    }

    /** Updates the widget list based on {@code tempEntries}. */
    public void setWidgets(List<WidgetsListBaseEntry> tempEntries) {
        mAllEntries = tempEntries.stream().sorted(mRowComparator)
                .collect(Collectors.toList());
        if (shouldClearVisibleEntries()) {
            mVisibleEntries.clear();
        }
        updateVisibleEntries();
    }

    /** Updates the widget list based on {@code searchResults}. */
    public void setWidgetsOnSearch(List<WidgetsListBaseEntry> searchResults) {
        // Forget the expanded package every time widget list is refreshed in search mode.
        mWidgetsContentVisiblePackageUserKey = null;
        setWidgets(searchResults);
    }

    private void updateVisibleEntries() {
        mAllEntries.forEach(entry -> {
            if (entry instanceof WidgetsListHeaderEntry) {
                ((WidgetsListHeaderEntry) entry).setIsWidgetListShown(
                        isHeaderForVisibleContent(entry));
            } else if (entry instanceof WidgetsListSearchHeaderEntry) {
                ((WidgetsListSearchHeaderEntry) entry).setIsWidgetListShown(
                        isHeaderForVisibleContent(entry));
            }
        });
        List<WidgetsListBaseEntry> newVisibleEntries = mAllEntries.stream()
                .filter(entry -> (mFilter == null || mFilter.test(entry))
                        && mHeaderAndSelectedContentFilter.test(entry))
                .collect(Collectors.toList());
        mDiffReporter.process(mVisibleEntries, newVisibleEntries, mRowComparator);
    }

    /** Returns whether {@code entry} matches {@link #mWidgetsContentVisiblePackageUserKey}. */
    private boolean isHeaderForVisibleContent(WidgetsListBaseEntry entry) {
        return isHeaderForPackageUserKey(entry, mWidgetsContentVisiblePackageUserKey);
    }

    /** Returns whether {@code entry} matches {@code key}. */
    private boolean isHeaderForPackageUserKey(WidgetsListBaseEntry entry, PackageUserKey key) {
        return (entry instanceof WidgetsListHeaderEntry
                || entry instanceof WidgetsListSearchHeaderEntry)
                && new PackageUserKey(entry.mPkgItem.packageName, entry.mPkgItem.user).equals(key);
    }

    /**
     * Resets any expanded widget header.
     */
    public void resetExpandedHeader() {
        if (mWidgetsContentVisiblePackageUserKey != null) {
            mWidgetsContentVisiblePackageUserKey = null;
            updateVisibleEntries();
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int pos) {
        ViewHolderBinder viewHolderBinder = mViewHolderBinders.get(getItemViewType(pos));
        viewHolderBinder.bindViewHolder(holder, mVisibleEntries.get(pos), pos);
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
        return Arrays.hashCode(new Object[]{
                mVisibleEntries.get(pos).mPkgItem.hashCode(),
                getItemViewType(pos)});
    }

    @Override
    public int getItemViewType(int pos) {
        WidgetsListBaseEntry entry = mVisibleEntries.get(pos);
        if (entry instanceof WidgetsListContentEntry) {
            return VIEW_TYPE_WIDGETS_LIST;
        } else if (entry instanceof WidgetsListHeaderEntry) {
            return VIEW_TYPE_WIDGETS_HEADER;
        } else if (entry instanceof WidgetsListSearchHeaderEntry) {
            return VIEW_TYPE_WIDGETS_SEARCH_HEADER;
        }
        throw new UnsupportedOperationException("ViewHolderBinder not found for " + entry);
    }

    @Override
    public void onHeaderClicked(boolean showWidgets, PackageUserKey packageUserKey) {
        // Ignore invalid clicks, such as collapsing a package that isn't currently expanded.
        if (!showWidgets && !packageUserKey.equals(mWidgetsContentVisiblePackageUserKey)) return;

        if (showWidgets) {
            mWidgetsContentVisiblePackageUserKey = packageUserKey;
            mLauncher.getStatsLogManager().logger().log(LAUNCHER_WIDGETSTRAY_APP_EXPANDED);
        } else {
            mWidgetsContentVisiblePackageUserKey = null;
        }

        // Get the current top of the header with the matching key before adjusting the visible
        // entries.
        OptionalInt topForPackageUserKey =
                getOffsetForPosition(getPositionForPackageUserKey(packageUserKey));

        updateVisibleEntries();

        // Get the position for the clicked header after adjusting the visible entries. The
        // position may have changed if another header had previously been expanded.
        OptionalInt positionForPackageUserKey = getPositionForPackageUserKey(packageUserKey);
        scrollToPositionAndMaintainOffset(positionForPackageUserKey, topForPackageUserKey);
    }

    /** Returns the position of the currently expanded header, or empty if it's not present. */
    public OptionalInt getSelectedHeaderPosition() {
        if (mWidgetsContentVisiblePackageUserKey == null) return OptionalInt.empty();
        return getPositionForPackageUserKey(mWidgetsContentVisiblePackageUserKey);
    }

    /**
     * Returns the position of {@code key} in {@link #mVisibleEntries}, or  empty if it's not
     * present.
     */
    private OptionalInt getPositionForPackageUserKey(PackageUserKey key) {
        return IntStream.range(0, mVisibleEntries.size())
                .filter(index -> isHeaderForPackageUserKey(mVisibleEntries.get(index), key))
                .findFirst();
    }

    /**
     * Returns the top of {@code positionOptional} in the recycler view, or empty if its view
     * can't be found for any reason, including the position not being currently visible. The
     * returned value does not include the top padding of the recycler view.
     */
    private OptionalInt getOffsetForPosition(OptionalInt positionOptional) {
        if (!positionOptional.isPresent() || mRecyclerView == null) return OptionalInt.empty();

        RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        if (layoutManager == null) return OptionalInt.empty();

        View view = layoutManager.findViewByPosition(positionOptional.getAsInt());
        if (view == null) return OptionalInt.empty();

        return OptionalInt.of(layoutManager.getDecoratedTop(view));
    }

    /**
     * Scrolls to the selected header position with the provided offset. LinearLayoutManager
     * scrolls the minimum distance necessary, so this will keep the selected header in place during
     * clicks, without interrupting the animation.
     *
     * @param positionOptional The position too scroll to. No scrolling will be done if empty.
     * @param offsetOptional The offset from the top to maintain. If empty, then the list will
     *                       scroll to the top of the position.
     */
    private void scrollToPositionAndMaintainOffset(
            OptionalInt positionOptional,
            OptionalInt offsetOptional) {
        if (!positionOptional.isPresent() || mRecyclerView == null) return;
        int position = positionOptional.getAsInt();

        LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
        if (layoutManager == null) return;

        if (position == mVisibleEntries.size() - 2
                && mVisibleEntries.get(mVisibleEntries.size() - 1)
                instanceof WidgetsListContentEntry) {
            // If the selected header is in the last position and its content is showing, then
            // scroll to the final position so the last list of widgets will show.
            layoutManager.scrollToPosition(mVisibleEntries.size() - 1);
            return;
        }

        // Scroll to the header view's current offset, accounting for the recycler view's padding.
        // If the header view couldn't be found, then it will appear at the top of the list.
        layoutManager.scrollToPositionWithOffset(
                position,
                offsetOptional.orElse(0) - mRecyclerView.getPaddingTop());
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

    /**
     * Returns {@code true} if there is a change in {@link #mAllEntries} that results in an
     * invalidation of {@link #mVisibleEntries}. e.g. there is change in the device language.
     */
    private boolean shouldClearVisibleEntries() {
        Map<PackageUserKey, PackageItemInfo> packagesInfo =
                mAllEntries.stream()
                        .filter(entry -> entry instanceof WidgetsListHeaderEntry)
                        .map(entry -> entry.mPkgItem)
                        .collect(Collectors.toMap(
                                entry -> new PackageUserKey(entry.packageName, entry.user),
                                entry -> entry));
        for (WidgetsListBaseEntry visibleEntry: mVisibleEntries) {
            PackageUserKey key = new PackageUserKey(visibleEntry.mPkgItem.packageName,
                    visibleEntry.mPkgItem.user);
            PackageItemInfo packageItemInfo = packagesInfo.get(key);
            if (packageItemInfo != null
                    && !visibleEntry.mPkgItem.title.equals(packageItemInfo.title)) {
                return true;
            }
        }
        return false;
    }

    /** Comparator for sorting WidgetListRowEntry based on package title. */
    public static class WidgetListBaseRowEntryComparator implements
            Comparator<WidgetsListBaseEntry> {

        private final LabelComparator mComparator = new LabelComparator();

        @Override
        public int compare(WidgetsListBaseEntry a, WidgetsListBaseEntry b) {
            int i = mComparator.compare(a.mPkgItem.title.toString(), b.mPkgItem.title.toString());
            if (i != 0) {
                return i;
            }
            // Prioritize entries from current user over other users if the entries are same.
            if (a.mPkgItem.user.equals(b.mPkgItem.user)) return 0;
            if (a.mPkgItem.user.equals(Process.myUserHandle())) return -1;
            return 1;
        }
    }
}
