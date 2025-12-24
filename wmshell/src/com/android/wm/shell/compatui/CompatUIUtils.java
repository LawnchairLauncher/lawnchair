/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import static android.view.WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP;

import android.annotation.NonNull;
import android.app.TaskInfo;

/**
 * Utility class for Compat UI.
 */
final class CompatUIUtils {
    /**
     * Return whether or not to show size compat mode restart button if the display is phone sized.
     * @param taskInfo The info about the task to apply SCM.
     */
    public static boolean shouldShowSizeCompatRestartForPhoneScreen(@NonNull TaskInfo taskInfo) {
        // The Task's smallest screen width can't represent display smallest width for a Bubble
        // Task, so we should skip the check for Bubble.
        // TODO(b/384610402): Clean this up once Bubble Anything is launched and re-check logic
        // for other multi-window modes.
        boolean shouldCheckForPhoneScreenWidth = true;
        if (com.android.wm.shell.Flags.enableCreateAnyBubble()) {
            if (taskInfo.isAppBubble) {
                shouldCheckForPhoneScreenWidth = false;
            }
        }
        final int taskSmallestScreenWidthDp = taskInfo.configuration.smallestScreenWidthDp;
        return shouldCheckForPhoneScreenWidth &&
                taskSmallestScreenWidthDp < LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP;
    }

    private CompatUIUtils() {}
}
