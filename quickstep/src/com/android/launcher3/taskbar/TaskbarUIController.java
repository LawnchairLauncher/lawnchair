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

import androidx.annotation.CallSuper;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;

import java.io.PrintWriter;
import java.util.stream.Stream;

/**
 * Base class for providing different taskbar UI
 */
public class TaskbarUIController {

    public static final TaskbarUIController DEFAULT = new TaskbarUIController();

    // Initialized in init.
    protected TaskbarControllers mControllers;

    @CallSuper
    protected void init(TaskbarControllers taskbarControllers) {
        mControllers = taskbarControllers;
    }

    @CallSuper
    protected void onDestroy() {
        mControllers = null;
    }

    protected boolean isTaskbarTouchable() {
        return true;
    }

    /**
     * This should only be called by TaskbarStashController so that a TaskbarUIController can
     * disable stashing. All other controllers should use
     * {@link TaskbarStashController#supportsVisualStashing()} as the source of truth.
     */
    public boolean supportsVisualStashing() {
        return true;
    }

    protected void onStashedInAppChanged() { }

    public Stream<ItemInfoWithIcon> getAppIconsForEdu() {
        return Stream.empty();
    }

    /** Called when an icon is launched. */
    public void onTaskbarIconLaunched(ItemInfo item) { }

    public View getRootView() {
        return mControllers.taskbarActivityContext.getDragLayer();
    }

    /**
     * Called when swiping from the bottom nav region in fully gestural mode.
     * @param inProgress True if the animation started, false if we just settled on an end target.
     */
    public void setSystemGestureInProgress(boolean inProgress) {
        mControllers.taskbarStashController.setSystemGestureInProgress(inProgress);
    }

    /**
     * Manually closes the all apps window.
     */
    public void hideAllApps() {
        mControllers.taskbarAllAppsController.hide();
    }

    /**
     * User expands PiP to full-screen (or split-screen) mode, try to hide the Taskbar.
     */
    public void onExpandPip() {
        if (mControllers != null) {
            final TaskbarStashController stashController = mControllers.taskbarStashController;
            stashController.updateStateForFlag(TaskbarStashController.FLAG_IN_APP, true);
            stashController.applyState();
        }
    }

    @CallSuper
    protected void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(String.format(
                "%sTaskbarUIController: using an instance of %s",
                prefix,
                getClass().getSimpleName()));
    }
}
