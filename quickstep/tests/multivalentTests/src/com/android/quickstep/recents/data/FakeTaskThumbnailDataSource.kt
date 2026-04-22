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

package com.android.quickstep.recents.data

import android.graphics.Bitmap
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import kotlinx.coroutines.delay
import org.mockito.kotlin.mock

class FakeTaskThumbnailDataSource : TaskThumbnailDataSource {

    val taskIdToBitmap: MutableMap<Int, Bitmap> =
        (0..10).associateWith { mock<Bitmap>() }.toMutableMap()
    private val completionPrevented: MutableSet<Int> = mutableSetOf()
    private val getThumbnailCalls = mutableMapOf<Int, Int>()

    var highResEnabled = true

    /** Retrieves and sets a thumbnail on [task] from [taskIdToBitmap]. */
    override suspend fun getThumbnail(task: Task): ThumbnailData {
        getThumbnailCalls[task.key.id] = (getThumbnailCalls[task.key.id] ?: 0) + 1

        while (task.key.id in completionPrevented) {
            // yield doesn't work here with an UnconfinedTestDispatcher
            delay(1L)
        }
        return ThumbnailData(
            thumbnail = taskIdToBitmap[task.key.id],
            reducedResolution = !highResEnabled,
        )
    }

    fun getNumberOfGetThumbnailCalls(taskId: Int): Int = getThumbnailCalls[taskId] ?: 0

    fun preventThumbnailLoad(taskId: Int) {
        completionPrevented.add(taskId)
    }

    fun completeLoadingForTask(taskId: Int) {
        completionPrevented.remove(taskId)
    }

    fun completeLoading() {
        completionPrevented.clear()
    }
}
