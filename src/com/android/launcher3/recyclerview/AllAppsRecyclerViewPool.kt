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
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.InflateException
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PROTECTED
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.android.launcher3.BubbleTextView
import com.android.launcher3.BuildConfig
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.VIEW_PREINFLATION_EXECUTOR
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext

const val PREINFLATE_ICONS_ROW_COUNT = 4
const val EXTRA_ICONS_COUNT = 2

/**
 * An [RecycledViewPool] that preinflates app icons ([ViewHolder] of [BubbleTextView]) of all apps
 * [RecyclerView]. The view inflation will happen on background thread and inflated [ViewHolder]s
 * will be added to [RecycledViewPool] on main thread.
 */
class AllAppsRecyclerViewPool<T> : RecycledViewPool() where T : Context, T : ActivityContext {

    var hasWorkProfile = false
    @VisibleForTesting(otherwise = PROTECTED)
    var mCancellableTask: CancellableTask<List<ViewHolder>>? = null

    companion object {
        private const val TAG = "AllAppsRecyclerViewPool"
        private const val NULL_LAYOUT_MANAGER_ERROR_STRING =
            "activeRv's layoutManager should not be null"
    }

    /**
     * Preinflate app icons. If all apps RV cannot be scrolled down, we don't need to preinflate.
     */
    fun preInflateAllAppsViewHolders(context: T) {
        val appsView = context.appsView ?: return
        val activeRv: RecyclerView = appsView.activeRecyclerView ?: return
        val preInflateCount = getPreinflateCount(context)
        if (preInflateCount <= 0) {
            return
        }

        if (activeRv.layoutManager == null) {
            if (false) {
                throw IllegalStateException(NULL_LAYOUT_MANAGER_ERROR_STRING)
            } else {
                Log.e(TAG, NULL_LAYOUT_MANAGER_ERROR_STRING)
            }
            return
        }

        // Create a separate context dedicated for all apps preinflation thread. The goal is to
        // create a separate AssetManager obj internally to avoid lock contention with
        // AssetManager obj that is associated with the launcher context on the main thread.
        val allAppsPreInflationContext =
            ContextThemeWrapper(context, Themes.getActivityThemeRes(context)).apply {
                applyOverrideConfiguration(context.resources.configuration)
            }

        // Because we perform onCreateViewHolder() on worker thread, we need a separate
        // adapter/inflator object as they are not thread-safe. Note that the adapter
        // just need to perform onCreateViewHolder(parent, VIEW_TYPE_ICON) so it doesn't need
        // data source information.
        val adapter: RecyclerView.Adapter<BaseAllAppsAdapter.ViewHolder> =
            object :
                BaseAllAppsAdapter<T>(
                    context,
                    context.appsView.layoutInflater.cloneInContext(allAppsPreInflationContext),
                    null,
                    null,
                ) {
                override fun setAppsPerRow(appsPerRow: Int) = Unit

                override fun getLayoutManager(): RecyclerView.LayoutManager? = null
            }

        preInflateAllAppsViewHolders(
            adapter,
            BaseAllAppsAdapter.VIEW_TYPE_ICON,
            activeRv,
            preInflateCount,
        ) {
            getPreinflateCount(context)
        }
    }

    @VisibleForTesting(otherwise = PROTECTED)
    fun preInflateAllAppsViewHolders(
        adapter: RecyclerView.Adapter<*>,
        viewType: Int,
        activeRv: RecyclerView,
        preInflationCount: Int,
        preInflationCountProvider: () -> Int,
    ) {
        if (preInflationCount <= 0) {
            return
        }
        mCancellableTask?.cancel()
        var task: CancellableTask<List<ViewHolder>>? = null
        task =
            CancellableTask(
                {
                    val list: ArrayList<ViewHolder> = ArrayList()
                    for (i in 0 until preInflationCount) {
                        if (task?.canceled == true) {
                            break
                        }
                        // If activeRv's layout manager has been reset to null on main thread, skip
                        // the preinflation as we cannot generate correct LayoutParams
                        if (activeRv.layoutManager == null) {
                            list.clear()
                            break
                        }
                        try {
                            list.add(adapter.createViewHolder(activeRv, viewType))
                        } catch (e: InflateException) {
                            list.clear()
                            // It's still possible for UI thread to set activeRv's layout manager to
                            // null and we should break the loop and cancel the preinflation.
                            break
                        }
                    }
                    list
                },
                MAIN_EXECUTOR,
                { viewHolders ->
                    // Run preInflationCountProvider again as the needed VH might have changed
                    val newPreInflationCount = preInflationCountProvider.invoke()
                    for (i in 0 until minOf(viewHolders.size, newPreInflationCount)) {
                        putRecycledView(viewHolders[i])
                    }
                },
            )
        mCancellableTask = task
        VIEW_PREINFLATION_EXECUTOR.execute(mCancellableTask)
    }

    /**
     * When clearing [RecycledViewPool], we should also abort pre-inflation tasks. This will make
     * sure we don't inflate app icons after DeviceProfile has changed.
     */
    override fun clear() {
        super.clear()
        mCancellableTask?.cancel()
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
    fun getPreinflateCount(context: T): Int {
        var targetPreinflateCount =
            PREINFLATE_ICONS_ROW_COUNT * context.deviceProfile.numShownAllAppsColumns +
                EXTRA_ICONS_COUNT
        val grid = ActivityContext.lookupContext<T>(context).deviceProfile
        targetPreinflateCount += grid.maxAllAppsRowCount * grid.numShownAllAppsColumns
        if (hasWorkProfile) {
            targetPreinflateCount *= 2
        }
        val existingPreinflateCount = getRecycledViewCount(BaseAllAppsAdapter.VIEW_TYPE_ICON)
        return targetPreinflateCount - existingPreinflateCount
    }
}
