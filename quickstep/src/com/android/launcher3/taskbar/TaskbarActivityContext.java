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

import android.content.ContextWrapper;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

/**
 * The {@link ActivityContext} with which we inflate Taskbar-related Views. This allows UI elements
 * that are used by both Launcher and Taskbar (such as Folder) to reference a generic
 * ActivityContext and BaseDragLayer instead of the Launcher activity and its DragLayer.
 */
public class TaskbarActivityContext extends ContextWrapper implements ActivityContext {

    private final DeviceProfile mDeviceProfile;
    private final LayoutInflater mLayoutInflater;
    private final TaskbarContainerView mTaskbarContainerView;
    private final MyDragController mDragController;

    public TaskbarActivityContext(BaseQuickstepLauncher launcher) {
        super(launcher);
        mDeviceProfile = launcher.getDeviceProfile().copy(this);
        float taskbarIconSize = getResources().getDimension(R.dimen.taskbar_icon_size);
        float iconScale = taskbarIconSize / mDeviceProfile.iconSizePx;
        mDeviceProfile.updateIconSize(iconScale, getResources());

        mLayoutInflater = LayoutInflater.from(this).cloneInContext(this);

        mTaskbarContainerView = (TaskbarContainerView) mLayoutInflater
                .inflate(R.layout.taskbar, null, false);
        mDragController = new MyDragController(this);
    }

    public TaskbarContainerView getTaskbarContainerView() {
        return mTaskbarContainerView;
    }

    @Override
    public LayoutInflater getLayoutInflater() {
        return mLayoutInflater;
    }

    @Override
    public BaseDragLayer<TaskbarActivityContext> getDragLayer() {
        return mTaskbarContainerView;
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    @Override
    public Rect getFolderBoundingBox() {
        return mTaskbarContainerView.getFolderBoundingBox();
    }

    @Override
    public DragController getDragController() {
        return mDragController;
    }

    private static class MyDragController extends DragController<TaskbarActivityContext> {
        MyDragController(TaskbarActivityContext activity) {
            super(activity);
        }

        @Override
        protected DragView startDrag(@Nullable Drawable drawable, @Nullable View view,
                DraggableView originalView, int dragLayerX, int dragLayerY, DragSource source,
                ItemInfo dragInfo, Point dragOffset, Rect dragRegion, float initialDragViewScale,
                float dragViewScaleOnDrop, DragOptions options) {
            return null;
        }

        @Override
        protected void exitDrag() { }

        @Override
        protected DropTarget getDefaultDropTarget(int[] dropCoordinates) {
            return null;
        }
    }
}
