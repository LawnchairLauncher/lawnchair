/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox.state

import android.app.ActivityManager.RunningTaskInfo
import android.view.SurfaceControl
import com.android.window.flags.Flags.appCompatRefactoring
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTaskOrganizer.TaskAppearedListener
import com.android.wm.shell.ShellTaskOrganizer.TaskVanishedListener
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.sysui.ShellInit
import javax.inject.Inject

/**
 * [TaskAppearedListener] and [TaskVanishedListener] implementation to store [TaskInfo] data
 * useful for letterboxing.
 */
@WMSingleton
class LetterboxTaskListenerAdapter @Inject constructor(
    shellInit: ShellInit,
    shellTaskOrganizer: ShellTaskOrganizer,
    private val letterboxTaskInfoRepository: LetterboxTaskInfoRepository
) : TaskVanishedListener, TaskAppearedListener {

    init {
        if (appCompatRefactoring()) {
            shellInit.addInitCallback({
                shellTaskOrganizer.addTaskAppearedListener(this)
                shellTaskOrganizer.addTaskVanishedListener(this)
            }, this)
        }
    }

    override fun onTaskAppeared(
        taskInfo: RunningTaskInfo,
        leash: SurfaceControl
    ) {
        letterboxTaskInfoRepository.insert(
            key = taskInfo.taskId,
            item = LetterboxTaskInfoState(
                containerToken = taskInfo.token,
                containerLeash = leash
            ),
            overrideIfPresent = true
        )
    }

    override fun onTaskVanished(taskInfo: RunningTaskInfo) {
        letterboxTaskInfoRepository.delete(taskInfo.taskId)
    }
}
