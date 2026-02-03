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

import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CacheLookupFlag
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.widgetpicker.data.repository.WidgetAppIconsRepository
import com.android.launcher3.widgetpicker.shared.model.AppIcon
import com.android.launcher3.widgetpicker.shared.model.AppIconBadge
import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * An implementation of [WidgetAppIconsRepository] that provides the app icons for the widget picker
 * using the [IconCache].
 */
class WidgetAppIconsRepositoryImpl @Inject constructor(
    private val iconCache: IconCache
) : WidgetAppIconsRepository {
    override fun initialize() {} // nothing to do here.

    override fun getAppIcon(widgetAppId: WidgetAppId) = callbackFlow {
        trySend(WidgetAppIcon(AppIcon.PlaceHolderAppIcon, AppIconBadge.NoBadge))

        val category = widgetAppId.category
        val packageItemInfo = if (category != null) {
            PackageItemInfo(widgetAppId.packageName, category, widgetAppId.userHandle)
        } else {
            PackageItemInfo(widgetAppId.packageName, widgetAppId.userHandle)
        }

        iconCache.updateIconInBackground({ itemInfoWithIcon ->
            itemInfoWithIcon?.let {
                if (itemInfoWithIcon.bitmap.isLowRes) {
                    trySend(
                        WidgetAppIcon(
                            icon = AppIcon.LowResColorIcon(itemInfoWithIcon.bitmap.color),
                            badge = AppIconBadge.NoBadge
                        )
                    )
                } else {
                    trySend(
                        WidgetAppIcon(
                            icon = AppIcon.HighResBitmapIcon(itemInfoWithIcon.bitmap.icon),
                            badge = itemInfoWithIcon.bitmap.getBadgeDrawableInfo()?.let {
                                AppIconBadge.DrawableBadge(
                                    it.drawableRes,
                                    it.colorRes
                                )
                            } ?: AppIconBadge.NoBadge
                        )
                    )
                }
            }
        }, packageItemInfo, CacheLookupFlag.DEFAULT_LOOKUP_FLAG)

        awaitClose()
    }

    override fun cleanUp() {} // nothing to do here.
}
