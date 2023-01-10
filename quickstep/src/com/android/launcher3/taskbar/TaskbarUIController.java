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

import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;

import java.io.PrintWriter;
import java.util.function.Consumer;

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
     * Manually closes the overlay window.
     */
    public void hideOverlayWindow() {
        if (!DisplayController.isTransientTaskbar(mControllers.taskbarActivityContext)
                || mControllers.taskbarAllAppsController.isOpen()) {
            mControllers.taskbarOverlayController.hideWindow();
        }
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

    /**
     * Returns {@code true} iff taskbar is stashed.
     */
    public boolean isTaskbarStashed() {
        return mControllers.taskbarStashController.isStashed();
    }

    /**
     * Returns {@code true} iff taskbar All Apps is open.
     */
    public boolean isTaskbarAllAppsOpen() {
        return mControllers.taskbarAllAppsController.isOpen();
    }

    /**
     * Called at the end of the swipe gesture on Transient taskbar.
     */
    public void startTranslationSpring() {
        mControllers.taskbarActivityContext.startTranslationSpring();
    }

    /*
     * @param ev MotionEvent in screen coordinates.
     * @return Whether any Taskbar item could handle the given MotionEvent if given the chance.
     */
    public boolean isEventOverAnyTaskbarItem(MotionEvent ev) {
        return mControllers.taskbarViewController.isEventOverAnyItem(ev)
                || mControllers.navbarButtonsViewController.isEventOverAnyItem(ev);
    }

    /**
     * Returns true if icons should be aligned to hotseat in the current transition.
     */
    public boolean isIconAlignedWithHotseat() {
        return false;
    }

    /**
     * Returns true if hotseat icons are on top of view hierarchy when aligned in the current state.
     */
    public boolean isHotseatIconOnTopWhenAligned() {
        return true;
    }

    @CallSuper
    protected void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(String.format(
                "%sTaskbarUIController: using an instance of %s",
                prefix,
                getClass().getSimpleName()));
    }

    /**
     * Returns RecentsView. Overwritten in LauncherTaskbarUIController and
     * FallbackTaskbarUIController with Launcher-specific implementations. Returns null for other
     * UI controllers (like DesktopTaskbarUIController) that don't have a RecentsView.
     */
    public @Nullable RecentsView getRecentsView() {
        return null;
    }

    /**
     * Uses the clicked Taskbar icon to launch a second app for splitscreen.
     */
    public void triggerSecondAppForSplit(ItemInfoWithIcon info, Intent intent, View startingView) {
        RecentsView recents = getRecentsView();
        recents.findLastActiveTaskAndDoSplitOperation(
                info.getTargetComponent(),
                (Consumer<Task>) foundTask -> {
                    if (foundTask != null) {
                        TaskView foundTaskView = recents.getTaskViewByTaskId(foundTask.key.id);
                        // There is already a running app of this type, use that as second app.
                        recents.confirmSplitSelect(
                                foundTaskView,
                                foundTaskView.getTask(),
                                foundTaskView.getIconView().getDrawable(),
                                foundTaskView.getThumbnail(),
                                foundTaskView.getThumbnail().getThumbnail(),
                                null /* intent */);
                    } else {
                        // No running app of that type, create a new instance as second app.
                        recents.confirmSplitSelect(
                                null /* containerTaskView */,
                                null /* task */,
                                new BitmapDrawable(info.bitmap.icon),
                                startingView,
                                null /* thumbnail */,
                                intent);
                    }
                }
        );
    }
}
