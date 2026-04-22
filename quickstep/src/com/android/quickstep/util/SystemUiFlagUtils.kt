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
package com.android.quickstep.util

import com.android.systemui.shared.system.QuickStepContract
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags

/** Util class for holding and checking [SystemUiStateFlags] masks. */
object SystemUiFlagUtils {
    const val KEYGUARD_SYSUI_FLAGS =
        (QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING or
            QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING or
            QuickStepContract.SYSUI_STATE_DEVICE_DOZING or
            QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED or
            QuickStepContract.SYSUI_STATE_HOME_DISABLED or
            QuickStepContract.SYSUI_STATE_BACK_DISABLED or
            QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED or
            QuickStepContract.SYSUI_STATE_WAKEFULNESS_MASK)

    // If any of these SysUi flags (via QuickstepContract) is set, the device to be considered
    // locked.
    private const val MASK_ANY_SYSUI_LOCKED =
        (QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING or
            QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING or
            QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED or
            QuickStepContract.SYSUI_STATE_DEVICE_DREAMING)

    /**
     * Returns true iff the given [SystemUiStateFlags] imply that the device is considered locked.
     */
    @JvmStatic
    fun isLocked(@SystemUiStateFlags flags: Long): Boolean {
        return hasAnyFlag(flags, MASK_ANY_SYSUI_LOCKED) &&
            !hasAnyFlag(flags, QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_GOING_AWAY)
    }

    /**
     * Taskbar is hidden whenever the device is dreaming. The dreaming state includes the
     * interactive dreams, AoD, screen off. Since the SYSUI_STATE_DEVICE_DREAMING only kicks in when
     * the device is asleep, the second condition extends ensures that the transition from and to
     * the WAKEFULNESS_ASLEEP state also hide the taskbar, and improves the taskbar hide/reveal
     * animation timings. The Taskbar can show when dreaming if the glanceable hub is showing on
     * top.
     */
    @JvmStatic
    fun isTaskbarHidden(@SystemUiStateFlags flags: Long): Boolean {
        return ((hasAnyFlag(flags, QuickStepContract.SYSUI_STATE_DEVICE_DREAMING) &&
            !hasAnyFlag(flags, QuickStepContract.SYSUI_STATE_COMMUNAL_HUB_SHOWING)) ||
            (flags and QuickStepContract.SYSUI_STATE_WAKEFULNESS_MASK) !=
                QuickStepContract.WAKEFULNESS_AWAKE)
    }

    private fun hasAnyFlag(@SystemUiStateFlags flags: Long, flagMask: Long): Boolean {
        return (flags and flagMask) != 0L
    }
}
