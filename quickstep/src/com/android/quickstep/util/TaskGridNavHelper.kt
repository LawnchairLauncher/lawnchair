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
package com.android.quickstep.util

import com.android.launcher3.util.IntArray
import kotlin.math.abs
import kotlin.math.max

/** Helper class for navigating RecentsView grid tasks via arrow keys and tab. */
class TaskGridNavHelper(
    private val topIds: IntArray,
    bottomIds: IntArray,
    largeTileIds: List<Int>,
    hasAddDesktopButton: Boolean,
) {
    private val topRowIds = mutableListOf<Int>()
    private val bottomRowIds = mutableListOf<Int>()

    init {
        // Add AddDesktopButton and lage tiles to both rows.
        if (hasAddDesktopButton) {
            topRowIds += ADD_DESK_PLACEHOLDER_ID
            bottomRowIds += ADD_DESK_PLACEHOLDER_ID
        }
        topRowIds += largeTileIds
        bottomRowIds += largeTileIds

        // Add row ids to their respective rows.
        topRowIds += topIds
        bottomRowIds += bottomIds

        // Fill in the shorter array with the ids from the longer one.
        topRowIds += bottomRowIds.takeLast(max(bottomRowIds.size - topRowIds.size, 0))
        bottomRowIds += topRowIds.takeLast(max(topRowIds.size - bottomRowIds.size, 0))

        // Add the clear all button to the end of both arrays.
        topRowIds += CLEAR_ALL_PLACEHOLDER_ID
        bottomRowIds += CLEAR_ALL_PLACEHOLDER_ID
    }

    /** Returns the id of the next page in the grid or -1 for the clear all button. */
    fun getNextGridPage(
        currentPageTaskViewId: Int,
        delta: Int,
        direction: TaskNavDirection,
        cycle: Boolean,
    ): Int {
        val inTop = topRowIds.contains(currentPageTaskViewId)
        val inBottom = bottomRowIds.contains(currentPageTaskViewId)
        if (!inTop && !inBottom) {
            return currentPageTaskViewId
        }
        val index =
            if (inTop) topRowIds.indexOf(currentPageTaskViewId)
            else bottomRowIds.indexOf(currentPageTaskViewId)
        val maxSize = max(topRowIds.size, bottomRowIds.size)
        val nextIndex = index + delta

        return when (direction) {
            TaskNavDirection.UP,
            TaskNavDirection.DOWN -> {
                if (inTop) bottomRowIds[index] else topRowIds[index]
            }
            TaskNavDirection.LEFT -> {
                val boundedIndex =
                    if (cycle) nextIndex % maxSize else nextIndex.coerceAtMost(maxSize - 1)
                if (inTop) topRowIds[boundedIndex] else bottomRowIds[boundedIndex]
            }
            TaskNavDirection.RIGHT -> {
                val boundedIndex =
                    if (cycle) (if (nextIndex < 0) maxSize - 1 else nextIndex)
                    else nextIndex.coerceAtLeast(0)
                val inOriginalTop = topIds.contains(currentPageTaskViewId)
                if (inOriginalTop) topRowIds[boundedIndex] else bottomRowIds[boundedIndex]
            }
            TaskNavDirection.TAB -> {
                val boundedIndex =
                    if (cycle) (if (nextIndex < 0) maxSize - 1 else nextIndex % maxSize)
                    else nextIndex.coerceAtMost(maxSize - 1)
                if (delta >= 0) {
                    if (inTop && topRowIds[index] != bottomRowIds[index]) bottomRowIds[index]
                    else topRowIds[boundedIndex]
                } else {
                    if (topRowIds.contains(currentPageTaskViewId)) {
                        if (boundedIndex < 0) {
                            // If no cycling, always return the first task.
                            topRowIds[0]
                        } else {
                            bottomRowIds[boundedIndex]
                        }
                    } else {
                        // Go up to top if there is task above
                        if (topRowIds[index] != bottomRowIds[index]) topRowIds[index]
                        else bottomRowIds[boundedIndex]
                    }
                }
            }
            else -> currentPageTaskViewId
        }
    }

    /**
     * Returns a sequence of pairs of (TaskView ID, offset) in the grid, ordered according to tab
     * navigation, starting from the initial TaskView ID, towards the start or end of the grid.
     *
     * <p>A positive delta moves forward in the tab order towards the end of the grid, while a
     * negative value moves backward towards the beginning. The offset is the distance between
     * columns the tasks are in.
     */
    fun gridTaskViewIdOffsetPairInTabOrderSequence(
        initialTaskViewId: Int,
        towardsStart: Boolean,
    ): Sequence<Pair<Int, Int>> = sequence {
        val draggedTaskViewColumn = getColumn(initialTaskViewId)
        var nextTaskViewId: Int = initialTaskViewId
        var previousTaskViewId: Int = Int.MIN_VALUE
        while (nextTaskViewId != previousTaskViewId && nextTaskViewId >= 0) {
            previousTaskViewId = nextTaskViewId
            nextTaskViewId =
                getNextGridPage(
                    nextTaskViewId,
                    if (towardsStart) -1 else 1,
                    TaskNavDirection.TAB,
                    cycle = false,
                )
            if (nextTaskViewId >= 0 && nextTaskViewId != previousTaskViewId) {
                val columnOffset = abs(getColumn(nextTaskViewId) - draggedTaskViewColumn)
                yield(Pair(nextTaskViewId, columnOffset))
            }
        }
    }

    /** Returns the column of a task's id in the grid. */
    private fun getColumn(taskViewId: Int): Int =
        if (topRowIds.contains(taskViewId)) topRowIds.indexOf(taskViewId)
        else bottomRowIds.indexOf(taskViewId)

    enum class TaskNavDirection {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        TAB,
    }

    companion object {
        const val CLEAR_ALL_PLACEHOLDER_ID: Int = -1
        const val ADD_DESK_PLACEHOLDER_ID: Int = -2
    }
}
