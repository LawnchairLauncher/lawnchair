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

package com.android.launcher3

import com.android.launcher3.Flags.refactorTaskbarUiState

/** Temporary util class to get [LauncherUiState] properties. */
object LauncherUiStateUtil {

    @JvmStatic
    fun getDeviceProfile(
        launcher: LauncherInteractor,
        launcherUiState: LauncherUiState,
    ): DeviceProfile =
        if (refactorTaskbarUiState()) {
            val ret: DeviceProfile = launcherUiState.deviceProfileRef.value!!
            check(!(BuildConfigs.IS_STUDIO_BUILD && ret !== launcher.getDeviceProfile())) {
                "getDeviceProfile() doesn't match"
            }
            ret
        } else {
            launcher.getDeviceProfile()
        }

    @JvmStatic
    fun isSplitSelectActive(
        launcher: LauncherInteractor,
        launcherUiState: LauncherUiState,
    ): Boolean =
        if (refactorTaskbarUiState()) {
            val ret: Boolean = launcherUiState.isSplitSelectActiveRef.value
            if (BuildConfigs.IS_STUDIO_BUILD && ret != launcher.isSplitSelectActive()) {
                throw IllegalStateException("isSplitSelectionActive() doesn't match")
            }
            ret
        } else {
            launcher.isSplitSelectActive()
        }

    @JvmStatic
    fun getLauncherState(
        launcher: LauncherInteractor,
        launcherUiState: LauncherUiState,
    ): LauncherState =
        if (refactorTaskbarUiState()) {
            val ret: LauncherState = launcherUiState.launcherStateRef.value
            if (BuildConfigs.IS_STUDIO_BUILD && ret !== launcher.getState()) {
                throw IllegalStateException("launcher state doesn't match")
            }
            ret
        } else {
            launcher.getState()
        }

    @JvmStatic
    fun getTaskbarAlignmentChannelAlpha(
        launcher: LauncherInteractor,
        launcherUiState: LauncherUiState,
    ): Float =
        if (refactorTaskbarUiState()) {
            val ret: Float = launcherUiState.taskbarAlignmentChannelAlpha.value
            if (BuildConfigs.IS_STUDIO_BUILD && ret != launcher.getTaskbarAlignmentChannelAlpha()) {
                throw IllegalStateException("taskbarAlignmentChannelAlpha doesn't match")
            }
            ret
        } else {
            launcher.getTaskbarAlignmentChannelAlpha()
        }
}
