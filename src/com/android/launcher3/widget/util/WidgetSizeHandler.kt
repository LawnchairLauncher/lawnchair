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

package com.android.launcher3.widget.util

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.util.Executors
import javax.inject.Inject

/** Helper class for handling widget updates */
open class WidgetSizeHandler @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Updates the widget size range if it is not currently the same. This makes two binder calls,
     * one for getting the existing options, [AppWidgetManager.getAppWidgetOptions] and if it
     * doesn't match the expected value, another call to update it,
     * [AppWidgetManager.updateAppWidgetOptions].
     *
     * LC-Note: Float spans for subgrid positioning. Also skips widget IDs that aren't bound, which
     * takes one more binder call.
     *
     * Note that updating the options is a costly call as it wakes up the provider process and
     * causes a full widget update, hence two binder calls are preferable over unnecessarily
     * updating the widget options.
     */
    open fun updateSizeRangesAsync(
        widgetId: Int,
        info: AppWidgetProviderInfo,
        spanX: Float,
        spanY: Float,
    ) {
        Executors.UI_HELPER_EXECUTOR.execute {
            updateSizeRanges(widgetId, info, spanX, spanY)
        }
    }

    // LC-Note: Split out of updateSizeRangesAsync, skips widget IDs without a bound provider.
    internal fun updateSizeRanges(widgetId: Int, info: AppWidgetProviderInfo, spanX: Float, spanY: Float) {
        val widgetManager = AppWidgetManager.getInstance(context)
        // Imported placeholders have allocated IDs but no provider binding yet. Some Android
        // builds crash in AppWidgetService when updating options for those IDs. Binding supplies
        // the initial options, so skip the update here; nothing retries it, the next size update
        // after the widget is bound (load, resize) sends the options.
        if (widgetId <= 0 || widgetManager.getAppWidgetInfo(widgetId) == null) return
        val sizeOptions = WidgetSizes.getWidgetSizeOptions(context, info.provider, spanX, spanY) ?: return
        if (needsSizeUpdate(widgetManager.getAppWidgetOptions(widgetId), sizeOptions)) {
            widgetManager.updateAppWidgetOptions(widgetId, sizeOptions)
        }
    }

    companion object {

        // LC-Note: Unlike AOSP, also compare the min/max size options, not only the size list.
        internal fun needsSizeUpdate(current: Bundle, desired: Bundle): Boolean =
            current.getWidgetSizeList() != desired.getWidgetSizeList() ||
                listOf(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                    AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                ).any { !current.containsKey(it) || current.getInt(it) != desired.getInt(it) }

        fun Bundle.getWidgetSizeList() = getParcelableArrayList<SizeF>(OPTION_APPWIDGET_SIZES)
    }
}
