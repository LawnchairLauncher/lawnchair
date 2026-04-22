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

package com.android.launcher3.widgetpicker.repository

import com.android.launcher3.widgetpicker.data.repository.WidgetAppIconsRepository
import com.android.launcher3.widgetpicker.shared.model.AppIcon
import com.android.launcher3.widgetpicker.shared.model.AppIconBadge
import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/** A fake implementation of [WidgetAppIconsRepository] for testing */
class FakeWidgetAppIconsRepository : WidgetAppIconsRepository {
    private val _appIcons = MutableStateFlow(emptyMap<WidgetAppId, WidgetAppIcon>())
    fun seedAppIcons(icons: Map<WidgetAppId, WidgetAppIcon>) {
        _appIcons.update { icons }
    }

    override fun initialize() {}

    override fun getAppIcon(widgetAppId: WidgetAppId): Flow<WidgetAppIcon> = flow {
        emit(
            WidgetAppIcon(
                icon = AppIcon.PlaceHolderAppIcon,
                badge = AppIconBadge.NoBadge
            )
        )

        val appIcon = _appIcons.value[widgetAppId]
        if (appIcon != null) {
            emit(appIcon)
        }
    }

    override fun cleanUp() {
        _appIcons.update { emptyMap() }
    }
}
