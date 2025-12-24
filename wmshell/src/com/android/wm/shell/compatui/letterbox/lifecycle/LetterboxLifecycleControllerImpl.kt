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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.view.SurfaceControl
import com.android.wm.shell.compatui.letterbox.LetterboxController
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy

/** [LetterboxLifecycleController] default implementation. */
class LetterboxLifecycleControllerImpl(
    private val letterboxController: LetterboxController,
    private val letterboxModeStrategy: LetterboxControllerStrategy,
) : LetterboxLifecycleController {

    override fun onLetterboxLifecycleEvent(
        event: LetterboxLifecycleEvent,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
        val key = event.letterboxKey()
        // Each [LetterboxController] will handle its own Surfaces and will be responsible to
        // avoid the creation happens twice or that some visibility/size change operation
        // happens on missing surfaces.
        val isLetterboxed = event.letterboxBounds?.isEmpty == false
        with(letterboxController) {
            if (isLetterboxed) {
                // In this case the top Activity is letterboxed.
                event.taskLeash?.let { taskLeash ->
                    letterboxModeStrategy.configureLetterboxMode(event)
                    createLetterboxSurface(key, startTransaction, taskLeash, event.containerToken)
                }
            }
            updateLetterboxSurfaceVisibility(key, startTransaction, visible = isLetterboxed)
            // This happens after the visibility update because it needs to
            // check if the surfaces to show have empty bounds. When that happens
            // the clipAndCrop() doesn't actually work because cropping an empty
            // Rect means "do not crop" with the result of a surface filling the
            // task completely.
            if (isLetterboxed) {
                event.letterboxBounds?.let { activityBounds ->
                    updateLetterboxSurfaceBounds(
                        key,
                        startTransaction,
                        event.taskBounds,
                        activityBounds,
                    )
                }
            }
        }
    }
}
