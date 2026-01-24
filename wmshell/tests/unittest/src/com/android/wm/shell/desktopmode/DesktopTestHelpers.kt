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
import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.content.ComponentName
import android.graphics.Rect
import android.view.Display.DEFAULT_DISPLAY
import com.android.wm.shell.MockToken
import com.android.wm.shell.TestRunningTaskInfoBuilder

object DesktopTestHelpers {
    /** Create a task that has windowing mode set to [WINDOWING_MODE_FREEFORM] */
    fun createFreeformTask(
        displayId: Int = DEFAULT_DISPLAY,
        bounds: Rect? = null,
    ): RunningTaskInfo =
        TestRunningTaskInfoBuilder()
            .setDisplayId(displayId)
            .setParentTaskId(displayId)
            .setToken(MockToken().token())
            .setActivityType(ACTIVITY_TYPE_STANDARD)
            .setWindowingMode(WINDOWING_MODE_FREEFORM)
            .setLastActiveTime(100)
            .setUserId(DEFAULT_USER_ID)
            .apply { bounds?.let { setBounds(it) } }
            .build()

    fun createPinnedTask(displayId: Int = DEFAULT_DISPLAY, bounds: Rect? = null): RunningTaskInfo =
        TestRunningTaskInfoBuilder()
            .setDisplayId(displayId)
            .setParentTaskId(displayId)
            .setToken(MockToken().token())
            .setActivityType(ACTIVITY_TYPE_STANDARD)
            .setWindowingMode(WINDOWING_MODE_PINNED)
            .setLastActiveTime(100)
            .apply { bounds?.let { setBounds(it) } }
            .build()

    fun createFullscreenTaskBuilder(displayId: Int = DEFAULT_DISPLAY): TestRunningTaskInfoBuilder =
        TestRunningTaskInfoBuilder()
            .setDisplayId(displayId)
            .setToken(MockToken().token())
            .setActivityType(ACTIVITY_TYPE_STANDARD)
            .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
            .setUserId(DEFAULT_USER_ID)
            .setLastActiveTime(100)

    /** Create a task that has windowing mode set to [WINDOWING_MODE_FULLSCREEN] */
    fun createFullscreenTask(displayId: Int = DEFAULT_DISPLAY): RunningTaskInfo =
        createFullscreenTaskBuilder(displayId).build()

    /** Create a task that has windowing mode set to [WINDOWING_MODE_MULTI_WINDOW] */
    fun createSplitScreenTask(displayId: Int = DEFAULT_DISPLAY): RunningTaskInfo =
        TestRunningTaskInfoBuilder()
            .setDisplayId(displayId)
            .setToken(MockToken().token())
            .setActivityType(ACTIVITY_TYPE_STANDARD)
            .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
            .setUserId(DEFAULT_USER_ID)
            .setLastActiveTime(100)
            .build()

    fun createHomeTask(
        displayId: Int = DEFAULT_DISPLAY,
        userId: Int = DEFAULT_USER_ID,
    ): RunningTaskInfo =
        TestRunningTaskInfoBuilder()
            .setDisplayId(displayId)
            .setToken(MockToken().token())
            .setActivityType(ACTIVITY_TYPE_HOME)
            .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
            .setUserId(userId)
            .setLastActiveTime(100)
            .build()

    /**
     * Create a new System Modal task builder, i.e. a builder for a task with only transparent
     * activities.
     */
    fun createSystemModalTaskBuilder(displayId: Int = DEFAULT_DISPLAY): TestRunningTaskInfoBuilder =
        createFullscreenTaskBuilder(displayId).setActivityStackTransparent(true).setNumActivities(1)

    /** Create a new System Modal task, i.e. a task with only transparent activities. */
    fun createSystemModalTask(displayId: Int = DEFAULT_DISPLAY): RunningTaskInfo =
        createSystemModalTaskBuilder(displayId).build()

    /** Create a new System Modal task with a base Activity. */
    fun createSystemModalTaskWithBaseActivity() =
        createSystemModalTask().apply {
            baseActivity = ComponentName("com.test.dummypackage", "TestClass")
        }

    const val DEFAULT_USER_ID = 10
}
