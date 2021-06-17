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

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.UserHandle;
import android.view.DragEvent;
import android.view.View;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ClipDescriptionCompat;
import com.android.systemui.shared.system.LauncherAppsCompat;

/**
 * Handles long click on Taskbar items to start a system drag and drop operation.
 */
public class TaskbarDragController {

    private final Context mContext;
    private final int mDragIconSize;

    public TaskbarDragController(Context context) {
        mContext = context;
        Resources resources = mContext.getResources();
        mDragIconSize = resources.getDimensionPixelSize(R.dimen.taskbar_icon_drag_icon_size);
    }

    /**
     * Attempts to start a system drag and drop operation for the given View, using its tag to
     * generate the ClipDescription and Intent.
     * @return Whether {@link View#startDragAndDrop} started successfully.
     */
    protected boolean startSystemDragOnLongClick(View view) {
        if (!(view instanceof BubbleTextView)) {
            return false;
        }

        BubbleTextView btv = (BubbleTextView) view;
        View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view) {
            @Override
            public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
                shadowSize.set(mDragIconSize, mDragIconSize);
                // TODO: should be based on last touch point on the icon.
                shadowTouchPoint.set(shadowSize.x / 2, shadowSize.y / 2);
            }

            @Override
            public void onDrawShadow(Canvas canvas) {
                canvas.save();
                float scale = (float) mDragIconSize / btv.getIconSize();
                canvas.scale(scale, scale);
                btv.getIcon().draw(canvas);
                canvas.restore();
            }
        };

        Object tag = view.getTag();
        ClipDescription clipDescription = null;
        Intent intent = null;
        if (tag instanceof WorkspaceItemInfo) {
            WorkspaceItemInfo item = (WorkspaceItemInfo) tag;
            LauncherApps launcherApps = mContext.getSystemService(LauncherApps.class);
            clipDescription = new ClipDescription(item.title,
                    new String[] {
                            item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
                                    ? ClipDescriptionCompat.MIMETYPE_APPLICATION_SHORTCUT
                                    : ClipDescriptionCompat.MIMETYPE_APPLICATION_ACTIVITY
                    });
            intent = new Intent();
            if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME, item.getIntent().getPackage());
                intent.putExtra(Intent.EXTRA_SHORTCUT_ID, item.getDeepShortcutId());
            } else {
                intent.putExtra(ClipDescriptionCompat.EXTRA_PENDING_INTENT,
                        LauncherAppsCompat.getMainActivityLaunchIntent(launcherApps,
                                item.getIntent().getComponent(), null, item.user));
            }
            intent.putExtra(Intent.EXTRA_USER, item.user);
        } else if (tag instanceof Task) {
            Task task = (Task) tag;
            clipDescription = new ClipDescription(task.titleDescription,
                    new String[] {
                            ClipDescriptionCompat.MIMETYPE_APPLICATION_TASK
                    });
            intent = new Intent();
            intent.putExtra(ClipDescriptionCompat.EXTRA_TASK_ID, task.key.id);
            intent.putExtra(Intent.EXTRA_USER, UserHandle.of(task.key.userId));
        }

        if (clipDescription != null && intent != null) {
            ClipData clipData = new ClipData(clipDescription, new ClipData.Item(intent));
            view.setOnDragListener(getDraggedViewDragListener());
            return view.startDragAndDrop(clipData, shadowBuilder, null /* localState */,
                    View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_OPAQUE);
        }
        return false;
    }

    /**
     * Hide the original Taskbar item while it is being dragged.
     */
    private View.OnDragListener getDraggedViewDragListener() {
        return (view, dragEvent) -> {
            switch (dragEvent.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    view.setVisibility(INVISIBLE);
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    view.setVisibility(VISIBLE);
                    view.setOnDragListener(null);
                    return true;
            }
            return false;
        };
    }
}
