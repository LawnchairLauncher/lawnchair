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

package com.android.quickstep.recents.domain.usecase

import android.graphics.Rect
import android.view.Gravity
import com.android.quickstep.recents.domain.model.DesktopLayoutConfig
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData.RenderedDesktopTaskBoundsData

/** Utility functions for desktop layout calculations. */
object DesktopLayoutUtils {

    /**
     * Calculates the minimum height required to display all tasks if they were to be rendered at
     * their `layoutConfig.minTaskWidth`, maintaining their original aspect ratios.
     *
     * This ensures that when tasks are scaled to a minimum width, they are still tall enough to be
     * recognizable and interactable, based on their original proportions.
     *
     * @param taskBounds The list of tasks with their original bounds.
     * @param layoutConfig The layout configuration containing minimum task width and padding.
     * @return The maximum height calculated across all tasks when scaled to `minTaskWidth`, or
     *   `layoutConfig.verticalPaddingBetweenTasks` if `minTaskWidth` is not positive or no valid
     *   task dimensions are found.
     */
    fun getRequiredHeightForMinWidth(
        taskBounds: List<RenderedDesktopTaskBoundsData>,
        layoutConfig: DesktopLayoutConfig,
    ): Int {
        val defaultMinHeight = layoutConfig.verticalPaddingBetweenTasks
        if (layoutConfig.minTaskWidth <= 0) {
            return defaultMinHeight
        }

        val maxCalculatedHeight =
            taskBounds.maxOfOrNull { taskData ->
                with(taskData.bounds) {
                    if (width() > 0 && height() > 0) {
                        val aspectRatio = height().toFloat() / width().toFloat()
                        (layoutConfig.minTaskWidth.toFloat() * aspectRatio).toInt()
                    } else 0
                }
            } ?: 0

        return Math.max(maxCalculatedHeight, defaultMinHeight)
    }

    /**
     * Creates a small, square placeholder Rect centered within the given
     * [layoutConfig.desktopBounds]. The size of the square is determined by
     * [layoutConfig.minTaskWidth]. Used for tasks that cannot be laid out due to the size
     * constraints.
     */
    fun createPlaceholderBounds(layoutConfig: DesktopLayoutConfig): Rect {
        val desktopBounds = layoutConfig.desktopBounds
        if (desktopBounds.isEmpty) {
            // If desktopBounds is empty, we can't really center. Return an empty rect.
            return Rect()
        }
        val size = minOf(layoutConfig.minTaskWidth, desktopBounds.width(), desktopBounds.height())
        return Rect().apply { Gravity.apply(Gravity.CENTER, size, size, desktopBounds, this) }
    }

    /** Calculates the maximum possible height for a task window in the desktop tile in Overview. */
    fun getMaxTaskHeight(effectiveLayoutBounds: Rect) = effectiveLayoutBounds.height()

    /**
     * Calculates the task height allowed given the constraint of fitting tasks into
     * [layoutConfig].maxRows, considering necessary padding. Returns a positive height if possible,
     * or verticalPadding if constraints make it impossible.
     */
    fun getMinTaskHeightGivenMaxRows(
        availableLayoutBounds: Rect,
        layoutConfig: DesktopLayoutConfig,
    ): Int {
        val verticalPadding = layoutConfig.verticalPaddingBetweenTasks
        if (layoutConfig.maxRows <= 0) {
            return verticalPadding
        }

        // The following calculation is to make sure the returned height is the minimum height that
        // can't fix all windows in (maxRows +1) row.
        val totalPaddingBetweenRows = layoutConfig.maxRows * verticalPadding
        val heightForTaskContents = availableLayoutBounds.height() - totalPaddingBetweenRows
        if (heightForTaskContents <= 0) {
            return verticalPadding
        }
        return Math.max(
            (heightForTaskContents.toFloat() / (layoutConfig.maxRows + 1) + 1).toInt(),
            verticalPadding,
        )
    }
}

/** Calculates the effective bounds for layout by applying insets to the raw desktop bounds. */
fun Rect.getLayoutEffectiveBounds(
    singleRow: Boolean,
    taskNumber: Int,
    layoutConfig: DesktopLayoutConfig,
) =
    Rect(this).apply {
        val topInset =
            if (singleRow) layoutConfig.topBottomMarginOneRow else layoutConfig.topMarginMultiRows
        val bottomInset =
            if (singleRow) layoutConfig.topBottomMarginOneRow
            else layoutConfig.bottomMarginMultiRows
        val leftInset =
            if (singleRow && taskNumber <= 1) layoutConfig.leftRightMarginOneRow
            else layoutConfig.leftRightMarginMultiRows
        val rightInset =
            if (singleRow && taskNumber <= 1) leftInset
            else (leftInset - layoutConfig.horizontalPaddingBetweenTasks)

        inset(leftInset, topInset, rightInset, bottomInset)
    }
