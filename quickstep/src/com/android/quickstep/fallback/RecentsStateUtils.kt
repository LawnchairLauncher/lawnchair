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

package com.android.quickstep.fallback

import com.android.launcher3.LauncherState
import com.android.launcher3.testing.shared.TestProtocol

fun RecentsState.toLauncherState(): LauncherState {
    return when (ordinal) {
        RecentsState.DEFAULT_STATE_ORDINAL -> LauncherState.OVERVIEW
        RecentsState.MODAL_TASK_ORDINAL -> LauncherState.OVERVIEW_MODAL_TASK
        RecentsState.BACKGROUND_APP_ORDINAL -> LauncherState.BACKGROUND_APP
        RecentsState.HOME_STATE_ORDINAL -> LauncherState.NORMAL
        RecentsState.BG_LAUNCHER_ORDINAL -> LauncherState.NORMAL
        RecentsState.OVERVIEW_SPLIT_SELECT_ORDINAL -> LauncherState.OVERVIEW_SPLIT_SELECT
        else -> LauncherState.NORMAL
    }
}

fun LauncherState.toRecentsState(): RecentsState {
    return when (ordinal) {
        TestProtocol.OVERVIEW_STATE_ORDINAL -> RecentsState.DEFAULT
        TestProtocol.OVERVIEW_MODAL_TASK_STATE_ORDINAL -> RecentsState.MODAL_TASK
        TestProtocol.BACKGROUND_APP_STATE_ORDINAL -> RecentsState.BACKGROUND_APP
        TestProtocol.NORMAL_STATE_ORDINAL -> RecentsState.HOME
        TestProtocol.OVERVIEW_SPLIT_SELECT_ORDINAL -> RecentsState.OVERVIEW_SPLIT_SELECT
        else -> RecentsState.BG_LAUNCHER
    }
}

fun LauncherState.hasEquivalentRecentsState(): Boolean {
    return when (this) {
        LauncherState.OVERVIEW,
        LauncherState.OVERVIEW_MODAL_TASK,
        LauncherState.BACKGROUND_APP,
        LauncherState.NORMAL,
        LauncherState.OVERVIEW_SPLIT_SELECT -> true
        else -> false
    }
}

fun RecentsState.toLauncherStateOrdinal(): Int = toLauncherState().ordinal
