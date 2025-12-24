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

package com.android.systemui.util.kotlin

//import com.android.app.tracing.coroutines.launchTraced as launch
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Returns a new [Flow] that combines the two most recent emissions from [this] using [transform].
 * Note that the new Flow will not start emitting until it has received two emissions from the
 * upstream Flow.
 *
 * Useful for code that needs to compare the current value to the previous value.
 */
public fun <T, R> Flow<T>.pairwiseBy(transform: suspend (old: T, new: T) -> R): Flow<R> = flow {
    val noVal = Any()
    var previousValue: Any? = noVal
    collect { newVal ->
        if (previousValue != noVal) {
            @Suppress("UNCHECKED_CAST") emit(transform(previousValue as T, newVal))
        }
        previousValue = newVal
    }
}

/**
 * Returns a new [Flow] that combines the two most recent emissions from [this] using [transform].
 * [initialValue] will be used as the "old" value for the first emission.
 *
 * Useful for code that needs to compare the current value to the previous value.
 */
public fun <S, T : S, R> Flow<T>.pairwiseBy(
    initialValue: S,
    transform: suspend (previousValue: S, newValue: T) -> R,
): Flow<R> = pairwiseBy(getInitialValue = { initialValue }, transform)

/**
 * Returns a new [Flow] that combines the two most recent emissions from [this] using [transform].
 *
 * The output of [getInitialValue] will be used as the "old" value for the first emission. As
 * opposed to the initial value in the above [pairwiseBy], [getInitialValue] can do some work before
 * returning the initial value.
 *
 * Useful for code that needs to compare the current value to the previous value.
 */
public fun <S, T : S, R> Flow<T>.pairwiseBy(
    getInitialValue: suspend () -> S,
    transform: suspend (previousValue: S, newValue: T) -> R,
): Flow<R> = flow {
    var previousValue: S = getInitialValue()
    collect { newVal ->
        emit(transform(previousValue, newVal))
        previousValue = newVal
    }
}

/**
 * Returns a new [Flow] that produces the two most recent emissions from [this]. Note that the new
 * Flow will not start emitting until it has received two emissions from the upstream Flow.
 *
 * Useful for code that needs to compare the current value to the previous value.
 */
public fun <T> Flow<T>.pairwise(): Flow<WithPrev<T, T>> = pairwiseBy(::WithPrev)

/**
 * Returns a new [Flow] that produces the two most recent emissions from [this]. [initialValue] will
 * be used as the "old" value for the first emission.
 *
 * Useful for code that needs to compare the current value to the previous value.
 */
public fun <S, T : S> Flow<T>.pairwise(initialValue: S): Flow<WithPrev<S, T>> =
    pairwiseBy(initialValue, ::WithPrev)

/** Holds a [newValue] emitted from a [Flow], along with the [previousValue] emitted value. */
public data class WithPrev<out S, out T : S>(val previousValue: S, val newValue: T)

/** Emits a [Unit] only when the number of downstream subscribers of this flow increases. */
public fun <T> MutableSharedFlow<T>.onSubscriberAdded(): Flow<Unit> {
    return subscriptionCount
        .pairwise(initialValue = 0)
        .filter { (previous, current) -> current > previous }
        .map {}
}

/**
 * Returns a new [Flow] that combines the [Set] changes between each emission from [this] using
 * [transform].
 *
 * If [emitFirstEvent] is `true`, then the first [Set] emitted from the upstream [Flow] will cause a
 * change event to be emitted that contains no removals, and all elements from that first [Set] as
 * additions.
 *
 * If [emitFirstEvent] is `false`, then the first emission is ignored and no changes are emitted
 * until a second [Set] has been emitted from the upstream [Flow].
 */
