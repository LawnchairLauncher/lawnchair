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

package com.android.quickstep.recents.domain.usecase

import android.os.UserHandle
import com.android.launcher3.Flags.enableRefactorDigitalWellbeingToast
import com.android.quickstep.recents.data.RecentTasksRepository
import com.android.quickstep.recents.domain.model.TaskModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetTaskUseCase(
    private val tasksRepository: RecentTasksRepository,
    private val getRemainingAppTimerDurationUseCase: GetRemainingAppTimerDurationUseCase,
) {
    operator fun invoke(taskId: Int): Flow<TaskModel?> =
        tasksRepository.getTaskDataById(taskId).map { task ->
            if (task == null) return@map null

            val packageName = task.topComponent.packageName

            // TODO(b/405359794): If getTask for a single task ends up being called multiple
            //  times by the UI, explore alternatives of loading the timer info only once.
            val remainingDuration =
                if (enableRefactorDigitalWellbeingToast()) {
                    getRemainingAppTimerDurationUseCase(
                        packageName = packageName,
                        userHandle = UserHandle(task.key.userId),
                    )
                } else {
                    null
                }

            TaskModel(
                id = task.key.id,
                packageName = packageName,
                title = task.title,
                titleDescription = task.titleDescription,
                icon = task.icon,
                thumbnail = task.thumbnail,
                backgroundColor = task.colorBackground,
                isLocked = task.isLocked,
                isMinimized = task.isMinimized,
                remainingAppDuration = remainingDuration,
            )
        }
}
