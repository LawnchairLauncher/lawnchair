/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DropTarget;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BubbleTextHolder;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseAccessibilityDelegate<T extends Context & ActivityContext>
        extends View.AccessibilityDelegate implements DragController.DragListener {

    public enum DragType {
        ICON,
        FOLDER,
        APP_PAIR,
        WIDGET
    }

    public static class DragInfo {
        public DragType dragType;
        public ItemInfo info;
        public View item;
    }

    protected final SparseArray<LauncherAction> mActions = new SparseArray<>();
    protected final T mContext;

    protected DragInfo mDragInfo = null;

    protected BaseAccessibilityDelegate(T context) {
        mContext = context;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        if (host.getTag() instanceof ItemInfo) {
            ItemInfo item = (ItemInfo) host.getTag();

            List<LauncherAction> actions = new ArrayList<>();
            getSupportedActions(host, item, actions);
            actions.forEach(la -> info.addAction(la.accessibilityAction));

            if (!itemSupportsLongClick(host)) {
                info.setLongClickable(false);
                info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK);
            }
        }
    }

    /**
     * Adds all the accessibility actions that can be handled.
     */
    protected abstract void getSupportedActions(View host, ItemInfo item, List<LauncherAction> out);

    private boolean itemSupportsLongClick(View host) {
        if (host instanceof BubbleTextView) {
            return ((BubbleTextView) host).canShowLongPressPopup();
        } else if (host instanceof BubbleTextHolder) {
            BubbleTextHolder holder = (BubbleTextHolder) host;
            return holder.getBubbleText() != null && holder.getBubbleText().canShowLongPressPopup();
        } else {
            return false;
        }
    }

    protected boolean itemSupportsAccessibleDrag(ItemInfo item) {
        if (item instanceof WorkspaceItemInfo) {
            // Support the action unless the item is in a context menu.
            return item.screenId >= 0
                    && item.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
        }
        return (item instanceof LauncherAppWidgetInfo)
                || (item instanceof CollectionInfo);
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        if ((host.getTag() instanceof ItemInfo)
                && performAction(host, (ItemInfo) host.getTag(), action, false)) {
            return true;
        }
        return super.performAccessibilityAction(host, action, args);
    }

    protected abstract boolean performAction(
            View host, ItemInfo item, int action, boolean fromKeyboard);

    @Thunk
    protected void announceConfirmation(String confirmation) {
        mContext.getDragLayer().announceForAccessibility(confirmation);
    }

    public boolean isInAccessibleDrag() {
        return mDragInfo != null;
    }

    public DragInfo getDragInfo() {
        return mDragInfo;
    }

    /**
     * @param clickedTarget the actual view that was clicked
     * @param dropLocation relative to {@param clickedTarget}. If provided, its center is used
     * as the actual drop location otherwise the views center is used.
     */
    public void handleAccessibleDrop(View clickedTarget, Rect dropLocation,
            String confirmation) {
        if (!isInAccessibleDrag()) return;

        int[] loc = new int[2];
        if (dropLocation == null) {
            loc[0] = clickedTarget.getWidth() / 2;
            loc[1] = clickedTarget.getHeight() / 2;
        } else {
            loc[0] = dropLocation.centerX();
            loc[1] = dropLocation.centerY();
        }

        mContext.getDragLayer().getDescendantCoordRelativeToSelf(clickedTarget, loc);
        mContext.getDragController().completeAccessibleDrag(loc);

        if (!TextUtils.isEmpty(confirmation)) {
            announceConfirmation(confirmation);
        }
    }

    protected abstract boolean beginAccessibleDrag(View item, ItemInfo info, boolean fromKeyboard);


    @Override
    public void onDragEnd() {
        mContext.getDragController().removeDragListener(this);
        mDragInfo = null;
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        // No-op
    }

    public class LauncherAction {
        public final int keyCode;
        public final AccessibilityNodeInfo.AccessibilityAction accessibilityAction;

        private final BaseAccessibilityDelegate<T> mDelegate;

        public LauncherAction(int id, int labelRes, int keyCode) {
            this.keyCode = keyCode;
            accessibilityAction = new AccessibilityNodeInfo.AccessibilityAction(
                    id, mContext.getString(labelRes));
            mDelegate = BaseAccessibilityDelegate.this;
        }

        /**
         * Invokes the action for the provided host
         */
        public boolean invokeFromKeyboard(View host) {
            if (host != null && host.getTag() instanceof ItemInfo) {
                return mDelegate.performAction(
                        host, (ItemInfo) host.getTag(), accessibilityAction.getId(), true);
            } else {
                return false;
            }
        }
    }
}
