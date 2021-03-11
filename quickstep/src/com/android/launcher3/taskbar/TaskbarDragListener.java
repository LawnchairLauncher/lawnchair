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

import android.content.ClipDescription;
import android.graphics.Point;
import android.view.DragEvent;
import android.view.View;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.model.data.ItemInfo;

import java.util.UUID;

/**
 * Listens to system drag and drop events initated by the Taskbar, and forwards them to Launcher's
 * internal DragController to move Hotseat items.
 */
public class TaskbarDragListener implements View.OnDragListener {

    private static final String MIME_TYPE_PREFIX = "com.android.launcher3.taskbar.drag_and_drop/";

    private final BaseQuickstepLauncher mLauncher;
    private final ItemInfo mDraggedItem;
    // Randomly generated id used to verify the drag event.
    private final String mId;

    // Initialized in init().
    DragLayer mDragLayer;

    /**
     * @param draggedItem The info of the item that was long clicked, which we will use to find
     *                    the equivalent match on Hotseat to drag internally.
     */
    public TaskbarDragListener(BaseQuickstepLauncher launcher, ItemInfo draggedItem) {
        mLauncher = launcher;
        mDraggedItem = draggedItem;
        mId = UUID.randomUUID().toString();
    }

    protected void init(DragLayer dragLayer) {
        mDragLayer = dragLayer;
        mDragLayer.setOnDragListener(this);
    }

    private void cleanup() {
        mDragLayer.setOnDragListener(null);
        mLauncher.setNextWorkspaceDragOptions(null);
    }

    /**
     * Returns a randomly generated id used to verify the drag event.
     */
    protected String getMimeType() {
        return MIME_TYPE_PREFIX + mId;
    }

    @Override
    public boolean onDrag(View dragLayer, DragEvent dragEvent) {
        ClipDescription clipDescription = dragEvent.getClipDescription();
        if (dragEvent.getAction() == DragEvent.ACTION_DRAG_STARTED) {
            if (clipDescription == null || !clipDescription.hasMimeType(getMimeType())) {
                // We didn't initiate this drag, ignore.
                cleanup();
                return false;
            }
            View hotseatView = mLauncher.getHotseat().getFirstItemMatch(
                    (info, view) -> info == mDraggedItem);
            if (hotseatView == null) {
                cleanup();
                return false;
            }
            DragOptions dragOptions = new DragOptions();
            dragOptions.simulatedDndStartPoint = new Point((int) dragEvent.getX(),
                    (int) dragEvent.getY());
            mLauncher.setNextWorkspaceDragOptions(dragOptions);
            hotseatView.performLongClick();
        } else if (dragEvent.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            cleanup();
        }
        return mLauncher.getDragController().onDragEvent(dragEvent);
    }
}
