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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.graphics.Rect
import android.view.Display.DEFAULT_DISPLAY
import com.android.wm.shell.MockToken
import com.android.wm.shell.TestRunningTaskInfoBuilder

class DesktopTestHelpers {
    companion object {
        /** Create a task that has windowing mode set to [WINDOWING_MODE_FREEFORM] */
        @JvmStatic
        @JvmOverloads
        fun createFreeformTask(
                displayId: Int = DEFAULT_DISPLAY,
                bounds: Rect? = null
        ): RunningTaskInfo {
            return TestRunningTaskInfoBuilder()
                    .setDisplayId(displayId)
                    .setToken(MockToken().token())
                    .setActivityType(ACTIVITY_TYPE_STANDARD)
                    .setWindowingMode(WINDOWING_MODE_FREEFORM)
                    .setLastActiveTime(100)
                    .apply { bounds?.let { setBounds(it) }}
                    .build()
        }

        /** Create a task that has windowing mode set to [WINDOWING_MODE_FULLSCREEN] */
        @JvmStatic
        @JvmOverloads
        fun createFullscreenTask(displayId: Int = DEFAULT_DISPLAY): RunningTaskInfo {
            return TestRunningTaskInfoBuilder()
                .setDisplayId(displayId)
                .setToken(MockToken().token())
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .setLastActiveTime(100)
                .build()
        }

        /** Create a task that has windowing mode set to [WINDOWING_MODE_MULTI_WINDOW] */
        @JvmStatic
        @JvmOverloads
        fun createSplitScreenTask(displayId: Int = DEFAULT_DISPLAY): RunningTaskInfo {
            return TestRunningTaskInfoBuilder()
                .setDisplayId(displayId)
                .setToken(MockToken().token())
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .setLastActiveTime(100)
                .build()
        }

        /** Create a new home task */
        @JvmStatic
        @JvmOverloads
        fun createHomeTask(displayId: Int = DEFAULT_DISPLAY): RunningTaskInfo {
            return TestRunningTaskInfoBuilder()
                    .setDisplayId(displayId)
                    .setToken(MockToken().token())
                    .setActivityType(ACTIVITY_TYPE_HOME)
                    .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                    .setLastActiveTime(100)
                    .build()
        }
    }
}