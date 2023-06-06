/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.util.rule

import com.android.launcher3.config.FeatureFlags.BooleanFlag
import com.android.launcher3.config.FeatureFlags.IntFlag
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.TestUtil
import java.util.function.Supplier
import org.junit.rules.ExternalResource

/** Simple rule which wraps any SafeCloseable object */
class WrapperRule(private val overrideProvider: Supplier<SafeCloseable>) : ExternalResource() {

    private lateinit var overrideClosable: SafeCloseable

    override fun before() {
        overrideClosable = overrideProvider.get()
    }

    override fun after() {
        overrideClosable.close()
    }

    companion object {

        fun BooleanFlag.overrideFlag(value: Boolean) = WrapperRule {
            TestUtil.overrideFlag(this, value)
        }

        fun IntFlag.overrideFlag(value: Int) = WrapperRule { TestUtil.overrideFlag(this, value) }
    }
}
