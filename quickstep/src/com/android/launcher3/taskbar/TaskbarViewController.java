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

import android.view.View;

import com.android.launcher3.util.MultiValueAlpha;

/**
 * Handles properties/data collection, then passes the results to TaskbarView to render.
 */
public class TaskbarViewController {

    public static final int ALPHA_INDEX_HOME = 0;
    public static final int ALPHA_INDEX_LAUNCHER_STATE = 1;
    public static final int ALPHA_INDEX_IME = 2;

    private final TaskbarActivityContext mActivity;
    private final TaskbarView mTaskbarView;
    private final MultiValueAlpha mTaskbarIconAlpha;

    // Initialized in init.
    private TaskbarControllers mControllers;

    public TaskbarViewController(TaskbarActivityContext activity, TaskbarView taskbarView) {
        mActivity = activity;
        mTaskbarView = taskbarView;
        mTaskbarIconAlpha = new MultiValueAlpha(mTaskbarView, 3);
        mTaskbarIconAlpha.setUpdateVisibility(true);
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        mTaskbarView.init(new TaskbarViewCallbacks());
        mTaskbarView.getLayoutParams().height = mActivity.getDeviceProfile().taskbarSize;
    }

    public boolean areIconsVisible() {
        return mTaskbarView.areIconsVisible();
    }

    public MultiValueAlpha getTaskbarIconAlpha() {
        return mTaskbarIconAlpha;
    }

    /**
     * Should be called when the IME visibility changes, so we can make Taskbar not steal touches.
     */
    public void setImeIsVisible(boolean isImeVisible) {
        mTaskbarView.setTouchesEnabled(!isImeVisible);
    }

    /**
     * Sets OnClickListener and OnLongClickListener for the given view.
     */
    public void setClickAndLongClickListenersForIcon(View icon) {
        mTaskbarView.setClickAndLongClickListenersForIcon(icon);
    }

    /**
     * Callbacks for {@link TaskbarView} to interact with its controller.
     */
    public class TaskbarViewCallbacks {
        public View.OnClickListener getOnClickListener() {
            return mActivity::onTaskbarIconClicked;
        }

        public View.OnLongClickListener getOnLongClickListener() {
            return mControllers.taskbarDragController::startDragOnLongClick;
        }
    }
}
