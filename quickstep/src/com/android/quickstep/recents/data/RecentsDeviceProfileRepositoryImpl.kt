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

import com.android.quickstep.views.RecentsViewContainer
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus

/**
 * Repository for shrink down version of [com.android.launcher3.DeviceProfile] that only contains
 * data related to Recents.
 */
class RecentsDeviceProfileRepositoryImpl(private val container: RecentsViewContainer) :
    RecentsDeviceProfileRepository {

    override fun getRecentsDeviceProfile() =
        with(container.deviceProfile) {
            RecentsDeviceProfile(
                isLargeScreen = deviceProperties.isTablet,
                canEnterDesktopMode = DesktopModeStatus.canEnterDesktopMode(container.asContext()),
            )
        }
}
