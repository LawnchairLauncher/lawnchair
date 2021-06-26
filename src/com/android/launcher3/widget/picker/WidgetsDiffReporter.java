/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.util.Log;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.model.WidgetsListSearchHeaderEntry;
import com.android.launcher3.widget.picker.WidgetsListAdapter.WidgetListBaseRowEntryComparator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Do diff on widget's tray list items and call the {@link RecyclerView.Adapter}
 * methods accordingly.
 */
public class WidgetsDiffReporter {
    private static final boolean DEBUG = false;
    private static final String TAG = "WidgetsDiffReporter";

    private final IconCache mIconCache;
    private final RecyclerView.Adapter mListener;

    public WidgetsDiffReporter(IconCache iconCache, RecyclerView.Adapter listener) {
        mIconCache = iconCache;
        mListener = listener;
    }

    /**
     * Notifies the difference between {@code currentEntries} & {@code newEntries} by calling the
     * relevant {@link androidx.recyclerview.widget.RecyclerView.RecyclerViewDataObserver} methods.
     */
    public void process(ArrayList<WidgetsListBaseEntry> currentEntries,
            List<WidgetsListBaseEntry> newEntries,
            WidgetListBaseRowEntryComparator comparator) {
        if (DEBUG) {
            Log.d(TAG, "process oldEntries#=" + currentEntries.size()
                    + " newEntries#=" + newEntries.size());
        }
        // Early exit if either of the list is empty
        if (currentEntries.isEmpty() || newEntries.isEmpty()) {
            // Skip if both list are empty.
            // On rotation, we open the widget tray with empty. Then try to fetch the list again
            // when the animation completes (which still gives empty). And we get the final result
            // when the bind actually completes.
            if (currentEntries.size() != newEntries.size()) {
                currentEntries.clear();
                currentEntries.addAll(newEntries);
                mListener.notifyDataSetChanged();
            }
            return;
        }
        ArrayList<WidgetsListBaseEntry> orgEntries =
                (ArrayList<WidgetsListBaseEntry>) currentEntries.clone();
        Iterator<WidgetsListBaseEntry> orgIter = orgEntries.iterator();
        Iterator<WidgetsListBaseEntry> newIter = newEntries.iterator();

        WidgetsListBaseEntry orgRowEntry = orgIter.next();
        WidgetsListBaseEntry newRowEntry = newIter.next();

        do {
            int diff = compareAppNameAndType(orgRowEntry, newRowEntry, comparator);
            if (DEBUG) {
                Log.d(TAG, String.format("diff=%d orgRowEntry (%s) newRowEntry (%s)",
                        diff, orgRowEntry != null ? orgRowEntry.toString() : null,
                        newRowEntry != null ? newRowEntry.toString() : null));
            }
            int index = -1;
            if (diff < 0) {
                index = currentEntries.indexOf(orgRowEntry);
                mListener.notifyItemRemoved(index);
                if (DEBUG) {
                    Log.d(TAG, String.format("notifyItemRemoved called (%d)%s", index,
                            orgRowEntry.mTitleSectionName));
                }
                currentEntries.remove(index);
                orgRowEntry = orgIter.hasNext() ? orgIter.next() : null;
            } else if (diff > 0) {
                index = orgRowEntry != null ? currentEntries.indexOf(orgRowEntry)
                        : currentEntries.size();
                currentEntries.add(index, newRowEntry);
                if (DEBUG) {
                    Log.d(TAG, String.format("notifyItemInserted called (%d)%s", index,
                            newRowEntry.mTitleSectionName));
                }
                newRowEntry = newIter.hasNext() ? newIter.next() : null;
                mListener.notifyItemInserted(index);

            } else {
                // same app name & type but,
                // did the icon, title, etc, change?
                // or did the header view changed due to user interactions?
                // or did the widget size and desc, span, etc change?
                if (!isSamePackageItemInfo(orgRowEntry.mPkgItem, newRowEntry.mPkgItem)
                        || hasHeaderUpdated(orgRowEntry, newRowEntry)
                        || hasWidgetsListContentChanged(orgRowEntry, newRowEntry)) {
                    index = currentEntries.indexOf(orgRowEntry);
                    currentEntries.set(index, newRowEntry);
                    mListener.notifyItemChanged(index);
                    if (DEBUG) {
                        Log.d(TAG, String.format("notifyItemChanged called (%d)%s", index,
                                newRowEntry.mTitleSectionName));
                    }
                }
                orgRowEntry = orgIter.hasNext() ? orgIter.next() : null;
                newRowEntry = newIter.hasNext() ? newIter.next() : null;
            }
        } while(orgRowEntry != null || newRowEntry != null);
    }

    /**
     * Compares the app name and then entry type for the given {@link WidgetsListBaseEntry}s.
     *
     * @Return 0 if both entries' order is the same. Negative integer if {@code newRowEntry} should
     *         order before {@code orgRowEntry}. Positive integer if {@code orgRowEntry} should
     *         order before {@code newRowEntry}.
     */
    private int compareAppNameAndType(WidgetsListBaseEntry curRow, WidgetsListBaseEntry newRow,
            WidgetListBaseRowEntryComparator comparator) {
        if (curRow == null && newRow == null) {
            throw new IllegalStateException(
                    "Cannot compare PackageItemInfo if both rows are null.");
        }

        if (curRow == null && newRow != null) {
            return 1; // new row needs to be inserted
        } else if (curRow != null && newRow == null) {
            return -1; // old row needs to be deleted
        }
        int diff = comparator.compare(curRow, newRow);
        if (diff == 0) {
            return newRow.getRank() - curRow.getRank();
        }
        return diff;
    }

    /**
     * Returns {@code true} if both {@code curRow} & {@code newRow} are
     * {@link WidgetsListContentEntry}s with a different list or arrangement of widgets.
     */
    private boolean hasWidgetsListContentChanged(WidgetsListBaseEntry curRow,
            WidgetsListBaseEntry newRow) {
        if (!(curRow instanceof WidgetsListContentEntry)
                || !(newRow instanceof WidgetsListContentEntry)) {
            return false;
        }
        return !curRow.equals(newRow);
    }

    /**
     * Returns {@code true} if {@code newRow} is {@link WidgetsListHeaderEntry} and its content has
     * been changed due to user interactions.
     */
    private boolean hasHeaderUpdated(WidgetsListBaseEntry curRow, WidgetsListBaseEntry newRow) {
        if (newRow instanceof WidgetsListHeaderEntry && curRow instanceof WidgetsListHeaderEntry) {
            return !curRow.equals(newRow);
        }
        if (newRow instanceof WidgetsListSearchHeaderEntry
                && curRow instanceof WidgetsListSearchHeaderEntry) {
            // Always refresh search header entries to reset rounded corners in their view holder.
            return true;
        }
        return false;
    }

    private boolean isSamePackageItemInfo(PackageItemInfo curInfo, PackageItemInfo newInfo) {
        return curInfo.bitmap.icon.equals(newInfo.bitmap.icon)
                && !mIconCache.isDefaultIcon(curInfo.bitmap, curInfo.user);
    }
}
