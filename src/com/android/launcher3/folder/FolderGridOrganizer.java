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

package com.android.launcher3.folder;

import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.DEFAULT_NUM_ITEMS_IN_PREVIEW;

import android.content.Context;
import android.graphics.Point;
import android.util.Log;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;

import java.util.ArrayList;
import java.util.List;

import app.lawnchair.folder.FolderPreviewConfig;

/**
 * Utility class for managing item positions in a folder based on rank
 */
public class FolderGridOrganizer {

    private final Point mPoint = new Point();
    private final int mMaxCountX;
    private final int mMaxCountY;
    private final int mMaxItemsPerPage;
    private final int mPreviewMaxItems;

    private int mNumItemsInFolder;
    private int mCountX;
    private int mCountY;

    /**
     * Note: must call {@link #setFolderInfo(FolderInfo)} manually for verifier to work.
     */
    public FolderGridOrganizer(int maxCountX, int maxCountY) {
        this(maxCountX, maxCountY, DEFAULT_NUM_ITEMS_IN_PREVIEW);
    }

    /**
     * Creates an organizer with an explicit closed-folder preview item count.
     */
    public FolderGridOrganizer(int maxCountX, int maxCountY, int previewMaxItems) {
        mMaxCountX = maxCountX;
        mMaxCountY = maxCountY;
        mMaxItemsPerPage = mMaxCountX * mMaxCountY;
        mPreviewMaxItems = previewMaxItems;
    }

    /**
     * Creates a FolderGridOrganizer for the given DeviceProfile using the default 2×2 preview.
     */
    public static FolderGridOrganizer createFolderGridOrganizer(DeviceProfile profile) {
        return new FolderGridOrganizer(profile.numFolderColumns, profile.numFolderRows);
    }

    /**
     * Creates a FolderGridOrganizer using the user's folder preview grid preference.
     */
    public static FolderGridOrganizer createFolderGridOrganizer(Context context,
            DeviceProfile profile) {
        return new FolderGridOrganizer(profile.numFolderColumns, profile.numFolderRows,
                FolderPreviewConfig.getActiveItemCount(context));
    }

    /** Active number of icons shown in the closed-folder preview. */
    public int getPreviewMaxItems() {
        return mPreviewMaxItems;
    }

    /**
     * Updates the organizer with the provided folder info
     */
    public FolderGridOrganizer setFolderInfo(FolderInfo info) {
        return setContentSize(info.getContents().size());
    }

    /**
     * Updates the organizer to reflect the content size
     */
    public FolderGridOrganizer setContentSize(int contentSize) {
        if (contentSize != mNumItemsInFolder) {
            calculateGridSize(contentSize);
            mNumItemsInFolder = contentSize;
        }
        return this;
    }

    public int getCountX() {
        return mCountX;
    }

    public int getCountY() {
        return mCountY;
    }

    public int getMaxItemsPerPage() {
        return mMaxItemsPerPage;
    }

    /**
     * Calculates the grid size such that {@param count} items can fit in the grid.
     * The grid size is calculated such that countY <= countX and countX = ceil(sqrt(count)) while
     * maintaining the restrictions of {@link #mMaxCountX} &amp; {@link #mMaxCountY}.
     */
    private void calculateGridSize(int count) {
        boolean done;
        int gridCountX = mCountX;
        int gridCountY = mCountY;

        if (count >= mMaxItemsPerPage) {
            gridCountX = mMaxCountX;
            gridCountY = mMaxCountY;
            done = true;
        } else {
            done = false;
        }

        while (!done) {
            int oldCountX = gridCountX;
            int oldCountY = gridCountY;
            if (gridCountX * gridCountY < count) {
                // Current grid is too small, expand it
                if ((gridCountX <= gridCountY || gridCountY == mMaxCountY)
                        && gridCountX < mMaxCountX) {
                    gridCountX++;
                } else if (gridCountY < mMaxCountY) {
                    gridCountY++;
                }
                if (gridCountY == 0) gridCountY++;
            } else if ((gridCountY - 1) * gridCountX >= count && gridCountY >= gridCountX) {
                gridCountY = Math.max(0, gridCountY - 1);
            } else if ((gridCountX - 1) * gridCountY >= count) {
                gridCountX = Math.max(0, gridCountX - 1);
            }
            done = gridCountX == oldCountX && gridCountY == oldCountY;
        }

        mCountX = gridCountX;
        mCountY = gridCountY;
    }

    /**
     * Updates the item's cellX, cellY and rank corresponding to the provided rank.
     *
     * @return true if there was any change
     */
    public boolean updateRankAndPos(ItemInfo item, int rank) {
        if (rank != item.rank) {
            item.rank = rank;
            return true;
        }
        return false;
    }

    /**
     * Returns the position of the item in the grid
     */
    public Point getPosForRank(int rank) {
        int pagePos = rank % mMaxItemsPerPage;
        if (mCountX == 0) {
            mPoint.x = 0;
            mPoint.y = 0;
        } else {
            mPoint.x = pagePos % mCountX;
            mPoint.y = pagePos / mCountX;
        }
        return mPoint;
    }

    /**
     * Returns the preview items for the provided pageNo using the full list of contents.
     * Contents are expected in reading order (rank / cellY / cellX); the first
     * {@link #mPreviewMaxItems} items on the page are shown.
     */
    public <T, R extends T> ArrayList<R> previewItemsForPage(int page, List<T> contents) {
        ArrayList<R> result = new ArrayList<>();
        int itemsPerPage = Math.max(mCountX * mCountY, 1);
        int start = itemsPerPage * page;
        int end = Math.min(start + itemsPerPage, contents.size());

        for (int i = start; i < end && result.size() < mPreviewMaxItems; i++) {
            result.add((R) contents.get(i));
        }

        if (result.isEmpty()) {
            // Log specifics since we are getting empty result
            Log.d("b/383526431", "previewItemsForPage: "
                    + "mCountX = " + mCountX
                    + ", mCountY = " + mCountY
                    + ", content size = " + contents.size());
        }
        return result;
    }

    /**
     * Returns whether the item with rank is in the default Folder icon preview.
     */
    public boolean isItemInPreview(int rank) {
        return isItemInPreview(0, rank);
    }

    /**
     * @param page The page the item is on (unused; preview always follows reading order).
     * @param rank The rank of the item within the page.
     * @return True iff the icon is among the first {@link #mPreviewMaxItems} in reading order.
     */
    public boolean isItemInPreview(int page, int rank) {
        // Match closed-folder preview to folder contents order (left-to-right, top-to-bottom
        // by rank), not the upper-left corner of the opened folder grid. Otherwise a 4-column
        // folder with a 3×3 preview would skip the 4th icon on each row (e.g. Gemini after
        // YouTube).
        return rank < mPreviewMaxItems;
    }
}
