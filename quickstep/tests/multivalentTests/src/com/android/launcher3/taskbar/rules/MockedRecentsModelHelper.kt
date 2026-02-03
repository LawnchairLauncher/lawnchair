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

package com.android.launcher3.taskbar.rules

import com.android.quickstep.RecentsModel
import com.android.quickstep.RecentsModel.RecentTasksChangedListener
import com.android.quickstep.TaskIconCache
import com.android.quickstep.TaskThumbnailCache
import com.android.quickstep.util.GroupTask
import java.util.function.BiConsumer
import java.util.function.Consumer
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/** Helper class to mock the [RecentsModel] object in test */
class MockedRecentsModelHelper {
    private val mockIconCache: TaskIconCache = mock()
    private val mockThumbnailCache: TaskThumbnailCache = mock()

    var taskListId = 0
    var recentTasksChangedListener: RecentTasksChangedListener? = null
    var taskRequests: MutableList<(List<GroupTask>) -> Unit> = mutableListOf()

    val mockRecentsModel: RecentsModel = mock {
        on { iconCache } doReturn mockIconCache

        on { thumbnailCache } doReturn mockThumbnailCache

        on { unregisterRecentTasksChangedListener(any()) } doAnswer
            {
                recentTasksChangedListener = null
            }

        on { registerRecentTasksChangedListener(any<RecentTasksChangedListener>()) } doAnswer
            {
                recentTasksChangedListener = it.getArgument<RecentTasksChangedListener>(0)
            }

        on { getTasks(anyOrNull<BiConsumer<List<GroupTask>, Int>>(), anyOrNull()) } doAnswer
            {
                val request = it.getArgument<BiConsumer<List<GroupTask>, Int>?>(0)
                if (request != null) {
                    taskRequests.add { response -> request.accept(response, taskListId) }
                }
                taskListId
            }

        on { getTasks(anyOrNull(), anyOrNull<Consumer<List<GroupTask>>>()) } doAnswer
            {
                val request = it.getArgument<Consumer<List<GroupTask>>?>(1)
                if (request != null) {
                    taskRequests.add { response -> request.accept(response) }
                }
                taskListId
            }

        on { getTasks(anyOrNull()) } doAnswer
            {
                val request = it.getArgument<Consumer<List<GroupTask>>?>(0)
                if (request != null) {
                    taskRequests.add { response -> request.accept(response) }
                }
                taskListId
            }

        on { isTaskListValid(any()) } doAnswer { taskListId == it.getArgument(0) }
    }
}
