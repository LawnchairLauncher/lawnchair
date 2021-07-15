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
package com.android.launcher3.taskbar;

import android.view.View;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Hotseat;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.model.data.ItemInfo;

import java.util.function.Consumer;

/**
 * Works with TaskbarController to update the TaskbarView's Hotseat items.
 */
public class TaskbarHotseatController {

    private final BaseQuickstepLauncher mLauncher;
    private final Hotseat mHotseat;
    private final Consumer<ItemInfo[]> mTaskbarCallbacks;
    private final int mNumHotseatIcons;

    private final DragController.DragListener mDragListener = new DragController.DragListener() {
        @Override
        public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) { }

        @Override
        public void onDragEnd() {
            onHotseatUpdated();
        }
    };

    public TaskbarHotseatController(
            BaseQuickstepLauncher launcher, Consumer<ItemInfo[]> taskbarCallbacks) {
        mLauncher = launcher;
        mHotseat = mLauncher.getHotseat();
        mTaskbarCallbacks = taskbarCallbacks;
        mNumHotseatIcons = mLauncher.getDeviceProfile().numShownHotseatIcons;
    }

    protected void init() {
        mLauncher.getDragController().addDragListener(mDragListener);
        onHotseatUpdated();
    }

    protected void cleanup() {
        mLauncher.getDragController().removeDragListener(mDragListener);
    }

    /**
     * Called when any Hotseat item changes, and reports the new list of items to TaskbarController.
     */
    protected void onHotseatUpdated() {
        ShortcutAndWidgetContainer shortcutsAndWidgets = mHotseat.getShortcutsAndWidgets();
        ItemInfo[] hotseatItemInfos = new ItemInfo[mNumHotseatIcons];
        for (int i = 0; i < shortcutsAndWidgets.getChildCount(); i++) {
            View child = shortcutsAndWidgets.getChildAt(i);
            Object tag = shortcutsAndWidgets.getChildAt(i).getTag();
            if (tag instanceof ItemInfo) {
                ItemInfo itemInfo = (ItemInfo) tag;
                CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();
                // Since the hotseat might be laid out vertically or horizontally, use whichever
                // index is higher.
                int index = Math.max(lp.cellX, lp.cellY);
                if (0 <= index && index < hotseatItemInfos.length) {
                    hotseatItemInfos[index] = itemInfo;
                }
            }
        }

        mTaskbarCallbacks.accept(hotseatItemInfos);
    }
}
