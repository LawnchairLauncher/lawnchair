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
package com.android.launcher3.widget.dragndrop;

import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

/** A drag listener of {@link LauncherAppWidgetHostView}. */
public final class AppWidgetHostViewDragListener implements DragController.DragListener {
    private final Launcher mLauncher;
    private DropTarget.DragObject mDragObject;
    private LauncherAppWidgetHostView mAppWidgetHostView;

    public AppWidgetHostViewDragListener(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions unused) {
        if (dragObject.dragView.getContentView() instanceof LauncherAppWidgetHostView) {
            mDragObject = dragObject;
            mAppWidgetHostView = (LauncherAppWidgetHostView) dragObject.dragView.getContentView();
            mAppWidgetHostView.startDrag(this);
        } else {
            mLauncher.getDragController().removeDragListener(this);
        }
    }

    @Override
    public void onDragEnd() {
        mAppWidgetHostView.endDrag();
        mLauncher.getDragController().removeDragListener(this);
    }

    /** Notifies when there is a content change in the drag view. */
    public void onDragContentChanged() {
        if (mDragObject.dragView != null) {
            mDragObject.dragView.invalidate();
        }
    }
}
