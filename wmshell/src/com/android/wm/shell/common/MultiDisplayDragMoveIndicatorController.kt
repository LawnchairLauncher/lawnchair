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
package com.android.wm.shell.common

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.RectF
import android.view.SurfaceControl
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.shared.annotations.ShellDesktopThread
import com.android.wm.shell.shared.desktopmode.DesktopState

/**
 * Controller to manage the indicators that show users the current position of the dragged window on
 * the new display when performing drag move across displays.
 */
class MultiDisplayDragMoveIndicatorController(
    private val displayController: DisplayController,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val indicatorSurfaceFactory: MultiDisplayDragMoveIndicatorSurface.Factory,
    @ShellDesktopThread private val desktopExecutor: ShellExecutor,
    private val desktopState: DesktopState,
) {
    @ShellDesktopThread
    private val dragIndicators =
        mutableMapOf<Int, MutableMap<Int, MultiDisplayDragMoveIndicatorSurface>>()

    /**
     * Called during drag move, which started at [startDisplayId] and currently at
     * [currentDisplayid]. Updates the position and visibility of the drag move indicators for the
     * [taskInfo] based on [boundsDp] on the destination displays ([displayIds]) as the dragged
     * window moves. [transactionSupplier] provides a [SurfaceControl.Transaction] for applying
     * changes to the indicator surfaces.
     *
     * It is executed on the [desktopExecutor] to prevent blocking the main thread and avoid jank,
     * as creating and manipulating surfaces can be expensive.
     */
    fun onDragMove(
        boundsDp: RectF,
        currentDisplayId: Int,
        startDisplayId: Int,
        taskLeash: SurfaceControl,
        taskInfo: RunningTaskInfo,
        displayIds: Set<Int>,
        transactionSupplier: () -> SurfaceControl.Transaction,
    ) {
        desktopExecutor.execute {
            val startDisplayDpi =
                displayController.getDisplayLayout(startDisplayId)?.densityDpi() ?: return@execute
            val transaction = transactionSupplier()
            for (displayId in displayIds) {
                if (
                    displayId == startDisplayId ||
                        !desktopState.isDesktopModeSupportedOnDisplay(displayId)
                ) {
                    // No need to render indicators on the original display where the drag started,
                    // or on displays that do not support desktop mode.
                    continue
                }
                val displayLayout = displayController.getDisplayLayout(displayId) ?: continue
                val displayContext = displayController.getDisplayContext(displayId) ?: continue
                val visibility =
                    if (RectF.intersects(RectF(boundsDp), displayLayout.globalBoundsDp())) {
                        if (displayId == currentDisplayId) {
                            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE
                        } else {
                            MultiDisplayDragMoveIndicatorSurface.Visibility.TRANSLUCENT
                        }
                    } else {
                        MultiDisplayDragMoveIndicatorSurface.Visibility.INVISIBLE
                    }
                if (
                    dragIndicators[taskInfo.taskId]?.containsKey(displayId) != true &&
                        visibility == MultiDisplayDragMoveIndicatorSurface.Visibility.INVISIBLE
                ) {
                    // Skip this display if:
                    // - It doesn't have an existing indicator that needs to be updated, AND
                    // - The latest dragged window bounds don't intersect with this display.
                    continue
                }

                val boundsPx =
                    MultiDisplayDragMoveBoundsCalculator.convertGlobalDpToLocalPxForRect(
                        boundsDp,
                        displayLayout,
                    )

                // Get or create the inner map for the current task.
                val dragIndicatorsForTask =
                    dragIndicators.getOrPut(taskInfo.taskId) { mutableMapOf() }
                dragIndicatorsForTask[displayId]?.also { existingIndicator ->
                    existingIndicator.relayout(boundsPx, transaction, visibility)
                }
                    ?: run {
                        val newIndicator = indicatorSurfaceFactory.create(displayContext, taskLeash)
                        newIndicator.show(
                            transaction,
                            taskInfo,
                            rootTaskDisplayAreaOrganizer,
                            displayId,
                            boundsPx,
                            visibility,
                            displayLayout.densityDpi().toFloat() / startDisplayDpi.toFloat(),
                        )
                        dragIndicatorsForTask[displayId] = newIndicator
                    }
            }
            transaction.apply()
        }
    }

    /**
     * Called when the drag ends. Disposes of the drag move indicator surfaces associated with the
     * given [taskId]. [transactionSupplier] provides a [SurfaceControl.Transaction] for applying
     * changes to the indicator surfaces.
     *
     * It is executed on the [desktopExecutor] to ensure that any pending `onDragMove` operations
     * have completed before disposing of the surfaces.
     */
    fun onDragEnd(taskId: Int, transactionSupplier: () -> SurfaceControl.Transaction) {
        desktopExecutor.execute {
            dragIndicators
                .remove(taskId)
                ?.values
                ?.takeIf { it.isNotEmpty() }
                ?.let { indicators ->
                    val transaction = transactionSupplier()
                    indicators.forEach { indicator -> indicator.dispose(transaction) }
                    transaction.apply()
                }
        }
    }
}
