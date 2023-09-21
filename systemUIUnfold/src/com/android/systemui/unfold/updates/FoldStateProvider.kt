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
package com.android.systemui.unfold.updates

import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import com.android.systemui.unfold.updates.FoldStateProvider.FoldUpdatesListener
import com.android.systemui.unfold.util.CallbackController

/**
 * Allows to subscribe to main events related to fold/unfold process such as hinge angle update,
 * start folding/unfolding, screen availability
 */
interface FoldStateProvider : CallbackController<FoldUpdatesListener> {
    fun start()
    fun stop()

    val isFinishedOpening: Boolean

    interface FoldUpdatesListener {
        fun onHingeAngleUpdate(@FloatRange(from = 0.0, to = 180.0) angle: Float) {}
        fun onFoldUpdate(@FoldUpdate update: Int) {}
        fun onUnfoldedScreenAvailable() {}
    }

    @IntDef(
        value =
            [
                FOLD_UPDATE_START_OPENING,
                FOLD_UPDATE_START_CLOSING,
                FOLD_UPDATE_FINISH_HALF_OPEN,
                FOLD_UPDATE_FINISH_FULL_OPEN,
                FOLD_UPDATE_FINISH_CLOSED])
    @Retention(AnnotationRetention.SOURCE)
    annotation class FoldUpdate
}

const val FOLD_UPDATE_START_OPENING = 0
const val FOLD_UPDATE_START_CLOSING = 1
const val FOLD_UPDATE_FINISH_HALF_OPEN = 2
const val FOLD_UPDATE_FINISH_FULL_OPEN = 3
const val FOLD_UPDATE_FINISH_CLOSED = 4
