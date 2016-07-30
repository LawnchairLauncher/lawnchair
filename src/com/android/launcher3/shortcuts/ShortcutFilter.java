/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.shortcuts;

import android.support.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts and filters shortcuts.
 */
public class ShortcutFilter {

    public static final int MAX_SHORTCUTS = 4;
    @VisibleForTesting static final int NUM_DYNAMIC = 2;

    /**
     * Sorts shortcuts in rank order, with manifest shortcuts coming before dynamic shortcuts.
     */
    private static final Comparator<ShortcutInfoCompat> RANK_COMPARATOR
            = new Comparator<ShortcutInfoCompat>() {
        @Override
        public int compare(ShortcutInfoCompat a, ShortcutInfoCompat b) {
            if (a.isDeclaredInManifest() && !b.isDeclaredInManifest()) {
                return -1;
            }
            if (!a.isDeclaredInManifest() && b.isDeclaredInManifest()) {
                return 1;
            }
            return Integer.compare(a.getRank(), b.getRank());
        }
    };

    /**
     * Filters the shortcuts so that only MAX_SHORTCUTS or fewer shortcuts are retained.
     * We want the filter to include both static and dynamic shortcuts, so we always
     * include NUM_DYNAMIC dynamic shortcuts, if at least that many are present.
     *
     * @return a subset of shortcuts, in sorted order, with size <= MAX_SHORTCUTS.
     */
    public static List<ShortcutInfoCompat> sortAndFilterShortcuts(
            List<ShortcutInfoCompat> shortcuts) {
        Collections.sort(shortcuts, RANK_COMPARATOR);
        if (shortcuts.size() <= MAX_SHORTCUTS) {
            return shortcuts;
        }

        // The list of shortcuts is now sorted with static shortcuts followed by dynamic
        // shortcuts. We want to preserve this order, but only keep MAX_SHORTCUTS.
        List<ShortcutInfoCompat> filteredShortcuts = new ArrayList<>(MAX_SHORTCUTS);
        int numDynamic = 0;
        int size = shortcuts.size();
        for (int i = 0; i < size; i++) {
            ShortcutInfoCompat shortcut = shortcuts.get(i);
            int filteredSize = filteredShortcuts.size();
            if (filteredSize < MAX_SHORTCUTS) {
                // Always add the first MAX_SHORTCUTS to the filtered list.
                filteredShortcuts.add(shortcut);
                if (shortcut.isDynamic()) {
                    numDynamic++;
                }
                continue;
            }
            // At this point, we have MAX_SHORTCUTS already, but they may all be static.
            // If there are dynamic shortcuts, remove static shortcuts to add them.
            if (shortcut.isDynamic() && numDynamic < NUM_DYNAMIC) {
                numDynamic++;
                int lastStaticIndex = filteredSize - numDynamic;
                filteredShortcuts.remove(lastStaticIndex);
                filteredShortcuts.add(shortcut);
            }
        }
        return filteredShortcuts;
    }
}
