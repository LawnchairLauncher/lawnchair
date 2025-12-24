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

import android.graphics.Region
import com.android.quickstep.recents.ui.viewmodel.DesktopTaskViewModel.TaskPosition

/**
 * This usecase is responsible for returning the task IDs of all desktop windows that are completely
 * obscured (they are completely overlapped by windows above them in the z-order).
 */
class GetObscuredDesktopTaskIdsUseCase {
    operator fun invoke(desktopTaskPositions: List<TaskPosition>): Set<Int> {
        var obscuredWindowIdSet = mutableSetOf<Int>()
        val totalOccludedRegion = Region()

        // From top to bottom in the z-order, keep adding the area of each visible window to
        // totalOccludedRegion to check if each window is obscured.
        desktopTaskPositions.forEach { taskPosition ->
            // If window is minimized, it won't occlude our window or be occluded, and we can
            // continue.
            if (taskPosition.isMinimized) {
                return@forEach
            }

            // To see if the occluded region completely obscures the current window, check if
            // the intersection of the region and the task bounds is the same as the task bounds
            // itself. If the window is completely obscured, the intersection should also be a
            // single rectangle (not complex) and we can use Region.quickContains() to verify
            // they are the same.
            val currentOccludedRegion = Region(totalOccludedRegion)
            currentOccludedRegion.op(taskPosition.bounds, Region.Op.INTERSECT)
            if (currentOccludedRegion.quickContains(taskPosition.bounds)) {
                obscuredWindowIdSet.add(taskPosition.taskId)
            } else {
                // Add the window's bounds to the total occluded region for the next iteration. This
                // only needs to be done if the current window isn't occluded, as otherwise its
                // bounds are already part of the total.
                totalOccludedRegion.op(taskPosition.bounds, Region.Op.UNION)
            }
        }

        return obscuredWindowIdSet
    }
}
