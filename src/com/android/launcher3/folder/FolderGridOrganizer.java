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

import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;

import android.graphics.Point;

import com.android.launcher3.FolderInfo;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for managing item positions in a folder based on rank
 */
public class FolderGridOrganizer {

    private final Point mPoint = new Point();
    private final int mMaxCountX;
    private final int mMaxCountY;
    private final int mMaxItemsPerPage;

    private int mNumItemsInFolder;
    private int mCountX;
    private int mCountY;
    private boolean mDisplayingUpperLeftQuadrant = false;

    /**
     * Note: must call {@link #setFolderInfo(FolderInfo)} manually for verifier to work.
     */
    public FolderGridOrganizer(InvariantDeviceProfile profile) {
        mMaxCountX = profile.numFolderColumns;
        mMaxCountY = profile.numFolderRows;
        mMaxItemsPerPage = mMaxCountX * mMaxCountY;
    }

    /**
     * Updates the organizer with the provided folder info
     */
    public FolderGridOrganizer setFolderInfo(FolderInfo info) {
        return setContentSize(info.contents.size());
    }

    /**
     * Updates the organizer to reflect the content size
     */
    public FolderGridOrganizer setContentSize(int contentSize) {
        if (contentSize != mNumItemsInFolder) {
            calculateGridSize(contentSize);

            mDisplayingUpperLeftQuadrant = contentSize > MAX_NUM_ITEMS_IN_PREVIEW;
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
     * @return true if there was any change
     */
    public boolean updateRankAndPos(ItemInfo item, int rank) {
        Point pos = getPosForRank(rank);
        if (!pos.equals(item.cellX, item.cellY) || rank != item.rank) {
            item.rank = rank;
            item.cellX = pos.x;
            item.cellY = pos.y;
            return true;
        }
        return false;
    }

    /**
     * Returns the position of the item in the grid
     */
    public Point getPosForRank(int rank) {
        int pagePos = rank % mMaxItemsPerPage;
        mPoint.x = pagePos % mCountX;
        mPoint.y = pagePos / mCountX;
        return mPoint;
    }

    /**
     * Returns the preview items for the provided pageNo using the full list of contents
     */
    public <T, R extends T> ArrayList<R> previewItemsForPage(int page, List<T> contents) {
        ArrayList<R> result = new ArrayList<>();
        int itemsPerPage = mCountX * mCountY;
        int start = itemsPerPage * page;
        int end = Math.min(start + itemsPerPage, contents.size());

        for (int i = start, rank = 0; i < end; i++, rank++) {
            if (isItemInPreview(page, rank)) {
                result.add((R) contents.get(i));
            }

            if (result.size() == MAX_NUM_ITEMS_IN_PREVIEW) {
                break;
            }
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
     * @param page The page the item is on.
     * @param rank The rank of the item.
     * @return True iff the icon is in the 2x2 upper left quadrant of the Folder.
     */
    public boolean isItemInPreview(int page, int rank) {
        // First page items are laid out such that the first 4 items are always in the upper
        // left quadrant. For all other pages, we need to check the row and col.
        if (page > 0 || mDisplayingUpperLeftQuadrant) {
            int col = rank % mCountX;
            int row = rank / mCountX;
            return col < 2 && row < 2;
        }
        return rank < MAX_NUM_ITEMS_IN_PREVIEW;
    }
}
