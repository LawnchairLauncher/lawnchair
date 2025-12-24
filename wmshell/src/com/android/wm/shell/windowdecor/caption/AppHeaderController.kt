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
import android.graphics.Rect
import android.os.Handler
import android.os.Trace
import android.view.Display
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.View
import android.view.View.OnLongClickListener
import android.view.WindowInsets
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import android.window.TaskSnapshot
import android.window.WindowContainerTransaction
//import com.android.app.tracing.traceSection
import com.android.internal.policy.SystemBarUtils.getDesktopViewAppHeaderHeightId
import com.android.window.flags2.Flags
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.apptoweb.AppToWebRepository
import com.android.wm.shell.apptoweb.OpenByDefaultDialog
import com.android.wm.shell.apptoweb.OpenByDefaultDialog.DialogLifecycleListener
import com.android.wm.shell.apptoweb.canShowAppLinks
import com.android.wm.shell.apptoweb.isBrowserApp
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.MultiInstanceHelper
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.desktopmode.CaptionState
import com.android.wm.shell.desktopmode.CaptionState.AppHeader
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository
import com.android.wm.shell.desktopmode.isTaskMaximized
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DefaultMaximizeMenuFactory
import com.android.wm.shell.windowdecor.DesktopHeaderManageWindowsMenu
import com.android.wm.shell.windowdecor.HandleMenu
import com.android.wm.shell.windowdecor.HandleMenu.Companion.shouldShowChangeAspectRatioButton
import com.android.wm.shell.windowdecor.HandleMenu.Companion.shouldShowRestartButton
import com.android.wm.shell.windowdecor.HandleMenu.HandleMenuFactory
import com.android.wm.shell.windowdecor.HandleMenuController
import com.android.wm.shell.windowdecor.ManageWindowsMenuController
import com.android.wm.shell.windowdecor.MaximizeMenu
import com.android.wm.shell.windowdecor.MaximizeMenuController
import com.android.wm.shell.windowdecor.MaximizeMenuFactory
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.WindowDecoration2.RelayoutParams
import com.android.wm.shell.windowdecor.WindowDecoration2.SurfaceControlViewHostFactory
import com.android.wm.shell.windowdecor.WindowDecorationActions
import com.android.wm.shell.windowdecor.WindowManagerWrapper
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.extension.isDragResizable
import com.android.wm.shell.windowdecor.extension.requestingImmersive
import com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder
import com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder.HeaderData
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.launch

/**
 * Controller for the app header. Creates, updates, and removes the views of the caption and its
 * menus.
 */
