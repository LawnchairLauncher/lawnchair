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

package com.android.wm.shell.windowdecor

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources.ID_NULL
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Region
import android.os.Handler
import android.os.Trace
import android.os.UserHandle
import android.util.Size
import android.view.Choreographer
import android.view.InsetsSource.FLAG_FORCE_CONSUMING
import android.view.InsetsSource.FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.View.OnClickListener
import android.view.View.OnGenericMotionListener
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.INPUT_FEATURE_SPY
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManagerGlobal
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import android.window.WindowContainerTransaction
//import com.android.app.tracing.traceSection
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.window.flags2.Flags
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.apptoweb.AppToWebRepository
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.LockTaskChangeListener
import com.android.wm.shell.common.MultiInstanceHelper
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.desktopmode.DesktopModeEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DragPositioningCallbackUtility.DragEventListener
import com.android.wm.shell.windowdecor.DragResizeWindowGeometry.DisabledEdge
import com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getFineResizeCornerSize
import com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getLargeResizeCornerSize
import com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getResizeEdgeHandleSize
import com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getResizeHandleEdgeInset
import com.android.wm.shell.windowdecor.caption.AppHandleController
import com.android.wm.shell.windowdecor.caption.AppHeaderController
import com.android.wm.shell.windowdecor.caption.CaptionController
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.ExclusionRegionListener
import com.android.wm.shell.windowdecor.common.Theme
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.extension.getDimensionPixelSize
import com.android.wm.shell.windowdecor.extension.isDragResizable
import com.android.wm.shell.windowdecor.extension.isTransparentCaptionBarAppearance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher

/**
 * Default window decoration implementation that controls both the app handle and the app header
 * captions. This class also adds various decorations to the window including the [ResizeVeil].
 */
