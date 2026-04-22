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

package com.android.wm.shell.util

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.content.pm.ActivityInfo
import android.graphics.Rect
import com.android.wm.shell.desktopmode.CaptionState
import com.android.wm.shell.desktopmode.education.data.WindowingEducationProto
import com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier
import org.mockito.kotlin.mock

/**
 * Create an instance of [CaptionState.AppHandle] with parameters as properties.
 *
 * Any fields without corresponding parameters will retain their default values.
 */
fun createAppHandleState(
    runningTaskInfo: RunningTaskInfo = createTaskInfo(),
    isHandleMenuExpanded: Boolean = false,
    globalAppHandleBounds: Rect = Rect(),
    isCapturedLinkAvailable: Boolean = false,
    appHandleIdentifier: AppHandleIdentifier = mock(),
    isFocused: Boolean = true,
): CaptionState.AppHandle =
    CaptionState.AppHandle(
        runningTaskInfo = runningTaskInfo,
        isHandleMenuExpanded = isHandleMenuExpanded,
        globalAppHandleBounds = globalAppHandleBounds,
        isCapturedLinkAvailable = isCapturedLinkAvailable,
        appHandleIdentifier = appHandleIdentifier,
        isFocused = isFocused,
    )

/**
 * Create an instance of [CaptionState.AppHeader] with parameters as properties.
 *
 * Any fields without corresponding parameters will retain their default values.
 */
fun createAppHeaderState(
    runningTaskInfo: RunningTaskInfo = createTaskInfo(),
    isHeaderMenuExpanded: Boolean = false,
    globalAppChipBounds: Rect = Rect(),
    isCapturedLinkAvailable: Boolean = false,
    isFocused: Boolean = true,
): CaptionState.AppHeader =
    CaptionState.AppHeader(
        runningTaskInfo = runningTaskInfo,
        isHeaderMenuExpanded = isHeaderMenuExpanded,
        globalAppChipBounds = globalAppChipBounds,
        isCapturedLinkAvailable = isCapturedLinkAvailable,
        isFocused = isFocused
    )

/**
 * Create an instance of [RunningTaskInfo] with parameters as properties.
 *
 * Any fields without corresponding parameters will retain their default values.
 */
fun createTaskInfo(
    deviceWindowingMode: Int = WINDOWING_MODE_UNDEFINED,
    runningTaskPackageName: String = GMAIL_PACKAGE_NAME,
): RunningTaskInfo =
    RunningTaskInfo().apply {
      configuration.windowConfiguration.windowingMode = deviceWindowingMode
      topActivityInfo = ActivityInfo().apply { packageName = runningTaskPackageName }
    }

/**
 * Constructs a [WindowingEducationProto] object, populating its fields with the provided
 * parameters.
 *
 * Any fields without corresponding parameters will retain their default values.
 */
fun createWindowingEducationProto(
    appHandleHintViewedTimestampMillis: Long? = null,
    appHandleHintUsedTimestampMillis: Long? = null,
    appUsageStats: Map<String, Int>? = null,
    appUsageStatsLastUpdateTimestampMillis: Long? = null,
    enterDesktopModeHintViewedTimestampMillis: Long? = null,
    exitDesktopModeHintViewedTimestampMillis: Long? = null,
): WindowingEducationProto =
    WindowingEducationProto.newBuilder()
        .apply {
          if (appHandleHintViewedTimestampMillis != null) {
            setAppHandleHintViewedTimestampMillis(appHandleHintViewedTimestampMillis)
          }
          if (appHandleHintUsedTimestampMillis != null) {
            setAppHandleHintUsedTimestampMillis(appHandleHintUsedTimestampMillis)
          }
          if (enterDesktopModeHintViewedTimestampMillis != null) {
              setEnterDesktopModeHintViewedTimestampMillis(enterDesktopModeHintViewedTimestampMillis)
          }
          if (exitDesktopModeHintViewedTimestampMillis != null) {
              setExitDesktopModeHintViewedTimestampMillis(exitDesktopModeHintViewedTimestampMillis)
          }
          setAppHandleEducation(
              createAppHandleEducationProto(appUsageStats, appUsageStatsLastUpdateTimestampMillis))
        }
        .build()

/**
 * Constructs a [WindowingEducationProto.AppHandleEducation] object, populating its fields with the
 * provided parameters.
 *
 * Any fields without corresponding parameters will retain their default values.
 */
fun createAppHandleEducationProto(
    appUsageStats: Map<String, Int>? = null,
    appUsageStatsLastUpdateTimestampMillis: Long? = null
): WindowingEducationProto.AppHandleEducation =
    WindowingEducationProto.AppHandleEducation.newBuilder()
        .apply {
          if (appUsageStats != null) putAllAppUsageStats(appUsageStats)
          if (appUsageStatsLastUpdateTimestampMillis != null) {
            setAppUsageStatsLastUpdateTimestampMillis(appUsageStatsLastUpdateTimestampMillis)
          }
        }
        .build()

const val GMAIL_PACKAGE_NAME = "com.google.android.gm"
const val YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"
const val LAUNCHER_PACKAGE_NAME = "com.google.android.apps.nexuslauncher"
