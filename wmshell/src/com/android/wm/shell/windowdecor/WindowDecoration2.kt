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
import android.content.pm.ActivityInfo.CONFIG_ASSETS_PATHS
import android.content.pm.ActivityInfo.CONFIG_UI_MODE
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Region
import android.gui.BorderSettings
import android.gui.BoxShadowSettings
import android.os.Handler
import android.os.Trace
import android.view.Display
import android.view.InsetsSource
import android.view.InsetsState
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager.LayoutParams
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowlessWindowManager
import android.window.DesktopExperienceFlags
import android.window.TaskConstants
import android.window.WindowContainerTransaction
//import com.android.app.tracing.traceSection
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.BoxShadowHelper
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.caption.CaptionController
import com.android.wm.shell.windowdecor.extension.getDimensionPixelSize
import com.android.wm.shell.windowdecor.extension.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manages a container surface and a windowless window to show window decorations. Responsible to
 * update window decoration window state and layout parameters on task info changes and so that
 * window decoration is in correct state and bounds.
 *
 * The container surface is a child of the task display area in the same display, so that window
 * decorations can be drawn out of the task bounds and receive input events from out of the task
 * bounds to support drag resizing.
 *
 * The windowless window that hosts window decorations is positioned in front of all activities, to
 * allow the foreground activity to draw its own background behind window decorations, such as the
 * window captions.
 *
 * @param <T> The type of the root view
 */
