/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.quickstep.util

import com.android.launcher3.util.SplitConfigurationOptions
import com.android.wm.shell.util.SplitBounds

class SplitScreenUtils {
    companion object {
        // TODO(b/254378592): Remove these methods when the two classes are reunited
        /** Converts the shell version of SplitBounds to the launcher version */
        @JvmStatic
        fun convertShellSplitBoundsToLauncher(
            shellSplitBounds: SplitBounds?
        ): SplitConfigurationOptions.SplitBounds? {
            return if (shellSplitBounds == null) {
                null
            } else {
                SplitConfigurationOptions.SplitBounds(
                    shellSplitBounds.leftTopBounds, shellSplitBounds.rightBottomBounds,
                    shellSplitBounds.leftTopTaskId, shellSplitBounds.rightBottomTaskId,
                    shellSplitBounds.snapPosition
                )
            }
        }

        /** Converts the launcher version of SplitBounds to the shell version */
        @JvmStatic
        fun convertLauncherSplitBoundsToShell(
            launcherSplitBounds: SplitConfigurationOptions.SplitBounds?
        ): SplitBounds? {
            return if (launcherSplitBounds == null) {
                null
            } else {
                SplitBounds(
                    launcherSplitBounds.leftTopBounds,
                    launcherSplitBounds.rightBottomBounds,
                    launcherSplitBounds.leftTopTaskId,
                    launcherSplitBounds.rightBottomTaskId,
                    launcherSplitBounds.snapPosition
                )
            }
        }
    }
}
