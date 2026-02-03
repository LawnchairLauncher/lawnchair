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

package com.android.wm.shell.desktopmode.education

import android.annotation.IntegerRes
import android.app.ActivityManager.RunningTaskInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings.Secure
import com.android.wm.shell.R
import com.android.wm.shell.desktopmode.CaptionState
import com.android.wm.shell.desktopmode.education.AppHandleEducationController.Companion.FORCE_SHOW_DESKTOP_MODE_EDUCATION
import com.android.wm.shell.desktopmode.education.data.AppHandleEducationDatastoreRepository
import com.android.wm.shell.desktopmode.education.data.WindowingEducationProto
import java.time.Duration

@kotlinx.coroutines.ExperimentalCoroutinesApi
/** Filters incoming app handle education triggers based on set conditions. */
class AppHandleEducationFilter(
    private val context: Context,
    private val appHandleEducationDatastoreRepository: AppHandleEducationDatastoreRepository,
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    suspend fun shouldShowDesktopModeEducation(captionState: CaptionState.AppHeader): Boolean =
        shouldShowDesktopModeEducation(captionState.runningTaskInfo)

    suspend fun shouldShowDesktopModeEducation(captionState: CaptionState.AppHandle): Boolean =
        shouldShowDesktopModeEducation(captionState.runningTaskInfo)

    /**
     * Returns true if conditions to show app handle, enter desktop mode and exit desktop mode
     * education are met based on the app info and usage, returns false otherwise.
     *
     * If [FORCE_SHOW_DESKTOP_MODE_EDUCATION] is true, this method will always return true.
     */
    private suspend fun shouldShowDesktopModeEducation(taskInfo: RunningTaskInfo): Boolean {
        if (FORCE_SHOW_DESKTOP_MODE_EDUCATION) return true

        val focusAppPackageName = taskInfo.topActivityInfo?.packageName ?: return false
        val windowingEducationProto =
            appHandleEducationDatastoreRepository.windowingEducationProto()

        return isFocusAppInAllowlist(focusAppPackageName) &&
            !isOtherEducationShowing() &&
            hasSufficientTimeSinceSetup() &&
            hasMinAppUsage(windowingEducationProto, focusAppPackageName)
    }

    private fun isFocusAppInAllowlist(focusAppPackageName: String): Boolean =
        focusAppPackageName in
            context.resources.getStringArray(
                R.array.desktop_windowing_app_handle_education_allowlist_apps
            )

    // TODO: b/350953004 - Add checks based on App compat
    // TODO: b/350951797 - Add checks based on PKT tips education
    private fun isOtherEducationShowing(): Boolean = isTaskbarEducationShowing()

    private fun isTaskbarEducationShowing(): Boolean =
        Secure.getInt(context.contentResolver, Secure.LAUNCHER_TASKBAR_EDUCATION_SHOWING, 0) == 1

    private fun hasSufficientTimeSinceSetup(): Boolean =
        Duration.ofMillis(SystemClock.elapsedRealtime()) >
            convertIntegerResourceToDuration(
                R.integer.desktop_windowing_education_required_time_since_setup_seconds
            )

    private suspend fun hasMinAppUsage(
        windowingEducationProto: WindowingEducationProto,
        focusAppPackageName: String,
    ): Boolean =
        (launchCountByPackageName(windowingEducationProto)[focusAppPackageName] ?: 0) >=
            context.resources.getInteger(R.integer.desktop_windowing_education_min_app_launch_count)

    private suspend fun launchCountByPackageName(
        windowingEducationProto: WindowingEducationProto
    ): Map<String, Int> =
        if (isAppUsageCacheStale(windowingEducationProto)) {
            // Query and return user stats, update cache in datastore
            getAndCacheAppUsageStats()
        } else {
            // Return cached usage stats
            windowingEducationProto.appHandleEducation.appUsageStatsMap
        }

    private fun isAppUsageCacheStale(windowingEducationProto: WindowingEducationProto): Boolean {
        val currentTime = currentTimeInDuration()
        val lastUpdateTime =
            Duration.ofMillis(
                windowingEducationProto.appHandleEducation.appUsageStatsLastUpdateTimestampMillis
            )
        val appUsageStatsCachingInterval =
            convertIntegerResourceToDuration(
                R.integer.desktop_windowing_education_app_usage_cache_interval_seconds
            )
        return (currentTime - lastUpdateTime) > appUsageStatsCachingInterval
    }

    private suspend fun getAndCacheAppUsageStats(): Map<String, Int> {
        val currentTime = currentTimeInDuration()
        val appUsageStats = queryAppUsageStats()
        appHandleEducationDatastoreRepository.updateAppUsageStats(appUsageStats, currentTime)
        return appUsageStats
    }

    private fun queryAppUsageStats(): Map<String, Int> {
        val endTime = currentTimeInDuration()
        val appLaunchInterval =
            convertIntegerResourceToDuration(
                R.integer.desktop_windowing_education_app_launch_interval_seconds
            )
        val startTime = endTime - appLaunchInterval

        return usageStatsManager
            .queryAndAggregateUsageStats(startTime.toMillis(), endTime.toMillis())
            .mapValues { it.value.appLaunchCount }
    }

    private fun convertIntegerResourceToDuration(@IntegerRes resourceId: Int): Duration =
        Duration.ofSeconds(context.resources.getInteger(resourceId).toLong())

    private fun currentTimeInDuration(): Duration = Duration.ofMillis(System.currentTimeMillis())
}
