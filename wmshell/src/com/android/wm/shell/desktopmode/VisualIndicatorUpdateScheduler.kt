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

package com.android.wm.shell.desktopmode

import android.graphics.Rect
import android.hardware.display.DisplayTopology
import android.hardware.display.DisplayTopology.TreeNode.POSITION_LEFT
import android.hardware.display.DisplayTopology.TreeNode.POSITION_RIGHT
import android.hardware.display.DisplayTopology.TreeNode.POSITION_TOP
import android.hardware.display.DisplayTopologyGraph
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellInit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages scheduling updates for the visual indicator shown during task drags in desktop mode.
 *
 * This class introduces a delay before updating the indicator if the drag gesture is potentially
 * moving towards an adjacent display (cross-display drag). This prevents flickering or premature
 * updates while the user is dragging near the edge.
 */
class VisualIndicatorUpdateScheduler(
    shellInit: ShellInit,
    @ShellMainThread private val mainDispatcher: CoroutineDispatcher,
    @ShellBackgroundThread private val bgScope: CoroutineScope,
    private val displayController: DisplayController,
) {
    private var updateJob: Job? = null
    private val previousBounds = Rect()
    private var previousIndicatorType = IndicatorType.NO_INDICATOR
    private var displayTopologyGraph: DisplayTopologyGraph? = null

    private val displayTopologyListener =
        object : DisplayController.OnDisplaysChangedListener {
            override fun onTopologyChanged(topology: DisplayTopology?) {
                displayTopologyGraph = topology?.getGraph()
            }
        }

    init {
        shellInit.addInitCallback({ onInit() }, this)
    }

    private fun onInit() {
        displayController.addDisplayWindowListener(displayTopologyListener)
    }

    /**
     * Requests an update for the visual indicator based on the current drag state.
     *
     * This function determines whether to update the indicator immediately or schedule a delayed
     * update. A delay is introduced if the drag gesture, defined by the pointer coordinates
     * [inputX] and [inputY] (in pixels relative to the display identified by [displayId]), is
     * potentially moving towards an adjacent display, considering the requested [indicatorType]
     * (e.g., fullscreen, split).
     *
     * An immediate update occurs if the drag is not deemed a potential cross-display move. If a
     * delay *is* scheduled, it can be preempted and updated immediately if the dragged task's
     * bounds, provided in [taskBounds], change significantly compared to the last recorded bounds.
     *
     * The actual UI update logic should be encapsulated in the [performUpdateAction] lambda, which
     * will be executed either immediately or after the delay (on the [mainDispatcher] if delayed).
     */
    fun schedule(
        displayId: Int,
        indicatorType: IndicatorType,
        inputX: Float,
        inputY: Float,
        taskBounds: Rect,
        visualIndicator: DesktopModeVisualIndicator?,
    ) {
        if (!isPotentialCrossDisplayDrag(displayId, indicatorType, inputX, inputY)) {
            updateJob?.cancel()
            visualIndicator?.updateIndicatorWithType(indicatorType)
            return
        }

        if (previousIndicatorType != indicatorType || didBoundsChangeSignificantly(taskBounds)) {
            updateJob?.cancel()
            updateJob =
                bgScope.launch {
                    if (!isActive) return@launch
                    delay(timeMillis = DELAY_MILLIS)
                    withContext(mainDispatcher) {
                        if (!isActive) return@withContext
                        visualIndicator?.updateIndicatorWithType(indicatorType)
                    }
                }
        }

        previousIndicatorType = indicatorType
        previousBounds.set(taskBounds)
    }

    private fun isPotentialCrossDisplayDrag(
        displayId: Int,
        indicatorType: IndicatorType,
        inputX: Float,
        inputY: Float,
    ): Boolean {
        return when (indicatorType) {
            IndicatorType.TO_FULLSCREEN_INDICATOR ->
                isCursorNearAdjacentDisplayEdge(displayId, POSITION_TOP, inputX, inputY)
            IndicatorType.TO_SPLIT_LEFT_INDICATOR ->
                isCursorNearAdjacentDisplayEdge(displayId, POSITION_LEFT, inputX, inputY)
            IndicatorType.TO_SPLIT_RIGHT_INDICATOR ->
                isCursorNearAdjacentDisplayEdge(displayId, POSITION_RIGHT, inputX, inputY)
            // Ignore indicators that don't represent dragging towards a relevant display edge.
            // TO_FULLSCREEN (top edge), TO_SPLIT_LEFT (left edge), and TO_SPLIT_RIGHT (right edge)
            // are the only types considered for potential cross-display transitions.
            else -> false
        }
    }

    private fun isCursorNearAdjacentDisplayEdge(
        displayId: Int,
        position: Int,
        inputX: Float,
        inputY: Float,
    ): Boolean {
        val adjacentDisplays =
            displayTopologyGraph
                ?.displayNodes
                ?.find { node -> node.displayId == displayId }
                ?.adjacentDisplays ?: return false
        val adjacentDisplayId =
            adjacentDisplays.find { adjDisplay -> adjDisplay.position == position }?.displayId
                ?: return false

        val currentDisplayLayout = displayController.getDisplayLayout(displayId) ?: return false

        val adjacentDisplayLayout =
            displayController.getDisplayLayout(adjacentDisplayId) ?: return false

        val currentBounds = currentDisplayLayout.globalBoundsDp()
        val adjacentBounds = adjacentDisplayLayout.globalBoundsDp()
        return if (position == POSITION_TOP) {
            // Horizontal border: Calculate horizontal overlap and check inputX
            val overlapStart = max(currentBounds.left, adjacentBounds.left)
            val overlapEnd = min(currentBounds.right, adjacentBounds.right)
            currentDisplayLayout.pxToDp(inputX) in overlapStart..overlapEnd
        } else {
            // Vertical border (must be LEFT or RIGHT): Calculate vertical overlap and check inputY
            val overlapStart = max(currentBounds.top, adjacentBounds.top)
            val overlapEnd = min(currentBounds.bottom, adjacentBounds.bottom)
            currentDisplayLayout.pxToDp(inputY) in overlapStart..overlapEnd
        }
    }

    private fun didBoundsChangeSignificantly(currentBounds: Rect) =
        abs(currentBounds.left - previousBounds.left) > BOUNDS_CHANGE_THRESHOLD_PX ||
            abs(currentBounds.top - previousBounds.top) > BOUNDS_CHANGE_THRESHOLD_PX ||
            abs(currentBounds.right - previousBounds.right) > BOUNDS_CHANGE_THRESHOLD_PX ||
            abs(currentBounds.bottom - previousBounds.bottom) > BOUNDS_CHANGE_THRESHOLD_PX

    companion object {
        private const val DELAY_MILLIS: Long = 800L
        private const val BOUNDS_CHANGE_THRESHOLD_PX = 5
    }
}
