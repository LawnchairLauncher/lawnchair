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

package com.android.wm.shell.compatui.impl

import com.android.wm.shell.compatui.api.CompatUIComponentIdGenerator
import com.android.wm.shell.compatui.api.CompatUIInfo
import com.android.wm.shell.compatui.api.CompatUISpec
import junit.framework.Assert.assertEquals

/**
 * A Fake {@link CompatUIComponentIdGenerator} implementation.
 */
class FakeCompatUIComponentIdGenerator(var generatedComponentId: String = "compId") :
    CompatUIComponentIdGenerator {

    var generateInvocations = 0

    override fun generateId(compatUIInfo: CompatUIInfo, spec: CompatUISpec): String {
        generateInvocations++
        return generatedComponentId
    }

    fun resetInvocations() {
        generateInvocations = 0
    }

    fun assertGenerateInvocations(expected: Int) =
        assertEquals(expected, generateInvocations)
}
