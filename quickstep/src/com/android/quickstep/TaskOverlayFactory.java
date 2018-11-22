/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.quickstep;

import android.content.Context;
import android.graphics.Matrix;
import android.view.View;

import androidx.annotation.AnyThread;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory class to create and add an overlays on the TaskView
 */
public class TaskOverlayFactory implements ResourceBasedOverride {
    private static TaskOverlayFactory sInstance;

    /** Note that these will be shown in order from top to bottom, if available for the task. */
    private static final TaskSystemShortcut[] MENU_OPTIONS = new TaskSystemShortcut[]{
            new TaskSystemShortcut.AppInfo(),
            new TaskSystemShortcut.SplitScreen(),
            new TaskSystemShortcut.Pin(),
            new TaskSystemShortcut.Install(),
            new TaskSystemShortcut.Freeform()
    };

    public static TaskOverlayFactory get(Context context) {
        Preconditions.assertUIThread();
        if (sInstance == null) {
            sInstance = Overrides.getObject(TaskOverlayFactory.class,
                    context.getApplicationContext(), R.string.task_overlay_factory_class);
        }
        return sInstance;
    }

    @AnyThread
    public boolean needAssist() {
        return false;
    }

    public TaskOverlay createOverlay(View thumbnailView) {
        return new TaskOverlay();
    }

    public static class TaskOverlay {

        public void setTaskInfo(Task task, ThumbnailData thumbnail, Matrix matrix) {
        }

        public void reset() {
        }

        public List<TaskSystemShortcut> getEnabledShortcuts(TaskView taskView) {
            final ArrayList<TaskSystemShortcut> shortcuts = new ArrayList<>();
            final BaseDraggingActivity activity = BaseActivity.fromContext(taskView.getContext());
            for (TaskSystemShortcut menuOption : MENU_OPTIONS) {
                View.OnClickListener onClickListener =
                        menuOption.getOnClickListener(activity, taskView);
                if (onClickListener != null) {
                    shortcuts.add(menuOption);
                }
            }
            return shortcuts;
        }
    }
}
