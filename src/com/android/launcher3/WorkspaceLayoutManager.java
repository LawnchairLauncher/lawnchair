/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.IntSet;

public interface WorkspaceLayoutManager {

    String TAG = "Launcher.Workspace";

    // The screen id used for the empty screen always present at the end.
    int EXTRA_EMPTY_SCREEN_ID = -201;
    // The screen id used for the second empty screen always present at the end for two panel home.
    int EXTRA_EMPTY_SCREEN_SECOND_ID = -200;
    // The screen ids used for the empty screens at the end of the workspaces.
    IntSet EXTRA_EMPTY_SCREEN_IDS =
            IntSet.wrap(EXTRA_EMPTY_SCREEN_ID, EXTRA_EMPTY_SCREEN_SECOND_ID);

    // The is the first screen. It is always present, even if its empty.
    int FIRST_SCREEN_ID = 0;
    // This is the second page. On two panel home it is always present, even if its empty.
    int SECOND_SCREEN_ID = 1;

    /**
     * At bind time, we use the rank (screenId) to compute x and y for hotseat items.
     * See {@link #addInScreen}.
     */
    default void addInScreenFromBind(View child, ItemInfo info) {
        CellPos presenterPos = getCellPosMapper().mapModelToPresenter(info);
        int x = presenterPos.cellX;
        int y = presenterPos.cellY;
        if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT
                || info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            int screenId = presenterPos.screenId;
            x = getHotseat().getCellXFromOrder(screenId);
            y = getHotseat().getCellYFromOrder(screenId);
            // TODO(b/335141365): Remove this log after the bug is fixed.
            Log.d(TAG, "addInScreenFromBind: hotseat inflation with x = " + x
                    + " and y = " + y);
        }
        addInScreen(child, info.container, presenterPos.screenId, x, y, info.spanX, info.spanY);
    }

    /**
     * Adds the specified child in the specified screen based on the {@param info}
     * See {@link #addInScreen(View, int, int, int, int, int, int)}.
     */
    default void addInScreen(View child, ItemInfo info) {
        CellPos presenterPos = getCellPosMapper().mapModelToPresenter(info);
        addInScreen(child, info.container,
                presenterPos.screenId, presenterPos.cellX, presenterPos.cellY,
                info.spanX, info.spanY);
    }

    /**
     * Adds the specified child in the specified screen. The position and dimension of
     * the child are defined by x, y, spanX and spanY.
     *
     * @param child The child to add in one of the workspace's screens.
     * @param screenId The screen in which to add the child.
     * @param x The X position of the child in the screen's grid.
     * @param y The Y position of the child in the screen's grid.
     * @param spanX The number of cells spanned horizontally by the child.
     * @param spanY The number of cells spanned vertically by the child.
     */
    default void addInScreen(View child, int container, int screenId, int x, int y,
            int spanX, int spanY) {
        if (container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            if (getScreenWithId(screenId) == null) {
                Log.e(TAG, "Skipping child, screenId " + screenId + " not found");
                // DEBUGGING - Print out the stack trace to see where we are adding from
                new Throwable().printStackTrace();
                return;
            }
        }
        if (EXTRA_EMPTY_SCREEN_IDS.contains(screenId)) {
            // This should never happen
            throw new RuntimeException("Screen id should not be extra empty screen: " + screenId);
        }

        final CellLayout layout;
        if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT
                || container == LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            layout = getHotseat();

            // Hide folder title in the hotseat
            if (child instanceof FolderIcon) {
                ((FolderIcon) child).setTextVisible(false);
            }
        } else {
            // Show folder title if not in the hotseat
            if (child instanceof FolderIcon) {
                ((FolderIcon) child).setTextVisible(true);
            }
            layout = getScreenWithId(screenId);
        }

        ViewGroup.LayoutParams genericLp = child.getLayoutParams();
        CellLayoutLayoutParams lp;
        if (genericLp == null || !(genericLp instanceof CellLayoutLayoutParams)) {
            lp = new CellLayoutLayoutParams(x, y, spanX, spanY);
        } else {
            lp = (CellLayoutLayoutParams) genericLp;
            lp.setCellX(x);
            lp.setCellY(y);
            lp.cellHSpan = spanX;
            lp.cellVSpan = spanY;
        }

        if (spanX < 0 && spanY < 0) {
            lp.isLockedToGrid = false;
        }

        // Get the canonical child id to uniquely represent this view in this screen
        ItemInfo info = (ItemInfo) child.getTag();
        int childId = info.getViewId();

        boolean markCellsAsOccupied = !(child instanceof Folder);
        if (!layout.addViewToCellLayout(child, -1, childId, lp, markCellsAsOccupied)) {
            // TODO: This branch occurs when the workspace is adding views
            // outside of the defined grid
            // maybe we should be deleting these items from the LauncherModel?
            Log.e(TAG, "Failed to add to item at (" + lp.getCellX() + "," + lp.getCellY()
                    + ") to CellLayout");
        }

        child.setHapticFeedbackEnabled(false);
        child.setOnLongClickListener(getWorkspaceChildOnLongClickListener());
        if (child instanceof DropTarget) {
            onAddDropTarget((DropTarget) child);
        }
    }

    default View.OnLongClickListener getWorkspaceChildOnLongClickListener() {
        return ItemLongClickListener.INSTANCE_WORKSPACE;
    }

    /**
     * Returns the mapper for converting between model and presenter
     */
    CellPosMapper getCellPosMapper();

    Hotseat getHotseat();

    CellLayout getScreenWithId(int screenId);

    default void onAddDropTarget(DropTarget target) { }
}
