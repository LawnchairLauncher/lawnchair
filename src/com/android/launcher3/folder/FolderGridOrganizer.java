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
import android.util.Log;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;

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
    /**
     * How many items the folder icon's preview has room for.
     *
     * Four, for as long as a folder was always one cell. A folder given more
     * cells lays its preview on a grid and can show more, so it says how many
     * before asking for them.
     */
    private int mPreviewLimit = MAX_NUM_ITEMS_IN_PREVIEW;
    private int mCountX;
    private int mCountY;
    private int mMinCountX = 0;
    private int mMinCountY = 0;
    private boolean mDisplayingUpperLeftQuadrant = false;
    private static final int PREVIEW_MAX_ROWS = 2;
    private static final int PREVIEW_MAX_COLUMNS = 2;

    /**
     * Note: must call {@link #setFolderInfo(FolderInfo)} manually for verifier to work.
     */
    public FolderGridOrganizer(int maxCountX, int maxCountY) {
        mMaxCountX = maxCountX;
        mMaxCountY = maxCountY;
        mMaxItemsPerPage = mMaxCountX * mMaxCountY;
    }

    /**
     * Creates a FolderGridOrganizer for the given DeviceProfile
     */
    public static FolderGridOrganizer createFolderGridOrganizer(DeviceProfile profile) {
        return new FolderGridOrganizer(profile.numFolderColumns, profile.numFolderRows);
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

            mDisplayingUpperLeftQuadrant = contentSize > MAX_NUM_ITEMS_IN_PREVIEW;
            mNumItemsInFolder = contentSize;
        }
        return this;
    }

    /** Sets how many items the caller's preview can show. Never fewer than four. */
    public FolderGridOrganizer setPreviewLimit(int limit) {
        mPreviewLimit = Math.max(MAX_NUM_ITEMS_IN_PREVIEW, limit);
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

    /** Sets minimum grid columns and rows (e.g. forced 3x3 in Centered mode). */
    public FolderGridOrganizer setMinGridSize(int minX, int minY) {
        if (mMinCountX != minX || mMinCountY != minY) {
            mMinCountX = minX;
            mMinCountY = minY;
            calculateGridSize(mNumItemsInFolder);
        }
        return this;
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

        mCountX = Math.max(gridCountX, mMinCountX);
        mCountY = Math.max(gridCountY, mMinCountY);
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

            if (result.size() == mPreviewLimit) {
                break;
            }
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
     * @param page The page the item is on.
     * @param rank The rank of the item.
     * @return True iff the icon is in the 2x2 upper left quadrant of the Folder.
     */
    public boolean isItemInPreview(int page, int rank) {
        // A grown plate lays its preview out on a grid of its own and fills it
        // in reading order, so the upper-left quadrant the huddle drew from
        // stops applying -- it would hand back four items for a preview with
        // seven places to put them.
        if (mPreviewLimit > MAX_NUM_ITEMS_IN_PREVIEW) {
            return rank < mPreviewLimit;
        }
        // First page items are laid out such that the first 4 items are always in the upper
        // left quadrant. For all other pages, we need to check the row and col.
        if (page > 0 || mDisplayingUpperLeftQuadrant) {
            int col = rank % mCountX;
            int row = rank / mCountX;
            return col < PREVIEW_MAX_COLUMNS && row < PREVIEW_MAX_ROWS;
        }
        // If we have less than 4 items do this
        return rank < MAX_NUM_ITEMS_IN_PREVIEW;
    }
}