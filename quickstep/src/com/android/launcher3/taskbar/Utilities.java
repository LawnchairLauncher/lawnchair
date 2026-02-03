/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.launcher3.Utilities.dpToPx;

import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.taskbar.customization.TaskbarIconSpecs;

/**
 * Various utilities shared amongst the Taskbar's classes.
 */
public final class Utilities {

    private Utilities() {
    }

    /**
     * Sets drag, long-click, and split selection behavior on 1P and 3P launchers with Taskbar
     */
    static void setOverviewDragState(TaskbarControllers controllers,
            boolean disallowGlobalDrag, boolean disallowLongClick,
            boolean allowInitialSplitSelection) {
        controllers.taskbarDragController.setDisallowGlobalDrag(disallowGlobalDrag);
        controllers.taskbarDragController.setDisallowLongClick(disallowLongClick);
        controllers.taskbarAllAppsController.setDisallowGlobalDrag(disallowGlobalDrag);
        controllers.taskbarAllAppsController.setDisallowLongClick(disallowLongClick);
        controllers.taskbarPopupController.setAllowInitialSplitSelection(
                allowInitialSplitSelection);
    }

    /**
     * Gives radius for Transient Taskbar based on selected Launcher Icon Shape.
     * Transient Taskbar radius = (icon shape radius * icon size ratio) + padding.
     *
     * @return The radius for Transient Taskbar.
     */
    static float getShapedTaskbarRadius(TaskbarActivityContext activityContext) {
        float taskbarIconSize =
                activityContext.getTaskbarSpecsEvaluator().getTaskbarIconSize().getSize();
        float maxIconSize = TaskbarIconSpecs.INSTANCE.getIconSize52dp().getSize();
        float iconShapeRadius =
                ThemeManager.INSTANCE.get(activityContext).getIconState().getShapeRadius();
        float iconSizeRatio = taskbarIconSize / maxIconSize;
        return dpToPx((iconShapeRadius * iconSizeRatio)
                + TaskbarIconSpecs.INSTANCE.getDefaultTransientIconMargin().getSize(),
                activityContext);
    }
}
