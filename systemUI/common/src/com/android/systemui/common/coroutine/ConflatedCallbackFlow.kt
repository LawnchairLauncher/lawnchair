/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.common.coroutine

import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow as wrapped
import kotlin.experimental.ExperimentalTypeInference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Deprecated("Use com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow instead")
object ConflatedCallbackFlow {

    /**
     * A [callbackFlow] that uses a buffer [Channel] that is "conflated" meaning that, if
     * backpressure occurs (if the producer that emits new values into the flow is faster than the
     * consumer(s) of the values in the flow), the values are buffered and, if the buffer fills up,
     * we drop the oldest values automatically instead of suspending the producer.
     */
    @Deprecated(
        "Use com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow instead",
        ReplaceWith(
            "conflatedCallbackFlow",
            "com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow"
        )
    )
    @OptIn(ExperimentalTypeInference::class)
    fun <T> conflatedCallbackFlow(
        @BuilderInference block: suspend ProducerScope<T>.() -> Unit,
    ): Flow<T> = wrapped(block)
}
