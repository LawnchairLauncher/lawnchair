/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.unfold.util

import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import com.android.systemui.unfold.updates.FoldProvider
import com.android.systemui.unfold.updates.FoldProvider.FoldCallback
import java.util.concurrent.Executor

/**
 * [UnfoldTransitionProgressProvider] that emits transition progress only when unfolding but not
 * when folding, so we can play the animation only one way but not the other way.
 */
class UnfoldOnlyProgressProvider(
    foldProvider: FoldProvider,
    @Main private val executor: Executor,
    private val sourceProvider: UnfoldTransitionProgressProvider,
    private val scopedProvider: ScopedUnfoldTransitionProgressProvider =
        ScopedUnfoldTransitionProgressProvider(sourceProvider)
) : UnfoldTransitionProgressProvider by scopedProvider {

    private var isFolded = false

    init {
        foldProvider.registerCallback(FoldListener(), executor)
        sourceProvider.addCallback(SourceTransitionListener())
    }

    private inner class SourceTransitionListener : TransitionProgressListener {
        override fun onTransitionFinished() {
            // Disable scoped progress provider after the first unfold animation, so fold animation
            // will not be propagated. It will be re-enabled after folding so we can play
            // the unfold animation again.
            if (!isFolded) {
                scopedProvider.setReadyToHandleTransition(false)
            }
        }
    }

    private inner class FoldListener : FoldCallback {
        override fun onFoldUpdated(isFolded: Boolean) {
            if (isFolded) {
                scopedProvider.setReadyToHandleTransition(true)
            }

            this@UnfoldOnlyProgressProvider.isFolded = isFolded
        }
    }
}
