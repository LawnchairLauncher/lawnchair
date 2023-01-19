/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.unfold

import android.annotation.FloatRange
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import com.android.systemui.statusbar.policy.CallbackController

/**
 * Interface that allows to receive unfold transition progress updates.
 * It can be used to update view properties based on the current animation progress.
 * onTransitionProgress callback could be called on each frame.
 *
 * Use [createUnfoldTransitionProgressProvider] to create instances of this interface
 */
interface UnfoldTransitionProgressProvider : CallbackController<TransitionProgressListener> {

    fun destroy()

    interface TransitionProgressListener {
        fun onTransitionStarted() {}
        fun onTransitionFinished() {}
        fun onTransitionProgress(@FloatRange(from = 0.0, to = 1.0) progress: Float) {}
    }
}
