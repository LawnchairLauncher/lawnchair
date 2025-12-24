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
import android.app.ActivityTaskManager
import android.content.res.Configuration
import android.view.SurfaceControl
import android.window.WindowContainerToken
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.compatui.letterbox.lifecycle.isALeafTask
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import com.android.wm.shell.repository.GenericRepository
import com.android.wm.shell.repository.MemoryRepositoryImpl
import javax.inject.Inject

/** Encapsulate the [TaskInfo] information useful for letterboxing in shell. */
data class LetterboxTaskInfoState(
    val containerToken: WindowContainerToken? = null,
    val containerLeash: SurfaceControl,
    val taskId: Int = ActivityTaskManager.INVALID_TASK_ID,
    val parentTaskId: Int = ActivityTaskManager.INVALID_TASK_ID,
    val configuration: Configuration,
)

/**
 * Repository for keeping the reference to the [TaskInfo] data useful to handle letterbox surfaces
 * lifecycle.
 */
@WMSingleton
class LetterboxTaskInfoRepository @Inject constructor() :
    GenericRepository<Int, LetterboxTaskInfoState> by MemoryRepositoryImpl(
        logger = { msg ->
            ProtoLog.v(WM_SHELL_APP_COMPAT, "%s: %s", "TaskInfoMemoryRepository", msg)
        }
    )

/**
 * We assume that only leaf Tasks will be present in the [LetterboxTaskInfoRepository]. This method
 * is responsible for keeping this invariant always true.
 */
fun LetterboxTaskInfoRepository.updateTaskLeafState(
    taskInfo: RunningTaskInfo,
    leash: SurfaceControl,
) {
    if (taskInfo.isALeafTask) {
        insert(
            key = taskInfo.taskId,
            item =
                LetterboxTaskInfoState(
                    containerToken = taskInfo.token,
                    containerLeash = leash,
                    parentTaskId = taskInfo.parentTaskId,
                    taskId = taskInfo.taskId,
                    configuration = taskInfo.configuration,
                ),
            overrideIfPresent = true,
        )
    } else {
        delete(taskInfo.taskId)
    }
}

/** Updates the configuration given the [TaskInfo] and the leash. */
fun LetterboxTaskInfoRepository.updateConfiguration(
    taskInfo: RunningTaskInfo,
    leash: SurfaceControl,
) {
    insert(
        taskInfo.taskId,
        LetterboxTaskInfoState(taskInfo.token, leash, configuration = taskInfo.configuration),
        overrideIfPresent = true,
    )
}
