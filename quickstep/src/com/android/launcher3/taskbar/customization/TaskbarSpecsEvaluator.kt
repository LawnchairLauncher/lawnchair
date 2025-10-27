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

package com.android.launcher3.taskbar.customization

import com.android.launcher3.taskbar.TaskbarActivityContext

/** Evaluates the taskbar specs based on the taskbar grid size and the taskbar icon size. */
class TaskbarSpecsEvaluator(
    private val taskbarActivityContext: TaskbarActivityContext,
    private val taskbarFeatureEvaluator: TaskbarFeatureEvaluator,
    numRows: Int = taskbarActivityContext.deviceProfile.inv.numRows,
    numColumns: Int = taskbarActivityContext.deviceProfile.inv.numColumns,
) {
    var taskbarIconSize: TaskbarIconSize = getIconSizeByGrid(numColumns, numRows)
    val numShownHotseatIcons
        get() = taskbarActivityContext.deviceProfile.numShownHotseatIcons

    // TODO(b/341146605) : initialize it to taskbar container in later cl.
    private var taskbarContainer: List<TaskbarContainer> = emptyList()

    val taskbarIconPadding: Int =
        if (
            TaskbarIconSpecs.transientOrPinnedTaskbarIconPaddingSize.size > taskbarIconSize.size &&
                !taskbarFeatureEvaluator.hasNavButtons
        ) {
            (TaskbarIconSpecs.iconSize52dp.size - taskbarIconSize.size) / 2
        } else {
            0
        }

    val taskbarIconMargin: TaskbarIconMarginSize =
        if (taskbarFeatureEvaluator.isTransient) {
            TaskbarIconSpecs.defaultTransientIconMargin
        } else {
            TaskbarIconSpecs.defaultPersistentIconMargin
        }

    fun getIconSizeByGrid(columns: Int, rows: Int): TaskbarIconSize {
        return if (taskbarFeatureEvaluator.isTransient) {
            TaskbarIconSpecs.transientTaskbarIconSizeByGridSize.getOrDefault(
                TransientTaskbarIconSizeKey(columns, rows, taskbarFeatureEvaluator.isLandscape),
                TaskbarIconSpecs.defaultTransientIconSize,
            )
        } else {
            TaskbarIconSpecs.defaultPersistentIconSize
        }
    }

    fun getIconSizeStepDown(iconSize: TaskbarIconSize): TaskbarIconSize {
        if (!taskbarFeatureEvaluator.isTransient) return TaskbarIconSpecs.defaultPersistentIconSize

        val currentIconSizeIndex = TaskbarIconSpecs.transientTaskbarIconSizes.indexOf(iconSize)
        // return the current icon size if supplied icon size is unknown or we have reached the
        // min icon size.
        return if (currentIconSizeIndex == -1 || currentIconSizeIndex == 0) {
            iconSize
        } else {
            TaskbarIconSpecs.transientTaskbarIconSizes[currentIconSizeIndex - 1]
        }
    }

    fun getIconSizeStepUp(iconSize: TaskbarIconSize): TaskbarIconSize {
        if (!taskbarFeatureEvaluator.isTransient) return TaskbarIconSpecs.defaultPersistentIconSize

        val currentIconSizeIndex = TaskbarIconSpecs.transientTaskbarIconSizes.indexOf(iconSize)
        // return the current icon size if supplied icon size is unknown or we have reached the
        // max icon size.
        return if (
            currentIconSizeIndex == -1 ||
                currentIconSizeIndex == TaskbarIconSpecs.transientTaskbarIconSizes.size - 1
        ) {
            iconSize
        } else {
            TaskbarIconSpecs.transientTaskbarIconSizes[currentIconSizeIndex + 1]
        }
    }

    // TODO(jagrutdesai) : Call this in init once the containers are ready.
    private fun calculateTaskbarIconSize() {
        while (
            taskbarIconSize != TaskbarIconSpecs.minimumIconSize &&
                taskbarActivityContext.transientTaskbarBounds.width() <
                    calculateSpaceNeeded(taskbarContainer)
        ) {
            taskbarIconSize = getIconSizeStepDown(taskbarIconSize)
        }
    }

    private fun calculateSpaceNeeded(containers: List<TaskbarContainer>): Int {
        return containers.sumOf { it.spaceNeeded }
    }
}

data class TaskbarIconSize(val size: Int)

data class TransientTaskbarIconSizeKey(val columns: Int, val rows: Int, val isLandscape: Boolean)

data class TaskbarIconMarginSize(val size: Int)
