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

package com.android.launcher3.dragndrop

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG
import com.android.launcher3.dagger.DaggerLauncherAppComponent
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.SandboxApplication
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for {@link SystemDragController}. */
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class SystemDragControllerTest {

    @get:Rule val context = SandboxApplication()
    @get:Rule val flags: SetFlagsRule = SetFlagsRule()

    private lateinit var controller: SystemDragController

    @Before
    fun setUp() {
        context.initDaggerComponent(DaggerLauncherAppComponent.builder())
        controller = SystemDragController.INSTANCE[context]
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testInjection_withFlagDisabled() {
        assertTrue(controller is SystemDragControllerStub)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testInjection_withFlagEnabled() {
        assertTrue(controller is SystemDragControllerImpl)
    }
}