abstract class WindowDecoration2<T>(
    var taskInfo: RunningTaskInfo,
    private val context: Context,
    private val displayController: DisplayController,
    taskSurface: SurfaceControl,
    private val surfaceControlSupplier: () -> SurfaceControl,
    private val taskOrganizer: ShellTaskOrganizer,
    @ShellMainThread private val handler: Handler,
    @ShellMainThread private val mainScope: CoroutineScope,
    private val transitions: Transitions,
    private val surfaceControlBuilderSupplier: () -> SurfaceControl.Builder = {
        SurfaceControl.Builder()
    },
    private val surfaceControlTransactionSupplier: () -> SurfaceControl.Transaction = {
        SurfaceControl.Transaction()
    },
    private val windowContainerTransactionSupplier: () -> WindowContainerTransaction = {
        WindowContainerTransaction()
    },
) : AutoCloseable where T : View, T : TaskFocusStateConsumer {

    protected var captionController: CaptionController<T>? = null
    /** Returns the display associated with the task. */
    var display: Display? = displayController.getDisplay(taskInfo.displayId)
    private var windowDecorConfig: Configuration? = null
    /** The decor's context. */
    lateinit var decorWindowContext: Context
    /** Whether the task associated with the decor has global focus. */
    var hasGlobalFocus = false
    /** The exclusion region of the task's display. */
    val exclusionRegion = Region.obtain()
    /** The last calculated valid drag area of the task. */
    var lastValidDragArea: Rect? = null

    private val onDisplaysChangedListener: OnDisplaysChangedListener =
        object : OnDisplaysChangedListener {
            override fun onDisplayAdded(displayId: Int) {
                if (taskInfo.displayId != displayId) {
                    return
                }
                displayController.removeDisplayWindowListener(this)
                relayout(taskInfo, hasGlobalFocus, exclusionRegion)
            }
        }
    /** The surface control of the task that owns this decoration. */
    var taskSurface = cloneSurfaceControl(taskSurface, surfaceControlSupplier)
        private set

    protected var decorationContainerSurface: SurfaceControl? = null
    /** Sets the [TaskDragResizer] which allows task to be drag-resized. */
    var taskDragResizer: TaskDragResizer? = null

    protected var isKeyguardVisibleAndOccluded = false
    protected var isStatusBarVisible =
        displayController
            .getInsetsState(taskInfo.displayId)
            ?.isVisible(WindowInsets.Type.statusBars()) ?: false

    /**
     * Used by the [DragPositioningCallback] associated with the implementing class to enforce drags
     * ending in a valid position. Returns null if task cannot be dragged.
     */
    abstract fun calculateValidDragArea(): Rect?

    /** Creates the correct caption controller for the [CaptionType]. */
    abstract fun createCaptionController(
        captionType: CaptionController.CaptionType
    ): CaptionController<T>?

    /** Updates the window decorations when limited information is available. */
    abstract fun relayout(
        taskInfo: RunningTaskInfo,
        hasGlobalFocus: Boolean,
        displayExclusionRegion: Region,
    )

    /**
     * Updates the task's window decorations given a change to the task (i.e. task visibility
     * change) or system (i.e. status bar showing/hiding).
     *
     * When a task info change occurs due to a [Transition], this function is called after a
     * transition is started but before the transition is animated. [startT] will be applied at the
     * start of [Transition#startAnimation] and [finishT] will be called on the transition's finish.
     *
     * If display associated with the task has not yet appeared or the task is not visible, returns
     * null. Otherwise, returns the [RelayoutResult].
     */
    fun relayout(
        params: RelayoutParams,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        wct: WindowContainerTransaction,
        newTaskSurface: SurfaceControl?,
    ): RelayoutResult<T>? {
            logD("relayout(task=%d) startT=%d finishT=%d", taskInfo.taskId, startT.id, finishT.id)
            taskInfo = params.runningTaskInfo
            hasGlobalFocus = params.hasGlobalFocus
            exclusionRegion.set(params.displayExclusionRegion)

            if (
                decorationContainerSurface != null &&
                    newTaskSurface != null &&
                    !newTaskSurface.isSameSurface(taskSurface)
            ) {
                val containerSurface =
                    checkNotNull(decorationContainerSurface) {
                        "expected non-null decoration container surface"
                    }
                taskSurface.release()
                taskSurface = cloneSurfaceControl(newTaskSurface, surfaceControlSupplier)
                startT.reparent(containerSurface, taskSurface)
            }

            if (!taskInfo.isVisible) {
                releaseViews(wct)
                if (params.setTaskVisibilityPositionAndCrop) {
                    finishT.hide(taskSurface)
                }
                lastValidDragArea = captionController?.calculateValidDragArea()
                logD("relayout(task=%d) invisible task, skipping", taskInfo.taskId)
                return null
            }

            releaseViewsIfNeeded(params, wct)

            // If display has not yet appeared, return. Relayout will run again once display is
            // registered
            if (display == null) {
                logD("relayout(task=%d) null display, skipping", taskInfo.taskId)
                return null
            }

            val taskBounds = taskInfo.getConfiguration().windowConfiguration.bounds
            val taskWidth = taskBounds.width()
            val taskHeight = taskBounds.height()

            val borderSettings =
                if (params.borderSettingsId != Resources.ID_NULL) {
                    BoxShadowHelper.getBorderSettings(decorWindowContext, params.borderSettingsId)
                } else null

            val boxShadowSettings =
                if (params.boxShadowSettingsIds != null) {
                    BoxShadowHelper.getBoxShadowSettings(
                        decorWindowContext,
                        params.boxShadowSettingsIds,
                    )
                } else null

            val cornerRadius =
                if (DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue) {
                    context.resources.getDimensionPixelSize(
                        params.cornerRadiusId,
                        INVALID_CORNER_RADIUS,
                    )
                } else INVALID_CORNER_RADIUS

            val shadowRadius =
                if (DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue) {
                    context.resources.getDimensionPixelSize(
                        params.shadowRadiusId,
                        INVALID_SHADOW_RADIUS,
                    )
                } else INVALID_SHADOW_RADIUS

            updateDecorationContainerSurface(startT, taskWidth, taskHeight)
            updateTaskSurface(
                params,
                startT,
                finishT,
                taskWidth,
                taskHeight,
                borderSettings,
                boxShadowSettings,
                shadowRadius,
                cornerRadius,
            )

            val controller = getOrCreateCaptionController(params.captionType)
            if (controller == null) {
                logD("relayout(task=%d) null caption controller, skipping", taskInfo.taskId)
                return null
            }
            val captionResult =
                controller.relayout(
                    params = params,
                    parentContainer =
                        checkNotNull(decorationContainerSurface) {
                            "expected non-null decoration container surface control"
                        },
                    display = checkNotNull(display) { "expected non-null display" },
                    decorWindowContext = decorWindowContext,
                    startT = startT,
                    finishT = finishT,
                    wct = wct,
                )

            lastValidDragArea = captionController?.calculateValidDragArea()

            return RelayoutResult(
                captionResult = captionResult,
                taskWidth = taskBounds.width(),
                taskHeight = taskBounds.height(),
                cornerRadius = cornerRadius,
                shadowRadius = shadowRadius,
                borderSettings = borderSettings,
                boxShadowSettings = boxShadowSettings,
            )
        }

    private fun getOrCreateCaptionController(
        captionType: CaptionController.CaptionType
    ): CaptionController<T>? {
        if (captionController == null) {
            captionController = createCaptionController(captionType)
        } else if (captionController?.captionType != captionType) {
            releaseCaptionController()
            captionController = createCaptionController(captionType)
        }
        return captionController
    }

    private fun releaseCaptionController() {
        val wct = windowContainerTransactionSupplier()
        val t = surfaceControlTransactionSupplier()
        releaseCaptionController(wct, t)
        t.apply()
        if (!wct.isEmpty) {
            if (DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP.isTrue) {
                mainScope.launch {
                    transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
                }
            } else {
                taskOrganizer.applyTransaction(wct)
            }
        }
    }

    /** Releases the caption controller. Returns [true] if a caption view was actually released. */
    private fun releaseCaptionController(
        wct: WindowContainerTransaction,
        t: SurfaceControl.Transaction,
    ): Boolean {
        val released = captionController?.close(wct, t) == true
        captionController = null
        return released
    }

    private fun updateTaskSurface(
        params: RelayoutParams,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        taskWidth: Int,
        taskHeight: Int,
        borderSettings: BorderSettings?,
        boxShadowSettings: BoxShadowSettings?,
        shadowRadius: Int,
        cornerRadius: Int,
    ) {
        if (params.setTaskVisibilityPositionAndCrop) {
            val taskPosition = taskInfo.positionInParent
            startT.setWindowCrop(taskSurface, taskWidth, taskHeight)
            finishT
                .setWindowCrop(taskSurface, taskWidth, taskHeight)
                .setPosition(taskSurface, taskPosition.x.toFloat(), taskPosition.y.toFloat())
        }

        if (params.setTaskVisibilityPositionAndCrop) {
            startT.show(taskSurface)
        }

        if (params.shouldSetBackground) {
            // TODO: b/437011322 - Use taskDescription backgroundColor for free resizing
            val backgroundColorInt = Color.BLACK
            val tmpColor =
                floatArrayOf(
                    Color.red(backgroundColorInt).toFloat() / 255f,
                    Color.green(backgroundColorInt).toFloat() / 255f,
                    Color.blue(backgroundColorInt).toFloat() / 255f,
                )
            startT.setColor(taskSurface, tmpColor)
        } else {
            startT.unsetColor(taskSurface)
        }

        updateTaskSurfaceOutline(
            params,
            startT,
            finishT,
            borderSettings,
            boxShadowSettings,
            shadowRadius,
            cornerRadius,
        )
    }

    private fun updateTaskSurfaceOutline(
        params: RelayoutParams,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        borderSettings: BorderSettings?,
        boxShadowSettings: BoxShadowSettings?,
        shadowRadius: Int,
        cornerRadius: Int,
    ) {
        if (
            (DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue ||
                DesktopExperienceFlags.ENABLE_FREEFORM_BOX_SHADOWS.isTrue) &&
                !params.inSyncWithTransition
        ) {
            // Update these outline properties only when the relayout is driven by Transition
            // callbacks because they must be updated together with some of other properties (e.g.,
            // position) which is set by transition handler although the outline properties are
            // expected to be set by WindowDecoration instead of the transition handler.
            return
        }

        if (borderSettings != null && borderSettings.strokeWidth > 0) {
            startT.setBorderSettings(taskSurface, borderSettings)
            finishT.setBorderSettings(taskSurface, borderSettings)
        }

        if (boxShadowSettings != null && boxShadowSettings.boxShadows.isNotEmpty()) {
            startT.setBoxShadowSettings(taskSurface, boxShadowSettings)
            finishT.setBoxShadowSettings(taskSurface, boxShadowSettings)
        }

        if (DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue) {
            if (shadowRadius != INVALID_SHADOW_RADIUS) {
                startT.setShadowRadius(taskSurface, shadowRadius.toFloat())
                finishT.setShadowRadius(taskSurface, shadowRadius.toFloat())
            }

            if (cornerRadius != INVALID_CORNER_RADIUS) {
                startT.setCornerRadius(taskSurface, cornerRadius.toFloat())
                finishT.setCornerRadius(taskSurface, cornerRadius.toFloat())
            }
        } else {
            if (params.shadowRadius != INVALID_SHADOW_RADIUS) {
                startT.setShadowRadius(taskSurface, params.shadowRadius.toFloat())
                finishT.setShadowRadius(taskSurface, params.shadowRadius.toFloat())
            }
            if (params.cornerRadius != INVALID_CORNER_RADIUS) {
                startT.setCornerRadius(taskSurface, params.cornerRadius.toFloat())
                finishT.setCornerRadius(taskSurface, params.cornerRadius.toFloat())
            }
        }
    }

    private fun updateDecorationContainerSurface(
        startT: SurfaceControl.Transaction,
        taskWidth: Int,
        taskHeight: Int,
    ) {
        if (decorationContainerSurface == null) {
            val builder = surfaceControlBuilderSupplier()
            val containerSurface =
                builder
                    .setName("Decor container of Task=" + taskInfo.taskId)
                    .setContainerLayer()
                    .setParent(taskSurface)
                    .setCallsite("WindowDecoration2.updateDecorationContainerSurface")
                    .build()

            startT
                .setTrustedOverlay(containerSurface, true)
                .setLayer(containerSurface, TaskConstants.TASK_CHILD_LAYER_WINDOW_DECORATIONS)
            decorationContainerSurface = containerSurface
        }
        val containerSurface =
            checkNotNull(decorationContainerSurface) {
                "expected non-null decoration container surface"
            }
        startT.setWindowCrop(containerSurface, taskWidth, taskHeight).show(containerSurface)
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_WINDOW_DECORATION, "%s: $msg", TAG, *arguments)
    }

    private fun releaseViewsIfNeeded(params: RelayoutParams, wct: WindowContainerTransaction) {
            val windowDecorConfigInitialized = windowDecorConfig != null
            val oldConfig = windowDecorConfig ?: taskInfo.configuration
            val newConfig = params.windowDecorConfig ?: taskInfo.configuration
            checkNotNull(newConfig) { "Expected Non-null Configuration for Window Decoration" }
            val fontScaleChanged = oldConfig.fontScale != taskInfo.configuration.fontScale
            val localeListChanged = oldConfig.locales != taskInfo.configuration.locales
            val densityDpiChanged = oldConfig.densityDpi != newConfig.densityDpi
            val oldNightMode = oldConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val diff = newConfig.diff(oldConfig)
            val themeChanged = (diff and CONFIG_ASSETS_PATHS) != 0 || (diff and CONFIG_UI_MODE) != 0
            windowDecorConfig = newConfig

            if (
                densityDpiChanged ||
                    display == null ||
                    display?.displayId != taskInfo.displayId ||
                    oldNightMode != newNightMode ||
                    !windowDecorConfigInitialized ||
                    fontScaleChanged ||
                    localeListChanged ||
                    themeChanged
            ) {
                releaseViews(wct)
                if (!obtainDisplayOrRegisterListener()) {
                    return
                }
                decorWindowContext = context.createConfigurationContext(newConfig)
                decorWindowContext.setTheme(context.themeResId)
            }
        }

    /** Updates the window decorations when keyguard visibility changes. */
    fun onKeyguardStateChanged(visible: Boolean, occluded: Boolean) {
        val prevVisAndOccluded = isKeyguardVisibleAndOccluded
        isKeyguardVisibleAndOccluded = visible && occluded
        val changed = prevVisAndOccluded != isKeyguardVisibleAndOccluded
        if (changed) {
            relayout(taskInfo, hasGlobalFocus, exclusionRegion)
        }
    }

    /** Updates the window decoration on status bar visibility changed. */
    fun onInsetsStateChanged(insetsState: InsetsState) {
        val prevStatusBarVisibility = isStatusBarVisible
        isStatusBarVisible = insetsState.isVisible(WindowInsets.Type.statusBars())
        val changed = prevStatusBarVisibility != isStatusBarVisible

        if (changed) {
            relayout(taskInfo, hasGlobalFocus, exclusionRegion)
        }
    }

    /** Updates the window decorations when exclusion region changes. */
    open fun onExclusionRegionChanged(exclusionRegion: Region) {
        relayout(taskInfo, hasGlobalFocus, exclusionRegion)
    }

    /**
     * Obtains the [Display] instance for the display ID in [taskInfo] if it exists or registers
     * [OnDisplaysChangedListener] if it doesn't.
     *
     * @return [true] if the [Display] instance exists; or [false] otherwise
     */
    private fun obtainDisplayOrRegisterListener(): Boolean {
        display = displayController.getDisplay(taskInfo.displayId)
        if (display == null) {
            // Post to the handler to avoid an infinite loop. See b/415631133 for more details.
            // TODO(b/419398609): Remove this whole work around once the root timing issue is
            //  resolved.
            handler.post { displayController.addDisplayWindowListener(onDisplaysChangedListener) }
            return false
        }
        return true
    }

    /** Releases all window decoration views. */
    private fun releaseViews(wct: WindowContainerTransaction) {
            val t = surfaceControlTransactionSupplier()
            var released = false

            decorationContainerSurface?.let {
                t.remove(it)
                decorationContainerSurface = null
                released = true
            }

            released = released or releaseCaptionController(wct, t)

            if (released) {
                t.apply()
            }
        }

    override fun close() {
            displayController.removeDisplayWindowListener(onDisplaysChangedListener)
            taskDragResizer?.close()
            val wct = windowContainerTransactionSupplier()
            releaseViews(wct)
            if (!wct.isEmpty) {
                if (DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP.isTrue) {
                    mainScope.launch {
                        transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
                    }
                } else {
                    taskOrganizer.applyTransaction(wct)
                }
            }
            taskSurface.release()
        }

    private fun cloneSurfaceControl(
        sc: SurfaceControl,
        surfaceControlSupplier: () -> SurfaceControl,
    ) = surfaceControlSupplier().apply { copyFrom(sc, TAG) }

    /** Holds the data required to update the window decorations. */
    data class RelayoutParams(
        val runningTaskInfo: RunningTaskInfo,
        val captionType: CaptionController.CaptionType,
        val inputFeatures: Int = 0,
        val isInsetSource: Boolean = true,
        @InsetsSource.Flags val insetSourceFlags: Int = 0,
        val displayExclusionRegion: Region = Region.obtain(),
        @Deprecated("") val shadowRadius: Int = INVALID_SHADOW_RADIUS,
        @Deprecated("") val cornerRadius: Int = INVALID_CORNER_RADIUS,
        val shadowRadiusId: Int = Resources.ID_NULL,
        val cornerRadiusId: Int = Resources.ID_NULL,
        val borderSettingsId: Int = Resources.ID_NULL,
        val boxShadowSettingsIds: IntArray? = null,
        val isCaptionVisible: Boolean = false,
        val windowDecorConfig: Configuration? = null,
        val asyncViewHost: Boolean = false,
        val applyStartTransactionOnDraw: Boolean = false,
        val setTaskVisibilityPositionAndCrop: Boolean = false,
        val hasGlobalFocus: Boolean = false,
        val shouldSetAppBounds: Boolean = false,
        val shouldSetBackground: Boolean = false,
        val inSyncWithTransition: Boolean = false,
    ) {

        /** Returns true if caption input should fall through to the app. */
        fun hasInputFeatureSpy(): Boolean {
            return (inputFeatures and LayoutParams.INPUT_FEATURE_SPY) != 0
        }
    }

    /** Data calculated and retrieved during a [relayout] call. */
    data class RelayoutResult<T>(
        val captionResult: CaptionController.CaptionRelayoutResult,
        val taskWidth: Int,
        val taskHeight: Int,
        val cornerRadius: Int = INVALID_CORNER_RADIUS,
        val shadowRadius: Int = INVALID_SHADOW_RADIUS,
        val borderSettings: BorderSettings? = null,
        val boxShadowSettings: BoxShadowSettings? = null,
    ) where T : View, T : TaskFocusStateConsumer

    /** Creates [SurfaceControlViewHost] for window decoration views. */
    interface SurfaceControlViewHostFactory {

        /** Returns a new [SurfaceControlViewHost]. */
        fun create(
            c: Context,
            d: Display,
            wmm: WindowlessWindowManager,
            callsite: String = TAG,
        ): SurfaceControlViewHost = SurfaceControlViewHost(c, d, wmm, callsite)
    }

    companion object {
        private const val TAG = "WindowDecoration2"

        /**
         * The Z-order of the task input sink in [DragPositioningCallback].
         *
         * This task input sink is used to prevent undesired dispatching of motion events out of
         * task bounds; by layering it behind the caption surface, we allow captions to handle input
         * events first.
         */
        private const val INPUT_SINK_Z_ORDER: Int = -2

        /** Invalid corner radius that signifies that corner radius should not be set. */
        const val INVALID_CORNER_RADIUS: Int = -1

        /** Invalid corner radius that signifies that shadow radius should not be set. */
        const val INVALID_SHADOW_RADIUS: Int = -1
    }
}
