/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.flicker.bubbles.utils

import java.util.concurrent.atomic.AtomicBoolean
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The rule that applies the [rule] per parameters instead of executing it per test.
 *
 * @param rule the rule that we only want to apply once per test parameter change.
 */
class ApplyPerParameterRule(private val rule: TestRule, private vararg val params: Any) : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                if (!params.contentEquals(sParams)) {
                    sParams = params
                    isExecuted.set(false)
                }
                if (isExecuted.compareAndSet(false, true)) {
                    rule.apply(base, description).evaluate()
                }
                base.evaluate()
            }
        }
    }

    companion object {
        private val isExecuted = AtomicBoolean()
        private var sParams: Array<out Any> = emptyArray()
    }
}