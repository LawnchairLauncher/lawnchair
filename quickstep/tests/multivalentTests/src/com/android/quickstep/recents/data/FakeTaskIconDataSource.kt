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

import android.graphics.drawable.Drawable
import com.android.quickstep.TaskIconCache.TaskCacheEntry
import com.android.quickstep.task.thumbnail.data.TaskIconDataSource
import com.android.systemui.shared.recents.model.Task
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.yield
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FakeTaskIconDataSource : TaskIconDataSource {

    val taskIdToDrawable: MutableMap<Int, Drawable> =
        (0..10).associateWith { mockCopyableDrawable() }.toMutableMap()
    private val completionPrevented: MutableSet<Int> = mutableSetOf()

    /** Retrieves and sets an icon on [task] from [taskIdToDrawable]. */
    override suspend fun getIcon(task: Task): TaskCacheEntry {
        while (task.key.id in completionPrevented) {
            yield()
        }
        return TaskCacheEntry(
            taskIdToDrawable.getValue(task.key.id),
            "content desc ${task.key.id}",
            "title ${task.key.id}",
        )
    }

    fun preventIconLoad(taskId: Int) {
        completionPrevented.add(taskId)
    }

    fun completeLoadingForTask(taskId: Int) {
        completionPrevented.remove(taskId)
    }

    fun completeLoading() {
        completionPrevented.clear()
    }

    companion object {
        fun mockCopyableDrawable(): Drawable {
            val mutableDrawable = mock<Drawable>()
            val immutableDrawable =
                mock<Drawable>().apply { whenever(mutate()).thenReturn(mutableDrawable) }
            val constantState =
                mock<Drawable.ConstantState>().apply {
                    whenever(newDrawable()).thenReturn(immutableDrawable)
                }
            return mutableDrawable.apply { whenever(this.constantState).thenReturn(constantState) }
        }
    }
}

fun Task.assertHasIconDataFromSource(fakeTaskIconDataSource: FakeTaskIconDataSource) {
    assertThat(icon).isEqualTo(fakeTaskIconDataSource.taskIdToDrawable[key.id])
    assertThat(titleDescription).isEqualTo("content desc ${key.id}")
    assertThat(title).isEqualTo("title ${key.id}")
}
