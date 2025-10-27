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

package com.android.quickstep.recents.domain.usecase

import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import com.android.launcher3.util.SystemUiController.FLAG_DARK_NAV
import com.android.launcher3.util.SystemUiController.FLAG_DARK_STATUS
import com.android.launcher3.util.SystemUiController.FLAG_LIGHT_NAV
import com.android.launcher3.util.SystemUiController.FLAG_LIGHT_STATUS
import com.android.systemui.shared.recents.model.ThumbnailData

/** UseCase to calculate flags for status bar and navigation bar */
class GetSysUiStatusNavFlagsUseCase {
    operator fun invoke(thumbnailData: ThumbnailData?): Int {
        if (thumbnailData == null) return 0
        val thumbnailAppearance = thumbnailData.appearance
        var flags = 0
        flags =
            flags or
                if (thumbnailAppearance and APPEARANCE_LIGHT_STATUS_BARS != 0) FLAG_LIGHT_STATUS
                else FLAG_DARK_STATUS
        flags =
            flags or
                if (thumbnailAppearance and APPEARANCE_LIGHT_NAVIGATION_BARS != 0) FLAG_LIGHT_NAV
                else FLAG_DARK_NAV
        return flags
    }
}
