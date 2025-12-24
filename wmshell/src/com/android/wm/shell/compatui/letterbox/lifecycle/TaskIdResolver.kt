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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.app.TaskInfo
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoRepository
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.windowdecor.extension.isMultiWindow
import javax.inject.Inject

/**
 * The id for the Task to use in the Letterbox Lifecycle is not always the one received in
 * [TaskInfo]. Sometimes (e.g. Split Screen) the [Change] received are related to a parent [Task].
 * This class encapsulate the logic to find the right id for the [Task] used in the [Change].
 */
@WMSingleton
class TaskIdResolver
@Inject
constructor(private val letterboxTaskInfoRepository: LetterboxTaskInfoRepository) {

    /** @return The id for the Task to consider for the Letterbox bounds update. */
    fun getLetterboxTaskId(taskInfo: TaskInfo): Int {
        // We use the taskId itself if in the repository.
        val candidateId = taskInfo.taskId
        letterboxTaskInfoRepository.find(candidateId)?.let { item ->
            // In this case there's an item for the candidateId which means that it's an
            // eligible Task.
            return candidateId
        }
        // In this case the candidateId is not present. In case of split screen this happens when
        // the Change contains the parent of the Task with letterbox surfaces and not the Task
        // itself.
        if (taskInfo.isMultiWindow) {
            letterboxTaskInfoRepository
                .find { key, item -> item.parentTaskId == candidateId }
                .let { items ->
                    if (items.isNotEmpty()) {
                        // In the repository there's a Task that is eligible for letterbox surfaces
                        // whose parent has id equals to candidateId. In this case the item id
                        // will be the one. This should be exactly 1.
                        return items.first().taskId
                    }
                }
        }
        // Here the id is not present and there's no task whose parent is eligible for letterbox.
        return candidateId
    }
}
