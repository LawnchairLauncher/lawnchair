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

import android.window.DesktopExperienceFlags.DesktopExperienceFlag
import com.android.launcher3.Flags

object TaskbarDesktopExperienceFlags {
    @JvmField
    val enableAltTabKqsOnConnectedDisplays: DesktopExperienceFlag =
        DesktopExperienceFlag(
            Flags::enableAltTabKqsOnConnectedDisplays,
            /* shouldOverrideByDevOption= */ true,
            Flags.FLAG_ENABLE_ALT_TAB_KQS_ON_CONNECTED_DISPLAYS,
        )

    @JvmField
    val enableAltTabKqsFlatenning: DesktopExperienceFlag =
        DesktopExperienceFlag(
            Flags::enableAltTabKqsFlatenning,
            /* shouldOverrideByDevOption= */ true,
            Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING,
        )

    @JvmField
    val enableCustomHeightForAllAppsOnCd: DesktopExperienceFlag =
        DesktopExperienceFlag(
            Flags::enableCustomHeightForAllAppsOnCd,
            /* shouldOverrideByDevOption= */ true,
            Flags.FLAG_ENABLE_CUSTOM_HEIGHT_FOR_ALL_APPS_ON_CD,
        )

    @JvmField
    val enableAutoStashConnectedDisplayTaskbar: DesktopExperienceFlag =
        DesktopExperienceFlag(
            Flags::enableAutoStashConnectedDisplayTaskbar,
            /* shouldOverrideByDevOption= */ true,
            Flags.FLAG_ENABLE_AUTO_STASH_CONNECTED_DISPLAY_TASKBAR,
        )
}
