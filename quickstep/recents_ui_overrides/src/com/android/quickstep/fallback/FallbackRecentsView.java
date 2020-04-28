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
package com.android.quickstep.fallback;

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;

import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState.ScaleAndTranslation;
import com.android.launcher3.Utilities;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;

import java.util.ArrayList;

public class FallbackRecentsView extends RecentsView<RecentsActivity> {

    public static final FloatProperty<FallbackRecentsView> ZOOM_PROGRESS =
            new FloatProperty<FallbackRecentsView> ("zoomInProgress") {

                @Override
                public void setValue(FallbackRecentsView view, float value) {
                    view.setZoomProgress(value);
                }

                @Override
                public Float get(FallbackRecentsView view) {
                    return view.mZoomInProgress;
                }
            };

    private float mZoomInProgress = 0;
    private boolean mInOverviewState = true;

    private float mZoomScale = 1f;
    private float mZoomTranslationY = 0f;

    private RunningTaskInfo mRunningTaskInfo;

    public FallbackRecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FallbackRecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOverviewStateEnabled(true);
        setOverlayEnabled(true);
    }

    @Override
    public void startHome() {
        mActivity.startHome();
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateEmptyMessage();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        updateEmptyMessage();
    }

    @Override
    public void draw(Canvas canvas) {
        maybeDrawEmptyMessage(canvas);
        super.draw(canvas);
    }

    @Override
    public void reset() {
        super.reset();
        resetViewUI();
    }

    @Override
    protected void getTaskSize(DeviceProfile dp, Rect outRect) {
        LayoutUtils.calculateFallbackTaskSize(getContext(), dp, outRect);
    }

    @Override
    public boolean shouldUseMultiWindowTaskSizeStrategy() {
        // Just use the activity task size for multi-window as well.
        return false;
    }

    public void resetViewUI() {
        setZoomProgress(0);
        resetTaskVisuals();
    }

    public void setInOverviewState(boolean inOverviewState) {
        if (mInOverviewState != inOverviewState) {
            mInOverviewState = inOverviewState;
            if (mInOverviewState) {
                resetTaskVisuals();
            } else {
                setZoomProgress(1);
            }
        }
    }

    @Override
    public void resetTaskVisuals() {
        super.resetTaskVisuals();
        setFullscreenProgress(mFullscreenProgress);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (getTaskViewCount() == 0) {
            mZoomScale = 1f;
            mZoomTranslationY = 0f;
        } else {
            TaskView dummyTask = getTaskViewAt(0);
            ScaleAndTranslation sat = getTempClipAnimationHelper()
                    .updateForFullscreenOverview(dummyTask)
                    .getScaleAndTranslation();
            mZoomScale = sat.scale;
            mZoomTranslationY = sat.translationY;
        }

        setZoomProgress(mZoomInProgress);
    }

    public void setZoomProgress(float progress) {
        mZoomInProgress = progress;
        SCALE_PROPERTY.set(this, Utilities.mapRange(mZoomInProgress, 1, mZoomScale));
        TRANSLATION_Y.set(this, Utilities.mapRange(mZoomInProgress, 0, mZoomTranslationY));
        FULLSCREEN_PROGRESS.set(this, mZoomInProgress);
    }

    public void onGestureAnimationStart(RunningTaskInfo runningTaskInfo) {
        mRunningTaskInfo = runningTaskInfo;
        onGestureAnimationStart(runningTaskInfo == null ? -1 : runningTaskInfo.taskId);
    }

    @Override
    public void setCurrentTask(int runningTaskId) {
        super.setCurrentTask(runningTaskId);
        if (mRunningTaskInfo != null && mRunningTaskInfo.taskId != runningTaskId) {
            mRunningTaskInfo = null;
        }
    }

    @Override
    protected void applyLoadPlan(ArrayList<Task> tasks) {
        // When quick-switching on 3p-launcher, we add a "dummy" tile corresponding to Launcher
        // as well. This tile is never shown as we have setCurrentTaskHidden, but allows use to
        // track the index of the next task appropriately, as if we are switching on any other app.
        if (mRunningTaskInfo != null && mRunningTaskInfo.taskId == mRunningTaskId) {
            // Check if the task list has running task
            boolean found = false;
            for (Task t : tasks) {
                if (t.key.id == mRunningTaskId) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                ArrayList<Task> newList = new ArrayList<>(tasks.size() + 1);
                newList.addAll(tasks);
                newList.add(Task.from(new TaskKey(mRunningTaskInfo), mRunningTaskInfo, false));
                tasks = newList;
            }
        }
        super.applyLoadPlan(tasks);
    }
}
