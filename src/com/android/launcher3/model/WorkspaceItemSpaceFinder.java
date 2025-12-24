/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID;

import android.util.SparseArray;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemCoordinates;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.IntSet;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * Utility class to help find space for new workspace items
 */
public class WorkspaceItemSpaceFinder {

    private final BgDataModel mDataModel;
    private final InvariantDeviceProfile mIDP;
    private final LauncherModel mModel;

    @Inject
    public WorkspaceItemSpaceFinder(
            BgDataModel dataModel, InvariantDeviceProfile idp, LauncherModel model) {
        mDataModel = dataModel;
        mIDP = idp;
        mModel = model;
    }

    /**
     * Find a position on the screen for the given size or adds a new screen.
     *
     * @return screenId and the coordinates for the item wrapped in
     * {@link WorkspaceItemCoordinates}.
     */
    public WorkspaceItemCoordinates findSpaceForItem(ArrayList<ItemInfo> addItemsFinal, int spanX,
            int spanY, IntSet excludedScreens) {
        SparseArray<ArrayList<ItemInfo>> screenItems = new SparseArray<>();
        screenItems.put(FIRST_SCREEN_ID, new ArrayList<>());

        // Use sBgItemsIdMap as all the items are already loaded.
        synchronized (mDataModel) {
            for (ItemInfo info : mDataModel.itemsIdMap) {
                if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    ArrayList<ItemInfo> items = screenItems.get(info.screenId);
                    if (items == null) {
                        items = new ArrayList<>();
                        screenItems.put(info.screenId, items);
                    }
                    items.add(info);
                }
            }
        }

        // Add items that are due to be added to the database from AddWorkspaceItemsTask#execute.
        for (ItemInfo info : addItemsFinal) {
            if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                ArrayList<ItemInfo> items = screenItems.get(info.screenId);
                if (items == null) {
                    items = new ArrayList<>();
                    screenItems.put(info.screenId, items);
                }
                items.add(info);
            }
        }

        // Find appropriate space for the item.
        int screenId = 0;
        int[] coordinates = new int[2];
        boolean found = false;

        for (int screen = 0; screen < screenItems.size(); screen++) {
            screenId = screenItems.keyAt(screen);
            if (!excludedScreens.contains(screenId) && findNextAvailableIconSpaceInScreen(
                    screenItems.get(screenId), coordinates, spanX, spanY)) {
                // We found a space for it
                found = true;
                break;
            }
        }

        if (!found) {
            // Still no position found. Add a new screen to the end.
            screenId = mModel.getModelDbController().getNewScreenId();

            // If we still can't find an empty space, then God help us all!!!
            if (!findNextAvailableIconSpaceInScreen(
                    screenItems.get(screenId), coordinates, spanX, spanY)) {
                throw new RuntimeException("Can't find space to add the item");
            }
        }
        return new WorkspaceItemCoordinates(screenId, coordinates[0], coordinates[1]);
    }

    private boolean findNextAvailableIconSpaceInScreen(
            ArrayList<ItemInfo> occupiedPos, int[] xy, int spanX, int spanY) {
        GridOccupancy occupied = new GridOccupancy(mIDP.numColumns, mIDP.numRows);
        if (occupiedPos != null) {
            for (ItemInfo r : occupiedPos) {
                occupied.markCells(r, true);
            }
        }
        return occupied.findVacantCell(xy, spanX, spanY);
    }
}
