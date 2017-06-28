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

package com.android.launcher3.popup;

import android.content.pm.ShortcutInfo;
import android.support.test.runner.AndroidJUnit4;

import com.android.launcher3.shortcuts.ShortcutInfoCompat;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.launcher3.popup.PopupPopulator.MAX_SHORTCUTS;
import static com.android.launcher3.popup.PopupPopulator.NUM_DYNAMIC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the sorting and filtering of shortcuts in {@link PopupPopulator}.
 */
@RunWith(AndroidJUnit4.class)
public class PopupPopulatorTest {

    @Test
    public void testSortAndFilterShortcuts() {
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(3, 0), 3, 0);
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(0, 3), 0, 3);
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(5, 0), MAX_SHORTCUTS, 0);
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(0, 5), 0, MAX_SHORTCUTS);
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(3, 3),
                MAX_SHORTCUTS - NUM_DYNAMIC, NUM_DYNAMIC);
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(5, 5),
                MAX_SHORTCUTS - NUM_DYNAMIC, NUM_DYNAMIC);
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(5, 1), MAX_SHORTCUTS - 1, 1);
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(1, 5), 1, MAX_SHORTCUTS - 1);
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(5, 3),
                MAX_SHORTCUTS - NUM_DYNAMIC, NUM_DYNAMIC);
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(3, 5),
                MAX_SHORTCUTS - NUM_DYNAMIC, NUM_DYNAMIC);
    }

    @Test
    public void testDeDupeShortcutId() {
        // Successfully remove one of the shortcuts
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(3, 0), 2, 0, generateId(true, 1));
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(0, 3), 0, 2, generateId(false, 1));
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(2, 2), 2, 1, generateId(false, 1));
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(2, 2), 1, 2, generateId(true, 1));
        // Successfully keep all shortcuts when id doesn't exist
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(3, 0), 3, 0, generateId(false, 1));
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(3, 0), 3, 0, generateId(true, 4));
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(2, 2), 2, 2, generateId(false, 4));
        filterShortcutsAndAssertNumStaticAndDynamic(createShortcutsList(2, 2), 2, 2, generateId(true, 4));
    }

    private String generateId(boolean isStatic, int rank) {
        return (isStatic ? "static" : "dynamic") + rank;
    }

    private void filterShortcutsAndAssertNumStaticAndDynamic(
            List<ShortcutInfoCompat> shortcuts, int expectedStatic, int expectedDynamic) {
        filterShortcutsAndAssertNumStaticAndDynamic(shortcuts, expectedStatic, expectedDynamic, null);
    }

    private void filterShortcutsAndAssertNumStaticAndDynamic(List<ShortcutInfoCompat> shortcuts,
            int expectedStatic, int expectedDynamic, String shortcutIdToRemove) {
        Collections.shuffle(shortcuts);
        List<ShortcutInfoCompat> filteredShortcuts = PopupPopulator.sortAndFilterShortcuts(
                shortcuts, shortcutIdToRemove);
        assertIsSorted(filteredShortcuts);

        int numStatic = 0;
        int numDynamic = 0;
        for (ShortcutInfoCompat shortcut : filteredShortcuts) {
            if (shortcut.isDeclaredInManifest()) {
                numStatic++;
            }
            if (shortcut.isDynamic()) {
                numDynamic++;
            }
        }
        assertEquals(expectedStatic, numStatic);
        assertEquals(expectedDynamic, numDynamic);
    }

    private void assertIsSorted(List<ShortcutInfoCompat> shortcuts) {
        int lastStaticRank = -1;
        int lastDynamicRank = -1;
        boolean hasSeenDynamic = false;
        for (ShortcutInfoCompat shortcut : shortcuts) {
            int rank = shortcut.getRank();
            if (shortcut.isDeclaredInManifest()) {
                assertFalse("Static shortcuts should come before all dynamic shortcuts.",
                        hasSeenDynamic);
                assertTrue(rank > lastStaticRank);
                lastStaticRank = rank;
            }
            if (shortcut.isDynamic()) {
                hasSeenDynamic = true;
                assertTrue(rank > lastDynamicRank);
                lastDynamicRank = rank;
            }
        }
    }

    private List<ShortcutInfoCompat> createShortcutsList(int numStatic, int numDynamic) {
        List<ShortcutInfoCompat> shortcuts = new ArrayList<>();
        for (int i = 0; i < numStatic; i++) {
            shortcuts.add(new Shortcut(true, i));
        }
        for (int i = 0; i < numDynamic; i++) {
            shortcuts.add(new Shortcut(false, i));
        }
        return shortcuts;
    }

    private class Shortcut extends ShortcutInfoCompat {
        private boolean mIsStatic;
        private int mRank;
        private String mId;

        public Shortcut(ShortcutInfo shortcutInfo) {
            super(shortcutInfo);
        }

        public Shortcut(boolean isStatic, int rank) {
            this(null);
            mIsStatic = isStatic;
            mRank = rank;
            mId = generateId(isStatic, rank);
        }

        @Override
        public boolean isDeclaredInManifest() {
            return mIsStatic;
        }

        @Override
        public boolean isDynamic() {
            return !mIsStatic;
        }

        @Override
        public int getRank() {
            return mRank;
        }

        @Override
        public String getId() {
            return mId;
        }
    }
}