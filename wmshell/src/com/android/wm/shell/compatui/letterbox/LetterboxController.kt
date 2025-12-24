/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.graphics.Rect
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.WindowContainerToken

/**
 * Abstracts the component responsible to handle a single or multiple letterbox surfaces for a
 * specific [Change].
 */
interface LetterboxController {

    /** Creates a Letterbox Surface for a given displayId/taskId if it doesn't exist. */
    fun createLetterboxSurface(
        key: LetterboxKey,
        transaction: Transaction,
        parentLeash: SurfaceControl,
        token: WindowContainerToken?,
    )

    /** Invoked to destroy the surfaces for a letterbox session for given displayId/taskId. */
    fun destroyLetterboxSurface(key: LetterboxKey, transaction: Transaction)

    /** Invoked to show/hide the letterbox surfaces for given displayId/taskId. */
    fun updateLetterboxSurfaceVisibility(
        key: LetterboxKey,
        transaction: Transaction,
        visible: Boolean,
    )

    /** Updates the bounds for the letterbox surfaces for given displayId/taskId. */
    fun updateLetterboxSurfaceBounds(
        key: LetterboxKey,
        transaction: Transaction,
        taskBounds: Rect,
        activityBounds: Rect,
    )

    /** Utility method to dump the current state. */
    fun dump()
}