public fun <T, R> Flow<Set<T>>.setChangesBy(
    transform: suspend (removed: Set<T>, added: Set<T>) -> R,
    emitFirstEvent: Boolean = true,
): Flow<R> =
    (if (emitFirstEvent) onStart { emit(emptySet()) } else this)
        .distinctUntilChanged()
        .pairwiseBy { old: Set<T>, new: Set<T> ->
            // If an element was present in the old set, but not the new one, then it was removed
            val removed = old - new
            // If an element is present in the new set, but on the old one, then it was added
            val added = new - old
            transform(removed, added)
        }

/**
 * Returns a new [Flow] that produces the [Set] changes between each emission from [this].
 *
 * If [emitFirstEvent] is `true`, then the first [Set] emitted from the upstream [Flow] will cause a
 * change event to be emitted that contains no removals, and all elements from that first [Set] as
 * additions.
 *
 * If [emitFirstEvent] is `false`, then the first emission is ignored and no changes are emitted
 * until a second [Set] has been emitted from the upstream [Flow].
 */
public fun <T> Flow<Set<T>>.setChanges(emitFirstEvent: Boolean = true): Flow<SetChanges<T>> =
    setChangesBy(::SetChanges, emitFirstEvent)

/** Contains the difference in elements between two [Set]s. */
public data class SetChanges<T>(
    /** Elements that are present in the first [Set] but not in the second. */
    val removed: Set<T>,
    /** Elements that are present in the second [Set] but not in the first. */
    val added: Set<T>,
)

/**
 * Returns a new [Flow] that emits at the same rate as [this], but combines the emitted value with
 * the most recent emission from [other] using [transform].
 *
 * Note that the returned Flow will not emit anything until [other] has emitted at least one value.
 */
public fun <A, B, C> Flow<A>.sample(other: Flow<B>, transform: suspend (A, B) -> C): Flow<C> =
    flow {
        coroutineScope {
            val noVal = Any()
            val sampledRef = AtomicReference(noVal)
//            val job =
//                launch(context = Dispatchers.Unconfined) { other.collect { sampledRef.set(it) } }
            collect {
                val sampled = sampledRef.get()
                if (sampled != noVal) {
                    @Suppress("UNCHECKED_CAST") emit(transform(it, sampled as B))
                }
            }
//            job.cancel()
        }
    }

/**
 * Returns a new [Flow] that emits at the same rate as [this], but emits the most recently emitted
 * value from [other] instead.
 *
 * Note that the returned Flow will not emit anything until [other] has emitted at least one value.
 */
public fun <A> Flow<*>.sample(other: Flow<A>): Flow<A> = sample(other) { _, a -> a }

public inline fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6) -> R,
): Flow<R> {
    return kotlinx.coroutines.flow.combine(flow, flow2, flow3, flow4, flow5, flow6) { args: Array<*>
        ->
        @Suppress("UNCHECKED_CAST")
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6,
        )
    }
}

public inline fun <T1, T2, T3, T4, T5, T6, T7, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7) -> R,
): Flow<R> {
    return kotlinx.coroutines.flow.combine(flow, flow2, flow3, flow4, flow5, flow6, flow7) {
        args: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6,
            args[6] as T7,
        )
    }
}

public inline fun <T1, T2, T3, T4, T5, T6, T7, T8, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8) -> R,
): Flow<R> {
    return kotlinx.coroutines.flow.combine(flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8) {
        args: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6,
            args[6] as T7,
            args[7] as T8,
        )
    }
}

public inline fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> R,
): Flow<R> {
    return kotlinx.coroutines.flow.combine(
        flow,
        flow2,
        flow3,
        flow4,
        flow5,
        flow6,
        flow7,
        flow8,
        flow9,
    ) { args: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6,
            args[6] as T7,
            args[7] as T8,
            args[8] as T9,
        )
    }
}

/**
 * Returns a [Flow] that immediately emits [Unit] when started, then emits from the given upstream
 * [Flow] as normal.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun Flow<Unit>.emitOnStart(): Flow<Unit> = onStart { emit(Unit) }

