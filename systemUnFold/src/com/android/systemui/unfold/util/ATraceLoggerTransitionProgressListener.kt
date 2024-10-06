/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.os.Trace
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Qualifier

/**
 * Listener that logs start and end of the fold-unfold transition.
 *
 * [tracePrefix] arg helps in differentiating those. Currently, this is expected to be logged twice
 * for each fold/unfold: in (1) systemui and (2) launcher process.
 */
class ATraceLoggerTransitionProgressListener
@AssistedInject
internal constructor(@UnfoldTransitionATracePrefix tracePrefix: String, @Assisted details: String) :
    TransitionProgressListener {

    private val traceName = "$tracePrefix$details#$UNFOLD_TRANSITION_TRACE_NAME"

    override fun onTransitionStarted() {
        Trace.beginAsyncSection(traceName, /* cookie= */ 0)
    }

    override fun onTransitionFinished() {
        Trace.endAsyncSection(traceName, /* cookie= */ 0)
    }

    override fun onTransitionProgress(progress: Float) {
        Trace.setCounter(traceName, (progress * 100).toLong())
    }

    @AssistedFactory
    interface Factory {
        /** Creates an [ATraceLoggerTransitionProgressListener] with [details] in the track name. */
        fun create(details: String): ATraceLoggerTransitionProgressListener
    }
}

private const val UNFOLD_TRANSITION_TRACE_NAME = "FoldUnfoldTransitionInProgress"

@Qualifier annotation class UnfoldTransitionATracePrefix
