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
import android.content.Context
import android.os.SystemClock
import android.provider.Settings.Secure
import com.android.wm.shell.R
import com.android.wm.shell.desktopmode.CaptionState
import com.android.wm.shell.desktopmode.education.data.AppToWebEducationDatastoreRepository
import com.android.wm.shell.desktopmode.education.data.WindowingEducationProto
import java.time.Duration

/** Filters incoming App-to-Web education triggers based on set conditions. */
class AppToWebEducationFilter(
    private val context: Context,
    private val appToWebEducationDatastoreRepository: AppToWebEducationDatastoreRepository,
) {

    /** Returns true if conditions to show App-to-web education are met, returns false otherwise. */
    suspend fun shouldShowAppToWebEducation(captionState: CaptionState): Boolean {
        val (taskInfo: RunningTaskInfo, isCapturedLinkAvailable: Boolean) =
            when (captionState) {
                is CaptionState.AppHandle ->
                    Pair(captionState.runningTaskInfo, captionState.isCapturedLinkAvailable)
                is CaptionState.AppHeader ->
                    Pair(captionState.runningTaskInfo, captionState.isCapturedLinkAvailable)
                else -> return false
            }

        val focusAppPackageName = taskInfo.topActivityInfo?.packageName ?: return false
        val windowingEducationProto = appToWebEducationDatastoreRepository.windowingEducationProto()

        return !isOtherEducationShowing() &&
            !isEducationViewLimitReached(windowingEducationProto) &&
            hasSufficientTimeSinceSetup() &&
            !isFeatureUsedBefore(windowingEducationProto) &&
            isCapturedLinkAvailable &&
            isFocusAppInAllowlist(focusAppPackageName)
    }

    private fun isFocusAppInAllowlist(focusAppPackageName: String): Boolean =
        focusAppPackageName in
            context.resources.getStringArray(
                R.array.desktop_windowing_app_to_web_education_allowlist_apps
            )

    // TODO: b/350953004 - Add checks based on App compat
    // TODO: b/350951797 - Add checks based on PKT tips education
    private fun isOtherEducationShowing(): Boolean =
        isTaskbarEducationShowing() || isCompatUiEducationShowing()

    private fun isTaskbarEducationShowing(): Boolean =
        Secure.getInt(context.contentResolver, Secure.LAUNCHER_TASKBAR_EDUCATION_SHOWING, 0) == 1

    private fun isCompatUiEducationShowing(): Boolean =
        Secure.getInt(context.contentResolver, Secure.COMPAT_UI_EDUCATION_SHOWING, 0) == 1

    private fun hasSufficientTimeSinceSetup(): Boolean =
        Duration.ofMillis(SystemClock.elapsedRealtime()) >
            convertIntegerResourceToDuration(
                R.integer.desktop_windowing_education_required_time_since_setup_seconds
            )

    /** Returns true if education is viewed maximum amount of times it should be shown. */
    fun isEducationViewLimitReached(windowingEducationProto: WindowingEducationProto): Boolean =
        windowingEducationProto.getAppToWebEducation().getEducationShownCount() >=
            MAXIMUM_TIMES_EDUCATION_SHOWN

    private fun isFeatureUsedBefore(windowingEducationProto: WindowingEducationProto): Boolean =
        windowingEducationProto.hasFeatureUsedTimestampMillis()

    private fun convertIntegerResourceToDuration(@IntegerRes resourceId: Int): Duration =
        Duration.ofSeconds(context.resources.getInteger(resourceId).toLong())

    companion object {
        private const val MAXIMUM_TIMES_EDUCATION_SHOWN = 100
    }
}