class AppHeaderController(
    taskInfo: RunningTaskInfo,
    windowDecorViewHostSupplier: WindowDecorViewHostSupplier<WindowDecorViewHost>,
    private val context: Context,
    private val userContext: Context,
    private val displayController: DisplayController,
    private val taskResourceLoader: WindowDecorTaskResourceLoader,
    private val splitScreenController: SplitScreenController,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val transitions: Transitions,
    private val taskSurface: SurfaceControl,
    private val decorationSurface: SurfaceControl,
    @ShellMainThread private val mainHandler: Handler,
    @ShellMainThread private val mainExecutor: ShellExecutor,
    @ShellMainThread private val mainDispatcher: MainCoroutineDispatcher,
    @ShellMainThread private val mainScope: CoroutineScope,
    @ShellBackgroundThread private val bgExecutor: ShellExecutor,
    private val syncQueue: SyncTransactionQueue,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val windowManagerWrapper: WindowManagerWrapper,
    private val multiInstanceHelper: MultiInstanceHelper,
    private val windowDecorCaptionRepository: WindowDecorCaptionRepository,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
    private val desktopState: DesktopState,
    private val windowDecorationActions: WindowDecorationActions,
    private val decorWindowContext: Context,
    private val onCaptionTouchListener: View.OnTouchListener,
    private val onCaptionButtonClickListener: View.OnClickListener,
    private val onLongClickListener: OnLongClickListener,
    private val onCaptionGenericMotionListener: View.OnGenericMotionListener,
    private val appToWebRepository: AppToWebRepository,
    private val maximizeMenuFactory: MaximizeMenuFactory = DefaultMaximizeMenuFactory,
    private val handleMenuFactory: HandleMenuFactory = HandleMenuFactory,
    private val appHeaderViewHolderFactory: AppHeaderViewHolder.Factory =
        AppHeaderViewHolder.Factory(),
    private val surfaceControlBuilderSupplier: () -> SurfaceControl.Builder = {
        SurfaceControl.Builder()
    },
    private val surfaceControlTransactionSupplier: () -> SurfaceControl.Transaction = {
        SurfaceControl.Transaction()
    },
    surfaceControlViewHostFactory: SurfaceControlViewHostFactory =
        object : SurfaceControlViewHostFactory {},
) :
    CaptionController<WindowDecorLinearLayout>(
        taskInfo,
        windowDecorViewHostSupplier,
        surfaceControlBuilderSupplier,
        surfaceControlViewHostFactory,
    ),
    MaximizeMenuController,
    HandleMenuController,
    ManageWindowsMenuController {

    override val captionType = CaptionType.APP_HEADER

    private lateinit var viewHolder: AppHeaderViewHolder

    private var handleMenu: HandleMenu? = null
    private var openByDefaultDialog: OpenByDefaultDialog? = null
    private var maximizeMenu: MaximizeMenu? = null
    private var manageWindowsMenu: DesktopHeaderManageWindowsMenu? = null

    override val maximizeMenuController = this
    override val handleMenuController = this
    override val manageWindowsMenuController = this

    override val isMaximizeMenuActive: Boolean
        get() = maximizeMenu != null

    override val isHandleMenuActive: Boolean
        get() = handleMenu != null

    private val isOpenByDefaultDialogActive
        get() = openByDefaultDialog != null

    private val inFullImmersive
        get() =
            desktopUserRepositories
                .getProfile(taskInfo.userId)
                .isTaskInFullImmersiveState(taskInfo.taskId)

    private val taskPositionInParent
        get() = taskInfo.positionInParent

    private val display
        get() = displayController.getDisplay(taskInfo.displayId)

    private val closeMaximizeWindowRunnable = Runnable { closeMaximizeMenu() }
    private val isEducationOrHandleReportingEnabled =
        Flags.enableDesktopWindowingAppHandleEducation() ||
            DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION_INTEGRATION
                .isTrue ||
            DesktopExperienceFlags.ENABLE_APP_HANDLE_POSITION_REPORTING.isTrue

    private var isMaximizeMenuHovered = false
    private var isAppHeaderMaximizeButtonHovered = false
    private var loadAppInfoJob: Job? = null

    override fun relayout(
        params: RelayoutParams,
        parentContainer: SurfaceControl,
        display: Display,
        decorWindowContext: Context,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        wct: WindowContainerTransaction,
    ): CaptionRelayoutResult {
            // If we get a relayout call while hovering over maximize button in the app header but
            // the task has lost focus, explicitly cancel the hover (since we don't get a HOVER_EXIT
            // signal in this case).
            if (!taskInfo.isFocused && isAppHeaderMaximizeButtonHovered) {
                setAppHeaderMaximizeButtonHovered(hovered = false)
                onMaximizeButtonHoverExit()
            }

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
                // Add top padding to the caption Y so that the menu is shown over what is the
                // actual contents of the caption, ignoring padding. This is currently relevant
                // to the Header in desktop immersive.
                captionLayout.captionY + captionLayout.captionTopPadding,
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

            updateMaximizeMenu(startT)

            updateViewHolder(params.hasGlobalFocus)

            if (!params.hasGlobalFocus) {
                closeHandleMenu()
                closeManageWindowsMenu()
                closeMaximizeMenu()
            }

            notifyCaptionStateChanged()
            return captionLayout
        }

    override fun getCaptionTopPadding(): Int {
        if (!inFullImmersive) {
            return 0
        }
        val displayInsetsState = displayController.getInsetsState(taskInfo.displayId)
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        val systemBarInsets =
            displayInsetsState.calculateInsets(
                /* frame= */ taskBounds,
                /* hostBounds= */ taskBounds,
                /* types= */ WindowInsets.Type.systemBars() and
                    WindowInsets.Type.captionBar().inv(),
                /* ignoreVisibility= */ false,
            )
        return systemBarInsets.top
    }

    private fun notifyCaptionStateChanged() {
        if (!desktopState.canEnterDesktopMode || !isEducationOrHandleReportingEnabled) {
            return
        }
        if (!isCaptionVisible || !hasGlobalFocus) {
            notifyNoCaption()
            return
        }
        viewHolder.runOnAppChipGlobalLayout { notifyAppHeaderStateChanged() }
    }

    private fun notifyNoCaption() {
        if (!desktopState.canEnterDesktopMode || !isEducationOrHandleReportingEnabled) return
        windowDecorCaptionRepository.notifyCaptionChanged(CaptionState.NoCaption(taskInfo.taskId))
    }

    private fun notifyAppHeaderStateChanged() {
        val appChipPositionInWindow = viewHolder.getAppChipLocationInWindow()
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        val appChipGlobalPosition =
            Rect(
                taskBounds.left + appChipPositionInWindow.left,
                taskBounds.top + appChipPositionInWindow.top,
                taskBounds.left + appChipPositionInWindow.right,
                taskBounds.top + appChipPositionInWindow.bottom,
            )
        val captionState =
            AppHeader(
                runningTaskInfo = taskInfo,
                isHeaderMenuExpanded = isHandleMenuActive,
                globalAppChipBounds = appChipGlobalPosition,
                isFocused = hasGlobalFocus,
            )

        windowDecorCaptionRepository.notifyCaptionChanged(captionState)
    }

    private fun updateMaximizeMenu(startT: SurfaceControl.Transaction) {
        if (!taskInfo.isDragResizable(inFullImmersive) || !isMaximizeMenuActive) return
        if (!taskInfo.isVisible()) {
            closeMaximizeMenu()
        } else {
            maximizeMenu?.positionMenu(startT)
        }
    }

    private fun calculateMaximizeMenuPosition(menuWidth: Int, menuHeight: Int): Point {
        val position = Point()
        val displayLayout =
            displayController.getDisplayLayout(taskInfo.displayId) ?: return position

        val displayWidth = displayLayout.width()
        val displayHeight = displayLayout.height()
        val captionHeight = getCaptionHeight()

        val maximizeButtonLocation = viewHolder.getMaximizeButtonPosition()

        var menuLeft =
            taskPositionInParent.x + maximizeButtonLocation[0] -
                (menuWidth - viewHolder.maximizeButtonWidth) / 2
        var menuTop = taskPositionInParent.y + captionHeight
        val menuRight = menuLeft + menuWidth
        val menuBottom = menuTop + menuHeight

        // If the menu is out of screen bounds, shift it as needed
        if (menuLeft < 0) {
            menuLeft = 0
        } else if (menuRight > displayWidth) {
            menuLeft = (displayWidth - menuWidth)
        }
        if (menuBottom > displayHeight) {
            menuTop = (displayHeight - menuHeight)
        }

        return Point(menuLeft, menuTop)
    }

    /** Create and display maximize menu window */
    override fun createMaximizeMenu() {
        if (isMaximizeMenuActive) return
        desktopModeUiEventLogger.log(
            taskInfo,
            DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_REVEAL_MENU,
        )
        maximizeMenu =
            maximizeMenuFactory
                .create(
                    syncQueue = syncQueue,
                    rootTdaOrganizer = rootTaskDisplayAreaOrganizer,
                    displayController = displayController,
                    windowDecorationActions = windowDecorationActions,
                    taskInfo = taskInfo,
                    decorWindowContext = decorWindowContext,
                    positionSupplier = { width, height ->
                        calculateMaximizeMenuPosition(width, height)
                    },
                    transactionSupplier = surfaceControlTransactionSupplier,
                    desktopModeUiEventLogger = desktopModeUiEventLogger,
                )
                .apply {
                    show(
                        isTaskInImmersiveMode = inFullImmersive,
                        showImmersiveOption =
                            DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue &&
                                taskInfo.requestingImmersive,
                        showSnapOptions = taskInfo.isResizeable,
                        onHoverListener = { hovered: Boolean ->
                            isMaximizeMenuHovered = hovered
                            onMaximizeHoverStateChanged()
                        },
                        onOutsideTouchListener = { closeMaximizeMenu() },
                        onMaximizeMenuClickedListener = { closeMaximizeMenu() },
                    )
                }
    }

    /** Set whether the app header's maximize button is hovered. */
    override fun setAppHeaderMaximizeButtonHovered(hovered: Boolean) {
        isAppHeaderMaximizeButtonHovered = hovered
        onMaximizeHoverStateChanged()
    }

    /**
     * Called when either one of the maximize button in the app header or the maximize menu has
     * changed its hover state.
     */
    override fun onMaximizeHoverStateChanged() {
        if (!isMaximizeMenuHovered && !isAppHeaderMaximizeButtonHovered) {
            // Neither is hovered, close the menu.
            if (isMaximizeMenuActive) {
                mainHandler.postDelayed(closeMaximizeWindowRunnable, CLOSE_MAXIMIZE_MENU_DELAY_MS)
            }
            return
        }
        // At least one of the two is hovered, cancel the close if needed.
        mainHandler.removeCallbacks(closeMaximizeWindowRunnable)
    }

    /** Close the maximize menu window if open. */
    private fun closeMaximizeMenu() {
        maximizeMenu?.close {
            // Request the accessibility service to refocus on the maximize button after closing
            // the menu.
            a11yFocusMaximizeButton()
        }
        maximizeMenu = null
    }

    /** Request direct a11y focus on the maximize button */
    fun a11yFocusMaximizeButton() = viewHolder.requestAccessibilityFocus()

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

    private fun canOpenMaximizeMenu(animatingTaskResizeOrReposition: Boolean): Boolean =
        if (!DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue) {
            !animatingTaskResizeOrReposition
        } else {
            val inImmersiveAndRequesting = inFullImmersive && taskInfo.requestingImmersive
            !animatingTaskResizeOrReposition && !inImmersiveAndRequesting
        }

    /**
     * Called when there is a [MotionEvent.ACTION_HOVER_EXIT] on the maximize window button.
     *
     * TODO(b/409648813): Move all hover logic to view holder
     */
    override fun onMaximizeButtonHoverExit() {
        viewHolder.onMaximizeWindowHoverExit()
    }

    /**
     * Called when there is a [MotionEvent.ACTION_HOVER_ENTER] on the maximize window button.
     *
     * TODO(b/409648813): Move all hover logic to view holder
     */
    override fun onMaximizeButtonHoverEnter() {
        viewHolder.onMaximizeWindowHoverEnter()
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
                    layoutResId = R.layout.desktop_mode_app_header,
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
                    captionView = viewHolder.rootView,
                    captionWidth = captionLayoutResult.captionWidth,
                    captionHeight = captionLayoutResult.captionHeight,
                    captionX = captionLayoutResult.captionX,
                    // Add top padding to the caption Y so that the menu is shown over what is the
                    // actual contents of the caption, ignoring padding. This is currently relevant
                    // to the Header in desktop immersive.
                    captionY = captionLayoutResult.captionY + captionLayoutResult.captionTopPadding,
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
                                windowDecorCaptionRepository.onAppToWebUsage()
                            }
                        },
                        onOpenByDefaultClickListener = { createOpenByDefaultDialog() },
                        onCloseMenuClickListener = { closeHandleMenu() },
                        onOutsideTouchListener = { closeHandleMenu() },
                        onHandleMenuClicked = { closeHandleMenu() },
                        forceShowSystemBars = inFullImmersive,
                    )
                }
        notifyCaptionStateChanged()
    }

    /** Close the handle menu window. */
    private fun closeHandleMenu() {
        if (!isHandleMenuActive) return
        viewHolder.onHandleMenuClosed()
        handleMenu?.close()
        handleMenu = null
        if (desktopState.canEnterDesktopMode && isEducationOrHandleReportingEnabled) {
            notifyCaptionStateChanged()
        }
    }

    private fun isBrowserApp(): Boolean =
        taskInfo.baseActivity?.let { isBrowserApp(userContext, it.packageName, userContext.userId) }
            ?: false

    /** Creates and shows the manage windows menu. */
    override fun createManageWindowsMenu(snapshotList: ArrayList<Pair<Int, TaskSnapshot>>) {
        // The menu uses display-wide coordinates for positioning, so make position the sum
        // of task position and caption position.
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        manageWindowsMenu =
            DesktopHeaderManageWindowsMenu(
                callerTaskInfo = taskInfo,
                x = taskBounds.left + captionLayoutResult.captionX,
                y =
                    taskBounds.top +
                        captionLayoutResult.captionY +
                        captionLayoutResult.captionTopPadding,
                displayController = displayController,
                rootTdaOrganizer = rootTaskDisplayAreaOrganizer,
                context = context,
                desktopUserRepositories = desktopUserRepositories,
                surfaceControlBuilderSupplier = surfaceControlBuilderSupplier,
                surfaceControlTransactionSupplier = surfaceControlTransactionSupplier,
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

    /** Update the view holder for app header. */
    private fun updateViewHolder(
        hasGlobalFocus: Boolean,
        animatingTaskResizeOrReposition: Boolean = false,
    ) {
            val displayId = taskInfo.displayId
            val displayLayout = displayController.getDisplayLayout(displayId) ?: return
            viewHolder.bindData(
                HeaderData(
                    taskInfo,
                    isTaskMaximized(taskInfo, displayLayout),
                    inFullImmersive,
                    hasGlobalFocus,
                    canOpenMaximizeMenu(animatingTaskResizeOrReposition),
                    isCaptionVisible,
                )
            )
        }

    override fun onAnimatingTaskRepositioningOrResize(animatingTaskResizeOrReposition: Boolean) {
        updateViewHolder(hasGlobalFocus, animatingTaskResizeOrReposition)
    }

    override fun createCaptionView(): WindowDecorationViewHolder<HeaderData> {
        val appHeaderViewHolder =
            appHeaderViewHolderFactory.create(
                // View holder should inflate the caption's root view
                rootView = null,
                context = decorWindowContext,
                windowDecorationActions = windowDecorationActions,
                onCaptionTouchListener = onCaptionTouchListener,
                onCaptionButtonClickListener = onCaptionButtonClickListener,
                onLongClickListener = onLongClickListener,
                onCaptionGenericMotionListener = onCaptionGenericMotionListener,
                onMaximizeHoverAnimationFinishedListener = { createMaximizeMenu() },
                desktopModeUiEventLogger = desktopModeUiEventLogger,
            )

        loadAppInfoJob =
            mainScope.launch {
                val (name, icon) = taskResourceLoader.getNameAndHeaderIcon(taskInfo)
                viewHolder.setAppName(name)
                viewHolder.setAppIcon(icon)
                if (desktopState.canEnterDesktopMode && isEducationOrHandleReportingEnabled) {
                    notifyCaptionStateChanged()
                }
            }
        viewHolder = appHeaderViewHolder
        return appHeaderViewHolder
    }

    /** Returns the valid drag area for a task based on elements in the app chip. */
    override fun calculateValidDragArea(): Rect {
        val resources = context.resources
        val leftButtonsWidth =
            resources.getDimensionPixelSize(R.dimen.desktop_mode_app_details_width_minus_text) +
                viewHolder.appNameTextWidth
        val requiredEmptySpace =
            resources.getDimensionPixelSize(R.dimen.freeform_required_visible_empty_space_in_header)
        val rightButtonsWidth =
            resources.getDimensionPixelSize(R.dimen.desktop_mode_right_edge_buttons_width)
        val taskWidth = taskInfo.configuration.windowConfiguration.bounds.width()
        val layout = displayController.getDisplayLayout(taskInfo.displayId) ?: return Rect()
        val displayWidth = layout.width()
        val stableBounds = Rect()
        layout.getStableBounds(stableBounds)
        return Rect(
            determineMinX(leftButtonsWidth, rightButtonsWidth, requiredEmptySpace, taskWidth),
            stableBounds.top,
            determineMaxX(
                leftButtonsWidth,
                rightButtonsWidth,
                requiredEmptySpace,
                taskWidth,
                displayWidth,
            ),
            determineMaxY(requiredEmptySpace, stableBounds),
        )
    }

    /** Determine the lowest x coordinate of a freeform task. Used for restricting drag inputs. */
    private fun determineMinX(
        leftButtonsWidth: Int,
        rightButtonsWidth: Int,
        requiredEmptySpace: Int,
        taskWidth: Int,
    ): Int =
        // Do not let apps with < 48dp empty header space go off the left edge at all.
        if (leftButtonsWidth + rightButtonsWidth + requiredEmptySpace > taskWidth) {
            0
        } else {
            -taskWidth + requiredEmptySpace + rightButtonsWidth
        }

    /** Determine the highest x coordinate of a freeform task. Used for restricting drag inputs. */
    private fun determineMaxX(
        leftButtonsWidth: Int,
        rightButtonsWidth: Int,
        requiredEmptySpace: Int,
        taskWidth: Int,
        displayWidth: Int,
    ): Int =
        // Do not let apps with < 48dp empty header space go off the right edge at all.
        if (leftButtonsWidth + rightButtonsWidth + requiredEmptySpace > taskWidth) {
            displayWidth - taskWidth
        } else {
            displayWidth - requiredEmptySpace - leftButtonsWidth
        }

    /** Determine the highest y coordinate of a freeform task. Used for restricting drag inputs. */
    private fun determineMaxY(requiredEmptySpace: Int, stableBounds: Rect): Int =
        stableBounds.bottom - requiredEmptySpace

    override fun getCaptionHeight(): Int =
        context.resources.getDimensionPixelSize(getDesktopViewAppHeaderHeightId()) +
            getCaptionTopPadding()

    override fun getCaptionWidth(): Int =
        taskInfo.getConfiguration().windowConfiguration.bounds.width()

    override val occludingElements: List<OccludingElement>
        get() = viewHolder.getOccludingElements()

    /**
     * Announces that the app window is now being focused for accessibility. This is used after a
     * window is minimized/closed, and a new app window gains focus.
     */
    fun a11yAnnounceFocused() {
        viewHolder.a11yAnnounceFocused()
    }

    override fun close(wct: WindowContainerTransaction, t: SurfaceControl.Transaction): Boolean {
        loadAppInfoJob?.cancel()
        closeHandleMenu()
        closeManageWindowsMenu()
        closeMaximizeMenu()
        viewHolder.close()
        if (desktopState.canEnterDesktopMode && isEducationOrHandleReportingEnabled) {
            notifyNoCaption()
        }
        return super.close(wct, t)
    }

    private companion object {
        private const val TAG = "AppHeaderController"
        const val CLOSE_MAXIMIZE_MENU_DELAY_MS = 150L
    }
}
