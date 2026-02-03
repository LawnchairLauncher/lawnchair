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
import com.android.wm.shell.compatui.api.CompatUIState
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull

/**
 * Asserts no component state exists for the given CompatUISpec
 */
internal fun CompatUIState.assertHasNoStateFor(componentId: String) =
    assertNull(stateForComponent(componentId))

/**
 * Asserts component state for the given CompatUISpec
 */
internal fun CompatUIState.assertHasStateEqualsTo(
    componentId: String,
    expected: CompatUIComponentState
) =
    assertEquals(stateForComponent(componentId), expected)

/**
 * Asserts no component exists for the given CompatUISpec
 */
internal fun CompatUIState.assertHasNoComponentFor(componentId: String) =
    assertNull(getUIComponent(componentId))

/**
 * Asserts component for the given CompatUISpec
 */
internal fun CompatUIState.assertHasComponentFor(componentId: String) =
    assertNotNull(getUIComponent(componentId))
