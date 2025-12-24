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

import android.util.Log
import android.view.ContextThemeWrapper
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import androidx.annotation.VisibleForTesting.Companion.PROTECTED
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.android.launcher3.BubbleTextView
import com.android.launcher3.BuildConfig
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.AsyncObjectAllocator
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import javax.inject.Inject
import javax.inject.Named

/**
 * An [RecycledViewPool] that preinflates app icons ([ViewHolder] of [BubbleTextView]) of all apps
 * [RecyclerView]. The view inflation will happen on background thread and inflated [ViewHolder]s
 * will be added to [RecycledViewPool] on main thread.
 */
@ActivityContextSingleton
class AllAppsRecyclerViewPool
@Inject
constructor(
    private val activityContext: ActivityContext,
    private val allAppsStore: AllAppsStore,
    @Named(PRELOAD_ALL_APPS_DAGGER_KEY) private val preInflateAllApps: Boolean,
    private val userCache: UserCache,
) : RecycledViewPool() {

    // Initialized to RecycledViewPool.DEFAULT_MAX_SCRAP
    private var targetPoolSize: Int = 0

    @VisibleForTesting(otherwise = PROTECTED) var mCancellableTask: SafeCloseable? = null

    init {
        // This class is a activity level singleton, so no need to remove the listener
        activityContext.addOnDeviceProfileChangeListener { updatePoolSize() }
        // Update pool size, in-case the work-profile availability changes
        allAppsStore.addUpdateListener { updatePoolSize() }
        updatePoolSize()
    }

    /**
     * After testing on phone, foldable and tablet, we found [PREINFLATE_ICONS_ROW_COUNT] rows of
     * app icons plus [EXTRA_ICONS_COUNT] is the magic minimal count of app icons to preinflate to
     * suffice fast scrolling.
     */
    private fun updatePoolSize() {
        val grid = activityContext.deviceProfile
        var targetCount =
            EXTRA_ICONS_COUNT +
                (PREINFLATE_ICONS_ROW_COUNT + grid.maxAllAppsRowCount) * grid.numShownAllAppsColumns

        // Double the count if there is a work tab
        if (allAppsStore.apps.any { userCache.getUserInfo(it.user).isWork }) {
            targetCount *= 2
        }
        targetPoolSize = targetCount
        setMaxRecycledViews(BaseAllAppsAdapter.VIEW_TYPE_ICON, targetPoolSize)
        if (preInflateAllApps) {
            schedulePreInflation()
        }
    }

    /**
     * Preinflate app icons. If all apps RV cannot be scrolled down, we don't need to preinflate.
     */
    private fun schedulePreInflation() {
        val appsView = activityContext.appsView ?: return
        val activeRv: RecyclerView = appsView.activeRecyclerView ?: return
        val preInflateCount = getPreInflateCount()
        if (preInflateCount <= 0) return

        if (activeRv.layoutManager == null) {
            if (false) {
                throw IllegalStateException(NULL_LAYOUT_MANAGER_ERROR_STRING)
            } else {
                Log.e(TAG, NULL_LAYOUT_MANAGER_ERROR_STRING)
            }
            return
        }

        val context = activityContext.asContext()

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
                BaseAllAppsAdapter(
                    activityContext,
                    appsView.layoutInflater.cloneInContext(allAppsPreInflationContext),
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
            getPreInflateCount()
        }
    }

    @VisibleForTesting(otherwise = PRIVATE)
    inline fun preInflateAllAppsViewHolders(
        adapter: RecyclerView.Adapter<*>,
        viewType: Int,
        activeRv: RecyclerView,
        preInflationCount: Int,
        crossinline preInflationCountProvider: () -> Int,
    ) {
        if (preInflationCount <= 0) {
            return
        }
        mCancellableTask?.close()
        mCancellableTask =
            AsyncObjectAllocator.allocate(
                count = preInflationCount,
                factory = {
                    // If activeRv's layout manager has been reset to null on main thread, skip
                    // the preinflation as we cannot generate correct LayoutParams
                    // It's still possible for UI thread to set activeRv's layout manager to
                    // null, in which case the allocator will catch any error and cancel.
                    if (activeRv.layoutManager != null) adapter.createViewHolder(activeRv, viewType)
                    else null
                },
                callbackExecutor = MAIN_EXECUTOR,
            ) {
                if (preInflationCountProvider.invoke() > 0) putRecycledView(it)
            }
    }

    /**
     * When clearing [RecycledViewPool], we should also abort pre-inflation tasks. This will make
     * sure we don't inflate app icons after DeviceProfile has changed.
     */
    override fun clear() {
        super.clear()
        mCancellableTask?.close()
    }

    private fun getPreInflateCount(): Int =
        targetPoolSize - getRecycledViewCount(BaseAllAppsAdapter.VIEW_TYPE_ICON)

    companion object {

        private const val TAG = "AllAppsRecyclerViewPool"
        private const val NULL_LAYOUT_MANAGER_ERROR_STRING =
            "activeRv's layoutManager should not be null"

        private const val PREINFLATE_ICONS_ROW_COUNT = 4
        private const val EXTRA_ICONS_COUNT = 2

        const val PRELOAD_ALL_APPS_DAGGER_KEY = "PRELOAD_ALL_APPS"
    }
}