class DefaultWindowDecoration
@JvmOverloads
constructor(
    taskInfo: RunningTaskInfo,
    taskSurface: SurfaceControl,
    val context: Context,
    private val userContext: Context,
    private val displayController: DisplayController,
    private val taskResourceLoader: WindowDecorTaskResourceLoader,
    private val splitScreenController: SplitScreenController,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val taskOrganizer: ShellTaskOrganizer,
    @ShellMainThread private val handler: Handler,
    @ShellMainThread private val mainExecutor: ShellExecutor,
    @ShellMainThread private val mainDispatcher: MainCoroutineDispatcher,
    @ShellMainThread private val mainScope: CoroutineScope,
    @ShellBackgroundThread private val bgExecutor: ShellExecutor,
    private val transitions: Transitions,
    private val choreographer: Choreographer,
    private val syncQueue: SyncTransactionQueue,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val windowDecorViewHostSupplier: WindowDecorViewHostSupplier<WindowDecorViewHost>,
    private val multiInstanceHelper: MultiInstanceHelper,
    private val windowDecorCaptionRepository: WindowDecorCaptionRepository,
    private val desktopModeEventLogger: DesktopModeEventLogger,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
    private val desktopModeCompatPolicy: DesktopModeCompatPolicy,
    private val desktopState: DesktopState,
    private val desktopConfig: DesktopConfig,
    private val windowDecorationActions: WindowDecorationActions,
    private val appToWebRepository: AppToWebRepository,
    private val windowManagerWrapper: WindowManagerWrapper =
        WindowManagerWrapper(context.getSystemService(WindowManager::class.java)),
    private val surfaceControlBuilderSupplier: () -> SurfaceControl.Builder = {
        SurfaceControl.Builder()
    },
    private val surfaceControlTransactionSupplier: () -> SurfaceControl.Transaction = {
        SurfaceControl.Transaction()
    },
    private val windowContainerTransactionSupplier: () -> WindowContainerTransaction = {
        WindowContainerTransaction()
    },
    surfaceControlSupplier: () -> SurfaceControl = { SurfaceControl() },
    private val lockTaskChangeListener: LockTaskChangeListener,
) :
    WindowDecoration2<WindowDecorLinearLayout>(
        taskInfo,
        context,
        displayController,
        taskSurface,
        surfaceControlSupplier,
        taskOrganizer,
        handler,
        mainScope,
        transitions,
    ) {
    private lateinit var onClickListener: OnClickListener
    private lateinit var onTouchListener: OnTouchListener
    private lateinit var onLongClickListener: OnLongClickListener
    private lateinit var onGenericMotionListener: OnGenericMotionListener
    private lateinit var exclusionRegionListener: ExclusionRegionListener
    private lateinit var dragPositioningCallback: DragPositioningCallback

    private var disabledResizingEdge = DisabledEdge.NONE
    private val taskPositionInParent = Point()
    private var dragResizeListener: DragResizeInputListener? = null
    private var resizeVeil: ResizeVeil? = null
    private val positionInParent
        get() = taskInfo.positionInParent

    private val inFullImmersive
        get() = desktopUserRepositories.current.isTaskInFullImmersiveState(taskInfo.taskId)

    private val taskBounds
        get() = taskInfo.getConfiguration().windowConfiguration.bounds

    private val taskWidth
        get() = taskBounds.width()

    private val taskHeight
        get() = taskBounds.height()

    /** Returns the current user. */
    val user: UserHandle
        get() = userContext.user

    private val captionType
        get() = captionController?.captionType ?: CaptionController.CaptionType.NO_CAPTION

    val maximizeMenuController: MaximizeMenuController?
        get() = captionController?.maximizeMenuController

    val handleMenuController: HandleMenuController?
        get() = captionController?.handleMenuController

    val manageWindowsMenuController: ManageWindowsMenuController?
        get() = captionController?.manageWindowsMenuController

    init {
        taskResourceLoader.onWindowDecorCreated(taskInfo)
    }

    /** Declares whether the window decoration is being dragged. */
    var isDragging = false

    /**
     * Declares whether a Recents transition is currently active.
     *
     * <p> When a Recents transition is active we allow that transition to take ownership of the
     * corner radius of its task surfaces, so each window decoration should stop updating the corner
     * radius of its task surface during that time.
     */
    var isRecentsTransitionRunning = false
        set(running) {
            field = running
            captionController?.isRecentsTransitionRunning = running
        }

    /** Adds the [dragResizeListener] which gets notified on the task being drag resized. */
    fun addDragResizeListener(dragResizeListener: DragEventListener?) {
        taskDragResizer?.addDragEventListener(dragResizeListener)
    }

    /** Removes the [dragResizeListener] if previously added. */
    fun removeDragResizeListener(dragResizeListener: DragEventListener?) {
        taskDragResizer?.removeDragEventListener(dragResizeListener)
    }

    /** Set the listeners for the decorations. */
    fun setListeners(
        onClickListener: OnClickListener,
        onTouchListener: OnTouchListener,
        onLongClickListener: OnLongClickListener,
        onGenericMotionListener: OnGenericMotionListener,
    ) {
        this.onClickListener = onClickListener
        this.onTouchListener = onTouchListener
        this.onLongClickListener = onLongClickListener
        this.onGenericMotionListener = onGenericMotionListener
    }

    /**
     * Sets the [exclusionRegionListener] which is notified when the exclusion region is changed or
     * dismissed.
     */
    fun setExclusionRegionListener(exclusionRegionListener: ExclusionRegionListener) {
        this.exclusionRegionListener = exclusionRegionListener
    }

    /** Sets the [dragPositioningCallback] which is called when a task is repositioned via drag. */
    fun setDragPositioningCallback(dragPositioningCallback: DragPositioningCallback) {
        this.dragPositioningCallback = dragPositioningCallback
    }

    /**
     * To be called when exclusion region is changed to allow [relayout] to be called if necessary.
     */
    override fun onExclusionRegionChanged(exclusionRegion: Region) {
        if (
            Flags.appHandleNoRelayoutOnExclusionChange() &&
                captionType == CaptionController.CaptionType.APP_HANDLE
        ) {
            // Avoid unnecessary relayouts for app handle. See b/383672263
            return
        }
        relayout(taskInfo, hasGlobalFocus, exclusionRegion)
    }

    /**
     * Disables resizing for the [disabledResizingEdge]. Executes immediately or delays if
     * [shouldDelayUpdate].
     */
    fun updateDisabledResizingEdge(disabledResizingEdge: DisabledEdge, shouldDelayUpdate: Boolean) {
        this.disabledResizingEdge = disabledResizingEdge
        if (shouldDelayUpdate) return
        decorationContainerSurface?.let { updateDragResizeListenerIfNeeded(it) }
    }

    /** Updates all window decorations, including any existing caption. */
    override fun relayout(
        taskInfo: RunningTaskInfo,
        hasGlobalFocus: Boolean,
        displayExclusionRegion: Region,
    ) {
        val t = surfaceControlTransactionSupplier.invoke()
        // The visibility, crop and position of the task should only be set when a task is
        // fluid resizing. In all other cases, it is expected that the transition handler sets
        // those task properties to allow the handler time to animate with full control of the task
        // leash. In general, allowing the window decoration to set any of these is likely to cause
        // incorrect frames and flickering because relayouts from TaskListener#onTaskInfoChanged
        // aren't synchronized with shell transition callbacks, so if they come too early it
        // might show/hide or crop the task at a bad time.
        // Fluid resizing is exempt from this because it intentionally doesn't use shell
        // transitions to resize the task, so onTaskInfoChanged relayouts is the only way to make
        // sure the crop is set correctly.
        val shouldSetTaskVisibilityPositionAndCrop =
            !desktopConfig.isVeiledResizeEnabled && taskDragResizer?.isResizingOrAnimating() == true

        // For headers only (i.e. in freeform): use |applyStartTransactionOnDraw| so that the
        // transaction (that applies task crop) is synced with the buffer transaction (that draws
        // the View). Both will be shown on screen at the same, whereas applying them independently
        // causes flickering. See b/270202228.
        val applyTransactionOnDraw = taskInfo.isFreeform
        relayout(
            taskInfo,
            t,
            t,
            applyTransactionOnDraw,
            shouldSetTaskVisibilityPositionAndCrop,
            hasGlobalFocus,
            displayExclusionRegion,
            inSyncWithTransition = false,
            taskSurface,
        )
        if (!applyTransactionOnDraw) {
            t.apply()
        }
    }

    /** Updates all window decorations, including any existing caption. */
    fun relayout(
        taskInfo: RunningTaskInfo,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        applyStartTransactionOnDraw: Boolean,
        shouldSetTaskVisibilityPositionAndCrop: Boolean,
        hasGlobalFocus: Boolean,
        displayExclusionRegion: Region,
        inSyncWithTransition: Boolean,
        taskSurface: SurfaceControl?,
    ) {
            if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_APP_TO_WEB.isTrue) {
                taskInfo.capturedLink?.let {
                    appToWebRepository.setCapturedLink(
                        taskInfo.taskId,
                        it,
                        taskInfo.capturedLinkTimestamp,
                    )
                }
            }

            if (DesktopExperienceFlags.ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue) {
                displayController.getDisplayContext(taskInfo.displayId)?.let {
                    windowManagerWrapper.updateWindowManager(
                        it.getSystemService(WindowManager::class.java)
                    )
                }
            }

            val relayoutParams =
                getRelayoutParams(
                    context,
                    taskInfo,
                    splitScreenController,
                    applyStartTransactionOnDraw,
                    shouldSetTaskVisibilityPositionAndCrop,
                    hasGlobalFocus,
                    displayExclusionRegion,
                    /* shouldIgnoreCornerRadius= */ isRecentsTransitionRunning &&
                        DesktopModeFlags.ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX.isTrue,
                    desktopModeCompatPolicy.shouldExcludeCaptionFromAppBounds(taskInfo),
                    desktopConfig,
                    inSyncWithTransition,
                )

            val wct = windowContainerTransactionSupplier.invoke()
            val oldDecorationSurface = decorationContainerSurface
            relayout(relayoutParams, startT, finishT, wct, taskSurface)

            // After this line, [WindowDecoration2.taskInfo] is up-to-date and should be
            // used instead of the taskInfo passed to the relayout method.
            if (!wct.isEmpty) {
                if (
                    DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP.isTrue &&
                        relayoutParams.shouldSetAppBounds
                ) {
                    // When expanding from PiP to freeform, we need to start a Transition for
                    // applying
                    // the inset changes so that PiP receives the insets for the final bounds. This
                    // is
                    // because |mShouldSetAppBounds| applies the insets by modifying app bounds,
                    // which
                    // can cause a bounds offset that needs to be reported to transition handlers.
                        handler.post {
                            transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
                        }
                } else {
                        bgExecutor.execute { taskOrganizer.applyTransaction(wct) }
                }
                Trace.endSection()
            }

            decorationContainerSurface?.let {
                updateDragResizeListenerIfNeeded(oldDecorationSurface)
            }
        }

    private fun getRelayoutParams(
        context: Context,
        taskInfo: RunningTaskInfo,
        splitScreenController: SplitScreenController,
        applyStartTransactionOnDraw: Boolean,
        shouldSetTaskVisibilityPositionAndCrop: Boolean,
        hasGlobalFocus: Boolean,
        displayExclusionRegion: Region,
        shouldIgnoreCornerRadius: Boolean,
        shouldExcludeCaptionFromAppBounds: Boolean,
        desktopConfig: DesktopConfig,
        inSyncWithTransition: Boolean,
    ): RelayoutParams {
        val captionType =
            if (taskInfo.isFreeform) {
                CaptionController.CaptionType.APP_HEADER
            } else {
                CaptionController.CaptionType.APP_HANDLE
            }
        val isAppHeader = captionType == CaptionController.CaptionType.APP_HEADER
        val isAppHandle = captionType == CaptionController.CaptionType.APP_HANDLE

        // Allow the handle view to be delayed since the handle is just a small addition to the
        // window, whereas the header cannot be delayed because it is expected to be visible from
        // the first frame.
        val asyncViewHost = isAppHandle

        val isBottomSplit =
            !splitScreenController.isLeftRightSplit &&
                (splitScreenController.getSplitPosition(taskInfo.taskId) ==
                    SPLIT_POSITION_BOTTOM_OR_RIGHT)
        val isInsetSource = (isAppHeader && !inFullImmersive) || isBottomSplit
        var inputFeatures = 0
        var insetSourceFlags = 0
        var shouldSetAppBounds = false
        if (isAppHeader) {
            if (taskInfo.isTransparentCaptionBarAppearance) {
                // The app is requesting to customize the caption bar, which means input on
                // customizable/exclusion regions must go to the app instead of to the system.
                // Custom touchable regions OR spy windows are usually sufficient to satisfy this
                // requirement for the general case, but some edge cases make it so we actually
                // need both:
                // 1) Spy window by itself does not let a11y services "see" through the window and
                // focus the custom content. The touchable region carveout helps here. Note that
                // this is set by |CaptionController#calculateLimitedTouchableRegion|.
                // 2) When the app has a modal window on top of the window that reports exclusion
                // regions, the modal window actually blocks the exclusion region from being
                // reported to SystemUI, which prevents the window decoration from correctly
                // setting the touchable region (of the caption) and thus touching the
                // custom region has the input consumed by the caption and makes it impossible for
                // the modal to be closed in this region, see b/414521306.
                // So by setting the spy feature the input can fall through to the windows below,
                // but more precisely it allows the first motion event over a modal window to fall
                // through and dismiss the modal, even when the caption touchable region is not
                // being limited.
                inputFeatures = inputFeatures or WindowManager.LayoutParams.INPUT_FEATURE_SPY
            } else if (DesktopModeFlags.ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION.isTrue) {
                if (shouldExcludeCaptionFromAppBounds) {
                    shouldSetAppBounds = true
                } else {
                    // Force-consume the caption bar insets when the app tries to hide the
                    // caption. This improves app compatibility of immersive apps.
                    insetSourceFlags = insetSourceFlags or FLAG_FORCE_CONSUMING
                }
            }
            if (DesktopModeFlags.ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION_ALWAYS.isTrue) {
                if (shouldExcludeCaptionFromAppBounds) {
                    shouldSetAppBounds = true
                } else {
                    // Always force-consume the caption bar insets for maximum app compatibility,
                    // including non-immersive apps that just don't handle caption insets properly.
                    insetSourceFlags = insetSourceFlags or FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR
                }
            }
            inputFeatures =
                inputFeatures or WindowManager.LayoutParams.INPUT_FEATURE_DISPLAY_TOPOLOGY_AWARE
        } else if (
            isAppHandle && DesktopExperienceFlags.ENABLE_REMOVE_STATUS_BAR_INPUT_LAYER.isTrue
        ) {
            // Add input feature spy flag if caption is an app handle so that input is not stolen
            // when motion event exits caption view.
            inputFeatures = inputFeatures or INPUT_FEATURE_SPY
        }

        // The configuration used to layout the window decoration. A copy is made instead of using
        // the original reference so that the configuration isn't mutated on config changes and
        // diff checks can be made in WindowDecoration#relayout using the pre/post-relayout
        // configuration. See b/301119301.
        // TODO: b/301119301 - consider moving the config data needed for diffs to relayout params
        // instead of using a whole Configuration as a parameter.
        val windowDecorConfig =
            if (DesktopModeFlags.ENABLE_APP_HEADER_WITH_TASK_DENSITY.isTrue && isAppHeader) {
                // Should match the density of the task. The task may have had its density
                // overridden
                // to be different that SysUI's.
                Configuration(taskInfo.configuration)
            } else if (desktopConfig.useDesktopOverrideDensity) {
                // The task has had its density overridden, but keep using the system's density to
                // layout the header.
                Configuration(context.resources.configuration)
            } else {
                Configuration(taskInfo.configuration)
            }

        // Set opaque background for all freeform tasks to prevent freeform tasks below
        // from being visible if freeform task window above is translucent.
        // Otherwise if fluid resize is enabled, add a background to freeform tasks.
        val shouldSetBackground = desktopConfig.shouldSetBackground(taskInfo)

        return RelayoutParams(
            runningTaskInfo = taskInfo,
            captionType = captionType,
            inputFeatures = inputFeatures,
            isInsetSource = isInsetSource,
            insetSourceFlags = insetSourceFlags,
            displayExclusionRegion = Region.obtain(displayExclusionRegion),
            shadowRadius = getShadowRadius(captionType, hasGlobalFocus),
            cornerRadius = getCornerRadius(captionType, shouldIgnoreCornerRadius),
            shadowRadiusId = getShadowRadiusId(captionType, hasGlobalFocus),
            cornerRadiusId = getCornerRadiusId(captionType, shouldIgnoreCornerRadius),
            borderSettingsId = getBorderSettingsId(captionType, taskInfo, hasGlobalFocus),
            boxShadowSettingsIds = getShadowSettingsIds(captionType, hasGlobalFocus),
            isCaptionVisible = shouldShowCaption(taskInfo, lockTaskChangeListener.isTaskLocked),
            windowDecorConfig = windowDecorConfig,
            asyncViewHost = asyncViewHost,
            applyStartTransactionOnDraw = applyStartTransactionOnDraw,
            setTaskVisibilityPositionAndCrop = shouldSetTaskVisibilityPositionAndCrop,
            hasGlobalFocus = hasGlobalFocus,
            shouldSetAppBounds = shouldSetAppBounds,
            shouldSetBackground = shouldSetBackground,
            inSyncWithTransition = inSyncWithTransition,
        )
    }

    private fun getShadowSettingsIds(
        captionType: CaptionController.CaptionType,
        hasGlobalFocus: Boolean,
    ): IntArray? =
        if (
            !DesktopExperienceFlags.ENABLE_FREEFORM_BOX_SHADOWS.isTrue ||
                captionType != CaptionController.CaptionType.APP_HEADER ||
                !desktopConfig.useWindowShadow(isFocusedWindow = hasGlobalFocus)
        ) {
            null
        } else if (hasGlobalFocus) {
            intArrayOf(R.style.BoxShadowParamsKeyFocused, R.style.BoxShadowParamsAmbientFocused)
        } else {
            intArrayOf(R.style.BoxShadowParamsKeyUnfocused, R.style.BoxShadowParamsAmbientUnfocused)
        }

    private fun getBorderSettingsId(
        captionType: CaptionController.CaptionType,
        taskInfo: RunningTaskInfo,
        hasGlobalFocus: Boolean,
    ): Int =
        if (
            !DesktopExperienceFlags.ENABLE_FREEFORM_BOX_SHADOWS.isTrue ||
                captionType != CaptionController.CaptionType.APP_HEADER ||
                !desktopConfig.useWindowShadow(isFocusedWindow = hasGlobalFocus)
        ) {
            ID_NULL
        } else if (DecorThemeUtil(context).getAppTheme(taskInfo) == Theme.DARK) {
            if (hasGlobalFocus) {
                R.style.BorderSettingsFocusedDark
            } else {
                R.style.BorderSettingsUnfocusedDark
            }
        } else {
            if (hasGlobalFocus) R.style.BorderSettingsFocusedLight
            else R.style.BorderSettingsUnfocusedLight
        }

    private fun getShadowRadiusId(
        captionType: CaptionController.CaptionType,
        hasGlobalFocus: Boolean,
    ): Int =
        if (DesktopExperienceFlags.ENABLE_FREEFORM_BOX_SHADOWS.isTrue) {
            ID_NULL
        } else if (
            !DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue ||
                captionType != CaptionController.CaptionType.APP_HEADER ||
                !desktopConfig.useWindowShadow(isFocusedWindow = hasGlobalFocus)
        ) {
            ID_NULL
        } else if (hasGlobalFocus) {
            R.dimen.freeform_decor_shadow_focused_thickness
        } else {
            R.dimen.freeform_decor_shadow_unfocused_thickness
        }

    @Deprecated("")
    private fun getShadowRadius(
        captionType: CaptionController.CaptionType,
        hasGlobalFocus: Boolean,
    ): Int =
        if (DesktopExperienceFlags.ENABLE_FREEFORM_BOX_SHADOWS.isTrue) {
            INVALID_SHADOW_RADIUS
        } else if (
            DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue ||
                captionType != CaptionController.CaptionType.APP_HEADER ||
                !desktopConfig.useWindowShadow(isFocusedWindow = hasGlobalFocus)
        ) {
            INVALID_SHADOW_RADIUS
        } else if (hasGlobalFocus) {
            context.resources.getDimensionPixelSize(R.dimen.freeform_decor_shadow_focused_thickness)
        } else {
            context.resources.getDimensionPixelSize(
                R.dimen.freeform_decor_shadow_unfocused_thickness
            )
        }

    @Deprecated("")
    private fun getCornerRadius(
        captionType: CaptionController.CaptionType,
        shouldIgnoreCornerRadius: Boolean,
    ): Int =
        if (
            captionType != CaptionController.CaptionType.APP_HEADER ||
                DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue ||
                !desktopConfig.useRoundedCorners ||
                shouldIgnoreCornerRadius
        ) {
            INVALID_CORNER_RADIUS
        } else {
            context.resources.getDimensionPixelSize(
                com.android.wm.shell.shared.R.dimen.desktop_windowing_freeform_rounded_corner_radius
            )
        }

    private fun getCornerRadiusId(
        captionType: CaptionController.CaptionType,
        shouldIgnoreCornerRadius: Boolean,
    ): Int =
        if (
            captionType != CaptionController.CaptionType.APP_HEADER ||
                !DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue ||
                !desktopConfig.useRoundedCorners ||
                shouldIgnoreCornerRadius
        ) {
            ID_NULL
        } else {
            com.android.wm.shell.shared.R.dimen.desktop_windowing_freeform_rounded_corner_radius
        }

    private fun shouldShowCaption(taskInfo: RunningTaskInfo, isTaskLocked: Boolean): Boolean {
        var showCaption: Boolean
        if (DesktopModeFlags.ENABLE_DESKTOP_IMMERSIVE_DRAG_BUGFIX.isTrue && isDragging) {
            // If the task is being dragged, the caption should not be hidden so that it continues
            // receiving input
            showCaption = true
        } else if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue && inFullImmersive) {
            showCaption = isStatusBarVisible && !isKeyguardVisibleAndOccluded

            if (
                DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_ENTERPRISE_BUGFIX.isTrue() &&
                    !taskInfo.isFreeform
            ) {
                showCaption = showCaption && !isTaskLocked
            }
        } else {
            // Caption should always be visible in freeform mode. When not in freeform,
            // align with the status bar except when showing over keyguard (where it should not
            // shown).
            //  TODO: b/356405803 - Investigate how it's possible for the status bar visibility to
            //   be false while a freeform window is open if the status bar is always
            //   forcibly-shown. It may be that the InsetsState (from which |mIsStatusBarVisible|
            //   is set) still contains an invisible insets source in immersive cases even if the
            //   status bar is shown?
            showCaption =
                taskInfo.isFreeform || (isStatusBarVisible && !isKeyguardVisibleAndOccluded)

            if (
                DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_ENTERPRISE_BUGFIX.isTrue() &&
                    !taskInfo.isFreeform
            ) {
                showCaption = showCaption && !isTaskLocked
            }
        }

        return showCaption
    }

    private fun updateDragResizeListenerIfNeeded(containerSurface: SurfaceControl?) {
        val taskPositionChanged = !taskInfo.positionInParent.equals(taskPositionInParent)
        if (!taskInfo.isDragResizable(inFullImmersive)) {
            if (taskPositionChanged) {
                // We still want to track caption bar's exclusion region on a non-resizeable task.
                updateExclusionRegion()
            }
            closeDragResizeListener()
            return
        }
        updateDragResizeListener(containerSurface) { geometryChanged ->
            if (geometryChanged || taskPositionChanged) {
                updateExclusionRegion()
            }
        }
    }

    private fun updateDragResizeListener(
        containerSurface: SurfaceControl?,
        onUpdateFinished: (Boolean) -> Unit,
    ) {
        val containerSurfaceChanged = containerSurface != decorationContainerSurface
        if (containerSurfaceChanged) {
            closeDragResizeListener()
        }
        val listener =
            dragResizeListener
                ?: DragResizeInputListener(
                    context,
                    WindowManagerGlobal.getWindowSession(),
                    mainExecutor,
                    if (DesktopModeFlags.ENABLE_DRAG_RESIZE_SET_UP_IN_BG_THREAD.isTrue) {
                        bgExecutor
                    } else {
                        mainExecutor
                    },
                    taskInfo,
                    handler,
                    choreographer,
                    checkNotNull(display?.displayId) { "expected non-null display" },
                    checkNotNull(decorationContainerSurface),
                    dragPositioningCallback,
                    surfaceControlBuilderSupplier,
                    surfaceControlTransactionSupplier,
                    displayController,
                    desktopModeEventLogger,
                )
        val touchSlop = ViewConfiguration.get(decorWindowContext).scaledTouchSlop
        val res = decorWindowContext.resources
        val shouldIgnoreCornerRadius =
            isRecentsTransitionRunning &&
                DesktopModeFlags.ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX.isTrue
        val newGeometry =
            DragResizeWindowGeometry(
                if (DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue) {
                    context.resources.getDimensionPixelSize(
                        getCornerRadiusId(captionType, shouldIgnoreCornerRadius),
                        defaultValue = 0,
                    )
                } else {
                    getCornerRadius(captionType, shouldIgnoreCornerRadius)
                },
                Size(taskWidth, taskHeight),
                getResizeEdgeHandleSize(res),
                getResizeHandleEdgeInset(res),
                getFineResizeCornerSize(res),
                getLargeResizeCornerSize(res),
                disabledResizingEdge,
            )
        listener.addInitializedCallback {
            onUpdateFinished.invoke(listener.setGeometry(newGeometry, touchSlop))
        }
        dragResizeListener = listener
    }

    /**
     * Returns the valid drag area for this task if the task can be dragged. Otherwise, returns
     * null.
     */
    override fun calculateValidDragArea(): Rect? = captionController?.calculateValidDragArea()

    private fun updateExclusionRegion() {
        exclusionRegionListener.onExclusionRegionChanged(
            taskInfo.taskId,
            getGlobalExclusionRegion(),
        )
    }

    /**
     * Create a new exclusion region from the corner rects (if resizeable) and caption bounds of
     * this task.
     */
    private fun getGlobalExclusionRegion(): Region {
        val exclusionRegion =
            if (taskInfo.isDragResizable(inFullImmersive)) {
                dragResizeListener?.cornersRegion ?: Region()
            } else {
                Region()
            }
        if (inFullImmersive) {
            // Task can't be moved in full immersive, so skip excluding the caption region.
            return exclusionRegion
        }
        exclusionRegion.union(Rect(0, 0, taskWidth, captionController?.getCaptionHeight() ?: 0))
        exclusionRegion.translate(positionInParent.x, positionInParent.y)
        return exclusionRegion
    }

    /** Returns [true] if [dragResizeListener] should handle the motion event. */
    fun shouldResizeListenerHandleEvent(e: MotionEvent, offset: Point): Boolean {
        return dragResizeListener?.shouldHandleEvent(e, offset) ?: false
    }

    /** Returns [true] if [dragResizeListener] is handling the motion event. */
    fun isHandlingDragResize(): Boolean {
        return dragResizeListener?.isHandlingDragResize ?: false
    }

    private fun closeDragResizeListener() {
        dragResizeListener?.close()
        dragResizeListener = null
    }

    /** Returns true if task resize or reposition is currently being animated. */
    fun setAnimatingTaskResizeOrReposition(animatingTaskResizeOrReposition: Boolean) {
        captionController?.onAnimatingTaskRepositioningOrResize(animatingTaskResizeOrReposition)
    }

    /**
     * Create the resize veil for this task. Note the veil's visibility is View.GONE by default
     * until a resize event calls showResizeVeil below.
     */
    private fun getOrCreateResizeVeil(): ResizeVeil {
        val veil =
            resizeVeil
                ?: ResizeVeil(
                    context = context,
                    displayController = displayController,
                    taskResourceLoader = taskResourceLoader,
                    mainDispatcher = mainDispatcher,
                    mainScope = mainScope,
                    parentSurface = taskSurface,
                    surfaceControlTransactionSupplier = surfaceControlTransactionSupplier,
                    taskInfo = taskInfo,
                )
        resizeVeil = veil
        return veil
    }

    /** Show the resize veil. */
    fun showResizeVeil(taskBounds: Rect) {
        getOrCreateResizeVeil().showVeil(taskSurface, taskBounds, taskInfo)
    }

    /** Show the resize veil. */
    fun showResizeVeil(tx: SurfaceControl.Transaction, taskBounds: Rect) {
        getOrCreateResizeVeil()
            .showVeil(
                t = tx,
                parent = taskSurface,
                taskBounds = taskBounds,
                taskInfo = taskInfo,
                fadeIn = false,
            )
    }

    /** Set new bounds for the resize veil */
    fun updateResizeVeil(newBounds: Rect) {
        resizeVeil?.updateResizeVeil(newBounds)
    }

    /** Set new bounds for the resize veil */
    fun updateResizeVeil(tx: SurfaceControl.Transaction, newBounds: Rect) {
        resizeVeil?.updateResizeVeil(tx, newBounds)
    }

    /** Fade the resize veil out. */
    fun hideResizeVeil() {
        resizeVeil?.hideVeil()
    }

    private fun disposeResizeVeil() {
        resizeVeil?.dispose()
        resizeVeil = null
    }

    /** Handles a interruption to a drag event. */
    fun handleDragInterrupted() {
        (captionController as? AppHandleController)?.handleDragInterrupted()
    }

    /**
     * Check if touch event occurred in caption when caption is unable to receive touch events (i.e.
     * when caption is behind the status bar).
     */
    fun checkTouchEventInCaption(e: MotionEvent): Boolean =
        (captionController as? AppHandleController)?.checkTouchEventInCaption(e) ?: false

    /** Checks if touch event occurred in caption's customizable region. */
    fun checkTouchEventInCustomizableRegion(e: MotionEvent): Boolean =
        captionController?.checkTouchEventInCustomizableRegion(e) ?: false

    /** Adds inset for caption if one exists. */
    fun addCaptionInset(wct: WindowContainerTransaction) {
        captionController?.addCaptionInset(wct)
    }

    /**
     * Announces that the app window is now being focused for accessibility. This is used after a
     * window is minimized/closed, and a new app window gains focus.
     */
    fun a11yAnnounceNewFocusedWindow() {
        (captionController as? AppHeaderController)?.a11yAnnounceFocused()
    }

    /**
     * Request direct a11y focus on the maximize button. This is used after a maximize/restore to
     * ensure that focus always goes back to the button.
     */
    fun a11yFocusMaximizeButton() {
        (captionController as? AppHeaderController)?.a11yFocusMaximizeButton()
    }

    /** Closes the window decoration. */
    override fun close() {
        taskResourceLoader.onWindowDecorClosed(taskInfo)
        closeDragResizeListener()
        disposeResizeVeil()
        exclusionRegionListener.onExclusionRegionDismissed(taskInfo.taskId)
        super.close()
    }

    override fun createCaptionController(
        captionType: CaptionController.CaptionType
    ): CaptionController<WindowDecorLinearLayout>? =
        when (captionType) {
            CaptionController.CaptionType.APP_HEADER -> {
                AppHeaderController(
                    taskInfo,
                    windowDecorViewHostSupplier,
                    context,
                    userContext,
                    displayController,
                    taskResourceLoader,
                    splitScreenController,
                    desktopUserRepositories,
                    transitions,
                    taskSurface,
                    checkNotNull(decorationContainerSurface) {
                        "Expected non-null decoration container surface"
                    },
                    handler,
                    mainExecutor,
                    mainDispatcher,
                    mainScope,
                    bgExecutor,
                    syncQueue,
                    rootTaskDisplayAreaOrganizer,
                    windowManagerWrapper,
                    multiInstanceHelper,
                    windowDecorCaptionRepository,
                    desktopModeUiEventLogger,
                    desktopState,
                    windowDecorationActions,
                    decorWindowContext,
                    onTouchListener,
                    onClickListener,
                    onLongClickListener,
                    onGenericMotionListener,
                    appToWebRepository,
                )
            }

            CaptionController.CaptionType.APP_HANDLE -> {
                AppHandleController(
                    taskInfo,
                    windowDecorViewHostSupplier,
                    context,
                    userContext,
                    transitions,
                    displayController,
                    taskResourceLoader,
                    splitScreenController,
                    desktopUserRepositories,
                    taskOrganizer,
                    taskSurface,
                    checkNotNull(decorationContainerSurface) {
                        "Expected non-null decoration container surface"
                    },
                    handler,
                    mainDispatcher,
                    mainScope,
                    windowManagerWrapper,
                    multiInstanceHelper,
                    windowDecorCaptionRepository,
                    desktopModeUiEventLogger,
                    desktopState,
                    windowDecorationActions,
                    decorWindowContext,
                    onTouchListener,
                    onClickListener,
                    appToWebRepository,
                )
            }

            CaptionController.CaptionType.NO_CAPTION -> null
        }
}
