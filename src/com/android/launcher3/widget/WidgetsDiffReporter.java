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

package com.android.launcher3.widget;

import android.util.Log;

import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.widget.WidgetsListAdapter.WidgetListRowEntryComparator;

import java.util.ArrayList;
import java.util.Iterator;

import androidx.recyclerview.widget.RecyclerView;

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

    public void process(ArrayList<WidgetListRowEntry> currentEntries,
            ArrayList<WidgetListRowEntry> newEntries, WidgetListRowEntryComparator comparator) {
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
        ArrayList<WidgetListRowEntry> orgEntries =
                (ArrayList<WidgetListRowEntry>) currentEntries.clone();
        Iterator<WidgetListRowEntry> orgIter = orgEntries.iterator();
        Iterator<WidgetListRowEntry> newIter = newEntries.iterator();

        WidgetListRowEntry orgRowEntry = orgIter.next();
        WidgetListRowEntry newRowEntry = newIter.next();

        do {
            int diff = comparePackageName(orgRowEntry, newRowEntry, comparator);
            if (DEBUG) {
                Log.d(TAG, String.format("diff=%d orgRowEntry (%s) newRowEntry (%s)",
                        diff, orgRowEntry != null? orgRowEntry.toString() : null,
                        newRowEntry != null? newRowEntry.toString() : null));
            }
            int index = -1;
            if (diff < 0) {
                index = currentEntries.indexOf(orgRowEntry);
                mListener.notifyItemRemoved(index);
                if (DEBUG) {
                    Log.d(TAG, String.format("notifyItemRemoved called (%d)%s", index,
                            orgRowEntry.titleSectionName));
                }
                currentEntries.remove(index);
                orgRowEntry = orgIter.hasNext() ? orgIter.next() : null;
            } else if (diff > 0) {
                index = orgRowEntry != null? currentEntries.indexOf(orgRowEntry):
                        currentEntries.size();
                currentEntries.add(index, newRowEntry);
                if (DEBUG) {
                    Log.d(TAG, String.format("notifyItemInserted called (%d)%s", index,
                            newRowEntry.titleSectionName));
                }
                newRowEntry = newIter.hasNext() ? newIter.next() : null;
                mListener.notifyItemInserted(index);

            } else {
                // same package name but,
                // did the icon, title, etc, change?
                // or did the widget size and desc, span, etc change?
                if (!isSamePackageItemInfo(orgRowEntry.pkgItem, newRowEntry.pkgItem) ||
                        !orgRowEntry.widgets.equals(newRowEntry.widgets)) {
                    index = currentEntries.indexOf(orgRowEntry);
                    currentEntries.set(index, newRowEntry);
                    mListener.notifyItemChanged(index);
                    if (DEBUG) {
                        Log.d(TAG, String.format("notifyItemChanged called (%d)%s", index,
                                newRowEntry.titleSectionName));
                    }
                }
                orgRowEntry = orgIter.hasNext() ? orgIter.next() : null;
                newRowEntry = newIter.hasNext() ? newIter.next() : null;
            }
        } while(orgRowEntry != null || newRowEntry != null);
    }

    /**
     * Compare package name using the same comparator as in {@link WidgetsListAdapter}.
     * Also handle null row pointers.
     */
    private int comparePackageName(WidgetListRowEntry curRow, WidgetListRowEntry newRow,
            WidgetListRowEntryComparator comparator) {
        if (curRow == null && newRow == null) {
            throw new IllegalStateException("Cannot compare PackageItemInfo if both rows are null.");
        }

        if (curRow == null && newRow != null) {
            return 1; // new row needs to be inserted
        } else if (curRow != null && newRow == null) {
            return -1; // old row needs to be deleted
        }
        return comparator.compare(curRow, newRow);
    }

    private boolean isSamePackageItemInfo(PackageItemInfo curInfo, PackageItemInfo newInfo) {
        return curInfo.iconBitmap.equals(newInfo.iconBitmap) &&
                !mIconCache.isDefaultIcon(curInfo.iconBitmap, curInfo.user);
    }
}
