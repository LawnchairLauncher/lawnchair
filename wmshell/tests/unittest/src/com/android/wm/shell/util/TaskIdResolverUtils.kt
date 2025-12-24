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

package com.android.wm.shell.util

import com.android.wm.shell.compatui.letterbox.lifecycle.TaskIdResolver

@DslMarker
annotation class TaskIdResolverTagMarker

@TaskIdResolverTagMarker
class TaskIdResolverTestContext(
    private val taskIdResolver: TaskIdResolver
) : BaseRunningTaskInfoTestContext() {

    fun verifyLetterboxTaskId(consumer: (Int) -> Unit) {
        consumer(taskIdResolver.getLetterboxTaskId(taskInfo))
    }
}

/**
 * Function to run tests for the different [TaskIdResolver] implementations.
 */
fun testTaskIdResolver(
    factory: () -> TaskIdResolver,
    init: TaskIdResolverTestContext.() -> Unit
): TaskIdResolverTestContext {
    val testContext = TaskIdResolverTestContext(factory())
    testContext.init()
    return testContext
}
