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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_CANCEL;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_REMOVE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_UNDO;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.views.Snackbar;

public class DeleteDropTarget extends ButtonDropTarget {

    private final StatsLogManager mStatsLogManager;

    private StatsLogManager.LauncherEvent mLauncherEvent;

    public DeleteDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mStatsLogManager = StatsLogManager.newInstance(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setDrawable(R.drawable.ic_remove_no_shadow);
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        super.onDragStart(dragObject, options);
        setTextBasedOnDragSource(dragObject.dragInfo);
        setControlTypeBasedOnDragSource(dragObject.dragInfo);
    }

    /**
     * @return true for items that should have a "Remove" action in accessibility.
     */
    @Override
    public boolean supportsAccessibilityDrop(ItemInfo info, View view) {
        if (info instanceof WorkspaceItemInfo) {
            // Support the action unless the item is in a context menu.
            return canRemove(info);
        }

        return (info instanceof LauncherAppWidgetInfo)
                || (info instanceof FolderInfo);
    }

    @Override
    public int getAccessibilityAction() {
        return LauncherAccessibilityDelegate.REMOVE;
    }

    @Override
    protected void setupItemInfo(ItemInfo info) {}

    @Override
    protected boolean supportsDrop(ItemInfo info) {
        return true;
    }

    /**
     * Set the drop target's text to either "Remove" or "Cancel" depending on the drag item.
     */
    private void setTextBasedOnDragSource(ItemInfo item) {
        if (!TextUtils.isEmpty(mText)) {
            mText = getResources().getString(canRemove(item)
                    ? R.string.remove_drop_target_label
                    : android.R.string.cancel);
            setContentDescription(mText);
            requestLayout();
        }
    }

    private boolean canRemove(ItemInfo item) {
        return item.id != ItemInfo.NO_ID;
    }

    /**
     * Set mControlType depending on the drag item.
     */
    private void setControlTypeBasedOnDragSource(ItemInfo item) {
        mLauncherEvent = item.id != ItemInfo.NO_ID ? LAUNCHER_ITEM_DROPPED_ON_REMOVE
                : LAUNCHER_ITEM_DROPPED_ON_CANCEL;
    }

    @Override
    public void onDrop(DragObject d, DragOptions options) {
        if (canRemove(d.dragInfo)) {
            mLauncher.getModelWriter().prepareToUndoDelete();
        }
        super.onDrop(d, options);
        mStatsLogManager.logger().withInstanceId(d.logInstanceId)
                .log(mLauncherEvent);
    }

    @Override
    public void completeDrop(DragObject d) {
        ItemInfo item = d.dragInfo;
        if (canRemove(item)) {
            ItemInfo pageItem = item;
            if (item.container <= 0) {
                View v = mLauncher.getWorkspace().getHomescreenIconByItemId(item.container);
                if (v != null) {
                    pageItem = (ItemInfo) v.getTag();
                }
            }
            IntSet pageIds = pageItem.container == Favorites.CONTAINER_DESKTOP
                    ? IntSet.wrap(pageItem.screenId)
                    : mLauncher.getWorkspace().getCurrentPageScreenIds();

            onAccessibilityDrop(null, item);
            ModelWriter modelWriter = mLauncher.getModelWriter();
            Runnable onUndoClicked = () -> {
                mLauncher.setPagesToBindSynchronously(pageIds);
                modelWriter.abortDelete();
                mLauncher.getStatsLogManager().logger().log(LAUNCHER_UNDO);
            };
            Snackbar.show(mLauncher, R.string.item_removed, R.string.undo,
                    modelWriter::commitDelete, onUndoClicked);
        }
    }

    /**
     * Removes the item from the workspace. If the view is not null, it also removes the view.
     */
    @Override
    public void onAccessibilityDrop(View view, ItemInfo item) {
        // Remove the item from launcher and the db, we can ignore the containerInfo in this call
        // because we already remove the drag view from the folder (if the drag originated from
        // a folder) in Folder.beginDrag()
        mLauncher.removeItem(view, item, true /* deleteFromDb */, "removed by accessibility drop");
        mLauncher.getWorkspace().stripEmptyScreens();
        mLauncher.getDragLayer()
                .announceForAccessibility(getContext().getString(R.string.item_removed));
    }
}
