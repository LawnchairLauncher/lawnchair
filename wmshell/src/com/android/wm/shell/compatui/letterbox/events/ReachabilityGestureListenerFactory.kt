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

import android.window.WindowContainerToken
import com.android.wm.shell.common.suppliers.WindowContainerTransactionSupplier
import com.android.wm.shell.compatui.letterbox.animations.LetterboxAnimationHandler
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.transition.Transitions
import javax.inject.Inject

/** A Factory for [ReachabilityGestureListener]. */
@WMSingleton
class ReachabilityGestureListenerFactory
@Inject
constructor(
    private val transitions: Transitions,
    private val animationHandler: LetterboxAnimationHandler,
    private val wctSupplier: WindowContainerTransactionSupplier,
    private val letterboxState: LetterboxState,
) {
    /**
     * @return a [ReachabilityGestureListener] implementation to listen to double tap events and
     *   creating the related [WindowContainerTransaction] to handle the transition.
     */
    fun createReachabilityGestureListener(
        taskId: Int,
        token: WindowContainerToken?,
    ): ReachabilityGestureListener =
        ReachabilityGestureListener(
            taskId,
            token,
            transitions,
            animationHandler,
            wctSupplier,
            letterboxState,
        )
}
