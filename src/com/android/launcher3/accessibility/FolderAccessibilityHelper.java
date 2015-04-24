/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.accessibility;

import com.android.launcher3.CellLayout;
import com.android.launcher3.FolderPagedView;
import com.android.launcher3.R;

/**
 * Implementation of {@link DragAndDropAccessibilityDelegate} to support DnD in a folder.
 */
public class FolderAccessibilityHelper extends DragAndDropAccessibilityDelegate {
    private final int mStartPosition;

    public FolderAccessibilityHelper(CellLayout layout) {
        super(layout);
        FolderPagedView parent = (FolderPagedView) layout.getParent();

        int index = parent.indexOfChild(layout);
        mStartPosition = 1 + index * layout.getCountX() * layout.getCountY();
    }
    @Override
    protected int intersectsValidDropTarget(int id) {
        return id;
    }

    @Override
    protected String getLocationDescriptionForIconDrop(int id) {
        return mContext.getString(R.string.move_to_position, id + mStartPosition);
    }

    @Override
    protected String getConfirmationForIconDrop(int id) {
        return mContext.getString(R.string.item_moved);
    }
}
