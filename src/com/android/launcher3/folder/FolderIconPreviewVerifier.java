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

package com.android.launcher3.folder;

import com.android.launcher3.FolderInfo;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.config.FeatureFlags;

/**
 * Verifies whether an item in a Folder is displayed in the FolderIcon preview.
 */
public class FolderIconPreviewVerifier {

    private final int mMaxGridCountX;
    private final int mMaxGridCountY;
    private final int mMaxItemsPerPage;
    private final int[] mGridSize = new int[2];

    private int mGridCountX;
    private boolean mDisplayingUpperLeftQuadrant = false;

    public FolderIconPreviewVerifier(InvariantDeviceProfile profile) {
        mMaxGridCountX = profile.numFolderColumns;
        mMaxGridCountY = profile.numFolderRows;
        mMaxItemsPerPage = mMaxGridCountX * mMaxGridCountY;
    }

    public void setFolderInfo(FolderInfo info) {
        int numItemsInFolder = info.contents.size();
        mDisplayingUpperLeftQuadrant = FeatureFlags.LAUNCHER3_NEW_FOLDER_ANIMATION
                && !FeatureFlags.LAUNCHER3_LEGACY_FOLDER_ICON
                && numItemsInFolder > FolderIcon.NUM_ITEMS_IN_PREVIEW;

        if (mDisplayingUpperLeftQuadrant) {
            FolderPagedView.calculateGridSize(info.contents.size(), 0, 0, mMaxGridCountX,
                    mMaxGridCountY, mMaxItemsPerPage, mGridSize);
            mGridCountX = mGridSize[0];
        }
    }

    public boolean isItemInPreview(int rank) {
        if (mDisplayingUpperLeftQuadrant) {
            // Returns true iff the icon is in the 2x2 upper left quadrant of the Folder.
            int col = rank % mGridCountX;
            int row = rank / mGridCountX;
            return col < 2 && row < 2;
        }
        return rank < FolderIcon.NUM_ITEMS_IN_PREVIEW;
    }
}
