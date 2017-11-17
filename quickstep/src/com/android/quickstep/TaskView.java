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

import android.app.ActivityOptions;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher3.R;
import com.android.launcher3.uioverrides.OverviewState;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskCallbacks;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecCompat;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture;
import com.android.systemui.shared.recents.view.RecentsTransition;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.ArrayList;
import java.util.List;

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
        setOnClickListener((view) -> {
            launchTask(true /* animate */);
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
        if (mTask != null) {
            mTask.removeCallback(this);
        }
        mTask = task;
        task.addCallback(this);
    }

    public Task getTask() {
        return mTask;
    }

    public TaskThumbnailView getThumbnail() {
        return mSnapshotView;
    }

    public void launchTask(boolean animate) {
        if (mTask != null) {
            final ActivityOptions opts;
            if (animate) {
                // Calculate the bounds of the thumbnail to animate from
                final Rect bounds = new Rect();
                final int[] pos = new int[2];
                mSnapshotView.getLocationInWindow(pos);
                bounds.set(pos[0], pos[1],
                        pos[0] + mSnapshotView.getWidth(),
                        pos[1] + mSnapshotView.getHeight());
                AppTransitionAnimationSpecsFuture animFuture =
                        new AppTransitionAnimationSpecsFuture(getHandler()) {
                            @Override
                            public List<AppTransitionAnimationSpecCompat> composeSpecs() {
                                ArrayList<AppTransitionAnimationSpecCompat> specs =
                                        new ArrayList<>();
                                specs.add(new AppTransitionAnimationSpecCompat(mTask.key.id, null,
                                        bounds));
                                return specs;
                            }
                        };
                opts = RecentsTransition.createAspectScaleAnimation(
                        getContext(), getHandler(), true /* scaleUp */, animFuture, null);
            } else {
                opts = ActivityOptions.makeCustomAnimation(getContext(), 0, 0);
            }
            ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(mTask.key,
                    opts, null, null);
        }
    }

    @Override
    public void onTaskDataLoaded(Task task, ThumbnailData thumbnailData) {
        mSnapshotView.setThumbnail(thumbnailData);
        mIconView.setImageDrawable(task.icon);
    }

    @Override
    public void onTaskDataUnloaded() {
        mSnapshotView.setThumbnail(null);
        mIconView.setImageDrawable(null);
    }

    @Override
    public void onTaskWindowingModeChanged() {
        // Do nothing
    }
}
