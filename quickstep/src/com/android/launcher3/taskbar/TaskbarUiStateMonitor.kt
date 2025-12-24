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

package com.android.launcher3.taskbar

import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Holds and monitors [TaskbarUiState] for each taskbar. */
@LauncherAppSingleton
class TaskbarUiStateMonitor @Inject constructor() {

    private val taskbarUiStateByDisplay = ConcurrentHashMap<Int, TaskbarUiState>()

    fun getTaskbarUiState(displayId: Int): TaskbarUiState =
        taskbarUiStateByDisplay.getOrPut(displayId) { TaskbarUiState() }

    companion object {
        @JvmField
        val INSTANCE: DaggerSingletonObject<TaskbarUiStateMonitor> =
            DaggerSingletonObject<TaskbarUiStateMonitor>(
                LauncherAppComponent::getTaskbarUiStateMonitor
            )
    }
}
