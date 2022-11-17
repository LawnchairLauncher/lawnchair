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

package com.android.quickstep.util;

import static com.android.launcher3.config.FeatureFlags.ENABLE_SPLIT_FROM_FULLSCREEN_WITH_KEYBOARD_SHORTCUTS;
import static com.android.launcher3.config.FeatureFlags.ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE;

import android.content.Intent;
import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.model.data.WorkspaceItemInfo;

/** Handles when the stage split lands on the home screen. */
public class SplitToWorkspaceController {

    private final Launcher mLauncher;
    private final SplitSelectStateController mController;

    public SplitToWorkspaceController(Launcher launcher, SplitSelectStateController controller) {
        mLauncher = launcher;
        mController = controller;
    }

    /**
     * Handles second app selection from stage split. If the item can't be opened in split or
     * it's not in stage split state, we pass it onto Launcher's default item click handler.
     */
    public boolean handleSecondAppSelectionForSplit(View view) {
        if ((!ENABLE_SPLIT_FROM_FULLSCREEN_WITH_KEYBOARD_SHORTCUTS.get()
                && !ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE.get())
                || !mController.isSplitSelectActive()) {
            return false;
        }
        Object tag = view.getTag();
        Intent intent;
        if (tag instanceof WorkspaceItemInfo) {
            final WorkspaceItemInfo workspaceItemInfo = (WorkspaceItemInfo) tag;
            intent = workspaceItemInfo.intent;
        } else if (tag instanceof com.android.launcher3.model.data.AppInfo) {
            final com.android.launcher3.model.data.AppInfo appInfo =
                    (com.android.launcher3.model.data.AppInfo) tag;
            intent = appInfo.intent;
        } else {
            return false;
        }
        mController.setSecondTask(intent);
        mController.launchSplitTasks(aBoolean -> mLauncher.getDragLayer().removeView(
                mController.getFirstFloatingTaskView()));
        return true;
    }
}
