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

package com.android.wm.shell.windowdecor.caption

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo.CONFIG_FONT_SCALE
import android.content.pm.ActivityInfo.CONFIG_LOCALE
import android.content.pm.ActivityInfo.CONFIG_UI_MODE
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.os.Trace
import android.view.Display
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.View
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import android.window.TaskSnapshot
import android.window.WindowContainerTransaction
//import com.android.app.tracing.traceSection
import com.android.internal.policy.SystemBarUtils
import com.android.window.flags2.Flags
import com.android.wm.shell.R
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.apptoweb.AppToWebRepository
import com.android.wm.shell.apptoweb.OpenByDefaultDialog
import com.android.wm.shell.apptoweb.OpenByDefaultDialog.DialogLifecycleListener
import com.android.wm.shell.apptoweb.canShowAppLinks
import com.android.wm.shell.apptoweb.isBrowserApp
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.MultiInstanceHelper
import com.android.wm.shell.desktopmode.CaptionState
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DesktopHandleManageWindowsMenu
import com.android.wm.shell.windowdecor.HandleMenu
import com.android.wm.shell.windowdecor.HandleMenu.Companion.shouldShowChangeAspectRatioButton
import com.android.wm.shell.windowdecor.HandleMenu.Companion.shouldShowRestartButton
import com.android.wm.shell.windowdecor.HandleMenu.HandleMenuFactory
import com.android.wm.shell.windowdecor.HandleMenuController
import com.android.wm.shell.windowdecor.ManageWindowsMenuController
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.WindowDecoration2.RelayoutParams
import com.android.wm.shell.windowdecor.WindowDecoration2.SurfaceControlViewHostFactory
import com.android.wm.shell.windowdecor.WindowDecorationActions
import com.android.wm.shell.windowdecor.WindowManagerWrapper
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier
import com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier.AppHandleWindowingMode
import com.android.wm.shell.windowdecor.viewholder.AppHandleViewHolder
import com.android.wm.shell.windowdecor.viewholder.AppHandleViewHolder.HandleData
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.launch

/**
 * Controller for the app handle. Creates, updates, and removes the views of the caption and its
 * menus.
 */
