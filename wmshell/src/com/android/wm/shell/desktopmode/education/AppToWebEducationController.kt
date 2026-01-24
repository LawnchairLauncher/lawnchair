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

import android.annotation.DimenRes
import android.annotation.StringRes
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.os.SystemProperties
import androidx.compose.ui.graphics.toArgb
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.desktopmode.CaptionState
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository
import com.android.wm.shell.desktopmode.education.data.AppToWebEducationDatastoreRepository
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationPromoController
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationPromoController.EducationColorScheme
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationPromoController.EducationViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Controls App-to-Web education end to end.
 *
 * Listen to usages of App-to-Web, calls an api to check if the education should be shown and
 * controls education UI.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
class AppToWebEducationController(
    private val context: Context,
    private val appToWebEducationFilter: AppToWebEducationFilter,
    private val appToWebEducationDatastoreRepository: AppToWebEducationDatastoreRepository,
    private val windowDecorCaptionRepository: WindowDecorCaptionRepository,
    private val windowingEducationViewController: DesktopWindowingEducationPromoController,
    @ShellMainThread private val applicationCoroutineScope: CoroutineScope,
    @ShellBackgroundThread private val backgroundDispatcher: MainCoroutineDispatcher,
    private val desktopState: DesktopState,
) {
    private val decorThemeUtil = DecorThemeUtil(context)

    init {
        runIfEducationFeatureEnabled {
            applicationCoroutineScope.launch {
                // Central block handling the App-to-Web's educational flow end-to-end.
                isEducationViewLimitReachedFlow()
                    .flatMapLatest { countExceedsMaximum ->
                        if (countExceedsMaximum) {
                            // If the education has been viewed the maximum amount of times then
                            // return emptyFlow() that completes immediately. This will help us to
                            // not listen to [captionHandleStateFlow] after the education should
                            // not be shown.
                            emptyFlow()
                        } else {
                            // Listen for changes to window decor's caption.
                            windowDecorCaptionRepository.captionStateFlow
                                // Wait for few seconds before emitting the latest state.
                                .debounce(APP_TO_WEB_EDUCATION_DELAY_MILLIS)
                                .filter { captionState ->
                                    captionState !is CaptionState.NoCaption &&
                                        appToWebEducationFilter.shouldShowAppToWebEducation(
                                            captionState
                                        )
                                }
                        }
                    }
                    .flowOn(backgroundDispatcher)
                    .collectLatest { captionState ->
                        val educationColorScheme = educationColorScheme(captionState)
                        showEducation(captionState, educationColorScheme!!)
                        // After showing first tooltip, increase count of education views
                        appToWebEducationDatastoreRepository.updateEducationShownCount()
                    }
            }

            applicationCoroutineScope.launch {
                if (isFeatureUsed()) return@launch
                windowDecorCaptionRepository.appToWebUsageFlow.collect {
                    // If user utilizes App-to-Web, mark user has used the feature
                    appToWebEducationDatastoreRepository.updateFeatureUsedTimestampMillis(
                        isViewed = true
                    )
                }
            }
        }
    }

    private inline fun runIfEducationFeatureEnabled(block: () -> Unit) {
        if (
            desktopState.canEnterDesktopMode &&
                Flags.enableDesktopWindowingAppToWebEducationIntegration()
        ) {
            block()
        }
    }

    private fun showEducation(captionState: CaptionState, colorScheme: EducationColorScheme) {
        val educationGlobalCoordinates: Point
        val taskId: Int
        when (captionState) {
            is CaptionState.AppHandle -> {
                val appHandleBounds = captionState.globalAppHandleBounds
                val educationWidth =
                    loadDimensionPixelSize(R.dimen.desktop_windowing_education_promo_width)
                educationGlobalCoordinates =
                    Point(appHandleBounds.centerX() - educationWidth / 2, appHandleBounds.bottom)
                taskId = captionState.runningTaskInfo.taskId
            }

            is CaptionState.AppHeader -> {
                val taskBounds =
                    captionState.runningTaskInfo.configuration.windowConfiguration.bounds
                educationGlobalCoordinates =
                    Point(taskBounds.left, captionState.globalAppChipBounds.bottom)
                taskId = captionState.runningTaskInfo.taskId
            }

            else -> return
        }

        // Populate information important to inflate education promo.
        val educationConfig =
            EducationViewConfig(
                viewLayout = R.layout.desktop_windowing_education_promo,
                educationColorScheme = colorScheme,
                viewGlobalCoordinates = educationGlobalCoordinates,
                educationText = getString(R.string.desktop_windowing_app_to_web_education_text),
                widthId = R.dimen.desktop_windowing_education_promo_width,
                heightId = R.dimen.desktop_windowing_education_promo_height,
            )

        windowingEducationViewController.showEducation(
            viewConfig = educationConfig,
            taskId = taskId,
        )
    }

    private fun educationColorScheme(captionState: CaptionState): EducationColorScheme? {
        val taskInfo: RunningTaskInfo =
            when (captionState) {
                is CaptionState.AppHandle -> captionState.runningTaskInfo
                is CaptionState.AppHeader -> captionState.runningTaskInfo
                else -> return null
            }

        val colorScheme = decorThemeUtil.getColorScheme(taskInfo)
        val tooltipContainerColor = colorScheme.surfaceBright.toArgb()
        val tooltipTextColor = colorScheme.onSurface.toArgb()
        return EducationColorScheme(tooltipContainerColor, tooltipTextColor)
    }

    /**
     * Listens to changes in the number of times the education has been viewed, mapping the count to
     * true if the education has been viewed the maximum amount of times.
     */
    private fun isEducationViewLimitReachedFlow(): Flow<Boolean> =
        appToWebEducationDatastoreRepository.dataStoreFlow
            .map { preferences -> appToWebEducationFilter.isEducationViewLimitReached(preferences) }
            .distinctUntilChanged()

    /**
     * Listens to the changes to [WindowingEducationProto#hasFeatureUsedTimestampMillis()] in
     * datastore proto object.
     */
    private suspend fun isFeatureUsed(): Boolean =
        appToWebEducationDatastoreRepository.dataStoreFlow.first().hasFeatureUsedTimestampMillis()

    private fun loadDimensionPixelSize(@DimenRes resourceId: Int): Int {
        if (resourceId == Resources.ID_NULL) return 0
        return context.resources.getDimensionPixelSize(resourceId)
    }

    private fun getString(@StringRes resId: Int): String = context.resources.getString(resId)

    companion object {
        const val TAG = "AppToWebEducationController"
        val APP_TO_WEB_EDUCATION_DELAY_MILLIS: Long
            get() = SystemProperties.getLong("persist.windowing_app_handle_education_delay", 3000L)
    }
}
