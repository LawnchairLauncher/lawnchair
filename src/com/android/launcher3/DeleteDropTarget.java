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

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.patrykmichalik.opto.core.PreferenceExtensionsKt;

import app.lawnchair.preferences2.PreferenceManager2;

public class DeleteDropTarget extends ButtonDropTarget {

    private final StatsLogManager mStatsLogManager;

    private StatsLogManager.LauncherEvent mLauncherEvent;

    private final PreferenceManager2 pref2;

    public DeleteDropTarget(Context context) {
        this(context, null, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mStatsLogManager = StatsLogManager.newInstance(context);
        pref2 = PreferenceManager2.getInstance(context);
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
                || (info instanceof CollectionInfo);
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

    private boolean isCanDrop(ItemInfo item){
        return !(item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                item.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER);
    }

    private boolean canRemove(ItemInfo item) {
        boolean isDeckLayoutFirst = PreferenceExtensionsKt.firstBlocking(pref2.getDeckLayout());
        return isDeckLayoutFirst ? isCanDrop(item) : item.id != ItemInfo.NO_ID;
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
            mDropTargetHandler.prepareToUndoDelete();
        }
        super.onDrop(d, options);
        mStatsLogManager.logger().withInstanceId(d.logInstanceId)
                .log(mLauncherEvent);
    }

    @Override
    public void completeDrop(DragObject d) {
        ItemInfo item = d.dragInfo;
        if (canRemove(item)) {
            mDropTargetHandler.onDeleteComplete(item);
        } else if (mText == getResources().getText(R.string.remove_drop_target_label)) {
            Log.wtf("b/379606516", "If the drop target text is 'remove', then"
                    + " users should always be able to delete the item from launcher's db."
                    + " Invalid drag ItemInfo: " + item);
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
        CharSequence announcement = getContext().getString(R.string.item_removed);
        if (!PreferenceExtensionsKt.firstBlocking(pref2.getDeckLayout())) {
            mDropTargetHandler.onAccessibilityDelete(view, item, announcement);
        }
    }
}