class AppHandleController(
    taskInfo: RunningTaskInfo,
    windowDecorViewHostSupplier: WindowDecorViewHostSupplier<WindowDecorViewHost>,
    private val context: Context,
    private val userContext: Context,
    private val transitions: Transitions,
    private val displayController: DisplayController,
    private val taskResourceLoader: WindowDecorTaskResourceLoader,
    private val splitScreenController: SplitScreenController,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val taskOrganizer: ShellTaskOrganizer,
    private val taskSurface: SurfaceControl,
    private val decorationSurface: SurfaceControl,
    @ShellMainThread private val mainHandler: Handler,
    @ShellMainThread private val mainDispatcher: MainCoroutineDispatcher,
    @ShellMainThread private val mainScope: CoroutineScope,
    private val windowManagerWrapper: WindowManagerWrapper,
    private val multiInstanceHelper: MultiInstanceHelper,
    private val windowDecorHandleRepository: WindowDecorCaptionRepository,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
    private val desktopState: DesktopState,
    private val windowDecorationActions: WindowDecorationActions,
    private val decorWindowContext: Context,
    private val onCaptionTouchListener: View.OnTouchListener,
    private val onCaptionButtonClickListener: View.OnClickListener,
    private val appToWebRepository: AppToWebRepository,
    private val handleMenuFactory: HandleMenuFactory = HandleMenuFactory,
    private val appHandleViewHolderFactory: AppHandleViewHolder.Factory =
        AppHandleViewHolder.Factory(),
    private val surfaceControlTransactionSupplier: () -> SurfaceControl.Transaction = {
        SurfaceControl.Transaction()
    },
    surfaceControlBuilderSupplier: () -> SurfaceControl.Builder = { SurfaceControl.Builder() },
    surfaceControlViewHostFactory: SurfaceControlViewHostFactory =
        object : SurfaceControlViewHostFactory {},
) :
    CaptionController<WindowDecorLinearLayout>(
        taskInfo,
        windowDecorViewHostSupplier,
        surfaceControlBuilderSupplier,
        surfaceControlViewHostFactory,
    ),
    HandleMenuController,
    ManageWindowsMenuController {

    override val captionType = CaptionType.APP_HANDLE
    private lateinit var viewHolder: AppHandleViewHolder

    private var handleMenu: HandleMenu? = null
    private var openByDefaultDialog: OpenByDefaultDialog? = null
    private var manageWindowsMenu: DesktopHandleManageWindowsMenu? = null

    override val handleMenuController = this
    override val manageWindowsMenuController = this

    override val isHandleMenuActive: Boolean
        get() = handleMenu != null

    private val isOpenByDefaultDialogActive
        get() = openByDefaultDialog != null

    private val showInputLayer
        // Don't show the input layer during the recents transition, otherwise it could become
        // touchable while in overview, during quick-switch or even for a short moment after
        // going home.
        get() = isCaptionVisible && !isRecentsTransitionRunning

    private val isEducationOrHandleReportingEnabled =
        Flags.enableDesktopWindowingAppHandleEducation() ||
            DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION_INTEGRATION
                .isTrue ||
            DesktopExperienceFlags.ENABLE_APP_HANDLE_POSITION_REPORTING.isTrue
    private val display
        get() = displayController.getDisplay(taskInfo.displayId)

    private val inFullImmersive
        get() =
            desktopUserRepositories
                .getProfile(taskInfo.userId)
                .isTaskInFullImmersiveState(taskInfo.taskId)

    override fun relayout(
        params: RelayoutParams,
        parentContainer: SurfaceControl,
        display: Display,
        decorWindowContext: Context,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        wct: WindowContainerTransaction,
    ): CaptionRelayoutResult {
            // Check for relevant configuration changes
            val oldConfig = this.taskInfo.configuration
            val newConfig = params.runningTaskInfo.configuration
            val diff = newConfig.diff(oldConfig)
            // Check for UI mode (dark/light), locale, or font scale changes
            val configChanged =
                (diff and (CONFIG_UI_MODE or CONFIG_LOCALE or CONFIG_FONT_SCALE)) != 0

            val captionLayout =
                super.relayout(
                    params,
                    parentContainer,
                    display,
                    decorWindowContext,
                    startT,
                    finishT,
                    wct,
                )

            handleMenu?.relayout(
                startT,
                taskInfo.configuration,
                captionLayout.captionX,
                captionLayout.captionY,
            )

            if (configChanged && isOpenByDefaultDialogActive) {
                // Config changed, so destroy the old dialog and create a new one.
                // The new one will inflate with the correct resources.
                openByDefaultDialog?.dismiss() // Triggers onDialogDismissed, setting it to null
                createOpenByDefaultDialog()
            } else {
                // No config change, just relayout the existing dialog for size/position changes.
                openByDefaultDialog?.relayout(taskInfo)
            }

            updateViewHolder(captionLayout)

            if (!params.hasGlobalFocus) {
                closeHandleMenu()
                closeManageWindowsMenu()
            }

            notifyCaptionStateChanged(captionLayout)
            return captionLayout
        }

    private fun notifyNoCaption() {
        if (!desktopState.canEnterDesktopMode || !isEducationOrHandleReportingEnabled) return
        windowDecorHandleRepository.notifyCaptionChanged(CaptionState.NoCaption(taskInfo.taskId))
    }

    private fun notifyCaptionStateChanged(captionLayoutResult: CaptionRelayoutResult) {
        if (!desktopState.canEnterDesktopMode || !isEducationOrHandleReportingEnabled) return
        if (!isCaptionVisible) {
            notifyNoCaption()
            return
        }
        val captionState =
            CaptionState.AppHandle(
                runningTaskInfo = taskInfo,
                isHandleMenuExpanded = isHandleMenuActive,
                globalAppHandleBounds = getCurrentAppHandleBounds(captionLayoutResult),
                appHandleIdentifier = getAppHandleIdentifier(captionLayoutResult),
                isFocused = hasGlobalFocus,
            )
        windowDecorHandleRepository.notifyCaptionChanged(captionState)
    }

    /** Updates app handle position and notifies [AppHandleNotifier] of any changes. */
    private fun getAppHandleIdentifier(
        captionLayoutResult: CaptionRelayoutResult
    ): AppHandleIdentifier =
        AppHandleIdentifier(
            getCurrentAppHandleBounds(captionLayoutResult),
            taskInfo.displayId,
            taskInfo.taskId,
            getAppHandleIdentifierWindowingMode(),
        )

    /** Returns the windowing mode of the App Handle. */
    private fun getAppHandleIdentifierWindowingMode(): AppHandleWindowingMode =
        if (
            BubbleAnythingFlagHelper.enableBubbleToFullscreen() &&
                !desktopState.isDesktopModeSupportedOnDisplay(display)
        ) {
            AppHandleWindowingMode.APP_HANDLE_WINDOWING_MODE_BUBBLE
        } else if (splitScreenController.isTaskInSplitScreen(taskInfo.taskId)) {
            AppHandleWindowingMode.APP_HANDLE_WINDOWING_MODE_SPLIT_SCREEN
        } else {
            AppHandleWindowingMode.APP_HANDLE_WINDOWING_MODE_FULLSCREEN
        }

    /** Returns the current bounds relative to the parent task. */
    private fun getCurrentAppHandleBounds(captionLayoutResult: CaptionRelayoutResult): Rect {
        val bounds =
            Rect(
                captionLayoutResult.captionX,
                captionLayoutResult.captionY,
                captionLayoutResult.captionX + captionLayoutResult.captionWidth,
                captionLayoutResult.captionY + captionLayoutResult.captionHeight,
            )

        if (
            splitScreenController.getSplitPosition(taskInfo.taskId) ==
                SPLIT_POSITION_BOTTOM_OR_RIGHT
        ) {
            if (splitScreenController.isLeftRightSplit) {
                // If this is the right split task, add left stage's width.
                val rightStageBounds =
                    Rect().also { splitScreenController.getStageBounds(Rect(), it) }
                bounds.offset(rightStageBounds.left, /* dy= */ 0)
            } else {
                val bottomStageBounds =
                    Rect().also { splitScreenController.getRefStageBounds(Rect(), it) }
                bounds.offset(/* dx= */ 0, bottomStageBounds.top)
            }
        }
        return bounds
    }

    /**
     * Dispose of the view used to forward inputs in status bar region. Intended to be used any time
     * handle is no longer visible.
     */
    private fun disposeStatusBarInputLayer() {
        viewHolder.disposeStatusBarInputLayer()
    }

    /** Gets the global coordinates of the app handle's position. */
    private fun getHandlePosition(captionLayoutResult: CaptionRelayoutResult): Point {
        val position = Point(captionLayoutResult.captionX, captionLayoutResult.captionY)
        if (
            splitScreenController.getSplitPosition(taskInfo.taskId) ==
                SPLIT_POSITION_BOTTOM_OR_RIGHT
        ) {
            if (splitScreenController.isLeftRightSplit) {
                // If this is the right split task, add left stage's width.
                val leftStageBounds = Rect()
                splitScreenController.getStageBounds(leftStageBounds, Rect())
                position.x += leftStageBounds.width()
            } else {
                val bottomStageBounds = Rect()
                splitScreenController.getRefStageBounds(Rect(), bottomStageBounds)
                position.y += bottomStageBounds.top
            }
        }
        return position
    }

    /** Update the view holder for app handle. */
    private fun updateViewHolder(captionLayoutResult: CaptionRelayoutResult) {
            viewHolder.bindData(
                HandleData(
                    taskInfo,
                    getHandlePosition(captionLayoutResult),
                    captionLayoutResult.captionWidth,
                    captionLayoutResult.captionHeight,
                    showInputLayer,
                    isCaptionVisible,
                )
            )
        }

    private fun createOpenByDefaultDialog() {
        if (isOpenByDefaultDialogActive) return
        openByDefaultDialog =
            OpenByDefaultDialog(
                context,
                userContext,
                transitions,
                taskInfo,
                taskSurface,
                displayController,
                taskResourceLoader,
                surfaceControlTransactionSupplier,
                mainDispatcher,
                mainScope,
                object : DialogLifecycleListener {
                    override fun onDialogDismissed() {
                        openByDefaultDialog = null
                    }
                },
                desktopModeUiEventLogger,
            )
    }

    /** Updates app info and creates and displays handle menu window. */
    override fun createHandleMenu(minimumInstancesFound: Boolean) {
        if (isHandleMenuActive) return
        mainScope.launch {
            val isBrowserApp = isBrowserApp()
            val appToWebIntent =
                if (canShowAppLinks(display, desktopState)) {
                    appToWebRepository.getAppToWebIntent(taskInfo, isBrowserApp)
                } else {
                    // Skip request for assist content as it is only used for links, which are not
                    // supported
                    null
                }
            createHandleMenu(
                openInAppOrBrowserIntent = appToWebIntent,
                isBrowserApp = isBrowserApp,
                minimumInstancesFound = minimumInstancesFound,
            )
        }
    }

    /** Creates and shows the handle menu. */
    private fun createHandleMenu(
        openInAppOrBrowserIntent: Intent?,
        isBrowserApp: Boolean,
        minimumInstancesFound: Boolean,
    ) {
        val supportsMultiInstance =
            multiInstanceHelper.supportsMultiInstanceSplit(
                taskInfo.baseActivity,
                taskInfo.userId,
            ) && DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_MULTI_INSTANCE_FEATURES.isTrue
        val shouldShowManageWindowsButton = supportsMultiInstance && minimumInstancesFound
        val shouldShowChangeAspectRatioButton = shouldShowChangeAspectRatioButton(taskInfo)
        val shouldShowRestartButton = shouldShowRestartButton(taskInfo)
        viewHolder.onHandleMenuOpened()
        handleMenu =
            handleMenuFactory
                .create(
                    mainDispatcher = mainDispatcher,
                    mainScope = mainScope,
                    context = decorWindowContext,
                    taskInfo = taskInfo,
                    parentSurface = decorationSurface,
                    display = display,
                    windowManagerWrapper = windowManagerWrapper,
                    windowDecorationActions = windowDecorationActions,
                    taskResourceLoader = taskResourceLoader,
                    // TODO(b/409648813): Have handle menus use [CaptionType]
                    layoutResId = R.layout.desktop_mode_app_handle,
                    splitScreenController = splitScreenController,
                    shouldShowWindowingPill = desktopState.canEnterDesktopModeOrShowAppHandle,
                    shouldShowNewWindowButton = supportsMultiInstance,
                    shouldShowManageWindowsButton = shouldShowManageWindowsButton,
                    shouldShowChangeAspectRatioButton = shouldShowChangeAspectRatioButton,
                    shouldShowDesktopModeButton =
                        desktopState.isDesktopModeSupportedOnDisplay(display),
                    shouldShowRestartButton = shouldShowRestartButton,
                    isBrowserApp = isBrowserApp,
                    openInAppOrBrowserIntent = openInAppOrBrowserIntent,
                    desktopModeUiEventLogger = desktopModeUiEventLogger,
                    captionView = viewHolder.captionHandle,
                    captionWidth = captionLayoutResult.captionWidth,
                    captionHeight = captionLayoutResult.captionHeight,
                    captionX = captionLayoutResult.captionX,
                    captionY = captionLayoutResult.captionY,
                )
                .apply {
                    show(
                        openInAppOrBrowserClickListener = { intent ->
                            windowDecorationActions.onOpenInBrowser(taskInfo.taskId, intent)
                            appToWebRepository.onCapturedLinkUsed(taskInfo.taskId)
                            if (
                                DesktopExperienceFlags
                                    .ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION_INTEGRATION
                                    .isTrue
                            ) {
                                windowDecorHandleRepository.onAppToWebUsage()
                            }
                        },
                        onOpenByDefaultClickListener = { createOpenByDefaultDialog() },
                        onCloseMenuClickListener = { closeHandleMenu() },
                        onOutsideTouchListener = { closeHandleMenu() },
                        onHandleMenuClicked = { closeHandleMenu() },
                        forceShowSystemBars = inFullImmersive,
                    )
                }
        notifyCaptionStateChanged(captionLayoutResult)
    }

    /** Close the handle menu window. */
    private fun closeHandleMenu() {
        if (!isHandleMenuActive) return
        viewHolder.onHandleMenuClosed()
        handleMenu?.close()
        handleMenu = null
        if (desktopState.canEnterDesktopMode && isEducationOrHandleReportingEnabled) {
            notifyCaptionStateChanged(captionLayoutResult)
        }
    }

    /** Checks if a [MotionEvent] occurs in caption. */
    fun checkTouchEventInCaption(ev: MotionEvent): Boolean {
        val inputPoint = offsetCaptionLocation(ev)
        return inputPoint.x >= captionLayoutResult.captionX &&
            inputPoint.x <= captionLayoutResult.captionX + captionLayoutResult.captionWidth &&
            inputPoint.y >= 0 &&
            inputPoint.y <= captionLayoutResult.captionHeight
    }

    /** Offset the coordinates of a [MotionEvent] to be in the same coordinate space as caption. */
    private fun offsetCaptionLocation(ev: MotionEvent): PointF {
        val result = PointF(ev.x, ev.y)
        val taskInfo = taskOrganizer.getRunningTaskInfo(taskInfo.taskId) ?: return result
        val positionInParent = taskInfo.positionInParent
        result.offset(-positionInParent.x.toFloat(), -positionInParent.y.toFloat())
        return result
    }

    /**
     * Indicates that an app handle drag has been interrupted, this can happen e.g. if we receive an
     * unknown transition during the drag-to-desktop transition.
     */
    fun handleDragInterrupted() {
        viewHolder.setHandleHovered(false)
        viewHolder.setHandlePressed(false)
    }

    private fun isBrowserApp(): Boolean =
        taskInfo.baseActivity?.let { isBrowserApp(userContext, it.packageName, userContext.userId) }
            ?: false

    override fun createCaptionView(): WindowDecorationViewHolder<HandleData> {
        val appHandleViewHolder =
            appHandleViewHolderFactory.create(
                // View holder should inflate the caption's root view
                rootView = null,
                context = decorWindowContext,
                onCaptionTouchListener = onCaptionTouchListener,
                onCaptionButtonClickListener = onCaptionButtonClickListener,
                windowManagerWrapper = windowManagerWrapper,
                handler = mainHandler,
                desktopModeUiEventLogger = desktopModeUiEventLogger,
            )
        viewHolder = appHandleViewHolder
        return appHandleViewHolder
    }

    /** Creates and shows the manage windows menu. */
    override fun createManageWindowsMenu(snapshotList: ArrayList<Pair<Int, TaskSnapshot>>) {
        manageWindowsMenu =
            DesktopHandleManageWindowsMenu(
                callerTaskInfo = taskInfo,
                splitScreenController = splitScreenController,
                captionX = captionLayoutResult.captionX,
                captionWidth = captionLayoutResult.captionWidth,
                windowManagerWrapper = windowManagerWrapper,
                desktopState = desktopState,
                context = context,
                snapshotList = snapshotList,
                onIconClickListener = { requestedTaskId ->
                    closeManageWindowsMenu()
                    windowDecorationActions.onOpenInstance(taskInfo, requestedTaskId)
                },
                onOutsideClickListener = { closeManageWindowsMenu() },
            )
    }

    private fun closeManageWindowsMenu() {
        manageWindowsMenu?.animateClose()
        manageWindowsMenu = null
    }

    override fun getCaptionHeight(): Int =
        SystemBarUtils.getStatusBarHeight(decorWindowContext.resources, display.cutout)

    override fun getCaptionWidth(): Int =
        context.resources.getDimensionPixelSize(R.dimen.desktop_mode_fullscreen_decor_caption_width)

    override val occludingElements: List<OccludingElement> = emptyList()

    override fun close(wct: WindowContainerTransaction, t: SurfaceControl.Transaction): Boolean {
        closeHandleMenu()
        closeManageWindowsMenu()
        disposeStatusBarInputLayer()
        viewHolder.close()
        notifyNoCaption()
        return super.close(wct, t)
    }
}
