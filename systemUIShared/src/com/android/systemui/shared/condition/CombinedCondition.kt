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

package com.android.systemui.shared.condition

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * A higher order [Condition] which combines multiple conditions with a specified
 * [Evaluator.ConditionOperand]. Conditions are executed lazily as-needed.
 *
 * @param scope The [CoroutineScope] to execute in.
 * @param conditions The list of conditions to evaluate. Since conditions are executed lazily, the
 *   ordering is important here.
 * @param operand The [Evaluator.ConditionOperand] to apply to the conditions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CombinedCondition
constructor(
    private val scope: CoroutineScope,
    private val conditions: Collection<Condition>,
    @Evaluator.ConditionOperand private val operand: Int
) : Condition(scope, null, false) {

    private var job: Job? = null
    private val _startStrategy by lazy { calculateStartStrategy() }

    override fun start() {
        job =
            scope.launch {
                val groupedConditions = conditions.groupBy { it.isOverridingCondition }

                lazilyEvaluate(
                        conditions = groupedConditions.getOrDefault(true, emptyList()),
                        filterUnknown = true
                    )
                    .distinctUntilChanged()
                    .flatMapLatest { overriddenValue ->
                        // If there are overriding conditions with values set, they take precedence.
                        if (overriddenValue == null) {
                            lazilyEvaluate(
                                conditions = groupedConditions.getOrDefault(false, emptyList()),
                                filterUnknown = false
                            )
                        } else {
                            flowOf(overriddenValue)
                        }
                    }
                    .collect { conditionMet ->
                        if (conditionMet == null) {
                            clearCondition()
                        } else {
                            updateCondition(conditionMet)
                        }
                    }
            }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Evaluates a list of conditions lazily with support for short-circuiting. Conditions are
     * executed serially in the order provided. At any point if the result can be determined, we
     * short-circuit and return the result without executing all conditions.
     */
    private fun lazilyEvaluate(
        conditions: Collection<Condition>,
        filterUnknown: Boolean,
    ): Flow<Boolean?> = callbackFlow {
        val jobs = MutableList<Job?>(conditions.size) { null }
        val values = MutableList<Boolean?>(conditions.size) { null }
        val flows = conditions.map { it.toFlow() }

        fun cancelAllExcept(indexToSkip: Int) {
            for (index in 0 until jobs.size) {
                if (index == indexToSkip) {
                    continue
                }
                if (
                    indexToSkip == -1 ||
                        conditions.elementAt(index).startStrategy == START_WHEN_NEEDED
                ) {
                    jobs[index]?.cancel()
                    jobs[index] = null
                    values[index] = null
                }
            }
        }

        fun collectFlow(index: Int) {
            // Base case which is triggered once we have collected all the flows. In this case,
            // we never short-circuited and therefore should return the fully evaluated
            // conditions.
            if (flows.isEmpty() || index == -1) {
                val filteredValues =
                    if (filterUnknown) {
                        values.filterNotNull()
                    } else {
                        values
                    }
                trySend(Evaluator.evaluate(filteredValues, operand))
                return
            }
            jobs[index] =
                scope.launch {
                    flows.elementAt(index).collect { value ->
                        values[index] = value
                        if (shouldEarlyReturn(value)) {
                            trySend(value)
                            // The overall result is contingent on this condition, so we don't need
                            // to monitor any other conditions.
                            cancelAllExcept(index)
                        } else {
                            collectFlow(jobs.indexOfFirst { it == null })
                        }
                    }
                }
        }

        // Collect any eager conditions immediately.
        var started = false
        for ((index, condition) in conditions.withIndex()) {
            if (condition.startStrategy == START_EAGERLY) {
                collectFlow(index)
                started = true
            }
        }

        // If no eager conditions started, start the first condition to kick off evaluation.
        if (!started) {
            collectFlow(0)
        }
        awaitClose { cancelAllExcept(-1) }
    }

    private fun shouldEarlyReturn(conditionMet: Boolean?): Boolean {
        return when (operand) {
            Evaluator.OP_AND -> conditionMet == false
            Evaluator.OP_OR -> conditionMet == true
            else -> false
        }
    }

    /**
     * Calculate the start strategy for this condition. This depends on the strategies of the child
     * conditions. If there are any eager conditions, we must also start this condition eagerly. In
     * the absence of eager conditions, we check for lazy conditions. In the absence of either, we
     * make the condition only start when needed.
     */
    private fun calculateStartStrategy(): Int {
        var startStrategy = START_WHEN_NEEDED
        for (condition in conditions) {
            when (condition.startStrategy) {
                START_EAGERLY -> return START_EAGERLY
                START_LAZILY -> {
                    startStrategy = START_LAZILY
                }
                START_WHEN_NEEDED -> {
                    // this is the default, so do nothing
                }
            }
        }
        return startStrategy
    }

    override fun getStartStrategy(): Int {
        return _startStrategy
    }
}
