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

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.SizeF
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.dagger.LauncherComponentProvider.get
import com.android.launcher3.util.Executors
import java.util.concurrent.Executor
import javax.inject.Inject

/** Helper class for handling widget updates */
open class WidgetSizeHandler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val idp: InvariantDeviceProfile,
) {

    /**
     * Updates the widget size range if it is not currently the same. This makes two binder calls,
     * one for getting the existing options, [AppWidgetManager.getAppWidgetOptions] and if it
     * doesn't match the expected value, another call to update it,
     * [AppWidgetManager.updateAppWidgetOptions].
     *
     * Note that updating the options is a costly call as it wakes up the provider process and
     * causes a full widget update, hence two binder calls are preferable over unnecessarily
     * updating the widget options.
     */
    @JvmOverloads
    open fun updateSizeRangesAsync(
        widgetId: Int,
        spanX: Int,
        spanY: Int,
        executor: Executor = Executors.UI_HELPER_EXECUTOR,
    ) {
        if (widgetId <= 0) return
        executor.execute {
            val widgetManager = AppWidgetManager.getInstance(context)
            val sizeOptions = getWidgetSizeOptions(spanX, spanY)
            if (
                sizeOptions.getWidgetSizeList() !=
                    widgetManager.getAppWidgetOptions(widgetId).getWidgetSizeList()
            )
                widgetManager.updateAppWidgetOptions(widgetId, sizeOptions)
        }
    }

    /** Returns the bundle to be used as the default options for a widget with provided size. */
    fun getWidgetSizeOptions(spanX: Int, spanY: Int): Bundle {
        val density = context.resources.displayMetrics.density
        val paddedSizes =
            idp.supportedProfiles.mapTo(ArrayList()) {
                val widgetSizePx = WidgetSizes.getWidgetSizePx(it, spanX, spanY)
                SizeF(widgetSizePx.width / density, widgetSizePx.height / density)
            }

        val rect = getMinMaxSizes(paddedSizes)
        val options = Bundle()
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, rect.left)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, rect.top)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, rect.right)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, rect.bottom)
        options.putParcelableArrayList(OPTION_APPWIDGET_SIZES, paddedSizes)
        return options
    }

    companion object {

        fun Bundle.getWidgetSizeList() = getParcelableArrayList<SizeF>(OPTION_APPWIDGET_SIZES)

        /**
         * Returns the min and max widths and heights given a list of sizes, in dp.
         *
         * @param sizes List of sizes to get the min/max from.
         * @return A rectangle with the left (resp. top) is used for the min width (resp. height)
         *   and the right (resp. bottom) for the max. The returned rectangle is set with 0s if the
         *   list is empty.
         */
        private fun getMinMaxSizes(sizes: List<SizeF>): Rect {
            if (sizes.isEmpty()) return Rect()

            val first = sizes[0]
            val result =
                Rect(
                    first.width.toInt(),
                    first.height.toInt(),
                    first.width.toInt(),
                    first.height.toInt(),
                )
            for (i in 1..<sizes.size) {
                result.union(sizes[i].width.toInt(), sizes[i].height.toInt())
            }
            return result
        }

        /**
         * Updates a widget with size, [spanX], [spanY]
         *
         * On Android S+, it also updates the given widget with a list of sizes derived from
         * [spanX], [spanY] in all supported device profiles.
         */
        @JvmStatic
        fun AppWidgetHostView.updateSizeRanges(spanX: Int, spanY: Int) =
            context.appComponent.widgetSizeHandler.updateSizeRangesAsync(appWidgetId, spanX, spanY)
    }
}
