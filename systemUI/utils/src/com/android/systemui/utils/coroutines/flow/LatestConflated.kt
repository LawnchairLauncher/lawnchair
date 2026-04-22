/*
 * Copyright (C) 2024 The Android Open Source Project
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

@file:OptIn(ExperimentalTypeInference::class)

package com.android.systemui.utils.coroutines.flow

import kotlin.experimental.ExperimentalTypeInference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.transformLatest

/**
 * Returns a flow that emits elements from the original flow transformed by [transform] function.
 * When the original flow emits a new value, computation of the [transform] block for previous value
 * is cancelled.
 *
 * For example, the following flow:
 * ```
 * flow {
 *     emit("a")
 *     delay(100)
 *     emit("b")
 * }.mapLatest { value ->
 *     println("Started computing $value")
 *     delay(200)
 *     "Computed $value"
 * }
 * ```
 *
 * will print "Started computing a" and "Started computing b", but the resulting flow will contain
 * only "Computed b" value.
 *
 * This operator is [conflated][conflate] by default, and as such should be preferred over usage of
 * [mapLatest], due to the latter's default configuration of using an internal buffer, negatively
 * impacting system health.
 *
 * @see mapLatest
 */
fun <T, R> Flow<T>.mapLatestConflated(@BuilderInference transform: suspend (T) -> R): Flow<R> =
    mapLatest(transform).conflate()

/**
 * Returns a flow that switches to a new flow produced by [transform] function every time the
 * original flow emits a value. When the original flow emits a new value, the previous flow produced
 * by `transform` block is cancelled.
 *
 * For example, the following flow:
 * ```
 * flow {
 *     emit("a")
 *     delay(100)
 *     emit("b")
 * }.flatMapLatest { value ->
 *     flow {
 *         emit(value)
 *         delay(200)
 *         emit(value + "_last")
 *     }
 * }
 * ```
 *
 * produces `a b b_last`
 *
 * This operator is [conflated][conflate] by default, and as such should be preferred over usage of
 * [flatMapLatest], due to the latter's default configuration of using an internal buffer,
 * negatively impacting system health.
 *
 * @see flatMapLatest
 */
fun <T, R> Flow<T>.flatMapLatestConflated(
    @BuilderInference transform: suspend (T) -> Flow<R>,
): Flow<R> = flatMapLatest(transform).conflate()

/**
 * Returns a flow that produces element by [transform] function every time the original flow emits a
 * value. When the original flow emits a new value, the previous `transform` block is cancelled,
 * thus the name `transformLatest`.
 *
 * For example, the following flow:
 * ```
 * flow {
 *     emit("a")
 *     delay(100)
 *     emit("b")
 * }.transformLatest { value ->
 *     emit(value)
 *     delay(200)
 *     emit(value + "_last")
 * }
 * ```
 *
 * produces `a b b_last`.
 *
 * This operator is [conflated][conflate] by default, and as such should be preferred over usage of
 * [transformLatest], due to the latter's default configuration of using an internal buffer,
 * negatively impacting system health.
 *
 * @see transformLatest
 */
fun <T, R> Flow<T>.transformLatestConflated(
    @BuilderInference transform: suspend FlowCollector<R>.(T) -> Unit,
): Flow<R> = transformLatest(transform).conflate()
