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

import android.view.SurfaceControl
import android.window.WindowContainerToken
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import com.android.wm.shell.repository.GenericRepository
import com.android.wm.shell.repository.MemoryRepositoryImpl
import javax.inject.Inject

/**
 * Encapsulate the [TaskInfo] information useful for letterboxing in shell.
 */
data class LetterboxTaskInfoState(
    val containerToken: WindowContainerToken,
    val containerLeash: SurfaceControl
)

/**
 * Repository for keeping the reference to the [TaskInfo] data useful to handle letterbox
 * surfaces lifecycle.
 */
@WMSingleton
class LetterboxTaskInfoRepository @Inject constructor(
) : GenericRepository<Int, LetterboxTaskInfoState> by MemoryRepositoryImpl(
    logger = { msg -> ProtoLog.v(WM_SHELL_APP_COMPAT, "%s: %s", "TaskInfoMemoryRepository", msg) }
)
