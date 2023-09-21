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

/**
 * A higher order [Condition] which combines multiple conditions with a specified
 * [Evaluator.ConditionOperand].
 */
internal class CombinedCondition
constructor(
    private val conditions: Collection<Condition>,
    @Evaluator.ConditionOperand private val operand: Int
) : Condition(null, false), Condition.Callback {

    override fun start() {
        onConditionChanged(this)
        conditions.forEach { it.addCallback(this) }
    }

    override fun onConditionChanged(condition: Condition) {
        Evaluator.evaluate(conditions, operand)?.also { value -> updateCondition(value) }
            ?: clearCondition()
    }

    override fun stop() {
        conditions.forEach { it.removeCallback(this) }
    }
}
