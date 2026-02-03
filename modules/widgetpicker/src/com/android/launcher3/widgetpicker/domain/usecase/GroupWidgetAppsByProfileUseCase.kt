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

package com.android.launcher3.widgetpicker.domain.usecase

import android.os.UserHandle
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfile
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfiles
import javax.inject.Inject

/** A helper class that groups widget apps by user profiles. */
class GroupWidgetAppsByProfileUseCase @Inject constructor() {
    operator fun invoke(
        widgetApps: List<WidgetApp>,
        userProfiles: WidgetUserProfiles,
        workProfileUser: UserHandle?,
    ): Map<WidgetUserProfile, List<WidgetApp>> {
        val personalProfile = userProfiles.personal
        val workProfile = userProfiles.work

        return when {
            (workProfile != null && workProfileUser != null) -> {
                val (workWidgetApps, personalWidgetApps) =
                    widgetApps.partition { it.id.userHandle == workProfileUser }
                val finalWorkWidgetApps =
                    (workWidgetApps.takeIf { !workProfile.paused } ?: emptyList())

                mapOf(personalProfile to personalWidgetApps, workProfile to finalWorkWidgetApps)
            }

            else -> mapOf(personalProfile to widgetApps)
        }
    }
}
