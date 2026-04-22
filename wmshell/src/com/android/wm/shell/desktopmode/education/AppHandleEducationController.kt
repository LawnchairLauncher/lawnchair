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
import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.os.SystemProperties
import android.view.View.LAYOUT_DIRECTION_RTL
import android.window.DesktopExperienceFlags
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.desktopmode.CaptionState
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository
import com.android.wm.shell.desktopmode.education.data.AppHandleEducationDatastoreRepository
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationTooltipController
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationTooltipController.TooltipColorScheme
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationTooltipController.TooltipEducationViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

/**
 * Controls app handle education end to end.
 *
 * Listen to the user trigger for app handle education, calls an api to check if the education
 * should be shown and controls education UI.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
class AppHandleEducationController(
    private val context: Context,
    private val appHandleEducationFilter: AppHandleEducationFilter,
    private val appHandleEducationDatastoreRepository: AppHandleEducationDatastoreRepository,
    private val windowDecorCaptionRepository: WindowDecorCaptionRepository,
    private val windowingEducationViewController: DesktopWindowingEducationTooltipController,
    @ShellMainThread private val applicationCoroutineScope: CoroutineScope,
    @ShellBackgroundThread private val backgroundDispatcher: MainCoroutineDispatcher,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
    private val desktopState: DesktopState,
) {
    private lateinit var openHandleMenuCallback: (Int) -> Unit
    private lateinit var toDesktopModeCallback: (Int, DesktopModeTransitionSource) -> Unit
    private val onTertiaryFixedColor =
        context.getColor(com.android.internal.R.color.materialColorOnTertiaryFixed)
    private val tertiaryFixedColor =
        context.getColor(com.android.internal.R.color.materialColorTertiaryFixed)

    init {
        runIfEducationFeatureEnabled {
            // Coroutine block for the first hint that appears on a full-screen app's app handle to
            // encourage users to open the app handle menu.
            applicationCoroutineScope.launch {
                if (isAppHandleHintViewed()) return@launch
                windowDecorCaptionRepository.captionStateFlow
                    .debounce(APP_HANDLE_EDUCATION_DELAY_MILLIS)
                    .filter { captionState ->
                        captionState is CaptionState.AppHandle &&
                            !captionState.isHandleMenuExpanded &&
                            isCaptionFocused(captionState) &&
                            !isAppHandleHintViewed() &&
                            appHandleEducationFilter.shouldShowDesktopModeEducation(captionState)
                    }
                    .take(1)
                    .flowOn(backgroundDispatcher)
                    .collectLatest { captionState ->
                        showEducation(captionState)
                        appHandleEducationDatastoreRepository
                            .updateAppHandleHintViewedTimestampMillis(true)
                        delay(TOOLTIP_VISIBLE_DURATION_MILLIS)
                        windowingEducationViewController.hideEducationTooltip()
                    }
            }

            // Coroutine block for the hint that appears when an app handle is expanded to
            // encourage users to enter desktop mode.
            applicationCoroutineScope.launch {
                if (isEnterDesktopModeHintViewed()) return@launch
                windowDecorCaptionRepository.captionStateFlow
                    .debounce(ENTER_DESKTOP_MODE_EDUCATION_DELAY_MILLIS)
                    .filter { captionState ->
                        captionState is CaptionState.AppHandle &&
                            captionState.isHandleMenuExpanded &&
                            isCaptionFocused(captionState) &&
                            !isEnterDesktopModeHintViewed() &&
                            appHandleEducationFilter.shouldShowDesktopModeEducation(captionState)
                    }
                    .take(1)
                    .flowOn(backgroundDispatcher)
                    .collectLatest { captionState ->
                        showWindowingImageButtonTooltip(captionState as CaptionState.AppHandle)
                        appHandleEducationDatastoreRepository
                            .updateEnterDesktopModeHintViewedTimestampMillis(true)
                        delay(TOOLTIP_VISIBLE_DURATION_MILLIS)
                        windowingEducationViewController.hideEducationTooltip()
                    }
            }

            // Coroutine block for the hint that appears on the window app header in freeform mode
            // to let users know how to exit desktop mode.
            applicationCoroutineScope.launch {
                if (isExitDesktopModeHintViewed()) return@launch
                windowDecorCaptionRepository.captionStateFlow
                    .debounce(APP_HANDLE_EDUCATION_DELAY_MILLIS)
                    .filter { captionState ->
                        captionState is CaptionState.AppHeader &&
                            !captionState.isHeaderMenuExpanded &&
                            isCaptionFocused(captionState) &&
                            !isExitDesktopModeHintViewed() &&
                            appHandleEducationFilter.shouldShowDesktopModeEducation(captionState)
                    }
                    .take(1)
                    .flowOn(backgroundDispatcher)
                    .collectLatest { captionState ->
                        showExitWindowingTooltip(captionState as CaptionState.AppHeader)
                        appHandleEducationDatastoreRepository
                            .updateExitDesktopModeHintViewedTimestampMillis(true)
                        delay(TOOLTIP_VISIBLE_DURATION_MILLIS)
                        windowingEducationViewController.hideEducationTooltip()
                    }
            }

            // Listens to a [NoCaption] state change to dismiss any tooltip if the app handle or app
            // header is gone or de-focused (e.g. when a user swipes up to home, overview, or enters
            // split screen)
            applicationCoroutineScope.launch {
                if (
                    isAppHandleHintViewed() &&
                        isEnterDesktopModeHintViewed() &&
                        isExitDesktopModeHintViewed()
                )
                    return@launch
                windowDecorCaptionRepository.captionStateFlow
                    .filter { captionState ->
                        !isCaptionFocused(captionState) &&
                            !isAppHandleHintViewed() &&
                            !isEnterDesktopModeHintViewed() &&
                            !isExitDesktopModeHintViewed()
                    }
                    .flowOn(backgroundDispatcher)
                    .collectLatest { windowingEducationViewController.hideEducationTooltip() }
            }
        }
    }

    private inline fun runIfEducationFeatureEnabled(block: () -> Unit) {
        if (desktopState.canEnterDesktopMode && Flags.enableDesktopWindowingAppHandleEducation())
            block()
    }

    private fun isCaptionFocused(captionState: CaptionState) =
        if (!DesktopExperienceFlags.ENABLE_APP_HANDLE_POSITION_REPORTING.isTrue) {
            captionState !is CaptionState.NoCaption
        } else {
            captionState.isFocused
        }

    private fun showEducation(captionState: CaptionState) {
        val appHandleBounds = (captionState as CaptionState.AppHandle).globalAppHandleBounds
        val taskInfo = captionState.runningTaskInfo
        val tooltipGlobalCoordinates =
            Point(appHandleBounds.left + appHandleBounds.width() / 2, appHandleBounds.bottom)
        // Populate information important to inflate app handle education tooltip.
        val appHandleTooltipConfig =
            TooltipEducationViewConfig(
                tooltipViewLayout = R.layout.desktop_windowing_education_top_arrow_tooltip,
                tooltipColorScheme =
                    TooltipColorScheme(
                        tertiaryFixedColor,
                        onTertiaryFixedColor,
                        onTertiaryFixedColor,
                    ),
                tooltipViewGlobalCoordinates = tooltipGlobalCoordinates,
                tooltipText = getString(R.string.windowing_app_handle_education_tooltip),
                arrowDirection =
                    DesktopWindowingEducationTooltipController.TooltipArrowDirection.UP,
                onEducationClickAction = {
                    openHandleMenuCallback(taskInfo.taskId)
                    desktopModeUiEventLogger.log(
                        taskInfo,
                        DesktopUiEventEnum.APP_HANDLE_EDUCATION_TOOLTIP_CLICKED,
                    )
                },
                onDismissAction = {
                    desktopModeUiEventLogger.log(
                        taskInfo,
                        DesktopUiEventEnum.APP_HANDLE_EDUCATION_TOOLTIP_DISMISSED,
                    )
                },
            )

        windowingEducationViewController.showEducationTooltip(
            tooltipViewConfig = appHandleTooltipConfig,
            taskId = taskInfo.taskId,
        )
        desktopModeUiEventLogger.log(
            taskInfo,
            DesktopUiEventEnum.APP_HANDLE_EDUCATION_TOOLTIP_SHOWN,
        )
    }

    /** Show tooltip that points to windowing image button in app handle menu */
    private suspend fun showWindowingImageButtonTooltip(captionState: CaptionState.AppHandle) {
        val appInfoPillHeight = getSize(R.dimen.desktop_mode_handle_menu_app_info_pill_height)
        val taskInfo = captionState.runningTaskInfo
        val windowingOptionPillHeight =
            getSize(R.dimen.desktop_mode_handle_menu_windowing_pill_height)
        val appHandleMenuWidth =
            getSize(R.dimen.desktop_mode_handle_menu_width) +
                getSize(R.dimen.desktop_mode_handle_menu_pill_spacing_margin)
        val appHandleMenuMargins =
            getSize(R.dimen.desktop_mode_handle_menu_margin_top) +
                getSize(R.dimen.desktop_mode_handle_menu_pill_spacing_margin)

        val appHandleBounds = captionState.globalAppHandleBounds
        val appHandleCenterX = appHandleBounds.left + appHandleBounds.width() / 2
        val tooltipGlobalCoordinates =
            Point(
                if (isRtl()) {
                    appHandleCenterX - appHandleMenuWidth / 2
                } else {
                    appHandleCenterX + appHandleMenuWidth / 2
                },
                appHandleBounds.top +
                    appHandleMenuMargins +
                    appInfoPillHeight +
                    windowingOptionPillHeight / 2,
            )
        // Populate information important to inflate windowing image button education
        // tooltip.
        val windowingImageButtonTooltipConfig =
            TooltipEducationViewConfig(
                tooltipViewLayout = R.layout.desktop_windowing_education_horizontal_arrow_tooltip,
                tooltipColorScheme =
                    TooltipColorScheme(
                        tertiaryFixedColor,
                        onTertiaryFixedColor,
                        onTertiaryFixedColor,
                    ),
                tooltipViewGlobalCoordinates = tooltipGlobalCoordinates,
                tooltipText =
                    getString(R.string.windowing_desktop_mode_image_button_education_tooltip),
                arrowDirection =
                    DesktopWindowingEducationTooltipController.TooltipArrowDirection.HORIZONTAL,
                onEducationClickAction = {
                    toDesktopModeCallback(
                        taskInfo.taskId,
                        DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON,
                    )
                    desktopModeUiEventLogger.log(
                        taskInfo,
                        DesktopUiEventEnum.ENTER_DESKTOP_MODE_EDUCATION_TOOLTIP_CLICKED,
                    )
                },
                onDismissAction = {
                    desktopModeUiEventLogger.log(
                        taskInfo,
                        DesktopUiEventEnum.ENTER_DESKTOP_MODE_EDUCATION_TOOLTIP_DISMISSED,
                    )
                },
            )

        windowingEducationViewController.showEducationTooltip(
            taskId = taskInfo.taskId,
            tooltipViewConfig = windowingImageButtonTooltipConfig,
        )
        desktopModeUiEventLogger.log(
            taskInfo,
            DesktopUiEventEnum.ENTER_DESKTOP_MODE_EDUCATION_TOOLTIP_SHOWN,
        )
    }

    /** Show tooltip that points to app chip button and educates user on how to exit desktop mode */
    private suspend fun showExitWindowingTooltip(captionState: CaptionState.AppHeader) {
        val globalAppChipBounds = captionState.globalAppChipBounds
        val taskInfo = captionState.runningTaskInfo
        val tooltipGlobalCoordinates =
            Point(
                if (isRtl()) {
                    globalAppChipBounds.left
                } else {
                    globalAppChipBounds.right
                },
                globalAppChipBounds.top + globalAppChipBounds.height() / 2,
            )
        // Populate information important to inflate exit desktop mode education tooltip.
        val exitWindowingTooltipConfig =
            TooltipEducationViewConfig(
                tooltipViewLayout = R.layout.desktop_windowing_education_horizontal_arrow_tooltip,
                tooltipColorScheme =
                    TooltipColorScheme(
                        tertiaryFixedColor,
                        onTertiaryFixedColor,
                        onTertiaryFixedColor,
                    ),
                tooltipViewGlobalCoordinates = tooltipGlobalCoordinates,
                tooltipText = getString(R.string.windowing_desktop_mode_exit_education_tooltip),
                arrowDirection =
                    DesktopWindowingEducationTooltipController.TooltipArrowDirection.HORIZONTAL,
                onDismissAction = {
                    desktopModeUiEventLogger.log(
                        taskInfo,
                        DesktopUiEventEnum.EXIT_DESKTOP_MODE_EDUCATION_TOOLTIP_DISMISSED,
                    )
                },
                onEducationClickAction = {
                    openHandleMenuCallback(taskInfo.taskId)
                    desktopModeUiEventLogger.log(
                        taskInfo,
                        DesktopUiEventEnum.EXIT_DESKTOP_MODE_EDUCATION_TOOLTIP_CLICKED,
                    )
                },
            )
        windowingEducationViewController.showEducationTooltip(
            taskId = taskInfo.taskId,
            tooltipViewConfig = exitWindowingTooltipConfig,
        )
        desktopModeUiEventLogger.log(
            taskInfo,
            DesktopUiEventEnum.EXIT_DESKTOP_MODE_EDUCATION_TOOLTIP_SHOWN,
        )
    }

    /**
     * Setup callbacks for app handle education tooltips.
     *
     * @param openHandleMenuCallback callback invoked to open app handle menu or app chip menu.
     * @param toDesktopModeCallback callback invoked to move task into desktop mode.
     */
    fun setAppHandleEducationTooltipCallbacks(
        openHandleMenuCallback: (taskId: Int) -> Unit,
        toDesktopModeCallback: (taskId: Int, DesktopModeTransitionSource) -> Unit,
    ) {
        this.openHandleMenuCallback = openHandleMenuCallback
        this.toDesktopModeCallback = toDesktopModeCallback
    }

    private suspend fun isAppHandleHintViewed(): Boolean =
        appHandleEducationDatastoreRepository.dataStoreFlow
            .first()
            .hasAppHandleHintViewedTimestampMillis() && !FORCE_SHOW_DESKTOP_MODE_EDUCATION

    private suspend fun isEnterDesktopModeHintViewed(): Boolean =
        appHandleEducationDatastoreRepository.dataStoreFlow
            .first()
            .hasEnterDesktopModeHintViewedTimestampMillis() && !FORCE_SHOW_DESKTOP_MODE_EDUCATION

    private suspend fun isExitDesktopModeHintViewed(): Boolean =
        appHandleEducationDatastoreRepository.dataStoreFlow
            .first()
            .hasExitDesktopModeHintViewedTimestampMillis() && !FORCE_SHOW_DESKTOP_MODE_EDUCATION

    private fun getSize(@DimenRes resourceId: Int): Int {
        if (resourceId == Resources.ID_NULL) return 0
        return context.resources.getDimensionPixelSize(resourceId)
    }

    private fun getString(@StringRes resId: Int): String = context.resources.getString(resId)

    private fun isRtl() = context.resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL

    companion object {
        const val TAG = "AppHandleEducationController"
        val APP_HANDLE_EDUCATION_DELAY_MILLIS: Long
            get() = SystemProperties.getLong("persist.windowing_app_handle_education_delay", 3000L)

        val ENTER_DESKTOP_MODE_EDUCATION_DELAY_MILLIS: Long
            get() =
                SystemProperties.getLong(
                    "persist.windowing_enter_desktop_mode_education_timeout",
                    400L,
                )

        val TOOLTIP_VISIBLE_DURATION_MILLIS: Long
            get() = SystemProperties.getLong("persist.windowing_tooltip_visible_duration", 12000L)

        val FORCE_SHOW_DESKTOP_MODE_EDUCATION: Boolean
            get() =
                SystemProperties.getBoolean(
                    "persist.windowing_force_show_desktop_mode_education",
                    false,
                )
    }
}
