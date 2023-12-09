/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.recyclerview

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.android.launcher3.BubbleTextView
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.VIEW_PREINFLATION_EXECUTOR
import com.android.launcher3.views.ActivityContext
import java.util.concurrent.Future

const val PREINFLATE_ICONS_ROW_COUNT = 4
const val EXTRA_ICONS_COUNT = 2

/**
 * An [RecycledViewPool] that preinflates app icons ([ViewHolder] of [BubbleTextView]) of all apps
 * [RecyclerView]. The view inflation will happen on background thread and inflated [ViewHolder]s
 * will be added to [RecycledViewPool] on main thread.
 */
class AllAppsRecyclerViewPool<T> : RecycledViewPool() {

    private var future: Future<Void>? = null

    /**
     * Preinflate app icons. If all apps RV cannot be scrolled down, we don't need to preinflate.
     */
    fun <T> preInflateAllAppsViewHolders(context: T) where T : Context, T : ActivityContext {
        val appsView = context.appsView ?: return
        val activeRv: RecyclerView = appsView.activeRecyclerView ?: return
        val preInflateCount = getPreinflateCount(context)
        if (preInflateCount <= 0) {
            return
        }

        // Because we perform onCreateViewHolder() on worker thread, we need a separate
        // adapter/inflator object as they are not thread-safe. Note that the adapter
        // just need to perform onCreateViewHolder(parent, VIEW_TYPE_ICON) so it doesn't need
        // data source information.
        val adapter: RecyclerView.Adapter<BaseAllAppsAdapter.ViewHolder> =
            object : BaseAllAppsAdapter<T>(context, context.appsView.layoutInflater, null, null) {
                override fun setAppsPerRow(appsPerRow: Int) = Unit
                override fun getLayoutManager(): RecyclerView.LayoutManager? = null
            }

        // Inflate view holders on background thread, and added to view pool on main thread.
        future?.cancel(true)
        future =
            VIEW_PREINFLATION_EXECUTOR.submit<Void> {
                val viewHolders =
                    Array(preInflateCount) {
                        adapter.createViewHolder(activeRv, BaseAllAppsAdapter.VIEW_TYPE_ICON)
                    }
                MAIN_EXECUTOR.execute {
                    for (i in 0 until minOf(viewHolders.size, getPreinflateCount(context))) {
                        putRecycledView(viewHolders[i])
                    }
                }
                null
            }
    }

    /**
     * After testing on phone, foldable and tablet, we found [PREINFLATE_ICONS_ROW_COUNT] rows of
     * app icons plus [EXTRA_ICONS_COUNT] is the magic minimal count of app icons to preinflate to
     * suffice fast scrolling.
     *
     * Note that if [FeatureFlags.ALL_APPS_GONE_VISIBILITY] is enabled, we need to preinfate extra
     * app icons in size of one all apps pages, so that opening all apps don't need to inflate app
     * icons.
     */
    fun <T> getPreinflateCount(context: T): Int where T : Context, T : ActivityContext {
        var targetPreinflateCount =
            PREINFLATE_ICONS_ROW_COUNT * context.deviceProfile.numShownAllAppsColumns +
                EXTRA_ICONS_COUNT
        if (FeatureFlags.ALL_APPS_GONE_VISIBILITY.get()) {
            val grid = ActivityContext.lookupContext<T>(context).deviceProfile
            val approxRows =
                Math.ceil((grid.availableHeightPx / grid.allAppsIconSizePx).toDouble()).toInt()
            targetPreinflateCount += (approxRows + 1) * grid.numShownAllAppsColumns
        }
        val existingPreinflateCount = getRecycledViewCount(BaseAllAppsAdapter.VIEW_TYPE_ICON)
        return targetPreinflateCount - existingPreinflateCount
    }
}
