/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.model;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Utils class for {@link com.android.launcher3.LauncherModel}.
 */
public class ModelUtils {

    /**
     * Filters the set of items who are directly or indirectly (via another container) on the
     * specified screen.
     */
    public static <T extends ItemInfo> void filterCurrentWorkspaceItems(int currentScreenId,
            ArrayList<T> allWorkspaceItems,
            ArrayList<T> currentScreenItems,
            ArrayList<T> otherScreenItems) {
        // Purge any null ItemInfos
        Iterator<T> iter = allWorkspaceItems.iterator();
        while (iter.hasNext()) {
            ItemInfo i = iter.next();
            if (i == null) {
                iter.remove();
            }
        }
        // Order the set of items by their containers first, this allows use to walk through the
        // list sequentially, build up a list of containers that are in the specified screen,
        // as well as all items in those containers.
        IntSet itemsOnScreen = new IntSet();
        Collections.sort(allWorkspaceItems,
                (lhs, rhs) -> Integer.compare(lhs.container, rhs.container));
        for (T info : allWorkspaceItems) {
            if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                if (info.screenId == currentScreenId) {
                    currentScreenItems.add(info);
                    itemsOnScreen.add(info.id);
                } else {
                    otherScreenItems.add(info);
                }
            } else if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                currentScreenItems.add(info);
                itemsOnScreen.add(info.id);
            } else {
                if (itemsOnScreen.contains(info.container)) {
                    currentScreenItems.add(info);
                    itemsOnScreen.add(info.id);
                } else {
                    otherScreenItems.add(info);
                }
            }
        }
    }

    /**
     * Sorts the set of items by hotseat, workspace (spatially from top to bottom, left to right)
     */
    public static void sortWorkspaceItemsSpatially(InvariantDeviceProfile profile,
            ArrayList<ItemInfo> workspaceItems) {
        final int screenCols = profile.numColumns;
        final int screenCellCount = profile.numColumns * profile.numRows;
        Collections.sort(workspaceItems, (lhs, rhs) -> {
            if (lhs.container == rhs.container) {
                // Within containers, order by their spatial position in that container
                switch (lhs.container) {
                    case LauncherSettings.Favorites.CONTAINER_DESKTOP: {
                        int lr = (lhs.screenId * screenCellCount + lhs.cellY * screenCols
                                + lhs.cellX);
                        int rr = (rhs.screenId * screenCellCount + +rhs.cellY * screenCols
                                + rhs.cellX);
                        return Integer.compare(lr, rr);
                    }
                    case LauncherSettings.Favorites.CONTAINER_HOTSEAT: {
                        // We currently use the screen id as the rank
                        return Integer.compare(lhs.screenId, rhs.screenId);
                    }
                    default:
                        if (FeatureFlags.IS_STUDIO_BUILD) {
                            throw new RuntimeException(
                                    "Unexpected container type when sorting workspace items.");
                        }
                        return 0;
                }
            } else {
                // Between containers, order by hotseat, desktop
                return Integer.compare(lhs.container, rhs.container);
            }
        });
    }

    /**
     * Iterates though current workspace items and returns available hotseat ranks for prediction.
     */
    public static IntArray getMissingHotseatRanks(List<ItemInfo> items, int len) {
        IntSet seen = new IntSet();
        items.stream().filter(
                info -> info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT)
                .forEach(i -> seen.add(i.screenId));
        IntArray result = new IntArray(len);
        IntStream.range(0, len).filter(i -> !seen.contains(i)).forEach(result::add);
        return result;
    }
}
