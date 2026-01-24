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

import com.android.wm.shell.compatui.api.CompatUIComponentState
import com.android.wm.shell.compatui.api.CompatUIInfo
import com.android.wm.shell.compatui.api.CompatUILifecyclePredicates
import com.android.wm.shell.compatui.api.CompatUISharedState
import junit.framework.Assert.assertEquals

/**
 * Fake class for {@link CompatUILifecycle}
 */
class FakeCompatUILifecyclePredicates(
    private val creationReturn: Boolean = false,
    private val removalReturn: Boolean = false,
    private val initialState: (
        CompatUIInfo,
        CompatUISharedState
    ) -> CompatUIComponentState? = { _, _ -> null }
) {
    var creationInvocation = 0
    var removalInvocation = 0
    var initialStateInvocation = 0
    var lastCreationCompatUIInfo: CompatUIInfo? = null
    var lastCreationSharedState: CompatUISharedState? = null
    var lastRemovalCompatUIInfo: CompatUIInfo? = null
    var lastRemovalSharedState: CompatUISharedState? = null
    var lastRemovalCompState: CompatUIComponentState? = null
    fun getLifecycle() = CompatUILifecyclePredicates(
        creationPredicate = { uiInfo, sharedState ->
            lastCreationCompatUIInfo = uiInfo
            lastCreationSharedState = sharedState
            creationInvocation++
            creationReturn
        },
        removalPredicate = { uiInfo, sharedState, compState ->
            lastRemovalCompatUIInfo = uiInfo
            lastRemovalSharedState = sharedState
            lastRemovalCompState = compState
            removalInvocation++
            removalReturn
        },
        stateBuilder = { a, b -> initialStateInvocation++; initialState(a, b) }
    )

    fun assertCreationInvocation(expected: Int) =
        assertEquals(expected, creationInvocation)

    fun assertRemovalInvocation(expected: Int) =
        assertEquals(expected, removalInvocation)

    fun assertInitialStateInvocation(expected: Int) =
        assertEquals(expected, initialStateInvocation)
}
