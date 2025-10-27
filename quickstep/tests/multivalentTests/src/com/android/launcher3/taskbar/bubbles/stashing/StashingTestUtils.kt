/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar.bubbles.stashing

class ImmediateAction : BubbleStashController.ControllersAfterInitAction {
    override fun runAfterInit(action: Runnable) = action.run()
}

class DefaultDimensionsProvider(
    private val taskBarBottomSpace: Int = TASKBAR_BOTTOM_SPACE,
    private val taskBarHeight: Int = TASKBAR_HEIGHT,
) : BubbleStashController.TaskbarHotseatDimensionsProvider {
    override fun getTaskbarBottomSpace(): Int = taskBarBottomSpace

    override fun getTaskbarHeight(): Int = taskBarHeight

    companion object {
        const val TASKBAR_BOTTOM_SPACE = 0
        const val TASKBAR_HEIGHT = 110
    }
}
