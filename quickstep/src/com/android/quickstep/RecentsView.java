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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan;
import com.android.systemui.shared.recents.model.RecentsTaskLoader;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.TaskStack;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.ArrayList;

/**
 * A list of recent tasks.
 */
public class RecentsView extends PagedView {

    private boolean mOverviewStateEnabled;
    private boolean mTaskStackListenerRegistered;

    private TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {
        @Override
        public void onTaskSnapshotChanged(int taskId, ThumbnailData snapshot) {
            for (int i = 0; i < getChildCount(); i++) {
                final TaskView taskView = (TaskView) getChildAt(i);
                if (taskView.getTask().key.id == taskId) {
                    taskView.getThumbnail().setThumbnail(snapshot);
                    return;
                }
            }
        }
    };

    public RecentsView(Context context) {
        this(context, null);
    }

    public RecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setPageSpacing((int) getResources().getDimension(R.dimen.recents_page_spacing));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // TODO: These are rough calculations which currently use the stable insets
        DeviceProfile profile = Launcher.getLauncher(getContext()).getDeviceProfile();
        Rect stableInsets = new Rect();
        WindowManagerWrapper.getInstance().getStableInsets(stableInsets);
        Rect padding = profile.getWorkspacePadding(null);
        float taskWidth = profile.getCurrentWidth() - stableInsets.left - stableInsets.right;
        float taskHeight = profile.getCurrentHeight() - stableInsets.top - stableInsets.bottom;
        float overviewHeight = profile.availableHeightPx - padding.top - padding.bottom
                - stableInsets.top;
        float overviewWidth = taskWidth * overviewHeight / taskHeight;
        padding.left = padding.right = (int) ((profile.availableWidthPx - overviewWidth) / 2);
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateTaskStackListenerState();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTaskStackListenerState();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateTaskStackListenerState();
    }

    public void setOverviewStateEnabled(boolean enabled) {
        mOverviewStateEnabled = enabled;
        updateTaskStackListenerState();
    }

    public void update(RecentsTaskLoadPlan loadPlan) {
        final RecentsTaskLoader loader = TouchInteractionService.getRecentsTaskLoader();
        setCurrentPage(0);
        TaskStack stack = loadPlan != null ? loadPlan.getTaskStack() : null;
        if (stack == null) {
            removeAllViews();
            return;
        }

        // Ensure there are as many views as there are tasks in the stack (adding and trimming as
        // necessary)
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final ArrayList<Task> tasks = stack.getTasks();
        for (int i = getChildCount(); i < tasks.size(); i++) {
            final TaskView taskView = (TaskView) inflater.inflate(R.layout.task, this, false);
            addView(taskView);
        }
        while (getChildCount() > tasks.size()) {
            final TaskView taskView = (TaskView) getChildAt(getChildCount() - 1);
            removeView(taskView);
            loader.unloadTaskData(taskView.getTask());
        }

        // Rebind all task views
        for (int i = tasks.size() - 1; i >= 0; i--) {
            final Task task = tasks.get(i);
            final TaskView taskView = (TaskView) getChildAt(tasks.size() - i - 1);
            taskView.bind(task);
            loader.loadTaskData(task);
        }
    }

    public void launchTaskWithId(int taskId) {
        for (int i = 0; i < getChildCount(); i++) {
            final TaskView taskView = (TaskView) getChildAt(i);
            if (taskView.getTask().key.id == taskId) {
                taskView.launchTask(false /* animate */);
                return;
            }
        }
    }

    private void updateTaskStackListenerState() {
        boolean registerStackListener = mOverviewStateEnabled && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE;
        if (registerStackListener != mTaskStackListenerRegistered) {
            if (registerStackListener) {
                ActivityManagerWrapper.getInstance().registerTaskStackListener(mTaskStackListener);
            } else {
                ActivityManagerWrapper.getInstance().unregisterTaskStackListener(mTaskStackListener);
            }
            mTaskStackListenerRegistered = registerStackListener;
        }
    }
}
