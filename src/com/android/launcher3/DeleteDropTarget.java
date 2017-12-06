/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;

public class DeleteDropTarget extends ButtonDropTarget {

    public DeleteDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Get the hover color
        mHoverColor = getResources().getColor(R.color.delete_target_hover_tint);

        setDrawable(R.drawable.ic_remove_shadow);
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        super.onDragStart(dragObject, options);
        setTextBasedOnDragSource(dragObject.dragSource);
    }

    /** @return true for items that should have a "Remove" action in accessibility. */
    public static boolean supportsAccessibleDrop(ItemInfo info) {
        return (info instanceof ShortcutInfo)
                || (info instanceof LauncherAppWidgetInfo)
                || (info instanceof FolderInfo);
    }

    @Override
    protected boolean supportsDrop(DragSource source, ItemInfo info) {
        return true;
    }

    /**
     * Set the drop target's text to either "Remove" or "Cancel" depending on the drag source.
     */
    public void setTextBasedOnDragSource(DragSource dragSource) {
        if (!TextUtils.isEmpty(mText)) {
            mText = getResources().getString(dragSource.supportsDeleteDropTarget()
                    ? R.string.remove_drop_target_label
                    : android.R.string.cancel);
            requestLayout();
        }
    }

    @Override
    public void completeDrop(DragObject d) {
        ItemInfo item = d.dragInfo;
        if ((d.dragSource instanceof Workspace) || (d.dragSource instanceof Folder)) {
            removeWorkspaceOrFolderItem(mLauncher, item, null);
        }
    }

    /**
     * Removes the item from the workspace. If the view is not null, it also removes the view.
     */
    public static void removeWorkspaceOrFolderItem(Launcher launcher, ItemInfo item, View view) {
        // Remove the item from launcher and the db, we can ignore the containerInfo in this call
        // because we already remove the drag view from the folder (if the drag originated from
        // a folder) in Folder.beginDrag()
        launcher.removeItem(view, item, true /* deleteFromDb */);
        launcher.getWorkspace().stripEmptyScreens();
        launcher.getDragLayer().announceForAccessibility(launcher.getString(R.string.item_removed));
    }
}
