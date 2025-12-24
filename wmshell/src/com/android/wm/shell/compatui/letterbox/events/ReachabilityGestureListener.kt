/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox.events

import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.window.WindowContainerToken
import com.android.wm.shell.common.suppliers.WindowContainerTransactionSupplier
import com.android.wm.shell.compatui.letterbox.animations.LetterboxAnimationHandler
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_MOVE_LETTERBOX_REACHABILITY

/**
 * [GestureDetector.SimpleOnGestureListener] implementation which receives events from the Letterbox
 * Input surface, understands the type of event and filter them based on the current letterbox
 * position.
 */
class ReachabilityGestureListener(
    private val taskId: Int,
    private val token: WindowContainerToken?,
    private val transitions: Transitions,
    private val animationHandler: LetterboxAnimationHandler,
    private val wctSupplier: WindowContainerTransactionSupplier,
    private val letterboxState: LetterboxState,
) : GestureDetector.SimpleOnGestureListener() {

    // The current letterbox bounds. Double tap events are ignored when happening in these bounds.
    private val activityBounds = Rect()

    override fun onDoubleTap(e: MotionEvent): Boolean {
        val x = e.rawX.toInt()
        val y = e.rawY.toInt()
        if (!activityBounds.contains(x, y)) {
            letterboxState.lastInputSourceId = taskId
            val wct = wctSupplier.get().apply { setReachabilityOffset(token!!, taskId, x, y) }
            transitions.startTransition(TRANSIT_MOVE_LETTERBOX_REACHABILITY, wct, animationHandler)
            return true
        }
        return false
    }

    /** Updates the bounds for the letterboxed activity. */
    fun updateActivityBounds(newActivityBounds: Rect) {
        activityBounds.set(newActivityBounds)
    }
}
