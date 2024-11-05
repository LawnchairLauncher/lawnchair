/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.Display;
import android.window.IWindowContainerToken;
import android.window.WindowContainerToken;

public final class TestRunningTaskInfoBuilder {
    static int sNextTaskId = 500;
    private Rect mBounds = new Rect(0, 0, 100, 100);

    private WindowContainerToken mToken = createMockWCToken();
    private int mParentTaskId = INVALID_TASK_ID;
    private Intent mBaseIntent = new Intent();
    private @WindowConfiguration.ActivityType int mActivityType = ACTIVITY_TYPE_STANDARD;
    private @WindowConfiguration.WindowingMode int mWindowingMode = WINDOWING_MODE_UNDEFINED;
    private int mDisplayId = Display.DEFAULT_DISPLAY;
    private ActivityManager.TaskDescription.Builder mTaskDescriptionBuilder = null;
    private final Point mPositionInParent = new Point();
    private boolean mIsVisible = false;
    private long mLastActiveTime;

    public static WindowContainerToken createMockWCToken() {
        final IWindowContainerToken itoken = mock(IWindowContainerToken.class);
        final IBinder asBinder = mock(IBinder.class);
        doReturn(asBinder).when(itoken).asBinder();
        return new WindowContainerToken(itoken);
    }

    public TestRunningTaskInfoBuilder setToken(WindowContainerToken token) {
        mToken = token;
        return this;
    }

    public TestRunningTaskInfoBuilder setBounds(Rect bounds) {
        mBounds.set(bounds);
        return this;
    }

    public TestRunningTaskInfoBuilder setParentTaskId(int taskId) {
        mParentTaskId = taskId;
        return this;
    }

    /**
     * Set {@link ActivityManager.RunningTaskInfo#baseIntent} for the task info, by default
     * an empty intent is assigned
     */
    public TestRunningTaskInfoBuilder setBaseIntent(@NonNull Intent intent) {
        mBaseIntent = intent;
        return this;
    }

    public TestRunningTaskInfoBuilder setActivityType(
            @WindowConfiguration.ActivityType int activityType) {
        mActivityType = activityType;
        return this;
    }

    public TestRunningTaskInfoBuilder setWindowingMode(
            @WindowConfiguration.WindowingMode int windowingMode) {
        mWindowingMode = windowingMode;
        return this;
    }

    public TestRunningTaskInfoBuilder setDisplayId(int displayId) {
        mDisplayId = displayId;
        return this;
    }

    public TestRunningTaskInfoBuilder setTaskDescriptionBuilder(
            ActivityManager.TaskDescription.Builder builder) {
        mTaskDescriptionBuilder = builder;
        return this;
    }

    public TestRunningTaskInfoBuilder setPositionInParent(int x, int y) {
        mPositionInParent.set(x, y);
        return this;
    }

    public TestRunningTaskInfoBuilder setVisible(boolean isVisible) {
        mIsVisible = isVisible;
        return this;
    }

    public TestRunningTaskInfoBuilder setLastActiveTime(long lastActiveTime) {
        mLastActiveTime = lastActiveTime;
        return this;
    }

    public ActivityManager.RunningTaskInfo build() {
        final ActivityManager.RunningTaskInfo info = new ActivityManager.RunningTaskInfo();
        info.taskId = sNextTaskId++;
        info.baseIntent = mBaseIntent;
        info.parentTaskId = mParentTaskId;
        info.displayId = mDisplayId;
        info.configuration.windowConfiguration.setBounds(mBounds);
        info.configuration.windowConfiguration.setActivityType(mActivityType);
        info.configuration.windowConfiguration.setWindowingMode(mWindowingMode);
        info.token = mToken;
        info.isResizeable = true;
        info.supportsMultiWindow = true;
        info.taskDescription =
                mTaskDescriptionBuilder != null ? mTaskDescriptionBuilder.build() : null;
        info.positionInParent = mPositionInParent;
        info.isVisible = mIsVisible;
        info.lastActiveTime = mLastActiveTime;
        return info;
    }
}
