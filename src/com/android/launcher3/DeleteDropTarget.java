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

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.logging.LoggerUtils;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

public class DeleteDropTarget extends ButtonDropTarget {

    private int mControlType = ControlType.DEFAULT_CONTROLTYPE;

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
        setTextBasedOnDragSource(dragObject.dragInfo);
        setControlTypeBasedOnDragSource(dragObject.dragInfo);
    }

    /**
     * @return true for items that should have a "Remove" action in accessibility.
     */
    @Override
    public boolean supportsAccessibilityDrop(ItemInfo info, View view) {
        return (info instanceof ShortcutInfo)
                || (info instanceof LauncherAppWidgetInfo)
                || (info instanceof FolderInfo);
    }

    @Override
    public int getAccessibilityAction() {
        return LauncherAccessibilityDelegate.REMOVE;
    }

    @Override
    protected boolean supportsDrop(ItemInfo info) {
        return true;
    }

    /**
     * Set the drop target's text to either "Remove" or "Cancel" depending on the drag item.
     */
    private void setTextBasedOnDragSource(ItemInfo item) {
        if (!TextUtils.isEmpty(mText)) {
            mText = getResources().getString(item.id != ItemInfo.NO_ID
                    ? R.string.remove_drop_target_label
                    : android.R.string.cancel);
            requestLayout();
        }
    }

    /**
     * Set mControlType depending on the drag item.
     */
    private void setControlTypeBasedOnDragSource(ItemInfo item) {
        mControlType = item.id != ItemInfo.NO_ID ? ControlType.REMOVE_TARGET
                : ControlType.CANCEL_TARGET;
    }

    @Override
    public void completeDrop(DragObject d) {
        ItemInfo item = d.dragInfo;
        if ((d.dragSource instanceof Workspace) || (d.dragSource instanceof Folder)) {
            onAccessibilityDrop(null, item);
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
        mLauncher.removeItem(view, item, true /* deleteFromDb */);
        mLauncher.getWorkspace().stripEmptyScreens();
        mLauncher.getDragLayer()
                .announceForAccessibility(getContext().getString(R.string.item_removed));
    }

    @Override
    public Target getDropTargetForLogging() {
        Target t = LoggerUtils.newTarget(Target.Type.CONTROL);
        t.controlType = mControlType;
        return t;
    }
}
