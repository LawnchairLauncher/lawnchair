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

package com.android.quickstep.recents.ui.viewmodel

import android.graphics.Rect
import android.util.Size
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData
import com.android.quickstep.recents.domain.usecase.OrganizeDesktopTasksUseCase

/** ViewModel used for [com.android.quickstep.views.DesktopTaskView]. */
class DesktopTaskViewModel(private val organizeDesktopTasksUseCase: OrganizeDesktopTasksUseCase) {
    /** Positions for desktop tasks as calculated by [organizeDesktopTasksUseCase] */
    var organizedDesktopTaskPositions = emptyList<DesktopTaskBoundsData>()
        private set

    /**
     * Computes new task positions using [organizeDesktopTasksUseCase]. The result is stored in
     * [organizedDesktopTaskPositions]. This is used for the exploded desktop view where the usecase
     * will scale and translate tasks so that they don't overlap.
     *
     * @param desktopSize the size available for organizing the tasks.
     * @param defaultPositions the tasks and their bounds as they appear on a desktop.
     */
    fun organizeDesktopTasks(desktopSize: Size, defaultPositions: List<DesktopTaskBoundsData>) {
        organizedDesktopTaskPositions =
            organizeDesktopTasksUseCase.run(
                desktopBounds = Rect(0, 0, desktopSize.width, desktopSize.height),
                taskBounds = defaultPositions,
            )
    }
}
