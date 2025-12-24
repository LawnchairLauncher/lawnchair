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
package com.android.wm.shell.windowdecor

import android.graphics.PointF
import android.graphics.Rect
import android.hardware.display.DisplayTopology
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.internal.jank.Cuj
import com.android.internal.jank.InteractionJankMonitor
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.MultiDisplayDragMoveBoundsCalculator
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ResizeTrigger
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.getInputMethodType
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.freeformCaptionInsets
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_LEFT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP
import java.util.concurrent.TimeUnit

/**
 * A task positioner that also takes into account resizing a
 * [com.android.wm.shell.windowdecor.ResizeVeil] and dragging move across multiple displays.
 * - If the drag is resizing the task, we resize the veil instead.
 * - If the drag is repositioning, we consider multi-display topology if needed, and update in the
 *   typical manner.
 */
class MultiDisplayVeiledResizeTaskPositioner(
    private val taskOrganizer: ShellTaskOrganizer,
    private val windowDecoration: WindowDecorationWrapper,
    private val displayController: DisplayController,
    private val transactionSupplier: () -> SurfaceControl.Transaction,
    private val transitions: Transitions,
    private val interactionJankMonitor: InteractionJankMonitor,
    @ShellMainThread private val handler: Handler,
    private val multiDisplayDragMoveIndicatorController: MultiDisplayDragMoveIndicatorController,
    private val desktopState: DesktopState,
    private val desktopTasksController: DesktopTasksController,
) : TaskPositioner, Transitions.TransitionHandler, DisplayController.OnDisplaysChangedListener {

    private val dragEventListeners =
        mutableListOf<DragPositioningCallbackUtility.DragEventListener>()
    private val stableBounds = Rect()
    private val taskBoundsAtDragStart = Rect()
    private val repositionStartPoint = PointF()
    private val repositionTaskBounds = Rect()
    private val isResizing: Boolean
        get() =
            (ctrlType and DragPositioningCallback.CTRL_TYPE_TOP) != 0 ||
                (ctrlType and DragPositioningCallback.CTRL_TYPE_BOTTOM) != 0 ||
                (ctrlType and DragPositioningCallback.CTRL_TYPE_LEFT) != 0 ||
                (ctrlType and DragPositioningCallback.CTRL_TYPE_RIGHT) != 0

    @DragPositioningCallback.CtrlType private var ctrlType = 0
    private var isResizingOrAnimatingResize = false
    @Surface.Rotation private var rotation = 0
    private var startDisplayId = 0
    private var hasMoved = false
    private val displayIds = mutableSetOf<Int>()
    private var hasMovedTaskSurfaceOffScreen = false
    private var resizeTrigger = ResizeTrigger.UNKNOWN_RESIZE_TRIGGER
    private var inputMethod = InputMethod.UNKNOWN_INPUT_METHOD

    constructor(
        taskOrganizer: ShellTaskOrganizer,
        windowDecoration: WindowDecorationWrapper,
        displayController: DisplayController,
        transitions: Transitions,
        interactionJankMonitor: InteractionJankMonitor,
        @ShellMainThread handler: Handler,
        multiDisplayDragMoveIndicatorController: MultiDisplayDragMoveIndicatorController,
        desktopState: DesktopState,
        desktopTasksController: DesktopTasksController,
    ) : this(
        taskOrganizer,
        windowDecoration,
        displayController,
        { SurfaceControl.Transaction() },
        transitions,
        interactionJankMonitor,
        handler,
        multiDisplayDragMoveIndicatorController,
        desktopState,
        desktopTasksController,
    )

    init {
        displayController.addDisplayWindowListener(this)
    }

    override fun onDragPositioningStart(
        ctrlType: Int,
        displayId: Int,
        x: Float,
        y: Float,
        inputMethodType: Int,
    ): Rect {
        this.ctrlType = ctrlType
        startDisplayId = displayId
        hasMovedTaskSurfaceOffScreen = false
        taskBoundsAtDragStart.set(
            windowDecoration.taskInfo.configuration.windowConfiguration.bounds
        )
        repositionStartPoint[x] = y
        hasMoved = false
        inputMethod = getInputMethodType(inputMethodType)
        if (isResizing) {
            resizeTrigger =
                if (
                    ctrlType == CTRL_TYPE_BOTTOM ||
                        ctrlType == CTRL_TYPE_TOP ||
                        ctrlType == CTRL_TYPE_RIGHT ||
                        ctrlType == CTRL_TYPE_LEFT
                ) {
                    ResizeTrigger.EDGE
                } else {
                    ResizeTrigger.CORNER
                }
            for (dragEventListener in dragEventListeners) {
                dragEventListener.onDragResizeStarted(
                    windowDecoration.taskInfo.taskId,
                    resizeTrigger,
                    inputMethod,
                    taskBoundsAtDragStart,
                )
            }
            // Capture CUJ for re-sizing window in DW mode.
            interactionJankMonitor.begin(
                createLongTimeoutJankConfigBuilder(Cuj.CUJ_DESKTOP_MODE_RESIZE_WINDOW)
            )
        }
        repositionTaskBounds.set(taskBoundsAtDragStart)
        val rotation = windowDecoration.taskInfo.configuration.windowConfiguration.displayRotation
        if (stableBounds.isEmpty || this.rotation != rotation) {
            this.rotation = rotation
            displayController
                .getDisplayLayout(windowDecoration.taskInfo.displayId)!!
                .getStableBounds(stableBounds)
        }
        return Rect(repositionTaskBounds)
    }

    override fun onDragPositioningMove(displayId: Int, x: Float, y: Float): Rect {
        check(Looper.myLooper() == handler.looper) {
            "This method must run on the shell main thread."
        }
        val delta = DragPositioningCallbackUtility.calculateDelta(x, y, repositionStartPoint)
        if (
            isResizing &&
                DragPositioningCallbackUtility.changeBounds(
                    ctrlType,
                    repositionTaskBounds,
                    taskBoundsAtDragStart,
                    stableBounds,
                    delta,
                    displayController,
                    windowDecoration,
                    desktopState.canEnterDesktopMode,
                )
        ) {
            if (!isResizingOrAnimatingResize) {
                for (dragEventListener in dragEventListeners) {
                    dragEventListener.onDragMove(windowDecoration.taskInfo.taskId)
                }
                windowDecoration.showResizeVeil(repositionTaskBounds)
                isResizingOrAnimatingResize = true
            } else {
                windowDecoration.updateResizeVeil(repositionTaskBounds)
            }
        } else if (ctrlType == DragPositioningCallback.CTRL_TYPE_UNDEFINED) {
            // Begin window drag CUJ instrumentation only when drag position moves.
            interactionJankMonitor.begin(
                createLongTimeoutJankConfigBuilder(Cuj.CUJ_DESKTOP_MODE_DRAG_WINDOW)
            )

            val t = transactionSupplier()
            val startDisplayLayout = displayController.getDisplayLayout(startDisplayId)
            val currentDisplayLayout = displayController.getDisplayLayout(displayId)

            if (startDisplayLayout == null || currentDisplayLayout == null) {
                // Fall back to single-display drag behavior if any display layout is unavailable.
                DragPositioningCallbackUtility.setPositionOnDrag(
                    windowDecoration,
                    repositionTaskBounds,
                    taskBoundsAtDragStart,
                    repositionStartPoint,
                    t,
                    x,
                    y,
                )
            } else {
                val boundsDp =
                    MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
                        startDisplayLayout,
                        repositionStartPoint,
                        taskBoundsAtDragStart,
                        currentDisplayLayout,
                        x,
                        y,
                    )
                repositionTaskBounds.set(
                    MultiDisplayDragMoveBoundsCalculator.convertGlobalDpToLocalPxForRect(
                        boundsDp,
                        startDisplayLayout,
                    )
                )

                multiDisplayDragMoveIndicatorController.onDragMove(
                    boundsDp,
                    displayId,
                    startDisplayId,
                    windowDecoration.taskSurface,
                    windowDecoration.taskInfo,
                    displayIds,
                    transactionSupplier,
                )
                if (DesktopExperienceFlags.ENABLE_WINDOW_DROP_SMOOTH_TRANSITION.isTrue) {
                    // Move the original task surface off-screen to hide it. A mirrored surface is
                    // used for the drag indicator on all displays, including the start display.
                    // This is necessary for independent opacity control, as a mirror's alpha is
                    // capped by its source.
                    if (!hasMovedTaskSurfaceOffScreen) {
                        hasMovedTaskSurfaceOffScreen = true
                        t.setPosition(
                            windowDecoration.taskSurface,
                            startDisplayLayout.width().toFloat(),
                            startDisplayLayout.height().toFloat(),
                        )
                    }
                } else {
                    t.setPosition(
                        windowDecoration.taskSurface,
                        repositionTaskBounds.left.toFloat(),
                        repositionTaskBounds.top.toFloat(),
                    )
                    // Make the window translucent in the case when the cursor moves to another
                    // display.
                    val alpha =
                        if (startDisplayId == displayId) {
                            ALPHA_FOR_WINDOW_ON_DISPLAY_WITH_CURSOR
                        } else {
                            ALPHA_FOR_WINDOW_ON_NON_CURSOR_DISPLAY
                        }
                    t.setAlpha(windowDecoration.taskSurface, alpha)
                }
            }
            t.setFrameTimeline(Choreographer.getInstance().vsyncId)
            t.apply()
        }
        if (!hasMoved) {
            // Update taskbar rounding once the drag/resize has registered a move event - in case
            // the moved task is no longer maximized. Only call this once per resize/drag so we
            // don't call into Launcher with each drag/resize frame to try to update the taskbar.
            desktopTasksController.updateTaskbarRoundingOnTaskResize(
                displayId,
                windowDecoration.taskInfo.taskId,
                Rect(repositionTaskBounds),
            )
            hasMoved = true
        }
        return Rect(repositionTaskBounds)
    }

    override fun onDragPositioningEnd(displayId: Int, x: Float, y: Float): Rect {
        val delta = DragPositioningCallbackUtility.calculateDelta(x, y, repositionStartPoint)
        if (isResizing) {
            if (taskBoundsAtDragStart != repositionTaskBounds) {
                DragPositioningCallbackUtility.changeBounds(
                    ctrlType,
                    repositionTaskBounds,
                    taskBoundsAtDragStart,
                    stableBounds,
                    delta,
                    displayController,
                    windowDecoration,
                    desktopState.canEnterDesktopMode,
                )
                for (dragEventListener in dragEventListeners) {
                    dragEventListener.onDragResizeEnded(
                        windowDecoration.taskInfo.taskId,
                        resizeTrigger,
                        inputMethod,
                        repositionTaskBounds,
                    )
                }
                windowDecoration.updateResizeVeil(repositionTaskBounds)
                val wct = WindowContainerTransaction()
                wct.setBounds(windowDecoration.taskInfo.token, repositionTaskBounds)
                val captionInsets = windowDecoration.taskInfo.freeformCaptionInsets
                if (captionInsets != 0) {
                    // Reset app bounds if app bounds were overridden.
                    wct.setAppBounds(windowDecoration.taskInfo.token, null)
                }
                transitions.startTransition(WindowManager.TRANSIT_CHANGE, wct, this)
            } else {
                // If bounds haven't changed, perform necessary veil reset here as startAnimation
                // won't be called.
                resetVeilIfVisible()
            }
            interactionJankMonitor.end(Cuj.CUJ_DESKTOP_MODE_RESIZE_WINDOW)
        } else {
            val startDisplayLayout = displayController.getDisplayLayout(startDisplayId)
            val currentDisplayLayout = displayController.getDisplayLayout(displayId)

            if (
                startDisplayId == displayId ||
                    startDisplayLayout == null ||
                    currentDisplayLayout == null
            ) {
                // Fall back to single-display drag behavior if:
                // 1. The drag destination display is the same as the start display. This prevents
                // unnecessary animations caused by minor width/height changes due to DPI scaling.
                // 2. Either the starting or current display layout is unavailable.
                DragPositioningCallbackUtility.updateTaskBounds(
                    repositionTaskBounds,
                    taskBoundsAtDragStart,
                    repositionStartPoint,
                    x,
                    y,
                )
            } else {
                val boundsDp =
                    MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
                        startDisplayLayout,
                        repositionStartPoint,
                        taskBoundsAtDragStart,
                        currentDisplayLayout,
                        x,
                        y,
                    )
                repositionTaskBounds.set(
                    MultiDisplayDragMoveBoundsCalculator.convertGlobalDpToLocalPxForRect(
                        boundsDp,
                        currentDisplayLayout,
                    )
                )

                if (
                    DesktopExperienceFlags.ENABLE_DRAG_END_STABLE_BOUNDS_RESET.isTrue &&
                        displayId != startDisplayId
                ) {
                    currentDisplayLayout.getStableBounds(stableBounds)
                }
            }

            // Call the MultiDisplayDragMoveIndicatorController to clear any active indicator
            // surfaces. This is necessary even if the drag ended on the same display, as surfaces
            // may have been created for other displays during the drag.
            if (!DesktopExperienceFlags.ENABLE_WINDOW_DROP_SMOOTH_TRANSITION.isTrue) {
                multiDisplayDragMoveIndicatorController.onDragEnd(
                    windowDecoration.taskInfo.taskId,
                    transactionSupplier(),
                )
            }

            interactionJankMonitor.end(Cuj.CUJ_DESKTOP_MODE_DRAG_WINDOW)
        }

        ctrlType = DragPositioningCallback.CTRL_TYPE_UNDEFINED
        taskBoundsAtDragStart.setEmpty()
        repositionStartPoint[0f] = 0f
        hasMovedTaskSurfaceOffScreen = false
        return Rect(repositionTaskBounds)
    }

    override fun close() {
        displayController.removeDisplayWindowListener(this)
    }

    private fun resetVeilIfVisible() {
        if (isResizingOrAnimatingResize) {
            windowDecoration.hideResizeVeil()
            isResizingOrAnimatingResize = false
        }
    }

    private fun createLongTimeoutJankConfigBuilder(@Cuj.CujType cujType: Int) =
        InteractionJankMonitor.Configuration.Builder.withSurface(
                cujType,
                windowDecoration.context,
                windowDecoration.taskSurface,
                handler,
            )
            .setTimeout(LONG_CUJ_TIMEOUT_MS)

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        for (change in info.changes) {
            if (change.taskInfo == null) {
                // Ignore non-task (e.g., display, activity) changes.
                continue
            }
            val sc = change.leash
            val endBounds = change.endAbsBounds
            val endPosition = change.endRelOffset
            startTransaction
                .setWindowCrop(sc, endBounds.width(), endBounds.height())
                .setPosition(sc, endPosition.x.toFloat(), endPosition.y.toFloat())
            finishTransaction
                .setWindowCrop(sc, endBounds.width(), endBounds.height())
                .setPosition(sc, endPosition.x.toFloat(), endPosition.y.toFloat())
        }

        startTransaction.apply()
        resetVeilIfVisible()
        ctrlType = DragPositioningCallback.CTRL_TYPE_UNDEFINED
        finishCallback.onTransitionFinished(null /* wct */)
        isResizingOrAnimatingResize = false
        interactionJankMonitor.end(Cuj.CUJ_DESKTOP_MODE_DRAG_WINDOW)
        return true
    }

    /**
     * We should never reach this as this handler's transitions are only started from shell
     * explicitly.
     */
    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        return null
    }

    override fun isResizingOrAnimating() = isResizingOrAnimatingResize

    override fun addDragEventListener(
        dragEventListener: DragPositioningCallbackUtility.DragEventListener
    ) {
        dragEventListeners.add(dragEventListener)
    }

    override fun removeDragEventListener(
        dragEventListener: DragPositioningCallbackUtility.DragEventListener
    ) {
        dragEventListeners.remove(dragEventListener)
    }

    override fun onTopologyChanged(topology: DisplayTopology?) {
        // TODO: b/383069173 - Cancel window drag when topology changes happen during drag.

        displayIds.clear()
        if (topology == null) return
        displayIds.addAll(topology.allNodesIdMap().keys)
    }

    companion object {
        // Timeout used for resize and drag CUJs, this is longer than the default timeout to avoid
        // timing out in the middle of a resize or drag action.
        private val LONG_CUJ_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(/* duration= */ 10L)

        private val ALPHA_FOR_WINDOW_ON_DISPLAY_WITH_CURSOR = 1.0f
        private val ALPHA_FOR_WINDOW_ON_NON_CURSOR_DISPLAY = 0.7f
    }
}
