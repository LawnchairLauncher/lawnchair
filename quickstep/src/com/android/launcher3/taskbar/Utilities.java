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

/**
 * Various utilities shared amongst the Taskbar's classes.
 */
public final class Utilities {

    private Utilities() {}

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
}
