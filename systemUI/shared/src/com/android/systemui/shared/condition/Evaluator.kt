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

import android.annotation.IntDef

/**
 * Helper for evaluating a collection of [Condition] objects with a given
 * [Evaluator.ConditionOperand]
 */
object Evaluator {
    /** Operands for combining multiple conditions together */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(value = [OP_AND, OP_OR])
    annotation class ConditionOperand

    /**
     * 3-valued logical AND operand, with handling for unknown values (represented as null)
     *
     * ```
     * +-----+----+---+---+
     * | AND | T  | F | U |
     * +-----+----+---+---+
     * | T   | T  | F | U |
     * | F   | F  | F | F |
     * | U   | U  | F | U |
     * +-----+----+---+---+
     * ```
     */
    const val OP_AND = 0

    /**
     * 3-valued logical OR operand, with handling for unknown values (represented as null)
     *
     * ```
     * +-----+----+---+---+
     * | OR  | T  | F | U |
     * +-----+----+---+---+
     * | T   | T  | T | T |
     * | F   | T  | F | U |
     * | U   | T  | U | U |
     * +-----+----+---+---+
     * ```
     */
    const val OP_OR = 1

    /**
     * Evaluates a set of conditions with a given operand
     *
     * If overriding conditions are present, they take precedence over normal conditions if set.
     *
     * @param conditions The collection of conditions to evaluate. If empty, null is returned.
     * @param operand The operand to use when evaluating.
     * @return Either true or false if the value is known, or null if value is unknown
     */
    fun evaluate(conditions: Collection<Condition>, @ConditionOperand operand: Int): Boolean? {
        if (conditions.isEmpty()) return null
        // If there are overriding conditions with values set, they take precedence.
        val values: Collection<Boolean?> =
            conditions
                .filter { it.isConditionSet && it.isOverridingCondition }
                .ifEmpty { conditions }
                .map { condition ->
                    if (condition.isConditionSet) {
                        condition.isConditionMet
                    } else {
                        null
                    }
                }
        return evaluate(values = values, operand = operand)
    }

    /**
     * Evaluates a set of booleans with a given operand
     *
     * @param operand The operand to use when evaluating.
     * @return Either true or false if the value is known, or null if value is unknown
     */
    internal fun evaluate(values: Collection<Boolean?>, @ConditionOperand operand: Int): Boolean? {
        if (values.isEmpty()) return null
        return when (operand) {
            OP_AND -> threeValuedAndOrOr(values = values, returnValueIfAnyMatches = false)
            OP_OR -> threeValuedAndOrOr(values = values, returnValueIfAnyMatches = true)
            else -> null
        }
    }

    /**
     * Helper for evaluating 3-valued logical AND/OR.
     *
     * @param returnValueIfAnyMatches AND returns false if any value is false. OR returns true if
     *   any value is true.
     */
    private fun threeValuedAndOrOr(
        values: Collection<Boolean?>,
        returnValueIfAnyMatches: Boolean
    ): Boolean? {
        var hasUnknown = false
        for (value in values) {
            if (value == null) {
                hasUnknown = true
                continue
            }
            if (value == returnValueIfAnyMatches) {
                return returnValueIfAnyMatches
            }
        }
        return if (hasUnknown) null else !returnValueIfAnyMatches
    }
}
