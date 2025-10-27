package com.android.systemui.shared.condition

import com.android.systemui.shared.condition.Condition.StartStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Converts a boolean flow to a [Condition] object which can be used with a [Monitor] */
@JvmOverloads
fun Flow<Boolean>.toCondition(
    scope: CoroutineScope,
    @StartStrategy strategy: Int,
    initialValue: Boolean? = null,
): Condition {
    return object : Condition(scope, initialValue, false) {
        var job: Job? = null

        override suspend fun start() {
            job = scope.launch { collect { updateCondition(it) } }
        }

        override fun stop() {
            job?.cancel()
            job = null
        }

        override val startStrategy: Int
            get() = strategy
    }
}

/** Converts a [Condition] to a boolean flow */
fun Condition.toFlow(): Flow<Boolean?> {
    return callbackFlow {
            val callback =
                object : Condition.Callback {
                    override fun onConditionChanged(condition: Condition) {
                        if (condition.isConditionSet) {
                            trySend(condition.isConditionMet)
                        } else {
                            trySend(null)
                        }
                    }
                }

            addCallback(callback)
            callback.onConditionChanged(this@toFlow)
            awaitClose { removeCallback(callback) }
        }
        .distinctUntilChanged()
}
