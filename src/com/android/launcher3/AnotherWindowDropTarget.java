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

package com.android.launcher3;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;

/**
 * Drop target used when another window (i.e. another process) has accepted a global system drag.
 * If the accepted item was a shortcut, we delete it from Launcher.
 */
public class AnotherWindowDropTarget implements DropTarget {
    final Launcher mLauncher;

    public AnotherWindowDropTarget (Context context) { mLauncher = (Launcher) context; }

    @Override
    public boolean isDropEnabled() { return true; }

    @Override
    public void onDrop(DragObject dragObject) {
        dragObject.deferDragViewCleanupPostAnimation = false;
        LauncherModel.deleteItemFromDatabase(mLauncher, (ShortcutInfo) dragObject.dragInfo);
    }

    @Override
    public void onDragEnter(DragObject dragObject) {}

    @Override
    public void onDragOver(DragObject dragObject) {}

    @Override
    public void onDragExit(DragObject dragObject) {}

    @Override
    public void onFlingToDelete(DragObject dragObject, PointF vec) {}

    @Override
    public boolean acceptDrop(DragObject dragObject) {
        return dragObject.dragInfo instanceof ShortcutInfo;
    }

    @Override
    public void prepareAccessibilityDrop() {}

    // These methods are implemented in Views
    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {}
}
