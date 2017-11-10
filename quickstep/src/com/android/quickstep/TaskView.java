/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher3.R;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskCallbacks;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;

/**
 * A task in the Recents view.
 */
public class TaskView extends FrameLayout implements TaskCallbacks {

    private Task mTask;
    private TaskThumbnailView mSnapshotView;
    private ImageView mIconView;

    public TaskView(Context context) {
        this(context, null);
    }

    public TaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setOnClickListener((view) -> {
            if (mTask != null) {
                ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(mTask.key,
                        null, null, null);
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        mSnapshotView = findViewById(R.id.snapshot);
        mIconView = findViewById(R.id.icon);
    }

    /**
     * Updates this task view to the given {@param task}.
     */
    public void bind(Task task) {
        mTask = task;
        task.addCallback(this);
    }

    @Override
    public void onTaskDataLoaded(Task task, ThumbnailData thumbnailData) {
        mSnapshotView.setThumbnail(thumbnailData);
        mSnapshotView.setDimAlpha(1f);
        mIconView.setImageDrawable(task.icon);
    }

    @Override
    public void onTaskDataUnloaded() {
        // Do nothing
    }

    @Override
    public void onTaskWindowingModeChanged() {
        // Do nothing
    }
}
